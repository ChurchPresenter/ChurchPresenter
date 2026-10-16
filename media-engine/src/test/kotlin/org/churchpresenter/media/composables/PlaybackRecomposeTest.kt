@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.media.composables

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.v2.runComposeUiTest
import io.mockk.mockk
import io.mockk.verify
import org.churchpresenter.media.viewmodel.MediaViewModel
import uk.co.caprica.vlcj.player.base.MediaPlayer
import javax.swing.SwingUtilities
import kotlin.test.Test

class PlaybackRecomposeTest {

    private val url = "https://example.org/clip.mp4"

    private fun loaded() = MediaViewModel().apply { loadMedia(url, "url") }

    private fun flushSwing() = SwingUtilities.invokeAndWait { }

    @Test
    fun `a player swapped in takes the next play`() = runComposeUiTest {
        val vm = loaded()
        val first = mockk<MediaPlayer>(relaxed = true)
        val second = mockk<MediaPlayer>(relaxed = true)
        var mp by mutableStateOf(first)
        var gate by mutableStateOf(PlayerReleaseGate())
        var audible by mutableStateOf(false)
        var device by mutableStateOf("")
        setContent {
            AudioOutputDevice(mp, device)
            PlaybackSync(vm, mp, gate, audible)
            SubtitleTrackSync(vm, mp, gate)
        }
        waitForIdle()
        mp = second
        gate = PlayerReleaseGate()
        audible = true
        device = "usb"
        waitForIdle()
        vm.play()
        vm.audio.setVolume(0.6f)
        vm.subtitles.selectSubtitleTrack(2)
        vm.position.setDuration(10_000L)
        vm.position.seekTo(3_000L)
        waitForIdle()
        flushSwing()
        verify { second.controls().play() }
        verify { second.audio().setVolume(60) }
        verify { second.audio().setOutputDevice(null, "usb") }
        verify { second.subpictures().setTrack(2) }
        verify { second.controls().setTime(3_000L) }
        audible = false
        waitForIdle()
    }

    @Test
    fun `the embedded player keeps working across a recomposition with new settings`() = runComposeUiTest {
        val vm = loaded()
        val mp = mockk<MediaPlayer>(relaxed = true)
        var device by mutableStateOf("")
        var released by mutableStateOf(0)
        setContent { EmbeddedPlayback(vm, mp, audioEnabled = false, audioDeviceId = device, release = { released++ }) }
        waitForIdle()
        device = "hdmi"
        waitForIdle()
        vm.loadMedia("https://example.org/next.mp4", "url")
        waitForIdle()
        verify { mp.audio().setOutputDevice(null, "hdmi") }
        verify { mp.media().play("https://example.org/next.mp4", ":no-audio") }
    }

    @Test
    fun `the software player keeps working across a recomposition with new settings`() = runComposeUiTest {
        val vm = loaded()
        val mp = mockk<MediaPlayer>(relaxed = true)
        val firstFrame = mutableStateOf(false)
        var device by mutableStateOf("")
        var reports by mutableStateOf(true)
        setContent {
            SoftwarePlayback(
                vm, mp, firstFrame, audioEnabled = false, audioDeviceId = device, reportsPlaybackEnd = reports,
            )
        }
        waitForIdle()
        device = "hdmi"
        reports = false
        waitForIdle()
        vm.loadMedia("https://example.org/next.mp4", "url")
        waitForIdle()
        verify { mp.audio().setOutputDevice(null, "hdmi") }
        verify { mp.media().play("https://example.org/next.mp4", *anyVararg()) }
    }
}
