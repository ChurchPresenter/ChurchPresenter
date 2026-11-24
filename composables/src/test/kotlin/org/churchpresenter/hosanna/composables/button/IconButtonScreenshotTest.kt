package org.churchpresenter.hosanna.composables.button

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.hosanna.composables.model.IconButtonSize
import org.churchpresenter.hosanna.composables.testing.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import org.churchpresenter.hosanna.composables.testing.testIcon

class IconButtonScreenshotTest {

    private val section = "button/iconButton"

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
                            val source = remember { frozenSource(state) }
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
