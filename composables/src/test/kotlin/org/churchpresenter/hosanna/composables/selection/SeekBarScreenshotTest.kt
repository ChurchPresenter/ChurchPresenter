package org.churchpresenter.hosanna.composables.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.testing.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import org.churchpresenter.hosanna.composables.testing.hosannaPopup
import kotlin.test.Test

class SeekBarScreenshotTest {

    private val section = "selection/seekBar"
    private val width = Modifier.width(340.dp)

    @Composable
    private fun Variants(state: ButtonState = ButtonState.Rest) {
        val enabled = state != ButtonState.Disabled
        Column(
            Modifier.padding(top = 32.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            SeekBar(
                83.0, 214.0, {}, "Seek", width, remember { frozenSource(state) },
                buffered = 130.0, enabled = enabled,
            )
            SeekBar(
                3700.0, 5400.0, {}, "Long", width, remember { frozenSource(state) },
                buffered = 4000.0, enabled = enabled,
            )
            SeekBar(
                60.0, 214.0, {}, "Bare", width, remember { frozenSource(state) },
                showTimes = false, enabled = enabled,
            )
        }
    }

    @Test
    fun rest() = hosannaComponent(section, "rest") { Variants() }

    @Test
    fun hovered() = hosannaComponent(section, "hovered") { Variants(ButtonState.Hovered) }

    @Test
    fun dragging() = hosannaComponent(section, "dragging") { Variants(ButtonState.Pressed) }

    @Test
    fun focused() = hosannaComponent(section, "focused") { Variants(ButtonState.Focused) }

    @Test
    fun disabled() = hosannaComponent(section, "disabled") { Variants(ButtonState.Disabled) }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun pointer() = hosannaPopup(
        section, "pointer", keepHover = true,
        drive = { onNodeWithContentDescription("Seek").performMouseInput { moveTo(Offset(width * 0.75f, centerY)) } },
    ) {
        Column(Modifier.padding(top = 32.dp, start = 8.dp, end = 8.dp)) {
            SeekBar(83.0, 214.0, {}, "Seek", width, buffered = 130.0)
        }
    }
}
