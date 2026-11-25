package org.churchpresenter.hosanna.composables.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.model.SwitchLayout
import org.churchpresenter.hosanna.composables.testing.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import kotlin.test.Test

class SwitchScreenshotTest {

    private val section = "selection/switch"

    @Composable
    private fun Values(state: ButtonState = ButtonState.Rest) {
        val enabled = state != ButtonState.Disabled
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(false, {}, "Off", interactionSource = remember { frozenSource(state) }, enabled = enabled)
            Switch(true, {}, "On", interactionSource = remember { frozenSource(state) }, enabled = enabled)
            Switch(
                true, {}, "On with check",
                interactionSource = remember { frozenSource(state) },
                showCheck = true, enabled = enabled,
            )
            Switch(
                true, {}, "Quick upload",
                interactionSource = remember { frozenSource(state) },
                description = "Send each slide to the ATEM as it goes live",
                enabled = enabled,
            )
            Switch(
                false, {}, "Loop",
                interactionSource = remember { frozenSource(state) },
                compact = true, enabled = enabled,
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
    fun disabled() = hosannaComponent(section, "disabled") { Values(ButtonState.Disabled) }

    @Test
    fun loading() = hosannaComponent(section, "loading") {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(false, {}, "Starting", isLoading = true)
            Switch(true, {}, "Stopping", isLoading = true)
            Switch(false, {}, "Compact", compact = true, isLoading = true)
        }
    }

    @Test
    fun layouts() = hosannaComponent(section, "layouts") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(true, {}, "Chords", layout = SwitchLayout.Inline)
            Switch(
                true, {}, "Companion server", Modifier.width(360.dp),
                description = "Lets Stream Deck control the app", layout = SwitchLayout.Row,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Switch(true, {}, "Loop", compact = true)
                Switch(false, {}, "Auto-play next", compact = true)
            }
            Column(Modifier.width(360.dp)) {
                Switch(true, {}, "Remote", trailing = "3 phones connected", live = true)
                Switch(false, {}, "Stage display", trailing = "Off")
                Switch(true, {}, "Lobby screen", trailing = "Unavailable", live = true, enabled = false)
            }
        }
    }
}
