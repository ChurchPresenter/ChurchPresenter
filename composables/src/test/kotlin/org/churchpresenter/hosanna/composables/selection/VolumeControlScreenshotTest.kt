package org.churchpresenter.hosanna.composables.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.model.VolumeLabels
import org.churchpresenter.hosanna.composables.testing.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import kotlin.test.Test

class VolumeControlScreenshotTest {

    private val section = "selection/volumeControl"
    private val labels = VolumeLabels("Volume", "Mute", "Unmute")

    @Composable
    private fun Levels(mute: ButtonState = ButtonState.Rest, slider: ButtonState = ButtonState.Rest) {
        val enabled = mute != ButtonState.Disabled
        Column(
            Modifier.padding(top = 32.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            listOf(100.0, 35.0, 0.0).forEach { level ->
                VolumeControl(
                    level, {}, labels,
                    interactionSource = remember { frozenSource(slider) },
                    muteInteractionSource = remember { frozenSource(mute) },
                    enabled = enabled,
                )
            }
        }
    }

    @Test
    fun rest() = hosannaComponent(section, "rest") { Levels() }

    @Test
    fun muteHovered() = hosannaComponent(section, "muteHovered") { Levels(mute = ButtonState.Hovered) }

    @Test
    fun mutePressed() = hosannaComponent(section, "mutePressed") { Levels(mute = ButtonState.Pressed) }

    @Test
    fun muteFocused() = hosannaComponent(section, "muteFocused") { Levels(mute = ButtonState.Focused) }

    @Test
    fun dragging() = hosannaComponent(section, "dragging") { Levels(slider = ButtonState.Pressed) }

    @Test
    fun disabled() = hosannaComponent(section, "disabled") { Levels(ButtonState.Disabled, ButtonState.Disabled) }
}
