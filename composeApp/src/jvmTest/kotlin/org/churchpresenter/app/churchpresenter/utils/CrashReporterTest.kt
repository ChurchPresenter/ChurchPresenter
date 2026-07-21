package org.churchpresenter.app.churchpresenter.utils

import io.sentry.SentryLevel
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * [CrashReporter] with Sentry never initialised — which is exactly the state of a user who opted
 * out of analytics, or any build without a DSN. Every telemetry entry point must degrade to a
 * silent no-op there, and the local crash file must still be written.
 *
 * [CrashReporter.initialize] is deliberately NOT called: it installs a global uncaught-exception
 * handler and a JVM shutdown hook, which would outlive the test. Tests create the crash directory
 * themselves, which is the only thing `initialize` does that the write path depends on.
 */
class CrashReporterTest {

    private val appDir = File(System.getProperty("user.home"), ".churchpresenter")
    private val crashDir = File(appDir, "crash-reports")
    private val installIdFile = File(appDir, ".install_id")
    private val crashCountFile = File(appDir, ".crash_count")

    @BeforeTest
    fun freshState() {
        crashDir.deleteRecursively()
        crashDir.mkdirs() // normally done by initialize()
        installIdFile.delete()
        crashCountFile.delete()
    }

    @AfterTest
    fun cleanup() {
        crashDir.deleteRecursively()
        CrashReporter.videoBackgroundsDisabled = false
    }

    private fun crashFiles() = crashDir.listFiles()?.filter { it.name.startsWith("crash_") }.orEmpty()

    /** [CrashReporter.scrubPii] is private but privacy-critical, so it is exercised directly. */
    private fun scrubPii(input: String?): String? {
        val method = CrashReporter::class.java
            .getDeclaredMethod("scrubPii", String::class.java)
            .apply { isAccessible = true }
        return method.invoke(CrashReporter, input) as String?
    }

    // ── Sentry-disabled degradation ─────────────────────────────────────────────

    @Test
    fun `isEnabled is false when Sentry was never initialised`() {
        assertFalse(CrashReporter.isEnabled())
    }

    @Test
    fun `every telemetry entry point is a silent no-op with Sentry disabled`() {
        // None of these may throw — they are called from UI and render paths.
        CrashReporter.breadcrumb("a breadcrumb")
        CrashReporter.breadcrumb("categorised", category = "test", level = SentryLevel.WARNING)
        CrashReporter.setTag("tab", "songs")
        CrashReporter.setConfigTags(mapOf("outputs" to "2", "vlc" to "true"))
        CrashReporter.setContext("jcef", mapOf("installDir" to "/tmp/jcef"))
        CrashReporter.setUser("some-install-id")
        CrashReporter.reportWarning("a warning", RuntimeException("boom"), mapOf("k" to "v"))
        CrashReporter.sendUserFeedback("it broke", name = "Sam", email = "sam@example.org")
        assertFalse(CrashReporter.sendTestEvent(), "a test event cannot be sent while disabled")
    }

    /**
     * The masked DSN is shown in the settings UI, so it must never expose the secret key. Whether
     * a real `sentry.properties` is on the classpath depends on the checkout, so this reads the
     * same resource the reporter does and asserts against whichever case applies.
     */
    @Test
    fun `maskedDsn never reveals the secret key`() {
        val props = java.util.Properties().apply {
            CrashReporter::class.java.classLoader
                ?.getResourceAsStream("sentry.properties")?.use { load(it) }
        }
        val rawDsn = props.getProperty("dsn", "").trim()
        val masked = CrashReporter.maskedDsn()

        if (rawDsn.isBlank()) {
            assertEquals("", masked, "an unconfigured DSN masks to the empty string")
            return
        }

        assertTrue(masked.isNotBlank())
        assertFalse(masked == rawDsn, "the DSN must not be shown verbatim")
        assertTrue("•" in masked, "the key should be bulleted out: $masked")

        // Everything after '@' (host/project id) is not secret and is kept; the key before it
        // must be reduced to at most its first 6 characters.
        val at = rawDsn.indexOf('@')
        if (at >= 0) {
            val key = rawDsn.substring(rawDsn.indexOf("//") + 2, at)
            assertTrue(key.length > 6, "test needs a key longer than the 6 kept chars")
            assertFalse(key in masked, "the full key leaked into the masked form")
            assertTrue(rawDsn.substring(at) in masked, "the non-secret host part should be kept")
        }
    }

    // ── trace ───────────────────────────────────────────────────────────────────

    @Test
    fun `trace runs the block and returns its value when Sentry is disabled`() {
        var ran = false
        val result = CrashReporter.trace("op", "name") { ran = true; 42 }
        assertTrue(ran, "the block must always run, instrumented or not")
        assertEquals(42, result)
    }

    @Test
    fun `trace propagates exceptions rather than swallowing them`() {
        try {
            CrashReporter.trace<Unit>("op", "name") { throw IllegalStateException("inner failure") }
            fail("exception should have propagated")
        } catch (e: IllegalStateException) {
            assertEquals("inner failure", e.message)
        }
    }

    // ── Local crash log ─────────────────────────────────────────────────────────

    @Test
    fun `reportException writes a local crash file with the stack trace`() {
        CrashReporter.reportException(IllegalStateException("kaboom"), context = "Loading song file")

        val file = assertNotNull(crashFiles().singleOrNull(), "expected exactly one crash file")
        assertTrue(file.name.startsWith("crash_"))
        assertTrue(file.name.endsWith("_error.txt"), "a reported exception is non-fatal: ${file.name}")

        val text = file.readText()
        assertTrue("=== ChurchPresenter Crash Report ===" in text)
        assertTrue("Fatal: false" in text)
        assertTrue("Context: Loading song file" in text)
        assertTrue("IllegalStateException" in text)
        assertTrue("kaboom" in text)
        assertTrue("at " in text, "the stack trace itself should be present")
        for (field in listOf("Timestamp:", "Version:", "OS:", "Java:")) {
            assertTrue(field in text, "missing $field")
        }
    }

    @Test
    fun `the context line is omitted when no context is given`() {
        CrashReporter.reportException(RuntimeException("no context"))
        assertFalse("Context:" in crashFiles().single().readText())
    }

    @Test
    fun `a nested cause is recorded`() {
        val cause = IllegalArgumentException("the real reason")
        CrashReporter.reportException(RuntimeException("wrapper", cause))
        val text = crashFiles().single().readText()
        assertTrue("wrapper" in text)
        assertTrue("the real reason" in text, "the root cause must survive into the report")
        assertTrue("Caused by" in text)
    }

    @Test
    fun `reporting never throws even when the crash directory is missing`() {
        // Simulates a user deleting ~/.churchpresenter while the app runs.
        crashDir.deleteRecursively()
        CrashReporter.reportException(RuntimeException("nowhere to write"))
        assertTrue(crashFiles().isEmpty())
    }

    // ── Install id ──────────────────────────────────────────────────────────────

    @Test
    fun `installId is created once and then stable`() {
        val first = CrashReporter.installId()
        assertTrue(first.isNotBlank())
        assertEquals(36, first.length, "expected a UUID: $first")
        assertEquals(first, CrashReporter.installId(), "the id must not change between calls")
        assertEquals(first, installIdFile.readText().trim(), "and must be the persisted value")
    }

    @Test
    fun `an existing install id is reused rather than regenerated`() {
        installIdFile.parentFile.mkdirs()
        installIdFile.writeText("  pre-existing-id  \n")
        assertEquals("pre-existing-id", CrashReporter.installId(), "should be read and trimmed")
    }

    // ── Video-background crash guard ────────────────────────────────────────────

    @Test
    fun `re-enabling video backgrounds clears the flag and resets the crash counter`() {
        crashCountFile.writeText("5")
        CrashReporter.videoBackgroundsDisabled = true

        CrashReporter.reEnableVideoBackgrounds()

        assertFalse(CrashReporter.videoBackgroundsDisabled)
        assertEquals("0", crashCountFile.readText().trim(), "the counter must reset, or it re-trips at once")
    }

    // ── PII scrubbing ───────────────────────────────────────────────────────────

    @Test
    fun `home directory paths are redacted on every platform layout`() {
        assertEquals("/Users/<user>/Documents/song.sps", scrubPii("/Users/alice/Documents/song.sps"))
        assertEquals("/home/<user>/songs", scrubPii("/home/bob/songs"))
        assertEquals("""C:\Users\<user>\AppData""", scrubPii("""C:\Users\carol\AppData"""))
    }

    @Test
    fun `redaction is case-insensitive and keeps the rest of the path`() {
        assertEquals("/USERS/<user>/x", scrubPii("/USERS/Dave/x"))
        assertEquals("/Users/<user>/deeply/nested/file.txt", scrubPii("/Users/erin/deeply/nested/file.txt"))
    }

    @Test
    fun `multiple paths in one string are all redacted`() {
        assertEquals(
            "copy /Users/<user>/a to /home/<user>/b",
            scrubPii("copy /Users/frank/a to /home/grace/b"),
        )
    }

    @Test
    fun `text with nothing to redact is returned unchanged`() {
        val safe = "java.lang.IllegalStateException: something broke in the renderer"
        assertEquals(safe, scrubPii(safe))
        assertEquals("", scrubPii(""))
        assertEquals(null, scrubPii(null))
    }

    @Test
    fun `the current OS username is redacted wherever it appears`() {
        val user = System.getProperty("user.name", "")
        if (user.length < 3) return // the guard in scrubPii; nothing to assert on such a machine
        val scrubbed = assertNotNull(scrubPii("connection failed for account $user on host box"))
        assertFalse(user in scrubbed, "the bare username should not survive: $scrubbed")
        assertTrue("<user>" in scrubbed)
    }

    /**
     * Documents that the LOCAL crash file is deliberately NOT scrubbed — it stays on the user's
     * own machine and full paths make it more useful for support. Scrubbing applies only on the
     * way out to Sentry (`beforeSend` scrubs the event and attaches a scrubbed copy of this file).
     */
    @Test
    fun `the local crash file keeps full paths on purpose`() {
        val user = System.getProperty("user.name", "")
        if (user.length < 3) return
        CrashReporter.reportException(RuntimeException("failed reading /Users/$user/songs/a.sps"))
        val text = crashFiles().single().readText()
        assertTrue(user in text, "the local file is intentionally unscrubbed")
    }
}
