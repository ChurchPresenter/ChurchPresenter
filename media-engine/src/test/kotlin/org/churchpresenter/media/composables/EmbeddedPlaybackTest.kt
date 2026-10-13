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
import uk.co.caprica.vlcj.media.TrackType
import uk.co.caprica.vlcj.player.base.AudioApi
import uk.co.caprica.vlcj.player.base.MediaApi
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.SubpictureApi
import uk.co.caprica.vlcj.player.base.TrackDescription
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmbeddedPlaybackTest {

    private val url = "https://example.org/clip.mp4"

    private fun player(tracks: List<TrackDescription> = emptyList()): Pair<MediaPlayer, MediaApi> {
        val mp = mockk<MediaPlayer>(relaxed = true)
        val subpictures = mockk<SubpictureApi>(relaxed = true)
        val media = mockk<MediaApi>(relaxed = true)
        every { mp.subpictures() } returns subpictures
        every { subpictures.trackDescriptions() } returns tracks
        every { mp.media() } returns media
        return mp to media
    }

    private fun track(id: Int, name: String) = mockk<TrackDescription> {
        every { id() } returns id
        every { description() } returns name
    }

    private fun events(
        vm: MediaViewModel,
        firstFrame: androidx.compose.runtime.MutableState<Boolean> = mutableStateOf(false),
        timer: androidx.compose.runtime.MutableState<javax.swing.Timer?> = mutableStateOf(null),
        gate: PlayerReleaseGate = PlayerReleaseGate(),
    ) = embeddedPlayerEvents(vm, firstFrame, gate, timer)

    private fun javax.swing.Timer.fireNow() = actionListeners.forEach { it.actionPerformed(null) }

    @Test
    fun `a positive length is reported and a non-positive one is not`() {
        val vm = MediaViewModel()
        val (mp, _) = player()
        events(vm).lengthChanged(mp, 0L)
        assertEquals(0L, vm.duration)
        events(vm).lengthChanged(mp, 42_000L)
        assertEquals(42_000L, vm.duration)
    }

    @Test
    fun `the tracks are listed once the media is ready, without VLC's own Disable entry`() {
        val vm = MediaViewModel()
        val (mp, _) = player(listOf(track(-1, "Disable"), track(2, "English")))
        events(vm).mediaPlayerReady(mp)
        assertEquals(listOf("English"), vm.subtitleTracks.map { it.name })
    }

    @Test
    fun `only a text stream re-lists the tracks`() {
        val vm = MediaViewModel()
        val (mp, _) = player(listOf(track(3, "Spanish")))
        events(vm).elementaryStreamAdded(mp, TrackType.AUDIO, 1)
        assertTrue(vm.subtitleTracks.isEmpty())
        events(vm).elementaryStreamAdded(mp, TrackType.TEXT, 3)
        assertEquals(listOf("Spanish"), vm.subtitleTracks.map { it.name })
    }

    @Test
    fun `playing while the view model is playing is left alone`() {
        val vm = MediaViewModel().apply { loadMedia(url, "url"); play() }
        val timer = mutableStateOf<javax.swing.Timer?>(null)
        val (mp, _) = player()
        events(vm, timer = timer).playing(mp)
        assertEquals(null, timer.value)
    }

    @Test
    fun `the first play of a load is paused and rewound after the grace window`() {
        val vm = MediaViewModel().apply { loadMedia(url, "url") }
        val timer = mutableStateOf<javax.swing.Timer?>(null)
        val (mp, _) = player()
        events(vm, timer = timer).playing(mp)
        val scheduled = timer.value ?: error("the grace timer was not scheduled")
        scheduled.stop()
        scheduled.fireNow()
        verify { mp.controls().pause() }
        verify { mp.controls().setTime(0) }
    }

    @Test
    fun `the grace timer leaves a clip the operator started alone`() {
        val vm = MediaViewModel().apply { loadMedia(url, "url") }
        val timer = mutableStateOf<javax.swing.Timer?>(null)
        val (mp, _) = player()
        events(vm, timer = timer).playing(mp)
        val scheduled = timer.value ?: error("the grace timer was not scheduled")
        scheduled.stop()
        vm.play()
        scheduled.fireNow()
        verify(exactly = 0) { mp.controls().setTime(any()) }
    }

    @Test
    fun `once a frame has been seen, a stray play is paused straight away`() {
        val vm = MediaViewModel().apply { loadMedia(url, "url") }
        val timer = mutableStateOf<javax.swing.Timer?>(null)
        val (mp, _) = player()
        events(vm, firstFrame = mutableStateOf(true), timer = timer).playing(mp)
        SwingUtilities.invokeAndWait { }
        assertEquals(null, timer.value)
        verify { mp.controls().pause() }
    }

    @Test
    fun `the end of the file finishes the clip`() {
        val vm = MediaViewModel().apply { loadMedia(url, "url"); play() }
        val (mp, _) = player()
        events(vm).finished(mp)
        assertTrue(vm.mediaFinished)
        assertFalse(vm.isPlaying)
    }

    @Test
    fun `a playback error pauses the clip`() {
        val vm = MediaViewModel().apply { loadMedia(url, "url"); play() }
        val (mp, _) = player()
        events(vm).error(mp)
        SwingUtilities.invokeAndWait { }
        assertFalse(vm.isPlaying)
    }

    @Test
    fun `a video output means the first frame is in`() {
        val firstFrame = mutableStateOf(false)
        val (mp, _) = player()
        events(MediaViewModel(), firstFrame = firstFrame).videoOutput(mp, 0)
        assertFalse(firstFrame.value)
        events(MediaViewModel(), firstFrame = firstFrame).videoOutput(mp, 1)
        assertTrue(firstFrame.value)
    }

    @Test
    fun `nothing loaded stops the player and opens nothing`() {
        val (mp, media) = player()
        loadEmbedded(mp, MediaViewModel(), audioEnabled = true)
        verify { mp.controls().stop() }
        verify(exactly = 0) { media.play(any<String>(), *anyVararg()) }
    }

    @Test
    fun `a load with sound sets the volume and stays muted until played`() {
        val (mp, media) = player()
        val vm = MediaViewModel().apply { loadMedia(url, "url"); audio.setVolume(0.3f) }
        loadEmbedded(mp, vm, audioEnabled = true)
        verify { mp.audio().setVolume(30) }
        verify { mp.audio().setMute(true) }
        verify { media.play(url) }
    }

    @Test
    fun `a load that is already playing is not muted`() {
        val (mp, _) = player()
        val vm = MediaViewModel().apply { loadMedia(url, "url"); play() }
        loadEmbedded(mp, vm, audioEnabled = true)
        verify { mp.audio().setMute(false) }
    }

    @Test
    fun `a silent load drops the audio track`() {
        val (mp, media) = player()
        val audio = mockk<AudioApi>(relaxed = true)
        every { mp.audio() } returns audio
        val vm = MediaViewModel().apply { loadMedia(url, "url") }
        loadEmbedded(mp, vm, audioEnabled = false)
        verify(exactly = 0) { audio.setVolume(any()) }
        verify { audio.setMute(true) }
        verify { media.play(url, ":no-audio") }
    }

    @Test
    fun `the player listens, loads, and is released when it leaves`() = runComposeUiTest {
        val (mp, media) = player()
        val vm = MediaViewModel().apply { loadMedia(url, "url") }
        var shown by mutableStateOf(true)
        var released = false
        setContent { if (shown) EmbeddedPlayback(vm, mp, audioEnabled = false, release = { released = true }) }
        waitForIdle()
        verify { mp.events().addMediaPlayerEventListener(any()) }
        verify { media.play(url, ":no-audio") }
        shown = false
        waitForIdle()
        assertTrue(released)
    }

    @Test
    fun `a loop restart opens the clip again`() = runComposeUiTest {
        val (mp, media) = player()
        val vm = MediaViewModel().apply { loadMedia(url, "url"); looping.setLooping(true); play() }
        setContent { EmbeddedPlayback(vm, mp, audioEnabled = false) }
        waitForIdle()
        vm.markFinished()
        waitForIdle()
        verify(exactly = 2) { media.play(url, ":no-audio") }
    }
}
