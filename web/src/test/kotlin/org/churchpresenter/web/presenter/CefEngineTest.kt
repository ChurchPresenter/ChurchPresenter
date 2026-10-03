package org.churchpresenter.web.presenter

import io.mockk.mockk
import org.cef.CefClient
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The web engine's state and the decisions behind it, on an engine of the test's own: whether an
 * install is attempted at all, what each outcome leaves the session with, which failures are
 * reported, and recovering from an engine whose clients cannot be made.
 *
 * The install itself and the native client are what cannot run here, so they arrive as an outcome
 * and a client source; everything [CefManager.init] and [CefManager.createClient] decide is here.
 */
class CefEngineTest {

    private val root: File = Files.createTempDirectory("cef-engine").toFile()
    private val cleanedUp = mutableListOf<File>()
    private val failures = mutableListOf<Throwable>()
    private val warnings = mutableListOf<Pair<Throwable, Map<String, String>>>()
    private var installs = 0

    private val engine = CefEngine(
        cleanupLegacy = { cleanedUp += it },
        reportFailure = { failures += it },
        reportWarning = { _, t, tags -> warnings += t to tags },
    )

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun install(outcome: JcefInstall.Outcome): () -> JcefInstall.Outcome = {
        installs++
        outcome
    }

    // ── Whether to install ──────────────────────────────────────────────────────────────────────

    @Test
    fun `an unsupported macOS is flagged and nothing is installed`() {
        engine.init(unsupportedMacOS = true, unsupportedWindows = false, install(JcefInstall.Outcome.Installed(root)))

        assertTrue(engine.macOsUnsupported)
        assertFalse(engine.initialized)
        assertEquals(0, installs)
    }

    @Test
    fun `an unsupported Windows is flagged and nothing is installed`() {
        engine.init(unsupportedMacOS = false, unsupportedWindows = true, install(JcefInstall.Outcome.Installed(root)))

        assertTrue(engine.windowsUnsupported)
        assertFalse(engine.macOsUnsupported)
        assertEquals(0, installs)
    }

    @Test
    fun `once installed, a second init installs nothing`() {
        engine.init(unsupportedMacOS = false, unsupportedWindows = false, install(JcefInstall.Outcome.Installed(root)))
        engine.init(unsupportedMacOS = false, unsupportedWindows = false, install(JcefInstall.Outcome.Installed(root)))

        assertEquals(1, installs)
    }

    // ── What an outcome leaves ──────────────────────────────────────────────────────────────────

    @Test
    fun `an install records its root and reclaims the old footprint`() {
        engine.init(unsupportedMacOS = false, unsupportedWindows = false, install(JcefInstall.Outcome.Installed(root)))

        assertTrue(engine.initialized)
        assertEquals(root, engine.installRoot)
        assertEquals(listOf(root), cleanedUp)
    }

    @Test
    fun `a blocked install leaves the engine off and reports nothing`() {
        engine.applyInstallOutcome(JcefInstall.Outcome.Blocked("disk_space"))

        assertFalse(engine.initialized)
        assertNull(engine.installRoot)
        assertEquals(emptyList(), failures)
    }

    @Test
    fun `a failed install is reported as ours`() {
        val cause = UnsatisfiedLinkError("libcef: The specified procedure could not be found")
        engine.applyInstallOutcome(JcefInstall.Outcome.Failed(root, cause))

        assertFalse(engine.initialized)
        assertFalse(engine.blockedByPolicy)
        assertEquals(listOf<Throwable>(cause), failures)
    }

    @Test
    fun `a failure the machine's policy caused is flagged instead of reported`() {
        val cause = UnsatisfiedLinkError("jcef.dll: An Application Control policy has blocked this file")
        engine.applyInstallOutcome(JcefInstall.Outcome.Failed(File(root, "Ünïcode"), cause))

        assertTrue(engine.blockedByPolicy)
        assertEquals(emptyList(), failures)
    }

    @Test
    fun `an install that fails after one that worked takes its clients away`() {
        engine.applyInstallOutcome(JcefInstall.Outcome.Installed(root))
        engine.clientSource = { mockk<CefClient>() }

        engine.applyInstallOutcome(JcefInstall.Outcome.Blocked("permission_denied"))

        assertFalse(engine.initialized)
        assertNull(engine.createClient())
    }

    // ── Clients ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `with no engine there is no client, and nothing is reported`() {
        assertNull(engine.createClient())
        assertEquals(emptyList(), warnings)
    }

    @Test
    fun `a working engine hands out the client it makes`() {
        val client = mockk<CefClient>()
        engine.applyInstallOutcome(JcefInstall.Outcome.Installed(root))
        engine.clientSource = { client }

        assertSame(client, engine.createClient())
        assertTrue(engine.initialized)
    }

    @Test
    fun `an engine that cannot make a client is switched off, warned about once, and not asked again`() {
        var asked = 0
        engine.applyInstallOutcome(JcefInstall.Outcome.Installed(root))
        engine.clientSource = {
            asked++
            error("CefApp INITIALIZATION_FAILED")
        }

        assertNull(engine.createClient())
        assertNull(engine.createClient())

        assertFalse(engine.initialized, "the tab falls back to its explanatory panel")
        assertEquals(1, asked)
        assertEquals("true", warnings.single().second["jcef.recovered"])
    }

    // ── The defaults the app runs with ──────────────────────────────────────────────────────────

    @Test
    fun `on its own defaults a failed install lands in the crash log, and a client failure only warns`() {
        // The app makes this at startup; the reporter writes into it but does not make it.
        val crashDir = File(System.getProperty("user.home"), ".churchpresenter/crash-reports").apply { mkdirs() }
        val before = crashDir.listFiles()?.size ?: 0
        val defaults = CefEngine()

        defaults.applyInstallOutcome(JcefInstall.Outcome.Failed(root, UnsatisfiedLinkError("chrome_elf.dll")))
        defaults.applyInstallOutcome(JcefInstall.Outcome.Installed(root))
        defaults.clientSource = { error("INITIALIZATION_FAILED") }
        assertNull(defaults.createClient())

        val logs = crashDir.listFiles().orEmpty()
        assertEquals(before + 1, logs.size, "the install failure, and only it, was written")
        assertTrue(logs.any { "chrome_elf.dll" in it.readText() })
        assertNull(defaults.clientSource, "the failed source is dropped")
    }
}
