package org.churchpresenter.app.churchpresenter.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What a row's length is worked out from when nobody has typed one: ffmpeg's own report for a
 * clip, and arithmetic for a slideshow.
 */
class MediaDurationTest {

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
}
