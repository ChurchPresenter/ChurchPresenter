package org.churchpresenter.hosanna.composables.field

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.testing.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_search
import org.jetbrains.compose.resources.painterResource

class TextFieldScreenshotTest {

    private val section = "field/textField"
    private val width = Modifier.width(280.dp)

    @Composable
    private fun Kinds(
        state: ButtonState = ButtonState.Rest,
        filled: Boolean = false,
        error: String? = null,
        enabled: Boolean = true,
        readOnly: Boolean = false,
    ) {
        val title = if (filled) "Amazing Grace" else ""
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextField(
                title, {}, width, interactionSource = remember { frozenSource(state) },
                label = "Song title", placeholder = "Title", error = error, enabled = enabled, readOnly = readOnly,
            )
            TextField(
                title, {}, width, interactionSource = remember { frozenSource(state) },
                placeholder = "Search songs", leadingIcon = painterResource(Res.drawable.ic_search),
                helper = "Title, lyrics or number", error = error, enabled = enabled, readOnly = readOnly,
            )
            TextField(
                if (filled) "Sung slower on the last verse" else "", {}, width,
                interactionSource = remember { frozenSource(state) },
                label = "Comments", placeholder = "Notes", multiline = true, maxLength = 120,
                error = error, enabled = enabled, readOnly = readOnly,
            )
        }
    }

    @Test
    fun rest() = hosannaComponent(section, "rest") { Kinds() }

    @Test
    fun hovered() = hosannaComponent(section, "hovered") { Kinds(ButtonState.Hovered) }

    @Test
    fun focused() = hosannaComponent(section, "focused") { Kinds(ButtonState.Focused) }

    @Test
    fun filled() = hosannaComponent(section, "filled") { Kinds(filled = true) }

    @Test
    fun error() = hosannaComponent(section, "error") { Kinds(filled = true, error = "Required") }

    @Test
    fun errorFocused() =
        hosannaComponent(section, "error_focused") { Kinds(ButtonState.Focused, filled = true, error = "Required") }

    @Test
    fun disabled() = hosannaComponent(section, "disabled") { Kinds(filled = true, enabled = false) }

    @Test
    fun readOnly() = hosannaComponent(section, "read_only") { Kinds(filled = true, readOnly = true) }

    @Test
    fun extras() = hosannaComponent(section, "extras") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextField("1920", {}, width, label = "Width", suffix = "px")
            TextField("Too long for the limit", {}, width, helper = "Short name", maxLength = 10)
            TextField(
                "Grace", {}, width,
                interactionSource = remember { frozenSource(ButtonState.Hovered) },
                leadingIcon = painterResource(Res.drawable.ic_search),
                clearContentDescription = "Clear",
            )
            TextField("", {}, width, placeholder = "Add a question")
        }
    }
}
