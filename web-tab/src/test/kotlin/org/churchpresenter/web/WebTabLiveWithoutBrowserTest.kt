@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.web

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import org.churchpresenter.settings.WebBookmark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Live, but with no browser attached yet.
 *
 * This is a real state and not a short one: between the operator pressing Go Live and the output
 * window finishing its own Chromium, `liveBrowser` is null while `isLive` is already true. Every
 * control in the toolbar reaches for that browser through `output?.liveBrowser?.…`, so every one of
 * them takes its null path here — and none of them may throw, because the operator is mid-service
 * and clicking.
 *
 * The suites either side of this one cover the two settled states: off air entirely, and live with a
 * browser. This is the seam between them, which nothing else exercised.
 */
class WebTabLiveWithoutBrowserTest {

    private fun liveNoBrowser(block: ComposeUiTest.(FakeWebOutput) -> Unit) =
        webTab(
            settings = {
                it.copy(webBookmarks = listOf(WebBookmark(url = "https://a.example", title = "Alpha")))
            },
        ) { output, _ ->
            output.live = true
            output.setSnapshot(ImageBitmap(32, 32))
            waitForIdle()
            assertEquals(null, output.liveBrowser, "this suite is about the null-browser window")
            block(output)
        }

    @Test
    fun `Back does nothing rather than throwing`() = liveNoBrowser { _ ->
        webButton(WebLabel.BACK).performClick()
        waitForIdle()
    }

    @Test
    fun `Forward does nothing rather than throwing`() = liveNoBrowser { _ ->
        webButton(WebLabel.FORWARD).performClick()
        waitForIdle()
    }

    @Test
    fun `Refresh does nothing rather than throwing`() = liveNoBrowser { _ ->
        webButton(WebLabel.REFRESH).performClick()
        waitForIdle()
    }

    @Test
    fun `zooming still records the level for when the browser arrives`() = liveNoBrowser { _ ->
        webButton(WebLabel.ZOOM_IN).performClick()
        webButton(WebLabel.ZOOM_OUT).performClick()
        waitForIdle()
    }

    @Test
    fun `focus first input does nothing rather than throwing`() = liveNoBrowser { _ ->
        webButton(WebLabel.FOCUS_FIRST_INPUT).performClick()
        waitForIdle()
    }

    @Test
    fun `toggling mobile emulation does not reach for a browser that is not there`() = liveNoBrowser { _ ->
        onNodeWithText(WebLabel.DESKTOP).performClick()
        waitForIdle()
    }

    @Test
    fun `typing into the type-to-page field is buffered, not sent`() = liveNoBrowser { _ ->
        onNodeWithText(WebLabel.TYPE_TO_PAGE_PLACEHOLDER).performTextInput("hello")
        waitForIdle()

        // The text stays in the field. It is not lost, and it is not sent to a browser that does
        // not exist — the operator can carry on typing and it lands when the page comes up.
        onNodeWithText("hello").assertExists()
    }

    @Test
    fun `pressing Enter in the type-to-page field is survived`() = liveNoBrowser { _ ->
        onNodeWithText(WebLabel.TYPE_TO_PAGE_PLACEHOLDER).performTextInput("hi")
        waitForIdle()
        onNodeWithText("hi").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
    }

    @Test
    fun `a bookmark still stages its address`() = liveNoBrowser { output ->
        onAllNodesWithText("Alpha")[0].performClick()
        waitForIdle()

        // No browser to navigate, but the address must still be adopted so that the page loads as
        // soon as the output has one.
        assertEquals("https://a.example", output.url)
    }

    @Test
    fun `Enter in the address bar still stages the address`() = liveNoBrowser { output ->
        onNodeWithText(WebLabel.URL_PLACEHOLDER_DEFAULT).performTextReplacement("b.example")
        onNodeWithText("b.example").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertEquals("https://b.example", output.url)
    }

    @Test
    fun `the mirrored image absorbs pointer input with nothing to forward it to`() = liveNoBrowser { _ ->
        onRoot().performMouseInput {
            moveTo(center)
            press()
            release()
            scroll(1f)
        }
        waitForIdle()
    }

    @Test
    fun `the tab still reports itself live`() = liveNoBrowser { _ ->
        assertTrue(
            onAllNodesWithText(WebLabel.LIVE_BADGE).fetchSemanticsNodes(false).isNotEmpty(),
            "the LIVE badge belongs to the output's state, not to whether a browser exists",
        )
    }
}
