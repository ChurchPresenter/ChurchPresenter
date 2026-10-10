package org.churchpresenter.canvas

import org.churchpresenter.sharedui.utils.CommandResult
import org.churchpresenter.sharedui.utils.CommandRunner
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptureParsingEdgeCasesTest {

    private val overflow = "99999999999"

    // ── dshow / v4l2 format listings ──────────────────────────────────────────

    @Test
    fun `a dshow size too large to read adds no format`() {
        assertTrue(parseDshowFormats("  pixel_format=yuyv422  min s=${overflow}x720 fps=30").isEmpty())
        assertTrue(parseDshowFormats("  pixel_format=yuyv422  min s=1280x$overflow fps=30").isEmpty())
    }

    @Test
    fun `a dshow rate too large to read is skipped while the readable one is kept`() {
        assertEquals(
            listOf(CameraFormat(1280, 720, 30)),
            parseDshowFormats("  pixel_format=yuyv422  min s=1280x720 fps=$overflow max s=1280x720 fps=30"),
        )
    }

    @Test
    fun `a v4l2-ctl rate under a zero-sized entry is ignored`() {
        val listing = """
            Size: Discrete 000x720
                Interval: Discrete 0.033s (30.000 fps)
            Size: Discrete 1280x000
                Interval: Discrete 0.033s (30.000 fps)
            Size: Discrete 640x480
                Interval: Discrete 0.033s (30.000 fps)
        """.trimIndent()
        assertEquals(listOf(CameraFormat(640, 480, 30)), parseV4l2CtlFormats(listing))
    }

    // ── What the device says it accepts ───────────────────────────────────────

    @Test
    fun `pixel format entries that are blank or not plain names are dropped`() {
        val tail = listOf(
            "[avfoundation @ 0x1] Supported pixel formats:",
            "[avfoundation @ 0x1]   uyvy422,  nv12 (tv)",
            "[avfoundation @ 0x1]   bgr0",
            "Error opening input",
        )
        assertEquals(listOf("uyvy422", "nv12", "bgr0"), parseSupportedPixelFormats(tail))
    }

    @Test
    fun `a zero frame rate is not one to ask for`() {
        assertEquals(
            listOf(30.0),
            parseSupportedFramerates(listOf("Supported framerates:", "  0.000000 30.000000")),
        )
    }

    @Test
    fun `a refused pixel format with nothing listed has no next attempt`() {
        assertNull(
            nextCaptureOverride(
                CameraFailure.UNSUPPORTED_PIXEL_FORMAT,
                listOf("Selected pixel format (yuv420p) is not supported by the input device."),
                emptyList(),
                emptySet(),
            )
        )
    }

    @Test
    fun `a refused frame rate with no rate listed and no known format has no next attempt`() {
        assertNull(
            nextCaptureOverride(
                CameraFailure.UNSUPPORTED_FRAMERATE,
                listOf("Selected framerate (29.970030) is not supported by the device."),
                emptyList(),
                emptySet(),
            )
        )
    }

    @Test
    fun `a refused frame rate takes the first rate the device lists over a known format`() {
        assertEquals(
            CaptureOverride(framerate = "60"),
            nextCaptureOverride(
                CameraFailure.UNSUPPORTED_FRAMERATE,
                listOf("Supported framerates:", "  60.000000"),
                listOf(CameraFormat(1920, 1080, 25)),
                emptySet(),
            )
        )
    }

    // ── AVFoundation device listings ──────────────────────────────────────────

    @Test
    fun `a device listed twice by index is offered once`() {
        val listing = """
            [AVFoundation indev @ 0x1] AVFoundation video devices:
            [AVFoundation indev @ 0x1] [0] FaceTime HD Camera
            [AVFoundation indev @ 0x1] [1] facetime hd camera
            [AVFoundation indev @ 0x1] not a device line
            [AVFoundation indev @ 0x1] [2] Capture screen 0
            [AVFoundation indev @ 0x1] AVFoundation audio devices:
            [AVFoundation indev @ 0x1] [0] MacBook Pro Microphone
        """.trimIndent()
        assertEquals(
            listOf("avfoundation://0" to "FaceTime HD Camera", "avfoundation://2" to "Capture screen 0"),
            parseMacCameras("", listing).map { it.path to it.name },
        )
    }

    @Test
    fun `a device listed twice in the quoted shape is offered once`() {
        val listing = """
            "USB Camera" (video)
            "usb camera" (video)
            "Other Camera" (video)
        """.trimIndent()
        assertEquals(
            listOf("avfoundation://0" to "USB Camera", "avfoundation://1" to "Other Camera"),
            parseMacCameras("", listing).map { it.path to it.name },
        )
    }

    @Test
    fun `a dshow device listed twice is offered once`() {
        val dshow = """
            [dshow @ 0x1] DirectShow video devices
            [dshow @ 0x1]  "USB Camera"
            [dshow @ 0x1]  "usb camera"
            [dshow @ 0x1]     Alternative name "@device_pnp_x"
            [dshow @ 0x1] DirectShow audio devices
            [dshow @ 0x1]  "Microphone"
        """.trimIndent()
        val names = parseWindowsCameras(dshow, "").map { it.name }
        assertEquals(1, names.count { it.equals("USB Camera", ignoreCase = true) })
        assertTrue("Microphone" !in names, "audio devices are never cameras")
    }

    // ── ffmpeg's stream announcement ──────────────────────────────────────────

    @Test
    fun `a stream announcement with a zero dimension is not a size`() {
        assertNull(parseFfmpegVideoDimensions("Stream #0:0: Video: rawvideo (BGRA / 0x41524742), bgra, 00x720, 30 fps"))
        assertNull(parseFfmpegVideoDimensions("Stream #0:0: Video: rawvideo (BGRA / 0x41524742), bgra, 1280x00, 30 fps"))
        assertNull(parseFfmpegVideoDimensions("Stream #0:0: Video: rawvideo, bgra, 30 fps"))
    }

    // ── Window geometry ───────────────────────────────────────────────────────

    @Test
    fun `xwininfo lines that cannot be read count as zero`() {
        val report = """
            Absolute upper-left X:  10
            Absolute upper-left Y:  ?
            Width: 800
            Height: 600
        """.trimIndent()
        assertEquals(Rectangle(10, 0, 800, 600), parseXwininfoBounds(report))
        assertNull(parseXwininfoBounds("Width: wide\nHeight: 600"))
        assertNull(parseXwininfoBounds("Width: 800\nHeight: tall"))
    }

    @Test
    fun `a mac window with no height is not a window to capture`() {
        val run: CommandRunner = { _, _ -> CommandResult(0, "0,0,800,0") }
        assertNull(macWindowBoundsFrom("Slides", run))
    }

    @Test
    fun `a Windows window lookup off Windows finds nothing`() {
        assertNull(windowBoundsFor("windows 11", "Slides") { _, _ -> CommandResult(0, "") })
    }

    @Test
    fun `an X11 window whose name is blank is not offered`() {
        assertNull(xpropWindow("0x1", "_NET_WM_NAME(UTF8_STRING) = \" \""))
    }

    @Test
    fun `an X11 listing of nameless windows falls back to wmctrl`() {
        val run: CommandRunner = { command, _ ->
            when {
                command == listOf("xprop", "-root", "_NET_CLIENT_LIST_STACKING") ->
                    CommandResult(0, "_NET_CLIENT_LIST_STACKING(WINDOW): window id # 0x1")
                command.first() == "xprop" -> CommandResult(0, "_NET_WM_NAME(UTF8_STRING) = \"   \"")
                else -> CommandResult(0, "0x02 0 host Slides")
            }
        }
        assertEquals(listOf(WindowInfo("Slides", 2L)), linuxWindowsFrom(run))
    }

    // ── Preview shape labels ──────────────────────────────────────────────────

    @Test
    fun `a ratio stored at another height is labelled by its exact size`() {
        assertEquals("1000×700", previewShapeLabel(1000, 700))
        assertEquals("1×1080", previewShapeLabel(1, 1080), "a ratio too lopsided to read as one")
    }

    @Test
    fun `an empty size reduces to the default ratio`() {
        assertEquals(reducedRatio(0, 0), reducedRatio(1920, 0))
        assertEquals(reducedRatio(0, 0), reducedRatio(-1, 1080))
    }
}
