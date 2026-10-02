@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.qa

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.testing.showsExactly
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Views the other suites never land on: the Incoming filter over a mixed queue, the empty list
 * before any session, the remote-access slot, and the tab composed with only what it requires.
 */
class QATabViewsTest {

    private companion object {
        const val REMOTE_DIALOG = "Remote dialog"
    }

    @Test
    fun `the incoming filter lists only questions still waiting for a decision`() =
        qaTab(seed = {
            askAll("Still waiting", "Already approved")
            approveQuestion(questions.first { it.text == "Already approved" }.id)
        }) { _, _, _ ->
            selectFilter(QALabel.INCOMING)

            assertTrue(showsQuestion("Still waiting"))
            assertFalse(showsQuestion("Already approved"))
        }

    @Test
    fun `before a session the empty list says to start one, under every waiting filter`() =
        qaTab { _, _, _ ->
            assertTrue(showsExactly(QALabel.START_SESSION_HINT), "All")

            selectFilter(QALabel.INCOMING)
            assertTrue(showsExactly(QALabel.START_SESSION_HINT), QALabel.INCOMING)

            selectFilter(QALabel.INCOMING_APPROVED)
            assertTrue(showsExactly(QALabel.START_SESSION_HINT), QALabel.INCOMING_APPROVED)
        }

    @Test
    fun `the remote button opens the app's dialog, which can close itself`() =
        qaTab(remoteDialog = { onDismiss -> Text(REMOTE_DIALOG, Modifier.clickable(onClick = onDismiss)) }) { _, _, _ ->
            assertFalse(showsExactly(REMOTE_DIALOG))

            qaButton(QALabel.REMOTE).performClick()
            waitForIdle()
            assertTrue(showsExactly(REMOTE_DIALOG))

            clickQaLabel(REMOTE_DIALOG)
            assertFalse(showsExactly(REMOTE_DIALOG))
        }

    @Test
    fun `the tab draws with only the manager, the output, the address and presenting`() {
        val realHome = System.getProperty("user.home")
        val tempHome = Files.createTempDirectory("cp-qa-defaults").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        try {
            val qa = QAManager()
            runComposeUiTest {
                setContent {
                    QATab(qaManager = qa, output = FakeQAOutput(), serverUrl = "", presenting = { _: Presenting -> })
                }
                assertTrue(showsExactly(QALabel.START_SESSION_HINT))
            }
        } finally {
            realHome?.let { System.setProperty("user.home", it) }
            tempHome.deleteRecursively()
        }
    }
}
