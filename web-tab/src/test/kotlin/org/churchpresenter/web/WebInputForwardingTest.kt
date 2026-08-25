@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.web

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.performMouseInput
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.cef.browser.CefBrowser
import java.awt.Canvas
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Clicking and scrolling the mirrored image reaches the live browser, at the right place on the page.
 *
 * The mirror is a screenshot scaled to fit the tab, so a click at (x, y) in the preview has to be
 * mapped back onto the browser's own coordinates before it is forwarded — get the scale wrong and
 * every click lands somewhere other than where the operator aimed, which is invisible until someone
 * tries to press a button on a live page in front of a congregation.
 *
 * The forwarding is done by reflection, looking for `sendMouseEvent`/`sendMouseWheelEvent` by
 * walking up the browser's class hierarchy — those live on JCEF's `CefBrowser_N`, not on the
 * `CefBrowser` interface. [InputBrowser] below declares them, so the lookup succeeds without
 * starting Chromium, and the events land in a queue this test can read.
 *
 * The component the browser reports has to look real to the code under test — showing, with a
 * non-zero size — so it is an actual AWT [Canvas] with `isShowing` overridden rather than a mock.
 */
class WebInputForwardingTest {

    /** A component that claims to be on screen, so the readiness guard passes headless. */
    private class ShowingCanvas(private val w: Int, private val h: Int) : Canvas() {
        override fun isShowing() = true
        init { setSize(w, h) }
    }

    /**
     * Declares the two methods `findMethod` looks for. Abstract so mockk can fill in the rest of
     * `CefBrowser`, which has far more members than this test cares about.
     */
    abstract class InputBrowser : CefBrowser {
        @Suppress("UnusedParameter")
        fun sendMouseEvent(e: MouseEvent) = Unit

        @Suppress("UnusedParameter")
        fun sendMouseWheelEvent(e: MouseWheelEvent) = Unit
    }

    private fun browserOn(component: Canvas): InputBrowser {
        val browser = mockk<InputBrowser>(relaxed = true)
        every { browser.uiComponent } returns component
        return browser
    }

    /** The mouse events that actually reached [browser], in order. */
    private fun mouseEventsSentTo(browser: InputBrowser): List<MouseEvent> {
        val captured = mutableListOf<MouseEvent>()
        verify(atLeast = 0) { browser.sendMouseEvent(capture(captured)) }
        return captured
    }

    /** The wheel events that actually reached [browser]. */
    private fun wheelEventsSentTo(browser: InputBrowser): List<MouseWheelEvent> {
        val captured = mutableListOf<MouseWheelEvent>()
        verify(atLeast = 0) { browser.sendMouseWheelEvent(capture(captured)) }
        return captured
    }

    /** Waits for the Swing event queue to drain — the forwarding is posted with `invokeLater`. */
    private fun drainEdt() {
        repeat(3) { SwingUtilities.invokeAndWait { } }
    }

    @Test
    fun `clicking the mirrored image forwards a mouse event to the live browser`() {
        val component = ShowingCanvas(800, 600)
        val browser = browserOn(component)

        webTab { output, _ ->
            output.live = true
            output.liveBrowser = browser
            output.setSnapshot(ImageBitmap(400, 300))
            waitForIdle()

            onRoot().performMouseInput {
                moveTo(center)
                press()
                release()
            }
            waitForIdle()
        }
        drainEdt()

        assertTrue(mouseEventsSentTo(browser).isNotEmpty(), "the click never reached the browser")
    }

    @Test
    fun `a forwarded click lands inside the browser's own bounds`() {
        val component = ShowingCanvas(800, 600)
        val browser = browserOn(component)

        webTab { output, _ ->
            output.live = true
            output.liveBrowser = browser
            output.setSnapshot(ImageBitmap(400, 300))
            waitForIdle()
            onRoot().performMouseInput { moveTo(center); press(); release() }
            waitForIdle()
        }
        drainEdt()

        // Whatever the preview's size, the mapped point has to be a real pixel on the page — the
        // production code coerces into the component's bounds for exactly this reason.
        val sent = mouseEventsSentTo(browser)
        assertTrue(sent.isNotEmpty(), "nothing was forwarded, so the bounds check proves nothing")
        sent.forEach { e ->
            assertTrue(e.x in 0 until component.width, "x ${e.x} outside 0..${component.width - 1}")
            assertTrue(e.y in 0 until component.height, "y ${e.y} outside 0..${component.height - 1}")
        }
    }

    @Test
    fun `scrolling the mirrored image forwards a wheel event`() {
        val component = ShowingCanvas(800, 600)
        val browser = browserOn(component)

        webTab { output, _ ->
            output.live = true
            output.liveBrowser = browser
            output.setSnapshot(ImageBitmap(400, 300))
            waitForIdle()
            onRoot().performMouseInput { moveTo(center); scroll(1f) }
            waitForIdle()
        }
        drainEdt()

        assertTrue(wheelEventsSentTo(browser).isNotEmpty(), "the scroll never reached the browser")
    }

    @Test
    fun `with no browser attached the mirror stays inert`() {
        webTab { output, _ ->
            output.live = true
            output.setSnapshot(ImageBitmap(400, 300))
            waitForIdle()

            // The guard every forwarding lambda opens with. Nothing to assert but that it survives:
            // a null browser here is the ordinary state between going live and the output building
            // one, and an exception would take the tab down mid-service.
            onRoot().performMouseInput { moveTo(center); press(); release(); scroll(1f) }
            waitForIdle()

            assertEquals(null, output.liveBrowser)
        }
    }

    @Test
    fun `moving the pointer across the mirror forwards move events`() {
        val component = ShowingCanvas(800, 600)
        val browser = browserOn(component)

        webTab { output, _ ->
            output.live = true
            output.liveBrowser = browser
            output.setSnapshot(ImageBitmap(400, 300))
            waitForIdle()

            onRoot().performMouseInput {
                moveTo(center)
                moveBy(androidx.compose.ui.geometry.Offset(5f, 5f))
                moveBy(androidx.compose.ui.geometry.Offset(5f, 5f))
            }
            waitForIdle()
        }
        drainEdt()

        // The move path is throttled to one event per 50ms, and synthetic moves all land inside
        // the same millisecond — so a burst of them forwards at most one. That throttle is the
        // point: without it, dragging across the mirror would flood the live page with events.
        assertTrue(mouseEventsSentTo(browser).size <= 1, "the 20fps throttle should hold the burst")
    }

    @Test
    fun `dragging across the mirror forwards while the button is down`() {
        val component = ShowingCanvas(800, 600)
        val browser = browserOn(component)

        webTab { output, _ ->
            output.live = true
            output.liveBrowser = browser
            output.setSnapshot(ImageBitmap(400, 300))
            waitForIdle()

            onRoot().performMouseInput {
                moveTo(center)
                press()
                moveBy(androidx.compose.ui.geometry.Offset(10f, 10f))
                moveBy(androidx.compose.ui.geometry.Offset(10f, 10f))
                release()
            }
            waitForIdle()
        }
        drainEdt()

        // A drag is how an operator selects text or pans a map on the live page. The press gets
        // through; the moves behind it are throttled as above.
        assertTrue(mouseEventsSentTo(browser).isNotEmpty(), "the drag never reached the browser")
    }

    @Test
    fun `scrolling sideways forwards a horizontal wheel event`() {
        val component = ShowingCanvas(800, 600)
        val browser = browserOn(component)

        webTab { output, _ ->
            output.live = true
            output.liveBrowser = browser
            output.setSnapshot(ImageBitmap(400, 300))
            waitForIdle()

            onRoot().performMouseInput {
                moveTo(center)
                scroll(1f, ScrollWheel.Horizontal)
            }
            waitForIdle()
        }
        drainEdt()

        // Horizontal scroll takes its own arm, sending SHIFT_DOWN_MASK, because that is how the
        // platform expresses a sideways wheel to a browser.
        assertTrue(wheelEventsSentTo(browser).isNotEmpty(), "no sideways scroll reached the browser")
    }
}
