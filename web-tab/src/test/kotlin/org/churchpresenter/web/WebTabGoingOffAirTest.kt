@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.mockk.mockk
import io.mockk.verify
import org.cef.browser.CefBrowser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Coming back off air, and the two states that only exist while a page is live.
 *
 * Every other suite here goes live and stays there. Coming *back* is its own path and it has to
 * clean up after itself: the mirrored snapshot has to be dropped, or the tab keeps showing a frozen
 * picture of a page nobody is looking at any more, and the type-to-page buffer has to be cleared, or
 * the next go-live starts mid-sentence.
 */
class WebTabGoingOffAirTest {

    private fun liveThen(
        block: androidx.compose.ui.test.ComposeUiTest.(FakeWebOutput, CefBrowser) -> Unit,
    ) {
        val browser = mockk<CefBrowser>(relaxed = true)
        webTab { output, _ ->
            output.live = true
            output.liveBrowser = browser
            output.setSnapshot(ImageBitmap(32, 32))
            waitForIdle()
            block(output, browser)
        }
    }

    @Test
    fun `coming off air drops the mirrored snapshot`() = liveThen { output, _ ->
        assertTrue(output.snapshot != null, "a snapshot was showing while live")

        output.live = false
        waitForIdle()

        // A frozen frame of a page that is no longer on screen reads to the operator as still live.
        assertEquals(null, output.snapshot)
    }

    @Test
    fun `coming off air and going live again starts from the wait state`() = liveThen { output, _ ->
        output.live = false
        waitForIdle()

        output.live = true
        waitForIdle()

        // No snapshot carried over, so the operator is told the new page is still loading rather
        // than shown the last one.
        assertEquals(null, output.snapshot)
    }

    @Test
    fun `toggling mobile emulation while live reloads the live page`() = liveThen { _, browser ->
        onNodeWithText(WebLabel.DESKTOP).performClick()
        waitForIdle()

        // The live browser has its own navigation controller, so the only way to make it pick up
        // the new user agent is to reload it.
        verify { browser.reload() }
    }

    @Test
    fun `toggling mobile emulation off air leaves the live browser alone`() {
        val browser = mockk<CefBrowser>(relaxed = true)
        webTab { output, _ ->
            output.liveBrowser = browser

            onNodeWithText(WebLabel.DESKTOP).performClick()
            waitForIdle()

            verify(exactly = 0) { browser.reload() }
        }
    }

    // ── WebEngineUnavailable ────────────────────────────────────────────────────

    @Test
    fun `the unavailable notice draws with every parameter defaulted`() = runComposeUiTest {
        // Defaults read the real CefManager flags, which is how the tab itself composes it.
        setContent { MaterialTheme { Box(Modifier.size(200.dp)) { WebEngineUnavailable() } } }
        waitForIdle()

        onNodeWithText(WebLabel.ENGINE_UNAVAILABLE_TITLE).assertExists()
    }

    @Test
    fun `the unavailable notice draws with every parameter supplied`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.size(200.dp)) {
                    WebEngineUnavailable(
                        modifier = Modifier.fillMaxSize(),
                        macOsUnsupported = false,
                        windowsUnsupported = false,
                    )
                }
            }
        }
        waitForIdle()

        onNodeWithText(WebLabel.ENGINE_UNAVAILABLE_TITLE).assertExists()
    }
}
