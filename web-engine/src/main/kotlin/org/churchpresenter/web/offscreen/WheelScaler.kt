package org.churchpresenter.web.offscreen

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import org.cef.browser.CefBrowserHeadless
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent

/**
 * The wheel rotation to hand JCEF for an AWT [preciseWheelRotation][MouseWheelEvent.getPreciseWheelRotation].
 *
 * JCEF passes the rotation to Chromium as a pixel delta, negated and barely scaled: one notch down
 * arrives as `deltaY=-1` on macOS, `deltaY=-2.5` on Windows (truncated to whole pixels) and
 * `deltaY=-3` on Linux (the event's scroll amount, 3), where Chrome scrolls 40 px per line, 100 px
 * per notch and 53 px per notch. So this sends pre-negated units of JCEF's factor, and carries what
 * is left in pixels (after JCEF's own truncation) so a touchpad's small steps add up exactly. On
 * Linux the event is sent with a [scrollAmount] of 1, so JCEF's factor is exactly one.
 */
internal class WheelScaler(os: String = System.getProperty("os.name")) {
    private val pixelsPerUnit: Double
    private val nativeFactor: Double

    init {
        val (pixels, factor) = when {
            os.startsWith("Mac") -> MAC_PIXELS to 1.0
            os.startsWith("Windows") -> WINDOWS_PIXELS to WINDOWS_FACTOR
            else -> LINUX_PIXELS to 1.0
        }
        pixelsPerUnit = pixels
        nativeFactor = factor
    }

    /** The scroll amount to send instead of the event's own, where JCEF multiplies by it; null keeps it. */
    val scrollAmount: Int? = if (os.startsWith("Mac") || os.startsWith("Windows")) null else 1
    private var carryPixels = 0.0

    fun rotationFor(precise: Double): Int {
        val wanted = precise * pixelsPerUnit + carryPixels
        val units = (wanted / nativeFactor).toInt()
        // What Chromium will actually scroll: JCEF multiplies by its factor and drops the fraction.
        val sent = (units * nativeFactor).toInt()
        carryPixels = wanted - sent
        return -units
    }

    private companion object {
        const val MAC_PIXELS = 40.0
        const val WINDOWS_PIXELS = 100.0
        const val WINDOWS_FACTOR = 2.5
        const val LINUX_PIXELS = 53.0
    }
}

/** [pos] in a box of [box] as the point of a [viewWidth]×[viewHeight]-DIP page drawn letterboxed in it. */
internal fun pagePoint(pos: Offset, box: Size, viewWidth: Int, viewHeight: Int): Point {
    val dst = fitRect(box, viewWidth, viewHeight)
    val x = ((pos.x - dst.left) / dst.width * viewWidth).toInt().coerceIn(0, viewWidth - 1)
    val y = ((pos.y - dst.top) / dst.height * viewHeight).toInt().coerceIn(0, viewHeight - 1)
    return Point(x, y)
}

/**
 * Sends this element's mouse and wheel input to [browser]: each AWT event is re-made at the matching
 * point of the page (in DIPs), keeping its buttons, modifiers and click count. The pointer takes the
 * shape the page asks for ([cursorType] is an AWT cursor type; JCEF maps Chromium's onto them).
 */
internal fun Modifier.forwardPointer(browser: CefBrowserHeadless, cursorType: Int, onPress: () -> Unit): Modifier =
    pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(cursorType.coerceIn(0, Cursor.MOVE_CURSOR))))
        .pointerInput(browser) {
            val wheel = WheelScaler()
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val awt = event.nativeEvent as? MouseEvent
                    val pos = event.changes.firstOrNull()?.position
                    if (event.type == PointerEventType.Press) onPress()
                    if (awt != null && pos != null) {
                        val box = Size(size.width.toFloat(), size.height.toFloat())
                        val at = pagePoint(pos, box, browser.viewWidth, browser.viewHeight)
                        send(browser, awt, at, wheel)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }

/** [awt] re-made at [at] on the page, sent to [browser]; a wheel turn too small to scroll yet is held in [wheel]. */
internal fun send(browser: CefBrowserHeadless, awt: MouseEvent, at: Point, wheel: WheelScaler) {
    val src = browser.eventSource
    if (awt is MouseWheelEvent) {
        val rotation = wheel.rotationFor(awt.preciseWheelRotation)
        if (rotation == 0) return
        browser.wheel(
            MouseWheelEvent(
                src, awt.id, awt.`when`, awt.modifiersEx, at.x, at.y, awt.xOnScreen, awt.yOnScreen,
                awt.clickCount, awt.isPopupTrigger, awt.scrollType, wheel.scrollAmount ?: awt.scrollAmount,
                rotation, rotation.toDouble(),
            ),
        )
    } else {
        browser.mouse(
            MouseEvent(
                src, awt.id, awt.`when`, awt.modifiersEx, at.x, at.y, awt.xOnScreen, awt.yOnScreen,
                awt.clickCount, awt.isPopupTrigger, awt.button,
            ),
        )
    }
}
