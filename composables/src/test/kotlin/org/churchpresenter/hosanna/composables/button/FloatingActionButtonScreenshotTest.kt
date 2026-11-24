package org.churchpresenter.hosanna.composables.button

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.hosanna.composables.model.FabSize
import org.churchpresenter.hosanna.composables.testing.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import org.churchpresenter.hosanna.composables.testing.testIcon

class FloatingActionButtonScreenshotTest {

    private val section = "button/floatingActionButton"

    private val kinds = listOf(
        ButtonIntent.Default to false,
        ButtonIntent.Critical to false,
        ButtonIntent.Neutral to false,
        ButtonIntent.Default to true,
    )

    @Composable
    private fun Grid(state: ButtonState) {
        val enabled = state != ButtonState.Disabled
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            kinds.forEach { (intent, tonal) ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FabSize.entries.forEach { size ->
                        FloatingActionButton(
                            testIcon(), "Add", {}, interactionSource = remember { frozenSource(state) },
                            size = size, intent = intent, tonal = tonal, enabled = enabled,
                        )
                    }
                    ExtendedFloatingActionButton(
                        "Add layer", testIcon(), {}, interactionSource = remember { frozenSource(state) },
                        intent = intent, tonal = tonal, enabled = enabled,
                    )
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
}
