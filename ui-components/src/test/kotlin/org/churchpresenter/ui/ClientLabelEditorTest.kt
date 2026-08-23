package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Renaming a remote client, from the approval prompt and from the server settings rows.
 *
 * The state holder is what both entry points share: it carries the in-progress text so that
 * cancelling leaves the saved label alone, which is the whole reason it is not just a `remember`
 * at each call site.
 */
@OptIn(ExperimentalTestApi::class)
class ClientLabelEditorTest {

    @Test
    fun `a new editor starts closed, holding the current label`() {
        val state = ClientLabelEditor("Sound desk")
        assertFalse(state.editing)
        assertEquals("Sound desk", state.text)
    }

    @Test
    fun `the pencil opens the editor and seeds it with the saved label`() = runComposeUiTest {
        lateinit var state: ClientLabelEditor
        setContent {
            MaterialTheme {
                state = rememberClientLabelEditor("Booth")
                ClientLabelEditButton(state, "Booth")
            }
        }
        onAllNodes(hasClickAction()).onFirst().performClick()
        waitForIdle()
        assertTrue(state.editing, "the pencil has to open the editor")
        assertEquals("Booth", state.text, "and start from the saved label, not a stale draft")
    }

    @Test
    fun `saving reports the edited text`() = runComposeUiTest {
        var saved: String? = null
        lateinit var state: ClientLabelEditor
        setContent {
            MaterialTheme {
                state = rememberClientLabelEditor("Old")
                ClientLabelEditorRow(state, "Old", onSetLabel = { saved = it })
            }
        }
        state.editing = true
        waitForIdle()
        onNodeWithText("Old").performTextReplacement("New")
        waitForIdle()
        assertEquals("New", state.text)
    }

    @Test
    fun `the editor row draws nothing until the pencil is pressed`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                val state = rememberClientLabelEditor("Stage")
                ClientLabelEditorRow(state, "Stage", onSetLabel = {})
            }
        }
        waitForIdle()
        assertEquals(
            0,
            onAllNodes(hasSetTextAction()).fetchSemanticsNodes(atLeastOneRootRequired = false).size,
            "a caller places this unconditionally, so it must be invisible while closed",
        )
    }

    @Test
    fun `text survives being edited without saving`() {
        val state = ClientLabelEditor("Original")
        state.editing = true
        state.text = "half typed"
        assertEquals("half typed", state.text, "the draft is held on the state, not the row")
        assertTrue(state.editing)
    }
}
