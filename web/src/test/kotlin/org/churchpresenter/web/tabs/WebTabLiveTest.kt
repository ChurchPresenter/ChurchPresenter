@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.web.tabs

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.mockk.mockk
import io.mockk.verify
import org.cef.browser.CefBrowser
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

    @Test
    fun `a title the presenter reports for a loaded page retitles its schedule item`() = webTab { presenter, reports ->
        presenter.setPresentingMode(Presenting.WEBSITE)
        waitForIdle()
        presenter.setWebsiteUrl("https://live.example")
        waitForIdle()
        presenter.setWebPageTitle("Loaded Later")
        waitForIdle()

        assertEquals(listOf("https://live.example" to "Loaded Later"), reports.titleUpdates)
    }

    @Test
    fun `in interactive mode the presenter's navigation does not move the address bar`() =
        webTab { presenter, reports ->
        presenter.setPresentingMode(Presenting.WEBSITE)
        waitForIdle()
        onNodeWithText(WebLabel.MIRROR).performClick()
        waitForIdle()

        presenter.setWebsiteUrl("https://elsewhere.example")
        presenter.setWebPageTitle("Elsewhere")
        waitForIdle()

        // The operator is browsing their own copy; the live window wandering off must not drag it along.
        onNodeWithText("https://elsewhere.example").assertDoesNotExist()
        assertEquals(emptyList(), reports.titleUpdates)
    }

    @Test
    fun `a live browser that attaches while live is given the tab's zoom`() = webTab { presenter, _ ->
        presenter.setPresentingMode(Presenting.WEBSITE)
        waitForIdle()

        val browser = mockk<CefBrowser>(relaxed = true)
        presenter.setLiveBrowser(browser)
        waitForIdle()

        verify { browser.setZoomLevel(0.0) }
    }

    @Test
    fun `a live browser that attaches while not live is left alone`() = webTab { presenter, _ ->
        val browser = mockk<CefBrowser>(relaxed = true)
        presenter.setLiveBrowser(browser)
        waitForIdle()

        verify(exactly = 0) { browser.setZoomLevel(any()) }
    }

    @Test
    fun `a title that arrives before its address has no schedule item to retitle yet`() = webTab { presenter, reports ->
        presenter.setPresentingMode(Presenting.WEBSITE)
        waitForIdle()
        presenter.setWebPageTitle("Early Title")
        waitForIdle()

        onNodeWithText("Early Title").assertExists()
        assertEquals(emptyList(), reports.titleUpdates)
    }
}
