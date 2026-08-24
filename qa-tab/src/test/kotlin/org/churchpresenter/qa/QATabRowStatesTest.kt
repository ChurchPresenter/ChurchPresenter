@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.qa

import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.QASettings
import org.churchpresenter.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsContainingText

/**
 * What a question row offers at each stage of its life, and the vote counts it carries.
 *
 * A row's buttons are status-specific — pending gets approve/deny, approved gets go-live and done,
 * done and denied get a way back — and only the pending set had been exercised. The rule with teeth
 * is on a *done* question: going live again asks for confirmation first, because re-showing a
 * question the room has already moved past is the kind of mistake that is only obvious once it is on
 * the screen.
 */
class QATabRowStatesTest {

    private fun voting() = AppSettings(qaSettings = QASettings(votingEnabled = true))

    // ── Votes ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an unapproved question cannot be voted on at all`() {
        qaTab(
            settings = voting(),
            seed = {
                askAll("not approved yet")
                voteForQuestion(questions.single().id, clientIp = "10.1.0.1", direction = "up")
            },
        ) { qa, _, _ ->
            assertEquals(0, qa.questions.single().upvotes, "the room votes on what was let through")
            assertFalse(showsContainingText("▲"), renderedText().toString())
        }
    }

    @Test
    fun `an approved question with no votes shows no counts`() {
        qaTab(
            settings = voting(),
            seed = { askAll("nobody voted"); approveQuestion(questions.single().id) },
        ) { _, _, _ ->
            assertFalse(showsContainingText("▲"), renderedText().toString())
            assertFalse(showsContainingText("▼"))
        }
    }

    @Test
    fun `upvotes are counted on the row`() {
        qaTab(
            settings = voting(),
            seed = {
                askAll("a popular question")
                // Only an approved question can be voted on — the room votes on what the moderator
                // has let through, so an unapproved one silently takes no votes at all.
                val id = questions.single().id
                approveQuestion(id)
                voteForQuestion(id, clientIp = "10.1.0.1", direction = "up")
                voteForQuestion(id, clientIp = "10.1.0.2", direction = "up")
            },
        ) { qa, _, _ ->
            assertEquals(2, qa.questions.single().upvotes)
            assertTrue(showsContainingText("▲ 2"), renderedText().toString())
        }
    }

    @Test
    fun `downvotes are counted separately`() {
        qaTab(
            settings = voting(),
            seed = {
                askAll("a divisive question")
                val id = questions.single().id
                approveQuestion(id)
                voteForQuestion(id, clientIp = "10.1.0.1", direction = "up")
                voteForQuestion(id, clientIp = "10.1.0.2", direction = "down")
            },
        ) { _, _, _ ->
            assertTrue(showsContainingText("▲ 1"), renderedText().toString())
            assertTrue(showsContainingText("▼ 1"))
        }
    }

    // ── Row buttons per status ──────────────────────────────────────────────────────────────────

    @Test
    fun `a pending question offers approve and deny`() {
        qaTab(seed = { askAll("waiting") }) { _, _, _ ->
            assertTrue(hasQaButton(QALabel.APPROVE))
            assertTrue(hasQaButton(QALabel.DENY))
            assertFalse(hasQaButton(QALabel.MARK_DONE), "nothing to finish before it is approved")
        }
    }

    @Test
    fun `an approved question offers go live and mark done`() {
        qaTab(seed = { askAll("approved") }) { _, _, _ ->
            qaButton(QALabel.APPROVE).performClick()
            waitForIdle()

            assertTrue(hasQaButton(QALabel.GO_LIVE))
            assertTrue(hasQaButton(QALabel.MARK_DONE))
            assertFalse(hasQaButton(QALabel.APPROVE), "already approved")
        }
    }

    @Test
    fun `a done question offers a way back to incoming`() {
        qaTab(seed = { askAll("finished") }) { qa, _, _ ->
            val id = qa.questions.single().id
            qa.approveQuestion(id)
            qa.markDone(id)
            waitForIdle()

            assertTrue(hasQaButton(QALabel.BACK_TO_INCOMING))
        }
    }

    @Test
    fun `sending a done question back re-approves it`() {
        qaTab(seed = { askAll("finished") }) { qa, _, _ ->
            val id = qa.questions.single().id
            qa.approveQuestion(id)
            qa.markDone(id)
            waitForIdle()

            qaButton(QALabel.BACK_TO_INCOMING).performClick()
            waitForIdle()

            assertFalse(hasQaButton(QALabel.BACK_TO_INCOMING), "it is not done any more")
            assertTrue(hasQaButton(QALabel.MARK_DONE))
        }
    }

    // ── Going live again from done ──────────────────────────────────────────────────────────────

    @Test
    fun `going live on a done question asks first`() {
        qaTab(seed = { askAll("already answered") }) { qa, output, _ ->
            val id = qa.questions.single().id
            qa.approveQuestion(id)
            qa.markDone(id)
            waitForIdle()

            qaButton(QALabel.GO_LIVE).performClick()
            waitForIdle()

            assertTrue(showsContainingText("Go Live?"), renderedText().toString())
            assertNull(output.shownQuestion, "asking must not have shown it yet")
        }
    }

    @Test
    fun `confirming shows the done question again`() {
        qaTab(seed = { askAll("already answered") }) { qa, output, _ ->
            val id = qa.questions.single().id
            qa.approveQuestion(id)
            qa.markDone(id)
            waitForIdle()

            qaButton(QALabel.GO_LIVE).performClick()
            waitForIdle()
            qaButton(QALabel.CONFIRM_GO_LIVE).performClick()
            waitForIdle()

            assertEquals(id, output.shownQuestion?.id)
        }
    }

    @Test
    fun `cancelling the confirmation leaves the output alone`() {
        qaTab(seed = { askAll("already answered") }) { qa, output, _ ->
            val id = qa.questions.single().id
            qa.approveQuestion(id)
            qa.markDone(id)
            waitForIdle()

            qaButton(QALabel.GO_LIVE).performClick()
            waitForIdle()
            qaButton(QALabel.CANCEL).performClick()
            waitForIdle()

            assertNull(output.shownQuestion)
            assertFalse(showsContainingText("Go Live?"), renderedText().toString())
        }
    }
}
