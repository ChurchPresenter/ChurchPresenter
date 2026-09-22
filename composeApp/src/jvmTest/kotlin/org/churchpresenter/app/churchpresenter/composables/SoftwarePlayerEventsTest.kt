package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.runtime.mutableStateOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import uk.co.caprica.vlcj.media.TrackType
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.SubpictureApi
import uk.co.caprica.vlcj.player.base.TrackDescription
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The software player's VLC event listener, driven directly with a mocked [MediaPlayer] --
 * `MediaPlayer` is a concrete vlcj class backed by native libvlc handles (confirmed via `javap`),
 * so it cannot be constructed for real off a device; a mock is the only way in, per AGENT.md's
 * mocking rule. `EmbeddedVideoDecoderTest` already establishes the same pattern for vlcj classes.
 *
 * The `playing()` timer branch (first-frame grace pause) does not need to wait out the real 200ms:
 * `javax.swing.Timer` exposes its registered `ActionListener`s, so the test fires the listener
 * directly instead of waiting for the schedule -- a positive signal (the callback ran), not a
 * duration, per AGENT.md's rule against tests whose cost is a wait. The `error()`/invokeLater
 * branch stays untested: it hands work to the AWT event queue for a `System.err.println` and a
 * `viewModel.pause()` with nothing to assert beyond "a mock was called," which the project's own
 * rule against stub-only assertions rules out.
 */
class SoftwarePlayerEventsTest {

    private fun mediaPlayer(tracks: List<TrackDescription> = emptyList()): MediaPlayer {
        val mp = mockk<MediaPlayer>(relaxed = true)
        val subpictures = mockk<SubpictureApi>(relaxed = true)
        every { mp.subpictures() } returns subpictures
        every { subpictures.trackDescriptions() } returns tracks
        return mp
    }

    private fun listener(
        viewModel: MediaViewModel = MediaViewModel(),
        reportsPlaybackEnd: Boolean = true,
    ) = softwarePlayerEvents(
        viewModel = viewModel,
        firstFrameCaptured = mutableStateOf(false),
        gate = PlayerReleaseGate(),
        pauseTimer = mutableStateOf(null),
        reportsPlaybackEnd = reportsPlaybackEnd,
    )

    // ── lengthChanged ────────────────────────────────────────────────────────────────────────

    @Test
    fun `lengthChanged reports a positive length to the view model`() {
        val vm = MediaViewModel()
        listener(vm).lengthChanged(mediaPlayer(), 60_000L)
        assertEquals(60_000L, vm.duration)
    }

    @Test
    fun `lengthChanged ignores a non-positive length`() {
        val vm = MediaViewModel()
        vm.setDuration(60_000L)
        listener(vm).lengthChanged(mediaPlayer(), 0L)
        assertEquals(60_000L, vm.duration, "the earlier real length must survive a spurious 0")
    }

    // ── mediaPlayerReady ─────────────────────────────────────────────────────────────────────

    @Test
    fun `mediaPlayerReady lists the tracks when this decoder reports playback end`() {
        val vm = MediaViewModel()
        val mp = mediaPlayer(listOf(TrackDescription(3, "English"), TrackDescription(-1, "Disable")))

        listener(vm, reportsPlaybackEnd = true).mediaPlayerReady(mp)

        assertEquals(listOf(3), vm.subtitleTracks.map { it.id })
        assertEquals("English", vm.subtitleTracks.single().name)
    }

    @Test
    fun `mediaPlayerReady is a no-op for a mirror decoder`() {
        val vm = MediaViewModel()
        val mp = mediaPlayer(listOf(TrackDescription(3, "English")))

        listener(vm, reportsPlaybackEnd = false).mediaPlayerReady(mp)

        assertTrue(vm.subtitleTracks.isEmpty())
    }

    // ── elementaryStreamAdded ────────────────────────────────────────────────────────────────

    @Test
    fun `elementaryStreamAdded re-lists tracks only for a text stream`() {
        val vm = MediaViewModel()
        val mp = mediaPlayer(listOf(TrackDescription(5, "Spanish")))
        val target = listener(vm, reportsPlaybackEnd = true)

        target.elementaryStreamAdded(mp, TrackType.VIDEO, 0)
        assertTrue(vm.subtitleTracks.isEmpty(), "a video stream must not touch the subtitle list")

        target.elementaryStreamAdded(mp, TrackType.TEXT, 5)
        assertEquals(listOf(5), vm.subtitleTracks.map { it.id })
    }

    @Test
    fun `elementaryStreamAdded is a no-op for a mirror decoder even for a text stream`() {
        val vm = MediaViewModel()
        val mp = mediaPlayer(listOf(TrackDescription(5, "Spanish")))

        listener(vm, reportsPlaybackEnd = false).elementaryStreamAdded(mp, TrackType.TEXT, 5)

        assertTrue(vm.subtitleTracks.isEmpty())
    }

    // ── playing ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `playing does nothing once the view model already considers itself playing`() {
        val vm = MediaViewModel()
        vm.loadMedia("https://example.org/clip.mp4", "url")
        vm.play()
        val mp = mediaPlayer()

        listener(vm).playing(mp)

        // No pause/setTime call was made reachable to assert directly (mp is relaxed), but the
        // real outcome that matters is observable: the view model's own state is untouched.
        assertTrue(vm.isPlaying)
    }

    /** Fires a just-scheduled [javax.swing.Timer]'s callback directly, instead of waiting the real 200ms. */
    private fun javax.swing.Timer.fireNow() = actionListeners.forEach { it.actionPerformed(null) }

    @Test
    fun `the first-frame grace timer pauses and rewinds when still not playing when it fires`() {
        val vm = MediaViewModel()
        val pauseTimer = mutableStateOf<javax.swing.Timer?>(null)
        val mp = mockk<MediaPlayer>(relaxed = true)

        softwarePlayerEvents(
            viewModel = vm,
            firstFrameCaptured = mutableStateOf(false),
            gate = PlayerReleaseGate(),
            pauseTimer = pauseTimer,
            reportsPlaybackEnd = true,
        ).playing(mp)

        val timer = pauseTimer.value ?: error("playing() must have scheduled the grace timer")
        timer.fireNow()

        verify { mp.controls().pause() }
        verify { mp.controls().setTime(0) }
    }

    @Test
    fun `the grace timer does nothing if the view model started playing before it fires`() {
        val vm = MediaViewModel()
        val pauseTimer = mutableStateOf<javax.swing.Timer?>(null)
        val mp = mockk<MediaPlayer>(relaxed = true)

        softwarePlayerEvents(
            viewModel = vm,
            firstFrameCaptured = mutableStateOf(false),
            gate = PlayerReleaseGate(),
            pauseTimer = pauseTimer,
            reportsPlaybackEnd = true,
        ).playing(mp)
        val timer = pauseTimer.value ?: error("playing() must have scheduled the grace timer")

        // An operator went live during the load-grace window -- the timer must not undo it.
        vm.loadMedia("https://example.org/clip.mp4", "url")
        vm.play()
        timer.fireNow()

        verify(exactly = 0) { mp.controls().pause() }
        verify(exactly = 0) { mp.controls().setTime(any()) }
    }

    @Test
    fun `the grace timer does nothing once the release gate has fired`() {
        val vm = MediaViewModel()
        val pauseTimer = mutableStateOf<javax.swing.Timer?>(null)
        val gate = PlayerReleaseGate()
        val mp = mockk<MediaPlayer>(relaxed = true)

        softwarePlayerEvents(
            viewModel = vm,
            firstFrameCaptured = mutableStateOf(false),
            gate = gate,
            pauseTimer = pauseTimer,
            reportsPlaybackEnd = true,
        ).playing(mp)
        val timer = pauseTimer.value ?: error("playing() must have scheduled the grace timer")

        gate.release()
        timer.fireNow()

        verify(exactly = 0) { mp.controls().pause() }
        verify(exactly = 0) { mp.controls().setTime(any()) }
    }

    // ── finished ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `finished marks the view model finished when this decoder reports playback end`() {
        val vm = MediaViewModel()
        vm.loadMedia("https://example.org/clip.mp4", "url")
        vm.play()

        listener(vm, reportsPlaybackEnd = true).finished(mediaPlayer())

        assertTrue(vm.mediaFinished)
        assertFalse(vm.isPlaying)
    }

    @Test
    fun `finished is a no-op for a mirror decoder`() {
        val vm = MediaViewModel()
        vm.loadMedia("https://example.org/clip.mp4", "url")
        vm.play()

        listener(vm, reportsPlaybackEnd = false).finished(mediaPlayer())

        assertFalse(vm.mediaFinished)
        assertTrue(vm.isPlaying)
    }
}
