package org.churchpresenter.osrproto

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.cef.browser.HeadlessSink
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.awt.Rectangle
import java.nio.ByteBuffer
import java.util.ArrayDeque

/** One published picture of the page: the view, and the open popup (a `<select>` list) over it. */
class Frame(
    val seq: Long,
    val view: Image,
    val popup: Image?,
    val popupRect: Rectangle?,
    /** `System.nanoTime()` when Chromium handed the paint over, for paint→draw latency. */
    val paintedAt: Long,
    /** Physical pixels per DIP: [view] is in pixels, [popupRect] in DIPs. */
    val scale: Float,
)

/**
 * Turns Chromium's paints into [frames].
 *
 * Each paint's dirty rects are copied into a mirror array (the CEF buffer is only valid inside
 * the callback, so this must be synchronous), then one immutable
 * [Image] snapshot is taken and published. Every window draws that same snapshot, so they cannot
 * disagree about what is on the page.
 *
 * Images are closed a few frames after they stop being current rather than left to the GC, which
 * at 60 fps of 8–33 MB images lets native memory run far ahead of the heap.
 */
class FrameStore(private val stats: Stats, private val scale: Float) : HeadlessSink {
    private val _frames = MutableStateFlow<Frame?>(null)
    val frames: StateFlow<Frame?> = _frames

    private val _cursor = MutableStateFlow(0)
    val cursor: StateFlow<Int> = _cursor

    private var view: Mirror? = null
    private var popup: Mirror? = null
    private var popupVisible = false
    private var popupRect: Rectangle? = null
    private var popupImage: Image? = null
    private var seq = 0L
    private val retired = ArrayDeque<Image>()

    @Synchronized
    override fun onPaint(popup: Boolean, dirtyRects: Array<Rectangle>, buffer: ByteBuffer, width: Int, height: Int) {
        val start = System.nanoTime()
        val mirror = mirrorFor(popup, width, height)
        val full = mirror.copy(dirtyRects, buffer)
        val copied = System.nanoTime()
        val image = mirror.snapshot()
        val snapped = System.nanoTime()
        stats.paint(popup, copiedNs = copied - start, snapshotNs = snapped - copied, full = full, rects = dirtyRects)

        if (popup) {
            popupImage?.let(::retire)
            popupImage = image
            republish(start)
        } else {
            publish(image, start)
        }
    }

    @Synchronized
    override fun onPopupShow(show: Boolean) {
        popupVisible = show
        if (!show) {
            popupImage?.let(::retire)
            popupImage = null
            popupRect = null
        }
        republish(System.nanoTime())
    }

    @Synchronized
    override fun onPopupSize(rect: Rectangle) {
        popupRect = Rectangle(rect)
    }

    override fun onCursorChange(cursorType: Int) {
        _cursor.value = cursorType
    }

    private fun mirrorFor(popup: Boolean, width: Int, height: Int): Mirror {
        val current = if (popup) this.popup else view
        if (current != null && current.width == width && current.height == height) return current
        return Mirror(width, height).also { if (popup) this.popup = it else view = it }
    }

    private fun republish(paintedAt: Long) {
        val current = _frames.value ?: return
        _frames.value = Frame(
            seq = ++seq,
            view = current.view,
            popup = popupImage.takeIf { popupVisible },
            popupRect = popupRect.takeIf { popupVisible },
            paintedAt = paintedAt,
            scale = scale,
        )
    }

    private fun publish(image: Image, paintedAt: Long) {
        _frames.value?.view?.let(::retire)
        _frames.value = Frame(
            seq = ++seq,
            view = image,
            popup = popupImage.takeIf { popupVisible },
            popupRect = popupRect.takeIf { popupVisible },
            paintedAt = paintedAt,
            scale = scale,
        )
    }

    private fun retire(image: Image) {
        retired.addLast(image)
        while (retired.size > RETIRED_KEPT) retired.removeFirst().close()
    }

    /** A BGRA copy of one paint target (the view or the popup), kept up to date by dirty rects. */
    private class Mirror(val width: Int, val height: Int) {
        private val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        private val rowBytes = width * 4
        private val pixels = ByteArray(rowBytes * height)
        private var filled = false

        /** Copies [rects] of [source]; the first paint copies everything. Returns whether it was a full copy. */
        fun copy(rects: Array<Rectangle>, source: ByteBuffer): Boolean {
            val full = !filled || rects.any { it.width >= width && it.height >= height }
            if (full) {
                source.get(0, pixels, 0, pixels.size)
                filled = true
            } else {
                for (r in rects) {
                    val x = r.x.coerceIn(0, width)
                    val w = (r.x + r.width).coerceAtMost(width) - x
                    if (w <= 0) continue
                    val y0 = r.y.coerceIn(0, height)
                    val y1 = (r.y + r.height).coerceAtMost(height)
                    for (y in y0 until y1) {
                        val at = y * rowBytes + x * 4
                        source.get(at, pixels, at, w * 4)
                    }
                }
            }
            return full
        }

        /** An immutable image of the mirror as it is now (one copy, shared by every window). */
        fun snapshot(): Image = Image.makeRaster(info, pixels, rowBytes)
    }

    private companion object {
        /** Snapshots kept alive after they stop being current: a window may still be drawing one. */
        const val RETIRED_KEPT = 6
    }
}
