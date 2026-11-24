@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.field

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performMouseInput
import kotlin.test.Test
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.model.TimerPreset
import org.churchpresenter.hosanna.composables.model.TimerSpinnerLabels
import org.churchpresenter.hosanna.composables.model.TimerUnit
import org.churchpresenter.hosanna.composables.testing.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import org.churchpresenter.hosanna.composables.testing.hosannaPopup

class TimerSpinnerScreenshotTest {

    private val section = "field/timerSpinner"
    private val labels = TimerSpinnerLabels("Hours", "Min", "Sec", "Increase", "Decrease")
    private val presets = listOf(
        TimerPreset("1 min", 60),
        TimerPreset("5 min", 300),
        TimerPreset("10 min", 600),
        TimerPreset("15 min", 900),
        TimerPreset("30 min", 1800),
    )

    @Test
    fun rest() = hosannaComponent(section, "rest") { TimerSpinner(300, {}, labels, presets = presets) }

    @Test
    fun stepperHovered() = hosannaComponent(section, "stepper_hovered") {
        TimerSpinner(
            3725, {}, labels,
            interactionSources = remember {
                TimerUnit.entries.associateWith {
                    if (it == TimerUnit.Minutes) frozenSource(ButtonState.Hovered) else MutableInteractionSource()
                }
            },
        )
    }

    @Test
    fun buttonHovered() = hosannaPopup(
        section,
        "button_hovered",
        keepHover = true,
        drive = { onAllNodesWithContentDescription("Increase")[1].performMouseInput { moveTo(center) } },
    ) { TimerSpinner(3725, {}, labels) }

    @Test
    fun buttonPressed() = hosannaPopup(
        section,
        "button_pressed",
        keepHover = true,
        drive = {
            onAllNodesWithContentDescription("Increase")[1].performMouseInput {
                moveTo(center)
                press()
            }
        },
    ) { TimerSpinner(3725, {}, labels) }

    @Test
    fun editing() = hosannaPopup(
        section,
        "editing",
        drive = { onAllNodesWithText("02")[0].performMouseInput { click(center) } },
    ) { TimerSpinner(3725, {}, labels) }

    @Test
    fun atLimit() = hosannaComponent(section, "at_limit") { TimerSpinner(0, {}, labels) }

    @Test
    fun disabled() = hosannaComponent(section, "disabled") {
        TimerSpinner(300, {}, labels, presets = presets, enabled = false)
    }
}
