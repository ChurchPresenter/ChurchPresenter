package org.churchpresenter.osrproto

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.cef.browser.HeadlessSink
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.impl.BufferUtil
import java.awt.Rectangle
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/** One published picture of the page: the view, smaller copies for the preview, and the open popup over it. */
class Frame(
    val seq: Long,
    val view: Image,
    /** [view] halved, quartered and so on, largest first; empty when [view] is already small. */
    val levels: List<Image>,
    val popup: Image?,
    val popupRect: Rectangle?,
    /** `System.nanoTime()` when Chromium handed the paint over, for paint→draw latency. */
    val paintedAt: Long,
    /** The page's width in DIPs: [popupRect] is in DIPs, the images in pixels. */
    val dipWidth: Int,
    /** Counts of new [view]s and new [levels] published so far, for each window's skipped-frame count. */
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
 * A worker thread also keeps a [Pyramid] of the view: halved, and halved again down to
 * [SMALLEST_LEVEL] wide, each level the exact 2×2 average of the one above. A small window draws
 * the smallest level at least its own size, so it never shrinks a picture by more than 2:1 — past
 * that a bilinear sample skips source pixels and small text breaks up — and never uploads a 4K
 * texture to draw it at 640 px. Each level is redone only over what changed, where mipmaps would be
 * rebuilt whole for every frame in every window. A frame is published only with its levels, so
 * both windows change at the same moment: on Linux skiko draws the windows that changed together and
 * waits for one vertical blank, and a preview a few milliseconds behind the output cost a second
 * wait every frame and halved both windows' rate. The worker takes the newest frame whenever it is
 * free; a paint that arrives while it is busy replaces the one waiting, and is not drawn.
 * `-PplainPreview=1` turns the levels off, and frames are then published from the paint callback.
 *
 * An image is closed [RETIRED_KEPT] frames after it stops being current. The ring has room for
 * those, the current frame, the one with the worker, the one waiting and the one being written; a
 * run of paints dropped while the worker is busy uses ring buffers too, and if it were long enough
 * to come round to a frame still on screen, that frame would tear. Nothing is freed under an image.
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
    /** The preview worker's levels, and replaced ones kept until no image over them is left. */
    private var pyramid: Pyramid? = null
    private val oldPyramids = ArrayDeque<Pair<Long, Pyramid>>()
    private var seq = 0L
    private var viewSeq = 0L
    private var previewSeq = 0L
    private val retired = ArrayDeque<List<Image>>()

    /** The newest levels, which every published frame carries. */
    private var latestLevels: List<Image> = emptyList()
    private val retiredLevels = ArrayDeque<List<Image>>()
    /** Whether the preview worker is busy with a frame. */
    private var scaling = false
    /** A paint written but not yet published, waiting for the worker, and when it arrived. */
    private var pending: Ring.Written? = null
    private var pendingAt = 0L
    /** The bounding box, in view pixels, of everything painted since the preview worker last started. */
    private var previewDirty: Rectangle? = null
    private val previewWorker = Executors.newSingleThreadExecutor(daemon("osr-preview"))
    private val copyPool = Executors.newFixedThreadPool(COPY_THREADS - 1, daemon("osr-copy"))
    /** The preview worker's own helpers, so its work never queues ahead of a paint's copy. */
    private val levelPool = Executors.newFixedThreadPool(COPY_THREADS - 1, daemon("osr-levels"))
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
            ?: Ring(width, height, swizzle = rgba).also { fresh ->
                // Images over the old ring's buffers are still being retired; keep it alive until they are.
                ring?.let { old -> oldRings.addLast(seq to old) }
                ring = fresh
            }
        val written = target.write(dirtyRects, buffer, copyPool)
        stats.paint(false, copiedNs = System.nanoTime() - start, previewNs = 0, full = written.full, rects = dirtyRects)
        if (width / 2 < SMALLEST_LEVEL || plainPreview) {
            publish(written.image, emptyList(), start)
            return
        }
        dirtyRects.forEach { r -> previewDirty = previewDirty?.union(r) ?: Rectangle(r) }
        pending?.image?.close()
        pending = written
        pendingAt = start
        if (!scaling) startScaling()
    }

    /** Hands the waiting paint, if any, to the preview worker. Called holding the lock. */
    private fun startScaling() {
        val next = pending ?: return
        val paintedAt = pendingAt
        val dirty = previewDirty
        pending = null
        previewDirty = null
        scaling = true
        previewWorker.execute { scaleLevels(next, paintedAt, dirty) }
    }

    /** On the preview worker: brings the levels up to date over [dirty] of [view], then publishes the two together. */
    private fun scaleLevels(view: Ring.Written, paintedAt: Long, dirty: Rectangle?) {
        val start = System.nanoTime()
        val width = view.image.width
        val height = view.image.height
        val fresh = pyramid?.takeIf { it.viewWidth == width && it.viewHeight == height }
        val target = fresh ?: Pyramid(width, height).also { made ->
            // Images over the old levels' buffers are still being retired; keep them alive until they are.
            pyramid?.let { old -> oldPyramids.addLast(previewSeq to old) }
            pyramid = made
        }
        while (oldPyramids.isNotEmpty() && previewSeq - oldPyramids.first().first > RING_SIZE) oldPyramids.removeFirst()
        val levels = target.update(view.buffer, if (fresh == null) null else dirty, levelPool)
        stats.preview(System.nanoTime() - start)
        synchronized(this) {
            scaling = false
            retiredLevels.addLast(latestLevels)
            while (retiredLevels.size > RETIRED_KEPT) retiredLevels.removeFirst().forEach(Image::close)
            latestLevels = levels
            previewSeq++
            publish(view.image, levels, paintedAt)
            startScaling()
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

    private fun republish(paintedAt: Long) {
        val current = _frames.value ?: return
        _frames.value = Frame(
            seq = ++seq,
            view = current.view,
            levels = latestLevels,
            popup = popupImage.takeIf { popupVisible },
            popupRect = popupRect.takeIf { popupVisible },
            paintedAt = paintedAt,
            dipWidth = dipWidth,
            viewSeq = viewSeq,
            previewSeq = previewSeq,
        )
    }

    private fun publish(image: Image, levels: List<Image>, paintedAt: Long) {
        _frames.value?.let { old -> retire(listOf(old.view)) }
        _frames.value = Frame(
            seq = ++seq,
            view = image,
            levels = levels,
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
            retired.removeFirst().forEach(Image::close)
        }
        while (oldRings.isNotEmpty() && seq - oldRings.first().first > RING_SIZE) oldRings.removeFirst()
    }

    /**
     * [RING_SIZE] direct buffers the size of the view, written in turn. Each remembers which paint
     * it holds; writing one copies, from Chromium's buffer, the dirty rects of every paint since then.
     * With [swizzle] the copy also turns Chromium's BGRA into RGBA. Pixels are in the order
     * `-Prgba=1` asks for, whatever the ring.
     */
    private class Ring(val width: Int, val height: Int, private val swizzle: Boolean) {
        /** What [write] made: the image, whether the copy was full, and the buffer under the image. */
        class Written(val image: Image, val full: Boolean, val buffer: ByteBuffer)

        private val info = ImageInfo(width, height, if (rgba) ColorType.RGBA_8888 else ColorType.BGRA_8888, ColorAlphaType.PREMUL)
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

        /** Brings the next buffer up to this paint, from [source], and wraps it in an image. */
        fun write(rects: Array<Rectangle>, source: ByteBuffer, pool: ExecutorService): Written {
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
                // The pending paints' rects are reduced to their exact union as non-overlapping rects, so
                // a repeated or slightly shifting rect (a playing video, a rotating element) is copied
                // once; a union covering most of the frame goes as one pooled full copy instead.
                val union = disjointUnion(pending.flatMap { it!!.asList() }.mapNotNull(::clip))
                val area = union.sumOf { it.width.toLong() * it.height }
                if (area * FULL_COPY_DENOMINATOR >= width.toLong() * height * FULL_COPY_NUMERATOR) {
                    full = true
                } else {
                    union.forEach { copyRect(it, source, buffer, pool) }
                }
            }
            if (full) copyAll(source, buffer, pool)
            held[i] = paintNo

            val data = Data.makeWithoutCopy(BufferUtil.getPointerFromByteBuffer(buffer), bytes, owner)
            val image = Image.makeRaster(info, data, rowBytes)
            data.close()
            return Written(image, full, buffer)
        }

        /** Copies the whole frame in [COPY_THREADS] bands of rows: this thread takes the first, [pool] the rest. */
        private fun copyAll(source: ByteBuffer, target: ByteBuffer, pool: ExecutorService) {
            inBands(0, height, pool) { y0, y1 -> span(source, target, y0 * rowBytes, (y1 - y0) * rowBytes) }
        }

        /** Copies [length] bytes at [at] from [source] to [target], swapping red and blue with [swizzle]. */
        private fun span(source: ByteBuffer, target: ByteBuffer, at: Int, length: Int) {
            if (!swizzle) {
                target.put(at, source, at, length)
                return
            }
            val s = source.duplicate().order(ByteOrder.nativeOrder())
            val t = target.duplicate().order(ByteOrder.nativeOrder())
            var i = at
            val end = at + length
            while (i < end) {
                val p = s.getInt(i)
                t.putInt(i, (p and GREEN_ALPHA) or (p ushr 16 and 0xff) or (p and 0xff shl 16))
                i += 4
            }
        }

        /**
         * The union of [rects] as non-overlapping rects: the plane is cut on every rect edge, and each
         * horizontal band's covered cells are merged into runs. A paint has only a few rects, so the
         * grid stays small.
         */
        private fun disjointUnion(rects: List<Rectangle>): List<Rectangle> {
            if (rects.size <= 1) return rects
            val xs = rects.flatMap { listOf(it.x, it.x + it.width) }.distinct().sorted()
            val ys = rects.flatMap { listOf(it.y, it.y + it.height) }.distinct().sorted()
            val out = ArrayList<Rectangle>()
            for (j in 0 until ys.size - 1) {
                val y0 = ys[j]
                val y1 = ys[j + 1]
                var runStart = -1
                for (i in 0 until xs.size) {
                    val covered = i < xs.size - 1 && rects.any { r ->
                        r.x <= xs[i] && r.x + r.width >= xs[i + 1] && r.y <= y0 && r.y + r.height >= y1
                    }
                    if (covered && runStart < 0) runStart = i
                    if (!covered && runStart >= 0) {
                        out += Rectangle(xs[runStart], y0, xs[i] - xs[runStart], y1 - y0)
                        runStart = -1
                    }
                }
            }
            return out
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
                    span(source, target, y * rowBytes + c.x * 4, rowLength)
                }
            }
            if (c.width.toLong() * c.height >= POOLED_RECT_PIXELS) inBands(c.y, c.y + c.height, pool, copy)
            else copy(c.y, c.y + c.height)
        }
    }

    /**
     * The view halved, and halved again while a level stays at least [SMALLEST_LEVEL] wide. Each
     * level is kept whole in its own buffer and redone over only the part that changed, then
     * published through a [Ring] of its own, so a window can draw one while the next is written.
     */
    private class Pyramid(val viewWidth: Int, val viewHeight: Int) {
        private class Level(val width: Int, val height: Int) {
            val pixels: ByteBuffer = ByteBuffer.allocateDirect(width * height * 4)
            val ring = Ring(width, height, swizzle = false)
        }

        private val levels = buildList {
            var w = viewWidth / 2
            var h = viewHeight / 2
            while (w >= SMALLEST_LEVEL) {
                add(Level(w, h))
                w /= 2
                h /= 2
            }
        }

        /** Redoes every level over [dirty] (view pixels; null for all of it) of [view], and returns their new images. */
        fun update(view: ByteBuffer, dirty: Rectangle?, pool: ExecutorService): List<Image> {
            var source = view
            var sourceWidth = viewWidth
            var changed = dirty ?: Rectangle(0, 0, viewWidth, viewHeight)
            return levels.map { level ->
                // The level pixels any changed source pixel feeds.
                val x0 = (changed.x / 2).coerceAtLeast(0)
                val y0 = (changed.y / 2).coerceAtLeast(0)
                val x1 = ((changed.x + changed.width + 1) / 2).coerceAtMost(level.width)
                val y1 = ((changed.y + changed.height + 1) / 2).coerceAtMost(level.height)
                val area = Rectangle(x0, y0, (x1 - x0).coerceAtLeast(0), (y1 - y0).coerceAtLeast(0))
                if (!area.isEmpty) {
                    val src = source
                    val srcWidth = sourceWidth
                    val work = { r0: Int, r1: Int -> halve(src, srcWidth, level.pixels, level.width, x0, x1, r0, r1) }
                    if (area.width.toLong() * area.height >= POOLED_RECT_PIXELS / 4) inBands(y0, y1, pool, work)
                    else work(y0, y1)
                }
                source = level.pixels
                sourceWidth = level.width
                changed = area
                level.ring.write(arrayOf(area), level.pixels, pool).image
            }
        }

        /**
         * Writes rows [y0]..[y1], columns [x0]..[x1] of [dst] as the 2×2 average of [src], rounded:
         * each pixel's four bytes are summed two lanes at a time, so a pixel is three adds a lane.
         * Both buffers are read and written in one byte order, so every byte stays in its place.
         */
        private fun halve(src: ByteBuffer, srcWidth: Int, dst: ByteBuffer, dstWidth: Int, x0: Int, x1: Int, y0: Int, y1: Int) {
            val s = src.duplicate().order(ByteOrder.nativeOrder())
            val d = dst.duplicate().order(ByteOrder.nativeOrder())
            val srcRow = srcWidth * 4
            for (y in y0 until y1) {
                val top = 2 * y * srcRow
                val bottom = top + srcRow
                var out = (y * dstWidth + x0) * 4
                for (x in x0 until x1) {
                    val at = x * 8
                    val p0 = s.getInt(top + at)
                    val p1 = s.getInt(top + at + 4)
                    val p2 = s.getInt(bottom + at)
                    val p3 = s.getInt(bottom + at + 4)
                    val even = (p0 and LANES) + (p1 and LANES) + (p2 and LANES) + (p3 and LANES) + ROUND
                    val odd = (p0 ushr 8 and LANES) + (p1 ushr 8 and LANES) + (p2 ushr 8 and LANES) +
                        (p3 ushr 8 and LANES) + ROUND
                    d.putInt(out, (even ushr 2 and LANES) or ((odd ushr 2 and LANES) shl 8))
                    out += 4
                }
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

        /** Ring buffers: the retired frames, the current one, the worker's, the waiting one and the one being written. */
        const val RING_SIZE = RETIRED_KEPT + 4

        /** The pyramid's levels stop before they would be narrower than this. */
        const val SMALLEST_LEVEL = 480

        /**
         * Pending rects whose union covers at least this share of the frame are copied as one pooled
         * full copy instead. Large rects are pooled too, so the full copy only pays when the change is
         * most of the frame.
         */
        const val FULL_COPY_NUMERATOR = 3
        const val FULL_COPY_DENOMINATOR = 4

        /** A dirty rect at least this big is split across the copy threads too. */
        const val POOLED_RECT_PIXELS = 1_000_000L

        /** Two bytes of a pixel, each in its own 16-bit lane, and half of four for rounding a lane's sum. */
        const val LANES = 0x00FF00FF
        const val ROUND = 0x00020002

        /** The two bytes that stay put when red and blue swap, on a little-endian machine. */
        const val GREEN_ALPHA = 0xFF00FF00.toInt()

        fun daemon(name: String) = ThreadFactory { r -> Thread(r, name).apply { isDaemon = true } }
    }
}

/**
 * `-Prgba=1`: frames are kept as RGBA rather than Chromium's BGRA, swapped during the copy, to find out
 * whether a GPU driver converts BGRA on the CPU while uploading it.
 */
internal val rgba = System.getProperty("osr.rgba") != null

/** `-PplainPreview=1`: no levels, so the preview draws the whole view with plain bilinear sampling. */
internal val plainPreview = System.getProperty("osr.plainPreview") != null

/** Runs [work] over rows [top]..[bottom] in [COPY_THREADS] bands, this thread taking the first. */
private fun inBands(top: Int, bottom: Int, pool: ExecutorService, work: (Int, Int) -> Unit) {
    val rows = (bottom - top + COPY_THREADS - 1) / COPY_THREADS
    fun band(k: Int) {
        val y0 = top + k * rows
        val y1 = minOf(bottom, y0 + rows)
        if (y1 > y0) work(y0, y1)
    }
    val others = (1 until COPY_THREADS).map { k -> pool.submit { band(k) } }
    band(0)
    others.forEach { it.get() }
}

/** Threads a whole-frame copy is split across, the calling thread included. */
private const val COPY_THREADS = 4
