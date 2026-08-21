@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.models.qa.QuestionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Q&A moderation queue.
 *
 * Q&A is the one part of the app a stranger can reach — anyone in the room posts to it from their
 * phone — so the moderator's queue is the gate between what was asked and what the congregation
 * sees. These pin which question ends up where: that approving does not display, that denying takes
 * it out of the queue without deleting it, and that nothing reaches the screen without an explicit
 * step. The server-side guards on submission are covered by `CompanionServerQaTest`.
 *
 * See `QATabTestSupport.kt` for the harness.
 */
class QATabTest {

    // ── The queue ───────────────────────────────────────────────────────────────

    @Test
    fun `an empty session waits rather than showing an empty list`() =
        qaTab(seed = { toggleSession() }) { qa, _, _ ->
            assertTrue(qa.questions.isEmpty())
            assertTrue(showsExactly(QALabel.WAITING), "got ${renderedText().take(12)}")
        }

    @Test
    fun `questions asked from a phone appear in the queue`() =
        qaTab(seed = { askAll("Is the coffee free?", "Where is the creche?") }) { _, _, _ ->
            assertTrue(showsQuestion("Is the coffee free?"))
            assertTrue(showsQuestion("Where is the creche?"))
        }

    @Test
    fun `the incoming count follows the queue`() =
        qaTab(seed = { askAll("One", "Two", "Three") }) { _, _, _ ->
            assertTrue(showsExactly("3"), "three waiting: ${renderedText().take(8)}")
            assertTrue(showsExactly(QALabel.INCOMING))
        }

    // ── Moderating ──────────────────────────────────────────────────────────────

    @Test
    fun `approving a question does not put it on screen`() =
        qaTab(seed = { askAll("Is the coffee free?") }) { qa, presenter, _ ->
            qaButton(QALabel.APPROVE).performClick()
            waitForIdle()

            assertEquals(QuestionStatus.APPROVED, qa.questions.single().status)
            // Approving is the moderator saying "this may be asked", not "show it now" — the
            // congregation must not see a question the moment it is cleared.
            assertFalse(presenter.presentingMode.value == Presenting.QA, "nothing was displayed")
            assertEquals(null, qa.displayedQuestion)
        }

    @Test
    fun `denying takes a question out of the queue without deleting it`() =
        qaTab(seed = { askAll("Off-topic question") }) { qa, _, _ ->
            qaButton(QALabel.DENY).performClick()
            waitForIdle()

            assertEquals(QuestionStatus.DENIED, qa.questions.single().status, "kept, but refused")
        }

    @Test
    fun `deleting asks before it removes anything`() =
        qaTab(seed = { askAll("Delete me") }) { qa, _, _ ->
            // The first press arms the confirmation rather than deleting — the queue is other
            // people's questions, and a misclick is not undoable.
            qaButton(QALabel.DELETE).performClick()
            waitForIdle()

            assertEquals(1, qa.questions.size, "still there")
            assertTrue(showsExactly("Delete?"), "and the confirmation is asked")
        }

    @Test
    fun `confirming the delete removes the question`() =
        qaTab(seed = { askAll("Delete me") }) { qa, _, _ ->
            qaButton(QALabel.DELETE).performClick()
            waitForIdle()
            // Two Delete buttons exist once armed — the confirm is the first of them.
            qaButton2(QALabel.DELETE, 0).performClick()
            waitForIdle()

            assertTrue(qa.questions.isEmpty(), "got ${qa.questions}")
            assertFalse(showsQuestion("Delete me"))
        }

    @Test
    fun `a row's buttons act on that row alone`() =
        qaTab(seed = { askAll("One question", "Another question") }) { qa, _, _ ->
            qaButton2(QALabel.APPROVE, 0).performClick()
            waitForIdle()

            // Deliberately not asserting *which* of the two was approved: both are submitted in the
            // same millisecond, so the newest-first sort is stable rather than ordered, and pinning
            // a row to a question would be asserting on the clock. What matters is that pressing
            // one row's button leaves the other row alone.
            assertEquals(
                1,
                qa.questions.count { it.status == QuestionStatus.APPROVED },
                "exactly one was approved: ${qa.questions.map { it.text to it.status }}",
            )
            assertEquals(1, qa.questions.count { it.status == QuestionStatus.PENDING })
        }

    // ── Filtering ───────────────────────────────────────────────────────────────

    @Test
    fun `the approved filter shows only what was cleared`() =
        qaTab(seed = { askAll("Approved one", "Still pending") }) { qa, _, _ ->
            qa.approveQuestion(qa.questions.first { it.text == "Approved one" }.id)
            waitForIdle()

            selectFilter(QALabel.APPROVED)

            assertTrue(showsQuestion("Approved one"))
            assertFalse(showsQuestion("Still pending"), "a pending question is not approved")
        }

    @Test
    fun `the denied filter shows what was refused, and nothing else`() =
        qaTab(seed = { askAll("Refused one", "Still pending") }) { qa, _, _ ->
            qa.denyQuestion(qa.questions.first { it.text == "Refused one" }.id)
            waitForIdle()

            selectFilter(QALabel.DENIED)

            assertTrue(showsQuestion("Refused one"))
            assertFalse(showsQuestion("Still pending"))
        }

    @Test
    fun `the approved filter says so when nothing has been cleared yet`() =
        qaTab(seed = { askAll("Still pending") }) { _, _, _ ->
            selectFilter(QALabel.APPROVED)

            assertTrue(showsExactly(QALabel.NO_APPROVED), "got ${renderedText().take(12)}")
        }

    // ── The session ─────────────────────────────────────────────────────────────

    @Test
    fun `a session can be stopped and started again`() = qaTab { qa, _, _ ->
        assertFalse(qa.sessionActive, "closed to begin with")

        clickQaLabel(QALabel.NEW_SESSION)
        assertTrue(qa.sessionActive, "open for questions")

        clickQaLabel(QALabel.STOP_SESSION)
        assertFalse(qa.sessionActive, "and closed again")
    }

    // ── Adding from the desk ────────────────────────────────────────────────────

    @Test
    fun `the moderator can add a question without a phone`() =
        qaTab(seed = { toggleSession() }) { qa, _, _ ->
            // A question asked out loud in the room still has to get into the queue.
            typeQuestion("Asked from the floor")
            clickQaLabel(QALabel.ADD)

            assertEquals("Asked from the floor", qa.questions.single().text)
        }

    @Test
    fun `an empty question is not added`() = qaTab(seed = { toggleSession() }) { qa, _, _ ->
        typeQuestion("   ")

        // The Add button is shut on blank input rather than adding an empty row.
        addButton().assertIsNotEnabled()
        assertTrue(qa.questions.isEmpty(), "got ${qa.questions}")
    }

    @Test
    fun `there is nowhere to add a question until a session is open`() = qaTab { _, _, _ ->
        assertFalse(showsExactly(QALabel.ADD_HINT), "no entry box before the session starts")
    }
}
