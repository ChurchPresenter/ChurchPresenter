package org.churchpresenter.web.offscreen

import androidx.compose.ui.unit.IntSize
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.cef.CefClient
import org.cef.browser.CefBrowserHeadless
import org.cef.callback.CefMenuModel
import org.cef.handler.CefContextMenuHandler
import org.cef.handler.CefDisplayHandler
import org.cef.handler.CefLifeSpanHandler
import org.cef.handler.CefRequestHandler
import java.awt.Rectangle
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One off-screen page against a client of the test's own: the handlers it installs, the size it is
 * laid out at and how a request changes it, paints reaching its frames, what a failed start leaves,
 * and closing. The native browser is never started ([OffscreenPage.Options.start] does nothing), and
 * JCEF's native calls are no-ops without its library, so this is the page's own logic end to end.
 */
class OffscreenPageTest {
    private val contextMenu = slot<CefContextMenuHandler>()
    private val display = slot<CefDisplayHandler>()
    private val client = mockk<CefClient>(relaxed = true) {
        every { addContextMenuHandler(capture(contextMenu)) } returns this
        every { addDisplayHandler(capture(display)) } returns this
    }
    private val sent = mutableListOf<OffscreenReport>()
    private val opened = mutableListOf<OffscreenPage>()

    private fun open(os: String = "Mac OS X", start: (CefBrowserHeadless) -> Unit = {}): OffscreenPage? =
        OffscreenPage.openWith(
            client, "https://example.org/", {}, {},
            OffscreenPage.Options(os, reports = true, mobileMode = { false }, start = start, send = { sent += it }),
        )?.also { opened += it }

    @AfterTest
    fun closePages() {
        opened.forEach(OffscreenPage::close)
    }

    @Test
    fun `a page installs its handlers and starts at 1080p`() {
        val page = assertNotNull(open())

        verify { client.addLifeSpanHandler(any<CefLifeSpanHandler>()) }
        verify { client.addRequestHandler(any<CefRequestHandler>()) }
        assertEquals(1920, page.browser.viewWidth)
        assertEquals(1080, page.browser.viewHeight)
        assertEquals(1.0, page.browser.scale)
    }

    @Test
    fun `the largest output a page is asked for sets its size and scale`() {
        val page = assertNotNull(open())
        val projector = Any()
        val window = Any()

        page.requestSize(window, IntSize(960, 512))
        page.requestSize(projector, IntSize(3840, 2160))
        page.applySize()
        assertEquals(1920 to 1080, page.browser.viewWidth to page.browser.viewHeight)
        assertEquals(2.0, page.browser.scale)

        page.releaseSize(projector)
        page.applySize()
        assertEquals(960 to 512, page.browser.viewWidth to page.browser.viewHeight)
        assertEquals(1.0, page.browser.scale)
    }

    @Test
    fun `a page of its own takes its screen's scale, and a change of scale alone resizes it`() {
        val page = assertNotNull(open())
        val preview = Any()
        page.requestSize(preview, IntSize(1500, 900), density = 1.5f)
        page.applySize()
        assertEquals(1000 to 600, page.browser.viewWidth to page.browser.viewHeight)
        assertEquals(1.5, page.browser.scale)

        page.requestSize(preview, IntSize(1500, 900), density = 1f)
        page.applySize()
        assertEquals(1.0, page.browser.scale)
    }

    @Test
    fun `a size of nothing is not a request`() {
        val page = assertNotNull(open())
        page.requestSize(Any(), IntSize(0, 0))
        page.applySize()
        assertEquals(1920, page.browser.viewWidth)
    }

    @Test
    fun `Chromium's paints reach the page's frames`() {
        val page = assertNotNull(open())
        page.browser.onPaint(page.browser, false, arrayOf(Rectangle(0, 0, 800, 450)), paintBuffer(800, 450), 800, 450)

        assertEquals(800, assertNotNull(page.frames.value).view.width)
    }

    @Test
    fun `a paint that throws is reported, not thrown into Chromium`() {
        val page = assertNotNull(open())
        // A buffer smaller than the size it claims makes the copy throw.
        page.browser.onPaint(page.browser, false, arrayOf(Rectangle(0, 0, 800, 450)), paintBuffer(8, 8), 800, 450)

        assertIs<OffscreenReport.PaintThrew>(sent.single())
    }

    @Test
    fun `a page that fails to start is reported, its client freed, and none is returned`() {
        assertNull(open(start = { throw IllegalStateException("no engine") }))

        assertEquals("no engine", assertIs<OffscreenReport.PageFailed>(sent.single()).failure.message)
        verify { client.dispose() }
    }

    @Test
    fun `closing a page clears its frames and frees its client, once`() {
        val page = assertNotNull(open())
        page.browser.onPaint(page.browser, false, arrayOf(Rectangle(0, 0, 800, 450)), paintBuffer(800, 450), 800, 450)

        page.close()
        page.close()

        assertNull(page.frames.value)
        verify(exactly = 1) { client.dispose() }
        assertTrue(sent.none { it is OffscreenReport.Session }, "a session this short sends no summary")
    }

    @Test
    fun `a page still open at a quit sends its session summary, and closing it later sends no second`() {
        var now = 0L
        val page = assertNotNull(
            OffscreenPage.openWith(
                client, "https://example.org/", {}, {},
                OffscreenPage.Options(
                    "Mac OS X", reports = true, mobileMode = { false }, start = {}, send = { sent += it },
                    clock = { now }, sessionsSent = AtomicInteger(),
                ),
            )?.also { opened += it },
        )
        now = 60_000

        OffscreenPage.reportOpenSessions()
        page.close()

        assertEquals(1, sent.count { it is OffscreenReport.Session })
    }

    @Test
    fun `the page clears Chromium's context menu, which it could not draw`() {
        assertNotNull(open())
        val model = mockk<CefMenuModel>(relaxed = true)
        contextMenu.captured.onBeforeContextMenu(null, null, null, model)
        verify { model.clear() }
    }

    @Test
    fun `only on Linux are console messages read for the clipboard`() {
        assertNotNull(open(os = "Mac OS X"))
        assertFalse(display.captured.onConsoleMessage(null, null, "__churchpresenter_clipboard__:x", null, 0))
        assertFalse(display.captured.onConsoleMessage(null, null, null, null, 0))
    }

    @Test
    fun `on Linux a page message that is not the clipboard's is left to the page`() {
        assertNotNull(open(os = "Linux"))
        assertFalse(display.captured.onConsoleMessage(null, null, "an ordinary page message", null, 0))
        assertFalse(display.captured.onConsoleMessage(null, null, null, null, 0))
    }

    @Test
    fun `a size with no width or no height is not a request`() {
        val page = assertNotNull(open())
        page.requestSize(Any(), IntSize(640, 0))
        page.requestSize(Any(), IntSize(0, 360))
        page.applySize()
        assertEquals(1920, page.browser.viewWidth)
    }

    @Test
    fun `no page opens while the engine renders windowed`() {
        assertNull(OffscreenPage.open("https://example.org/", {}, {}))
    }
}
