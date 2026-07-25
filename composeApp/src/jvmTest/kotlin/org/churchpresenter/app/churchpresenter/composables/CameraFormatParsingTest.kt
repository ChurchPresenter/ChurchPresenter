package org.churchpresenter.app.churchpresenter.composables

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Parsing camera resolutions/frame-rates out of the two Linux tools the app shells to, and the
 * ordering it presents them in. The line matching is fiddly — a size can appear with or without an
 * fps, `v4l2-ctl` puts the fps on lines after the size, and a wrong parse offers the operator a
 * resolution the camera can't do — so it's captured here from representative tool output.
 */
class CameraFormatParsingTest {

    // ── ffmpeg -list_formats ────────────────────────────────────────────────────

    @Test
    fun `ffmpeg lines yield their size, defaulting fps to 30 when absent`() {
        val out = """
            [video4linux2] Raw: yuyv422: 1920x1080 30 fps
            [video4linux2] Compressed: mjpeg: 1280x720 60 fps
            [video4linux2] Raw: yuyv422: 640x480
        """.trimIndent()
        assertEquals(
            setOf(Triple(1920, 1080, 30), Triple(1280, 720, 60), Triple(640, 480, 30)),
            parseFfmpegV4l2Formats(out),
        )
    }

    @Test
    fun `ffmpeg lines without a resolution are skipped`() =
        assertTrue(parseFfmpegV4l2Formats("Input #0, video4linux2\n  Stream mapping:\n").isEmpty())

    @Test
    fun `an empty ffmpeg output yields no formats`() =
        assertTrue(parseFfmpegV4l2Formats("").isEmpty())

    // ── v4l2-ctl --list-formats-ext ─────────────────────────────────────────────

    @Test
    fun `v4l2-ctl pairs each fps line with the most recent size`() {
        val out = """
            	Size: Discrete 1920x1080
            		Interval: Discrete 0.033s (30.000 fps)
            		Interval: Discrete 0.067s (15.000 fps)
            	Size: Discrete 1280x720
            		Interval: Discrete 0.033s (30.000 fps)
        """.trimIndent()
        assertEquals(
            setOf(Triple(1920, 1080, 30), Triple(1920, 1080, 15), Triple(1280, 720, 30)),
            parseV4l2CtlFormats(out),
        )
    }

    @Test
    fun `a v4l2-ctl fps line before any size is ignored`() =
        assertTrue(parseV4l2CtlFormats("Interval: Discrete 0.033s (30.000 fps)\n").isEmpty())

    // ── ordering ────────────────────────────────────────────────────────────────

    @Test
    fun `formats sort by area then by frame rate, both descending`() {
        val sorted = sortedCameraFormats(setOf(Triple(1280, 720, 30), Triple(1920, 1080, 30), Triple(1920, 1080, 60)))
        assertEquals(
            listOf(Triple(1920, 1080, 60), Triple(1920, 1080, 30), Triple(1280, 720, 30)),
            sorted.map { Triple(it.width, it.height, it.fps) },
            "largest area first, and within an area the higher fps first",
        )
    }
}
