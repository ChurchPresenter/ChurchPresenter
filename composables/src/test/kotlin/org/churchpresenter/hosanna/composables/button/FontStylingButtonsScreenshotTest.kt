package org.churchpresenter.hosanna.composables.button

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import org.churchpresenter.theme.AppTheme
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.model.TextStyleToggle
import org.churchpresenter.hosanna.composables.testing.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_text_backing
import org.churchpresenter.icons.generated.resources.ic_text_outline
import org.jetbrains.compose.resources.painterResource

class FontStylingButtonsScreenshotTest {

    private val section = "button/fontStyling"

    private val mixedStates = mapOf(
        TextStyleToggle.Bold to ToggleableState.On,
        TextStyleToggle.Italic to ToggleableState.Indeterminate,
    )

    @Composable
    private fun Toolbar(
        state: ButtonState = ButtonState.Rest,
        states: Map<TextStyleToggle, ToggleableState> = mixedStates,
        enabled: Boolean = true,
        freezeOptions: Boolean = false,
    ) {
        val sources = remember {
            TextStyleToggle.entries.associateWith {
                val rest = freezeOptions || it == TextStyleToggle.Strikethrough
                if (rest) MutableInteractionSource() else frozenSource(state)
            }
        }
        val mainSource = remember { if (freezeOptions) MutableInteractionSource() else frozenSource(state) }
        val optionsSource = remember { if (freezeOptions) frozenSource(state) else MutableInteractionSource() }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextStyleToggles(states, {}, { it.name }, interactionSources = sources, enabled = enabled)
            listOf(false, true).forEach { checked ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SplitToggleButton(
                        painterResource(Res.drawable.ic_text_outline), "Outline", checked, {}, {}, "Outline options",
                        interactionSource = mainSource,
                        optionsInteractionSource = optionsSource,
                        enabled = enabled,
                    )
                    SplitToggleButton(
                        painterResource(Res.drawable.ic_text_backing), "Backing", checked, {}, {}, "Backing options",
                        interactionSource = mainSource,
                        optionsInteractionSource = optionsSource,
                        indicatorColor = AppTheme.colors.star,
                        enabled = enabled,
                    )
                }
            }
        }
    }

    @Test
    fun allOff() = hosannaComponent(section, "all_off") { Toolbar(states = emptyMap()) }

    @Test
    fun rest() = hosannaComponent(section, "rest") { Toolbar() }

    @Test
    fun hovered() = hosannaComponent(section, "hovered") { Toolbar(ButtonState.Hovered) }

    @Test
    fun pressed() = hosannaComponent(section, "pressed") { Toolbar(ButtonState.Pressed) }

    @Test
    fun focused() = hosannaComponent(section, "focused") { Toolbar(ButtonState.Focused) }

    @Test
    fun optionsHovered() =
        hosannaComponent(section, "options_hovered") { Toolbar(ButtonState.Hovered, freezeOptions = true) }

    @Test
    fun optionDisabled() = hosannaComponent(section, "option_disabled") {
        TextStyleToggles(mixedStates, {}, { it.name }, disabled = setOf(TextStyleToggle.Italic))
    }

    @Test
    fun disabled() = hosannaComponent(section, "disabled") { Toolbar(enabled = false) }
}
