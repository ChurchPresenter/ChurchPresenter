package org.churchpresenter.web.tabs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.IntSize
import org.cef.browser.CefBrowser
import java.awt.Component
import java.awt.event.InputEvent
import java.awt.event.KeyEvent as AwtKeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.lang.reflect.Method
import javax.swing.SwingUtilities

/** The shortest gap between two pointer moves forwarded to the browser: about 20 a second. */
private const val MOUSE_MOVE_THROTTLE_MS = 50L

/** One notch of wheel rotation per this many pixels of Compose scroll delta. */
private const val WHEEL_ROTATION_PER_DELTA = 15

/** The most notches one scroll event may turn the wheel, either way. */
private const val MAX_WHEEL_ROTATION = 100

/**
 * The live browser as the mirrored preview drives it: the AWT component the events are addressed to,
 * and the three ways in.
 *
 * Everything that needs a real JCEF render surface stays behind this — whether the component is on
 * screen, and the reflective `CefBrowser_N` send calls on the AWT thread — so what to send stays
 * testable without one.
 */
internal interface BrowserInput {
    /** The component events are addressed to: their source, and the space their coordinates are in. */
    val component: Component

    /** The component's size while it is on screen with a real size, or null when it cannot take input. */
    fun showingSize(): IntSize?

    fun sendMouse(events: List<MouseEvent>)
    fun sendWheel(events: List<MouseWheelEvent>)
    fun sendKey(event: AwtKeyEvent)
}

/**
 * [BrowserInput] over a live browser, through the `sendMouseEvent`/`sendMouseWheelEvent`/`sendKeyEvent`
 * methods `CefBrowser_N` declares but does not expose (found by reflection, so a missing one sends
 * nothing). [browser] is the `CefBrowser`, and [component] its UI component.
 */
internal class CefBrowserInput(private val browser: Any, override val component: Component) : BrowserInput {
    constructor(browser: CefBrowser) : this(browser, browser.getUIComponent())

    private val mouse = findMethod(browser, "sendMouseEvent", MouseEvent::class.java)
    private val wheel = findMethod(browser, "sendMouseWheelEvent", MouseWheelEvent::class.java)
    private val key = findMethod(browser, "sendKeyEvent", AwtKeyEvent::class.java)

    override fun showingSize(): IntSize? =
        component.takeIf { it.isShowing && it.width > 0 && it.height > 0 }?.let { IntSize(it.width, it.height) }

    override fun sendMouse(events: List<MouseEvent>) = send(mouse, events)
    override fun sendWheel(events: List<MouseWheelEvent>) = send(wheel, events)
    override fun sendKey(event: AwtKeyEvent) = send(key, listOf(event))

    /** Sends [events] on the AWT thread, unless the component has left the screen by then. */
    private fun send(method: Method?, events: List<Any>) {
        if (method == null) return
        SwingUtilities.invokeLater {
            try {
                if (!component.isShowing) return@invokeLater
                events.forEach { method.invoke(browser, it) }
            } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                // A browser torn down between the check and the call throws from inside JCEF; the
                // input is simply dropped, as it would be had it arrived a moment later.
            }
        }
    }
}

/**
 * Where [pos] on the preview image lands in the browser's own coordinates, scaled from [imageSize]
 * to [browserSize] and kept inside it — or null while either has no size yet.
 */
internal fun browserPoint(pos: Offset, imageSize: IntSize, browserSize: IntSize?): Pair<Int, Int>? {
    if (browserSize == null || imageSize.width <= 0 || imageSize.height <= 0) return null
    val x = (pos.x * browserSize.width / imageSize.width).toInt().coerceIn(0, browserSize.width - 1)
    val y = (pos.y * browserSize.height / imageSize.height).toInt().coerceIn(0, browserSize.height - 1)
    return x to y
}

/**
 * Lets a pointer move through at most once per [intervalMs]: Chromium does not need every move, and
 * each is a reflective call on the AWT thread.
 */
internal class MoveThrottle(private val intervalMs: Long = MOUSE_MOVE_THROTTLE_MS) {
    private var last = 0L

    /** Whether a move at [now] goes through; one that does starts the next interval. */
    fun admit(now: Long): Boolean {
        if (now - last < intervalMs) return false
        last = now
        return true
    }
}

/**
 * The AWT mouse events a pointer event of [type] at ([x], [y]) becomes: a press enters and moves
 * first, so the page has hover state to click on; a release also clicks. Anything else sends nothing.
 */
internal fun mouseEventsFor(type: PointerEventType, comp: Component, x: Int, y: Int, now: Long): List<MouseEvent> =
    when (type) {
        PointerEventType.Press -> listOf(
            MouseEvent(comp, MouseEvent.MOUSE_ENTERED, now, 0, x, y, 0, false),
            MouseEvent(comp, MouseEvent.MOUSE_MOVED, now, 0, x, y, 0, false),
            MouseEvent(
                comp, MouseEvent.MOUSE_PRESSED, now, InputEvent.BUTTON1_DOWN_MASK,
                x, y, 1, false, MouseEvent.BUTTON1,
            ),
        )
        PointerEventType.Release -> listOf(
            MouseEvent(comp, MouseEvent.MOUSE_RELEASED, now, 0, x, y, 1, false, MouseEvent.BUTTON1),
            MouseEvent(comp, MouseEvent.MOUSE_CLICKED, now, 0, x, y, 1, false, MouseEvent.BUTTON1),
        )
        PointerEventType.Move -> listOf(MouseEvent(comp, MouseEvent.MOUSE_MOVED, now, 0, x, y, 0, false))
        else -> emptyList()
    }

/** Wheel notches for a Compose scroll [delta]: scrolling down turns the wheel up, as AWT counts it. */
private fun wheelRotation(delta: Float): Int =
    -(delta * WHEEL_ROTATION_PER_DELTA).toInt().coerceIn(-MAX_WHEEL_ROTATION, MAX_WHEEL_ROTATION)

/**
 * The wheel events a scroll by [scroll] at ([x], [y]) becomes: one vertical, and one horizontal sent
 * with Shift held, which is how AWT spells a sideways wheel. An axis too small to turn a notch is left out.
 */
internal fun wheelEventsFor(comp: Component, x: Int, y: Int, scroll: Offset, now: Long): List<MouseWheelEvent> {
    fun wheel(modifiers: Int, rotation: Int) = MouseWheelEvent(
        comp, MouseWheelEvent.MOUSE_WHEEL, now, modifiers, x, y,
        0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, rotation,
    )
    val vertical = wheelRotation(scroll.y)
    val horizontal = wheelRotation(scroll.x)
    return listOfNotNull(
        wheel(0, vertical).takeIf { vertical != 0 },
        wheel(InputEvent.SHIFT_DOWN_MASK, horizontal).takeIf { horizontal != 0 },
    )
}

/** The AWT key event a Compose key [type] with [nativeCode] becomes, or null for one that is neither down nor up. */
internal fun keyEventFor(type: KeyEventType, comp: Component, nativeCode: Int, now: Long): AwtKeyEvent? {
    val awtType = when (type) {
        KeyEventType.KeyDown -> AwtKeyEvent.KEY_PRESSED
        KeyEventType.KeyUp -> AwtKeyEvent.KEY_RELEASED
        else -> return null
    }
    return AwtKeyEvent(comp, awtType, now, 0, nativeCode, nativeCode.toChar())
}

/**
 * Forwards a pointer event of [type] at [pos] over an image of [imageSize]: nothing while the browser
 * cannot take input, a move only when [throttle] admits it, and nothing for a type with no mouse events.
 */
internal fun BrowserInput.forwardPointer(
    type: PointerEventType,
    pos: Offset?,
    imageSize: IntSize,
    throttle: MoveThrottle,
    now: Long,
) {
    val (x, y) = pos?.let { browserPoint(it, imageSize, showingSize()) } ?: return
    if (type == PointerEventType.Move && !throttle.admit(now)) return
    val events = mouseEventsFor(type, component, x, y, now)
    if (events.isNotEmpty()) sendMouse(events)
}

/** Forwards a scroll by [delta] at [pos] over an image of [imageSize], once it turns the wheel at all. */
internal fun BrowserInput.forwardScroll(pos: Offset?, delta: Offset?, imageSize: IntSize, now: Long) {
    if (delta == null) return
    val (x, y) = pos?.let { browserPoint(it, imageSize, showingSize()) } ?: return
    val events = wheelEventsFor(component, x, y, delta, now)
    if (events.isNotEmpty()) sendWheel(events)
}
