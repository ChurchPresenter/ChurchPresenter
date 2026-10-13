@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.media.composables

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.v2.runComposeUiTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.churchpresenter.media.viewmodel.MediaViewModel
import uk.co.caprica.vlcj.player.base.ControlsApi
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.StatusApi
import java.io.File
import java.nio.file.Files
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackSyncTest {

    private val dir: File = Files.createTempDirectory("cp-playback-sync").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun loaded() = MediaViewModel().apply { loadMedia("https://example.org/clip.mp4", "url") }

    private fun flushSwing() = SwingUtilities.invokeAndWait { }

    @Test
    fun `a file that exists is opened by its absolute path`() {
        val clip = File(dir, "clip.mp4").apply { writeText("x") }
        assertEquals(clip.absolutePath, mediaResourceLocator(clip.path))
    }

    @Test
    fun `a stream or a missing file is opened as given`() {
        assertEquals("rtsp://camera/1", mediaResourceLocator("rtsp://camera/1"))
        val missing = File(dir, "gone.mp4").path
        assertEquals(missing, mediaResourceLocator(missing))
    }

    @Test
    fun `the volume goes to VLC as a percentage`() {
        val mp = mockk<MediaPlayer>(relaxed = true)
        mp.applyVolume(0.42f)
        verify { mp.audio().setVolume(42) }
    }

    @Test
    fun `playing lifts the mute and plays`() {
        val mp = mockk<MediaPlayer>(relaxed = true)
        mp.applyPlaying(playing = true, audioEnabled = true)
        verify { mp.audio().setMute(false) }
        verify { mp.controls().play() }
    }

    @Test
    fun `pausing mutes and pauses`() {
        val mp = mockk<MediaPlayer>(relaxed = true)
        mp.applyPlaying(playing = false, audioEnabled = true)
        verify { mp.audio().setMute(true) }
        verify { mp.controls().pause() }
    }

    @Test
    fun `a silent player is never unmuted or muted`() {
        val mp = mockk<MediaPlayer>(relaxed = true)
        mp.applyPlaying(playing = true, audioEnabled = false)
        mp.applyPlaying(playing = false, audioEnabled = false)
        verify(exactly = 0) { mp.audio().setMute(any()) }
        verify { mp.controls().play() }
        verify { mp.controls().pause() }
    }

    @Test
    fun `a poll while VLC is not playing changes nothing`() {
        val vm = loaded()
        val mp = mockk<MediaPlayer>(relaxed = true)
        every { mp.status().isPlaying } returns false
        every { mp.status().time() } returns 5_000L
        pollPlayback(mp, vm)
        assertEquals(0L, vm.currentPosition)
    }

    @Test
    fun `a poll while playing takes the position and a missed length`() {
        val vm = loaded()
        val mp = mockk<MediaPlayer>(relaxed = true)
        every { mp.status().isPlaying } returns true
        every { mp.status().time() } returns 5_000L
        every { mp.status().length() } returns 60_000L
        pollPlayback(mp, vm)
        assertEquals(5_000L, vm.currentPosition)
        assertEquals(60_000L, vm.duration)
    }

    @Test
    fun `a known length is not asked for again`() {
        val vm = loaded().apply { position.setDuration(30_000L) }
        val mp = mockk<MediaPlayer>(relaxed = true)
        val status = mockk<StatusApi>(relaxed = true)
        every { mp.status() } returns status
        every { status.isPlaying } returns true
        every { status.time() } returns 1_000L
        pollPlayback(mp, vm)
        assertEquals(30_000L, vm.duration)
        verify(exactly = 0) { status.length() }
    }

    @Test
    fun `a length VLC does not know yet is not taken`() {
        val vm = loaded()
        val mp = mockk<MediaPlayer>(relaxed = true)
        every { mp.status().isPlaying } returns true
        every { mp.status().length() } returns 0L
        pollPlayback(mp, vm)
        assertEquals(0L, vm.duration)
    }

    @Test
    fun `releasing stops the player, then frees it`() {
        val mp = mockk<MediaPlayer>(relaxed = true)
        val gate = PlayerReleaseGate()
        val timer = javax.swing.Timer(10_000) {}.apply { start() }
        var released = false
        stopAndRelease(gate, mutableStateOf(timer), mp) { released = true }
        assertFalse(timer.isRunning)
        assertTrue(released)
        var ran = false
        gate.ifLive { ran = true }
        assertFalse(ran)
        verify { mp.controls().stop() }
    }

    @Test
    fun `a release that throws does not escape`() {
        val mp = mockk<MediaPlayer>(relaxed = true)
        stopAndRelease(PlayerReleaseGate(), mutableStateOf(null), mp) { error("native handle gone") }
        verify { mp.controls().stop() }
    }

    @Test
    fun `no device leaves VLC on the system default`() = runComposeUiTest {
        val mp = mockk<MediaPlayer>(relaxed = true)
        setContent { AudioOutputDevice(mp, "") }
        waitForIdle()
        verify(exactly = 0) { mp.audio().setOutputDevice(any(), any()) }
    }

    @Test
    fun `a chosen device is handed to VLC`() = runComposeUiTest {
        val mp = mockk<MediaPlayer>(relaxed = true)
        setContent { AudioOutputDevice(mp, "hdmi-1") }
        waitForIdle()
        verify { mp.audio().setOutputDevice(null, "hdmi-1") }
    }

    @Test
    fun `play and pause in the view model reach the player`() = runComposeUiTest {
        val vm = loaded()
        val mp = mockk<MediaPlayer>(relaxed = true)
        setContent { PlaybackSync(vm, mp, PlayerReleaseGate(), audioEnabled = false) }
        waitForIdle()
        flushSwing()
        verify { mp.controls().pause() }
        vm.play()
        waitForIdle()
        flushSwing()
        verify { mp.controls().play() }
    }

    @Test
    fun `nothing reaches a released player`() = runComposeUiTest {
        val vm = loaded().apply { play() }
        val mp = mockk<MediaPlayer>(relaxed = true)
        val controls = mockk<ControlsApi>(relaxed = true)
        every { mp.controls() } returns controls
        val gate = PlayerReleaseGate().apply { release() }
        setContent { PlaybackSync(vm, mp, gate, audioEnabled = false) }
        waitForIdle()
        flushSwing()
        verify(exactly = 0) { controls.play() }
        verify(exactly = 0) { controls.pause() }
    }

    @Test
    fun `volume changes reach a player with sound`() = runComposeUiTest {
        val vm = loaded()
        val mp = mockk<MediaPlayer>(relaxed = true)
        var audible by mutableStateOf(true)
        setContent { if (audible) PlaybackSync(vm, mp, PlayerReleaseGate(), audioEnabled = true) }
        waitForIdle()
        vm.audio.setVolume(0.5f)
        waitForIdle()
        verify { mp.audio().setVolume(50) }
        audible = false
        waitForIdle()
    }

    @Test
    fun `a silent player is never given a volume`() = runComposeUiTest {
        val vm = loaded()
        val mp = mockk<MediaPlayer>(relaxed = true)
        setContent { PlaybackSync(vm, mp, PlayerReleaseGate(), audioEnabled = false) }
        waitForIdle()
        vm.audio.setVolume(0.5f)
        waitForIdle()
        verify(exactly = 0) { mp.audio().setVolume(any()) }
    }

    @Test
    fun `a seek in the view model moves the player`() = runComposeUiTest {
        val vm = loaded().apply { position.setDuration(60_000L) }
        val mp = mockk<MediaPlayer>(relaxed = true)
        setContent { PlaybackSync(vm, mp, PlayerReleaseGate(), audioEnabled = false) }
        waitForIdle()
        vm.position.seekTo(12_000L)
        waitForIdle()
        verify { mp.controls().setTime(12_000L) }
    }
}
