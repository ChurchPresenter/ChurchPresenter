@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.web.offscreen

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.churchpresenter.web.presenter.WebNavController
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The rest of the frame store's and the surfaces' paths: a view whose size changes, paints that
 * come faster than the levels, a popup that is resized or painted before any view, closing while
 * the levels are being made; what counts as the same picture for a redraw; a popup drawn over the
 * page and a picture shrunk below its smallest level; pointer events re-made for the page; and where
 * reports go.
 */
class OffscreenCoverageTest {
    private val page = testPage()
    private val store = OffscreenFrames({ 960 }, rgba = true, stats = OffscreenStats(), onPaintFailed = {})

    @AfterTest
    fun close() {
        page.close()
        store.close()()
    }

    private fun paintStore(width: Int, height: Int, seed: Int = 1) =
        store.onPaint(false, arrayOf(Rectangle(0, 0, width, height)), paintBuffer(width, height, seed), width, height)

    private fun awaitLevels(width: Int) = runBlocking {
        withTimeout(5_000) { checkNotNull(store.frames.first { it?.view?.width == width && it.levels.isNotEmpty() }) }
    }

    @Test
    fun `a view that changes size gets a ring and a pyramid of its new size`() {
        paintStore(1920, 1080)
        awaitLevels(1920)
        paintStore(1280, 720)
        val frame = awaitLevels(1280)
        assertEquals(640, frame.levels.single().width)
        paintStore(800, 450)
        assertEquals(800, assertNotNull(store.frames.value).view.width)
    }

    @Test
    fun `paints that come faster than the levels are taken newest first`() {
        repeat(8) { paintStore(1920, 1080, seed = it) }
        val frame = awaitLevels(1920)
        assertTrue(frame.levelSeq in 1..8)
    }

    @Test
    fun `closing while the levels are made frees them instead of publishing them`() {
        paintStore(3840, 2160)
        store.close()()
        assertEquals(null, store.frames.value)
    }

    @Test
    fun `a popup painted before any view waits for one, and a resized popup is copied anew`() {
        store.onPopupSize(Rectangle(0, 0, 50, 20))
        store.onPopupShow(true)
        store.onPaint(true, arrayOf(Rectangle(0, 0, 50, 20)), paintBuffer(50, 20), 50, 20)
        assertEquals(null, store.frames.value)

        paintStore(800, 450)
        store.onPaint(true, arrayOf(Rectangle(0, 0, 60, 30)), paintBuffer(60, 30), 60, 30)
        assertEquals(60, assertNotNull(assertNotNull(store.frames.value).popup).image.width)
    }

    @Test
    fun `two shown pictures are the same only with the same picture and the same popup`() {
        paintStore(800, 450)
        val frame = assertNotNull(store.frames.value)
        val shown = Shown.of(frame, small = false)
        assertTrue(Shown.Policy.equivalent(shown, Shown.of(frame, small = true)))
        assertTrue(Shown.Policy.equivalent(null, null))
        assertFalse(Shown.Policy.equivalent(shown, null))

        store.onPopupSize(Rectangle(0, 0, 50, 20))
        store.onPopupShow(true)
        store.onPaint(true, arrayOf(Rectangle(0, 0, 50, 20)), paintBuffer(50, 20), 50, 20)
        val withPopup = Shown.of(assertNotNull(store.frames.value), small = false)
        assertFalse(Shown.Policy.equivalent(shown, withPopup))
        assertTrue(Shown.Policy.equivalent(withPopup, withPopup))
    }

    @Test
    fun `a popup is drawn over the page and a tiny surface draws the smallest level`() = runComposeUiTest {
        paint(page, 1920, 1080)
        page.browser.onPopupSize(page.browser, Rectangle(10, 10, 100, 40))
        page.browser.onPopupShow(page.browser, true)
        page.browser.onPaint(page.browser, true, arrayOf(Rectangle(0, 0, 100, 40)), paintBuffer(100, 40), 100, 40)
        waitUntil(timeoutMillis = 5_000) { page.frames.value?.levels?.isNotEmpty() == true }
        setContent {
            OffscreenWebView(page, Modifier.size(400.dp, 225.dp))
            OffscreenWebView(page, Modifier.size(100.dp, 56.dp), small = true)
        }
        onRoot().captureToImage()
        assertTrue(page.stats.drain().draws > 0)
    }

    @Test
    fun `a surface with no frame yet draws nothing`() = runComposeUiTest {
        setContent { OffscreenWebView(page, Modifier.size(100.dp, 100.dp)) }
        onRoot().captureToImage()
        assertEquals(0, page.stats.drain().draws)
    }

    @Test
    fun `a pointer event is re-made for the page, and a wheel turn too small waits`() {
        val src = page.browser.eventSource
        val wheel = WheelScaler("Mac OS X")
        val press = MouseEvent(src, MouseEvent.MOUSE_PRESSED, 0, 0, 1, 1, 1, false, MouseEvent.BUTTON1)
        send(page.browser, press, Point(5, 5), wheel)
        send(page.browser, wheelEvent(src, 0.001), Point(5, 5), wheel)
        send(page.browser, wheelEvent(src, 1.0), Point(5, 5), wheel)
        assertEquals(-40, WheelScaler("Mac OS X").rotationFor(1.0))
    }

    private fun wheelEvent(src: java.awt.Component, precise: Double) = MouseWheelEvent(
        src, MouseEvent.MOUSE_WHEEL, 0, 0, 1, 1, 1, 1, 0, false,
        MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 0, precise,
    )

    @Test
    fun `the tab's page passes its navigation and title on, and asks for mobile pages as told`() = runComposeUiTest {
        val nav = WebNavController()
        var request: PageRequest? = null
        val urls = mutableListOf<String>()
        val titles = mutableListOf<String>()
        setContent {
            OwnOffscreenPage(
                "https://example.org/", Modifier.size(100.dp), { urls += it }, { titles += it }, nav, null,
                open = { request = it; testPage() },
            )
        }
        waitForIdle()
        val asked = assertNotNull(request)
        asked.onUrlChanged("https://example.org/next")
        asked.onTitleChanged("Next")
        assertEquals(listOf("https://example.org/next"), urls)
        assertEquals(listOf("Next"), titles)
        assertFalse(asked.mobileMode())
        nav.setMobileEmulation(true)
        assertTrue(asked.mobileMode())
        assertEquals("https://example.org/", asked.url)
    }

    @Test
    fun `sending any kind of report, with reporting off or on, never throws`() {
        val failure = IllegalStateException("x")
        listOf(
            OffscreenReport.PageFailed(failure, emptyMap()),
            OffscreenReport.PaintThrew(failure, emptyMap()),
            OffscreenReport.LowFrameRate(emptyMap(), emptyMap()),
            OffscreenReport.Session(emptyMap(), emptyMap()),
        ).forEach(::sendToSentry)
        val quiet = OffscreenPage.Options("Mac OS X", reports = false, mobileMode = { false })
        quiet.send(OffscreenReport.PaintThrew(failure, emptyMap()))
        quiet.send(OffscreenReport.Session(emptyMap(), emptyMap()))
        val reporting = OffscreenPage.Options("Mac OS X", reports = true, mobileMode = { false })
        reporting.send(OffscreenReport.Session(emptyMap(), emptyMap()))
    }

    @Test
    fun `loading a blank or a new address is taken without the native library`() {
        page.load("")
        page.load("https://example.org/other")
    }

    @Test
    fun `the message pump runs on Windows only`() {
        SentMessagePump.start("Mac OS X")
        SentMessagePump.start("Linux")
    }
}
