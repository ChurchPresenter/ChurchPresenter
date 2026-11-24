package org.churchpresenter.hosanna.composables.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.testing.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import kotlin.test.Test

class CheckboxScreenshotTest {

    private val section = "selection/checkbox"

    @Composable
    private fun Values(state: ButtonState = ButtonState.Rest, error: Boolean = false) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ToggleableState.Off to "Unchecked",
                ToggleableState.On to "Checked",
                ToggleableState.Indeterminate to "Mixed",
            ).forEach { (value, label) ->
                TriStateCheckbox(
                    value, {}, label,
                    interactionSource = remember { frozenSource(state) },
                    error = error, enabled = state != ButtonState.Disabled,
                )
            }
            Checkbox(
                true, {}, "Show verse numbers",
                interactionSource = remember { frozenSource(state) },
                description = "Small numbers before each verse",
                enabled = state != ButtonState.Disabled,
            )
        }
    }

    @Test
    fun rest() = hosannaComponent(section, "rest") { Values() }

    @Test
    fun hovered() = hosannaComponent(section, "hovered") { Values(ButtonState.Hovered) }

    @Test
    fun pressed() = hosannaComponent(section, "pressed") { Values(ButtonState.Pressed) }

    @Test
    fun focused() = hosannaComponent(section, "focused") { Values(ButtonState.Focused) }

    @Test
    fun error() = hosannaComponent(section, "error") { Values(error = true) }

    @Test
    fun disabled() = hosannaComponent(section, "disabled") { Values(ButtonState.Disabled) }

    @Test
    fun layouts() = hosannaComponent(section, "layouts") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Checkbox(true, {}, "Whole words", compact = true)
                Checkbox(false, {}, "Match case", compact = true)
            }
            Column(Modifier.width(300.dp)) {
                Checkbox(true, {}, "Lobby screen", trailing = "6 items")
                Checkbox(false, {}, "Overflow room", trailing = "None", enabled = false)
            }
        }
    }
}
