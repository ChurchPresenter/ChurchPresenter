@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText

/**
 * The Q&A history view — where questions go when a session ends.
 *
 * Ending a session moves every question out of the queue and into history rather than deleting
 * them, so the next service starts clean without losing what was asked. History is the one view
 * reached by its own button rather than the filter dropdown (`selectedFilter = 6`), which is why
 * it had no coverage: every existing test drives the dropdown.
 *
 * The restore path is the one worth defending most. A session ended by accident mid-service is
 * recoverable only if history keeps the questions *and* can hand them back.
 */
class QATabHistoryTest {

    private fun ComposeUiTest.openHistory() {
        // The button carries its own count, so it cannot be addressed by a bare label.
        onNodeWithText(QALabel.HISTORY, substring = true).performClick()
        waitForIdle()
    }

    @Test
    fun `the history button reports how many questions are held`() =
        qaTab(seed = { askAll("First question", "Second question") }) { qa, _, _ ->
            onNodeWithText("${QALabel.HISTORY} (0)", substring = true).assertExists("empty to start with")

            qa.toggleSession()
            waitForIdle()

            onNodeWithText("${QALabel.HISTORY} (2)", substring = true)
                .assertExists("both questions moved into history: ${renderedText().take(200)}")
        }

    @Test
    fun `ending a session moves questions to history rather than deleting them`() =
        qaTab(seed = { askAll("Asked before the break") }) { qa, _, _ ->
            qa.toggleSession()
            waitForIdle()

            assertFalse(showsQuestion("Asked before the break"), "the queue is clear for the next session")

            openHistory()
            assertTrue(showsQuestion("Asked before the break"), "but it is still on record")
        }

    @Test
    fun `the history view offers to export what it holds`() =
        qaTab(seed = { askAll("Something asked") }) { qa, _, _ ->
            qa.toggleSession()
            waitForIdle()
            openHistory()

            // The actions bar only appears once there is history to act on.
            assertTrue(
                renderedText().any { it.contains("Export", ignoreCase = true) },
                "an export action is offered: ${renderedText().take(200)}",
            )
        }

    @Test
    fun `an empty history offers no actions to take`() =
        qaTab(seed = { askAll("Still in the queue") }) { _, _, _ ->
            openHistory()

            assertFalse(
                renderedText().any { it.contains("Export", ignoreCase = true) },
                "nothing to export yet: ${renderedText().take(200)}",
            )
        }

    @Test
    fun `restoring puts the questions back in the queue and empties history`() =
        qaTab(seed = { askAll("Asked before the break", "And another") }) { qa, _, _ ->
            qa.toggleSession()
            waitForIdle()
            assertEquals(2, qa.history.size)

            qa.restoreFromHistory()
            waitForIdle()

            assertTrue(qa.history.isEmpty(), "history handed them back rather than copying them")
            assertTrue(qa.sessionActive, "and the session is running again")
            assertTrue(showsQuestion("Asked before the break"), "they are answerable again")
            assertTrue(showsQuestion("And another"))
        }

    @Test
    fun `clearing history empties it without touching the live queue`() =
        qaTab(seed = { askAll("From the first session") }) { qa, _, _ ->
            qa.toggleSession()
            waitForIdle()
            qa.toggleSession()          // start a new session
            qa.addQuestion("From the second session")
            waitForIdle()

            qa.clearHistory()
            waitForIdle()

            assertTrue(qa.history.isEmpty())
            assertTrue(showsQuestion("From the second session"), "the current queue is untouched")
        }

    @Test
    fun `history survives a second session ending, accumulating both`() =
        qaTab(seed = { askAll("Session one question") }) { qa, _, _ ->
            qa.toggleSession()
            waitForIdle()

            qa.toggleSession()
            qa.addQuestion("Session two question")
            waitForIdle()
            qa.toggleSession()
            waitForIdle()

            openHistory()
            assertTrue(showsQuestion("Session one question"), "the first session is still there")
            assertTrue(showsQuestion("Session two question"), "and the second joined it")
        }
}
