package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The dropdown selector used across the settings dialogs and tabs, in its two overloads: one
 * keyed by a (value, display-label) pair list — where the closed selector must show the *label*
 * for the current *value*, not the raw value — and one keyed by a plain string list.
 *
 * The string-list overload already had incidental full coverage via `SourcePropertiesPanel`'s own
 * tests (it's the overload that panel uses), but its contract is pinned here directly rather than
 * relying on that indirect coverage. Dismissing the menu via an outside click was left untested:
 * `DropdownMenu`'s popup adds a second, full-window semantics root once open, and reliably picking
 * "outside the popup but still inside that root" without depending on root ordering (which isn't
 * guaranteed) would need more scaffolding than the one-line `{ expanded = false }` it exercises is
 * worth.
 */
@OptIn(ExperimentalTestApi::class)
class DropdownSelectorTest {

    // ── options: List<Pair<key, display>> overload ────────────────────────────────────────────

    @Test
    fun `the label is shown, uppercased`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                DropdownSelector(
                    label = "Fruit",
                    value = "a",
                    options = listOf("a" to "Apple"),
                    onValueChange = { },
                )
            }
        }
        onNodeWithText("FRUIT", substring = true).assertExists("the label must be shown in uppercase")
    }

    @Test
    fun `the display label for the current value is shown, not the raw value`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                DropdownSelector(
                    label = "Fruit",
                    value = "a",
                    options = listOf("a" to "Apple", "b" to "Banana"),
                    onValueChange = { },
                )
            }
        }
        onNodeWithText("Apple").assertExists("the closed selector must resolve the key to its display label")
        onNodeWithText("a").assertDoesNotExist()
    }

    @Test
    fun `when the value matches no option, the raw value is shown as a fallback`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                DropdownSelector(
                    label = "Fruit",
                    value = "unknown-key",
                    options = listOf("a" to "Apple"),
                    onValueChange = { },
                )
            }
        }
        onNodeWithText("unknown-key").assertExists("an unmatched value must still be shown, not left blank")
    }

    @Test
    fun `without a label, the value still shows`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                DropdownSelector(
                    label = "",
                    value = "a",
                    options = listOf("a" to "Apple"),
                    onValueChange = { },
                )
            }
        }
        onNodeWithText("Apple").assertExists("the value must still show even without a label")
    }

    @Test
    fun `clicking the selector opens the menu, showing every option's display label`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                DropdownSelector(
                    label = "Fruit",
                    value = "a",
                    options = listOf("a" to "Apple", "b" to "Banana"),
                    onValueChange = { },
                )
            }
        }
        onNodeWithText("Banana", substring = true).assertDoesNotExist()

        onAllNodesWithText("Apple").onLast().performClick()

        onAllNodesWithText("Apple").onLast().assertExists("the current option must also appear in the open menu")
        onAllNodesWithText("Banana").onLast().assertExists("every option must be listed in the open menu")
    }

    @Test
    fun `choosing an option invokes onValueChange with its key and closes the menu`() = runComposeUiTest {
        var current = "a"
        setContent {
            MaterialTheme {
                DropdownSelector(
                    label = "Fruit",
                    value = current,
                    options = listOf("a" to "Apple", "b" to "Banana"),
                    onValueChange = { current = it },
                )
            }
        }
        onAllNodesWithText("Apple").onLast().performClick()
        onAllNodesWithText("Banana").onLast().performClick()

        assertEquals("b", current, "choosing Banana must report its key, not its display label")
        onAllNodesWithText("Banana").assertCountEquals(0)
    }

    @Test
    fun `itemTrailingContent renders alongside each option, keyed to that option`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                DropdownSelector(
                    label = "Fruit",
                    value = "a",
                    options = listOf("a" to "Apple", "b" to "Banana"),
                    onValueChange = { },
                    itemTrailingContent = { key, index -> Text("trailing-$key-$index") },
                )
            }
        }
        onAllNodesWithText("Apple").onLast().performClick()

        onNodeWithText("trailing-a-0").assertExists()
        onNodeWithText("trailing-b-1").assertExists()
    }

    // ── items: List<String> overload ──────────────────────────────────────────────────────────

    @Test
    fun `the plain-list overload shows its label, uppercased`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                DropdownSelector(label = "Font", items = listOf("Arial"), selected = "Arial", onSelectedChange = { })
            }
        }
        onNodeWithText("FONT", substring = true).assertExists("the label must be shown in uppercase")
    }

    @Test
    fun `the plain-list overload shows the currently selected item as-is`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                DropdownSelector(
                    label = "Font",
                    items = listOf("Arial", "Georgia"),
                    selected = "Georgia",
                    onSelectedChange = { },
                )
            }
        }
        onNodeWithText("Georgia").assertExists("the closed selector must show the current selection")
    }

    @Test
    fun `the plain-list overload still shows the selection without a label`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                DropdownSelector(label = "", items = listOf("Arial"), selected = "Arial", onSelectedChange = { })
            }
        }
        onNodeWithText("Arial").assertExists("the selection must still show even without a label")
    }

    @Test
    fun `clicking the plain-list selector opens the menu, showing every item`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                DropdownSelector(
                    label = "Font",
                    items = listOf("Arial", "Georgia"),
                    selected = "Arial",
                    onSelectedChange = { },
                )
            }
        }
        onNodeWithText("Georgia", substring = true).assertDoesNotExist()

        onAllNodesWithText("Arial").onLast().performClick()

        onAllNodesWithText("Arial").onLast().assertExists("the current item must also appear in the open menu")
        onAllNodesWithText("Georgia").onLast().assertExists("every item must be listed in the open menu")
    }

    @Test
    fun `choosing a plain-list item invokes onSelectedChange and closes the menu`() = runComposeUiTest {
        var current = "Arial"
        setContent {
            MaterialTheme {
                DropdownSelector(
                    label = "Font",
                    items = listOf("Arial", "Georgia"),
                    selected = current,
                    onSelectedChange = { current = it },
                )
            }
        }
        onAllNodesWithText("Arial").onLast().performClick()
        onAllNodesWithText("Georgia").onLast().performClick()

        assertEquals("Georgia", current, "choosing Georgia must report it via onSelectedChange")
        onAllNodesWithText("Georgia").assertCountEquals(0)
    }
}
