package org.churchpresenter.canvas

import kotlinx.coroutines.runBlocking
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.diagnostics.CrashReportSweep
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CameraCaptureLoopTest {

    private val sweep = CrashReportSweep()

    @BeforeTest
    fun mark() = sweep.mark()

    @AfterTest
    fun clean() = sweep.sweep()

    private val source = SceneSource.CameraSource(
        id = "cam",
        name = "Camera",
        devicePath = "v4l2:///dev/video0",
        deviceName = "Studio Camera",
        videoFormat = "1280x720@30",
    )

    /** Hands out [script] one attempt at a time, records every command, and never waits. */
    private class ScriptedSteps(
        private val script: List<FfmpegAttempt?>,
        private val formats: List<CameraFormat> = listOf(CameraFormat(1280, 720, 25)),
    ) : CaptureSteps {
        val commands = mutableListOf<List<String>>()
        val pauses = mutableListOf<Long>()
        var formatQueries = 0
        var releases = 0

        override suspend fun attempt(command: List<String>, entry: CacheEntry): FfmpegAttempt? {
            commands += command
            return script.getOrNull(commands.size - 1)
        }

        override suspend fun formats(source: SceneSource.CameraSource): List<CameraFormat> {
            formatQueries++
            return formats
        }

        override suspend fun pause(millis: Long) {
            pauses += millis
        }

        override suspend fun releaseLingering(entry: CacheEntry) {
            releases++
        }

        override fun lastEnumeration(): CameraEnumerationFacts? = null
    }

    private fun failed(vararg stderr: String, immediate: Boolean = false) =
        FfmpegAttempt(framesProduced = false, exitCode = 1, stderrTail = stderr.toList(), exitedImmediately = immediate)

    private val streamed = FfmpegAttempt(framesProduced = true, exitCode = 0, stderrTail = emptyList())

    private fun run(steps: ScriptedSteps, entry: CacheEntry = CacheEntry()): CacheEntry = runBlocking {
        val loop = CaptureLoop(source, entry, steps)
        loop.run()
        loop.reportIfGaveUp()
        entry
    }

    @Test
    fun `a process that never starts is retried five times and then given up on`() {
        val steps = ScriptedSteps(List(5) { null })

        val entry = run(steps)

        assertEquals(5, steps.commands.size)
        assertEquals(CameraFailure.UNKNOWN, entry.error.value)
        assertEquals(5, steps.releases, "each attempt clears the last one away first")
    }

    @Test
    fun `a stream that ran clears the error and starts the failure count over`() {
        val steps = ScriptedSteps(listOf(failed(), failed(), streamed) + List(5) { failed() })

        run(steps)

        assertEquals(8, steps.commands.size, "five failures in a row after the stream, not five in all")
    }

    @Test
    fun `a stream that ran is restarted after a short pause, a failure after a longer one`() {
        val steps = ScriptedSteps(listOf(streamed) + List(5) { failed() })

        run(steps)

        assertEquals(1_000L, steps.pauses.first())
        assertTrue(steps.pauses.drop(1).all { it == 2_000L })
    }

    @Test
    fun `an attempt that ran without a word of stderr is read as no frames`() {
        val entry = run(ScriptedSteps(List(5) { failed() }))

        assertEquals(CameraFailure.NO_FRAMES, entry.error.value)
    }

    @Test
    fun `stderr that names nothing known is read as no frames too`() {
        val entry = run(ScriptedSteps(List(5) { failed("something unexpected happened") }))

        assertEquals(CameraFailure.NO_FRAMES, entry.error.value)
    }

    @Test
    fun `a privacy refusal stops at once instead of retrying`() {
        val steps = ScriptedSteps(List(5) { failed("Permission denied") })

        val entry = run(steps)

        assertEquals(1, steps.commands.size)
        assertEquals(CameraFailure.PERMISSION_DENIED, entry.error.value)
        assertTrue(steps.pauses.isEmpty(), "nothing to wait for once the operator has to act")
    }

    @Test
    fun `a refused frame rate is retried at the rate the device lists`() {
        val steps = ScriptedSteps(List(5) { failed("Selected framerate (29.97) is not supported by the device.") })

        val entry = run(steps)

        val retried = steps.commands[1]
        assertEquals("25", retried[retried.indexOf("-framerate") + 1])
        assertEquals(CameraFailure.UNSUPPORTED_FRAMERATE, entry.error.value)
        assertEquals(1, steps.formatQueries, "the device's formats are asked for once and kept")
    }

    @Test
    fun `a device that produced nothing is asked last for its own defaults`() {
        val steps = ScriptedSteps(List(5) { failed() })

        run(steps)

        assertTrue("-video_size" in steps.commands.first())
        assertTrue("-video_size" !in steps.commands[1], "the defaults attempt asks for no size")
    }

    @Test
    fun `an attempt that exits straight back out still counts toward giving up`() {
        val steps = ScriptedSteps(List(5) { failed("Device or resource busy", immediate = true) })

        val entry = run(steps)

        assertEquals(5, steps.commands.size)
        assertEquals(CameraFailure.DEVICE_BUSY, entry.error.value)
    }

    @Test
    fun `a device path no capture backend knows is never attempted`() {
        val steps = ScriptedSteps(emptyList())
        val entry = CacheEntry()

        runBlocking { CaptureLoop(source.copy(devicePath = "carrier-pigeon://1"), entry, steps).run() }

        assertTrue(steps.commands.isEmpty())
        assertNull(entry.error.value)
    }
}
