@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isEditable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The searchable dropdown, driven through its two configurations.
 *
 * [FontSettingsDropdown] delegates here with both flags off, and it has no test of its own, so the
 * flags-off cases below are what stands behind its 22 call sites: they assert that a field which
 * opts into nothing still behaves the way the font pickers always have.
 */
class SearchableDropdownFieldTest {

    private val fruits = listOf("Apple", "Banana", "Cherry")

    /** Renders the field and returns a reader for the last value it committed. */
    private fun ComposeUiTest.field(
        initial: String = "Apple",
        options: List<String> = fruits,
        clearOnFocus: Boolean = false,
    ): () -> String? {
        var committed: String? = null
        setContent {
            MaterialTheme {
                var value by mutableStateOf(initial)
                SearchableDropdownField(
                    value = value,
                    options = options,
                    onValueChange = { value = it; committed = it },
                    clearOnFocus = clearOnFocus,
                )
            }
        }
        waitForIdle()
        return { committed }
    }

    private fun ComposeUiTest.open() = onNode(isEditable()).also { it.performClick(); waitForIdle() }

    /**
     * A row in the menu.
     *
     * The field carries the same text as the row matching it, and is clickable too, so a menu row
     * has to be identified as the one that is *not* the text field.
     */
    private fun menuItem(text: String) = hasTextExactly(text) and hasClickAction() and !isEditable()

    @Test
    fun `the field shows its current value before anything is opened`() = runComposeUiTest {
        field()

        onNodeWithText("Apple").assertIsDisplayed()
    }

    @Test
    fun `opening an empty field lists every option`() = runComposeUiTest {
        field(initial = "")
        open()

        fruits.forEach { onNode(menuItem(it)).assertExists() }
    }

    @Test
    fun `typing narrows the menu to what matches`() = runComposeUiTest {
        field(initial = "")
        open().performTextInput("an")
        waitForIdle()

        onNode(menuItem("Banana")).assertExists()
        onNode(menuItem("Apple")).assertDoesNotExist()
        onNode(menuItem("Cherry")).assertDoesNotExist()
    }

    @Test
    fun `matching ignores case`() = runComposeUiTest {
        field(initial = "")
        open().performTextInput("CHERR")
        waitForIdle()

        onNode(menuItem("Cherry")).assertExists()
    }

    @Test
    fun `a non-Latin query matches a non-Latin option`() = runComposeUiTest {
        // Filtering is a plain substring match, so it is script-agnostic — pinned here because the
        // language filter leans on it to find "русский" from "рус".
        field(initial = "", options = listOf("Russian · русский · RUS (31)", "English · ENG (5)"))
        open().performTextInput("рус")
        waitForIdle()

        onNode(menuItem("Russian · русский · RUS (31)")).assertExists()
        onNode(menuItem("English · ENG (5)")).assertDoesNotExist()
    }

    @Test
    fun `a query matching nothing says so instead of showing an empty menu`() = runComposeUiTest {
        field(initial = "")
        open().performTextInput("kiwi")
        waitForIdle()

        onNodeWithText("No results found for \"kiwi\"").assertExists()
    }

    @Test
    fun `picking an option commits exactly that option`() = runComposeUiTest {
        val committed = field(initial = "")
        open()
        onNode(menuItem("Cherry")).performClick()
        waitForIdle()

        assertEquals("Cherry", committed())
    }

    @Test
    fun `typing alone commits nothing`() = runComposeUiTest {
        // Free-form text is a filter, not a value: only a pick may reach the caller.
        val committed = field(initial = "")
        open().performTextInput("Banana")
        waitForIdle()

        assertNull(committed())
    }

    // --- the clear button ---

    @Test
    fun `no clear button is offered when there is nothing to clear`() = runComposeUiTest {
        setContent { MaterialTheme { SearchableDropdownField(value = "Apple", options = fruits, onValueChange = {}) } }
        waitForIdle()

        onNodeWithContentDescription("Clear").assertDoesNotExist()
    }

    @Test
    fun `the clear button reports back to the caller`() = runComposeUiTest {
        var cleared = 0
        setContent {
            MaterialTheme {
                SearchableDropdownField(
                    value = "Apple",
                    options = fruits,
                    onValueChange = {},
                    onClear = { cleared++ },
                )
            }
        }
        waitForIdle()

        onNodeWithContentDescription("Clear").performClick()
        waitForIdle()

        assertEquals(1, cleared)
    }

    @Test
    fun `clearing discards the typed text as well as the selection`() = runComposeUiTest {
        var cleared = 0
        setContent {
            MaterialTheme {
                SearchableDropdownField(
                    value = "Apple",
                    options = fruits,
                    onValueChange = {},
                    clearOnFocus = true,
                    onClear = { cleared++ },
                )
            }
        }
        waitForIdle()
        open().performTextInput("cher")
        waitForIdle()

        onNodeWithContentDescription("Clear").performClick()
        waitForIdle()

        assertEquals(1, cleared)
        // A stale query left behind would keep filtering by a selection that no longer exists.
        onNodeWithText("cher", substring = true).assertDoesNotExist()
        onNodeWithText("Apple").assertIsDisplayed()
    }

    // --- the flags-off configuration the font pickers use ---

    @Test
    fun `without clearOnFocus the field keeps its value when focused`() = runComposeUiTest {
        field(initial = "Apple")
        open()

        // The font pickers rely on this: the field reads as its current font while the menu is open.
        onNode(isEditable() and hasTextExactly("Apple")).assertExists()
    }

    @Test
    fun `without clearOnFocus an untouched field filters to what its value matches`() = runComposeUiTest {
        field(initial = "Apple")
        open()

        onNode(menuItem("Apple")).assertExists()
        onNode(menuItem("Banana")).assertDoesNotExist()
    }

    // --- the clearOnFocus configuration the language filter uses ---

    @Test
    fun `with clearOnFocus the value shows as a placeholder while the field is empty`() = runComposeUiTest {
        field(initial = "Apple", clearOnFocus = true)

        // Unfocused it must still read as the current pick, not as a blank box.
        onNodeWithText("Apple").assertIsDisplayed()
    }

    @Test
    fun `with clearOnFocus focusing empties the field so typing starts fresh`() = runComposeUiTest {
        val committed = field(initial = "Apple", clearOnFocus = true)
        // Were the text kept, the click's caret would splice these characters into "Apple".
        open().performTextInput("Banana")
        waitForIdle()

        onNode(menuItem("Banana")).performClick()
        waitForIdle()

        assertEquals("Banana", committed())
    }

    @Test
    fun `with clearOnFocus the menu opens on the whole list, not just the current pick`() = runComposeUiTest {
        field(initial = "Apple", clearOnFocus = true)
        open()

        fruits.forEach { onNode(menuItem(it)).assertExists() }
    }
}
