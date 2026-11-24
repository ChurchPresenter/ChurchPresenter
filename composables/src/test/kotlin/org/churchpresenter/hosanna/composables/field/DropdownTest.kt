@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.field

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import kotlin.test.Test
import kotlin.test.assertEquals

class DropdownTest {

    private val width = Modifier.width(260.dp)
    private val entries = listOf(
        MenuEntry.Header("Time"),
        MenuEntry.Item("new", "Newest First"),
        MenuEntry.Item("old", "Oldest First"),
        MenuEntry.Divider,
        MenuEntry.Item("most", "Most Votes", count = "12"),
        MenuEntry.Item("speaker", "By speaker", supporting = "Needs speaker names on", enabled = false),
    )
    private val many = (1..14).map { MenuEntry.Item("b$it", "Bible $it") }

    private fun SemanticsNodeInteraction.mouseClick() = performMouseInput { click(center) }

    private fun ComposeUiTest.open(label: String) {
        onNodeWithText(label).mouseClick()
        waitForIdle()
    }

    @Test
    fun `the field shows the chosen label, or the placeholder`() = runComposeUiTest {
        var key by mutableStateOf<String?>(null)
        setContent {
            HosannaSurface { Dropdown(entries, key, { key = it }, width, label = "Sort", placeholder = "Choose…") }
        }
        onNodeWithText("SORT").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
        onNodeWithText("Choose…").assertExists()
        key = "most"
        waitForIdle()
        onNodeWithText("Most Votes").assertExists()
    }

    @Test
    fun `clicking opens the menu, picking a row selects it and closes`() = runComposeUiTest {
        var key by mutableStateOf<String?>("new")
        setContent { HosannaSurface { Dropdown(entries, key, { key = it }, width, label = "Sort") } }
        open("SORT")
        onNodeWithText("TIME").assertExists()
        onNodeWithText("Most Votes").mouseClick()
        waitForIdle()
        assertEquals("most", key)
        onNodeWithText("TIME").assertDoesNotExist()
    }

    @Test
    fun `a disabled row cannot be picked`() = runComposeUiTest {
        var key by mutableStateOf<String?>("new")
        setContent { HosannaSurface { Dropdown(entries, key, { key = it }, width, label = "Sort") } }
        open("SORT")
        onNodeWithText("By speaker").assertIsNotEnabled().mouseClick()
        waitForIdle()
        assertEquals("new", key)
    }

    @Test
    fun `the keyboard opens, moves, picks and closes`() = runComposeUiTest {
        var key by mutableStateOf<String?>("new")
        setContent { HosannaSurface { Dropdown(entries, key, { key = it }, width, label = "Sort") } }
        val field = onNodeWithText("SORT")
        field.requestFocus()
        field.performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        val menu = onNodeWithText("TIME")
        menu.performKeyInput { pressKey(Key.DirectionDown) }
        menu.performKeyInput { pressKey(Key.DirectionDown) }
        menu.performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals("most", key)
        field.performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        onNodeWithText("TIME").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        onNodeWithText("TIME").assertDoesNotExist()
        assertEquals("most", key)
    }

    @Test
    fun `typing a letter jumps to the first match`() = runComposeUiTest {
        var key by mutableStateOf<String?>("new")
        setContent { HosannaSurface { Dropdown(entries, key, { key = it }, width, label = "Sort") } }
        open("SORT")
        val menu = onNodeWithText("TIME")
        menu.performKeyInput { pressKey(Key.M) }
        menu.performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals("most", key)
    }

    @Test
    fun `a long list gets a search box that filters`() = runComposeUiTest {
        setContent {
            HosannaSurface {
                Dropdown(
                    many, "b1", {}, width,
                    label = "Bible", search = MenuSearch("Search Bibles", "Clear", "Nothing matches"),
                )
            }
        }
        open("BIBLE")
        onNode(hasSetTextAction()).performTextInput("12")
        waitForIdle()
        onNodeWithText("Bible 12").assertExists()
        onNodeWithText("Bible 3").assertDoesNotExist()
        onNode(hasSetTextAction()).performTextInput("x")
        waitForIdle()
        onNodeWithText("Nothing matches").assertExists()
    }

    @Test
    fun `a short list has no search box`() = runComposeUiTest {
        setContent {
            HosannaSurface {
                Dropdown(entries, "new", {}, width, label = "Sort", search = MenuSearch("Search", "Clear", "Nothing"))
            }
        }
        open("SORT")
        onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test
    fun `disabled and read-only fields do not open, an error shows below`() = runComposeUiTest {
        setContent {
            HosannaSurface {
                Column {
                    Dropdown(entries, "new", {}, width, label = "Off", enabled = false)
                    Dropdown(entries, "old", {}, width, label = "Shown", readOnly = true)
                    Dropdown(entries, null, {}, width, label = "Bible", error = "Pick a Bible to show")
                }
            }
        }
        onNodeWithText("Pick a Bible to show").assertExists()
        open("OFF")
        open("SHOWN")
        onNodeWithText("TIME").assertDoesNotExist()
    }
}
