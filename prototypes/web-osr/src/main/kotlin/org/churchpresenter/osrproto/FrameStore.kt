package org.churchpresenter.osrproto

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.cef.browser.HeadlessSink
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.impl.BufferUtil
import java.awt.Rectangle
import java.nio.ByteBuffer

/** One published picture of the page: the view, a smaller copy for the preview, and the open popup over it. */
class Frame(
    val seq: Long,
    val view: Image,
    /** [view] scaled down for a small window, or null when [view] is already small enough. */
    val preview: Image?,
    val popup: Image?,
    val popupRect: Rectangle?,
    /** `System.nanoTime()` when Chromium handed the paint over, for paint→draw latency. */
    val paintedAt: Long,
    /** The page's width in DIPs: [popupRect] is in DIPs, the images in pixels. */
    val dipWidth: Int,
)

/**
 * Turns Chromium's paints into [frames].
 *
 * The view is written into a small ring of direct buffers that Skia wraps without copying, so a
 * frame costs one copy out of Chromium's buffer (which is only valid inside the callback) and no
 * more. Each buffer is brought up to date with the dirty rects of every paint since it was last
 * written, taken from Chromium's buffer, which always holds the whole current frame. Every window
 * draws the same image, so they cannot disagree about what is on the page.
 *
 * A frame wider than [DOWNSCALE_ABOVE] also gets a [PREVIEW_WIDTH]-wide copy, so a small preview
 * window does not upload a 4K texture to the GPU every frame to draw it at 640 pixels. At 1080p the
 * downscale costs more CPU (about 3 ms a frame on an M-series Mac) than the upload it saves, so a
 * 1080p frame goes to the preview as it is.
 *
 * An image is closed [RETIRED_KEPT] frames after it stops being current, and a ring buffer is not
 * written again until every image over it is closed: the ring is longer than that by two.
 */
class FrameStore(private val stats: Stats, private val dipWidth: Int) : HeadlessSink {
    private val _frames = MutableStateFlow<Frame?>(null)
    val frames: StateFlow<Frame?> = _frames

    private val _cursor = MutableStateFlow(0)
    val cursor: StateFlow<Int> = _cursor

    private var ring: Ring? = null
    private var popupMirror: PopupMirror? = null
    private var popupVisible = false
    private var popupRect: Rectangle? = null
    private var popupImage: Image? = null
    private var previewSurface: Surface? = null
    private var seq = 0L
    private val retired = ArrayDeque<List<Image>>()
    private val oldRings = ArrayDeque<Pair<Long, Ring>>()

    @Synchronized
    override fun onPaint(popup: Boolean, dirtyRects: Array<Rectangle>, buffer: ByteBuffer, width: Int, height: Int) {
        val start = System.nanoTime()
        if (popup) {
            val mirror = popupMirror?.takeIf { it.width == width && it.height == height }
                ?: PopupMirror(width, height).also { popupMirror = it }
            val image = mirror.snapshot(buffer)
            stats.paint(true, copiedNs = System.nanoTime() - start, previewNs = 0, full = true, rects = dirtyRects)
            popupImage?.close()
            popupImage = image
            republish(start)
            return
        }
        val target = ring?.takeIf { it.width == width && it.height == height }
            ?: Ring(width, height).also { fresh ->
                // Images over the old ring's buffers are still being retired; keep it alive until they are.
                ring?.let { old -> oldRings.addLast(seq to old) }
                ring = fresh
            }
        val (image, full) = target.write(dirtyRects, buffer)
        val written = System.nanoTime()
        val preview = downscale(image)
        val scaled = System.nanoTime()
        stats.paint(false, copiedNs = written - start, previewNs = scaled - written, full = full, rects = dirtyRects)
        publish(image, preview, start)
    }

    @Synchronized
    override fun onPopupShow(show: Boolean) {
        popupVisible = show
        if (!show) {
            popupImage?.close()
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

    /** [image] drawn into a [PREVIEW_WIDTH]-wide raster, or null when it is no wider than [DOWNSCALE_ABOVE]. */
    private fun downscale(image: Image): Image? {
        if (image.width <= DOWNSCALE_ABOVE) return null
        val w = PREVIEW_WIDTH
        val h = image.height * w / image.width
        val surface = previewSurface?.takeIf { it.width == w && it.height == h }
            ?: Surface.makeRaster(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL))
                .also { previewSurface?.close(); previewSurface = it }
        surface.canvas.drawImageRect(
            image,
            Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
            Rect.makeWH(w.toFloat(), h.toFloat()),
            FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE),
            null,
            true,
        )
        return surface.makeImageSnapshot()
    }

    private fun republish(paintedAt: Long) {
        val current = _frames.value ?: return
        _frames.value = Frame(
            seq = ++seq,
            view = current.view,
            preview = current.preview,
            popup = popupImage.takeIf { popupVisible },
            popupRect = popupRect.takeIf { popupVisible },
            paintedAt = paintedAt,
            dipWidth = dipWidth,
        )
    }

    private fun publish(image: Image, preview: Image?, paintedAt: Long) {
        _frames.value?.let { old -> retire(listOfNotNull(old.view, old.preview)) }
        _frames.value = Frame(
            seq = ++seq,
            view = image,
            preview = preview,
            popup = popupImage.takeIf { popupVisible },
            popupRect = popupRect.takeIf { popupVisible },
            paintedAt = paintedAt,
            dipWidth = dipWidth,
        )
    }

    private fun retire(images: List<Image>) {
        retired.addLast(images)
        while (retired.size > RETIRED_KEPT) retired.removeFirst().forEach(Image::close)
        while (oldRings.isNotEmpty() && seq - oldRings.first().first > RING_SIZE) oldRings.removeFirst()
    }

    /**
     * [RING_SIZE] direct BGRA buffers the size of the view, written in turn. Each remembers which paint
     * it holds; writing one copies, from Chromium's buffer, the dirty rects of every paint since then.
     */
    private class Ring(val width: Int, val height: Int) {
        private val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        private val rowBytes = width * 4
        private val bytes = rowBytes * height
        private val buffers = Array(RING_SIZE) { ByteBuffer.allocateDirect(bytes) }
        private val held = LongArray(RING_SIZE) { -1 }
        /**
         * The owner skiko asks for when wrapping memory without a copy; it only keeps that memory
         * reachable, and [buffers] already do. One placeholder for the ring's lifetime.
         */
        private val owner = Data.makeEmpty()
        /** The dirty rects of the last [RING_SIZE] paints, newest last; null for a paint that covered everything. */
        private val history = ArrayDeque<Array<Rectangle>?>()
        private var paintNo = 0L
        private var next = 0

        /** Brings the next buffer up to this paint and wraps it in an image. Returns it and whether the copy was full. */
        fun write(rects: Array<Rectangle>, source: ByteBuffer): Pair<Image, Boolean> {
            paintNo++
            val covers = rects.any { it.x <= 0 && it.y <= 0 && it.width >= width && it.height >= height }
            history.addLast(if (covers) null else rects.map(::Rectangle).toTypedArray())
            while (history.size > RING_SIZE) history.removeFirst()

            val i = next
            next = (next + 1) % RING_SIZE
            val buffer = buffers[i]
            val behind = paintNo - held[i]
            val pending = history.toList().takeLast(behind.toInt().coerceAtMost(history.size))
            val full = held[i] < 0 || behind > history.size || pending.any { it == null }
            if (full) {
                buffer.put(0, source, 0, bytes)
            } else {
                pending.forEach { paint -> paint!!.forEach { copyRect(it, source, buffer) } }
            }
            held[i] = paintNo

            val data = Data.makeWithoutCopy(BufferUtil.getPointerFromByteBuffer(buffer), bytes, owner)
            val image = Image.makeRaster(info, data, rowBytes)
            data.close()
            return image to full
        }

        private fun copyRect(r: Rectangle, source: ByteBuffer, target: ByteBuffer) {
            val x = r.x.coerceIn(0, width)
            val w = (r.x + r.width).coerceAtMost(width) - x
            if (w <= 0) return
            val y0 = r.y.coerceIn(0, height)
            val y1 = (r.y + r.height).coerceAtMost(height)
            for (y in y0 until y1) {
                val at = y * rowBytes + x * 4
                target.put(at, source, at, w * 4)
            }
        }
    }

    /** The popup (a `<select>` list) is small and repainted whole: a plain copy each time. */
    private class PopupMirror(val width: Int, val height: Int) {
        private val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        private val pixels = ByteArray(width * height * 4)

        fun snapshot(source: ByteBuffer): Image {
            source.get(0, pixels, 0, pixels.size)
            return Image.makeRaster(info, pixels, width * 4)
        }
    }

    private companion object {
        /** Frames kept alive after they stop being current: a window may still be drawing one. */
        const val RETIRED_KEPT = 2

        /** Ring buffers: the current frame, the retired ones, and one being written. */
        const val RING_SIZE = RETIRED_KEPT + 2

        /** Frames wider than this get a scaled-down copy for the preview. */
        const val DOWNSCALE_ABOVE = 1920

        /** The width of that copy. */
        const val PREVIEW_WIDTH = 1280
    }
}
