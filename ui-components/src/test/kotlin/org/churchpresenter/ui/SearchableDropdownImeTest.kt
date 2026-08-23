package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pressing Enter in the search box, which is the keyboard route through the dropdown.
 *
 * It commits **only when the query has narrowed to exactly one option** — the operator has typed
 * enough to be unambiguous. With two still matching there is nothing to choose between, and picking
 * the first would put whichever option happened to sort earliest on the screen.
 */
@OptIn(ExperimentalTestApi::class)
class SearchableDropdownImeTest {

    private val options = listOf("Arial", "Arial Black", "Verdana")

    @Test
    fun `enter commits when exactly one option matches`() = runComposeUiTest {
        var picked: String? = null
        setContent {
            MaterialTheme { SearchableDropdownField("", options, { picked = it }) }
        }
        onNode(hasSetTextAction()).performTextInput("Verd")
        waitForIdle()
        onNode(hasSetTextAction()).performImeAction()
        waitForIdle()
        assertEquals("Verdana", picked, "one match is unambiguous, so Enter should take it")
    }

    @Test
    fun `enter commits nothing while two options still match`() = runComposeUiTest {
        var picked: String? = null
        setContent {
            MaterialTheme { SearchableDropdownField("", options, { picked = it }) }
        }
        onNode(hasSetTextAction()).performTextInput("Arial")
        waitForIdle()
        onNode(hasSetTextAction()).performImeAction()
        waitForIdle()
        assertNull(picked, "\"Arial\" also matches \"Arial Black\" — guessing between them would be wrong")
    }

    @Test
    fun `enter commits nothing when the query matches no option`() = runComposeUiTest {
        var picked: String? = null
        setContent {
            MaterialTheme { SearchableDropdownField("", options, { picked = it }) }
        }
        onNode(hasSetTextAction()).performTextInput("zzz")
        waitForIdle()
        onNode(hasSetTextAction()).performImeAction()
        waitForIdle()
        assertNull(picked)
    }

    @Test
    fun `enter on an empty query commits nothing`() = runComposeUiTest {
        var picked: String? = null
        setContent {
            MaterialTheme { SearchableDropdownField("", options, { picked = it }) }
        }
        onNode(hasSetTextAction()).performImeAction()
        waitForIdle()
        assertNull(picked, "every option matches an empty query, so there is nothing to single out")
    }

    @Test
    fun `after committing, the field shows what was chosen`() = runComposeUiTest {
        var picked: String? = null
        setContent {
            MaterialTheme { SearchableDropdownField("", options, { picked = it }) }
        }
        onNode(hasSetTextAction()).performTextInput("Verd")
        waitForIdle()
        onNode(hasSetTextAction()).performImeAction()
        waitForIdle()
        assertEquals("Verdana", picked)
        onNodeWithText("Verdana").assertIsDisplayed()
    }

    @Test
    fun `with clearOnFocus the query is emptied after committing`() = runComposeUiTest {
        var picked: String? = null
        setContent {
            MaterialTheme { SearchableDropdownField("", options, { picked = it }, clearOnFocus = true) }
        }
        onNode(hasSetTextAction()).performTextReplacement("Verd")
        waitForIdle()
        onNode(hasSetTextAction()).performImeAction()
        waitForIdle()
        assertEquals("Verdana", picked, "clearOnFocus changes what is left behind, not what is committed")
    }
}
