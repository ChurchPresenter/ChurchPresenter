package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The pure parts of camera capture: frame conversion, the ffmpeg command, and why a run gave up.
 *
 * Not covered: `runFfmpegCapture` returning early when ffmpeg is absent. Reaching it needs a real
 * `isFfmpegAvailable()` — which launches a process — and asserting "nothing was reported" needs a
 * seam on the `CrashReporter` singleton. The decision it makes is [cameraGiveUpReason]'s, and that
 * is tested directly.
 *
 * Also not covered here: the retry loop that consumes [CaptureOverride], the stderr drain feeding
 * it, and the `CrashReporter` report at the end — each needs a real process and a real device. The
 * decisions they make live in `CameraDiagnostics.kt` and are tested in `CameraDiagnosticsTest`.
 */
class SharedCameraFrameCacheTest {

    private fun camera(
        devicePath: String = "",
        videoFormat: String = "",
        videoConnection: Int = 0,
        isDeckLink: Boolean = false,
        deckLinkIndex: Int = -1,
    ) = SceneSource.CameraSource(
        id = "cam", name = "Camera", devicePath = devicePath, videoFormat = videoFormat,
        videoConnection = videoConnection, isDeckLink = isDeckLink, deckLinkIndex = deckLinkIndex
    )

    @Test
    fun `two sources on the same device and format share one capture`() {
        val a = camera(devicePath = "v4l2:///dev/video0", videoFormat = "1280x720@30")
        val b = camera(devicePath = "v4l2:///dev/video0", videoFormat = "1280x720@30")
        assertEquals(SharedCameraFrameCache.keyFor(a), SharedCameraFrameCache.keyFor(b))
    }

    @Test
    fun `a different format is a different capture`() {
        val a = camera(devicePath = "v4l2:///dev/video0", videoFormat = "1280x720@30")
        val b = camera(devicePath = "v4l2:///dev/video0", videoFormat = "1920x1080@30")
        assertNotEquals(SharedCameraFrameCache.keyFor(a), SharedCameraFrameCache.keyFor(b))
    }

    @Test
    fun `a decklink source keys on its index and connection, not its device path`() {
        val a = camera(devicePath = "ignored", isDeckLink = true, deckLinkIndex = 0, videoConnection = 1)
        val b = camera(devicePath = "different", isDeckLink = true, deckLinkIndex = 0, videoConnection = 1)
        val other = camera(isDeckLink = true, deckLinkIndex = 1, videoConnection = 1)
        assertEquals(SharedCameraFrameCache.keyFor(a), SharedCameraFrameCache.keyFor(b))
        assertNotEquals(SharedCameraFrameCache.keyFor(a), SharedCameraFrameCache.keyFor(other))
    }

    @Test
    fun `a decklink source with no index falls back to the ffmpeg key`() {
        val key = SharedCameraFrameCache.keyFor(camera(devicePath = "v4l2:///dev/video0", isDeckLink = true))
        assertTrue(key.startsWith("ffmpeg:"), key)
    }

    @Test
    fun `each platform capture backend gets its own ffmpeg input flags`() {
        assertEquals(
            listOf("-f", "dshow"),
            buildFfmpegCommand(camera(devicePath = "dshow://Logitech"))!!.subList(1, 3)
        )
        assertEquals(
            listOf("-f", "v4l2"),
            buildFfmpegCommand(camera(devicePath = "v4l2:///dev/video0"))!!.subList(1, 3)
        )
        assertEquals(
            listOf("-f", "avfoundation"),
            buildFfmpegCommand(camera(devicePath = "avfoundation://0"))!!.subList(1, 3)
        )
    }

    @Test
    fun `the device name is passed through in the form each backend expects`() {
        assertTrue(buildFfmpegCommand(camera(devicePath = "dshow://Logitech Cam"))!!
            .contains("video=Logitech Cam"))
        assertTrue(buildFfmpegCommand(camera(devicePath = "v4l2:///dev/video2"))!!
            .contains("/dev/video2"))
        assertTrue(buildFfmpegCommand(camera(devicePath = "avfoundation://1"))!!
            .contains("1:none"))
    }

    @Test
    fun `a requested video format becomes size and framerate args ahead of the input`() {
        val cmd = buildFfmpegCommand(camera(devicePath = "v4l2:///dev/video0", videoFormat = "1920x1080@60"))!!
        assertTrue(cmd.containsAll(listOf("-video_size", "1920x1080", "-framerate", "60")), cmd.toString())
        assertTrue(cmd.indexOf("-video_size") < cmd.indexOf("-i"), "input args must precede -i: $cmd")
    }

    @Test
    fun `an absent or unparseable format adds no size args`() {
        val none = buildFfmpegCommand(camera(devicePath = "v4l2:///dev/video0"))!!
        val junk = buildFfmpegCommand(camera(devicePath = "v4l2:///dev/video0", videoFormat = "best please"))!!
        assertTrue(!none.contains("-video_size"))
        assertTrue(!junk.contains("-video_size"))
    }

    @Test
    fun `every backend is asked for raw bgra with no audio`() {
        for (path in listOf("dshow://c", "v4l2:///dev/video0", "avfoundation://0")) {
            val cmd = buildFfmpegCommand(camera(devicePath = path))!!
            assertTrue(cmd.containsAll(listOf("-an", "-pix_fmt", "bgra", "-f", "rawvideo", "-")), cmd.toString())
        }
    }

    @Test
    fun `an unrecognised device path yields no command at all`() {
        assertNull(buildFfmpegCommand(camera(devicePath = "rtsp://camera.local")))
        assertNull(buildFfmpegCommand(camera()))
    }

    @Test
    fun `ffmpeg's stream announcement yields the negotiated dimensions`() {
        assertEquals(
            1280 to 720,
            parseFfmpegVideoDimensions("Stream #0:0: Video: rawvideo (BGRA / 0x41524742), bgra, 1280x720, 30 fps")
        )
    }

    @Test
    fun `lines that are not a bgra video announcement are ignored`() {
        assertNull(parseFfmpegVideoDimensions("Stream #0:1: Audio: pcm_s16le, 48000 Hz"))
        assertNull(parseFfmpegVideoDimensions("Video: h264, yuv420p, 1280x720"))
        assertNull(parseFfmpegVideoDimensions(""))
    }

    @Test
    fun `dimensions before the pixel format are not mistaken for the frame size`() {
        // Only what follows "bgra" describes the negotiated stream; anything earlier is another
        // stream's geometry and must not be picked up.
        assertEquals(
            640 to 480,
            parseFfmpegVideoDimensions("Video: rawvideo, 9999x9999 something, bgra, 640x480, 30 fps")
        )
    }

    @Test
    fun `a bgra line with no dimensions yields nothing`() {
        assertNull(parseFfmpegVideoDimensions("Stream #0:0: Video: rawvideo, bgra, progressive"))
    }

    @Test
    fun `bgra bytes are repacked as opaque argb pixels`() {
        // ffmpeg emits B,G,R,A per pixel; the canvas wants 0xAARRGGBB.
        val frame = byteArrayOf(
            0x10, 0x20, 0x30, 0xFF.toByte(),   // b=10 g=20 r=30 a=FF
            0x01, 0x02, 0x03, 0x80.toByte(),   // b=01 g=02 r=03 a=80
        )
        val pixels = IntArray(2)
        bgraBytesToArgbPixels(frame, pixels)
        assertEquals(0xFF302010.toInt(), pixels[0])
        assertEquals(0x80030201.toInt(), pixels[1])
    }

    @Test
    fun `high bytes survive the conversion unsigned`() {
        val frame = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val pixels = IntArray(1)
        bgraBytesToArgbPixels(frame, pixels)
        assertEquals(0xFFFFFFFF.toInt(), pixels[0])
    }

    @Test
    fun `only the pixels asked for are converted`() {
        val frame = ByteArray(16) { 0x7F }
        val pixels = IntArray(2)
        bgraBytesToArgbPixels(frame, pixels)
        assertEquals(0x7F7F7F7F, pixels[0])
        assertEquals(0x7F7F7F7F, pixels[1])
    }

    @Test
    fun `giving up names which of the three causes it was`() {
        // The warning used to carry only subsystem=camera, which cannot tell a machine whose PATH
        // will not launch ffmpeg from one whose webcam is already held by a video call.
        assertEquals(
            "ffmpeg_not_launchable",
            cameraGiveUpReason(everStarted = false, sawImmediateExit = false)
        )
        assertEquals(
            "device_unavailable",
            cameraGiveUpReason(everStarted = true, sawImmediateExit = true)
        )
        assertEquals(
            "no_frames",
            cameraGiveUpReason(everStarted = true, sawImmediateExit = false)
        )
    }

    @Test
    fun `a process that never started outranks anything a later attempt saw`() {
        // everStarted is false only when no attempt produced a process, so nothing a process did
        // can be reported against a run that never had one.
        assertEquals(
            "ffmpeg_not_launchable",
            cameraGiveUpReason(everStarted = false, sawImmediateExit = true)
        )
    }

    @Test
    fun `the device scheme is tagged without the device path`() {
        // The path names the user's hardware; the scheme is what separates a dshow problem from a
        // v4l2 one, and is bounded enough to be a tag.
        assertEquals("dshow", deviceScheme("dshow://Integrated Webcam"))
        assertEquals("v4l2", deviceScheme("v4l2:///dev/video0"))
        assertEquals("avfoundation", deviceScheme("avfoundation://0"))
        assertEquals("unknown", deviceScheme(""))
        assertEquals("unknown", deviceScheme("/dev/video0"))
    }
    @Test
    fun `no override leaves the command exactly as it was`() {
        // The retry path reaches buildFfmpegCommand through a defaulted parameter, so every attempt
        // before a device has complained must produce the argv that shipped before it existed.
        for (path in listOf("dshow://c", "v4l2:///dev/video0", "avfoundation://0")) {
            val source = camera(devicePath = path, videoFormat = "1280x720@30")
            assertEquals(buildFfmpegCommand(source), buildFfmpegCommand(source, CaptureOverride.NONE))
        }
    }

    @Test
    fun `an override's pixel format is an input flag, so it goes ahead of the input`() {
        for (path in listOf("dshow://c", "v4l2:///dev/video0", "avfoundation://0")) {
            val cmd = buildFfmpegCommand(camera(devicePath = path), CaptureOverride(pixelFormat = "uyvy422"))!!
            assertTrue(cmd.containsAll(listOf("-pixel_format", "uyvy422")), cmd.toString())
            assertTrue(cmd.indexOf("-pixel_format") < cmd.indexOf("-i"), "input args must precede -i: $cmd")
        }
    }

    @Test
    fun `the output pixel format stays bgra when the input format is overridden`() {
        // -pixel_format describes what the device sends; -pix_fmt describes what this app reads.
        // Confusing the two hands readFramesInto a buffer it will misinterpret as BGRA.
        val cmd = buildFfmpegCommand(camera(devicePath = "avfoundation://0"), CaptureOverride(pixelFormat = "nv12"))!!
        assertTrue(cmd.containsAll(listOf("-pix_fmt", "bgra")), cmd.toString())
        assertTrue(cmd.indexOf("-pixel_format") < cmd.indexOf("-i"), cmd.toString())
        assertTrue(cmd.indexOf("-pix_fmt") > cmd.indexOf("-i"), cmd.toString())
    }

    @Test
    fun `a device that refused a frame rate is not asked for it a second time`() {
        // The device's own answer outranks the format list that suggested the rejected rate, and
        // two -framerate flags in one argv is a command nobody can read in a bug report.
        val cmd = buildFfmpegCommand(
            camera(devicePath = "v4l2:///dev/video0", videoFormat = "1920x1080@60"),
            CaptureOverride(framerate = "25")
        )!!
        assertEquals(1, cmd.count { it == "-framerate" }, cmd.toString())
        assertEquals("25", cmd[cmd.indexOf("-framerate") + 1], cmd.toString())
        // The size it asked for is unaffected — only the rate was refused.
        assertTrue(cmd.containsAll(listOf("-video_size", "1920x1080")), cmd.toString())
    }

    @Test
    fun `ffmpeg is looked for where it is installed, not only where PATH points`() {
        // A desktop app does not inherit the shell's PATH: a Finder-launched macOS .app gets
        // launchd's default, which has neither Homebrew prefix on it. Resolving only the bare name
        // is what made a camera appear in the dropdown and then never show a picture (issue #431).
        val mac = ffmpegCandidatePaths("Mac OS X") { null }
        assertEquals("ffmpeg", mac.first(), "a configured PATH must still win: $mac")
        assertTrue("/opt/homebrew/bin/ffmpeg" in mac, mac.toString())
        assertTrue("/usr/local/bin/ffmpeg" in mac, mac.toString())

        val linux = ffmpegCandidatePaths("Linux") { null }
        assertTrue("/usr/bin/ffmpeg" in linux, linux.toString())
        assertTrue("/snap/bin/ffmpeg" in linux, linux.toString())

        val windows = ffmpegCandidatePaths("Windows 11") { if (it == "ProgramFiles") "C:\\Program Files" else null }
        assertTrue(windows.any { it.endsWith("ffmpeg.exe") }, windows.toString())
        assertTrue(windows.none { it.startsWith("/") }, "no POSIX paths on Windows: $windows")
    }

    @Test
    fun `resolution takes the first candidate that actually runs`() {
        val candidates = listOf("ffmpeg", "/opt/homebrew/bin/ffmpeg")

        val tried = mutableListOf<String>()
        val resolved = resolveFfmpegPath(candidates, isExecutable = { false }) { tried += it; false }
        // Nothing answered, so the bare name is reported and the callers say "install ffmpeg" —
        // which is the right thing to say when no candidate exists.
        assertEquals("ffmpeg", resolved)
        assertEquals(listOf("ffmpeg"), tried, "an absolute path that does not exist must not be launched")

        // The Homebrew install a Finder-launched app cannot see on its PATH, but can still run.
        assertEquals(
            "/opt/homebrew/bin/ffmpeg",
            resolveFfmpegPath(candidates, isExecutable = { true }) { it != "ffmpeg" }
        )
    }
}
