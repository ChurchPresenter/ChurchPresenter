@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals

class WebTabLiveTest {

    @Test
    fun `going live shows the LIVE badge and syncs the URL and title from the presenter`() = webTab { presenter, _ ->
        presenter.setPresentingMode(Presenting.WEBSITE)
        waitForIdle()
        presenter.setWebsiteUrl("https://live.example")
        presenter.setWebPageTitle("Live Page")
        waitForIdle()

        onNodeWithText(WebLabel.LIVE_BADGE).assertExists()
        onNodeWithText("Live Page").assertExists()
        onNodeWithText("https://live.example").assertExists()
    }

    @Test
    fun `mirror mode is the default and the type-to-page field is shown`() = webTab { presenter, _ ->
        presenter.setPresentingMode(Presenting.WEBSITE)
        waitForIdle()

        onNodeWithText(WebLabel.MIRROR).assertExists()
        onNodeWithText(WebLabel.TYPE_TO_PAGE_PLACEHOLDER).assertExists()
        webButton(WebLabel.FOCUS_FIRST_INPUT).assertExists()
    }

    @Test
    fun `toggling to interactive mode hides the type-to-page field`() = webTab { presenter, _ ->
        presenter.setPresentingMode(Presenting.WEBSITE)
        waitForIdle()

        onNodeWithText(WebLabel.MIRROR).performClick()

        onNodeWithText(WebLabel.INTERACTIVE).assertExists()
        onNodeWithText(WebLabel.TYPE_TO_PAGE_PLACEHOLDER).assertDoesNotExist()

        onNodeWithText(WebLabel.INTERACTIVE).performClick()
        onNodeWithText(WebLabel.MIRROR).assertExists()
    }

    @Test
    fun `typing into the type-to-page field updates it even with no live browser attached`() = webTab { presenter, _ ->
        presenter.setPresentingMode(Presenting.WEBSITE)
        waitForIdle()

        onNodeWithText(WebLabel.TYPE_TO_PAGE_PLACEHOLDER).performTextInput("hello")

        onNodeWithText("hello").assertExists()
    }

    @Test
    fun `clicking Focus first input does not crash with no live browser attached`() = webTab { presenter, _ ->
        presenter.setPresentingMode(Presenting.WEBSITE)
        waitForIdle()

        webButton(WebLabel.FOCUS_FIRST_INPUT).performClick()

        assertEquals(Presenting.WEBSITE, presenter.presentingMode.value)
    }

    @Test
    fun `leaving live mode clears the snapshot`() = webTab { presenter, _ ->
        presenter.setPresentingMode(Presenting.WEBSITE)
        waitForIdle()
        presenter.setPresentingMode(Presenting.NONE)
        waitForIdle()

        onNodeWithText(WebLabel.LIVE_BADGE).assertDoesNotExist()
        assertEquals(null, presenter.webSnapshot.value)
    }
}
