package org.churchpresenter.app.churchpresenter.composables

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The decisions the camera capture loop makes about a failure, tested as the pure functions they
 * are: classifying ffmpeg's stderr, reading what the device says it accepts, choosing the next
 * attempt, and redacting what goes to Sentry.
 *
 * The fixtures are real ffmpeg output. Two of these shapes — a device refusing the pixel format,
 * and macOS refusing on privacy grounds — are what issue #431 turned out to be about, and the app
 * could not tell them apart because it printed the answer to a `System.err` that a packaged `.app`
 * discards.
 *
 * Not covered here, because each needs a real process, a real device or a signed bundle: the
 * stderr drain, the retry loop that consumes these decisions, the `CrashReporter` call, and the
 * macOS entitlements. That is the deliberate split — the decisions are pure and tested, and the one
 * unreachable step (`ProcessBuilder.start()`) stays uncovered.
 */
class CameraDiagnosticsTest {

    private val unsupportedPixelFormat = listOf(
        "[AVFoundation indev @ 0x7fb2] Selected pixel format (yuv420p) is not supported by the input device.",
        "[AVFoundation indev @ 0x7fb2] Supported pixel formats:",
        "[AVFoundation indev @ 0x7fb2]   uyvy422",
        "[AVFoundation indev @ 0x7fb2]   yuyv422",
        "[AVFoundation indev @ 0x7fb2]   nv12",
        "[AVFoundation indev @ 0x7fb2]   0rgb",
        "[AVFoundation indev @ 0x7fb2]   bgr0",
        "0:none: Invalid argument",
    )

    private val unsupportedFramerate = listOf(
        "[AVFoundation indev @ 0x7fb2] Selected framerate (29.970030) is not supported by the device.",
        "[AVFoundation indev @ 0x7fb2] Supported framerates:",
        "[AVFoundation indev @ 0x7fb2]   {30.000000-30.000000}",
        "0:none: Invalid argument",
    )

    @Test
    fun `a device refusing the pixel format is told apart from one refusing the frame rate`() {
        assertEquals(CameraFailure.UNSUPPORTED_PIXEL_FORMAT, classifyCameraFfmpegStderr(unsupportedPixelFormat))
        assertEquals(CameraFailure.UNSUPPORTED_FRAMERATE, classifyCameraFfmpegStderr(unsupportedFramerate))
    }

    @Test
    fun `macOS refusing camera access is recognised as a permission problem`() {
        // The shape AVFoundation reports through ffmpeg when TCC has not granted camera access.
        val denied = listOf(
            "[AVFoundation indev @ 0x7fb2] Error opening video device: The operation couldn't be completed.",
            "[AVFoundation indev @ 0x7fb2] (AVFoundationErrorDomain error -11852 - Cannot use Video Device)",
        )
        assertEquals(CameraFailure.PERMISSION_DENIED, classifyCameraFfmpegStderr(denied))
    }

    @Test
    fun `a device held by another application is recognised as busy`() {
        val busy = listOf("[video4linux2,v4l2 @ 0x55d] Cannot open video device /dev/video0: Device or resource busy")
        assertEquals(CameraFailure.DEVICE_BUSY, classifyCameraFfmpegStderr(busy))
    }

    @Test
    fun `a device unplugged since it was enumerated is recognised as gone`() {
        val gone = listOf("[dshow @ 0000019] Could not find video device with name [USB Capture] among source devices",
            "video=USB Capture: I/O error")
        // "I/O error" also appears here; the device-not-found marker is what matters, so the
        // ordering must not let the busy check swallow it.
        assertEquals(CameraFailure.DEVICE_NOT_FOUND, classifyCameraFfmpegStderr(gone))
    }

    @Test
    fun `permission outranks every other marker in the same output`() {
        // A tail can hold more than one marker. Classification must be a function of the output,
        // not of which line happened to arrive last, or the same failure reports two ways.
        val both = unsupportedPixelFormat + "Error opening video device: Operation not permitted"
        assertEquals(CameraFailure.PERMISSION_DENIED, classifyCameraFfmpegStderr(both))
    }

    @Test
    fun `output that names nothing recognisable is unknown rather than guessed at`() {
        assertEquals(CameraFailure.UNKNOWN, classifyCameraFfmpegStderr(emptyList()))
        assertEquals(CameraFailure.UNKNOWN, classifyCameraFfmpegStderr(listOf("ffmpeg version 7.1 Copyright (c)")))
    }

    @Test
    fun `the pixel formats a device offers are read off its complaint`() {
        assertEquals(
            listOf("uyvy422", "yuyv422", "nv12", "0rgb", "bgr0"),
            parseSupportedPixelFormats(unsupportedPixelFormat)
        )
    }

    @Test
    fun `a format list running past the end of the captured tail is still read`() {
        // The tail is a window on ffmpeg's output, so the line that would terminate the block may
        // simply never have been captured. Dropping the block in that case loses the whole answer.
        assertEquals(listOf("uyvy422", "yuyv422"), parseSupportedPixelFormats(unsupportedPixelFormat.take(4)))
    }

    @Test
    fun `output with no format list yields no formats`() {
        assertTrue(parseSupportedPixelFormats(listOf("ffmpeg version 7.1")).isEmpty())
        assertTrue(parseSupportedFramerates(emptyList()).isEmpty())
    }

    @Test
    fun `both forms ffmpeg prints frame rates in are read`() {
        assertEquals(listOf(30.0), parseSupportedFramerates(unsupportedFramerate))
        assertEquals(
            listOf(30.0, 60.0),
            parseSupportedFramerates(listOf("Supported framerates:", "  30.000000 60.000000"))
        )
    }

    @Test
    fun `the best offered pixel format is chosen, not simply the first`() {
        // nv12 is offered first here, and uyvy422 is the one this app would rather decode.
        assertEquals("uyvy422", preferredPixelFormat(listOf("nv12", "uyvy422", "bgr0")))
    }

    @Test
    fun `a device offering nothing familiar still gets its first format tried`() {
        assertEquals("rgb565be", preferredPixelFormat(listOf("rgb565be", "gray")))
        assertNull(preferredPixelFormat(emptyList()))
    }

    @Test
    fun `a refused pixel format becomes the next attempt's explicit format`() {
        val next = nextCaptureOverride(
            CameraFailure.UNSUPPORTED_PIXEL_FORMAT, unsupportedPixelFormat, emptyList(), emptySet()
        )
        assertEquals(CaptureOverride(pixelFormat = "uyvy422"), next)
    }

    @Test
    fun `a refused frame rate falls back to an enumerated format when ffmpeg names none`() {
        val next = nextCaptureOverride(
            failure = CameraFailure.UNSUPPORTED_FRAMERATE,
            stderrTail = listOf("Selected framerate (29.970030) is not supported by the device."),
            knownFormats = listOf(CameraFormat(1920, 1080, 25)),
            alreadyTried = emptySet()
        )
        assertEquals(CaptureOverride(framerate = "25"), next)
    }

    @Test
    fun `an override already tried is not offered a second time`() {
        // Without this the loop would spend all five attempts on the same rejected command, which
        // is exactly what it did before there was an override at all.
        val tried = setOf(CaptureOverride(pixelFormat = "uyvy422"))
        assertNull(
            nextCaptureOverride(CameraFailure.UNSUPPORTED_PIXEL_FORMAT, unsupportedPixelFormat, emptyList(), tried)
        )
    }

    @Test
    fun `failures that retrying cannot fix yield no next attempt`() {
        val formats = listOf(CameraFormat(640, 480, 30))
        listOf(
            CameraFailure.PERMISSION_DENIED,
            CameraFailure.PERMISSION_OR_UNAVAILABLE,
            CameraFailure.DEVICE_BUSY,
        ).forEach {
            assertNull(nextCaptureOverride(it, unsupportedPixelFormat, formats, emptySet()), "$it")
        }
    }

    @Test
    fun `a device that sent no video is asked again for nothing in particular`() {
        // The failure that issue #464 was reported as: five identical attempts, then a grey
        // rectangle. What the device refused is what was asked of it, so the next attempt asks for
        // nothing — there is no format in the stderr to parse, and none is needed.
        listOf(CameraFailure.NO_FRAMES, CameraFailure.DEVICE_CONFIG_REFUSED).forEach {
            assertEquals(
                CaptureOverride.DEVICE_DEFAULTS,
                nextCaptureOverride(it, emptyList(), emptyList(), emptySet()),
                "$it",
            )
        }
    }

    @Test
    fun `asking for nothing is attempted once, not on every remaining retry`() {
        assertNull(
            nextCaptureOverride(
                CameraFailure.NO_FRAMES,
                emptyList(),
                emptyList(),
                setOf(CaptureOverride.DEVICE_DEFAULTS),
            ),
            "the loop has four attempts left and no reason to spend them identically",
        )
    }

    @Test
    fun `a device that refused the requested mode and fell back is not called busy`() {
        val fellBack = listOf(
            "[AVFoundation indev @ 0x7fe465804b40] Configuration of video device failed, " +
                "falling back to default."
        )
        assertEquals(CameraFailure.DEVICE_CONFIG_REFUSED, classifyCameraFfmpegStderr(fellBack, "avfoundation"))
    }

    @Test
    fun `on macos an IO error names permission as well as availability, and nowhere else does`() {
        // AVFoundation prints this for a privacy refusal — which carries no authorization string,
        // so PERMISSION_DENIED cannot match it — as well as for a device it cannot open. Calling it
        // DEVICE_BUSY put "already in use by another application" in front of an operator whose
        // camera nothing was using. dshow and v4l2 emit it only for a device another process holds.
        val ioError = listOf("[in#0 @ 0x12b007d90] Error opening input: Input/output error")

        assertEquals(
            CameraFailure.PERMISSION_OR_UNAVAILABLE,
            classifyCameraFfmpegStderr(ioError, "avfoundation"),
        )
        assertEquals(CameraFailure.DEVICE_BUSY, classifyCameraFfmpegStderr(ioError, "dshow"))
        assertEquals(CameraFailure.DEVICE_BUSY, classifyCameraFfmpegStderr(ioError, "v4l2"))
    }

    @Test
    fun `an explicitly busy device stays busy on every platform`() {
        val busy = listOf("[AVFoundation indev @ 0x7fb2] Cannot open device: already in use")
        assertEquals(CameraFailure.DEVICE_BUSY, classifyCameraFfmpegStderr(busy, "avfoundation"))
    }

    @Test
    fun `the report keeps ffmpeg's diagnosis and drops the operator's hardware and paths`() {
        val tail = listOf(
            "[dshow @ 01] Selected pixel format (yuv420p) is not supported by the input device.",
            "[dshow @ 01] video=Elgato Cam Link 4K opened from /Users/someone/Movies/clip.mov",
        )

        val redacted = redactedFfmpegStderr(tail, deviceName = "Elgato Cam Link 4K")

        assertFalse("Elgato" in redacted, redacted)
        assertFalse("someone" in redacted, redacted)
        // A redactor that eats the diagnosis is worse than no report at all.
        assertTrue("Selected pixel format (yuv420p) is not supported" in redacted, redacted)
    }

    @Test
    fun `a report is capped rather than truncating Sentry's own limit`() {
        val flood = List(200) { "line $it padded out to something worth measuring ${"x".repeat(80)}" }
        val redacted = redactedFfmpegStderr(flood, deviceName = "cam")
        assertTrue(redacted.length <= 4000, redacted.length.toString())
        assertTrue(redacted.lines().size <= 20, redacted.lines().size.toString())
    }

    @Test
    fun `a one-character device name is not redacted out of every line`() {
        // Replacing a name that short would strike the middle of ordinary words and leave a report
        // nobody can read.
        val redacted = redactedFfmpegStderr(listOf("Selected pixel format is not supported"), deviceName = "e")
        assertEquals("Selected pixel format is not supported", redacted)
    }

    @Test
    fun `the reported command keeps what explains the attempt and drops what names the machine`() {
        val command = listOf(
            "/opt/homebrew/bin/ffmpeg", "-f", "dshow", "-framerate", "30", "-pixel_format", "uyvy422",
            "-i", "video=Elgato Cam Link 4K", "-f", "rawvideo", "-"
        )

        val redacted = redactedFfmpegCommand(command)

        assertFalse("Elgato" in redacted, redacted)
        assertTrue("-pixel_format uyvy422" in redacted, redacted)
        assertTrue("-framerate 30" in redacted, redacted)
        assertTrue("-f dshow" in redacted, redacted)
    }

    @Test
    fun `an avfoundation index survives the report because it names a position, not a device`() {
        val redacted = redactedFfmpegCommand(listOf("ffmpeg", "-f", "avfoundation", "-i", "0:none"))
        assertTrue("0:none" in redacted, redacted)
    }

    @Test
    fun `every failure the app can reach has its own sentence for the operator`() {
        listOf("Mac OS X", "Windows 11", "Linux").forEach { os ->
            val resources = CameraFailure.entries.map { cameraFailureStringRes(it, os) }
            assertEquals(CameraFailure.entries.size, resources.toSet().size, "$os: $resources")
        }
    }

    @Test
    fun `windows is sent to the windows camera settings, not the mac ones`() {
        assertNotEquals(
            cameraFailureStringRes(CameraFailure.PERMISSION_DENIED, "Mac OS X"),
            cameraFailureStringRes(CameraFailure.PERMISSION_DENIED, "Windows 11"),
            "naming the wrong platform's settings pane is worse than saying nothing",
        )
    }

    @Test
    fun `windows is told a name may not match rather than that the camera was unplugged`() {
        assertNotEquals(
            cameraFailureStringRes(CameraFailure.DEVICE_NOT_FOUND, "Mac OS X"),
            cameraFailureStringRes(CameraFailure.DEVICE_NOT_FOUND, "Windows 11"),
            "on windows the likelier cause is a name DirectShow does not answer to",
        )
    }

    @Test
    fun `a failure with no platform remedy reads the same everywhere`() {
        assertEquals(
            cameraFailureStringRes(CameraFailure.DEVICE_BUSY, "Mac OS X"),
            cameraFailureStringRes(CameraFailure.DEVICE_BUSY, "Windows 11"),
            "only the two whose remedy is a place in the platform's settings diverge",
        )
    }
}
