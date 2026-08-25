package org.churchpresenter.canvas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceEnumerationParsingTest {

    @Test
    fun `a v4l2-ctl listing pairs each frame rate with the size above it`() {
        val output = """
            Size: Discrete 1920x1080
                Interval: Discrete 0.033s (30.000 fps)
                Interval: Discrete 0.067s (15.000 fps)
            Size: Discrete 1280x720
                Interval: Discrete 0.017s (60.000 fps)
        """.trimIndent()

        val formats = parseV4l2CtlFormats(output)

        assertTrue(formats.any { it.width == 1920 && it.height == 1080 && it.fps == 30 })
        assertTrue(formats.any { it.width == 1920 && it.height == 1080 && it.fps == 15 })
        assertTrue(formats.any { it.width == 1280 && it.height == 720 && it.fps == 60 })
    }

    @Test
    fun `a frame rate before any size is discarded rather than attached to nothing`() {
        val output = """
            Interval: Discrete 0.033s (30.000 fps)
            Size: Discrete 640x480
                Interval: Discrete 0.033s (30.000 fps)
        """.trimIndent()

        val formats = parseV4l2CtlFormats(output)

        assertEquals(1, formats.size)
        assertEquals(640, formats.single().width)
    }

    @Test
    fun `a v4l2-ctl listing with no frame rates yields no formats`() {
        assertTrue(parseV4l2CtlFormats("Size: Discrete 1920x1080").isEmpty())
    }

    @Test
    fun `an empty v4l2-ctl listing yields no formats`() {
        assertTrue(parseV4l2CtlFormats("").isEmpty())
    }

    @Test
    fun `the same size and rate listed twice is offered once`() {
        val output = """
            Size: Discrete 1920x1080
                Interval: Discrete 0.033s (30.000 fps)
            Size: Discrete 1920x1080
                Interval: Discrete 0.033s (30.000 fps)
        """.trimIndent()

        assertEquals(1, parseV4l2CtlFormats(output).size)
    }

    @Test
    fun `ffmpeg's bracketed rate list is not read, so the size comes back at the default rate`() {
        val output = "[avfoundation] 1280x720@[30.000030 60.000000]fps"

        val format = parseAvfoundationFormats(output).single()

        assertEquals(1280, format.width)
        assertEquals(720, format.height)
        assertEquals(30, format.fps)
    }

    @Test
    fun `a rate written next to fps is read`() {
        val format = parseAvfoundationFormats("1280x720 60 fps").single()

        assertEquals(60, format.fps)
    }

    @Test
    fun `an avfoundation line with no rate falls back to thirty`() {
        val formats = parseAvfoundationFormats("[avfoundation] 1920x1080")

        assertEquals(30, formats.single().fps, "a camera that reports no rate is assumed to run at 30")
    }

    @Test
    fun `an avfoundation line with no size at all is skipped`() {
        assertTrue(parseAvfoundationFormats("[avfoundation] Supported modes:").isEmpty())
    }

    @Test
    fun `an empty avfoundation listing yields no formats`() {
        assertTrue(parseAvfoundationFormats("").isEmpty())
    }

    @Test
    fun `avfoundation formats come back largest first`() {
        val output = """
            640x480 30 fps
            1920x1080 30 fps
            1280x720 30 fps
        """.trimIndent()

        assertEquals(listOf(1920, 1280, 640), parseAvfoundationFormats(output).map { it.width })
    }

    @Test
    fun `a format names itself the way the operator reads it`() {
        val format = parseAvfoundationFormats("1280x720 60 fps").single()

        assertEquals("1280x720 @ 60fps", format.displayName)
        assertEquals("1280x720@60", format.encodedValue)
    }

    @Test
    fun `a wmctrl listing yields each window with its id and title`() {
        val output = """
            0x03400007  0 host Terminal
            0x0260000a  0 host Church Presenter
        """.trimIndent()

        val windows = parseWmctrlWindows(output)

        assertEquals(listOf("Terminal", "Church Presenter"), windows.map { it.title })
        assertEquals(0x03400007L, windows.first().id)
    }

    @Test
    fun `a wmctrl line with no title is dropped`() {
        assertTrue(parseWmctrlWindows("0x03400007  0 host    ").isEmpty())
    }

    @Test
    fun `a wmctrl line with too few fields is dropped`() {
        assertTrue(parseWmctrlWindows("0x03400007  0").isEmpty())
    }

    @Test
    fun `a wmctrl id that is not hexadecimal reads as zero rather than dropping the window`() {
        val windows = parseWmctrlWindows("notanid  0 host Terminal")

        assertEquals("Terminal", windows.single().title)
        assertEquals(0L, windows.single().id)
    }

    @Test
    fun `blank lines in a wmctrl listing are ignored`() {
        val output = "\n0x03400007  0 host Terminal\n\n"

        assertEquals(1, parseWmctrlWindows(output).size)
    }

    @Test
    fun `an empty wmctrl listing yields no windows`() {
        assertTrue(parseWmctrlWindows("").isEmpty())
    }

    @Test
    fun `a window title containing spaces is kept whole`() {
        val windows = parseWmctrlWindows("0x03400007  0 host My Long Window Title")

        assertEquals("My Long Window Title", windows.single().title)
    }
}
