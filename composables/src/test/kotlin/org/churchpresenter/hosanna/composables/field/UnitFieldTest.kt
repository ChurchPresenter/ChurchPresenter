@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.field

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.churchpresenter.hosanna.composables.defaults.NumberFieldDefaults
import org.churchpresenter.hosanna.composables.model.NumberRange
import org.churchpresenter.hosanna.composables.testing.HosannaSurface

class UnitFieldTest {

    private fun ComposeUiTest.unitField(start: Double, unit: String, range: NumberRange): () -> Double {
        var value by mutableDoubleStateOf(start)
        setContent {
            HosannaSurface {
                Column {
                    UnitField(value, { value = it }, unit, range, rangeError = "Use the range")
                    UnitField(0.0, {}, "x", NumberRange(0.0, 1.0))
                }
            }
        }
        return { value }
    }

    private fun ComposeUiTest.input() = onAllNodes(hasSetTextAction())[0]

    @Test
    fun `the unit sits beside the number`() = runComposeUiTest {
        unitField(70.0, "%", NumberRange(0.0, 100.0))
        onNodeWithText("%").assertExists()
        onNodeWithText("70").assertExists()
    }

    @Test
    fun `typing applies on Enter, not before`() = runComposeUiTest {
        val value = unitField(70.0, "%", NumberRange(0.0, 100.0))
        input().requestFocus()
        input().performTextReplacement("45")
        waitForIdle()
        assertEquals(70.0, value())
        input().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(45.0, value())
    }

    @Test
    fun `negative and decimal values follow the range`() = runComposeUiTest {
        val value = unitField(0.0, "°", NumberRange(-180.0, 180.0))
        input().requestFocus()
        input().performTextReplacement("-45")
        input().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(-45.0, value())
        input().performTextReplacement("-400")
        waitForIdle()
        onNodeWithText("Use the range").assertExists()
        onAllNodes(hasSetTextAction())[1].requestFocus()
        waitForIdle()
        assertEquals(-180.0, value())
    }

    @Test
    fun `half steps with one decimal`() = runComposeUiTest {
        val value = unitField(2.5, "sec", NumberRange(0.0, 30.0, step = 0.5, decimals = 1))
        input().requestFocus()
        input().performKeyInput { pressKey(Key.DirectionUp) }
        waitForIdle()
        assertEquals(3.0, value())
        onNodeWithText("3.0").assertExists()
    }

    @Test
    fun `the box is 76dp wide`() = runComposeUiTest {
        setContent {
            HosannaSurface { UnitField(70.0, {}, "%", NumberRange(0.0, 100.0), Modifier.testTag("box")) }
        }
        onNodeWithTag("box").assertWidthIsEqualTo(NumberFieldDefaults.UnitWidth)
    }
}
