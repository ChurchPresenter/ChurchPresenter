package org.churchpresenter.app.churchpresenter.utils

import io.sentry.SentryLevel
import io.sentry.SentryOptions
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The startup sequence and the Sentry configuration, driven through the two seams that let a test
 * run the real code: [CrashReporter.startUp] takes the process-global installs as a parameter, and
 * [CrashReporter.configureOptions] applies to any [SentryOptions] rather than only to the live SDK.
 */
class CrashReporterStartupTest {

    private val appDir = File(System.getProperty("user.home"), ".churchpresenter")
    private val crashDir = File(appDir, "crash-reports")
    private val runningFile = File(appDir, ".running")
    private val crashCountFile = File(appDir, ".crash_count")
    private val installIdFile = File(appDir, ".install_id")

    /** Collects what `startUp` would have installed on the JVM, so the test can run it instead. */
    private var handler: Thread.UncaughtExceptionHandler? = null
    private var onExit: Runnable? = null
    private val order = mutableListOf<String>()

    private fun startUp(analyticsReportingEnabled: Boolean, build: BuildIdentity = BuildIdentity()) =
        CrashReporter.startUp(
            analyticsReportingEnabled,
            build,
            setUncaughtHandler = { handler = it; order += "handler" },
            addShutdownHook = { onExit = it; order += "shutdown" },
        )

    @BeforeTest
    fun freshState() {
        crashDir.deleteRecursively()
        runningFile.delete()
        crashCountFile.delete()
        installIdFile.delete()
    }

    @AfterTest
    fun cleanup() {
        crashDir.deleteRecursively()
        runningFile.delete()
        crashCountFile.delete()
        CrashReporter.videoBackgroundsDisabled = false
    }

    // ── startUp ─────────────────────────────────────────────────────────────────

    @Test
    fun `a first launch creates the crash directory and takes the run lock`() {
        startUp(analyticsReportingEnabled = false)

        assertTrue(crashDir.isDirectory, "the write path depends on this existing")
        assertTrue(runningFile.exists(), "the lock is what a later run reads as 'crashed'")
        assertFalse(CrashReporter.didCrashLastRun)
        assertEquals(0, CrashReporter.consecutiveCrashes)
        assertFalse(CrashReporter.videoBackgroundsDisabled)
    }

    @Test
    fun `a leftover run lock is read as a crash and counted`() {
        appDir.mkdirs()
        runningFile.createNewFile()

        startUp(analyticsReportingEnabled = false)

        assertTrue(CrashReporter.didCrashLastRun)
        assertEquals(1, CrashReporter.consecutiveCrashes)
        assertFalse(CrashReporter.videoBackgroundsDisabled, "one crash is not yet the threshold")
        assertEquals("1", crashCountFile.readText().trim(), "the count has to survive the restart")
    }

    @Test
    fun `a second consecutive crash disables video backgrounds`() {
        appDir.mkdirs()
        runningFile.createNewFile()
        crashCountFile.writeText("1")

        startUp(analyticsReportingEnabled = false)

        assertEquals(2, CrashReporter.consecutiveCrashes)
        assertTrue(
            CrashReporter.videoBackgroundsDisabled,
            "repeated crashes must turn off the most likely cause without the user doing anything",
        )
    }

    @Test
    fun `a clean previous run resets the count`() {
        appDir.mkdirs()
        crashCountFile.writeText("2")

        startUp(analyticsReportingEnabled = false)

        assertEquals(0, CrashReporter.consecutiveCrashes)
        assertEquals("0", crashCountFile.readText().trim())
    }

    @Test
    fun `the handler is installed before the shutdown hook`() {
        startUp(analyticsReportingEnabled = false)

        // The handler guards the rest of startUp; installing it last would leave that stretch bare.
        assertEquals(listOf("handler", "shutdown"), order)
    }

    @Test
    fun `the installed handler writes a crash log for the failing thread`() {
        startUp(analyticsReportingEnabled = false)
        val handler = assertNotNull(handler)

        handler.uncaughtException(Thread.currentThread(), IllegalStateException("kaboom"))

        val logs = crashDir.listFiles()?.filter { it.name.startsWith("crash_") }.orEmpty()
        assertEquals(1, logs.size, "an uncaught exception is exactly the case a crash log is for")
        val text = logs.single().readText()
        assertTrue(text.contains("kaboom"), "the message is the whole point of the log")
        assertTrue(text.contains(Thread.currentThread().name), "which thread died is triage data")
    }

    @Test
    fun `the exit hook releases the run lock so a clean quit is not read as a crash`() {
        startUp(analyticsReportingEnabled = false)
        assertTrue(runningFile.exists())

        assertNotNull(onExit).run()

        assertFalse(runningFile.exists())
    }

    @Test
    fun `enabling analytics stamps the install id, disabling it does not`() {
        startUp(analyticsReportingEnabled = false)
        assertFalse(installIdFile.exists(), "opting out must not mint an identifier")

        startUp(analyticsReportingEnabled = true)
        assertTrue(installIdFile.exists())
        val minted = installIdFile.readText().trim()

        startUp(analyticsReportingEnabled = true)
        assertEquals(minted, installIdFile.readText().trim(), "a restart is the same install")
    }

    @Test
    fun `the build identity it is given is what it reports`() {
        startUp(
            analyticsReportingEnabled = false,
            BuildIdentity(versionDisplay = "26.9.177 (abc1234)", appVersion = "26.9.177", isRelease = true),
        )

        val options = SentryOptions()
        CrashReporter.configureOptions(options, "https://key@example.org/1")
        assertEquals("26.9.177", options.release, "the release is what groups events in Sentry")
    }

    // ── Sentry options ──────────────────────────────────────────────────────────

    @Test
    fun `a dev build reports as development and samples everything`() {
        startUp(analyticsReportingEnabled = false, BuildIdentity(isRelease = false))
        val options = SentryOptions()

        CrashReporter.configureOptions(options, "https://key@example.org/1")

        assertEquals("development", options.environment, "dev runs must stay out of release health")
        assertEquals(1.0, options.tracesSampleRate)
        assertEquals(1.0, options.profilesSampleRate)
    }

    @Test
    fun `a release build reports as production and samples performance down`() {
        startUp(analyticsReportingEnabled = false, BuildIdentity(isRelease = true))
        val options = SentryOptions()

        CrashReporter.configureOptions(options, "https://key@example.org/1")

        assertEquals("production", options.environment)
        // Perf volume must not crowd errors out of the quota; errors are captured directly.
        assertEquals(0.2, options.tracesSampleRate)
        assertEquals(0.2, options.profilesSampleRate)
    }

    @Test
    fun `the privacy and triage options are not left to the SDK defaults`() {
        val options = SentryOptions()

        CrashReporter.configureOptions(options, "https://key@example.org/1")

        assertEquals("https://key@example.org/1", options.dsn)
        assertFalse(options.isAttachServerName, "a hostname is the user's machine name")
        assertFalse(options.isAttachThreads)
        assertTrue(options.isAttachStacktrace)
        assertTrue(options.isEnableUncaughtExceptionHandler)
        assertTrue(options.isEnableAutoSessionTracking)
        assertTrue(options.inAppIncludes.contains("org.churchpresenter"), "app frames must stand out")
        assertNotNull(options.beforeSend, "nothing may leave without going through the scrubber")
    }

    // ── beforeSend ──────────────────────────────────────────────────────────────

    @Test
    fun `beforeSend returns the event so delivery is never blocked`() {
        val callback = CrashReporter.crashAttachingBeforeSend()
        val event = io.sentry.SentryEvent().apply { level = SentryLevel.INFO }

        val result = callback.execute(event, io.sentry.Hint())

        assertEquals(event, result)
    }

    @Test
    fun `an error event carries the latest crash log, scrubbed`() {
        crashDir.mkdirs()
        File(crashDir, "crash_test.txt").writeText("failed for user sam at /Users/sam/Documents/set.cps")
        val callback = CrashReporter.crashAttachingBeforeSend()
        val hint = io.sentry.Hint()

        callback.execute(io.sentry.SentryEvent().apply { level = SentryLevel.ERROR }, hint)

        val attachment = hint.attachments.singleOrNull()
        assertNotNull(attachment, "the local log is what makes a remote error diagnosable")
        val body = String(assertNotNull(attachment.bytes))
        assertFalse(body.contains("/Users/sam"), "a home path names the person: $body")
    }

    @Test
    fun `an info event carries no attachment`() {
        crashDir.mkdirs()
        File(crashDir, "crash_test.txt").writeText("something")
        val hint = io.sentry.Hint()

        CrashReporter.crashAttachingBeforeSend()
            .execute(io.sentry.SentryEvent().apply { level = SentryLevel.INFO }, hint)

        assertTrue(hint.attachments.isEmpty(), "breadcrumb-level noise must not ship crash files")
    }

    // ── DSN ─────────────────────────────────────────────────────────────────────

    @Test
    fun `an absent sentry properties reads as no DSN rather than failing`() {
        assertEquals("", CrashReporter.dsnFrom(null))
    }

    @Test
    fun `the dsn property is read and trimmed`() {
        val stream = ByteArrayInputStream("dsn=https://key@example.org/1  \n".toByteArray())

        assertEquals("https://key@example.org/1", CrashReporter.dsnFrom(stream))
    }

    @Test
    fun `a properties file without a dsn key reads as empty`() {
        val stream = ByteArrayInputStream("something=else\n".toByteArray())

        assertEquals("", CrashReporter.dsnFrom(stream))
    }

    @Test
    fun `masking keeps the DSN recognisable without printing the key`() {
        val masked = CrashReporter.maskDsn("https://abcdef0123456789@o1.ingest.sentry.io/42")

        assertTrue(masked.startsWith("https://abcdef"), "enough to tell two DSNs apart: $masked")
        assertFalse(masked.contains("0123456789"), "the secret half must not survive: $masked")
        assertTrue(masked.endsWith("@o1.ingest.sentry.io/42"), "the host identifies the project")
    }

    @Test
    fun `an unconfigured DSN masks to empty`() {
        assertEquals("", CrashReporter.maskDsn(""))
        assertEquals("", CrashReporter.maskDsn("   "))
    }

    @Test
    fun `a malformed DSN is truncated rather than echoed`() {
        // No '@' means the shape is unknown, so none of it can be assumed safe to show.
        val masked = CrashReporter.maskDsn("not-a-dsn-but-quite-long-anyway")

        assertTrue(masked.endsWith("••••"))
        assertFalse(masked.contains("quite-long-anyway"), masked)
    }

    @Test
    fun `a fatal event carries the crash log too`() {
        crashDir.mkdirs()
        File(crashDir, "crash_fatal.txt").writeText("it died")
        val hint = io.sentry.Hint()

        CrashReporter.crashAttachingBeforeSend()
            .execute(io.sentry.SentryEvent().apply { level = SentryLevel.FATAL }, hint)

        assertEquals(1, hint.attachments.size, "a fatal is the case the log matters most for")
    }

    @Test
    fun `an error event with no crash log on disk still sends`() {
        crashDir.deleteRecursively()
        val hint = io.sentry.Hint()
        val event = io.sentry.SentryEvent().apply { level = SentryLevel.ERROR }

        val result = CrashReporter.crashAttachingBeforeSend().execute(event, hint)

        assertEquals(event, result, "no local log is not a reason to drop the report")
        assertTrue(hint.attachments.isEmpty())
    }

    @Test
    fun `the newest crash log is the one attached`() {
        crashDir.mkdirs()
        val older = File(crashDir, "crash_old.txt").apply { writeText("older") }
        val newer = File(crashDir, "crash_new.txt").apply { writeText("newer") }
        older.setLastModified(1_000_000L)
        newer.setLastModified(2_000_000L)

        assertEquals(newer, CrashReporter.latestCrashFile(), "an old log describes a different fault")
    }

    @Test
    fun `no crash logs at all reads as none rather than failing`() {
        crashDir.deleteRecursively()

        assertEquals(null, CrashReporter.latestCrashFile())
    }

    // ── the reporting opt-out ───────────────────────────────────────────────────

    @Test
    fun `turning reporting off with Sentry already disabled is a no-op`() {
        // The opt-out is reachable from the settings dialog at any time, including before anything
        // has initialised Sentry — it must not throw there.
        CrashReporter.setReportingEnabled(false)

        assertFalse(CrashReporter.isEnabled())
    }

    @Test
    fun `turning reporting on without a DSN leaves it disabled rather than half-initialised`() {
        // :diagnostics has no sentry.properties on its classpath, so this is the shipped
        // no-DSN path: it must come back cleanly instead of leaving a partly-configured SDK.
        CrashReporter.setReportingEnabled(true)

        assertFalse(CrashReporter.isEnabled())
    }

    @Test
    fun `the masked DSN of an unconfigured build is empty`() {
        assertEquals("", CrashReporter.maskedDsn())
    }

    @Test
    fun `a test event cannot be sent while Sentry is disabled`() {
        assertFalse(CrashReporter.sendTestEvent(), "nothing may be sent without a configured DSN")
    }

    @Test
    fun `only crash logs are considered, not whatever else is in the folder`() {
        crashDir.mkdirs()
        File(crashDir, "notes.txt").writeText("not a crash log")
        File(crashDir, "subdir").mkdirs()

        assertEquals(null, CrashReporter.latestCrashFile(), "a stray file must not be sent to Sentry")
    }

    @Test
    fun `logs older than the retention window are cleaned up and recent ones are kept`() {
        crashDir.mkdirs()
        val stale = File(crashDir, "crash_stale.txt").apply { writeText("old") }
        val recent = File(crashDir, "crash_recent.txt").apply { writeText("new") }
        val other = File(crashDir, "keep.txt").apply { writeText("not ours") }
        val fortyDaysAgo = System.currentTimeMillis() - 40L * 24 * 60 * 60 * 1000
        stale.setLastModified(fortyDaysAgo)
        other.setLastModified(fortyDaysAgo)

        CrashReporter.cleanOldLogs()

        assertFalse(stale.exists(), "crash logs are personal data and must not accumulate for ever")
        assertTrue(recent.exists(), "a recent log is still the one a user would be asked for")
        assertTrue(other.exists(), "cleanup owns crash_ files only")
    }
}
