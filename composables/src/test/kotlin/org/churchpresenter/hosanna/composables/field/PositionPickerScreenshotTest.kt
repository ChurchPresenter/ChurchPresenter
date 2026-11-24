@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.field

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.model.ScreenPosition
import org.churchpresenter.hosanna.composables.testing.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import org.churchpresenter.hosanna.composables.testing.hosannaPopup

class PositionPickerScreenshotTest {

    private val section = "field/positionPicker"
    private val label = "Position on screen"
    private val names = mapOf(
        ScreenPosition.TopLeft to "Top left",
        ScreenPosition.TopCenter to "Top center",
        ScreenPosition.TopRight to "Top right",
        ScreenPosition.MiddleLeft to "Middle left",
        ScreenPosition.Center to "Center",
        ScreenPosition.MiddleRight to "Middle right",
        ScreenPosition.BottomLeft to "Bottom left",
        ScreenPosition.BottomCenter to "Bottom center",
        ScreenPosition.BottomRight to "Bottom right",
    )

    private fun name(position: ScreenPosition) = names.getValue(position)

    @Test
    fun rest() = hosannaComponent(section, "rest") {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            PositionPicker(ScreenPosition.BottomCenter, {}, label, ::name)
            PositionPicker(ScreenPosition.Center, {}, label, ::name, compact = true)
        }
    }

    @Test
    fun cellHovered() = hosannaPopup(
        section,
        "cell_hovered",
        keepHover = true,
        drive = { onNodeWithContentDescription("Top right").performMouseInput { moveTo(center) } },
    ) { PositionPicker(ScreenPosition.BottomCenter, {}, label, ::name) }

    @Test
    fun cellPressed() = hosannaPopup(
        section,
        "cell_pressed",
        keepHover = true,
        drive = {
            onNodeWithContentDescription("Top right").performMouseInput {
                moveTo(center)
                press()
            }
        },
    ) { PositionPicker(ScreenPosition.BottomCenter, {}, label, ::name) }

    @Test
    fun focused() = hosannaComponent(section, "focused") {
        PositionPicker(
            ScreenPosition.BottomCenter, {}, label, ::name,
            interactionSource = remember { frozenSource(ButtonState.Focused) },
        )
    }

    @Test
    fun disabled() = hosannaComponent(section, "disabled") {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            PositionPicker(ScreenPosition.BottomCenter, {}, label, ::name, enabled = false)
            PositionPicker(ScreenPosition.Center, {}, label, ::name, compact = true, enabled = false)
        }
    }

    @Test
    fun everyPosition() = hosannaComponent(section, "every_position") {
        Column {
            FlowRow(
                modifier = Modifier.width(420.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ScreenPosition.entries.forEach { position ->
                    PositionPicker(position, {}, name(position), ::name, compact = true)
                }
            }
        }
    }
}
