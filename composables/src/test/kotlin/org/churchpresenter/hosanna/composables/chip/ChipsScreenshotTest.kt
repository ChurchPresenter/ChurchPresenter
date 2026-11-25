package org.churchpresenter.hosanna.composables.chip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import org.churchpresenter.hosanna.composables.button.icon
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.model.ChipColor
import org.churchpresenter.hosanna.composables.model.ChipSize
import org.churchpresenter.hosanna.composables.testing.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import org.churchpresenter.hosanna.composables.testing.testIcon

class ChipsScreenshotTest {

    private val states = listOf(
        ButtonState.Rest to "rest",
        ButtonState.Hovered to "hovered",
        ButtonState.Pressed to "pressed",
        ButtonState.Focused to "focused",
        ButtonState.Disabled to "disabled",
    )

    @Composable
    private fun Wrap(content: @Composable () -> Unit) {
        FlowRow(
            modifier = Modifier.width(720.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { content() }
    }

    @Composable
    private fun Action(state: ButtonState) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChipSize.entries.forEach { size ->
                Wrap {
                    ChipColor.entries.forEach { color ->
                        ActionChip(
                            color.name, {}, interactionSource = remember { frozenSource(state) },
                            icon = testIcon(), size = size, color = color, enabled = state != ButtonState.Disabled,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun Filter(state: ButtonState) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(false, true).forEach { selected ->
                ChipSize.entries.forEach { size ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf(ChipColor.Neutral, ChipColor.Chorus, ChipColor.Bible).forEach { color ->
                            FilterChip(
                                color.name, selected, {}, interactionSource = remember { frozenSource(state) },
                                size = size, color = color, enabled = state != ButtonState.Disabled,
                            )
                        }
                        FilterChip(
                            "With icon", selected, {}, interactionSource = remember { frozenSource(state) },
                            icon = testIcon(), size = size, enabled = state != ButtonState.Disabled,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun Input(state: ButtonState, removeState: ButtonState = ButtonState.Rest) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChipSize.entries.forEach { size ->
                Wrap {
                    ChipColor.entries.forEach { color ->
                        InputChip(
                            color.name, {}, "Remove", interactionSource = remember { frozenSource(state) },
                            removeInteractionSource = remember { frozenSource(removeState) },
                            dragHandle = color == ChipColor.Neutral, size = size, color = color,
                            enabled = state != ButtonState.Disabled,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun Favorite(state: ButtonState, starState: ButtonState = ButtonState.Rest) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                Triple("Easter service.pptx", false, false),
                Triple("Christmas.pptx", true, false),
                Triple("Sunday morning.pdf", false, true),
                Triple("A very long presentation file name that does not fit.pptx", true, true),
            ).forEach { (name, favorite, open) ->
                FavoriteFileChip(
                    name, {}, favorite, {}, "Favorite",
                    interactionSource = remember { frozenSource(state) },
                    starInteractionSource = remember { frozenSource(starState) },
                    icon = testIcon(), open = open, enabled = state != ButtonState.Disabled,
                )
            }
        }
    }

    @Test
    fun actionChip() = states.forEach { (state, name) ->
        hosannaComponent("chip/actionChip", name) { Action(state) }
    }

    @Test
    fun filterChip() = states.forEach { (state, name) ->
        hosannaComponent("chip/filterChip", name) { Filter(state) }
    }

    @Test
    fun inputChip() = states.forEach { (state, name) ->
        hosannaComponent("chip/inputChip", name) { Input(state) }
    }

    @Test
    fun inputChipRemoveHovered() =
        hosannaComponent("chip/inputChip", "remove_hovered") { Input(ButtonState.Rest, ButtonState.Hovered) }

    @Test
    fun favoriteFileChip() = states.forEach { (state, name) ->
        hosannaComponent("chip/favoriteFileChip", name) { Favorite(state) }
    }

    @Test
    fun favoriteFileChipStarHovered() =
        hosannaComponent("chip/favoriteFileChip", "star_hovered") { Favorite(ButtonState.Rest, ButtonState.Hovered) }

    @Test
    fun tag() = hosannaComponent("chip/tag", "all_colors") {
        Wrap { ChipColor.entries.forEach { Tag(it.name, color = it) } }
    }
}
