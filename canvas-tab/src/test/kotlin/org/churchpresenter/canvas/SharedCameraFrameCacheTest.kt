package org.churchpresenter.canvas

import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
}
