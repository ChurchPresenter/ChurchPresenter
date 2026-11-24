@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.field

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.button.ButtonState
import org.churchpresenter.hosanna.composables.button.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import org.churchpresenter.hosanna.composables.testing.hosannaPopup
import kotlin.test.Test

class ColorFieldScreenshotTest {

    private val section = "field/colorField"
    private val labels =
        ColorFieldLabels("Pick a color", "None", "Opacity", "Theme", "Recent", "Use a color like #1F5BB0")
    private val theme = listOf(
        Color(0xFF1F5BB0),
        Color(0xFFC43030),
        Color(0xFF0F7F86),
        Color(0xFFE8A33D),
        Color(0xFF8340B5),
        Color(0xFF1B1F24),
        Color(0xFFFFFFFF),
        Color(0xFF6B7079),
    )
    private val recent = listOf(Color(0xFFF0C27A), Color(0xFF2F6FD0), Color(0xFF14855A))

    @Composable
    private fun Fields(state: ButtonState = ButtonState.Rest, enabled: Boolean = true) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ColorField(
                Color(0xFF1F5BB0), {}, "Text", labels,
                interactionSource = remember { frozenSource(state) }, enabled = enabled,
            )
            ColorField(
                Color(0xB31B1F24), {}, "Background", labels,
                interactionSource = remember { frozenSource(state) }, allowOpacity = true, enabled = enabled,
            )
            ColorField(
                null, {}, "Outline", labels,
                interactionSource = remember { frozenSource(state) }, allowNone = true, enabled = enabled,
            )
        }
    }

    @Composable
    private fun Anchored(allowOpacity: Boolean) {
        Box(Modifier.size(300.dp, 480.dp)) {
            ColorField(
                if (allowOpacity) Color(0xB3C43030) else Color(0xFFC43030), {}, "Accent", labels,
                allowOpacity = allowOpacity, themeColors = theme, recentColors = recent,
            )
        }
    }

    @Test
    fun rest() = hosannaComponent(section, "rest") { Fields() }

    @Test
    fun hovered() = hosannaComponent(section, "hovered") { Fields(ButtonState.Hovered) }

    @Test
    fun focused() = hosannaPopup(
        section,
        "focused",
        drive = { onAllNodes(hasSetTextAction())[0].requestFocus() },
    ) { Box(Modifier.size(260.dp, 80.dp)) { ColorField(Color(0xFF1F5BB0), {}, "Text", labels) } }

    @Test
    fun error() = hosannaPopup(
        section,
        "error",
        drive = {
            val inputs = onAllNodes(hasSetTextAction())
            inputs[0].requestFocus()
            inputs[0].performTextReplacement("blue-ish")
            inputs[1].requestFocus()
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            var first by remember { mutableStateOf<Color?>(Color(0xFF1F5BB0)) }
            ColorField(first, { first = it }, "Text", labels)
            ColorField(Color(0xFF14855A), {}, "Other", labels)
        }
    }

    @Test
    fun disabled() = hosannaComponent(section, "disabled") { Fields(enabled = false) }

    @Test
    fun open() = hosannaPopup(
        section,
        "open",
        drive = { onAllNodesWithContentDescription("Pick a color")[1].performMouseInput { click(center) } },
    ) { Anchored(allowOpacity = true) }

    @Test
    fun openWithoutOpacity() = hosannaPopup(
        section,
        "open_without_opacity",
        drive = { onAllNodesWithContentDescription("Pick a color")[1].performMouseInput { click(center) } },
    ) { Anchored(allowOpacity = false) }
}
