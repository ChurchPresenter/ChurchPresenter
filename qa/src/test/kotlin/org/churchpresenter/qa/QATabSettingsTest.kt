@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.qa

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.core.models.qa.QuestionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A settings change handed back to the tab, as the app does it: the tab draws itself again over
 * the new settings, and every control it draws afterwards still acts on the same session.
 *
 * The other suites keep the settings they started with and only check that a change was asked
 * for, so nothing else draws the tab a second time over different settings.
 */
class QATabSettingsTest {

    @Test
    fun `turning voting on shows it on, and the live controls still act`() =
        qaTab(
            applySettings = true,
            seed = {
                askAll("On screen", "Waiting")
                approveQuestion(questions.first { it.text == "On screen" }.id)
            },
        ) { qa, _, reports ->
            qaButton(QALabel.GO_LIVE).performClick()
            waitForIdle()

            qaButton(QALabel.VOTING_DISABLED).performClick()
            waitForIdle()

            assertTrue(hasQaButton(QALabel.VOTING_ENABLED), "the header follows the new setting")
            assertEquals(true, reports.settingsAfterChange?.qaSettings?.votingEnabled)

            qaButton(QALabel.VOTING_ENABLED).performClick()
            waitForIdle()
            assertTrue(hasQaButton(QALabel.VOTING_DISABLED), "and back again")

            clickQaLabel(QALabel.CLEAR_DISPLAY)
            assertNull(qa.displayedQuestion)

            qaButton(QALabel.APPROVE).performClick()
            waitForIdle()
            assertEquals(QuestionStatus.APPROVED, qa.questions.first { it.text == "Waiting" }.status)
        }

    @Test
    fun `the history view keeps working after the settings change`() =
        qaTab(
            applySettings = true,
            seed = {
                askAll("Kept from last week")
                toggleSession()
            },
        ) { qa, _, _ ->
            onNodeWithText(QALabel.HISTORY, substring = true).performClick()
            waitForIdle()

            qaButton(QALabel.VOTING_DISABLED).performClick()
            waitForIdle()

            onNodeWithText(QALabel.DELETE_ALL_HISTORY).performClick()
            waitForIdle()
            assertTrue(qa.history.isEmpty())
        }
}
