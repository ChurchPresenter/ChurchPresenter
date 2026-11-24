package org.churchpresenter.hosanna.composables.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.model.NumberRange
import org.churchpresenter.hosanna.composables.testing.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import kotlin.test.Test

class RangeSliderScreenshotTest {

    private val section = "selection/rangeSlider"
    private val width = Modifier.width(300.dp)

    @Composable
    private fun Variants(state: ButtonState = ButtonState.Rest) {
        val enabled = state != ButtonState.Disabled
        Column(
            Modifier.padding(top = 32.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            RangeSlider(
                20.0, 70.0, { _, _ -> }, "Start", "End", width,
                startInteractionSource = remember { frozenSource(state) },
                valueText = "20–70", enabled = enabled,
            )
            RangeSlider(
                1.5, 4.0, { _, _ -> }, "Start", "End", width,
                endInteractionSource = remember { frozenSource(state) },
                range = NumberRange(0.0, 6.0, 0.5, 1), enabled = enabled,
            )
            RangeSlider(50.0, 50.0, { _, _ -> }, "Start", "End", width, valueText = "50–50", enabled = enabled)
        }
    }

    @Test
    fun rest() = hosannaComponent(section, "rest") { Variants() }

    @Test
    fun hovered() = hosannaComponent(section, "hovered") { Variants(ButtonState.Hovered) }

    @Test
    fun dragging() = hosannaComponent(section, "dragging") { Variants(ButtonState.Pressed) }

    @Test
    fun focused() = hosannaComponent(section, "focused") { Variants(ButtonState.Focused) }

    @Test
    fun disabled() = hosannaComponent(section, "disabled") { Variants(ButtonState.Disabled) }
}
