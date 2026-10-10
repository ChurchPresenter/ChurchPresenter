@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.ContentRegion
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.OutputSize
import kotlin.test.Test
import kotlin.test.assertEquals

class PreviewAdjustOverlayDragTest {

    private class State(band: Int?) {
        var margins by mutableStateOf(Margins(100, 100, 100, 100))
        var size by mutableStateOf(48)
        var band by mutableStateOf(band)
        var region by mutableStateOf(ContentRegion())
    }

    private val output = OutputSize(1920, 1080)

    private fun overlay(band: Int? = null, body: SkikoComposeUiTest.(State) -> Unit) =
        runSkikoComposeUiTest(size = Size(1200f, 1000f), density = Density(1f)) {
            val state = State(band)
            setContent {
                val model = AdjustModel(
                    margins = Adjustable(state.margins) { state.margins = it },
                    alignment = Adjustable(Constants.MIDDLE) {},
                    region = if (band == null) Adjustable(state.region) { state.region = it } else null,
                    textSize = Adjustable(state.size) { state.size = it },
                    band = state.band?.let { b -> Adjustable(b) { state.band = it } },
                )
                MaterialTheme {
                    Box(Modifier.offset(350.dp, 350.dp)) { PreviewAdjustOverlay(model, 480.dp, output) }
                }
            }
            waitForIdle()
            body(state)
        }

    private fun SkikoComposeUiTest.drag(tag: String, by: Offset) {
        onNodeWithTag(tag).performTouchInput { swipe(center, center + by, durationMillis = 200) }
        waitForIdle()
    }

    @Test
    fun `each margin bar stops at nothing one way and at its room the other`() = overlay { state ->
        val room = MarginRoom.of(output.width, output.height)
        drag(adjustMarginTag("top"), Offset(0f, -300f))
        assertEquals(0, state.margins.top)
        drag(adjustMarginTag("left"), Offset(-300f, 0f))
        assertEquals(0, state.margins.left)
        drag(adjustMarginTag("bottom"), Offset(0f, -300f))
        assertEquals(room.maxFor(MarginSide.BOTTOM, state.margins), state.margins.bottom)
        drag(adjustMarginTag("right"), Offset(-300f, 0f))
        assertEquals(room.maxFor(MarginSide.RIGHT, state.margins), state.margins.right)
    }

    @Test
    fun `the size corner keeps the text size inside its range`() = overlay { state ->
        drag(ADJUST_SIZE_TAG, Offset(300f, 300f))
        assertEquals(200, state.size)
        drag(ADJUST_SIZE_TAG, Offset(-300f, -300f))
        assertEquals(8, state.size)
    }

    @Test
    fun `the band bar keeps a lower third's band inside its range`() = overlay(band = 30) { state ->
        drag(ADJUST_BAND_TAG, Offset(0f, -330f))
        assertEquals(BAND_RANGE.last, state.band)
        drag(ADJUST_BAND_TAG, Offset(0f, 330f))
        assertEquals(BAND_RANGE.first, state.band)
        val room = MarginRoom.of(output.width, output.height, state.band)
        drag(adjustMarginTag("top"), Offset(0f, 250f))
        assertEquals(room.maxFor(MarginSide.TOP, state.margins), state.margins.top)
    }
}
