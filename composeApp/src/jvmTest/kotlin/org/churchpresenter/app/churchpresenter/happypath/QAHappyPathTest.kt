@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.happypath

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performClick
import org.churchpresenter.core.models.qa.QuestionStatus
import org.churchpresenter.qa.QAManager
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val QUESTION = "How should we pray when we don't know what to ask for?"
private const val SERVER = "http://192.168.1.42:8080"

/** How far above or below a question's text its row controls may sit. */
private const val ROW_SLACK = 40f

class QAHappyPathTest : HappyPathHarness() {

    /** Where `QAManager` keeps its session: this fork's home, shared with every other suite. */
    private val stateFile get() = File(System.getProperty("user.home"), ".churchpresenter/qa_state.json")

    @BeforeTest
    fun clearSession() {
        stateFile.delete()
    }

    @AfterTest
    fun removeSession() {
        stateFile.delete()
    }

    private fun ComposeUiTest.shows(text: String): Boolean =
        onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    /** The control described as [description] on the row asking [question]. */
    private fun ComposeUiTest.clickOnRow(question: String, description: String) {
        val row = onAllNodes(hasText(question)).fetchSemanticsNodes().first().boundsInRoot
        val nodes = onAllNodes(hasContentDescription(description) and isEnabled())
        val onRow = nodes.fetchSemanticsNodes()
            .indexOfFirst { it.boundsInRoot.center.y in row.top - ROW_SLACK..row.bottom + ROW_SLACK }
        assertTrue(onRow >= 0, "the row carries \"$description\"")
        nodes[onRow].performClick()
        waitForIdle()
    }

    /** A session started, and [QUESTION] sent in from a phone. */
    private fun ComposeUiTest.questionArrives(qa: QAManager) {
        clickText("New Session")
        await("the session is open") { qa.sessionActive }
        qa.submitQuestion(QUESTION, name = "Peter", clientIp = "10.0.0.7")
        await("the question is listed") { shows(QUESTION) }
    }

    @Test
    fun `a new session puts its join code on screen`() {
        val qa = QAManager()
        happyPath(settings(Tabs.QA), qaManager = qa, serverUrl = SERVER) { app ->
            clickText("New Session")
            await("the session is open") { qa.sessionActive }

            clickTabControl("Show QR on Display")

            await("the join code is on air") { qa.showQRCodeOnDisplay && app.presenter.isLive(Presenting.QA) }
        }
    }

    @Test
    fun `an approved question goes live`() {
        val qa = QAManager()
        happyPath(settings(Tabs.QA), qaManager = qa, serverUrl = SERVER) { app ->
            questionArrives(qa)
            clickOnRow(QUESTION, "Approve")
            clickOnRow(QUESTION, "Go Live")
            if (shows("Confirm Go Live")) clickText("Confirm Go Live")

            await("the question is on air") { app.presenter.isLive(Presenting.QA) }
            awaitOnOutput(app, QUESTION)
        }
    }

    @Test
    fun `a denied question leaves the queue`() {
        val qa = QAManager()
        happyPath(settings(Tabs.QA), qaManager = qa, serverUrl = SERVER) { app ->
            questionArrives(qa)
            clickOnRow(QUESTION, "Deny")

            await("the question is denied") { qa.questions.single().status == QuestionStatus.DENIED }
            onAllNodes(hasText("FILTER", substring = true))[0].performClick()
            waitForIdle()
            onAllNodes(hasText("Incoming (", substring = true))[0].performClick()
            waitForIdle()
            await("the question left the incoming queue") { !shows(QUESTION) }
            assertFalse(app.presenter.isLive(Presenting.QA))
        }
    }
}
