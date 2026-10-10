package org.churchpresenter.sharedui.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A presenter whose sizes are plain points, drawn as a 1920x1080 (or, portrait, a 1080x1920) output
 * would and scaled into the room it has: the canvas it lays out on, and where the result lands.
 */
@OptIn(ExperimentalTestApi::class)
class ReferenceScaledBoxTest {

    @Test
    fun `the reference outputs draw at 1 to 1, landscape and portrait`() {
        assertEquals(1f, referenceScale(1920.dp, 1080.dp))
        assertEquals(1f, referenceScale(1080.dp, 1920.dp))
    }

    @Test
    fun `a smaller output scales down by the shorter axis against its own reference`() {
        assertEquals(0.5f, referenceScale(960.dp, 540.dp))
        assertEquals(0.5f, referenceScale(540.dp, 960.dp), "a portrait output is measured against 1080x1920")
        assertEquals(2f, referenceScale(3840.dp, 2160.dp))
    }

    @Test
    fun `a pathological output is held to the presenter range`() {
        assertEquals(MIN_PRESENTER_SCALE, referenceScale(16.dp, 16.dp))
        assertEquals(MIN_PRESENTER_SCALE, referenceScale(16.dp, 32.dp))
        assertEquals(MAX_PRESENTER_SCALE, referenceScale(19_200.dp, 10_800.dp))
    }

    /** The canvas [ReferenceScaledBox] hands its content inside a [width] by [height] box. */
    private fun canvasIn(width: Dp, height: Dp): DpSize {
        var seen = DpSize.Zero
        runComposeUiTest {
            setContent {
                Box(Modifier.size(width, height)) {
                    ReferenceScaledBox {
                        BoxWithConstraints(Modifier.fillMaxSize().testTag("content")) {
                            seen = DpSize(maxWidth, maxHeight)
                        }
                    }
                }
            }
            val drawn = onNodeWithTag("content").getBoundsInRoot()
            assertTrue(abs(drawn.width.value - width.value) <= 1f, "drawn across the whole box: $drawn")
            assertTrue(abs(drawn.height.value - height.value) <= 1f, "drawn down the whole box: $drawn")
        }
        return seen
    }

    @Test
    fun `content lays out on the reference canvas and is drawn back into the box`() {
        assertEquals(DpSize(1920.dp, 1080.dp), canvasIn(960.dp, 540.dp))
        assertEquals(DpSize(1080.dp, 1920.dp), canvasIn(270.dp, 480.dp))
    }

    @Test
    fun `on a reference output the content is handed the box itself`() {
        var seen = DpSize.Zero
        runDesktopComposeUiTest(1920, 1080) {
            setContent {
                ReferenceScaledBox { BoxWithConstraints(Modifier.fillMaxSize()) { seen = DpSize(maxWidth, maxHeight) } }
            }
            waitForIdle()
        }
        assertEquals(DpSize(1920.dp, 1080.dp), seen)
    }
}
