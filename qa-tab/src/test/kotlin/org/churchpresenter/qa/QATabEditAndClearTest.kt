@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.qa

import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsContainingText
import org.churchpresenter.ui.showsExactly

/**
 * Editing a question in place, and clearing the whole session.
 *
 * Both are destructive-ish and both were untested. Editing swaps the row's text for a field and the
 * pencil for a tick, and the rules that matter are the ones that protect the original: cancelling
 * restores it, and saving an unchanged or blank value must not overwrite it. Clearing goes through a
 * confirmation dialog whose confirm button also has to blank the output — a cleared list with the
 * last question still on the projector is the failure worth catching.
 */
class QATabEditAndClearTest {

    // ── Editing ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the edit button turns the row into a field`() {
        qaTab(seed = { askAll("original text") }) { _, _, _ ->
            assertFalse(hasQaButton(QALabel.SAVE), "not editing yet")

            qaButton(QALabel.EDIT).performClick()
            waitForIdle()

            // The pencil becomes a tick, and a cancel appears beside it.
            assertTrue(hasQaButton(QALabel.SAVE))
            assertTrue(hasQaButton(QALabel.CANCEL))
        }
    }

    @Test
    fun `while editing the row's own actions are put away`() {
        qaTab(seed = { askAll("original text") }) { _, _, _ ->
            assertTrue(hasQaButton(QALabel.APPROVE))

            qaButton(QALabel.EDIT).performClick()
            waitForIdle()

            assertFalse(hasQaButton(QALabel.APPROVE), "approving mid-edit would be ambiguous")
            assertFalse(hasQaButton(QALabel.DENY))
        }
    }

    @Test
    fun `saving an edit replaces the question text`() {
        qaTab(seed = { askAll("original text") }) { qa, _, _ ->
            qaButton(QALabel.EDIT).performClick()
            waitForIdle()

            editField().performTextClearance()
            editField().performTextInput("corrected text")
            qaButton(QALabel.SAVE).performClick()
            waitForIdle()

            assertEquals("corrected text", qa.questions.single().text)
            assertTrue(showsContainingText("corrected text"), renderedText().toString())
        }
    }

    @Test
    fun `cancelling an edit leaves the question alone`() {
        qaTab(seed = { askAll("original text") }) { qa, _, _ ->
            qaButton(QALabel.EDIT).performClick()
            waitForIdle()
            editField().performTextClearance()
            editField().performTextInput("discard me")

            qaButton(QALabel.CANCEL).performClick()
            waitForIdle()

            assertEquals("original text", qa.questions.single().text)
            assertFalse(showsContainingText("discard me"), renderedText().toString())
            assertFalse(hasQaButton(QALabel.SAVE), "the row goes back to being a row")
        }
    }

    @Test
    fun `saving a blank edit keeps the original`() {
        qaTab(seed = { askAll("original text") }) { qa, _, _ ->
            qaButton(QALabel.EDIT).performClick()
            waitForIdle()
            editField().performTextClearance()

            qaButton(QALabel.SAVE).performClick()
            waitForIdle()

            assertEquals("original text", qa.questions.single().text, "blank must not erase it")
        }
    }

    @Test
    fun `saving an unchanged edit is not treated as a change`() {
        qaTab(seed = { askAll("original text") }) { qa, _, _ ->
            val before = qa.questions.single()

            qaButton(QALabel.EDIT).performClick()
            waitForIdle()
            qaButton(QALabel.SAVE).performClick()
            waitForIdle()

            assertEquals(before.text, qa.questions.single().text)
            assertEquals(before.id, qa.questions.single().id)
        }
    }

    // ── Clearing everything ─────────────────────────────────────────────────────────────────────

    @Test
    fun `there is nothing to clear until a question arrives`() {
        qaTab { _, _, _ ->
            assertFalse(showsExactly(QALabel.CLEAR_ALL), renderedText().toString())
        }
    }

    @Test
    fun `clearing asks first`() {
        qaTab(seed = { askAll("a question") }) { qa, _, _ ->
            clickQaLabel(QALabel.CLEAR_ALL)

            assertTrue(showsContainingText("cannot be undone"), renderedText().toString())
            assertEquals(1, qa.questions.size, "asking must not have cleared anything yet")
        }
    }

    @Test
    fun `cancelling the confirmation keeps the questions`() {
        qaTab(seed = { askAll("a question") }) { qa, _, _ ->
            clickQaLabel(QALabel.CLEAR_ALL)
            clickQaLabel(QALabel.CANCEL)

            assertEquals(1, qa.questions.size)
            assertFalse(showsContainingText("cannot be undone"), renderedText().toString())
        }
    }

    @Test
    fun `confirming clears the questions and blanks the output`() {
        qaTab(seed = { askAll("a question") }) { qa, output, reports ->
            // Put it live first, so there is something on the projector to be left behind.
            qaButton(QALabel.APPROVE).performClick()
            waitForIdle()
            qaButton(QALabel.GO_LIVE).performClick()
            waitForIdle()
            assertTrue(output.shownQuestion != null)

            clickQaLabel(QALabel.CLEAR_ALL)
            clickQaLabel(QALabel.CLEAR)

            assertTrue(qa.questions.isEmpty())
            assertEquals(null, output.shownQuestion, "the projector must not keep it")
            assertFalse(output.qrShown)
            assertEquals(false, output.liveChanges.last(), "the output was cleared")
        }
    }
}
