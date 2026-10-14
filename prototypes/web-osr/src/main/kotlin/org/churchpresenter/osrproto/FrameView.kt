package org.churchpresenter.osrproto

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import org.cef.browser.CefBrowserHeadless
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import java.awt.Cursor
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent

/**
 * The wheel rotation to hand JCEF for an AWT [preciseWheelRotation][MouseWheelEvent.getPreciseWheelRotation].
 *
 * JCEF passes the rotation to Chromium as a pixel delta, negated and barely scaled: one notch down
 * arrives as `deltaY=-1` on macOS and `deltaY=-2.5` on Windows (truncated to whole pixels), where
 * Chrome scrolls 40 px per line and 100 px per notch. So this sends pre-negated units of JCEF's
 * factor, and carries what is left in pixels — after JCEF's own truncation — so a touchpad's small
 * steps add up exactly. Linux is passed through until measured.
 */
internal class WheelScaler(os: String = System.getProperty("os.name")) {
    /** Pixels per AWT rotation unit, and JCEF's native multiplier; null where unmeasured. */
    private val scale: Pair<Double, Double>? = when {
        os.startsWith("Mac") -> 40.0 to 1.0
        os.startsWith("Windows") -> 100.0 to 2.5
        else -> null
    }
    private var carryPixels = 0.0

    fun rotationFor(precise: Double): Int {
        val (pixelsPerUnit, nativeFactor) = scale ?: return precise.toInt()
        val wanted = precise * pixelsPerUnit + carryPixels
        val units = (wanted / nativeFactor).toInt()
        // What Chromium will actually scroll: JCEF multiplies by its factor and drops the fraction.
        val sent = (units * nativeFactor).toInt()
        carryPixels = wanted - sent
        return -units
    }
}

/** Where the page's picture sits inside a box of [box]: centred, letterboxed to the frame's aspect. */
internal fun fitRect(box: Size, frameW: Int, frameH: Int): Rect {
    val scale = minOf(box.width / frameW, box.height / frameH)
    val w = frameW * scale
    val h = frameH * scale
    val x = (box.width - w) / 2
    val y = (box.height - h) / 2
    return Rect.makeXYWH(x, y, w, h)
}

/**
 * Draws the latest frame, letterboxed, with the open popup over it, and reports each draw to [stats]
 * under [name]. Every window that shows the page is one of these over the same [store]; a [small]
 * one draws the frame's scaled-down copy when there is one.
 */
@Composable
fun FrameView(store: FrameStore, stats: Stats, name: String, modifier: Modifier, small: Boolean = false) {
    val frame by store.frames.collectAsState()
    val lastSeq = remember { longArrayOf(-1) }
    val paint = remember { Paint() }
    Canvas(modifier) {
        val f = frame ?: return@Canvas
        val start = System.nanoTime()
        val picture = if (small) f.preview ?: f.view else f.view
        val dst = fitRect(size, picture.width, picture.height)
        val sampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)
        drawIntoCanvas { c ->
            val canvas = c.skiaCanvas
            canvas.drawImageRect(
                picture, Rect.makeWH(picture.width.toFloat(), picture.height.toFloat()), dst, sampling, paint, true,
            )
            val popup = f.popup
            val rect = f.popupRect
            if (popup != null && rect != null) {
                // The popup rect is in DIPs; the picture is in pixels.
                val dipToDst = dst.width / f.dipWidth
                val target = Rect.makeXYWH(
                    dst.left + rect.x * dipToDst, dst.top + rect.y * dipToDst,
                    rect.width * dipToDst, rect.height * dipToDst,
                )
                canvas.drawImageRect(
                    popup, Rect.makeWH(popup.width.toFloat(), popup.height.toFloat()), target, sampling, paint, true,
                )
            }
        }
        val gap = if (lastSeq[0] < 0) 0 else (f.seq - lastSeq[0] - 1).coerceAtLeast(0)
        lastSeq[0] = f.seq
        val now = System.nanoTime()
        stats.draw(name, drawNs = now - start, latencyNs = now - f.paintedAt, gap = gap)
    }
}

/**
 * Sends this element's mouse and wheel input to [browser]: each AWT event is re-made at the matching
 * point of the page (in DIPs), keeping its buttons, modifiers and click count. The pointer takes the
 * shape the page asks for ([cursorType] is an AWT cursor type; JCEF maps Chromium's onto them).
 */
fun Modifier.forwardPointer(browser: CefBrowserHeadless, cursorType: Int, onPress: () -> Unit): Modifier =
    pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(cursorType.coerceIn(0, Cursor.MOVE_CURSOR))))
        .pointerInput(browser) {
            val wheel = WheelScaler()
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val awt = event.nativeEvent as? MouseEvent ?: continue
                    val pos = event.changes.firstOrNull()?.position ?: continue
                    val box = Size(size.width.toFloat(), size.height.toFloat())
                    val dst = fitRect(box, browser.viewWidth, browser.viewHeight)
                    val x = ((pos.x - dst.left) / dst.width * browser.viewWidth).toInt()
                        .coerceIn(0, browser.viewWidth - 1)
                    val y = ((pos.y - dst.top) / dst.height * browser.viewHeight).toInt()
                        .coerceIn(0, browser.viewHeight - 1)
                    if (event.type == PointerEventType.Press) onPress()
                    val src = browser.eventSource
                    if (awt is MouseWheelEvent) {
                        if (System.getProperty("osr.logKeys") != null) {
                            println("wheel rot=${awt.wheelRotation} precise=${awt.preciseWheelRotation} amount=${awt.scrollAmount} type=${awt.scrollType} mods=${awt.modifiersEx}")
                        }
                        val rotation = wheel.rotationFor(awt.preciseWheelRotation)
                        if (rotation != 0) {
                            browser.wheel(
                                MouseWheelEvent(
                                    src, awt.id, awt.`when`, awt.modifiersEx, x, y, awt.xOnScreen, awt.yOnScreen,
                                    awt.clickCount, awt.isPopupTrigger, awt.scrollType, awt.scrollAmount,
                                    rotation, rotation.toDouble(),
                                ),
                            )
                        }
                    } else {
                        browser.mouse(
                            MouseEvent(
                                src, awt.id, awt.`when`, awt.modifiersEx, x, y, awt.xOnScreen, awt.yOnScreen,
                                awt.clickCount, awt.isPopupTrigger, awt.button,
                            ),
                        )
                    }
                    event.changes.forEach { it.consume() }
                }
            }
        }
