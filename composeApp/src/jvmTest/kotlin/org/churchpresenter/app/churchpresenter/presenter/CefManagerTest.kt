package org.churchpresenter.app.churchpresenter.presenter

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CefManagerTest {

    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-cefmanager").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun awaitUntil(timeoutMs: Long = 2_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for condition")
    }

    @Test
    fun `isUnsupportedMacOS is false on non-macOS operating systems`() {
        assertFalse(CefManager.isUnsupportedMacOS(osName = "Windows 11", osVersion = "10.0"))
        assertFalse(CefManager.isUnsupportedMacOS(osName = "Linux", osVersion = "6.1"))
    }

    @Test
    fun `isUnsupportedMacOS is false on macOS 12 and newer`() {
        assertFalse(CefManager.isUnsupportedMacOS(osName = "Mac OS X", osVersion = "12.0"))
        assertFalse(CefManager.isUnsupportedMacOS(osName = "Mac OS X", osVersion = "15.5"))
    }

    @Test
    fun `isUnsupportedMacOS is true below macOS 12`() {
        assertTrue(CefManager.isUnsupportedMacOS(osName = "Mac OS X", osVersion = "11.7"))
        assertTrue(CefManager.isUnsupportedMacOS(osName = "Mac OS X", osVersion = "10.15"))
    }

    @Test
    fun `isUnsupportedMacOS is false when the version cannot be parsed`() {
        assertFalse(CefManager.isUnsupportedMacOS(osName = "Mac OS X", osVersion = ""))
        assertFalse(CefManager.isUnsupportedMacOS(osName = "Mac OS X", osVersion = "unknown"))
    }

    @Test
    fun `jcefRootDir on Windows uses a writable ProgramData path`() {
        val root = CefManager.jcefRootDir(
            osName = "Windows 11",
            programData = dir.absolutePath,
            homeDir = "/should-not-be-used",
        )
        assertEquals(File(dir, "ChurchPresenter").absolutePath, root.absolutePath)
        assertTrue(root.isDirectory, "jcefRootDir must actually create the ProgramData directory")
    }

    @Test
    fun `jcefRootDir on Windows falls back to the home directory when ProgramData is unusable`() {
        val programData = File(dir, "programdata").apply { mkdirs() }
        // A plain file blocks mkdirs() at the exact "ChurchPresenter" path, forcing the writable check to fail.
        File(programData, "ChurchPresenter").writeText("not a directory")
        val home = File(dir, "home").apply { mkdirs() }

        val root = CefManager.jcefRootDir(
            osName = "Windows 11",
            programData = programData.absolutePath,
            homeDir = home.absolutePath,
        )

        assertEquals(File(home, ".churchpresenter").absolutePath, root.absolutePath)
    }

    @Test
    fun `jcefRootDir on non-Windows always uses the home directory`() {
        val home = File(dir, "home").apply { mkdirs() }
        val root = CefManager.jcefRootDir(
            osName = "Mac OS X",
            programData = dir.absolutePath,
            homeDir = home.absolutePath,
        )
        assertEquals(File(home, ".churchpresenter").absolutePath, root.absolutePath)
    }

    @Test
    fun `cleanupLegacyJcef does nothing when the active root is the home directory`() {
        val home = dir.absolutePath
        val churchDir = File(dir, ".churchpresenter")
        val legacyJcef = File(churchDir, "jcef").apply { mkdirs() }

        CefManager.cleanupLegacyJcef(activeRoot = churchDir, homeDir = home)

        assertTrue(
            legacyJcef.isDirectory,
            "nothing should be deleted when the active root already is the home directory",
        )
    }

    @Test
    fun `cleanupLegacyJcef does nothing when there is no legacy footprint`() {
        val home = File(dir, "home").apply { mkdirs() }
        val activeRoot = File(dir, "programdata-root").apply { mkdirs() }

        CefManager.cleanupLegacyJcef(activeRoot = activeRoot, homeDir = home.absolutePath)
    }

    @Test
    fun `cleanupLegacyJcef deletes the legacy jcef and webview-cache directories when relocated`() {
        val home = File(dir, "home").apply { mkdirs() }
        val churchDir = File(home, ".churchpresenter")
        val legacyJcef = File(churchDir, "jcef").apply { mkdirs() }
        val legacyCache = File(churchDir, "webview-cache").apply { mkdirs() }
        val activeRoot = File(dir, "programdata-root").apply { mkdirs() }

        CefManager.cleanupLegacyJcef(activeRoot = activeRoot, homeDir = home.absolutePath)

        awaitUntil { !legacyJcef.exists() && !legacyCache.exists() }
    }

    @Test
    fun `cleanupLegacyJcef deletes only the legacy directories that actually exist`() {
        val home = File(dir, "home").apply { mkdirs() }
        val churchDir = File(home, ".churchpresenter")
        val legacyJcef = File(churchDir, "jcef").apply { mkdirs() }
        val activeRoot = File(dir, "programdata-root").apply { mkdirs() }

        CefManager.cleanupLegacyJcef(activeRoot = activeRoot, homeDir = home.absolutePath)

        awaitUntil { !legacyJcef.exists() }
    }

    @Test
    fun `isUnsupportedMacOS with no arguments reads the real OS properties`() {
        assertEquals(
            CefManager.isUnsupportedMacOS(System.getProperty("os.name", ""), System.getProperty("os.version", "")),
            CefManager.isUnsupportedMacOS(),
        )
    }

    @Test
    fun `jcefRootDir with no arguments resolves to a churchpresenter directory`() {
        assertTrue(
            CefManager.jcefRootDir().name == ".churchpresenter" || CefManager.jcefRootDir().name == "ChurchPresenter",
        )
    }

    @Test
    fun `cleanupLegacyJcef with no home override reads the real user home`() {
        val home = System.getProperty("user.home")
        CefManager.cleanupLegacyJcef(activeRoot = File(home, ".churchpresenter"))
    }

    @Test
    fun `macOsUnsupported defaults to false since init is never called in tests`() {
        assertFalse(CefManager.macOsUnsupported)
    }

    @Test
    fun `patchJcefModuleAccess runs without throwing`() {
        CefManager.patchJcefModuleAccess()
    }

    @Test
    fun `isVirtualizedEnvironment matches a known VM or hypervisor DMI string`() {
        assertTrue(isVirtualizedEnvironment(listOf("Standard PC (i440FX + PIIX, 1996)")))
        assertTrue(isVirtualizedEnvironment(listOf("innotek GmbH", "VirtualBox")))
        assertTrue(isVirtualizedEnvironment(listOf("QEMU")))
    }

    @Test
    fun `isVirtualizedEnvironment does not match real hardware vendors`() {
        assertFalse(isVirtualizedEnvironment(listOf("Apple Inc.", "MacBookPro18,1")))
        assertFalse(isVirtualizedEnvironment(emptyList()))
    }

    @Test
    fun `dispose is a no-op`() {
        CefManager.dispose()
    }

    @Test
    fun `createClient returns null before init has run`() {
        assertNull(CefManager.createClient())
    }

    @Test
    fun `routeAudioToDevice with a blank device id does nothing`() {
        CefManager.routeAudioToDevice("")
    }

    @Test
    fun `routeAudioToDevice swallows the failure when the pactl tool is unavailable`() {
        CefManager.routeAudioToDevice("some-sink")
    }

    @Test
    fun `sinkInputIndicesForProcess finds only the sink inputs belonging to our pid`() {
        val output = """
            Sink Input #12
                application.process.id = "4321"
            Sink Input #13
                application.process.id = "9999"
            Sink Input #14
                application.process.id = "4321"
        """.trimIndent()

        assertEquals(listOf("12", "14"), sinkInputIndicesForProcess(output, pid = 4321))
    }

    @Test
    fun `sinkInputIndicesForProcess returns nothing when no block matches`() {
        val output = """
            Sink Input #1
                application.process.id = "1"
        """.trimIndent()

        assertEquals(emptyList(), sinkInputIndicesForProcess(output, pid = 4321))
    }

    @Test
    fun `sinkInputIndicesForProcess handles a match in the last block`() {
        val output = """
            Sink Input #7
                application.process.id = "555"
        """.trimIndent()

        assertEquals(listOf("7"), sinkInputIndicesForProcess(output, pid = 555))
    }

    @Test
    fun `sinkInputIndicesForProcess on empty output finds nothing`() {
        assertEquals(emptyList(), sinkInputIndicesForProcess("", pid = 1))
    }

    // ── Whether the install is worth attempting at all ──────────────────────────

    private val plentyOfSpace = 10L * 1024 * 1024 * 1024

    @Test
    fun `a directory that cannot be written to blocks the install`() {
        // ProgramData ACLs vary and a locked-down corporate build is normal, not a defect.
        assertEquals(
            "permission_denied",
            CefManager.jcefInstallBlocker(writable = false, usableSpaceBytes = plentyOfSpace),
        )
    }

    @Test
    fun `a disk without room for a bundled Chromium blocks the install`() {
        assertEquals(
            "disk_space",
            CefManager.jcefInstallBlocker(writable = true, usableSpaceBytes = 50L * 1024 * 1024),
        )
    }

    @Test
    fun `a writable directory with room goes ahead`() {
        assertNull(CefManager.jcefInstallBlocker(writable = true, usableSpaceBytes = plentyOfSpace))
    }

    @Test
    fun `an unknown free-space figure is not treated as a full disk`() {
        // File.usableSpace answers 0 when it cannot determine the figure, which is not the same as
        // a full disk — reading it that way would block the install wherever the filesystem does
        // not report one. The real attempt decides instead.
        assertNull(CefManager.jcefInstallBlocker(writable = true, usableSpaceBytes = 0))
    }

    @Test
    fun `permission is answered before space when neither is available`() {
        assertEquals(
            "permission_denied",
            CefManager.jcefInstallBlocker(writable = false, usableSpaceBytes = 0),
        )
    }

    @Test
    fun `a machine policy blocking the engine is named, and nothing else is`() {
        // Windows Application Control (WDAC/AppLocker) blocks jcef.dll on managed installs, which
        // this app cannot fix: JCEF is downloaded at first use rather than shipped signed by us.
        // Twelve reports across five churches, every launch, and the Web tab meanwhile told them to
        // install a Visual C++ redistributable that would not have helped.
        assertEquals(
            "policy",
            CefManager.jcefPolicyBlock(
                "C:\\ProgramData\\ChurchPresenter\\jcef\\jcef.dll: " +
                    "An Application Control policy has blocked this file"
            ),
        )

        // Everything else keeps being reported. Matching a message is a weak test and this only
        // ever suppresses an event, so falling through is the safe direction to be wrong in.
        assertNull(CefManager.jcefPolicyBlock("The specified module could not be found"))
        assertNull(CefManager.jcefPolicyBlock(null))
    }

    @Test
    fun `no CefApp means no client, and no throw`() {
        // The suite never initialises JCEF, so this is the uninitialised branch — the one that has
        // to answer null rather than dereference. The failed-native branch beside it cannot be
        // reached without a real CefApp whose native side has died, which is exactly the state a
        // test cannot construct; the guarantee that matters is shared and asserted here: every
        // caller sees null, never an exception, because the only call site is inside a `remember`.
        assertFalse(CefManager.initialized)
        assertNull(CefManager.createClient())
        assertFalse(CefManager.initialized, "answering null must not flip the flag on")
    }

}
