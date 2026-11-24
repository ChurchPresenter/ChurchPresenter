@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.field

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.button.ButtonState
import org.churchpresenter.hosanna.composables.button.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import org.churchpresenter.hosanna.composables.testing.hosannaPopup
import kotlin.test.Test

class DropdownScreenshotTest {

    private val section = "field/dropdown"
    private val width = Modifier.width(260.dp)
    private val sort = listOf(
        MenuEntry.Header("Time"),
        MenuEntry.Item("new", "Newest First"),
        MenuEntry.Item("old", "Oldest First"),
        MenuEntry.Divider,
        MenuEntry.Header("Votes"),
        MenuEntry.Item("most", "Most Votes", count = "12"),
        MenuEntry.Item("least", "Least Votes", count = "3"),
        MenuEntry.Item("speaker", "By speaker", supporting = "Needs speaker names on", enabled = false),
    )
    private val bibles = (1..16).map {
        MenuEntry.Item("b$it", "Bible translation $it", supporting = if (it % 3 == 0) "Russian" else null)
    }
    private val search = MenuSearch("Search Bibles", "Clear", "Nothing matches")

    @Composable
    private fun Fields(
        state: ButtonState = ButtonState.Rest,
        error: String? = null,
        enabled: Boolean = true,
        readOnly: Boolean = false,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Dropdown(
                sort, null, {}, width, interactionSource = remember { frozenSource(state) },
                label = "Sort", placeholder = "Choose…", error = error, enabled = enabled, readOnly = readOnly,
            )
            Dropdown(
                sort, "most", {}, width, interactionSource = remember { frozenSource(state) },
                label = "Sort", error = error, enabled = enabled, readOnly = readOnly,
            )
            Dropdown(
                sort, "new", {}, width, interactionSource = remember { frozenSource(state) },
                placeholder = "Scope", error = error, enabled = enabled, readOnly = readOnly,
            )
        }
    }

    @Composable
    private fun Anchored(content: @Composable () -> Unit) {
        Box(Modifier.size(300.dp, 460.dp)) { content() }
    }

    private fun ComposeUiTest.openSort() = onNodeWithText("SORT").performMouseInput { click(center) }

    private fun ComposeUiTest.menuKey(key: Key) = onNodeWithText("TIME").performKeyInput { pressKey(key) }

    @Test
    fun fieldRest() = hosannaComponent(section, "field_rest") { Fields() }

    @Test
    fun fieldHovered() = hosannaComponent(section, "field_hovered") { Fields(ButtonState.Hovered) }

    @Test
    fun fieldFocused() = hosannaComponent(section, "field_focused") { Fields(ButtonState.Focused) }

    @Test
    fun fieldError() = hosannaComponent(section, "field_error") { Fields(error = "Pick one to show") }

    @Test
    fun fieldDisabled() = hosannaComponent(section, "field_disabled") { Fields(enabled = false) }

    @Test
    fun fieldReadOnly() = hosannaComponent(section, "field_read_only") { Fields(readOnly = true) }

    @Test
    fun menu() = hosannaPopup(section, "menu", drive = { openSort() }) {
        Anchored { Dropdown(sort, "most", {}, width, label = "Sort") }
    }

    @Test
    fun menuRowHovered() = hosannaPopup(
        section,
        "menu_row_hovered",
        keepHover = true,
        drive = {
            openSort()
            mainClock.advanceTimeBy(400)
            onNodeWithText("Least Votes").performMouseInput { moveTo(center) }
        },
    ) {
        Anchored { Dropdown(sort, "most", {}, width, label = "Sort") }
    }

    @Test
    fun menuKeyboard() = hosannaPopup(
        section,
        "menu_keyboard",
        drive = {
            openSort()
            mainClock.advanceTimeBy(400)
            menuKey(Key.DirectionDown)
        },
    ) {
        Anchored { Dropdown(sort, "new", {}, width, label = "Sort") }
    }

    @Test
    fun menuSearch() = hosannaPopup(section, "menu_search", drive = { openSort() }) {
        Anchored { Dropdown(bibles, "b6", {}, width, label = "Sort", search = search) }
    }

    @Test
    fun menuNothingMatches() = hosannaPopup(
        section,
        "menu_nothing_matches",
        drive = {
            openSort()
            mainClock.advanceTimeBy(400)
            onNode(hasSetTextAction()).performTextInput("zzz")
        },
    ) {
        Anchored { Dropdown(bibles, "b6", {}, width, label = "Sort", search = search) }
    }
}
