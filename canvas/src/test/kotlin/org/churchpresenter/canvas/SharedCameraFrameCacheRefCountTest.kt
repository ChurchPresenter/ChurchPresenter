package org.churchpresenter.canvas

import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class SharedCameraFrameCacheRefCountTest {

    private fun camera(path: String, deckLinkIndex: Int = -1) = SceneSource.CameraSource(
        id = "refcount",
        name = "Camera",
        devicePath = path,
        isDeckLink = deckLinkIndex >= 0,
        deckLinkIndex = deckLinkIndex,
    )

    @Test
    fun `a capture shared by two holders stays until the second lets go`() {
        val source = camera("unsupported://refcount-shared")
        val before = SharedCameraFrameCache.liveCaptureCount

        val first = SharedCameraFrameCache.acquire(source)
        val second = SharedCameraFrameCache.acquire(source)
        assertSame(first.frame, second.frame, "both holders read the one capture")

        SharedCameraFrameCache.release(source)
        assertEquals(before + 1, SharedCameraFrameCache.liveCaptureCount, "the first release keeps it")

        SharedCameraFrameCache.release(source)
        assertEquals(before, SharedCameraFrameCache.liveCaptureCount, "the second release ends it")
        assertNull(first.frame.value)
    }

    @Test
    fun `releasing a capture that is not held is harmless`() {
        val source = camera("unsupported://refcount-unheld")
        val before = SharedCameraFrameCache.liveCaptureCount

        SharedCameraFrameCache.release(source)
        SharedCameraFrameCache.acquire(source)
        SharedCameraFrameCache.release(source)
        SharedCameraFrameCache.release(source)

        assertEquals(before, SharedCameraFrameCache.liveCaptureCount)
    }

    @Test
    fun `a DeckLink capture without the card library is acquired and released like any other`() {
        if (DeckLinkManager.isAvailable()) return
        val source = camera("unsupported://refcount-decklink", deckLinkIndex = 3)
        val before = SharedCameraFrameCache.liveCaptureCount

        SharedCameraFrameCache.acquire(source)
        assertEquals(before + 1, SharedCameraFrameCache.liveCaptureCount)
        SharedCameraFrameCache.release(source)

        assertEquals(before, SharedCameraFrameCache.liveCaptureCount)
    }
}
