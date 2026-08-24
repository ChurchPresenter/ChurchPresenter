@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.qa

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsContainingText

/**
 * What each view says when it has nothing to show, and what a denied question offers.
 *
 * Every filter has its own empty message, and three of them say something different depending on
 * whether the session is open: with no session running, "waiting for questions" would be a lie —
 * nobody can post. Getting that wrong leaves an operator watching an empty list that will never
 * fill, with the screen telling them to be patient.
 */
class QATabEmptyStatesTest {

    @Test
    fun `with the session closed the incoming view says how to open it`() = qaTab { _, _, _ ->
        assertTrue(
            showsContainingText(QALabel.WAITING).not(),
            "nothing can arrive while the session is closed: ${renderedText()}",
        )
    }

    @Test
    fun `with the session open the incoming view says it is waiting`() =
        qaTab(seed = { toggleSession() }) { _, _, _ ->
            assertTrue(showsContainingText(QALabel.WAITING), renderedText().toString())
        }

    @Test
    fun `the approved view has its own empty message`() = qaTab(seed = { toggleSession() }) { _, _, _ ->
        selectFilter(QALabel.APPROVED)

        assertTrue(showsContainingText(QALabel.NO_APPROVED), renderedText().toString())
    }

    @Test
    fun `the finished view has its own empty message`() = qaTab(seed = { toggleSession() }) { _, _, _ ->
        // The filter calls it "Done"; the badge above the list calls the same set "Finished".
        selectFilter(QALabel.DONE)

        assertTrue(showsContainingText(QALabel.NO_FINISHED), renderedText().toString())
    }

    @Test
    fun `the denied view has its own empty message`() = qaTab(seed = { toggleSession() }) { _, _, _ ->
        selectFilter(QALabel.DENIED)

        assertTrue(showsContainingText(QALabel.NO_DENIED), renderedText().toString())
    }

    @Test
    fun `an empty history offers neither export nor import`() =
        qaTab(seed = { toggleSession() }) { _, _, _ ->
            // History is a pane of its own rather than one of the FILTER options, and its tab
            // carries a live count ("History (0)"), so it is matched on a substring.
            onNodeWithText(QALabel.HISTORY, substring = true).performClick()
            waitForIdle()

            assertTrue(showsContainingText(QALabel.NO_HISTORY), renderedText().toString())
            assertTrue(
                showsContainingText(QALabel.EXPORT_TO_FILE).not(),
                "there is nothing to export from an empty history: ${renderedText()}",
            )
        }

    // ── A denied question ───────────────────────────────────────────────────────

    @Test
    fun `a denied question can be approved back into the queue`() =
        qaTab(seed = { askAll("Denied for now") }) { qa, _, _ ->
            qaButton(QALabel.DENY).performClick()
            waitForIdle()
            selectFilter(QALabel.DENIED)

            qaButton(QALabel.APPROVE).performClick()
            waitForIdle()

            assertTrue(
                qa.questions.single().status.name == "APPROVED",
                "approving a denied question puts it back in play",
            )
        }

    @Test
    fun `going live on a denied question asks first, and confirming approves it`() =
        qaTab(seed = { askAll("Denied then wanted") }) { qa, output, _ ->
            qaButton(QALabel.DENY).performClick()
            waitForIdle()
            selectFilter(QALabel.DENIED)

            qaButton(QALabel.GO_LIVE).performClick()
            waitForIdle()
            assertTrue(hasQaButton(QALabel.CONFIRM_GO_LIVE), "a denied question is not put up on one click")

            qaButton(QALabel.CONFIRM_GO_LIVE).performClick()
            waitForIdle()

            assertTrue(qa.questions.single().status.name != "DENIED", "confirming approves it first")
            assertTrue(output.liveChanges.lastOrNull() == true, "and then puts it up")
        }

    @Test
    fun `cancelling the confirmation on a denied question leaves it denied`() =
        qaTab(seed = { askAll("Stays denied") }) { qa, output, _ ->
            qaButton(QALabel.DENY).performClick()
            waitForIdle()
            selectFilter(QALabel.DENIED)

            qaButton(QALabel.GO_LIVE).performClick()
            waitForIdle()
            qaButton(QALabel.CANCEL).performClick()
            waitForIdle()

            assertTrue(qa.questions.single().status.name == "DENIED")
            assertTrue(output.liveChanges.isEmpty(), "nothing reached the screen")
        }

    // ── Deleting ────────────────────────────────────────────────────────────────

    @Test
    fun `deleting asks first and only removes the question once confirmed`() =
        qaTab(seed = { askAll("Delete me") }) { qa, _, _ ->
            qaButton(QALabel.DELETE).performClick()
            waitForIdle()
            assertTrue(qa.questions.size == 1, "the first click only asks")

            qaButton(QALabel.DELETE).performClick()
            waitForIdle()

            assertTrue(qa.questions.isEmpty(), "confirming removes it")
        }

    @Test
    fun `cancelling the delete confirmation keeps the question`() =
        qaTab(seed = { askAll("Keep me") }) { qa, _, _ ->
            qaButton(QALabel.DELETE).performClick()
            waitForIdle()
            qaButton(QALabel.CANCEL).performClick()
            waitForIdle()

            assertTrue(qa.questions.size == 1)
        }
}
