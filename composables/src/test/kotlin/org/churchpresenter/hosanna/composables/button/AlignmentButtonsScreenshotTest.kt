package org.churchpresenter.hosanna.composables.button

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.model.LayerAlignment
import org.churchpresenter.hosanna.composables.model.TextAlignment
import org.churchpresenter.hosanna.composables.testing.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent

class AlignmentButtonsScreenshotTest {

    private val section = "button/alignmentButtons"

    private val frozenStates = listOf(
        ButtonState.Hovered to "hovered",
        ButtonState.Pressed to "pressed",
        ButtonState.Focused to "focused",
    )

    private fun source(frozen: Boolean, state: ButtonState): MutableInteractionSource =
        if (frozen) frozenSource(state) else MutableInteractionSource()

    @Composable
    private fun textSources(state: ButtonState) = remember {
        TextAlignment.entries.associateWith {
            source(it == TextAlignment.Right || it == TextAlignment.Middle, state)
        }
    }

    @Composable
    private fun layerSources(state: ButtonState) = remember {
        LayerAlignment.entries.associateWith {
            source(it == LayerAlignment.Center || it == LayerAlignment.Bottom, state)
        }
    }

    @Composable
    private fun Text(
        frozen: Map<TextAlignment, MutableInteractionSource>? = null,
        horizontal: TextAlignment? = TextAlignment.Center,
        vertical: TextAlignment? = TextAlignment.Middle,
        enabled: Boolean = true,
        disabled: Set<TextAlignment> = emptySet(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(false, true).forEach { compact ->
                TextAlignmentButtons(
                    horizontal, vertical, {}, { it.name },
                    interactionSources = frozen ?: textSources(ButtonState.Rest),
                    compact = compact, enabled = enabled, disabled = disabled,
                )
            }
        }
    }

    @Composable
    private fun Layer(frozen: Map<LayerAlignment, MutableInteractionSource>? = null, enabled: Boolean = true) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(false, true).forEach { compact ->
                LayerAlignmentButtons(
                    {}, { it.name },
                    interactionSources = frozen ?: layerSources(ButtonState.Rest),
                    compact = compact, enabled = enabled,
                )
            }
        }
    }

    @Test
    fun textRest() = hosannaComponent(section, "text_rest") { Text() }

    @Test
    fun textStates() = frozenStates.forEach { (state, name) ->
        hosannaComponent(section, "text_$name") { Text(textSources(state)) }
    }

    @Test
    fun textMixed() = hosannaComponent(section, "text_mixed") { Text(horizontal = null, vertical = null) }

    @Test
    fun textOptionDisabled() = hosannaComponent(section, "text_option_disabled") {
        Text(horizontal = TextAlignment.Left, vertical = TextAlignment.Top, disabled = setOf(TextAlignment.Justify))
    }

    @Test
    fun textDisabled() = hosannaComponent(section, "text_disabled") { Text(enabled = false) }

    @Test
    fun layerRest() = hosannaComponent(section, "layer_rest") { Layer() }

    @Test
    fun layerStates() = frozenStates.forEach { (state, name) ->
        hosannaComponent(section, "layer_$name") { Layer(layerSources(state)) }
    }

    @Test
    fun layerDisabled() = hosannaComponent(section, "layer_disabled") { Layer(enabled = false) }

    @Test
    fun stacked() = hosannaComponent(section, "stacked") {
        Box(Modifier.width(200.dp)) { TextAlignmentButtons(TextAlignment.Left, TextAlignment.Top, {}, { it.name }) }
    }
}
