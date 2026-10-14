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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

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
    /** Counts of new [view]s and new [preview]s published so far, for each window's skipped-frame count. */
    val viewSeq: Long,
    val previewSeq: Long,
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
 * Chromium waits for each paint callback to return before it goes on, so the callback does only the
 * copy, split across [COPY_THREADS] threads when the whole frame changed.
 *
 * A frame wider than [DOWNSCALE_ABOVE] also gets a [PREVIEW_WIDTH]-wide copy, made on a worker
 * thread, so a small preview window does not upload a 4K texture every frame to draw it at 640 px.
 * The worker takes the newest frame whenever it is free, and every frame carries the latest copy it
 * made, a frame or so behind. At 1080p the downscale costs more CPU than the upload it saves, so a
 * 1080p frame goes to the preview as it is. A frame the worker is reading is not closed until it is
 * done; its ring buffer could in principle be rewritten under it if the worker took longer than
 * [RING_SIZE] frames, which would only tear the preview.
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
    private var viewSeq = 0L
    private var previewSeq = 0L
    private val retired = ArrayDeque<List<Image>>()

    /** The newest scaled-down copy, which every published frame carries. */
    private var latestPreview: Image? = null
    private val retiredPreviews = ArrayDeque<Image>()
    /** The frame the preview worker is reading, if any, and whether it was retired meanwhile. */
    private var previewSource: Image? = null
    private var closeSourceWhenScaled = false
    /** The bounding box, in view pixels, of everything painted since the preview worker last started. */
    private var previewDirty: Rectangle? = null
    private val previewWorker = Executors.newSingleThreadExecutor(daemon("osr-preview"))
    private val copyPool = Executors.newFixedThreadPool(COPY_THREADS - 1, daemon("osr-copy"))
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
        val (image, full) = target.write(dirtyRects, buffer, copyPool)
        stats.paint(false, copiedNs = System.nanoTime() - start, previewNs = 0, full = full, rects = dirtyRects)
        publish(image, start)
        if (image.width > DOWNSCALE_ABOVE) {
            dirtyRects.forEach { r -> previewDirty = previewDirty?.union(r) ?: Rectangle(r) }
            if (previewSource == null) {
                val dirty = previewDirty
                previewDirty = null
                previewSource = image
                previewWorker.execute { scalePreview(image, dirty) }
            }
        }
    }

    /** On the preview worker: scales [dirty] of [image] down, then hands the copy to the frames from now on. */
    private fun scalePreview(image: Image, dirty: Rectangle?) {
        val start = System.nanoTime()
        val preview = downscale(image, dirty)
        stats.preview(System.nanoTime() - start)
        synchronized(this) {
            previewSource = null
            if (closeSourceWhenScaled) {
                closeSourceWhenScaled = false
                image.close()
            }
            latestPreview?.let { old ->
                retiredPreviews.addLast(old)
                while (retiredPreviews.size > RETIRED_KEPT) retiredPreviews.removeFirst().close()
            }
            latestPreview = preview
            previewSeq++
            // Same picture of the page, so the same paint time: latency stays honest.
            _frames.value?.let { republish(it.paintedAt) }
        }
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

    /**
     * [image] drawn into a [PREVIEW_WIDTH]-wide raster that keeps the last copy, redrawing only the part
     * that covers [dirty] (in view pixels; null or a new raster redraws it all). Only the preview
     * worker calls this.
     */
    private fun downscale(image: Image, dirty: Rectangle?): Image {
        val w = PREVIEW_WIDTH
        val h = image.height * w / image.width
        val reused = previewSurface?.takeIf { it.width == w && it.height == h }
        val surface = reused
            ?: Surface.makeRaster(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL))
                .also { previewSurface?.close(); previewSurface = it }
        val canvas = surface.canvas
        canvas.save()
        if (reused != null && dirty != null) {
            // A margin of two preview pixels covers what linear filtering pulls in from around the change.
            val sx = w.toFloat() / image.width
            val sy = h.toFloat() / image.height
            canvas.clipRect(
                Rect.makeLTRB(
                    dirty.x * sx - CLIP_MARGIN, dirty.y * sy - CLIP_MARGIN,
                    (dirty.x + dirty.width) * sx + CLIP_MARGIN, (dirty.y + dirty.height) * sy + CLIP_MARGIN,
                ),
            )
        }
        canvas.drawImageRect(
            image,
            Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
            Rect.makeWH(w.toFloat(), h.toFloat()),
            FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE),
            null,
            true,
        )
        canvas.restore()
        return surface.makeImageSnapshot()
    }

    private fun republish(paintedAt: Long) {
        val current = _frames.value ?: return
        _frames.value = Frame(
            seq = ++seq,
            view = current.view,
            preview = latestPreview,
            popup = popupImage.takeIf { popupVisible },
            popupRect = popupRect.takeIf { popupVisible },
            paintedAt = paintedAt,
            dipWidth = dipWidth,
            viewSeq = viewSeq,
            previewSeq = previewSeq,
        )
    }

    private fun publish(image: Image, paintedAt: Long) {
        _frames.value?.let { old -> retire(listOf(old.view)) }
        _frames.value = Frame(
            seq = ++seq,
            view = image,
            preview = latestPreview.takeIf { image.width > DOWNSCALE_ABOVE },
            popup = popupImage.takeIf { popupVisible },
            popupRect = popupRect.takeIf { popupVisible },
            paintedAt = paintedAt,
            dipWidth = dipWidth,
            viewSeq = ++viewSeq,
            previewSeq = previewSeq,
        )
    }

    private fun retire(images: List<Image>) {
        retired.addLast(images)
        while (retired.size > RETIRED_KEPT) {
            retired.removeFirst().forEach { image ->
                if (image === previewSource) closeSourceWhenScaled = true else image.close()
            }
        }
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
        fun write(rects: Array<Rectangle>, source: ByteBuffer, pool: ExecutorService): Pair<Image, Boolean> {
            paintNo++
            val covers = rects.any { it.x <= 0 && it.y <= 0 && it.width >= width && it.height >= height }
            history.addLast(if (covers) null else rects.map(::Rectangle).toTypedArray())
            while (history.size > RING_SIZE) history.removeFirst()

            val i = next
            next = (next + 1) % RING_SIZE
            val buffer = buffers[i]
            val behind = paintNo - held[i]
            val pending = history.toList().takeLast(behind.toInt().coerceAtMost(history.size))
            var full = held[i] < 0 || behind > history.size || pending.any { it == null }
            if (!full) {
                // Paints that dirty the same rect (a playing video) are copied once, and a set that
                // covers a large share of the frame goes as one pooled full copy.
                val rects = pending.flatMap { it!!.asList() }.distinct()
                val area = rects.sumOf { clip(it)?.let { c -> c.width.toLong() * c.height } ?: 0L }
                if (area * FULL_COPY_SHARE >= width.toLong() * height) {
                    full = true
                } else {
                    rects.forEach { copyRect(it, source, buffer, pool) }
                }
            }
            if (full) copyAll(source, buffer, pool)
            held[i] = paintNo

            val data = Data.makeWithoutCopy(BufferUtil.getPointerFromByteBuffer(buffer), bytes, owner)
            val image = Image.makeRaster(info, data, rowBytes)
            data.close()
            return image to full
        }

        /** Copies the whole frame in [COPY_THREADS] bands of rows: this thread takes the first, [pool] the rest. */
        private fun copyAll(source: ByteBuffer, target: ByteBuffer, pool: ExecutorService) {
            inBands(0, height, pool) { y0, y1 -> target.put(y0 * rowBytes, source, y0 * rowBytes, (y1 - y0) * rowBytes) }
        }

        /** Runs [copy] over rows [top]..[bottom] in [COPY_THREADS] bands, this thread taking the first. */
        private fun inBands(top: Int, bottom: Int, pool: ExecutorService, copy: (Int, Int) -> Unit) {
            val rows = (bottom - top + COPY_THREADS - 1) / COPY_THREADS
            fun band(k: Int) {
                val y0 = top + k * rows
                val y1 = minOf(bottom, y0 + rows)
                if (y1 > y0) copy(y0, y1)
            }
            val others = (1 until COPY_THREADS).map { k -> pool.submit { band(k) } }
            band(0)
            others.forEach { it.get() }
        }

        /** [r] clipped to the frame, or null when nothing of it is inside. */
        private fun clip(r: Rectangle): Rectangle? =
            r.intersection(Rectangle(0, 0, width, height)).takeIf { !it.isEmpty }

        /** Copies [r] row by row; a large one is split into bands across [pool]. */
        private fun copyRect(r: Rectangle, source: ByteBuffer, target: ByteBuffer, pool: ExecutorService) {
            val c = clip(r) ?: return
            val rowLength = c.width * 4
            val copy = { y0: Int, y1: Int ->
                for (y in y0 until y1) {
                    val at = y * rowBytes + c.x * 4
                    target.put(at, source, at, rowLength)
                }
            }
            if (c.width.toLong() * c.height >= POOLED_RECT_PIXELS) inBands(c.y, c.y + c.height, pool, copy)
            else copy(c.y, c.y + c.height)
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

        /** Threads a whole-frame copy is split across, the paint thread included. */
        const val COPY_THREADS = 4

        /** Pending rects covering at least 1/this of the frame are copied as one pooled full copy instead. */
        const val FULL_COPY_SHARE = 3

        /** A dirty rect at least this big is split across the copy threads too. */
        const val POOLED_RECT_PIXELS = 1_000_000L

        /** Preview pixels redrawn around a change, for what linear filtering reaches. */
        const val CLIP_MARGIN = 2f

        fun daemon(name: String) = ThreadFactory { r -> Thread(r, name).apply { isDaemon = true } }
    }
}
