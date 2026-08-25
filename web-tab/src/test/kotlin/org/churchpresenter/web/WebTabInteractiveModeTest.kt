@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.web

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import org.cef.browser.CefBrowser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Interactive mode, and the toolbar in its narrow layout.
 *
 * Interactive is the half of the live/preview split that nothing drove: the operator stops watching
 * the mirrored screenshot and starts using the tab's *own* browser again, while the page stays live
 * on the output. Every toolbar control has to change target at that moment — and, crucially, must
 * stop touching the live browser, or the operator's private browsing shows up in front of the
 * congregation.
 */
class WebTabInteractiveModeTest {

    private fun interactive(block: ComposeUiTest.(FakeWebOutput, CefBrowser) -> Unit) {
        val browser = mockk<CefBrowser>(relaxed = true)
        webTab { output, _ ->
            output.live = true
            output.liveBrowser = browser
            output.setSnapshot(ImageBitmap(32, 32))
            waitForIdle()
            onNodeWithText(WebLabel.MIRROR).performClick()
            waitForIdle()
            block(output, browser)
        }
    }

    @Test
    fun `the toggle reads Interactive once switched`() = interactive { _, _ ->
        onNodeWithText(WebLabel.INTERACTIVE).assertExists()
    }

    @Test
    fun `Forward no longer touches the live page`() = interactive { _, browser ->
        webButton(WebLabel.FORWARD).performClick()
        waitForIdle()

        verify(exactly = 0) { browser.goForward() }
    }

    @Test
    fun `Refresh no longer touches the live page`() = interactive { _, browser ->
        webButton(WebLabel.REFRESH).performClick()
        waitForIdle()

        verify(exactly = 0) { browser.reload() }
    }

    @Test
    fun `zooming no longer touches the live page`() = interactive { _, browser ->
        // The tab pushes its current zoom to the live browser once that browser appears, so calls
        // already exist by now — what matters is that the *click* adds none.
        clearMocks(browser, answers = false, recordedCalls = true, verificationMarks = true)

        webButton(WebLabel.ZOOM_IN).performClick()
        waitForIdle()

        verify(exactly = 0) { browser.setZoomLevel(any()) }
    }

    @Test
    fun `the page stays live while the operator browses privately`() = interactive { output, _ ->
        // The whole point of interactive mode: the congregation keeps seeing the page while the
        // operator moves around. Dropping live here would blank the screen.
        assertTrue(output.isLive)
    }

    @Test
    fun `switching back to mirror hands the toolbar to the live page again`() = interactive { _, browser ->
        onNodeWithText(WebLabel.INTERACTIVE).performClick()
        waitForIdle()

        webButton(WebLabel.BACK).performClick()
        waitForIdle()

        verify { browser.goBack() }
    }

    // ── The narrow, two-row toolbar ─────────────────────────────────────────────

    @Test
    fun `a narrow panel still offers every control`() {
        // Below navButtons(440) + minUrl(200) + actions(320) the toolbar stacks into two rows — a
        // different composition, not a reflow, so the controls have to be checked for again.
        webTab(width = 520.dp) { _, _ ->
            listOf(
                WebLabel.BACK, WebLabel.FORWARD, WebLabel.REFRESH,
                WebLabel.ZOOM_IN, WebLabel.ZOOM_OUT, WebLabel.CLEAR_CACHE,
            ).forEach { assertTrue(hasWebButton(it), "$it is missing from the narrow layout") }
        }
    }

    @Test
    fun `a narrow panel still offers Go Live and the bookmark action`() {
        webTab(width = 520.dp) { _, _ ->
            assertTrue(hasWebButton(WebLabel.GO_LIVE))
            assertTrue(hasWebButton(WebLabel.BOOKMARK_ADD) || hasWebButton(WebLabel.BOOKMARK_REMOVE))
        }
    }

    // ── WebNavController's remaining surface ────────────────────────────────────

    @Test
    fun `canGoBack and canGoForward report what the browser says`() {
        val browser = mockk<CefBrowser>(relaxed = true)
        io.mockk.every { browser.canGoBack() } returns true
        io.mockk.every { browser.canGoForward() } returns false

        val controller = WebNavController().also { it.browser = browser }

        assertTrue(controller.canGoBack())
        assertFalse(controller.canGoForward())
    }

    @Test
    fun `canGoBack and canGoForward are false with no browser attached`() {
        val controller = WebNavController()

        // Before the preview has built a browser there is no history to consult, and the buttons
        // must not claim otherwise.
        assertFalse(controller.canGoBack())
        assertFalse(controller.canGoForward())
        assertEquals(false, controller.mobileMode)
    }
}
