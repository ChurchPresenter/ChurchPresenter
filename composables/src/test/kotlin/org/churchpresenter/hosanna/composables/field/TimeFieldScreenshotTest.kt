@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.field

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.button.ButtonState
import org.churchpresenter.hosanna.composables.button.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import org.churchpresenter.hosanna.composables.testing.hosannaPopup
import kotlin.test.Test

class TimeFieldScreenshotTest {

    private val section = "field/timeField"
    private val labels = TimeFieldLabels("Hour", "Minute", "AM/PM", "AM", "PM")
    private val actions = TimeFieldActions("Now", "Done", "Later", "Earlier")

    @Composable
    private fun Fields(
        state: ButtonState = ButtonState.Rest,
        error: String? = null,
        enabled: Boolean = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TimeField(
                480, {}, "From", labels, actions, interactionSource = remember { frozenSource(state) },
                error = error, enabled = enabled,
            )
            TimeField(
                30, {}, "To", labels, actions, interactionSource = remember { frozenSource(state) },
                nextDayTag = "next day", error = error, enabled = enabled,
            )
            TimeField(
                1290, {}, "Start", labels, actions, interactionSource = remember { frozenSource(state) },
                use24Hour = true, error = error, enabled = enabled,
            )
        }
    }

    @Composable
    private fun Anchored(use24Hour: Boolean) {
        Box(Modifier.size(260.dp, 340.dp)) {
            TimeField(if (use24Hour) 1290 else 480, {}, "From", labels, actions, use24Hour = use24Hour)
        }
    }

    private fun ComposeUiTest.openFrom() = onNodeWithText("FROM").performMouseInput { click(center) }

    @Test
    fun rest() = hosannaComponent(section, "rest") { Fields() }

    @Test
    fun hovered() = hosannaComponent(section, "hovered") { Fields(ButtonState.Hovered) }

    @Test
    fun focused() = hosannaPopup(section, "focused", drive = { onNodeWithText("FROM").requestFocus() }) {
        Anchored(use24Hour = false)
    }

    @Test
    fun error() = hosannaComponent(section, "error") { Fields(error = "Enter a time like 9:30 AM") }

    @Test
    fun disabled() = hosannaComponent(section, "disabled") { Fields(enabled = false) }

    @Test
    fun open() = hosannaPopup(section, "open", drive = { openFrom() }) { Anchored(use24Hour = false) }

    @Test
    fun open24Hour() = hosannaPopup(section, "open_24_hour", drive = { openFrom() }) { Anchored(use24Hour = true) }
}
