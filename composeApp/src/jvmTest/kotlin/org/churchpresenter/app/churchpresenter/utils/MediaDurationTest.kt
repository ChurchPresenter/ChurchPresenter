package org.churchpresenter.app.churchpresenter.utils

import org.junit.Assume
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What a row's length is worked out from when nobody has typed one: ffmpeg's own report for a
 * clip, and arithmetic for a slideshow.
 */
class MediaDurationTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    private val report = """
        Input #0, mov,mp4,m4a,3gp,3g2,mj2, from '/clips/welcome.mp4':
          Metadata:
            major_brand     : isom
          Duration: 00:01:23.45, start: 0.000000, bitrate: 2044 kb/s
          Stream #0:0(und): Video: h264 (avc1 / 0x31637661), yuv420p, 1920x1080
    """.trimIndent()

    @Test
    fun `reads the duration ffmpeg prints`() {
        assertEquals(84, parseFfmpegDuration(report), "1:23.45 occupies 1:24 of a service")
    }

    @Test
    fun `a whole number of seconds is not rounded up`() {
        assertEquals(83, parseFfmpegDuration("  Duration: 00:01:23.00, start: 0.0"))
    }

    @Test
    fun `hours count`() {
        assertEquals(3723, parseFfmpegDuration("  Duration: 01:02:03.00, bitrate: 1 kb/s"))
    }

    @Test
    fun `a stream has no duration to report`() {
        assertNull(parseFfmpegDuration("  Duration: N/A, start: 0.000000, bitrate: N/A"))
        assertNull(parseFfmpegDuration("rtsp://camera.local: Invalid data found"))
    }

    @Test
    fun `nothing at all is not a duration of zero`() {
        assertNull(parseFfmpegDuration(""))
    }

    @Test
    fun `a slideshow is its count times its interval`() {
        assertEquals(100, slideshowSeconds(20, 5f), "20 pictures at 5s")
        assertEquals(45, slideshowSeconds(30, 1.5f))
    }

    @Test
    fun `an empty folder or a nonsense interval has no length`() {
        assertNull(slideshowSeconds(0, 5f))
        assertNull(slideshowSeconds(20, 0f))
        assertNull(slideshowSeconds(20, -1f))
    }

    @Test
    fun `a slideshow always takes some time`() {
        assertEquals(1, slideshowSeconds(1, 0.4f), "never zero -- that would claim it is instant")
    }

    // ── Asking ffmpeg ───────────────────────────────────────────────────────────

    @Test
    fun `a file that is not there has no duration`() {
        assertNull(mediaDurationSeconds(File(temp.root, "missing.mp4").path, ffmpeg = "ffmpeg"))
        assertNull(mediaDurationSeconds(temp.root.path, ffmpeg = "ffmpeg"), "a folder is not a clip")
    }

    @Test
    fun `an ffmpeg that cannot be run is not an error, just no duration`() {
        val clip = temp.newFile("clip.mp4")
        assertNull(mediaDurationSeconds(clip.path, ffmpeg = File(temp.root, "no-such-ffmpeg").path))
    }

    @Test
    fun `reads what the probe prints`() {
        Assume.assumeTrue("needs a shell script to stand in for ffmpeg", !isWindows)
        val clip = temp.newFile("clip.mp4")
        val probe = temp.newFile("ffmpeg").apply {
            writeText(
                "#!/bin/sh\necho 'Input #0, mov, from clip.mp4:'\necho '  Duration: 00:00:04.50, start: 0.0'\nexit 1\n",
            )
            setExecutable(true)
        }

        assertEquals(5, mediaDurationSeconds(clip.path, ffmpeg = probe.path), "4.5s occupies 5s")
    }
}
