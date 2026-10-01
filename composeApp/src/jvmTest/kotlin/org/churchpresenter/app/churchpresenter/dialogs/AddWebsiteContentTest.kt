@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AddWebsiteContentTest {

    private class Result {
        var confirmed: Pair<String, String>? = null
        var dismissed = 0
    }

    private fun dialog(block: ComposeUiTest.(Result) -> Unit) {
        val result = Result()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    AddWebsiteDialogContent(
                        onDismiss = { result.dismissed++ },
                        onConfirm = { url, title -> result.confirmed = url to title },
                    )
                }
            }
            block(result)
        }
    }

    private fun ComposeUiTest.titleField(): SemanticsNodeInteraction = onAllNodes(hasSetTextAction())[0]
    private fun ComposeUiTest.urlField(): SemanticsNodeInteraction = onAllNodes(hasSetTextAction())[1]

    @Test
    fun `OK is disabled while the url is still the placeholder scheme`() = dialog {
        onNodeWithText("OK").assertIsNotEnabled()
    }

    @Test
    fun `OK is disabled for a blank url`() = dialog {
        urlField().performTextReplacement("")
        onNodeWithText("OK").assertIsNotEnabled()
    }

    @Test
    fun `OK becomes enabled once a real url is typed`() = dialog {
        urlField().performTextReplacement("https://example.com")
        onNodeWithText("OK").assertIsEnabled()
    }

    @Test
    fun `OK hands back the url and falls back to it as the title when none was typed`() = dialog { result ->
        urlField().performTextReplacement("https://example.com")
        onNodeWithText("OK").performClick()

        assertEquals("https://example.com" to "https://example.com", result.confirmed)
    }

    @Test
    fun `a typed title is sent instead of falling back to the url`() = dialog { result ->
        titleField().performTextInput("My Church")
        urlField().performTextReplacement("https://example.com")
        onNodeWithText("OK").performClick()

        assertEquals("My Church", result.confirmed?.second)
    }

    @Test
    fun `OK closes the dialog after confirming`() = dialog { result ->
        urlField().performTextReplacement("https://example.com")
        onNodeWithText("OK").performClick()

        assertEquals(1, result.dismissed)
    }

    @Test
    fun `Cancel dismisses without confirming`() = dialog { result ->
        urlField().performTextReplacement("https://example.com")
        onNodeWithText("Cancel").performClick()

        assertNull(result.confirmed)
        assertEquals(1, result.dismissed)
    }

    @Test
    fun `the disclaimer is shown`() = dialog {
        onNodeWithText(
            "We are not responsible for any advertisements or content that may appear on the displayed website.",
        ).assertExists()
    }
}
