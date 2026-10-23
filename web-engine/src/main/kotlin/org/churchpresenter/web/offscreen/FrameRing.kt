package org.churchpresenter.web.offscreen

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

/**
 * [RING_SIZE] direct buffers of [width]×[height] pixels, written in turn, each wrapped by Skia without
 * a copy. Each buffer remembers which paint it holds; writing one copies, from Chromium's buffer
 * (which always holds the whole current frame), the dirty rects of every paint since then. With
 * [swizzle] the copy also turns Chromium's BGRA into RGBA. [colorType] is what the pixels are once
 * written.
 */
internal class FrameRing(
    val width: Int,
    val height: Int,
    private val swizzle: Boolean,
    colorType: ColorType,
) {
    /** What [write] made: the image, whether the copy was full, and the buffer under the image. */
    class Written(val image: Image, val full: Boolean, val buffer: ByteBuffer)

    private val info = ImageInfo(width, height, colorType, ColorAlphaType.PREMUL)
    private val rowBytes = width * BYTES_PER_PIXEL
    private val bytes = rowBytes * height
    private val buffers = Array(RING_SIZE) { ByteBuffer.allocateDirect(bytes) }
    private val held = LongArray(RING_SIZE) { -1 }

    /**
     * The owner skiko asks for when wrapping memory without a copy; it only keeps that memory
     * reachable, and [buffers] already do. One placeholder for the ring's lifetime.
     */
    private val owner = Data.makeEmpty()

    /** The dirty rects of the last [RING_SIZE] paints, newest last; null for a paint that covered everything. */
    private val history = ArrayDeque<List<Rectangle>?>()
    private var paintNo = 0L
    private var next = 0

    /** Brings the next buffer up to this paint, from [source], and wraps it in an image. */
    fun write(rects: Array<Rectangle>, source: ByteBuffer, pool: ExecutorService): Written {
        paintNo++
        val covers = rects.any { it.x <= 0 && it.y <= 0 && it.width >= width && it.height >= height }
        history.addLast(if (covers) null else rects.map(::Rectangle))
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
            val union = disjointUnion(pending.flatMap { it.orEmpty() }.mapNotNull(::clip))
            val area = union.sumOf { it.width.toLong() * it.height }
            if (area * FULL_COPY_DENOMINATOR >= width.toLong() * height * FULL_COPY_NUMERATOR) {
                full = true
            } else {
                union.forEach { copyRect(it, source, buffer, pool) }
            }
        }
        if (full) inBands(0, height, pool) { y0, y1 -> span(source, buffer, y0 * rowBytes, (y1 - y0) * rowBytes) }
        held[i] = paintNo

        val data = Data.makeWithoutCopy(BufferUtil.getPointerFromByteBuffer(buffer), bytes, owner)
        val image = Image.makeRaster(info, data, rowBytes)
        data.close()
        return Written(image, full, buffer)
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
            t.putInt(i, (p and GREEN_ALPHA) or (p ushr RED_SHIFT and LOW_BYTE) or (p and LOW_BYTE shl RED_SHIFT))
            i += BYTES_PER_PIXEL
        }
    }

    /** [r] clipped to the frame, or null when nothing of it is inside. */
    private fun clip(r: Rectangle): Rectangle? =
        r.intersection(Rectangle(0, 0, width, height)).takeIf { !it.isEmpty }

    /** Copies [r] row by row; a large one is split into bands across [pool]. */
    private fun copyRect(r: Rectangle, source: ByteBuffer, target: ByteBuffer, pool: ExecutorService) {
        val c = clip(r) ?: return
        val rowLength = c.width * BYTES_PER_PIXEL
        val copy = { y0: Int, y1: Int ->
            for (y in y0 until y1) span(source, target, y * rowBytes + c.x * BYTES_PER_PIXEL, rowLength)
        }
        if (c.width.toLong() * c.height >= POOLED_RECT_PIXELS) inBands(c.y, c.y + c.height, pool, copy)
        else copy(c.y, c.y + c.height)
    }
}

/**
 * The union of [rects] as non-overlapping rects: the plane is cut on every rect edge, and each
 * horizontal band's covered cells are merged into runs. A paint has only a few rects, so the grid
 * stays small.
 */
internal fun disjointUnion(rects: List<Rectangle>): List<Rectangle> {
    if (rects.size <= 1) return rects
    val xs = rects.flatMap { listOf(it.x, it.x + it.width) }.distinct().sorted()
    val ys = rects.flatMap { listOf(it.y, it.y + it.height) }.distinct().sorted()
    val out = ArrayList<Rectangle>()
    for (j in 0 until ys.size - 1) {
        val y0 = ys[j]
        val y1 = ys[j + 1]
        var runStart = -1
        for (i in xs.indices) {
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

/** Runs [work] over rows [top]..[bottom] in [COPY_THREADS] bands, this thread taking the first. */
internal fun inBands(top: Int, bottom: Int, pool: ExecutorService, work: (Int, Int) -> Unit) {
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

/** Frames kept alive after they stop being current: a window may still be drawing one. */
internal const val RETIRED_KEPT = 2

/** Ring buffers: the retired frames, the current one, the worker's, the waiting one and the one being written. */
internal const val RING_SIZE = RETIRED_KEPT + 4

/** Threads a whole-frame copy is split across, the calling thread included. */
internal const val COPY_THREADS = 4

/** A dirty rect at least this big is split across the copy threads too. */
internal const val POOLED_RECT_PIXELS = 1_000_000L

internal const val BYTES_PER_PIXEL = 4

/**
 * Pending rects whose union covers at least this share of the frame are copied as one pooled full
 * copy instead. Large rects are pooled too, so the full copy only pays when the change is most of
 * the frame.
 */
private const val FULL_COPY_NUMERATOR = 3
private const val FULL_COPY_DENOMINATOR = 4

/** The two bytes that stay put when red and blue swap, on a little-endian machine. */
private const val GREEN_ALPHA = 0xFF00FF00.toInt()
private const val RED_SHIFT = 16
private const val LOW_BYTE = 0xff
