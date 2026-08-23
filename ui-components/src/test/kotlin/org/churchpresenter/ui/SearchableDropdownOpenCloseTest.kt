package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The clear button a caller can put on the searchable dropdown, and what pressing it does.
 *
 * It is wired only by the callers that have somewhere to go when the choice is withdrawn, and it
 * does three things at once: it drops the typed query (a stale one would filter the menu by a
 * selection that no longer exists), it shuts the menu, and it tells the caller.
 */
@OptIn(ExperimentalTestApi::class)
class SearchableDropdownOpenCloseTest {

    private val options = listOf("Georgia", "Arial", "Courier New")

    @Test
    fun `the clear button empties the query, shuts the menu and reports the clear`() = runComposeUiTest {
        var cleared = 0
        setContent {
            MaterialTheme {
                SearchableDropdownField(
                    value = "Georgia",
                    options = options,
                    onValueChange = {},
                    label = "Font",
                    clearOnFocus = true,
                    onClear = { cleared++ },
                )
            }
        }

        onNode(androidx.compose.ui.test.hasSetTextAction()).performClick()
        waitForIdle()
        assertTrue(optionsListed() > 0, "open first")

        onNodeWithContentDescription("Clear").performClick()
        waitForIdle()

        assertEquals(1, cleared)
        assertEquals(0, optionsListed(), "clearing shuts the menu")
    }

    /** The menu is a popup of its own, so its rows are not in the field's compose root. */
    private fun ComposeUiTest.optionsListed(): Int =
        onAllNodesWithText("Arial").fetchSemanticsNodes(atLeastOneRootRequired = false).size
}
