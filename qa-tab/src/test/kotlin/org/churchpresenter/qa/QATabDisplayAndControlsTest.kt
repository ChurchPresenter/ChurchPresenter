@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.qa

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.QASettings
import org.churchpresenter.core.models.qa.QuestionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsContainingText
import org.churchpresenter.ui.showsExactly

/**
 * The header controls (QR toggle, voting toggle, sort), the filters beyond approved/denied, the
 * clear-display bar, and what happens to a question that is on screen when it is finished, edited
 * or deleted from underneath itself.
 *
 * See `QATabTestSupport.kt` for the harness.
 */
class QATabDisplayAndControlsTest {

    private fun voting() = AppSettings(qaSettings = QASettings(votingEnabled = true))

    // ── Server state ────────────────────────────────────────────────────────────

    @Test
    fun `a stopped server tells the moderator why there is no session`() =
        qaTab(serverUrl = "") { _, _, _ ->
            assertTrue(showsContainingText("Server not running"), renderedText().toString())
            assertFalse(showsExactly(QALabel.NEW_SESSION), "no way to start a session without a server")
        }

    // ── Displaying an approved question ────────────────────────────────────────

    @Test
    fun `going live on an approved question puts it on screen`() =
        qaTab(seed = { askAll("ready to show") }) { qa, output, reports ->
            qaButton(QALabel.APPROVE).performClick()
            waitForIdle()
            qaButton(QALabel.GO_LIVE).performClick()
            waitForIdle()

            assertEquals(qa.questions.single().id, output.shownQuestion?.id)
            assertEquals(true, output.liveChanges.last(), "Q&A went live")
            assertTrue(showsContainingText("Displaying:"), renderedText().toString())
        }

    @Test
    fun `the clear display bar blanks the output without touching the queue`() =
        qaTab(seed = { askAll("ready to show") }) { qa, output, reports ->
            qaButton(QALabel.APPROVE).performClick()
            waitForIdle()
            qaButton(QALabel.GO_LIVE).performClick()
            waitForIdle()

            clickQaLabel(QALabel.CLEAR_DISPLAY)

            assertEquals(1, qa.questions.size, "the queue is untouched")
            assertEquals(QuestionStatus.APPROVED, qa.questions.single().status)
            assertEquals(null, output.shownQuestion)
            assertEquals(false, output.liveChanges.last(), "the output was cleared")
            assertFalse(showsContainingText("Displaying:"), renderedText().toString())
        }

    @Test
    fun `marking a displayed question done clears what is on screen`() =
        qaTab(seed = { askAll("live now") }) { qa, output, reports ->
            qaButton(QALabel.APPROVE).performClick()
            waitForIdle()
            qaButton(QALabel.GO_LIVE).performClick()
            waitForIdle()
            assertTrue(output.shownQuestion != null)

            qaButton(QALabel.DONE_CLEAR).performClick()
            waitForIdle()

            assertEquals(QuestionStatus.DONE, qa.questions.single().status)
            assertEquals(null, output.shownQuestion)
            assertEquals(false, output.liveChanges.last(), "the output was cleared")
        }

    @Test
    fun `deleting a displayed question clears what is on screen`() =
        qaTab(seed = { askAll("live now") }) { qa, output, reports ->
            qaButton(QALabel.APPROVE).performClick()
            waitForIdle()
            qaButton(QALabel.GO_LIVE).performClick()
            waitForIdle()

            qaButton(QALabel.DELETE).performClick()
            waitForIdle()
            qaButton2(QALabel.DELETE, 0).performClick()
            waitForIdle()

            assertTrue(qa.questions.isEmpty())
            assertEquals(null, output.shownQuestion)
            assertEquals(false, output.liveChanges.last(), "the output was cleared")
        }

    @Test
    fun `editing a displayed question updates what is on screen`() =
        qaTab(seed = { askAll("live now") }) { qa, output, _ ->
            qaButton(QALabel.APPROVE).performClick()
            waitForIdle()
            qaButton(QALabel.GO_LIVE).performClick()
            waitForIdle()

            qaButton(QALabel.EDIT).performClick()
            waitForIdle()
            editField().performTextClearance()
            editField().performTextInput("live now, edited")
            qaButton(QALabel.SAVE).performClick()
            waitForIdle()

            assertEquals("live now, edited", output.shownQuestion?.text)
        }

    // ── QR code on screen ───────────────────────────────────────────────────────

    @Test
    fun `showing the QR code puts it on screen`() =
        qaTab(seed = { askAll("q") }) { qa, output, reports ->
            qaButton(QALabel.SHOW_QR).performClick()
            waitForIdle()

            assertTrue(qa.showQRCodeOnDisplay)
            assertTrue(output.qrShown)
            assertEquals(true, output.liveChanges.last(), "Q&A went live")
            assertTrue(hasQaButton(QALabel.HIDE_QR))
        }

    @Test
    fun `hiding the QR code with nothing else displayed clears the output`() =
        qaTab(seed = { askAll("q") }) { qa, output, reports ->
            qaButton(QALabel.SHOW_QR).performClick()
            waitForIdle()
            qaButton(QALabel.HIDE_QR).performClick()
            waitForIdle()

            assertFalse(qa.showQRCodeOnDisplay)
            assertFalse(output.qrShown)
            assertEquals(false, output.liveChanges.last(), "the output was cleared")
        }

    @Test
    fun `the QR toggle is locked while a locked screen is showing it`() =
        qaTab(seed = { askAll("q") }) { qa, output, _ ->
            qaButton(QALabel.SHOW_QR).performClick()
            waitForIdle()
            output.lockedToQa = true

            qaButton(QALabel.HIDE_QR).assertIsNotEnabled()
            assertTrue(qa.showQRCodeOnDisplay, "still showing, the button is just locked")
        }

    // ── Voting toggle ───────────────────────────────────────────────────────────

    @Test
    fun `voting can be turned on from the header`() =
        qaTab { _, _, reports ->
            qaButton(QALabel.VOTING_DISABLED).performClick()
            waitForIdle()

            assertEquals(1, reports.settingsChanges)
            assertEquals(true, reports.settingsAfterChange?.qaSettings?.votingEnabled)
        }

    @Test
    fun `voting can be turned off again from the header`() =
        qaTab(settings = voting()) { _, _, reports ->
            qaButton(QALabel.VOTING_ENABLED).performClick()
            waitForIdle()

            assertEquals(false, reports.settingsAfterChange?.qaSettings?.votingEnabled)
        }

    // ── Sorting ─────────────────────────────────────────────────────────────────

    @Test
    fun `most votes sorts approved questions with the highest count first`() =
        qaTab(
            settings = voting(),
            seed = {
                askAll("Low votes", "High votes")
                val low = questions.first { it.text == "Low votes" }.id
                val high = questions.first { it.text == "High votes" }.id
                approveQuestion(low)
                approveQuestion(high)
                voteForQuestion(high, clientIp = "10.2.0.1", direction = "up")
                voteForQuestion(high, clientIp = "10.2.0.2", direction = "up")
                voteForQuestion(low, clientIp = "10.2.0.3", direction = "up")
            },
        ) { _, _, _ ->
            selectSort(QALabel.SORT_MOST_VOTES)
            assertEquals(listOf("High votes", "Low votes"), orderOfQuestions("High votes", "Low votes"))
        }

    @Test
    fun `least votes sorts approved questions with the lowest count first`() =
        qaTab(
            settings = voting(),
            seed = {
                askAll("Low votes", "High votes")
                val low = questions.first { it.text == "Low votes" }.id
                val high = questions.first { it.text == "High votes" }.id
                approveQuestion(low)
                approveQuestion(high)
                voteForQuestion(high, clientIp = "10.2.0.1", direction = "up")
                voteForQuestion(high, clientIp = "10.2.0.2", direction = "up")
                voteForQuestion(low, clientIp = "10.2.0.3", direction = "up")
            },
        ) { _, _, _ ->
            selectSort(QALabel.SORT_LEAST_VOTES)
            assertEquals(listOf("Low votes", "High votes"), orderOfQuestions("Low votes", "High votes"))
        }

    @Test
    fun `oldest first keeps every question on screen`() =
        qaTab(seed = { askAll("One", "Two", "Three") }) { _, _, _ ->
            selectSort(QALabel.SORT_OLDEST)

            assertTrue(showsQuestion("One"))
            assertTrue(showsQuestion("Two"))
            assertTrue(showsQuestion("Three"))
        }

    // ── Filters not covered elsewhere ──────────────────────────────────────────

    @Test
    fun `the all filter shows every question regardless of status`() =
        qaTab(seed = { askAll("pending", "approved", "denied") }) { qa, _, _ ->
            qa.approveQuestion(qa.questions.first { it.text == "approved" }.id)
            qa.denyQuestion(qa.questions.first { it.text == "denied" }.id)
            waitForIdle()

            selectFilter(QALabel.ALL)

            assertTrue(showsQuestion("pending"))
            assertTrue(showsQuestion("approved"))
            assertTrue(showsQuestion("denied"))
        }

    @Test
    fun `the incoming and approved filter combines both statuses`() =
        qaTab(seed = { askAll("still pending", "cleared", "finished") }) { qa, _, _ ->
            qa.approveQuestion(qa.questions.first { it.text == "cleared" }.id)
            qa.approveQuestion(qa.questions.first { it.text == "finished" }.id)
            qa.markDone(qa.questions.first { it.text == "finished" }.id)
            waitForIdle()

            selectFilter(QALabel.INCOMING_APPROVED)

            assertTrue(showsQuestion("still pending"))
            assertTrue(showsQuestion("cleared"))
            assertFalse(showsQuestion("finished"), "done questions are not incoming or approved")
        }

    @Test
    fun `the done filter shows only finished questions`() =
        qaTab(seed = { askAll("finished one", "still pending") }) { qa, _, _ ->
            val id = qa.questions.first { it.text == "finished one" }.id
            qa.approveQuestion(id)
            qa.markDone(id)
            waitForIdle()

            selectFilter(QALabel.DONE)

            assertTrue(showsQuestion("finished one"))
            assertFalse(showsQuestion("still pending"))
        }

    @Test
    fun `the done filter says so when nothing has finished yet`() =
        qaTab(seed = { askAll("still pending") }) { _, _, _ ->
            selectFilter(QALabel.DONE)

            assertTrue(showsExactly(QALabel.NO_FINISHED), renderedText().toString())
        }

    @Test
    fun `the denied filter says so when nothing has been refused yet`() =
        qaTab(seed = { askAll("still pending") }) { _, _, _ ->
            selectFilter(QALabel.DENIED)

            assertTrue(showsExactly(QALabel.NO_DENIED), renderedText().toString())
        }

    // ── Resuming a stopped session ──────────────────────────────────────────────

    @Test
    fun `resume brings history back into the live queue`() =
        qaTab(seed = { askAll("kept") }) { qa, _, _ ->
            qa.toggleSession()
            waitForIdle()
            assertFalse(qa.sessionActive)

            onNodeWithText("Resume (", substring = true).performClick()
            waitForIdle()

            assertTrue(qa.sessionActive)
            assertTrue(qa.history.isEmpty())
            assertTrue(showsQuestion("kept"))
        }

    // ── Denied questions can also go live, after confirming ─────────────────────

    @Test
    fun `going live on a denied question also asks first`() =
        qaTab(seed = { askAll("denied one") }) { qa, output, _ ->
            qaButton(QALabel.DENY).performClick()
            waitForIdle()
            qaButton(QALabel.GO_LIVE).performClick()
            waitForIdle()

            assertTrue(showsContainingText("Go Live?"), renderedText().toString())
            assertNull(output.shownQuestion)
        }

    @Test
    fun `confirming go live on a denied question approves and displays it`() =
        qaTab(seed = { askAll("denied one") }) { qa, output, _ ->
            qaButton(QALabel.DENY).performClick()
            waitForIdle()
            qaButton(QALabel.GO_LIVE).performClick()
            waitForIdle()
            qaButton(QALabel.CONFIRM_GO_LIVE).performClick()
            waitForIdle()

            assertEquals(QuestionStatus.APPROVED, qa.questions.single().status)
            assertEquals(qa.questions.single().id, output.shownQuestion?.id)
        }

    @Test
    fun `cancelling go live on a denied question leaves it denied`() =
        qaTab(seed = { askAll("denied one") }) { qa, output, _ ->
            qaButton(QALabel.DENY).performClick()
            waitForIdle()
            qaButton(QALabel.GO_LIVE).performClick()
            waitForIdle()
            qaButton(QALabel.CANCEL).performClick()
            waitForIdle()

            assertEquals(QuestionStatus.DENIED, qa.questions.single().status)
            assertNull(output.shownQuestion)
            assertFalse(showsContainingText("Go Live?"), renderedText().toString())
        }

    // ── Delete confirmation can be cancelled ─────────────────────────────────────

    @Test
    fun `cancelling delete leaves the question in place`() =
        qaTab(seed = { askAll("keep me") }) { qa, _, _ ->
            qaButton(QALabel.DELETE).performClick()
            waitForIdle()
            qaButton(QALabel.CANCEL).performClick()
            waitForIdle()

            assertEquals(1, qa.questions.size)
            assertFalse(showsExactly("Delete?"), renderedText().toString())
        }

    // ── Clearing history from the button, not the view model directly ───────────

    @Test
    fun `the delete all history button empties history`() =
        qaTab(seed = { askAll("old question") }) { qa, _, _ ->
            qa.toggleSession()
            waitForIdle()
            onNodeWithText(QALabel.HISTORY, substring = true).performClick()
            waitForIdle()

            clickQaLabel(QALabel.DELETE_ALL_HISTORY)

            assertTrue(qa.history.isEmpty())
        }

    // ── Who asked ───────────────────────────────────────────────────────────────

    @Test
    fun `a question shows who submitted it`() =
        qaTab(seed = {
            toggleSession()
            submitQuestion("Asked by name", name = "Jane", clientIp = "10.9.0.1")
        }) { _, _, _ ->
            assertTrue(showsQuestion("Jane"))
        }

    // ── Statistic badges ─────────────────────────────────────────────────────────

    @Test
    fun `the finished badge counts done and denied together`() =
        qaTab(seed = { askAll("done one", "denied one", "still pending") }) { qa, _, _ ->
            qa.approveQuestion(qa.questions.first { it.text == "done one" }.id)
            qa.markDone(qa.questions.first { it.text == "done one" }.id)
            qa.denyQuestion(qa.questions.first { it.text == "denied one" }.id)
            waitForIdle()

            assertTrue(showsExactly("2"), "one done + one denied: ${renderedText().take(12)}")
            assertTrue(showsExactly(QALabel.FINISHED))
        }

    // ── The tab follows the display back to empty ────────────────────────────────

    @Test
    fun `clearing the display externally resets the QA tab`() =
        qaTab(seed = { askAll("on screen") }) { qa, output, _ ->
            qaButton(QALabel.APPROVE).performClick()
            waitForIdle()
            qaButton(QALabel.GO_LIVE).performClick()
            waitForIdle()
            assertTrue(qa.displayedQuestion != null)

            output.outputIsClear = false
            waitForIdle()
            output.outputIsClear = true
            waitForIdle()

            assertEquals(null, qa.displayedQuestion, "the tab followed the display back to empty")
            assertFalse(showsContainingText("Displaying:"), renderedText().toString())
        }

    @Test
    fun `a locked QA screen keeps its content when the display clears`() =
        qaTab(seed = { askAll("on screen") }) { qa, output, _ ->
            qaButton(QALabel.APPROVE).performClick()
            waitForIdle()
            qaButton(QALabel.GO_LIVE).performClick()
            waitForIdle()
            output.lockedToQa = true

            output.outputIsClear = false
            waitForIdle()
            output.outputIsClear = true
            waitForIdle()

            assertEquals(qa.questions.single().id, qa.displayedQuestion?.id, "locked screen keeps its content")
        }
}
