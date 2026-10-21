package org.churchpresenter.liveoutput

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.presenter.LocalInMergedTile
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.web.offscreen.OffscreenPage
import org.churchpresenter.web.offscreen.stubOffscreenPage
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The live page off screen: one page for every surface that shows it, opened by the first and closed
 * a moment after the last lets go, handed to the Web tab as the live browser; a page that cannot
 * open turns every output back to a windowed browser of its own; and the web cue drawing the page.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class LiveWebOffscreenTest {
    private val context = PresenterContext()
    private val pages = mutableListOf<OffscreenPage>()
    private val urls = mutableListOf<String>()

    private fun state(opens: Boolean = true) = LiveWebState(context, offscreen = true) { url, _, _ ->
        urls += url
        if (opens) stubOffscreenPage().also { pages += it } else null
    }

    @AfterTest
    fun closePages() {
        pages.forEach(OffscreenPage::close)
    }

    private fun surface(kind: OutputSurfaceKind, manager: PresenterManager) =
        OutputSurface(kind, OutputProfile(), AppSettings(), manager, outputRole = "", showBg = true)

    /** Runs what the close timer would, once it has fired. */
    private fun LiveWebState.afterCloseDelay() {
        SwingUtilities.invokeAndWait { closeUnusedPage() }
    }

    @Test
    fun `the first surface opens the page at the live address and the tab steers its browser`() {
        val web = state()
        web.setWebsiteUrl("https://example.org/live")
        web.setWebSnapshot(ImageBitmap(2, 2))

        web.useOffscreenPage()
        web.useOffscreenPage()

        val page = pages.single()
        assertSame(page, web.offscreenPage.value)
        assertSame(page.browser, web.liveBrowser.value)
        assertNull(web.webSnapshot.value, "the outputs draw the page now, not a screenshot")
        assertEquals(listOf("https://example.org/live"), urls)
    }

    @Test
    fun `the page closes only once the last surface lets go`() {
        val web = state()
        web.useOffscreenPage()
        web.useOffscreenPage()
        val page = pages.single()

        web.releaseOffscreenPage()
        web.afterCloseDelay()
        assertSame(page, web.offscreenPage.value, "one surface still shows it")

        web.releaseOffscreenPage()
        web.afterCloseDelay()
        assertNull(web.offscreenPage.value)
        assertNull(web.liveBrowser.value)
        assertNull(page.frames.value)
    }

    @Test
    fun `a surface that comes back before the delay keeps the same page`() {
        val web = state()
        web.useOffscreenPage()
        web.releaseOffscreenPage()
        web.useOffscreenPage()
        web.afterCloseDelay()

        assertEquals(1, pages.size)
        assertSame(pages.single(), web.offscreenPage.value)
    }

    @Test
    fun `a page that cannot open turns the outputs back to the windowed browser`() {
        val web = state(opens = false)
        assertTrue(web.offscreenWebAvailable.value)

        web.useOffscreenPage()
        assertFalse(web.offscreenWebAvailable.value)
        web.useOffscreenPage()
        assertEquals(1, urls.size, "a failed page is not tried again this run")
    }

    @Test
    fun `a windowed engine never opens a page`() {
        val web = LiveWebState(context, offscreen = false) { _, _, _ -> error("not called") }
        web.useOffscreenPage()
        web.releaseOffscreenPage()
        web.releaseOffscreenPage()
        web.afterCloseDelay()
        assertNull(web.offscreenPage.value)
    }

    @Test
    fun `an output window, an off-screen output and a merged tile all draw the one page`() {
        val manager = PresenterManager(context, state())
        manager.setWebsiteUrl("https://example.org")
        listOf(OutputSurfaceKind.WINDOW, OutputSurfaceKind.OFFSCREEN).forEach { kind ->
            runComposeUiTest {
                setContent { WebCue(surface(kind, manager)) }
                waitForIdle()
                onAllNodes(hasTestTag(WEB_OFFSCREEN_TAG)).assertCountEquals(1)
                onAllNodes(hasTestTag(WEB_SNAPSHOT_TAG)).assertCountEquals(0)
            }
        }
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalInMergedTile provides true) {
                    WebCue(surface(OutputSurfaceKind.WINDOW, manager))
                }
            }
            waitForIdle()
            onAllNodes(hasTestTag(WEB_OFFSCREEN_TAG)).assertCountEquals(1)
        }
        assertEquals(1, pages.size, "every surface drew the same page")
    }

    @Test
    fun `the preview surface draws nothing and opens no page`() {
        val manager = PresenterManager(context, state())
        runComposeUiTest {
            setContent { WebCue(surface(OutputSurfaceKind.PREVIEW, manager)) }
            waitForIdle()
            onAllNodes(hasTestTag(WEB_OFFSCREEN_TAG)).assertCountEquals(0)
        }
        assertTrue(pages.isEmpty())
    }
}
