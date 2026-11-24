@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.field

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.model.NumberFieldActions
import org.churchpresenter.hosanna.composables.model.NumberRange
import org.churchpresenter.hosanna.composables.testing.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import org.churchpresenter.hosanna.composables.testing.hosannaPopup

class NumberFieldScreenshotTest {

    private val section = "field/numberField"
    private val actions = NumberFieldActions("Increase", "Decrease")
    private val width = Modifier.width(200.dp)

    @Composable
    private fun Numbers(state: ButtonState = ButtonState.Rest, enabled: Boolean = true) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            NumberField(
                0.0, {}, "Loop count", NumberRange(0.0, 99.0), actions, width,
                interactionSource = remember { frozenSource(state) }, helper = "0 = loop forever", enabled = enabled,
            )
            NumberField(
                24.0, {}, "Photos", NumberRange(1.0, 200.0), actions, width,
                interactionSource = remember { frozenSource(state) }, enabled = enabled,
            )
            NumberField(
                1920.0, {}, "Width", NumberRange(1.0, 7680.0), actions, width,
                interactionSource = remember { frozenSource(state) }, enabled = enabled,
            )
        }
    }

    @Composable
    private fun Units(state: ButtonState = ButtonState.Rest, enabled: Boolean = true) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            UnitField(
                70.0, {}, "%", NumberRange(0.0, 100.0),
                interactionSource = remember { frozenSource(state) }, enabled = enabled,
            )
            UnitField(
                -45.0, {}, "°", NumberRange(-180.0, 180.0),
                interactionSource = remember { frozenSource(state) }, enabled = enabled,
            )
            UnitField(
                2.5, {}, "sec", NumberRange(0.0, 30.0, step = 0.5, decimals = 1),
                interactionSource = remember { frozenSource(state) }, enabled = enabled,
            )
        }
    }

    @Test
    fun numberRest() = hosannaComponent(section, "number_rest") { Numbers() }

    @Test
    fun numberHovered() = hosannaComponent(section, "number_hovered") { Numbers(ButtonState.Hovered) }

    @Test
    fun numberFocused() = hosannaComponent(section, "number_focused") { Numbers(ButtonState.Focused) }

    @Test
    fun numberError() = hosannaPopup(
        section,
        "number_error",
        drive = { onAllNodes(hasSetTextAction())[0].performTextReplacement("500") },
    ) {
        NumberField(50.0, {}, "Photos", NumberRange(1.0, 200.0), actions, width, rangeError = "Use 1 to 200")
    }

    @Test
    fun numberDisabled() = hosannaComponent(section, "number_disabled") { Numbers(enabled = false) }

    @Test
    fun arrowHovered() = hosannaPopup(
        section,
        "arrow_hovered",
        keepHover = true,
        drive = { onAllNodesWithContentDescription("Increase")[0].performMouseInput { moveTo(center) } },
    ) { NumberField(24.0, {}, "Photos", NumberRange(1.0, 200.0), actions, width) }

    @Test
    fun arrowPressed() = hosannaPopup(
        section,
        "arrow_pressed",
        keepHover = true,
        drive = {
            onAllNodesWithContentDescription("Increase")[0].performMouseInput {
                moveTo(center)
                press()
            }
        },
    ) { NumberField(24.0, {}, "Photos", NumberRange(1.0, 200.0), actions, width) }

    @Test
    fun unitRest() = hosannaComponent(section, "unit_rest") { Units() }

    @Test
    fun unitFocused() = hosannaComponent(section, "unit_focused") { Units(ButtonState.Focused) }

    @Test
    fun unitError() = hosannaPopup(
        section,
        "unit_error",
        drive = { onAllNodes(hasSetTextAction())[0].performTextReplacement("140") },
    ) { UnitField(70.0, {}, "%", NumberRange(0.0, 100.0), rangeError = "Use 0 to 100") }

    @Test
    fun unitDisabled() = hosannaComponent(section, "unit_disabled") { Units(enabled = false) }
}
