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

class SliderScreenshotTest {

    private val section = "selection/slider"
    private val width = Modifier.width(300.dp)

    @Composable
    private fun Variants(state: ButtonState = ButtonState.Rest) {
        val enabled = state != ButtonState.Disabled
        Column(
            Modifier.padding(top = 32.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            Slider(60.0, {}, "Opacity", width, remember { frozenSource(state) }, valueText = "60%", enabled = enabled)
            Slider(
                3.0, {}, "Rating", width, remember { frozenSource(state) },
                range = NumberRange(0.0, 5.0), valueText = "3", enabled = enabled,
            )
            Slider(
                -2.0, {}, "Transpose", width, remember { frozenSource(state) },
                range = NumberRange(-6.0, 6.0), valueText = "−2", centered = true,
                startLabel = "−6", endLabel = "+6", enabled = enabled,
            )
            Slider(30.0, {}, "Plain", width, remember { frozenSource(state) }, enabled = enabled)
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

    @Test
    fun centered() = hosannaComponent(section, "centered") {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Slider(0.0, {}, "At zero", width, range = NumberRange(-6.0, 6.0), centered = true)
            Slider(3.0, {}, "Signed", width, range = NumberRange(-6.0, 6.0), valueText = "+3", centered = true)
            Slider(
                0.0, {}, "Balance", width, range = NumberRange(-50.0, 50.0),
                valueText = "Center", centered = true, startLabel = "L", endLabel = "R",
            )
        }
    }
}
