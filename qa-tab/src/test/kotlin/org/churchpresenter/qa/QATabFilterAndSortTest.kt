@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.qa

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsContainingText

class QATabFilterAndSortTest {

    private val startSessionHint = "Start a session to receive questions"

    private fun ComposeUiTest.openHistory() {
        onNodeWithText(QALabel.HISTORY, substring = true).performClick()
        waitForIdle()
    }

    @Test
    fun `the incoming and approved view shows both, and nothing else`() {
        qaTab(
            seed = {
                askAll("still pending", "let through", "refused")
                approveQuestion(questions[1].id)
                denyQuestion(questions[2].id)
            },
        ) { _, _, _ ->
            selectFilter(QALabel.INCOMING_APPROVED)

            assertTrue(showsContainingText("still pending"), renderedText().toString())
            assertTrue(showsContainingText("let through"), renderedText().toString())
            assertTrue(!showsContainingText("refused"), renderedText().toString())
        }
    }

    @Test
    fun `the finished view shows what was marked done`() {
        qaTab(
            seed = {
                askAll("answered already", "still pending")
                approveQuestion(questions[0].id)
                markDone(questions[0].id)
            },
        ) { _, _, _ ->
            selectFilter(QALabel.DONE)

            assertTrue(showsContainingText("answered already"), renderedText().toString())
            assertTrue(!showsContainingText("still pending"), renderedText().toString())
        }
    }

    @Test
    fun `the finished view says so when nothing has been answered`() {
        qaTab(seed = { askAll("still pending") }) { _, _, _ ->
            selectFilter(QALabel.DONE)

            assertTrue(showsContainingText(QALabel.NO_FINISHED), renderedText().toString())
        }
    }

    @Test
    fun `the denied view says so when nothing has been refused`() {
        qaTab(seed = { askAll("still pending") }) { _, _, _ ->
            selectFilter(QALabel.DENIED)

            assertTrue(showsContainingText(QALabel.NO_DENIED), renderedText().toString())
        }
    }

    @Test
    fun `with no session open the all view explains how to receive questions`() {
        qaTab { _, _, _ ->
            assertTrue(showsContainingText(startSessionHint), renderedText().toString())
        }
    }

    @Test
    fun `with no session open the incoming view explains how to receive questions`() {
        qaTab { _, _, _ ->
            selectFilter(QALabel.INCOMING)

            assertTrue(showsContainingText(startSessionHint), renderedText().toString())
        }
    }

    @Test
    fun `with no session open the incoming and approved view explains how to receive questions`() {
        qaTab { _, _, _ ->
            selectFilter(QALabel.INCOMING_APPROVED)

            assertTrue(showsContainingText(startSessionHint), renderedText().toString())
        }
    }

    @Test
    fun `an open session with nothing asked yet waits`() {
        qaTab(seed = { toggleSession() }) { _, _, _ ->
            assertTrue(showsContainingText(QALabel.WAITING), renderedText().toString())
        }
    }

    @Test
    fun `oldest first puts the earliest question at the top`() {
        qaTab(seed = { askAll("asked first", "asked second") }) { _, _, _ ->
            selectSort(QALabel.SORT_OLDEST)

            val text = renderedText().toString()
            assertTrue(text.indexOf("asked first") < text.indexOf("asked second"), text)
        }
    }

    @Test
    fun `the history view lists each question with the status it ended on`() {
        qaTab(
            seed = {
                askAll("answered already")
                approveQuestion(questions[0].id)
                markDone(questions[0].id)
                toggleSession()
            },
        ) { _, _, _ ->
            openHistory()

            assertTrue(showsContainingText("answered already"), renderedText().toString())
        }
    }
}
