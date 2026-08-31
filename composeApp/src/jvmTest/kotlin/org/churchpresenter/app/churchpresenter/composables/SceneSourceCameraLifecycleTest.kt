package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a camera source costs the capture cache as it comes and goes from a composition.
 *
 * **Why this can be tested at all**, when `SceneSourceRendererTest` says the camera branch needs
 * real hardware: the device path here names a scheme `buildFfmpegCommand` has no branch for, so
 * `runFfmpegCapture` returns at its first line and no process is ever started. The cache entry is
 * still created and still has to be released, which is the whole of what these tests are about.
 *
 * They exist because the release path had drifted from the acquire path. `acquire` was called from
 * a `remember` block and released from a `DisposableEffect`, and those are not the same lifecycle:
 * a composition that is abandoned before its effects run discards what `remember` produced without
 * calling any `onDispose`. The leaked entry owns an ffmpeg process holding the device open, so the
 * next acquire of that camera fails with `device_busy` — reported from the field as a canvas gone
 * black on a camera nothing else was using.
 *
 * Not covered: the abandonment itself, which needs a composition to be started and thrown away
 * mid-frame and has no hook in `runComposeUiTest`. What is pinned here is the invariant that made
 * it a leak — acquire and release keyed and scoped alike, balanced over an ordinary mount and
 * unmount.
 */
@OptIn(ExperimentalTestApi::class)
class SceneSourceCameraLifecycleTest {

    /** A scheme `buildFfmpegCommand` returns null for, so the capture coroutine starts nothing. */
    private fun camera(id: String, devicePath: String = "unsupported://test-device") =
        SceneSource.CameraSource(
            id = id,
            name = "Camera",
            devicePath = devicePath,
            videoFormat = "",
            videoConnection = 0,
            isDeckLink = false,
            deckLinkIndex = -1,
        )

    @Test
    fun `a camera leaving the composition releases its capture`() = runComposeUiTest {
        val before = SharedCameraFrameCache.liveCaptureCount
        var shown by mutableStateOf(true)

        setContent {
            if (shown) SceneSourceRenderer(camera("cam"), Modifier.size(64.dp))
        }
        waitForIdle()
        assertEquals(before + 1, SharedCameraFrameCache.liveCaptureCount, "mounting acquires one capture")

        shown = false
        waitForIdle()
        assertEquals(before, SharedCameraFrameCache.liveCaptureCount, "unmounting must release it")
    }

    @Test
    fun `the canvas preview and the presenter output share one capture of the same device`() =
        runComposeUiTest {
            val before = SharedCameraFrameCache.liveCaptureCount

            setContent {
                SceneSourceRenderer(camera("editor"), Modifier.size(64.dp))
                SceneSourceRenderer(camera("output"), Modifier.size(64.dp))
            }
            waitForIdle()

            assertEquals(
                before + 1,
                SharedCameraFrameCache.liveCaptureCount,
                "two renderers of one device are one capture, not two",
            )
        }

    @Test
    fun `re-pointing a source at another device leaves only the new one capturing`() =
        runComposeUiTest {
            val before = SharedCameraFrameCache.liveCaptureCount
            var path by mutableStateOf("unsupported://first")

            setContent { SceneSourceRenderer(camera("cam", path), Modifier.size(64.dp)) }
            waitForIdle()
            assertEquals(before + 1, SharedCameraFrameCache.liveCaptureCount)

            path = "unsupported://second"
            waitForIdle()
            assertEquals(
                before + 1,
                SharedCameraFrameCache.liveCaptureCount,
                "the old device's capture must be released, not left beside the new one",
            )
        }
}
