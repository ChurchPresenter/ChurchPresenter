package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Audio/video transport state. The actual decoding lives in VLC; this holds what the UI shows and
 * what the player is told to do, so the interesting behaviour is the clamping (a seek past the end,
 * a volume above 1.0) and the `seekVersion` counter that tells the player a jump was requested
 * rather than merely reported.
 */
class MediaViewModelTest {

    private fun loaded(url: String = "/media/clip.mp4", type: String = Constants.MEDIA_TYPE_LOCAL) =
        MediaViewModel().also { it.loadMedia(url, type) }

    // ── Loading ─────────────────────────────────────────────────────────────────

    @Test
    fun `a new view model has nothing loaded`() {
        val vm = MediaViewModel()
        assertFalse(vm.isLoaded)
        assertFalse(vm.isPlaying)
        assertEquals("", vm.mediaUrl)
        assertEquals(0L, vm.currentPosition)
    }

    @Test
    fun `loading a file derives the title from its name`() {
        val vm = loaded("/media/Sunday Sermon.mp4")
        assertTrue(vm.isLoaded)
        assertEquals("Sunday Sermon.mp4", vm.mediaTitle, "a non-existent path falls back to the last segment")
        assertFalse(vm.isPlaying, "loading must not autoplay onto the output")
    }

    @Test
    fun `loading a network stream derives the title from the url`() {
        val vm = loaded("https://example.org/live/stream.m3u8", Constants.MEDIA_TYPE_URL)
        assertEquals("stream.m3u8", vm.mediaTitle)
        assertTrue(vm.isLoaded)
    }

    @Test
    fun `loading a blank url leaves nothing loaded`() {
        val vm = loaded("")
        assertFalse(vm.isLoaded, "an empty url must not count as loaded media")
    }

    @Test
    fun `a schedule item keeps its own title rather than deriving one`() {
        val vm = MediaViewModel()
        vm.loadMediaFromSchedule("/media/clip.mp4", "Offering Video", Constants.MEDIA_TYPE_LOCAL)
        assertEquals("Offering Video", vm.mediaTitle, "the operator's chosen title must win")
    }

    @Test
    fun `an audio file is recognised by extension as well as by declared type`() {
        assertTrue(loaded("/media/track.mp3").isAudioFile, "extension alone should be enough")
        assertTrue(loaded("/media/anything", Constants.MEDIA_TYPE_AUDIO).isAudioFile)
        assertFalse(loaded("/media/clip.mp4").isAudioFile)
    }

    @Test
    fun `loading new media resets the previous playback state`() {
        val vm = loaded()
        vm.setDuration(60_000)
        vm.play()
        vm.seekTo(30_000)

        vm.loadMedia("/media/other.mp4", Constants.MEDIA_TYPE_LOCAL)
        assertFalse(vm.isPlaying, "the previous clip must not keep playing")
        assertEquals(0L, vm.currentPosition, "position must not carry across clips")
        assertEquals(0L, vm.duration)
    }

    @Test
    fun `unloading clears everything`() {
        val vm = loaded()
        vm.setDuration(60_000)
        vm.play()

        vm.unload()
        assertFalse(vm.isLoaded)
        assertFalse(vm.isPlaying)
        assertEquals("", vm.mediaUrl)
        assertEquals("", vm.mediaTitle)
        assertEquals(0L, vm.duration)
    }

    // ── Transport ───────────────────────────────────────────────────────────────

    @Test
    fun `play and pause only apply once media is loaded`() {
        val empty = MediaViewModel()
        empty.play()
        empty.togglePlayPause()
        assertFalse(empty.isPlaying, "there is nothing to play")

        val vm = loaded()
        vm.play()
        assertTrue(vm.isPlaying)
        vm.pause()
        assertFalse(vm.isPlaying)
        vm.togglePlayPause()
        assertTrue(vm.isPlaying)
    }

    @Test
    fun `stop rewinds to the start and requests a seek`() {
        val vm = loaded()
        vm.setDuration(60_000)
        vm.play()
        vm.seekTo(30_000)
        val version = vm.seekVersion

        vm.stop()
        assertFalse(vm.isPlaying)
        assertEquals(0L, vm.currentPosition)
        assertTrue(vm.seekVersion > version, "the player must be told to jump, not just the UI")
    }

    @Test
    fun `finishing stops playback and rewinds`() {
        val vm = loaded()
        vm.setDuration(60_000)
        vm.play()
        vm.seekTo(59_000)

        vm.markFinished()
        assertTrue(vm.mediaFinished)
        assertFalse(vm.isPlaying)
        assertEquals(0L, vm.currentPosition)

        vm.clearFinished()
        assertFalse(vm.mediaFinished)
    }

    // ── Seeking ─────────────────────────────────────────────────────────────────

    @Test
    fun `seeking forward stops at the end of the clip`() {
        val vm = loaded()
        vm.setDuration(10_000)
        vm.seekTo(9_000)
        vm.seekForward(10_000)
        assertEquals(10_000L, vm.currentPosition, "must not seek past the end")
    }

    @Test
    fun `seeking backward stops at zero`() {
        val vm = loaded()
        vm.setDuration(10_000)
        vm.seekTo(2_000)
        vm.seekBackward(10_000)
        assertEquals(0L, vm.currentPosition, "must not seek to a negative position")
    }

    @Test
    fun `seeking forward before the duration is known does nothing`() {
        // VLC reports duration asynchronously; until then there is no end to clamp against.
        val vm = loaded()
        vm.seekForward(5_000)
        assertEquals(0L, vm.currentPosition)
    }

    @Test
    fun `seekTo clamps into the clip`() {
        val vm = loaded()
        vm.setDuration(10_000)
        vm.seekTo(99_000)
        assertEquals(10_000L, vm.currentPosition)
        vm.seekTo(-5_000)
        assertEquals(0L, vm.currentPosition)
    }

    @Test
    fun `every seek bumps the version but a reported position does not`() {
        val vm = loaded()
        vm.setDuration(60_000)
        val start = vm.seekVersion

        vm.seekTo(1_000)
        vm.seekForward(1_000)
        vm.seekBackward(500)
        assertEquals(start + 3, vm.seekVersion)

        vm.setCurrentPosition(42_000)
        assertEquals(start + 3, vm.seekVersion, "a progress report must not be mistaken for a seek")
        assertEquals(42_000L, vm.currentPosition)
    }

    // ── Volume ──────────────────────────────────────────────────────────────────

    @Test
    fun `volume is clamped to the valid range`() {
        val vm = loaded()
        vm.setVolume(2.5f)
        assertEquals(1f, vm.volume)
        vm.setVolume(-1f)
        assertEquals(0f, vm.volume)
    }

    @Test
    fun `raising the volume while muted unmutes`() {
        val vm = loaded()
        vm.toggleMute()
        assertTrue(vm.isMuted)

        vm.setVolume(0.5f)
        assertFalse(vm.isMuted, "reaching for the slider means the operator wants to hear it")
        assertEquals(0.5f, vm.volume)
    }

    @Test
    fun `setting the volume to zero while muted stays muted`() {
        val vm = loaded()
        vm.toggleMute()
        vm.setVolume(0f)
        assertTrue(vm.isMuted)
    }

    @Test
    fun `muting silences the effective volume without losing the setting`() {
        val vm = loaded()
        vm.setVolume(0.8f)
        assertEquals(0.8f, vm.effectiveVolume)

        vm.toggleMute()
        assertEquals(0f, vm.effectiveVolume, "muted output is silent")
        assertEquals(0.8f, vm.volume, "the remembered level must survive the mute")

        vm.toggleMute()
        assertEquals(0.8f, vm.effectiveVolume, "unmuting restores the previous level")
    }

    // ── Time formatting ─────────────────────────────────────────────────────────

    @Test
    fun `times under an hour omit the hour field`() {
        val vm = MediaViewModel()
        assertEquals("0:00", vm.formatTime(0))
        assertEquals("0:05", vm.formatTime(5_000))
        assertEquals("1:05", vm.formatTime(65_000))
        assertEquals("59:59", vm.formatTime(3_599_000))
    }

    @Test
    fun `times of an hour or more include it, zero-padded`() {
        val vm = MediaViewModel()
        assertEquals("1:00:00", vm.formatTime(3_600_000))
        assertEquals("1:02:03", vm.formatTime(3_723_000))
        assertEquals("10:00:00", vm.formatTime(36_000_000))
    }

    @Test
    fun `sub-second remainders are truncated, not rounded up`() {
        val vm = MediaViewModel()
        assertEquals("0:01", vm.formatTime(1_999), "1.999s is still in its first second")
    }
}
