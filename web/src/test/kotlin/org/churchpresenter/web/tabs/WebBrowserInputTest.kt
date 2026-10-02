@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.web.tabs

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.awt.Component
import java.awt.event.InputEvent
import java.awt.event.KeyEvent as AwtKeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.SwingUtilities
import io.mockk.every
import io.mockk.mockk
import org.cef.browser.CefBrowser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A [BrowserInput] that records what it is sent, on a component of [size] (null: not on screen). */
private class FakeBrowserInput(var size: IntSize? = IntSize(200, 100)) : BrowserInput {
    override val component: Component = object : Component() {}
    val mouse = mutableListOf<MouseEvent>()
    val wheel = mutableListOf<MouseWheelEvent>()
    val keys = mutableListOf<AwtKeyEvent>()

    override fun showingSize(): IntSize? = size
    override fun sendMouse(events: List<MouseEvent>) {
        mouse += events
    }
    override fun sendWheel(events: List<MouseWheelEvent>) {
        wheel += events
    }
    override fun sendKey(event: AwtKeyEvent) {
        keys += event
    }
}

/**
 * What the mirrored preview sends the live browser for what the operator does over its image: where a
 * click lands, which mouse events a press or release becomes, how moves are throttled, how the wheel
 * turns, and which keys go through. The reflective send calls themselves need a real JCEF browser and
 * stay behind [CefBrowserInput].
 */
class WebBrowserInputTest {

    private val image = IntSize(100, 50)

    // ── Where a point lands ─────────────────────────────────────────────────────────────────────

    @Test
    fun `a point on the image is scaled to the browser's own size`() {
        assertEquals(50 to 30, browserPoint(Offset(25f, 15f), image, IntSize(200, 100)))
    }

    @Test
    fun `a point past the edge is kept inside the browser`() {
        assertEquals(199 to 0, browserPoint(Offset(500f, -20f), image, IntSize(200, 100)))
    }

    @Test
    fun `nothing lands while the browser is off screen or the image has no size`() {
        assertNull(browserPoint(Offset(1f, 1f), image, null))
        assertNull(browserPoint(Offset(1f, 1f), IntSize(0, 50), IntSize(200, 100)))
        assertNull(browserPoint(Offset(1f, 1f), IntSize(100, 0), IntSize(200, 100)))
    }

    // ── Moves ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `moves closer together than the interval are dropped`() {
        val throttle = MoveThrottle(intervalMs = 50)
        assertTrue(throttle.admit(1_000))
        assertEquals(false, throttle.admit(1_049))
        assertTrue(throttle.admit(1_050), "a full interval later the next one goes through")
    }

    // ── Which mouse events ──────────────────────────────────────────────────────────────────────

    private val comp = object : Component() {}

    @Test
    fun `a press enters and moves before it presses the first button`() {
        val events = mouseEventsFor(PointerEventType.Press, comp, 4, 5, 7)
        assertEquals(
            listOf(MouseEvent.MOUSE_ENTERED, MouseEvent.MOUSE_MOVED, MouseEvent.MOUSE_PRESSED),
            events.map { it.id },
        )
        assertEquals(MouseEvent.BUTTON1, events.last().button)
        assertEquals(InputEvent.BUTTON1_DOWN_MASK, events.last().modifiersEx and InputEvent.BUTTON1_DOWN_MASK)
        assertTrue(events.all { it.x == 4 && it.y == 5 && it.`when` == 7L && it.source === comp })
    }

    @Test
    fun `a release releases and clicks`() {
        val events = mouseEventsFor(PointerEventType.Release, comp, 1, 2, 0)
        assertEquals(listOf(MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED), events.map { it.id })
        assertTrue(events.all { it.button == MouseEvent.BUTTON1 && it.clickCount == 1 })
    }

    @Test
    fun `a move is one move, and any other pointer event is nothing`() {
        assertEquals(listOf(MouseEvent.MOUSE_MOVED), mouseEventsFor(PointerEventType.Move, comp, 0, 0, 0).map { it.id })
        assertEquals(emptyList(), mouseEventsFor(PointerEventType.Enter, comp, 0, 0, 0))
    }

    // ── The wheel ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `scrolling down turns the wheel the other way, with no modifiers`() {
        val events = wheelEventsFor(comp, 3, 4, Offset(0f, 1f), 0)
        assertEquals(1, events.size)
        assertEquals(-15, events.single().wheelRotation)
        assertEquals(0, events.single().modifiersEx)
    }

    @Test
    fun `a sideways scroll is sent with Shift held`() {
        val events = wheelEventsFor(comp, 0, 0, Offset(-2f, 0f), 0)
        assertEquals(30, events.single().wheelRotation)
        assertEquals(InputEvent.SHIFT_DOWN_MASK, events.single().modifiersEx)
    }

    @Test
    fun `a diagonal scroll sends both axes, and a huge one is capped`() {
        val events = wheelEventsFor(comp, 0, 0, Offset(100f, 100f), 0)
        assertEquals(listOf(-100, -100), events.map { it.wheelRotation })
    }

    @Test
    fun `a scroll too small to turn a notch sends nothing`() {
        assertEquals(emptyList(), wheelEventsFor(comp, 0, 0, Offset(0.01f, 0.01f), 0))
    }

    // ── Keys ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `down and up become pressed and released, with the native code as both code and char`() {
        val down = keyEventFor(KeyEventType.KeyDown, comp, AwtKeyEvent.VK_A, 9)
        assertEquals(AwtKeyEvent.KEY_PRESSED, down?.id)
        assertEquals(AwtKeyEvent.VK_A, down?.keyCode)
        assertEquals(AwtKeyEvent.VK_A.toChar(), down?.keyChar)
        assertEquals(AwtKeyEvent.KEY_RELEASED, keyEventFor(KeyEventType.KeyUp, comp, AwtKeyEvent.VK_A, 9)?.id)
        assertNull(keyEventFor(KeyEventType.Unknown, comp, AwtKeyEvent.VK_A, 9))
    }

    // ── Forwarding one event ────────────────────────────────────────────────────────────────────

    @Test
    fun `a pointer event is forwarded at the scaled point`() {
        val input = FakeBrowserInput()
        input.forwardPointer(PointerEventType.Release, Offset(10f, 10f), image, MoveThrottle(), 0)
        assertEquals(listOf(20 to 20, 20 to 20), input.mouse.map { it.x to it.y })
    }

    @Test
    fun `nothing is forwarded with no position, off screen, or for a type with no events`() {
        val input = FakeBrowserInput()
        input.forwardPointer(PointerEventType.Press, null, image, MoveThrottle(), 0)
        input.forwardPointer(PointerEventType.Exit, Offset(1f, 1f), image, MoveThrottle(), 0)
        input.size = null
        input.forwardPointer(PointerEventType.Press, Offset(1f, 1f), image, MoveThrottle(), 0)
        assertEquals(emptyList(), input.mouse)
    }

    @Test
    fun `a move the throttle refuses is not forwarded`() {
        val input = FakeBrowserInput()
        val throttle = MoveThrottle(intervalMs = 50)
        input.forwardPointer(PointerEventType.Move, Offset(1f, 1f), image, throttle, 100)
        input.forwardPointer(PointerEventType.Move, Offset(2f, 2f), image, throttle, 120)
        assertEquals(1, input.mouse.size)
    }

    @Test
    fun `a scroll is forwarded, unless it has no delta, no position, or turns nothing`() {
        val input = FakeBrowserInput()
        input.forwardScroll(Offset(1f, 1f), null, image, 0)
        input.forwardScroll(null, Offset(0f, 1f), image, 0)
        input.forwardScroll(Offset(1f, 1f), Offset.Zero, image, 0)
        assertEquals(emptyList(), input.wheel)

        input.forwardScroll(Offset(1f, 1f), Offset(0f, 1f), image, 0)
        assertEquals(1, input.wheel.size)
    }

    // ── The reflective sender ───────────────────────────────────────────────────────────────────

    /** Stands in for `CefBrowser_N`: the three send methods, private as they are there. */
    @Suppress("unused")
    private class FakeCefBrowser {
        val received = mutableListOf<Any>()
        private fun sendMouseEvent(e: MouseEvent) {
            received += e
        }
        private fun sendMouseWheelEvent(e: MouseWheelEvent) {
            received += e
        }
        private fun sendKeyEvent(e: AwtKeyEvent) {
            received += e
        }
    }

    /** A component that reports itself on screen while [showing] says so. */
    private class ScreenComponent(var showing: Boolean) : Component() {
        override fun isShowing() = showing
    }

    /** Lets every `invokeLater` queued so far run, so what it sent can be read. */
    private fun drainEdt() = SwingUtilities.invokeAndWait {}

    @Test
    fun `the reflective sender reaches each private send method on the AWT thread`() {
        val browser = FakeCefBrowser()
        val screen = ScreenComponent(showing = true).apply { setSize(40, 30) }
        val input = CefBrowserInput(browser, screen)

        assertEquals(IntSize(40, 30), input.showingSize())
        input.sendMouse(mouseEventsFor(PointerEventType.Release, screen, 0, 0, 0))
        input.sendWheel(wheelEventsFor(screen, 0, 0, Offset(0f, 1f), 0))
        input.sendKey(keyEventFor(KeyEventType.KeyDown, screen, AwtKeyEvent.VK_A, 0)!!)
        drainEdt()

        assertEquals(4, browser.received.size, "two mouse events, one wheel, one key")
    }

    @Test
    fun `over a real browser handle the sender addresses its UI component, and needs both dimensions`() {
        val screen = ScreenComponent(showing = true).apply { setSize(40, 0) }
        val browser = mockk<CefBrowser> { every { uiComponent } returns screen }

        val input = CefBrowserInput(browser)

        assertSame(screen, input.component)
        assertNull(input.showingSize(), "no height yet")
    }

    @Test
    fun `the reflective sender drops input once the browser has left the screen`() {
        val browser = FakeCefBrowser()
        val screen = ScreenComponent(showing = true).apply { setSize(40, 30) }
        val input = CefBrowserInput(browser, screen)

        input.sendMouse(mouseEventsFor(PointerEventType.Move, screen, 0, 0, 0))
        screen.showing = false
        drainEdt()

        assertEquals(emptyList(), browser.received, "it went off screen before the AWT thread got to it")
        assertNull(input.showingSize())
        assertNull(CefBrowserInput(browser, ScreenComponent(showing = true)).showingSize(), "nor with no size")
    }

    @Test
    fun `a browser without the send methods, or one that throws, sends nothing and does not fail`() {
        val screen = ScreenComponent(showing = true).apply { setSize(40, 30) }
        CefBrowserInput(Any(), screen).sendMouse(mouseEventsFor(PointerEventType.Move, screen, 0, 0, 0))

        val throwing = object {
            @Suppress("unused", "UnusedParameter")
            private fun sendKeyEvent(e: AwtKeyEvent): Unit = error("torn down")
        }
        CefBrowserInput(throwing, screen).sendKey(keyEventFor(KeyEventType.KeyUp, screen, AwtKeyEvent.VK_A, 0)!!)
        drainEdt()
    }

    // ── Through the modifier ────────────────────────────────────────────────────────────────────

    @Test
    fun `the modifier forwards a click, a scroll and a key from real input`() = runComposeUiTest {
        val input = FakeBrowserInput()
        val focus = FocusRequester()
        setContent {
            Box(
                Modifier.size(100.dp, 50.dp)
                    .testTag(TAG)
                    .forwardBrowserInput(input, mutableStateOf(image))
                    .focusRequester(focus)
                    .focusable(),
            )
        }
        runOnIdle { focus.requestFocus() }

        onNodeWithTag(TAG).performMouseInput {
            moveTo(center)
            press()
            release()
            scroll(1f)
        }
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.A) }
        waitForIdle()

        assertTrue(MouseEvent.MOUSE_PRESSED in input.mouse.map { it.id })
        assertTrue(MouseEvent.MOUSE_CLICKED in input.mouse.map { it.id })
        assertEquals(1, input.wheel.size)
        assertEquals(listOf(AwtKeyEvent.KEY_PRESSED, AwtKeyEvent.KEY_RELEASED), input.keys.map { it.id })
    }

    @Test
    fun `with the browser off screen, or no browser at all, keys are left to the tab`() = runComposeUiTest {
        val input = FakeBrowserInput(size = null)
        val focus = FocusRequester()
        setContent {
            Box(
                Modifier.size(100.dp, 50.dp)
                    .testTag(TAG)
                    .forwardBrowserInput(input, mutableStateOf(image))
                    .forwardBrowserInput(null, mutableStateOf(image))
                    .focusRequester(focus)
                    .focusable(),
            )
        }
        runOnIdle { focus.requestFocus() }

        onNodeWithTag(TAG).performKeyInput { pressKey(Key.A) }
        onNodeWithTag(TAG).performMouseInput {
            moveTo(center)
            press()
            release()
        }
        waitForIdle()

        assertEquals(emptyList(), input.keys)
        assertEquals(emptyList(), input.mouse)
    }

    private companion object {
        const val TAG = "mirror"
    }
}
