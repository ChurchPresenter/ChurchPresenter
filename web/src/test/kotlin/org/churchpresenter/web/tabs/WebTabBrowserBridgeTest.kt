@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.web.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.mockk.mockk
import io.mockk.verify
import org.cef.browser.CefBrowser
import org.churchpresenter.settings.WebBookmark
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The pieces of `WebTab` that only run once a live `CefBrowser` is attached, plus the narrow-width
 * layout `BoxWithConstraints` picks between. `CefBrowser` is JCEF's native browser handle — nothing
 * a test can construct for real — so it stands in as `mockk<CefBrowser>(relaxed = true)`, matching
 * `WebNavControllerTest`. There is no other observable effect of these branches (they exist to talk
 * to a real page that is not present here), so a `verify` on the mock is the only assertion available.
 *
 * See `WebTabTestSupport.kt` for the harness.
 */
class WebTabBrowserBridgeTest {

    // ── The URL bar's Enter key ─────────────────────────────────────────────────

    @Test
    fun `pressing Enter in the URL bar normalises it and updates the presenter`() = webTab { presenter, _ ->
        onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).performTextReplacement("example.com")
        onNodeWithText("example.com").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        onNodeWithText("https://example.com").assertExists()
        assertEquals("https://example.com", presenter.websiteUrl.value)
    }

    @Test
    fun `pressing Enter in the URL bar while live also navigates the live browser`() = webTab { presenter, _ ->
        val browser = mockk<CefBrowser>(relaxed = true)
        presenter.setPresentingMode(Presenting.WEBSITE)
        presenter.setLiveBrowser(browser)
        waitForIdle()

        onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).performTextReplacement("example.com")
        onNodeWithText("example.com").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        verify { browser.loadURL("https://example.com") }
    }

    // ── Attaching a live browser ─────────────────────────────────────────────────

    @Test
    fun `attaching a live browser applies the current zoom level`() = webTab { presenter, _ ->
        val browser = mockk<CefBrowser>(relaxed = true)
        presenter.setPresentingMode(Presenting.WEBSITE)

        presenter.setLiveBrowser(browser)
        waitForIdle()

        verify { browser.setZoomLevel(0.0) }
    }

    @Test
    fun `toggling desktop-mobile while live reloads the live browser`() = webTab { presenter, _ ->
        val browser = mockk<CefBrowser>(relaxed = true)
        presenter.setPresentingMode(Presenting.WEBSITE)
        presenter.setLiveBrowser(browser)
        waitForIdle()

        onNodeWithText(WebLabel.DESKTOP).performClick()
        waitForIdle()

        verify { browser.reload() }
    }

    // ── The "type to page" field with a live browser attached ───────────────────

    @Test
    fun `typing into the type-to-page field with a live browser injects JavaScript per character`() =
        webTab { presenter, _ ->
        val browser = mockk<CefBrowser>(relaxed = true)
        presenter.setPresentingMode(Presenting.WEBSITE)
        presenter.setLiveBrowser(browser)
        waitForIdle()

        onNodeWithText(WebLabel.TYPE_TO_PAGE_PLACEHOLDER).performTextInput("hi")
        waitForIdle()

        onNodeWithText("hi").assertExists()
        verify(exactly = 2) { browser.executeJavaScript(any(), "", 0) }
    }

    @Test
    fun `typing a quote and a backslash escapes them for JavaScript`() = webTab { presenter, _ ->
        val browser = mockk<CefBrowser>(relaxed = true)
        presenter.setPresentingMode(Presenting.WEBSITE)
        presenter.setLiveBrowser(browser)
        waitForIdle()

        onNodeWithText(WebLabel.TYPE_TO_PAGE_PLACEHOLDER).performTextInput("a\"\\")
        waitForIdle()

        onNodeWithText("a\"\\").assertExists()
        verify(exactly = 3) { browser.executeJavaScript(any(), "", 0) }
    }

    @Test
    fun `pressing Enter in the type-to-page field submits and clears it`() = webTab { presenter, _ ->
        val browser = mockk<CefBrowser>(relaxed = true)
        presenter.setPresentingMode(Presenting.WEBSITE)
        presenter.setLiveBrowser(browser)
        waitForIdle()

        onNodeWithText(WebLabel.TYPE_TO_PAGE_PLACEHOLDER).performTextInput("hi")
        onNodeWithText("hi").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        onNodeWithText(WebLabel.TYPE_TO_PAGE_PLACEHOLDER).assertExists()
    }

    @Test
    fun `clicking Focus first input with a live browser attached executes the focus script`() = webTab { presenter, _ ->
        val browser = mockk<CefBrowser>(relaxed = true)
        presenter.setPresentingMode(Presenting.WEBSITE)
        presenter.setLiveBrowser(browser)
        waitForIdle()

        webButton(WebLabel.FOCUS_FIRST_INPUT).performClick()
        waitForIdle()

        verify { browser.executeJavaScript(any(), "", 0) }
    }

    // ── Narrow layout ─────────────────────────────────────────────────────────────

    /** Comfortably under the 960dp (440+200+320) single-row threshold. */
    private val narrow = 700.dp

    @Test
    fun `the narrow layout still offers every toolbar control`() = webTab(width = narrow) { _, _ ->
        webButton(WebLabel.BACK).assertExists()
        webButton(WebLabel.FORWARD).assertExists()
        webButton(WebLabel.REFRESH).assertExists()
        webButton(WebLabel.CLEAR_CACHE).assertExists()
        webButton(WebLabel.ZOOM_IN).assertExists()
        webButton(WebLabel.ZOOM_OUT).assertExists()
        webButton(WebLabel.BOOKMARK_ADD).assertExists()
        webButton(WebLabel.GO_LIVE).assertExists()
        onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).assertExists()
    }

    @Test
    fun `the narrow layout stacks the URL bar below the toolbar`() = webTab(width = narrow) { _, _ ->
        val back = webButton(WebLabel.BACK).fetchSemanticsNode().boundsInRoot
        val urlField = onAllNodes(hasSetTextAction())[0].fetchSemanticsNode().boundsInRoot

        assertTrue(
            urlField.top >= back.bottom,
            "URL bar (top=${urlField.top}) must sit below the toolbar (bottom=${back.bottom})",
        )
    }

    @Test
    fun `the wide layout keeps the toolbar and URL bar on one line`() = webTab { _, _ ->
        val back = webButton(WebLabel.BACK).fetchSemanticsNode().boundsInRoot
        val urlField = onAllNodes(hasSetTextAction())[0].fetchSemanticsNode().boundsInRoot

        assertTrue(urlField.top < back.bottom, "the URL bar shares the row with the toolbar at full width")
    }

    // ── Navigation while live ───────────────────────────────────────────────────

    /** Live, mirroring, with [browser] as the live window's browser. */
    private fun androidx.compose.ui.test.ComposeUiTest.liveWith(presenter: FakeWebOutput, browser: CefBrowser) {
        presenter.setPresentingMode(Presenting.WEBSITE)
        presenter.setLiveBrowser(browser)
        waitForIdle()
    }

    @Test
    fun `while mirroring, Back Forward and Refresh drive the live browser`() = webTab { presenter, _ ->
        val browser = mockk<CefBrowser>(relaxed = true)
        liveWith(presenter, browser)

        webButton(WebLabel.BACK).performClick()
        webButton(WebLabel.FORWARD).performClick()
        webButton(WebLabel.REFRESH).performClick()

        verify { browser.goBack() }
        verify { browser.goForward() }
        verify { browser.reload() }
    }

    @Test
    fun `in interactive mode Back Forward and Refresh leave the live browser alone`() = webTab { presenter, _ ->
        val browser = mockk<CefBrowser>(relaxed = true)
        liveWith(presenter, browser)
        onNodeWithText(WebLabel.MIRROR).performClick()
        waitForIdle()

        webButton(WebLabel.BACK).performClick()
        webButton(WebLabel.FORWARD).performClick()
        webButton(WebLabel.REFRESH).performClick()

        verify(exactly = 0) { browser.goBack() }
        verify(exactly = 0) { browser.goForward() }
        verify(exactly = 0) { browser.reload() }
    }

    @Test
    fun `a bookmark chip clicked while live loads it into the live browser`() = webTab(
        settings = { it.copy(webBookmarks = listOf(WebBookmark(url = "https://a.example", title = ""))) },
    ) { presenter, _ ->
        val browser = mockk<CefBrowser>(relaxed = true)
        liveWith(presenter, browser)

        // A chip with no title shows its address instead.
        onAllNodesWithText("https://a.example")[0].performClick()
        waitForIdle()

        assertEquals("https://a.example", presenter.websiteUrl.value)
        verify { browser.loadURL("https://a.example") }
    }

    // ── Type to page ────────────────────────────────────────────────────────────

    @Test
    fun `shortening the typed text sends one backspace per removed character`() = webTab { presenter, _ ->
        val browser = mockk<CefBrowser>(relaxed = true)
        liveWith(presenter, browser)

        onNodeWithText(WebLabel.TYPE_TO_PAGE_PLACEHOLDER).performTextInput("abc")
        onNodeWithText("abc").performTextReplacement("a")
        waitForIdle()

        verify(exactly = 2) { browser.executeJavaScript(WEB_JS_BACKSPACE, "", 0) }
    }

    @Test
    fun `the clear button empties the typed text without touching the page`() = webTab { presenter, _ ->
        val browser = mockk<CefBrowser>(relaxed = true)
        liveWith(presenter, browser)
        onNodeWithText(WebLabel.TYPE_TO_PAGE_PLACEHOLDER).performTextInput("x")

        webButton(WebLabel.CLEAR_TYPED_TEXT).performClick()
        waitForIdle()

        onNodeWithText(WebLabel.TYPE_TO_PAGE_PLACEHOLDER).assertExists()
        verify(exactly = 0) { browser.executeJavaScript(WEB_JS_BACKSPACE, "", 0) }
    }

    @Test
    fun `control characters are escaped before they reach the page`() {
        assertTrue("\"\\n\"" in jsInsert('\n'))
        assertTrue("\"\\r\"" in jsInsert('\r'))
        assertTrue("\"\\t\"" in jsInsert('\t'))
        assertTrue("\"\\u0001\"" in jsInsert('\u0001'))
        assertTrue("\"é\"" in jsInsert('é'), "printable text goes through as itself")
    }

    // ── The address bar ─────────────────────────────────────────────────────────

    @Test
    fun `clearing the address shows the example address as a hint`() = webTab { _, _ ->
        onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).performTextReplacement("example.com")
        webButton(WebLabel.CLEAR_URL).performClick()
        waitForIdle()

        onNodeWithText(WebLabel.URL_HINT).assertExists()
        assertTrue(!hasWebButton(WebLabel.CLEAR_URL), "nothing left to clear")
    }

    // ── Real defaults ─────────────────────────────────────────────────────────────

    @Test
    fun `WebTab with no overrides falls back to the real CefManager state`() = runComposeUiTest {
        setContent { MaterialTheme { WebTab() } }

        onNodeWithText(WebLabel.ENGINE_UNAVAILABLE_TITLE).assertExists()
    }
}
