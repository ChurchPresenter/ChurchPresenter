package org.churchpresenter.app.churchpresenter.composables

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeFalse
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A camera's ffmpeg ends with its camera, and only its own ffmpeg ends.
 *
 * Found on Windows by the acceptance pass: switching a Camera source away inside the first two
 * seconds of an attempt left that attempt's ffmpeg running at a full core, and the kill that did
 * run was `taskkill /IM ffmpeg.exe`, which ended every ffmpeg on the computer. A long `sleep`
 * stands in for ffmpeg: what is tested is the process handling, not what the process does.
 */
class FfmpegProcessCleanupTest {

    @BeforeTest
    fun unixOnly() {
        // `sleep` and `sh` stand in for ffmpeg; the Windows kill path is the same function.
        assumeFalse(System.getProperty("os.name").lowercase().contains("win"))
    }

    private fun children(): List<ProcessHandle> =
        ProcessHandle.current().descendants().filter { it.isAlive }.toList()

    private fun awaitNewChild(before: Set<ProcessHandle>): ProcessHandle {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            children().firstOrNull { it !in before }?.let { return it }
            Thread.yield()
        }
        fail("the stand-in ffmpeg never started")
    }

    @Test
    fun `an attempt cancelled while its process starts up kills that process`() = runBlocking {
        val before = children().toSet()
        val attempt = launch(Dispatchers.IO) {
            SharedCameraFrameCache.attemptCapture(listOf("sleep", "30"), CacheEntry())
        }
        val child = awaitNewChild(before)

        attempt.cancel()
        attempt.join()

        assertTrue(child.onExit().get(5, TimeUnit.SECONDS).isAlive.not(), "the process outlived its camera")
    }

    @Test
    fun `killing a camera's process ends its children and nothing else`() {
        val bystander = ProcessBuilder("sleep", "30").start()
        val camera = ProcessBuilder("sh", "-c", "sleep 30 & wait").start()
        try {
            val deadline = System.currentTimeMillis() + 5_000
            while (camera.descendants().findAny().isEmpty && System.currentTimeMillis() < deadline) Thread.yield()
            val grandchild = camera.descendants().findAny().orElseThrow()

            killFfmpegProcess(camera)

            assertFalse(camera.isAlive, "the camera's process was killed")
            grandchild.onExit().get(5, TimeUnit.SECONDS)
            assertFalse(grandchild.isAlive, "and what it started")
            assertTrue(bystander.isAlive, "an unrelated process is left alone")
        } finally {
            bystander.destroyForcibly()
            camera.destroyForcibly()
        }
    }
}
