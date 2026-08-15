@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ShareYourStoryContentTest {

    private class Clicks {
        var shared = 0
        var dismissed = 0
    }

    private fun dialog(block: ComposeUiTest.(Clicks) -> Unit) {
        val clicks = Clicks()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(SHARE_STORY_DIALOG_WIDTH, SHARE_STORY_DIALOG_HEIGHT)) {
                        ShareYourStoryContent(
                            onShare = { clicks.shared++ },
                            onDismiss = { clicks.dismissed++ },
                        )
                    }
                }
            }
            block(clicks)
        }
    }

    @Test
    fun `the ask is shown`() = dialog {
        onNodeWithText("Has Church Presenter helped your church?").assertExists()
        onNodeWithText("WE'D LOVE TO HEAR FROM YOU").assertExists()
    }

    @Test
    fun `the three examples are shown`() = dialog {
        onNodeWithText("A smoother service order").assertExists()
        onNodeWithText("Scripture on the screen without a scramble").assertExists()
        onNodeWithText("One less subscription to pay for").assertExists()
    }

    @Test
    fun `the pull quote is shown`() = dialog {
        onNodeWithText("Every church runs it a little differently.").assertExists()
    }

    @Test
    fun `the reassurance is shown`() = dialog {
        onNodeWithText("Nothing is published without your OK.").assertExists()
    }

    @Test
    fun `sharing a story reports it once and does not dismiss`() = dialog { clicks ->
        onNodeWithText("Share your story").performClick()

        assertEquals(1, clicks.shared)
        assertEquals(0, clicks.dismissed)
    }

    @Test
    fun `maybe later dismisses without sharing`() = dialog { clicks ->
        onNodeWithText("Maybe later").performClick()

        assertEquals(1, clicks.dismissed)
        assertEquals(0, clicks.shared)
    }
}
