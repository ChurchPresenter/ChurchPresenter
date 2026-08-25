@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.web

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.mockk
import io.mockk.verify
import org.cef.browser.CefBrowser
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Which browser the toolbar drives once a page is live.
 *
 * This is the tab's most consequential either/or and it is invisible in the UI: Back, Forward,
 * Refresh and the zoom controls act on **the browser the audience is looking at** while the page is
 * live and the preview is mirroring it, and on the tab's own preview browser otherwise. Getting it
 * backwards means the operator presses Back and the congregation sees nothing change — or worse,
 * sees the page move when the operator only meant to look.
 *
 * Every test here attaches a browser to the output, which is what `isLive && !useInteractivePreview
 * && live != null` needs; the false side of that condition is covered by the suites that run without
 * one.
 */
class WebTabLiveToolbarTest {

    private fun liveBrowser() = mockk<CefBrowser>(relaxed = true)

    /** Live, mirroring, with [browser] attached — the state where the toolbar drives the output. */
    private fun mirroringLive(browser: CefBrowser, body: androidx.compose.ui.test.ComposeUiTest.() -> Unit) =
        webTab { output, _ ->
            output.live = true
            output.liveBrowser = browser
            output.setSnapshot(ImageBitmap(64, 64))
            waitForIdle()
            body()
        }

    @Test
    fun `Back goes back on the live page, not the preview`() {
        val browser = liveBrowser()
        mirroringLive(browser) {
            webButton(WebLabel.BACK).performClick()
            waitForIdle()
        }
        verify { browser.goBack() }
    }

    @Test
    fun `Forward goes forward on the live page`() {
        val browser = liveBrowser()
        mirroringLive(browser) {
            webButton(WebLabel.FORWARD).performClick()
            waitForIdle()
        }
        verify { browser.goForward() }
    }

    @Test
    fun `Refresh reloads the live page`() {
        val browser = liveBrowser()
        mirroringLive(browser) {
            webButton(WebLabel.REFRESH).performClick()
            waitForIdle()
        }
        verify { browser.reload() }
    }

    @Test
    fun `zooming in sets the zoom on the live page`() {
        val browser = liveBrowser()
        mirroringLive(browser) {
            webButton(WebLabel.ZOOM_IN).performClick()
            waitForIdle()
        }
        verify { browser.setZoomLevel(any()) }
    }

    @Test
    fun `zooming out sets the zoom on the live page`() {
        val browser = liveBrowser()
        mirroringLive(browser) {
            webButton(WebLabel.ZOOM_OUT).performClick()
            waitForIdle()
        }
        verify { browser.setZoomLevel(any()) }
    }

    @Test
    fun `switching to interactive hands the toolbar back to the preview`() {
        val browser = liveBrowser()
        mirroringLive(browser) {
            onNodeWithText(WebLabel.MIRROR).performClick()
            waitForIdle()

            webButton(WebLabel.BACK).performClick()
            waitForIdle()
        }
        // Interactive means the operator is driving the tab's own browser again, so the live one is
        // left alone — the audience keeps seeing whatever was already on screen.
        verify(exactly = 0) { browser.goBack() }
    }

    @Test
    fun `the live badge is shown while a page is live`() {
        val browser = liveBrowser()
        mirroringLive(browser) {
            onNodeWithText(WebLabel.LIVE_BADGE).assertExists()
        }
    }

    @Test
    fun `Go Live is not offered again while already live`() {
        val browser = liveBrowser()
        mirroringLive(browser) {
            assertTrue(hasWebButton(WebLabel.GO_LIVE), "the button stays present")
            webButton(WebLabel.GO_LIVE).assertIsNotEnabled()
        }
    }
}
