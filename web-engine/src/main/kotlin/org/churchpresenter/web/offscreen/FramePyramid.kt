package org.churchpresenter.web.offscreen

import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import java.awt.Rectangle
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService

/**
 * The view halved, and halved again while a level stays at least [SMALLEST_LEVEL] wide, each level
 * the exact 2×2 average of the one above. Each is kept whole in its own buffer and redone over only
 * the part that changed, then published through a [FrameRing] of its own, so a window can draw one
 * while the next is written. [colorType] is the view's, which the levels keep.
 */
internal class FramePyramid(val viewWidth: Int, val viewHeight: Int, colorType: ColorType) {
    private class Level(val width: Int, val height: Int, colorType: ColorType) {
        val pixels: ByteBuffer = ByteBuffer.allocateDirect(width * height * BYTES_PER_PIXEL)
        val ring = FrameRing(width, height, swizzle = false, colorType = colorType)
    }

    private val levels = buildList {
        var w = viewWidth / 2
        var h = viewHeight / 2
        while (w >= SMALLEST_LEVEL) {
            add(Level(w, h, colorType))
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
                val src = Plane(source, sourceWidth)
                val dst = Plane(level.pixels, level.width)
                val work = { r0: Int, r1: Int -> halve(src, dst, Rectangle(x0, r0, x1 - x0, r1 - r0)) }
                if (area.width.toLong() * area.height >= POOLED_LEVEL_PIXELS) inBands(y0, y1, pool, work)
                else work(y0, y1)
            }
            source = level.pixels
            sourceWidth = level.width
            changed = area
            level.ring.write(arrayOf(area), level.pixels, pool).image
        }
    }
}

/** Pixels [width] wide in [buffer], rows packed. */
private class Plane(val buffer: ByteBuffer, val width: Int)

/**
 * Writes [area] of [dst] as the 2×2 average of [src], rounded: each pixel's four bytes are summed two
 * lanes at a time, so a pixel is three adds a lane. Both buffers are read and written in one byte
 * order, so every byte stays in its place.
 */
private fun halve(src: Plane, dst: Plane, area: Rectangle) {
    val s = src.buffer.duplicate().order(ByteOrder.nativeOrder())
    val d = dst.buffer.duplicate().order(ByteOrder.nativeOrder())
    val srcRow = src.width * BYTES_PER_PIXEL
    for (y in area.y until area.y + area.height) {
        val top = 2 * y * srcRow
        val bottom = top + srcRow
        var out = (y * dst.width + area.x) * BYTES_PER_PIXEL
        for (x in area.x until area.x + area.width) {
            val at = x * 2 * BYTES_PER_PIXEL
            val p0 = s.getInt(top + at)
            val p1 = s.getInt(top + at + BYTES_PER_PIXEL)
            val p2 = s.getInt(bottom + at)
            val p3 = s.getInt(bottom + at + BYTES_PER_PIXEL)
            val even = (p0 and LANES) + (p1 and LANES) + (p2 and LANES) + (p3 and LANES) + ROUND
            val odd = (p0 ushr LANE_SHIFT and LANES) + (p1 ushr LANE_SHIFT and LANES) +
                (p2 ushr LANE_SHIFT and LANES) + (p3 ushr LANE_SHIFT and LANES) + ROUND
            d.putInt(out, (even ushr 2 and LANES) or ((odd ushr 2 and LANES) shl LANE_SHIFT))
            out += BYTES_PER_PIXEL
        }
    }
}

/** A level area at least this big is split across the copy threads: a level pixel reads four. */
private const val POOLED_LEVEL_PIXELS = POOLED_RECT_PIXELS / 4

/** The pyramid's levels stop before they would be narrower than this. */
internal const val SMALLEST_LEVEL = 480

/** Two bytes of a pixel, each in its own 16-bit lane, and half of four for rounding a lane's sum. */
private const val LANES = 0x00FF00FF
private const val ROUND = 0x00020002
private const val LANE_SHIFT = 8
