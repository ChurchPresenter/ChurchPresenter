package org.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The crash-feedback dialog shown after an unexpected shutdown. Its one real rule: the Send button
 * is gated on a non-blank comment — an empty report is worthless to Sentry — and what it sends is
 * the trimmed comment and email. Both the gate and the trim are asserted, plus that "Not now"
 * dismisses without sending.
 */
@OptIn(ExperimentalTestApi::class)
class CrashFeedbackDialogTest {

    @Test
    fun `send is disabled until a comment is entered, then sends the trimmed values`() = runComposeUiTest {
        var sentComment: String? = null
        var sentEmail: String? = null
        setContent {
            MaterialTheme {
                CrashFeedbackDialog(
                    onDismiss = {},
                    onSend = { comment, email -> sentComment = comment; sentEmail = email },
                )
            }
        }

        onNodeWithText("Send report").assertIsNotEnabled()

        // First text field is the comment; whitespace around it must be trimmed off before sending.
        onAllNodes(hasSetTextAction())[0].performTextInput("  export froze  ")

        onNodeWithText("Send report").assertIsEnabled()
        onNodeWithText("Send report").performClick()

        assertEquals("export froze", sentComment, "the trimmed comment must be sent, not the raw padded text")
        assertEquals("", sentEmail, "an untouched email field sends as empty, not null")
    }

    @Test
    fun `not now dismisses without sending`() = runComposeUiTest {
        var dismissed = false
        var sent = false
        setContent {
            MaterialTheme {
                CrashFeedbackDialog(
                    onDismiss = { dismissed = true },
                    onSend = { _, _ -> sent = true },
                )
            }
        }

        onNodeWithText("Not now").performClick()

        assertEquals(true, dismissed, "the dismiss button must invoke onDismiss")
        assertNull(sent.takeIf { it }, "dismissing must not send a report")
    }
}
