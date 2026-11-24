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
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import org.churchpresenter.hosanna.composables.testing.testIcon
import kotlin.test.Test

class SegmentedButtonScreenshotTest {

    private val section = "button/segmentedButton"

    @Composable
    private fun variants(): List<List<SegmentedOption>> = listOf(
        listOf(SegmentedOption("Timer"), SegmentedOption("Duration"), SegmentedOption("Specific Time")),
        listOf(SegmentedOption("Local File", testIcon()), SegmentedOption("Network URL", testIcon())),
        listOf(
            SegmentedOption("Left", testIcon(), iconOnly = true),
            SegmentedOption("Center", testIcon(), iconOnly = true),
            SegmentedOption("Right", testIcon(), iconOnly = true),
        ),
    )

    @Composable
    private fun sources(count: Int, frozenIndex: Int, state: ButtonState): List<MutableInteractionSource> =
        remember { List(count) { if (it == frozenIndex) frozenSource(state) else MutableInteractionSource() } }

    @Composable
    private fun Sheet(
        frozenIndex: Int = -1,
        state: ButtonState = ButtonState.Rest,
        enabled: Boolean = true,
        disableSecond: Boolean = false,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SegmentedButtonSize.entries.forEach { size ->
                variants().forEach { options ->
                    val shown = if (disableSecond) {
                        options.mapIndexed { i, o ->
                            if (i == 1) SegmentedOption(o.label, o.icon, o.iconOnly, enabled = false) else o
                        }
                    } else {
                        options
                    }
                    SegmentedButton(
                        shown, 0, {},
                        interactionSources = sources(shown.size, frozenIndex, state),
                        size = size, enabled = enabled,
                    )
                }
            }
        }
    }

    @Test
    fun rest() = hosannaComponent(section, "rest") { Sheet() }

    @Test
    fun unselectedHovered() = hosannaComponent(section, "unselected_hovered") { Sheet(1, ButtonState.Hovered) }

    @Test
    fun unselectedPressed() = hosannaComponent(section, "unselected_pressed") { Sheet(1, ButtonState.Pressed) }

    @Test
    fun unselectedFocused() = hosannaComponent(section, "unselected_focused") { Sheet(1, ButtonState.Focused) }

    @Test
    fun selectedHovered() = hosannaComponent(section, "selected_hovered") { Sheet(0, ButtonState.Hovered) }

    @Test
    fun selectedPressed() = hosannaComponent(section, "selected_pressed") { Sheet(0, ButtonState.Pressed) }

    @Test
    fun selectedFocused() = hosannaComponent(section, "selected_focused") { Sheet(0, ButtonState.Focused) }

    @Test
    fun optionDisabled() = hosannaComponent(section, "option_disabled") { Sheet(disableSecond = true) }

    @Test
    fun disabled() = hosannaComponent(section, "disabled") { Sheet(enabled = false) }

    @Test
    fun fill() = hosannaComponent(section, "fill") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(420.dp)) {
                SegmentedButton(
                    listOf(SegmentedOption("Lyrics"), SegmentedOption("Language 2")), 0, {}, fillWidth = true,
                )
            }
            Box(Modifier.width(420.dp)) {
                SegmentedButton(
                    listOf(SegmentedOption("Timer"), SegmentedOption("Duration"), SegmentedOption("Specific Time")),
                    2, {}, fillWidth = true,
                )
            }
            Box(Modifier.width(300.dp)) {
                SegmentedButton(
                    listOf(SegmentedOption("Timer"), SegmentedOption("Duration"), SegmentedOption("Specific Time")),
                    2, {}, fillWidth = true,
                )
            }
        }
    }
}
