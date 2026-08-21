package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaViewModelTransportTest {

    private lateinit var dir: File
    private lateinit var model: MediaViewModel

    @BeforeTest
    fun create() {
        dir = Files.createTempDirectory("cp-media-transport").toFile()
        model = MediaViewModel()
    }

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `an http url is named by its last path segment`() {
        assertEquals("sermon.mp4", model.deriveTitleFromUrl("https://example.org/media/sermon.mp4"))
    }

    @Test
    fun `a plain http url is named the same way as an https one`() {
        assertEquals("sermon.mp4", model.deriveTitleFromUrl("http://example.org/media/sermon.mp4"))
    }

    @Test
    fun `an rtsp stream is named by its last path segment`() {
        assertEquals("stream1", model.deriveTitleFromUrl("rtsp://camera.local/live/stream1"))
    }

    @Test
    fun `an rtp url is named by its last path segment`() {
        assertEquals("feed", model.deriveTitleFromUrl("rtp://239.0.0.1/feed"))
    }

    @Test
    fun `an mms url is named by its last path segment`() {
        assertEquals("feed", model.deriveTitleFromUrl("mms://server/feed"))
    }

    @Test
    fun `a udp url is named by its last path segment`() {
        assertEquals("feed", model.deriveTitleFromUrl("udp://239.0.0.1/feed"))
    }

    @Test
    fun `a url ending in a slash keeps the whole url as its name`() {
        assertEquals("https://example.org/", model.deriveTitleFromUrl("https://example.org/"))
    }

    @Test
    fun `a local file is named without its extension`() {
        val file = File(dir, "Opening Video.mp4").apply { writeBytes(ByteArray(4)) }

        assertEquals("Opening Video", model.deriveTitleFromUrl(file.absolutePath))
    }

    @Test
    fun `a local path that does not exist keeps its file name with the extension`() {
        val missing = File(dir, "gone.mp4").absolutePath

        assertEquals("gone.mp4", model.deriveTitleFromUrl(missing))
    }

    @Test
    fun `a bare name with no separators is its own title`() {
        assertEquals("clip.mp4", model.deriveTitleFromUrl("clip.mp4"))
    }

    @Test
    fun `loading a video marks it loaded, stopped and not audio`() {
        model.loadMedia("https://example.org/sermon.mp4", Constants.MEDIA_TYPE_LOCAL)

        assertTrue(model.isLoaded)
        assertFalse(model.isPlaying)
        assertFalse(model.isAudioFile)
        assertEquals(0L, model.currentPosition)
        assertEquals("sermon.mp4", model.mediaTitle)
    }

    @Test
    fun `a file with an audio extension is recognised as audio whatever its type`() {
        model.loadMedia("https://example.org/track.mp3", Constants.MEDIA_TYPE_LOCAL)

        assertTrue(model.isAudioFile)
    }

    @Test
    fun `a declared audio type is recognised even when the extension does not say so`() {
        model.loadMedia("rtsp://camera/live", Constants.MEDIA_TYPE_AUDIO)

        assertTrue(model.isAudioFile)
    }

    @Test
    fun `loading a blank url leaves nothing loaded`() {
        model.loadMedia("", Constants.MEDIA_TYPE_LOCAL)

        assertFalse(model.isLoaded)
    }

    @Test
    fun `a schedule item keeps the title the operator gave it`() {
        model.loadMediaFromSchedule("https://example.org/sermon.mp4", "Week 3 sermon", Constants.MEDIA_TYPE_LOCAL)

        assertEquals("Week 3 sermon", model.mediaTitle, "the schedule's own label must win over the file name")
        assertTrue(model.isLoaded)
    }

    @Test
    fun `a schedule item with an audio extension is recognised as audio`() {
        model.loadMediaFromSchedule("/music/prelude.wav", "Prelude", Constants.MEDIA_TYPE_LOCAL)

        assertTrue(model.isAudioFile)
    }

    @Test
    fun `a schedule item with a blank url leaves nothing loaded`() {
        model.loadMediaFromSchedule("", "Nothing", Constants.MEDIA_TYPE_LOCAL)

        assertFalse(model.isLoaded)
    }

    @Test
    fun `nothing loaded means play does nothing`() {
        model.play()
        model.togglePlayPause()

        assertFalse(model.isPlaying)
    }

    @Test
    fun `play and pause toggle once something is loaded`() {
        model.loadMedia("https://example.org/sermon.mp4", Constants.MEDIA_TYPE_LOCAL)

        model.togglePlayPause()
        assertTrue(model.isPlaying)

        model.togglePlayPause()
        assertFalse(model.isPlaying)
    }

    @Test
    fun `stopping rewinds to the start and asks the player to seek`() {
        model.loadMedia("https://example.org/sermon.mp4", Constants.MEDIA_TYPE_LOCAL)
        model.setDuration(60_000L)
        model.setCurrentPosition(30_000L)
        val before = model.seekVersion

        model.stop()

        assertEquals(0L, model.currentPosition)
        assertFalse(model.isPlaying)
        assertEquals(before + 1, model.seekVersion)
    }

    @Test
    fun `seeking past the end stops at the end`() {
        model.setDuration(60_000L)

        model.seekTo(90_000L)

        assertEquals(60_000L, model.currentPosition)
    }

    @Test
    fun `seeking before the start stops at the start`() {
        model.setDuration(60_000L)

        model.seekTo(-5_000L)

        assertEquals(0L, model.currentPosition)
    }

    @Test
    fun `seeking with no known duration is not clamped to zero`() {
        model.seekTo(30_000L)

        assertEquals(30_000L, model.currentPosition, "a live stream reports no duration but still seeks")
    }

    @Test
    fun `seeking forward stops at the end`() {
        model.setDuration(15_000L)
        model.setCurrentPosition(10_000L)

        model.seekForward()

        assertEquals(15_000L, model.currentPosition)
    }

    @Test
    fun `seeking forward does nothing with no known duration`() {
        model.setCurrentPosition(5_000L)
        val before = model.seekVersion

        model.seekForward()

        assertEquals(5_000L, model.currentPosition)
        assertEquals(before, model.seekVersion)
    }

    @Test
    fun `seeking backward stops at the start`() {
        model.setDuration(60_000L)
        model.setCurrentPosition(4_000L)

        model.seekBackward()

        assertEquals(0L, model.currentPosition)
    }

    @Test
    fun `unloading clears everything and asks the player to reset`() {
        model.loadMedia("https://example.org/track.mp3", Constants.MEDIA_TYPE_LOCAL)
        model.setDuration(60_000L)
        model.setCurrentPosition(30_000L)
        val before = model.seekVersion

        model.unload()

        assertFalse(model.isLoaded)
        assertEquals("", model.mediaUrl)
        assertEquals("", model.mediaTitle)
        assertFalse(model.isAudioFile)
        assertEquals(0L, model.duration)
        assertEquals(before + 1, model.seekVersion)
    }

    @Test
    fun `the volume is held between silence and full`() {
        model.setVolume(2f)
        assertEquals(1f, model.volume)

        model.setVolume(-1f)
        assertEquals(0f, model.volume)
    }

    @Test
    fun `raising the volume takes the mute off`() {
        model.toggleMute()
        assertTrue(model.isMuted)

        model.setVolume(0.5f)

        assertFalse(model.isMuted)
    }

    @Test
    fun `setting the volume to silence leaves the mute alone`() {
        model.toggleMute()

        model.setVolume(0f)

        assertTrue(model.isMuted)
    }

    @Test
    fun `the position reported by the player does not count as a seek`() {
        val before = model.seekVersion

        model.setCurrentPosition(1_234L)

        assertEquals(1_234L, model.currentPosition)
        assertEquals(before, model.seekVersion)
    }

    @Test
    fun `a time under an hour is minutes and seconds`() {
        assertEquals("3:05", model.formatTime(185_000L))
    }

    @Test
    fun `a time over an hour carries the hour`() {
        assertEquals("1:02:03", model.formatTime(3_723_000L))
    }

    @Test
    fun `the start of a clip formats as zero`() {
        assertEquals("0:00", model.formatTime(0L))
    }
}
