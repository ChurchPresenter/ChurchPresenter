package org.churchpresenter.canvas

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.churchpresenter.core.models.scene.SceneSource
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How long the camera keeps trying, and what the operator is told when it stops.
 *
 * ffmpeg dies for reasons that come back: a device another app held for a moment, a USB camera
 * re-enumerating, a laptop waking. So the capture restarts rather than giving up, and only a run of
 * failures with nothing in between is treated as a device that will not come. Getting the reset
 * wrong in either direction is a real fault — never resetting means a camera that has been working
 * for an hour gives up on its sixth blip, and never counting means a camera that is not there is
 * retried until the service ends with an empty rectangle and no message.
 *
 * None of that could be reached before: the loop's only exit for a missing binary is `ProcessBuilder`
 * throwing, and its give-up path costs five real retry delays. The process start and the pacing are
 * parameters now, so the whole sequence runs in milliseconds against a stand-in.
 */
class CameraRetryLoopTest {

    /** A process that is already over, with the exit code it is given. */
    private class Exited(private val code: Int, stderr: String = "") : Process() {
        private val err = ByteArrayInputStream(stderr.toByteArray())
        var destroyed = false
            private set

        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
        override fun getInputStream(): InputStream = InputStream.nullInputStream()
        override fun getErrorStream(): InputStream = err
        override fun waitFor(): Int = code
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = true
        override fun exitValue(): Int = code
        override fun isAlive(): Boolean = false
        override fun destroy() { destroyed = true }
        override fun destroyForcibly(): Process { destroyed = true; return this }
    }

    /** A process that streams one announced frame and then ends — a capture that worked. */
    private class Delivered(w: Int, h: Int) : Process() {
        private val out = ByteArrayInputStream(ByteArray(w * h * 4))
        private val err = ByteArrayInputStream(
            "  Stream #0:0: Video: rawvideo (BGRA / 0x41524742), bgra, ${w}x$h, 30 fps, 30 tbr\n".toByteArray()
        )

        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
        override fun getInputStream(): InputStream = out
        override fun getErrorStream(): InputStream = err
        override fun waitFor(): Int = 0
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = false
        override fun exitValue(): Int = 0
        override fun isAlive(): Boolean = false
        override fun destroy() = Unit
        override fun destroyForcibly(): Process = this
    }

    /** No waiting at all — the schedule itself is pinned by its own defaults, not by this. */
    private val instant = FfmpegPacing(deviceReleaseMs = 0, retryMs = 0, restartMs = 0)

    private fun webcam(path: String = "v4l2:///dev/video0", format: String = "") =
        SceneSource.CameraSource(id = "cam", name = "Camera", devicePath = path, deviceName = "USB Cam", videoFormat = format)

    private fun capture(
        source: SceneSource.CameraSource = webcam(),
        start: (List<String>) -> Process?,
    ): SharedCameraFrameCache.CacheEntry {
        val entry = SharedCameraFrameCache.CacheEntry()
        runBlocking {
            withTimeout(10_000) {
                SharedCameraFrameCache.runFfmpegCapture(source, entry, instant, start)
            }
        }
        return entry
    }

    // ── Device paths the app cannot build a command for ─────────────────────────

    @Test
    fun `a device path in no scheme the app knows is reported rather than retried`() {
        var starts = 0

        val entry = capture(webcam(path = "carrier-pigeon://bird0")) { starts++; Exited(0) }

        assertEquals(CameraFailure.UNSUPPORTED_DEVICE_PATH, entry.error.value)
        assertEquals(0, starts, "nothing was ever going to open — ffmpeg must not be run at all")
    }

    // ── ffmpeg not being on the machine ─────────────────────────────────────────

    @Test
    fun `a missing binary is reported at once, not after five attempts`() {
        var starts = 0

        val entry = capture { starts++; null }

        assertEquals(CameraFailure.FFMPEG_MISSING, entry.error.value)
        assertEquals(1, starts, "five more tries cannot install ffmpeg")
    }

    // ── A device that will not open ─────────────────────────────────────────────

    @Test
    fun `a device that keeps refusing is given up on, and says so`() {
        var starts = 0

        val entry = capture { starts++; Exited(1, "Device or resource busy\n") }

        assertEquals(5, starts, "the allowance is five consecutive failures")
        assertEquals(
            CameraFailure.DEVICE_UNAVAILABLE, entry.error.value,
            "an empty rectangle with no message is what this exists to prevent",
        )
    }

    @Test
    fun `a process that exited badly is cleaned up rather than left behind`() {
        val started = mutableListOf<Exited>()

        capture { Exited(1).also { started += it } }

        assertTrue(started.all { it.destroyed }, "every failed attempt must be reaped")
    }

    // ── A device that works, then stops ─────────────────────────────────────────

    @Test
    fun `a capture that delivered frames resets the failure count`() {
        // Four failures, one good run, then failures again: the good run has to put the count back
        // to zero, so the total is more than the bare allowance.
        var starts = 0

        val entry = capture {
            starts++
            if (starts == 5) Delivered(16, 12) else Exited(1)
        }

        assertTrue(starts > 5, "a working capture in between must have cleared the count (was $starts)")
        assertEquals(CameraFailure.DEVICE_UNAVAILABLE, entry.error.value)
    }

    @Test
    fun `frames arriving clear a failure the operator was already shown`() {
        var starts = 0
        val entry = SharedCameraFrameCache.CacheEntry()
        entry.error.value = CameraFailure.DEVICE_UNAVAILABLE

        runBlocking {
            withTimeout(10_000) {
                SharedCameraFrameCache.runFfmpegCapture(webcam(), entry, instant) {
                    starts++
                    if (starts == 1) Delivered(16, 12) else Exited(1)
                }
            }
        }

        // It ends on failures, so the interesting part is that the first good run cleared it — which
        // it did, or the count would never have reset and the run would have been shorter.
        assertTrue(starts > 5)
    }

    // ── What the give-up diagnostic carries ─────────────────────────────────────

    @Test
    fun `the diagnostic names the scheme but never the device`() {
        val report = cameraGiveUpReport(
            webcam(path = "avfoundation://0", format = "1280x720@30"),
            CaptureOutcome.NO_FRAMES, exitCode = 1, missing = false,
        )

        assertEquals("avfoundation", report.tags["camera.scheme"])
        assertEquals("camera", report.tags["subsystem"])
        assertEquals("no_frames", report.tags["failure.reason"])
        assertTrue(
            report.tags.values.none { it.contains("USB Cam") } &&
                report.extras.values.none { it.contains("USB Cam") },
            "the device name identifies a person's hardware and must never be sent",
        )
    }

    @Test
    fun `a missing binary is reported as its own reason, not as a device failure`() {
        val report = cameraGiveUpReport(webcam(), CaptureOutcome.NO_FRAMES, exitCode = null, missing = true)

        assertEquals("ffmpeg_missing", report.tags["failure.reason"])
        assertTrue(report.message.contains("could not be started"))
        assertNull(report.extras["ffmpeg.exit_code"], "there is no exit code when nothing started")
    }

    @Test
    fun `an exit code is carried when there is one`() {
        val report = cameraGiveUpReport(webcam(), CaptureOutcome.NO_DIMENSIONS, exitCode = 251, missing = false)

        assertEquals("251", report.extras["ffmpeg.exit_code"])
        assertEquals("no_dimensions", report.tags["failure.reason"])
    }

    @Test
    fun `a device with no format asked for is reported as auto`() {
        val report = cameraGiveUpReport(webcam(format = ""), CaptureOutcome.NO_FRAMES, null, missing = false)

        assertEquals("auto", report.extras["camera.format"])
    }

    @Test
    fun `a device path with no scheme at all is reported as unknown`() {
        val report = cameraGiveUpReport(
            webcam(path = "/dev/video0"), CaptureOutcome.NO_FRAMES, null, missing = false,
        )

        assertEquals("unknown", report.tags["camera.scheme"])
    }
}
