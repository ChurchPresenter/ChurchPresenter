package org.churchpresenter.liveoutput

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.presenter.LocalInMergedTile
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.WebPageView
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

    private fun state(opens: Boolean = true) = LiveWebState(context, offscreen = true) { url, _ ->
        urls += url
        if (opens) stubOffscreenPage().also { pages += it } else null
    }

    @AfterTest
    fun closePages() {
        pages.forEach(OffscreenPage::close)
    }

    private val modes = mutableListOf<Boolean>()
    private val callbacks = mutableListOf<PageCallbacks>()

    private fun twoLayouts() = LiveWebState(context, offscreen = true) { _, page ->
        modes += page.mobileMode()
        callbacks += page
        stubOffscreenPage().also { pages += it }
    }

    private fun surface(
        kind: OutputSurfaceKind,
        manager: PresenterManager,
        view: WebPageView = WebPageView.FOLLOW_WEB_TAB,
    ) = OutputSurface(kind, OutputProfile(webPageView = view), AppSettings(), manager, outputRole = "", showBg = true)

    /** Runs what the close timer would, once it has fired. */
    private fun LiveWebState.afterCloseDelay(mobile: Boolean = false) {
        SwingUtilities.invokeAndWait { closeUnusedPage(mobile) }
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
        val web = LiveWebState(context, offscreen = false) { _, _ -> error("not called") }
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

    // ── The desktop and mobile layouts ──────────────────────────────────────────

    @Test
    fun `each layout is a page of its own, asking for its own User-Agent`() {
        val web = twoLayouts()
        web.useOffscreenPage(mobile = false)
        web.useOffscreenPage(mobile = true)

        assertEquals(listOf(false, true), modes)
        assertSame(pages[0], web.offscreenPageFor(false))
        assertSame(pages[1], web.offscreenPageFor(true))
    }

    @Test
    fun `the Web tab steers the page in its button's layout, or the other when that one is not open`() {
        val web = twoLayouts()
        web.useOffscreenPage(mobile = true)
        val mobile = pages.single()
        assertSame(mobile, web.offscreenPage.value, "only the mobile page is open")
        assertSame(mobile.browser, web.liveBrowser.value)

        web.useOffscreenPage(mobile = false)
        val desktop = pages[1]
        assertSame(desktop, web.offscreenPage.value, "the button says desktop")
        assertSame(desktop.browser, web.liveBrowser.value)

        web.setWebMobile(true)
        assertTrue(web.webMobile.value)
        assertSame(mobile, web.offscreenPage.value)
        assertSame(mobile.browser, web.liveBrowser.value)
        web.setWebMobile(true)
        assertSame(mobile.browser, web.liveBrowser.value, "pressing the same layout again changes nothing")

        web.releaseOffscreenPage(mobile = true)
        web.afterCloseDelay(mobile = true)
        assertNull(web.offscreenPageFor(true))
        assertSame(desktop, web.offscreenPage.value, "with the mobile page gone, the tab steers the desktop one")
        assertSame(desktop.browser, web.liveBrowser.value)
    }

    @Test
    fun `only the page the Web tab steers moves the address and the title`() {
        val web = twoLayouts()
        web.useOffscreenPage(mobile = false)
        web.useOffscreenPage(mobile = true)
        val (desktop, mobile) = callbacks

        mobile.onUrlChanged("https://m.example.org")
        mobile.onTitleChanged("Mobile")
        assertEquals("", web.websiteUrl.value)
        assertEquals("", web.webPageTitle.value)

        desktop.onUrlChanged("https://example.org")
        desktop.onTitleChanged("Desktop")
        assertEquals("https://example.org", web.websiteUrl.value)
        assertEquals("Desktop", web.webPageTitle.value)
    }

    @Test
    fun `closing one layout leaves the other open`() {
        val web = twoLayouts()
        web.useOffscreenPage(mobile = false)
        web.useOffscreenPage(mobile = true)
        web.releaseOffscreenPage(mobile = false)
        web.afterCloseDelay(mobile = false)
        web.afterCloseDelay(mobile = false)

        assertNull(web.offscreenPageFor(false))
        assertSame(pages[1], web.offscreenPageFor(true))
        assertSame(pages[1].browser, web.liveBrowser.value)
    }

    @Test
    fun `each screen draws the page its profile asks for, and a following screen moves with the button`() {
        val web = twoLayouts()
        val manager = PresenterManager(context, web)
        manager.setWebsiteUrl("https://example.org")
        runComposeUiTest {
            setContent {
                WebCue(surface(OutputSurfaceKind.OFFSCREEN, manager))
                WebCue(surface(OutputSurfaceKind.OFFSCREEN, manager, WebPageView.MOBILE))
                WebCue(surface(OutputSurfaceKind.OFFSCREEN, manager, WebPageView.DESKTOP))
            }
            waitForIdle()
            onAllNodes(hasTestTag(WEB_OFFSCREEN_TAG)).assertCountEquals(3)
            assertEquals(listOf(false, true), modes, "one page per layout, however many screens show it")

            manager.setWebMobile(true)
            waitForIdle()
            onAllNodes(hasTestTag(WEB_OFFSCREEN_TAG)).assertCountEquals(3)
            assertEquals(listOf(false, true), modes, "the following screen joined the open mobile page")
        }
    }
}
