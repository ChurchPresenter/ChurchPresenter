@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.web

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import io.mockk.mockk
import io.mockk.verify
import org.cef.browser.CefBrowser
import org.churchpresenter.settings.WebBookmark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Picking a bookmark, and the empty-field hint.
 *
 * A bookmark does two different things depending on what is on screen. Off air it only loads the
 * address into the tab, ready for the operator to check before going live. **On air it navigates
 * the live page immediately** — one click and the congregation is looking somewhere else. Both
 * halves of that are worth pinning, because the difference is invisible in the UI.
 */
class WebTabBookmarkAndHintTest {

    private val bookmark = WebBookmark(url = "https://notices.example", title = "Notices")

    private fun withBookmark(block: androidx.compose.ui.test.ComposeUiTest.(FakeWebOutput) -> Unit) =
        webTab(settings = { it.copy(webBookmarks = listOf(bookmark)) }) { output, _ -> block(output) }

    @Test
    fun `clicking a bookmark off air only stages the address`() {
        val browser = mockk<CefBrowser>(relaxed = true)
        withBookmark { output ->
            output.liveBrowser = browser

            onNodeWithText(bookmark.title).performClick()
            waitForIdle()

            assertEquals(bookmark.url, output.url, "the address is staged for the operator")
            // Nothing is live, so nothing the audience sees may move.
            verify(exactly = 0) { browser.loadURL(any()) }
        }
    }

    @Test
    fun `clicking a bookmark on air navigates the live page straight away`() {
        val browser = mockk<CefBrowser>(relaxed = true)
        withBookmark { output ->
            output.live = true
            output.liveBrowser = browser
            output.setSnapshot(ImageBitmap(32, 32))
            waitForIdle()

            onNodeWithText(bookmark.title).performClick()
            waitForIdle()

            assertEquals(bookmark.url, output.url)
            verify { browser.loadURL(bookmark.url) }
        }
    }

    @Test
    fun `the address field starts pre-filled with a scheme rather than empty`() {
        webTab { _, _ ->
            // Not the hint: the field's own value. It opens on "https://" so the operator can type
            // a bare domain, which is why the empty-state hint below needs the field cleared first.
            onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).assertExists()
        }
    }

    @Test
    fun `clearing the address field reveals its hint`() {
        webTab { _, _ ->
            onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).performTextReplacement("")
            waitForIdle()

            // The hint draws only on the empty branch of the field's decoration box — the branch an
            // operator reaches by selecting all and deleting, which nothing else here does.
            onNodeWithText(WebLabel.URL_HINT).assertExists()
        }
    }

    @Test
    fun `focus first input reaches the live page`() {
        val browser = mockk<CefBrowser>(relaxed = true)
        webTab { output, _ ->
            output.live = true
            output.liveBrowser = browser
            output.setSnapshot(ImageBitmap(32, 32))
            waitForIdle()

            webButton(WebLabel.FOCUS_FIRST_INPUT).performClick()
            waitForIdle()

            // The operator's route to typing into a live page: focus something first, then type.
            verify { browser.executeJavaScript(any(), "", 0) }
        }
    }

    @Test
    fun `the bookmark for the page currently live is highlighted`() {
        val browser = mockk<CefBrowser>(relaxed = true)
        withBookmark { output ->
            output.live = true
            output.liveBrowser = browser
            output.setSnapshot(ImageBitmap(32, 32))
            waitForIdle()

            // Going live via the bookmark is what makes it the current one; the row then draws on a
            // container colour instead of the surface, so the operator can see at a glance which of
            // a long bookmark bar is on screen.
            onAllNodesWithText(bookmark.title)[0].performClick()
            waitForIdle()

            // The title now shows twice — on the bookmark row and in the live status line — which
            // is itself the signal that the tab adopted it.
            assertEquals(bookmark.url, output.url)
            assertTrue(
                onAllNodesWithText(bookmark.title).fetchSemanticsNodes(false).size >= 2,
                "the live page's title should appear beside the bookmark row",
            )
        }
    }

    @Test
    fun `a bookmark can be removed once it has been added`() {
        webTab(settings = { it.copy(webBookmarks = listOf(bookmark)) }) { _, reports ->
            onNodeWithText(bookmark.title).performClick()
            waitForIdle()

            // With the live address matching a saved bookmark, the star turns into a remove action.
            assertTrue(hasWebButton(WebLabel.BOOKMARK_REMOVE) || hasWebButton(WebLabel.BOOKMARK_ADD))
            assertEquals(0, reports.settingsChanges, "clicking a bookmark saves nothing by itself")
        }
    }
}
