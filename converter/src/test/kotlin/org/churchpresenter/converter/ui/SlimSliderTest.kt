@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.converter.ui

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SlimSliderTest {

    @Test
    fun `tapping and dragging the track set the value and report when it settles`() {
        val values = mutableListOf<Float>()
        var finished = 0
        runComposeUiTest {
            setConverterContent(FakePickers()) {
                var value by remember { mutableStateOf(0.5f) }
                SlimSlider(
                    value = value,
                    onValueChange = { value = it; values += it },
                    valueRange = 0f..1f,
                    modifier = Modifier.width(300.dp).testTag("slider"),
                    trailingLabel = "${(value * 100).toInt()}%",
                    onValueChangeFinished = { finished++ },
                )
            }
            assertTrue(isShowing("50%"))

            onNodeWithTag("slider").performTouchInput { click(centerLeft) }
            waitForIdle()
            assertEquals(1, finished)
            assertTrue(values.last() < 0.1f, values.toString())

            onNodeWithTag("slider").performTouchInput { swipeRight(startX = left + 1f, endX = right - 1f) }
            waitForIdle()
            assertTrue(values.last() > 0.6f, values.toString())
            assertEquals(2, finished)

            onNodeWithTag("slider").performMouseInput { moveTo(center) }
            waitForIdle()
            onNodeWithTag("slider").performMouseInput { moveTo(center.copy(x = -50f)) }
            waitForIdle()
        }
    }

    @Test
    fun `a disabled slider ignores input and an empty range does not divide by zero`() {
        val values = mutableListOf<Float>()
        runComposeUiTest {
            setConverterContent(FakePickers()) {
                SlimSlider(
                    value = 1f,
                    onValueChange = { values += it },
                    valueRange = 1f..1f,
                    modifier = Modifier.width(200.dp).testTag("slider"),
                    enabled = false,
                )
            }
            onNodeWithTag("slider").performTouchInput { click(center) }
            waitForIdle()
            assertTrue(values.isEmpty())
        }
    }
}
