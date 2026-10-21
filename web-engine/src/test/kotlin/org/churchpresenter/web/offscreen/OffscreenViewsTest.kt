@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.web.offscreen

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.cef.browser.CefBrowser
import org.churchpresenter.web.presenter.WebNavController
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The page's surfaces: an output view that draws the frames, counts its draws and drives the page's
 * size; the operator's control, which takes focus on a press; and the Web tab's page of its own,
 * opened and closed with the composable and handed to its navigation.
 */
class OffscreenViewsTest {
    private val page = testPage()

    @AfterTest
    fun closePage() {
        page.close()
    }

    @Test
    fun `an output view draws the frames and is counted as drawing them`() = runComposeUiTest {
        paint(page, 800, 450)
        setContent { OffscreenWebView(page, Modifier.size(400.dp, 225.dp)) }
        onRoot().captureToImage()

        assertTrue(page.stats.drain().draws > 0, "the view drew the frame")
    }

    @Test
    fun `an output window that drives the size gets the page laid out at its pixels`() = runComposeUiTest {
        var shown by mutableStateOf(true)
        setContent {
            if (shown) OffscreenWebView(page, Modifier.size(1000.dp, 500.dp).testTag("view"), drivesSize = true)
        }
        waitForIdle()
        val width = onNodeWithTag("view").fetchSemanticsNode().size.width

        page.applySize()
        assertEquals(width, page.browser.viewWidth)

        shown = false
        waitForIdle()
        page.requestSize(Any(), androidx.compose.ui.unit.IntSize(640, 360))
        page.applySize()
        assertEquals(640, page.browser.viewWidth, "the gone window no longer counts")
    }

    @Test
    fun `a small view draws the frame's levels and is not counted as an output`() = runComposeUiTest {
        paint(page, 1920, 1080)
        setContent { OffscreenWebView(page, Modifier.size(320.dp, 180.dp), small = true) }
        waitUntil(timeoutMillis = 5_000) { page.frames.value?.levels?.isNotEmpty() == true }
        onRoot().captureToImage()

        assertEquals(0, page.stats.drain().draws)
    }

    @Test
    fun `the operator's control takes focus on a press`() = runComposeUiTest {
        paint(page, 800, 450)
        setContent { OffscreenWebControl(page, Modifier.size(400.dp, 225.dp).testTag("control"), drivesSize = true) }

        onNodeWithTag("control").performClick()
        onNodeWithTag("control").assertIsFocused()
    }

    @Test
    fun `a page of its own opens with the composable, steers its navigation and closes with it`() = runComposeUiTest {
        val nav = WebNavController()
        var created: CefBrowser? = null
        var own: OffscreenPage? = null
        var shown by mutableStateOf(true)
        setContent {
            if (shown) {
                OwnOffscreenPage(
                    url = "https://example.org/",
                    modifier = Modifier.size(400.dp, 225.dp),
                    onUrlChanged = null,
                    onTitleChanged = null,
                    navController = nav,
                    onBrowserCreated = { created = it },
                    open = { testPage().also { own = it } },
                )
            }
        }
        waitForIdle()

        val opened = checkNotNull(own)
        assertSame(opened.browser, nav.browser)
        assertSame(opened.browser, created)

        paint(opened, 800, 450)
        shown = false
        waitForIdle()
        assertNull(nav.browser)
        assertNull(opened.frames.value, "the page closed with the composable")
    }

    @Test
    fun `a page that cannot open leaves an empty box`() = runComposeUiTest {
        val nav = WebNavController()
        setContent {
            OwnOffscreenPage("https://example.org/", Modifier.size(10.dp), null, null, nav, null, open = { null })
        }
        waitForIdle()
        assertNull(nav.browser)
    }
}
