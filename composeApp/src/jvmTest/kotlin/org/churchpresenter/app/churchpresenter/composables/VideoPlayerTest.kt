package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JPanel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * VLC/VLCJ-backed video playback. The two composables ([VideoPlayer], [SoftwareVideoPlayer]) need
 * a real, working native VLC install to get past `createMediaPlayerComponent()`; whether that
 * succeeds varies by machine (this project's own [SceneSourceRendererTest] documents the same
 * constraint for the canvas Video source), so they are not exercised here. What *is* tested is
 * every piece of this file's logic that does not require VLC to actually load: the VLC-directory
 * detection helpers (pure filesystem checks), the [Component] extension functions' non-VLC
 * fallback branch, and [SharedVideoOutputDisplay] — a plain composable over an in-memory
 * [SharedVideoOutput] frame holder with no VLC involvement at all.
 */
@OptIn(ExperimentalTestApi::class)
class VideoPlayerTest {

    // ── dirContainsVlcLib ──────────────────────────────────────────────────────────────────────

    private fun tempDir(): Path = Files.createTempDirectory("cp-vlc-test")

    @Test
    fun `a directory containing libvlc dll is recognized`() {
        val dir = tempDir()
        try {
            Files.createFile(dir.resolve("libvlc.dll"))
            assertTrue(dirContainsVlcLib(dir))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a directory containing libvlc dylib is recognized`() {
        val dir = tempDir()
        try {
            Files.createFile(dir.resolve("libvlc.dylib"))
            assertTrue(dirContainsVlcLib(dir))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a directory containing a versioned libvlc so is recognized`() {
        val dir = tempDir()
        try {
            Files.createFile(dir.resolve("libvlc.so.5"))
            assertTrue(dirContainsVlcLib(dir))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a directory containing only libvlccore is not recognized as containing libvlc`() {
        val dir = tempDir()
        try {
            Files.createFile(dir.resolve("libvlccore.so.9"))
            assertFalse(dirContainsVlcLib(dir))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `an empty directory does not contain a VLC library`() {
        val dir = tempDir()
        try {
            assertFalse(dirContainsVlcLib(dir))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a nonexistent directory does not contain a VLC library`() {
        assertFalse(dirContainsVlcLib(Path.of("/nonexistent/path/for/cp-vlc-test")))
    }

    // ── applyCustomVlcPath ─────────────────────────────────────────────────────────────────────

    private var savedCustomPath: String = ""
    private var savedJnaPath: String? = null

    @BeforeTest
    fun saveVlcState() {
        savedCustomPath = vlcCustomPath
        savedJnaPath = System.getProperty("jna.library.path")
    }

    @AfterTest
    fun restoreVlcState() {
        vlcCustomPath = savedCustomPath
        if (savedJnaPath != null) System.setProperty("jna.library.path", savedJnaPath!!)
        else System.clearProperty("jna.library.path")
    }

    @Test
    fun `a blank custom VLC path leaves jna library path untouched`() {
        System.clearProperty("jna.library.path")
        vlcCustomPath = ""
        applyCustomVlcPath()
        assertEquals(null, System.getProperty("jna.library.path"))
    }

    @Test
    fun `a custom VLC path pointing at a real directory is appended to jna library path`() {
        val dir = tempDir()
        try {
            System.clearProperty("jna.library.path")
            vlcCustomPath = dir.toString()
            applyCustomVlcPath()
            assertTrue(System.getProperty("jna.library.path").orEmpty().contains(dir.toString()))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a custom VLC path that is not a directory is ignored`() {
        val file = Files.createTempFile("cp-vlc-test-file", ".txt")
        try {
            System.clearProperty("jna.library.path")
            vlcCustomPath = file.toString()
            applyCustomVlcPath()
            assertEquals(null, System.getProperty("jna.library.path"))
        } finally {
            file.toFile().delete()
        }
    }

    @Test
    fun `applying the same custom path twice does not duplicate the jna library path entry`() {
        val dir = tempDir()
        try {
            System.clearProperty("jna.library.path")
            vlcCustomPath = dir.toString()
            applyCustomVlcPath()
            val once = System.getProperty("jna.library.path")
            applyCustomVlcPath()
            assertEquals(once, System.getProperty("jna.library.path"))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    // ── detectVlcInstallPath ───────────────────────────────────────────────────────────────────

    // No os.name save/restore here any more: nothing below swaps it. That matters beyond tidiness —
    // skiko latches os.name JVM-wide, so a Compose test composed inside a faked one breaks every
    // later Compose test in the same fork (see the root AGENT.md).

    // These drive the probe rather than the machine's own filesystem. They used to call
    // `detectVlcInstallPath()` against the real disk and assert "", which said nothing on a machine
    // without VLC and *failed* on one with it — the Windows case broke on any developer box with
    // VLC in Program Files.

    @Test
    fun `no candidate directory holds libvlc, so nothing is detected`() {
        val nowhere: (Path) -> Boolean = { false }

        assertEquals("", detectVlcInstallPathFor("windows 11", hasVlcLib = nowhere, pathExists = { false }))
        assertEquals("", detectVlcInstallPathFor("generic linux", hasVlcLib = nowhere, pathExists = { false }))
        assertEquals("", detectVlcInstallPathFor("mac os x", hasVlcLib = nowhere, pathExists = { false }))
    }

    @Test
    fun `on Windows the first candidate that holds libvlc wins`() {
        val programFiles = Paths.get(System.getenv("ProgramFiles") ?: "C:\\Program Files", "VideoLAN", "VLC")

        val found = detectVlcInstallPathFor("windows 11", hasVlcLib = { it == programFiles }, pathExists = { false })

        assertEquals(programFiles.toString(), found)
    }

    @Test
    fun `on Linux the search walks the candidates in order`() {
        val lib64 = Paths.get("/usr/lib64")

        val found = detectVlcInstallPathFor("generic linux", hasVlcLib = { it == lib64 }, pathExists = { false })

        assertEquals(lib64.toString(), found, "/usr/lib holds no libvlc, so the next candidate answers")
    }

    @Test
    fun `on macOS the bundle is returned even when its lib directory holds no libvlc`() {
        // The one branch that does not simply return the first hit: VLC.app is still where the user
        // installed VLC, and JNA may yet find the library through it.
        val bundle = detectVlcInstallPathFor("mac os x", hasVlcLib = { false }, pathExists = { true })
        assertEquals("/Applications/VLC.app", bundle)

        // Derived through Paths.get, not written as a POSIX literal: the production code returns
        // `libPath.toString()`, which separates with backslashes when the suite runs on Windows.
        val libDir = Paths.get("/Applications/VLC.app/Contents/MacOS/lib").toString()
        val withLib = detectVlcInstallPathFor("mac os x", hasVlcLib = { true }, pathExists = { true })
        assertEquals(libDir, withLib, "a real lib directory wins over it")
    }

    // ── Component extension functions ─────────────────────────────────────────────────────────

    @Test
    fun `mediaPlayer throws for a Component that is neither known VLCJ component type`() {
        assertFailsWith<IllegalStateException> { JPanel().mediaPlayer() }
    }

    @Test
    fun `releasePlayer is a no-op for a Component that is neither known VLCJ component type`() {
        JPanel().releasePlayer() // must not throw
    }

    // ── VlcAudioDevice ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `VlcAudioDevice holds the id and description it is constructed with`() {
        val device = VlcAudioDevice("dev-1", "Built-in Output")
        assertEquals("dev-1", device.id)
        assertEquals("Built-in Output", device.description)
    }

    /**
     * The shape a real Windows machine returns: VLC's own default entry first, with an empty id and
     * a description in VLC's language, then the actual outputs.
     */
    @Test
    fun `VLC's own default entry is dropped, because the app offers that choice itself`() {
        val fromVlc = listOf(
            VlcAudioDevice("", "Default"),
            VlcAudioDevice("{0.0.0.00000000}.{42775d99}", "Speakers (2- Blackmagic Audio)"),
            VlcAudioDevice("{0.0.0.00000000}.{4a89be19}", "Speakers (DeckLink 4K Extreme)"),
        )

        val kept = withoutVlcDefaultDevice(fromVlc)

        assertEquals(2, kept.size, "only the real outputs survive")
        assertTrue(kept.none { it.id.isBlank() }, "an empty id is the app's own stored 'system default'")
        assertEquals("Speakers (2- Blackmagic Audio)", kept.first().description, "and the order is kept")
    }

    @Test
    fun `a device list with no default entry is left exactly as it came`() {
        val fromVlc = listOf(VlcAudioDevice("dev-1", "Built-in Output"))

        assertEquals(fromVlc, withoutVlcDefaultDevice(fromVlc))
        assertEquals(emptyList(), withoutVlcDefaultDevice(emptyList()))
    }

    // ── SharedVideoOutputDisplay ───────────────────────────────────────────────────────────────

    @Test
    fun `SharedVideoOutputDisplay renders nothing when no frame has been written`() = runComposeUiTest {
        SharedVideoOutput.frame.value = null
        setContent {
            MaterialTheme {
                SharedVideoOutputDisplay(modifier = Modifier.testTag("shared-video"))
            }
        }
        onNodeWithTag("shared-video").assertDoesNotExist()
    }

    @Test
    fun `SharedVideoOutputDisplay renders the latest written frame`() = runComposeUiTest {
        try {
            SharedVideoOutput.frame.value = ImageBitmap(4, 4)
            setContent {
                MaterialTheme {
                    SharedVideoOutputDisplay(modifier = Modifier.testTag("shared-video"))
                }
            }
            onNodeWithTag("shared-video").assertExists()
        } finally {
            SharedVideoOutput.frame.value = null
        }
    }
}
