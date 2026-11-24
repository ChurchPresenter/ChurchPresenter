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
import org.churchpresenter.hosanna.composables.testing.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import org.churchpresenter.hosanna.composables.testing.testIcon

class StandardButtonScreenshotTest {

    private val section = "button/standardButton"

    private fun label(intent: ButtonIntent) = when (intent) {
        ButtonIntent.Default -> "Save"
        ButtonIntent.Critical -> "Delete"
        ButtonIntent.Neutral -> "Cancel"
        ButtonIntent.Success -> "Approve"
        ButtonIntent.Warning -> "Update"
    }

    @Composable
    private fun Grid(state: ButtonState) {
        val enabled = state != ButtonState.Disabled
        val loading = state == ButtonState.Loading
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ButtonHierarchy.entries.forEach { hierarchy ->
                ButtonIntent.entries.forEach { intent ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StandardButton(
                            label(intent), {}, interactionSource = remember { frozenSource(state) },
                            hierarchy = hierarchy, intent = intent, enabled = enabled, loading = loading,
                        )
                        StandardButton(
                            label(intent), {}, interactionSource = remember { frozenSource(state) },
                            hierarchy = hierarchy, intent = intent, icon = testIcon(),
                            enabled = enabled, loading = loading,
                        )
                        StandardButton(
                            testIcon(), label(intent), {}, interactionSource = remember { frozenSource(state) },
                            hierarchy = hierarchy, intent = intent, enabled = enabled, loading = loading,
                        )
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
}
