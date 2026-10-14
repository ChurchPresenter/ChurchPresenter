package org.churchpresenter.canvas

import kotlinx.coroutines.runBlocking
import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class SharedCameraFrameCacheRefCountTest {

    private fun camera(path: String, deckLinkIndex: Int = -1, isDeckLink: Boolean = deckLinkIndex >= 0) =
        SceneSource.CameraSource(
            id = "refcount",
            name = "Camera",
            devicePath = path,
            isDeckLink = isDeckLink,
            deckLinkIndex = deckLinkIndex,
        )

    /** Acquires [source] and waits for its capture coroutine, which returns at once for an unknown scheme. */
    private fun acquireSettled(source: SceneSource.CameraSource): SharedCameraFrameCache.CameraFlows {
        val flows = SharedCameraFrameCache.acquire(source)
        runBlocking { SharedCameraFrameCache.liveCaptureJobs[SharedCameraFrameCache.keyFor(source)]?.join() }
        return flows
    }

    @Test
    fun `a capture shared by two holders stays until the second lets go`() {
        val source = camera("unsupported://refcount-shared").copy(videoFormat = "1280x720@30")
        val before = SharedCameraFrameCache.liveCaptureCount

        val first = acquireSettled(source)
        val second = SharedCameraFrameCache.acquire(source)
        assertSame(first.frame, second.frame, "both holders read the one capture")
        assertSame(
            first.error, SharedCameraFrameCache.liveFailures[SharedCameraFrameCache.keyFor(source)],
            "and its failures are listed under its key",
        )

        SharedCameraFrameCache.release(source)
        assertEquals(before + 1, SharedCameraFrameCache.liveCaptureCount, "the first release keeps it")

        SharedCameraFrameCache.release(source)
        assertEquals(before, SharedCameraFrameCache.liveCaptureCount, "the second release ends it")
        assertNull(first.frame.value)
        assertNull(first.error.value, "a scheme no backend knows is not reported as a camera failure")
    }

    @Test
    fun `releasing a capture that is not held is harmless`() {
        val source = camera("unsupported://refcount-unheld")
        val before = SharedCameraFrameCache.liveCaptureCount

        SharedCameraFrameCache.release(source)
        acquireSettled(source)
        SharedCameraFrameCache.release(source)
        SharedCameraFrameCache.release(source)

        assertEquals(before, SharedCameraFrameCache.liveCaptureCount)
    }

    @Test
    fun `a DeckLink capture without the card library is acquired and released like any other`() {
        if (DeckLinkManager.isAvailable()) return
        val before = SharedCameraFrameCache.liveCaptureCount

        listOf(
            camera("unsupported://refcount-decklink", deckLinkIndex = 3),
            camera("unsupported://refcount-decklink-unindexed", deckLinkIndex = -1, isDeckLink = true),
        ).forEach { source ->
            acquireSettled(source)
            assertEquals(before + 1, SharedCameraFrameCache.liveCaptureCount)
            SharedCameraFrameCache.release(source)
        }

        assertEquals(before, SharedCameraFrameCache.liveCaptureCount)
    }
}
