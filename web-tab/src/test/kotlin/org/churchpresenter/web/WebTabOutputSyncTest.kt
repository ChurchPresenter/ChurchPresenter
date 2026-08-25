@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.web

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import io.mockk.mockk
import io.mockk.verify
import org.cef.browser.CefBrowser
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The tab following the output, and the address field driving it.
 *
 * While a page is live and the preview is mirroring it, the *output* is the source of truth: the
 * page can navigate itself (a redirect, a link the operator clicked through the mirror) and the
 * tab's address bar and title have to follow, or the operator ends up looking at a stale address
 * and adds the wrong URL to the schedule. Going interactive stops that following, because then the
 * operator is driving a different browser.
 */
class WebTabOutputSyncTest {

    /**
     * Whether [text] is rendered anywhere.
     *
     * A live address shows up twice — once in the editable field and once in the status line beside
     * the LIVE badge — so `onNodeWithText` fails on the count rather than on the content.
     */
    private fun androidx.compose.ui.test.ComposeUiTest.shows(text: String): Boolean =
        onAllNodesWithText(text, substring = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    private fun liveMirroring(
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

    // ── The tab follows the output ──────────────────────────────────────────────

    @Test
    fun `an address the live page navigated to reaches the tab's address bar`() = liveMirroring { output, _ ->
        output.setUrl("https://redirected.example")
        waitForIdle()

        assertTrue(shows("https://redirected.example"))
    }

    @Test
    fun `a title the live page reports reaches the tab`() = liveMirroring { output, _ ->
        output.setUrl("https://notices.example")
        output.setTitle("Sunday Notices")
        waitForIdle()

        assertTrue(shows("Sunday Notices"))
    }

    @Test
    fun `a blank address from the output is ignored rather than blanking the bar`() {
        val browser = mockk<CefBrowser>(relaxed = true)
        webTab { output, _ ->
            output.live = true
            output.liveBrowser = browser
            output.setSnapshot(ImageBitmap(32, 32))
            output.setUrl("https://kept.example")
            waitForIdle()

            // An empty push is what the output reports between pages; treating it as an address
            // would clear the bar mid-service for no reason.
            output.setUrl("")
            waitForIdle()

            assertTrue(shows("https://kept.example"))
        }
    }

    @Test
    fun `going interactive stops the tab following the live page`() = liveMirroring { output, _ ->
        onNodeWithText(WebLabel.MIRROR).performClick()
        waitForIdle()

        output.setUrl("https://moved-on.example")
        waitForIdle()

        // The operator is driving their own browser now, so the output's address must not yank the
        // field out from under whatever they are typing.
        assertTrue(!shows("https://moved-on.example"))
    }

    // ── The address field drives the output ─────────────────────────────────────

    @Test
    fun `pressing Enter in the address bar navigates the live page`() = liveMirroring { output, browser ->
        onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).performTextReplacement("notices.example")
        onNodeWithText("notices.example").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        // Typed without a scheme, stored with one — the field normalises before anything sees it.
        assertEquals("https://notices.example", output.url)
        verify { browser.loadURL("https://notices.example") }
    }

    @Test
    fun `pressing Enter off air stages the address without navigating anything`() {
        val browser = mockk<CefBrowser>(relaxed = true)
        webTab { output, _ ->
            output.liveBrowser = browser

            onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).performTextReplacement("notices.example")
            onNodeWithText("notices.example").performKeyInput { pressKey(Key.Enter) }
            waitForIdle()

            assertEquals("https://notices.example", output.url)
            verify(exactly = 0) { browser.loadURL(any()) }
        }
    }

    // ── Which outputs can show a website at all ─────────────────────────────────

    private fun withAssignment(
        assignment: ScreenAssignment,
        assertion: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) =
        webTab(
            settings = {
                it.copy(projectionSettings = ProjectionSettings(screenAssignments = listOf(assignment)))
            },
        ) { _, _ -> assertion() }

    @Test
    fun `a normal display with websites enabled counts as a web-capable output`() =
        withAssignment(ScreenAssignment(targetDisplay = 0, targetType = "screen", showWebsite = true)) {
            // Go Live still needs a second monitor, which a test machine has not got — what this
            // pins is that the assignment itself is accepted, which is the half that is settings.
            assertTrue(hasWebButton(WebLabel.GO_LIVE))
        }

    @Test
    fun `a DeckLink output does not count, however it is configured`() =
        withAssignment(ScreenAssignment(targetDisplay = 0, targetType = "decklink", showWebsite = true)) {
            assertTrue(hasWebButton(WebLabel.GO_LIVE))
            webButton(WebLabel.GO_LIVE).assertIsNotEnabled()
        }

    @Test
    fun `an output with websites switched off does not count`() =
        withAssignment(ScreenAssignment(targetDisplay = 0, targetType = "screen", showWebsite = false)) {
            webButton(WebLabel.GO_LIVE).assertIsNotEnabled()
        }

    @Test
    fun `an unassigned output does not count`() =
        withAssignment(ScreenAssignment(targetDisplay = -1, targetType = "screen", showWebsite = true)) {
            webButton(WebLabel.GO_LIVE).assertIsNotEnabled()
        }
}
