package org.churchpresenter.osrproto

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import org.jetbrains.skia.Image
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
 * arrives as `deltaY=-1` on macOS, `deltaY=-2.5` on Windows (truncated to whole pixels) and
 * `deltaY=-3` on Linux (the event's scroll amount, 3), where Chrome scrolls 40 px per line, 100 px
 * per notch and 53 px per notch. So this sends pre-negated units of JCEF's factor, and carries what
 * is left in pixels — after JCEF's own truncation — so a touchpad's small steps add up exactly. On
 * Linux the event is sent with a scroll amount of [scrollAmount] 1, so JCEF's factor is exactly one.
 */
internal class WheelScaler(os: String = System.getProperty("os.name")) {
    /** Pixels per AWT rotation unit, and JCEF's native multiplier. */
    private val scale: Pair<Double, Double> = when {
        os.startsWith("Mac") -> 40.0 to 1.0
        os.startsWith("Windows") -> 100.0 to 2.5
        else -> 53.0 to 1.0
    }

    /** The scroll amount to send instead of the event's own, where JCEF multiplies by it; null keeps it. */
    val scrollAmount: Int? = if (os.startsWith("Mac") || os.startsWith("Windows")) null else 1
    private var carryPixels = 0.0

    fun rotationFor(precise: Double): Int {
        val (pixelsPerUnit, nativeFactor) = scale
        val wanted = precise * pixelsPerUnit + carryPixels
        val units = (wanted / nativeFactor).toInt()
        // What Chromium will actually scroll: JCEF multiplies by its factor and drops the fraction.
        val sent = (units * nativeFactor).toInt()
        carryPixels = wanted - sent
        return -units
    }
}

/**
 * A small window drawn smaller than this share of even the smallest level samples it from mipmaps:
 * past 2:1 a bilinear sample skips source pixels. Only a very small preview gets there.
 */
private const val MIPMAP_BELOW = 0.5f

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
 * What one window draws from a [Frame]: the full view, or for a small window one of the frame's
 * [levels][Frame.levels] chosen by its size at draw time, and the popup. [pictures] is what it picks
 * from. Two are equivalent when they would draw the same pixels, so a window is not redrawn when only
 * the other window's pictures changed.
 */
private class Shown(val frame: Frame, val pictures: List<Image>, val pictureSeq: Long) {
    companion object {
        fun of(frame: Frame, small: Boolean): Shown =
            if (small && frame.levels.isNotEmpty()) Shown(frame, listOf(frame.view) + frame.levels, frame.previewSeq)
            else Shown(frame, listOf(frame.view), frame.viewSeq)

        val Policy = object : SnapshotMutationPolicy<Shown?> {
            override fun equivalent(a: Shown?, b: Shown?): Boolean =
                a?.pictures?.last() === b?.pictures?.last() && a?.frame?.popup === b?.frame?.popup &&
                    a?.frame?.popupRect == b?.frame?.popupRect
        }
    }
}

/** The smallest of [pictures] (largest first) at least [width] wide; the largest when none is. */
private fun pick(pictures: List<Image>, width: Float): Image =
    pictures.lastOrNull { it.width >= width } ?: pictures.first()

/**
 * Draws the latest frame, letterboxed, with the open popup over it, and reports each draw to [stats]
 * under [name]. Every window that shows the page is one of these over the same [store]; a [small]
 * one draws the frame's scaled-down copy when there is one. A window redraws only when what it shows
 * changed, and its skipped count is of its own pictures.
 */
@Composable
fun FrameView(store: FrameStore, stats: Stats, name: String, modifier: Modifier, small: Boolean = false) {
    val frames = store.frames.collectAsState()
    val shown by remember(small) { derivedStateOf(Shown.Policy) { frames.value?.let { Shown.of(it, small) } } }
    val lastSeq = remember { longArrayOf(-1) }
    val paint = remember { Paint() }
    Canvas(modifier) {
        val current = shown ?: return@Canvas
        val f = current.frame
        val start = System.nanoTime()
        val dst = fitRect(size, f.view.width, f.view.height)
        val picture = pick(current.pictures, dst.width)
        // Mipmaps are rebuilt on the CPU for every new picture, so only a small window that is below
        // even the smallest level pays for them. The Output is meant to be drawn at its own size.
        val shrinking = small && !plainPreview && dst.width < picture.width * MIPMAP_BELOW
        val sampling = FilterMipmap(FilterMode.LINEAR, if (shrinking) MipmapMode.LINEAR else MipmapMode.NONE)
        drawIntoCanvas { c ->
            val canvas = c.skiaCanvas
            // Not strict: Skia turns mipmapping off for a strict source rect, and the source is the
            // whole picture anyway, so there is nothing outside it to bleed in.
            canvas.drawImageRect(
                picture, Rect.makeWH(picture.width.toFloat(), picture.height.toFloat()), dst, sampling, paint, false,
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
                    popup, Rect.makeWH(popup.width.toFloat(), popup.height.toFloat()), target, sampling, paint, false,
                )
            }
        }
        val gap = if (lastSeq[0] < 0) 0 else (current.pictureSeq - lastSeq[0] - 1).coerceAtLeast(0)
        lastSeq[0] = current.pictureSeq
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
                                    awt.clickCount, awt.isPopupTrigger, awt.scrollType, wheel.scrollAmount ?: awt.scrollAmount,
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
