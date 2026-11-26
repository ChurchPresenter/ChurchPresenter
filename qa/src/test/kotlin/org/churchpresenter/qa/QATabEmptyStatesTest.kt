@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.qa

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.sharedui.testing.renderedText
import org.churchpresenter.sharedui.testing.showsExactly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QATabEmptyStatesTest {

    @Test
    fun `with no session the empty list explains one and starts it`() = qaTab { _, _, _ ->
        assertTrue(showsExactly("No Q&A session running"), renderedText().toString())
        assertFalse(showsExactly("Resume last session"), "nothing finished yet to resume")

        onNodeWithText("Start session").performClick()
        waitForIdle()

        assertTrue(showsExactly(QALabel.START_SESSION), "the naming dialog is open: ${renderedText()}")
    }

    @Test
    fun `the last finished session can be picked up again from the empty list`() =
        qaTab(seed = {
            startSession("Morning")
            askAll("Morning question")
            stopSession()
            startSession("Evening")
            askAll("Evening question")
            stopSession()
        }) { qa, _, _ ->
            onNodeWithText("Resume last session").performClick()
            waitForIdle()

            assertTrue(qa.sessionActive)
            assertEquals("Evening", qa.sessionName, "the newest one")
        }

    @Test
    fun `an open session with nothing asked shows where people join, and puts it on screen`() =
        qaTab(seed = { startSession("Open") }) { qa, output, _ ->
            assertTrue(showsExactly("Waiting for questions"), renderedText().toString())
            assertTrue(showsExactly("http://192.0.2.1:8080/qa"))

            onNodeWithText("Show join info on screen").performClick()
            waitForIdle()
            assertTrue(qa.showQRCodeOnDisplay)
            assertTrue(output.showQRCodeOnDisplay.value)
            assertTrue(showsExactly("Hide join info"))

            onNodeWithText("Hide join info").performClick()
            waitForIdle()
            assertFalse(qa.showQRCodeOnDisplay)
        }
}
