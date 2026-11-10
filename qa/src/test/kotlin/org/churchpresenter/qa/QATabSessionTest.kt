@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.qa

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import org.churchpresenter.sharedui.testing.renderedText
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Named sessions on the tab: naming one as it starts, seeing which one is running, the note that
 * says none is, and picking one finished session out of the history to look at, resume or delete.
 */
class QATabSessionTest {

    private fun ComposeUiTest.openNewSessionDialog() = clickQaLabel(QALabel.NEW_SESSION)

    /** The history's own Resume: the lower of the two, the top bar's menu being the other. */
    private fun ComposeUiTest.clickHistoryResume() {
        val nodes = onAllNodesWithText(QALabel.RESUME).fetchSemanticsNodes()
        val lowest = nodes.indices.maxBy { nodes[it].boundsInRoot.top }
        onAllNodesWithText(QALabel.RESUME)[lowest].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.openHistory() {
        // "History (n)": the bare word also matches Delete All History once the view is open.
        onNodeWithText("${QALabel.HISTORY} (", substring = true).performClick()
        waitForIdle()
    }

    /** Two finished sessions, each with one question; "Evening" is the newer. */
    private val twoSessions: QAManager.() -> Unit = {
        startSession("Morning")
        askAll("Morning question")
        stopSession()
        startSession("Evening")
        askAll("Evening question")
        stopSession()
    }

    // ── Starting ────────────────────────────────────────────────────────────────

    @Test
    fun `a new session is named in the dialog before it starts`() = qaTab { qa, _, _ ->
        openNewSessionDialog()
        onAllNodes(hasSetTextAction()).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
        onAllNodes(hasSetTextAction())[0].performTextReplacement("Youth night")
        waitForIdle()

        clickQaLabel(QALabel.START_SESSION)

        assertTrue(qa.sessionActive)
        assertEquals("Youth night", qa.sessionName)
    }

    @Test
    fun `the dialog offers the start time as the name`() = qaTab { qa, _, _ ->
        openNewSessionDialog()

        clickQaLabel(QALabel.START_SESSION)

        assertTrue(qa.sessionActive)
        assertTrue(qa.sessionName.isNotBlank(), "named for when it started")
    }

    @Test
    fun `Enter in the name field starts the session`() = qaTab { qa, _, _ ->
        openNewSessionDialog()
        val field = onAllNodes(hasSetTextAction())[0]
        field.performTextReplacement("Midweek")

        field.performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertTrue(qa.sessionActive)
        assertEquals("Midweek", qa.sessionName)
    }

    @Test
    fun `cancelling the dialog starts nothing`() = qaTab { qa, _, _ ->
        openNewSessionDialog()

        clickQaLabel(QALabel.CANCEL)

        assertFalse(qa.sessionActive)
        assertTrue(onAllNodesWithText(QALabel.START_SESSION).fetchSemanticsNodes().isEmpty(), "the dialog closed")
    }

    // ── Which session is running ────────────────────────────────────────────────

    @Test
    fun `the running session's name is shown beside Stop Session`() =
        qaTab(seed = { startSession("Sunday service") }) { _, _, _ ->
            onNodeWithText("Session: Sunday service").assertExists(renderedText().joinToString().take(200))
        }

    @Test
    fun `with no session running the tab says nobody can send questions`() = qaTab { qa, _, _ ->
        onNodeWithText(QALabel.NO_SESSION_BANNER).assertExists()

        qa.startSession("Open")
        waitForIdle()

        onNodeWithText(QALabel.NO_SESSION_BANNER).assertDoesNotExist()
    }

    @Test
    fun `with no server the no-session note gives way to the server one`() =
        qaTab(serverUrl = "") { _, _, _ ->
            onNodeWithText(QALabel.NO_SESSION_BANNER).assertDoesNotExist()
        }

    // ── Resuming ────────────────────────────────────────────────────────────────

    @Test
    fun `the resume menu lists each session and resumes the one picked`() = qaTab(seed = twoSessions) { qa, _, _ ->
        clickQaLabel(QALabel.RESUME)
        onNodeWithText("Evening (1)").assertExists("every session is offered")

        onNodeWithText("Morning (1)").performClick()
        waitForIdle()

        assertEquals("Morning", qa.sessionName)
        assertEquals(listOf("Morning question"), qa.questions.map { it.text })
    }

    // ── The history ─────────────────────────────────────────────────────────────

    @Test
    fun `the history shows one session at a time, picked from the list`() = qaTab(seed = twoSessions) { _, _, _ ->
        openHistory()
        assertTrue(showsQuestion("Evening question"), "the newest first")
        assertFalse(showsQuestion("Morning question"))

        onAllNodes(hasText("SESSION", substring = true))[0].performClick()
        waitForIdle()
        onAllNodes(hasText("Morning (1)"))[0].performClick()
        waitForIdle()

        assertTrue(showsQuestion("Morning question"))
        assertFalse(showsQuestion("Evening question"))
    }

    @Test
    fun `resume in the history reopens the session on show and leaves the history`() =
        qaTab(seed = twoSessions) { qa, _, _ ->
            openHistory()

            clickHistoryResume()

            assertEquals("Evening", qa.sessionName)
            assertTrue(onAllNodesWithText(QALabel.DELETE_SESSION).fetchSemanticsNodes().isEmpty(), "back on the queue")
        }

    @Test
    fun `delete session removes only the session on show`() = qaTab(seed = twoSessions) { qa, _, _ ->
        openHistory()

        clickQaLabel(QALabel.DELETE_SESSION)

        assertEquals(listOf("Morning"), qa.sessions.map { it.name })
        assertTrue(showsQuestion("Morning question"), "the history moves on to the one left")
    }

    @Test
    fun `the history button closes the history it opened`() = qaTab(seed = twoSessions) { _, _, _ ->
        openHistory()
        onNodeWithText(QALabel.DELETE_SESSION).assertExists()

        openHistory()

        onNodeWithText(QALabel.DELETE_SESSION).assertDoesNotExist()
    }

    // ── One question's row ──────────────────────────────────────────────────────

    @Test
    fun `a long question is shown whole and can be cut to three lines`() =
        qaTab(seed = { askAll((1..6).joinToString("\n") { "Line $it of a long question" }) }) { _, _, _ ->
            assertTrue(showsQuestion("Line 6"), "whole at first")

            clickQaLabel(QALabel.SHOW_LESS)

            onNodeWithText(QALabel.SHOW_MORE).assertExists()
            clickQaLabel(QALabel.SHOW_MORE)
            onNodeWithText(QALabel.SHOW_LESS).assertExists()
        }

    @Test
    fun `a short question has no show less`() = qaTab(seed = { askAll("Short one") }) { _, _, _ ->
        onNodeWithText(QALabel.SHOW_LESS).assertDoesNotExist()
    }

    @Test
    fun `questions start in line whether or not the sender gave a name`() = qaTab(seed = {
        startSession("Sunday")
        submitQuestion("Asked by Anna", name = "Anna", clientIp = "10.0.0.1")
        submitQuestion("Asked by nobody", clientIp = "10.0.0.2")
    }) { _, _, _ ->
        val named = onNodeWithText("Asked by Anna").fetchSemanticsNode().boundsInRoot.left
        val unnamed = onNodeWithText("Asked by nobody").fetchSemanticsNode().boundsInRoot.left
        assertTrue(abs(named - unnamed) < 1f, "named at $named, unnamed at $unnamed")

        val name = onNodeWithText("Anna").fetchSemanticsNode().boundsInRoot
        val question = onNodeWithText("Asked by Anna").fetchSemanticsNode().boundsInRoot
        assertTrue(name.right <= question.left, "the name sits in the column before the question")
    }
}
