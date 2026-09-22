package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.runtime.mutableStateOf
import io.mockk.every
import io.mockk.mockk
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
 * The `playing()` timer branch (first-frame grace pause) and the `error()`/invokeLater branch stay
 * untested here: both hand work to a real `javax.swing.Timer` or the AWT event queue, and AGENT.md
 * rules out a test whose cost is a duration rather than the work itself. Only the early-return
 * branch of `playing()` — reached without touching either — is covered.
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
