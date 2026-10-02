package org.churchpresenter.media.composables

import androidx.compose.runtime.mutableStateOf
import io.mockk.mockk
import io.mockk.verify
import org.churchpresenter.media.viewmodel.MediaViewModel
import uk.co.caprica.vlcj.player.base.MediaPlayer
import java.nio.file.Files
import javax.swing.SwingUtilities
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class PlayerEventsEdgeTest {

    private val url = "https://example.org/clip.mp4"

    private fun loaded() = MediaViewModel().apply { loadMedia(url, "url") }

    @Test
    fun `a software playback error pauses the clip`() {
        val vm = loaded().apply { play() }
        softwarePlayerEvents(vm, mutableStateOf(false), PlayerReleaseGate(), mutableStateOf(null), true)
            .error(mockk(relaxed = true))
        SwingUtilities.invokeAndWait { }
        assertFalse(vm.isPlaying)
    }

    @Test
    fun `a stray software play after the first frame is paused straight away`() {
        val mp = mockk<MediaPlayer>(relaxed = true)
        val timer = mutableStateOf<javax.swing.Timer?>(null)
        softwarePlayerEvents(loaded(), mutableStateOf(true), PlayerReleaseGate(), timer, true).playing(mp)
        SwingUtilities.invokeAndWait { }
        verify { mp.controls().pause() }
        assertTrue(timer.value == null)
    }

    @Test
    fun `a second software grace window replaces the first`() {
        val timer = mutableStateOf<javax.swing.Timer?>(null)
        val events = softwarePlayerEvents(loaded(), mutableStateOf(false), PlayerReleaseGate(), timer, true)
        events.playing(mockk(relaxed = true))
        val first = timer.value!!
        events.playing(mockk(relaxed = true))
        assertNotSame(first, timer.value)
        assertFalse(first.isRunning)
        timer.value?.stop()
    }

    @Test
    fun `a second embedded grace window replaces the first`() {
        val timer = mutableStateOf<javax.swing.Timer?>(null)
        val events = embeddedPlayerEvents(loaded(), mutableStateOf(false), PlayerReleaseGate(), timer)
        events.playing(mockk(relaxed = true))
        val first = timer.value!!
        events.playing(mockk(relaxed = true))
        assertNotSame(first, timer.value)
        assertFalse(first.isRunning)
        timer.value?.stop()
    }

    @Test
    fun `libvlc is recognized under each platform's name, and libvlccore is not libvlc`() {
        listOf("libvlc.dll", "libvlc.dylib", "libvlc.so", "libvlc.so.5").forEach { name ->
            val dir = Files.createTempDirectory("cp-vlc-lib")
            dir.resolve(name).createFile()
            assertTrue(dirContainsVlcLib(dir), name)
            dir.toFile().deleteRecursively()
        }
        val core = Files.createTempDirectory("cp-vlc-core")
        core.resolve("libvlccore.so.9").createFile()
        assertFalse(dirContainsVlcLib(core))
        core.toFile().deleteRecursively()
    }

    @Test
    fun `macOS reported as darwin is still searched as a mac`() {
        val found = detectVlcInstallPathFor("darwin")
        assertTrue(found.isBlank() || "VLC.app" in found)
    }

    @Test
    fun `a mac with no VLC does not fall back to which`() {
        var asked = false
        vlcInstalledOn("darwin", "", { _, _ -> asked = true; org.churchpresenter.sharedui.utils.CommandResult(0, "") })
        assertFalse(asked)
    }
}
