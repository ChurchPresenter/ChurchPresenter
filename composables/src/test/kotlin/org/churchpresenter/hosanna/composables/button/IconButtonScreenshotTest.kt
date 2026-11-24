package org.churchpresenter.hosanna.composables.button

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.testing.FrozenInteractionSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import org.churchpresenter.hosanna.composables.testing.testIcon
import kotlin.test.Test

class IconButtonScreenshotTest {

    private val section = "button/iconButton"

    private fun source(state: ButtonState): MutableInteractionSource = when (state) {
        ButtonState.Hovered -> FrozenInteractionSource(HoverInteraction.Enter())
        ButtonState.Pressed -> FrozenInteractionSource(PressInteraction.Press(Offset.Zero))
        ButtonState.Focused -> FrozenInteractionSource(FocusInteraction.Focus())
        else -> MutableInteractionSource()
    }

    @Composable
    private fun Grid(state: ButtonState, selected: Boolean = false) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ButtonHierarchy.entries.forEach { hierarchy ->
                ButtonIntent.entries.forEach { intent ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButtonSize.entries.forEach { size ->
                            val source = remember { source(state) }
                            val enabled = state != ButtonState.Disabled
                            val loading = state == ButtonState.Loading
                            if (selected) {
                                IconToggleButton(
                                    testIcon(), "Show on screen", true, {}, interactionSource = source,
                                    size = size, hierarchy = hierarchy, intent = intent,
                                    enabled = enabled, loading = loading,
                                )
                            } else {
                                IconButton(
                                    testIcon(), "Present", {}, interactionSource = source,
                                    size = size, hierarchy = hierarchy, intent = intent,
                                    enabled = enabled, loading = loading,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun rest() = hosannaComponent(section, "rest") { Grid(ButtonState.Rest) }

    @Test
    fun hovered() = hosannaComponent(section, "hovered") { Grid(ButtonState.Hovered) }

    @Test
    fun pressed() = hosannaComponent(section, "pressed") { Grid(ButtonState.Pressed) }

    @Test
    fun focused() = hosannaComponent(section, "focused") { Grid(ButtonState.Focused) }

    @Test
    fun disabled() = hosannaComponent(section, "disabled") { Grid(ButtonState.Disabled) }

    @Test
    fun loading() = hosannaComponent(section, "loading") { Grid(ButtonState.Loading) }

    @Test
    fun selected() = hosannaComponent(section, "selected") { Grid(ButtonState.Rest, selected = true) }

    @Test
    fun selectedHovered() = hosannaComponent(section, "selected_hovered") { Grid(ButtonState.Hovered, selected = true) }

    @Test
    fun selectedPressed() = hosannaComponent(section, "selected_pressed") { Grid(ButtonState.Pressed, selected = true) }

    @Test
    fun selectedFocused() = hosannaComponent(section, "selected_focused") { Grid(ButtonState.Focused, selected = true) }

    @Test
    fun selectedDisabled() =
        hosannaComponent(section, "selected_disabled") { Grid(ButtonState.Disabled, selected = true) }

    @Test
    fun selectedLoading() = hosannaComponent(section, "selected_loading") { Grid(ButtonState.Loading, selected = true) }
}
