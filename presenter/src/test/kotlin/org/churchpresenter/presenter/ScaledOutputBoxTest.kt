package org.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.churchpresenter.sharedui.utils.OutputSize
import kotlin.test.Test
import kotlin.test.assertEquals

/** A preview of an output: laid out at the output's own size, then shrunk to the space it has. */
@OptIn(ExperimentalTestApi::class)
class ScaledOutputBoxTest {

    @Test
    fun `content is laid out at the output's size and shown shrunk to fit`() = runComposeUiTest {
        var laidOut = IntSize.Zero
        var density = 0f
        setContent {
            Box(Modifier.size(480.dp, 480.dp)) {
                ScaledOutputBox(OutputSize(1920, 1080)) {
                    density = LocalDensity.current.density
                    Box(Modifier.fillMaxSize().testTag(BOX).onSizeChanged { laidOut = it })
                }
            }
        }
        waitForIdle()

        assertEquals(IntSize(1920, 1080), laidOut, "the content sees the whole output")
        assertEquals(1f, density, "one pixel to the dp")
        val bounds = onNodeWithTag(BOX).getBoundsInRoot()
        assertEquals(480f, (bounds.right - bounds.left).value, 1f)
        assertEquals(270f, (bounds.bottom - bounds.top).value, 1f, "the output's shape is kept")
    }

    @Test
    fun `a space wider than the output is fitted by its height`() = runComposeUiTest {
        setContent {
            Box(Modifier.size(1000.dp, 200.dp)) {
                ScaledOutputBox(OutputSize(1920, 1080)) { Box(Modifier.fillMaxSize().testTag(BOX)) }
            }
        }

        val bounds = onNodeWithTag(BOX).getBoundsInRoot()
        assertEquals(200f, (bounds.bottom - bounds.top).value, 1f)
        assertEquals(355f, (bounds.right - bounds.left).value, 1f)
    }

    private companion object {
        const val BOX = "box"
    }
}
