package org.churchpresenter.web.offscreen

import io.mockk.mockk
import org.cef.CefBrowserSettings
import org.cef.browser.CefBrowserHeadless
import org.cef.browser.CefPaintEvent
import org.cef.browser.HeadlessSink
import org.cef.handler.CefScreenInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.nio.ByteBuffer
import java.util.concurrent.ExecutionException
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The windowless browser as Chromium's render handler: the view and screen it reports, the size and
 * scale a resize changes, and everything Chromium hands it passed to the sink, a paint that throws
 * included. JCEF's native calls are no-ops without its library, so the input methods only have to
 * take their events.
 */
class CefBrowserHeadlessTest {
    private class RecordingSink : HeadlessSink {
        val paints = mutableListOf<Pair<Boolean, Int>>()
        val popups = mutableListOf<Any>()
        var cursor = -1
        var failure: Throwable? = null
        var throwOnPaint = false

        override fun onPaint(
            popup: Boolean,
            dirtyRects: Array<Rectangle>,
            buffer: ByteBuffer,
            width: Int,
            height: Int,
        ) {
            if (throwOnPaint) error("paint")
            paints += popup to width
        }
        override fun onPopupShow(show: Boolean) { popups += show }
        override fun onPopupSize(rect: Rectangle) { popups += rect }
        override fun onCursorChange(cursorType: Int) { cursor = cursorType }
        override fun onPaintFailed(failure: Throwable) { this.failure = failure }
    }

    private val sink = RecordingSink()
    private val browser =
        CefBrowserHeadless(mockk(relaxed = true), "about:blank", CefBrowserSettings(), 1920, 1080, 2.0, sink)

    @Test
    fun `the view and the screen are the page's size, at the page's scale`() {
        assertEquals(Rectangle(0, 0, 1920, 1080), browser.getViewRect(browser))
        val info = CefScreenInfo()
        assertTrue(browser.getScreenInfo(browser, info))
        assertEquals(2.0, info.device_scale_factor)
        assertEquals(Point(5, 7), browser.getScreenPoint(browser, Point(5, 7)))
    }

    @Test
    fun `a resize changes the size and scale Chromium is told`() {
        browser.resize(960, 540, 1.0)
        assertEquals(960 to 540, browser.viewWidth to browser.viewHeight)
        assertEquals(1.0, browser.scale)
        assertEquals(Rectangle(0, 0, 960, 540), browser.getViewRect(browser))
    }

    @Test
    fun `paints, popups and the cursor reach the sink, and paint listeners hear each paint`() {
        val heard = mutableListOf<CefPaintEvent>()
        browser.addOnPaintListener(Consumer { heard += it })

        browser.onPaint(browser, false, arrayOf(Rectangle(0, 0, 4, 4)), ByteBuffer.allocate(64), 4, 4)
        browser.onPopupSize(browser, Rectangle(1, 2, 3, 4))
        browser.onPopupShow(browser, true)
        assertTrue(browser.onCursorChange(browser, 12))

        assertEquals(listOf(false to 4), sink.paints)
        assertEquals(listOf<Any>(Rectangle(1, 2, 3, 4), true), sink.popups)
        assertEquals(12, sink.cursor)
        assertEquals(1, heard.size)

        val replaced = mutableListOf<CefPaintEvent>()
        val listener = Consumer<CefPaintEvent> { replaced += it }
        browser.setOnPaintListener(listener)
        browser.removeOnPaintListener(listener)
        browser.onPaint(browser, false, arrayOf(Rectangle(0, 0, 4, 4)), ByteBuffer.allocate(64), 4, 4)
        assertTrue(replaced.isEmpty())
    }

    @Test
    fun `a paint that throws goes to the sink's failure, not back into Chromium`() {
        sink.throwOnPaint = true
        browser.onPaint(browser, false, arrayOf(Rectangle(0, 0, 4, 4)), ByteBuffer.allocate(64), 4, 4)
        assertEquals("paint", sink.failure?.message)
    }

    @Test
    fun `it has no window, no screenshots and no drag and drop`() {
        assertSame(browser.eventSource, browser.uiComponent)
        assertSame(browser, browser.renderHandler)
        assertFailsWith<ExecutionException> { browser.createScreenshot(true).get() }
        assertFalse(browser.startDragging(browser, null, 0, 0, 0))
        browser.updateDragCursor(browser, 0)
    }

    @Test
    fun `input is taken without the native library`() {
        val src = browser.eventSource
        browser.mouse(MouseEvent(src, MouseEvent.MOUSE_MOVED, 0, 0, 1, 1, 0, false))
        val notch = MouseWheelEvent(
            src, MouseEvent.MOUSE_WHEEL, 0, 0, 1, 1, 0, false,
            MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, -1,
        )
        browser.wheel(notch)
        browser.key(KeyEvent(src, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'a'))
    }
}
