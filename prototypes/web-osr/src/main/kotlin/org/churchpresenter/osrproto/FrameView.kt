package org.churchpresenter.osrproto

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
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
 * under [name]. Every window that shows the page is one of these over the same [store].
 */
@Composable
fun FrameView(store: FrameStore, stats: Stats, name: String, modifier: Modifier) {
    val frame by store.frames.collectAsState()
    val lastSeq = remember { longArrayOf(-1) }
    val paint = remember { Paint() }
    Canvas(modifier) {
        val f = frame ?: return@Canvas
        val start = System.nanoTime()
        val dst = fitRect(size, f.view.width, f.view.height)
        val sampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)
        drawIntoCanvas { c ->
            val canvas = c.nativeCanvas
            canvas.drawImageRect(
                f.view, Rect.makeWH(f.view.width.toFloat(), f.view.height.toFloat()), dst, sampling, paint, true,
            )
            val popup = f.popup
            val rect = f.popupRect
            if (popup != null && rect != null) {
                // The popup rect is in DIPs; the view image is in physical pixels.
                val dipToDst = dst.width / (f.view.width / f.scale)
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
                        browser.wheel(
                            MouseWheelEvent(
                                src, awt.id, awt.`when`, awt.modifiersEx, x, y, awt.xOnScreen, awt.yOnScreen,
                                awt.clickCount, awt.isPopupTrigger, awt.scrollType, awt.scrollAmount,
                                awt.wheelRotation, awt.preciseWheelRotation,
                            ),
                        )
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
