package org.churchpresenter.web.offscreen

import org.jetbrains.skia.ColorType
import java.awt.Rectangle
import java.lang.ref.Reference
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The copy out of Chromium's buffer: a ring buffer brought up to date with every paint's dirty rects
 * matches the source exactly, whole or in part, swapped to RGBA or not; the rect union and the row
 * bands it rests on; and the level pyramid's exact 2×2 averages, redone over only what changed.
 */
class FrameCopyTest {

    @Test
    fun `a first write copies the whole frame`() = withPool { pool ->
        val source = paintBuffer(64, 32)
        val ring = FrameRing(64, 32, swizzle = false, colorType = ColorType.BGRA_8888)

        val written = ring.write(arrayOf(Rectangle(0, 0, 4, 4)), source, pool)

        assertTrue(written.full, "a buffer never written has nothing to bring up to date")
        assertContentEquals(source.allBytes(), written.image.bytes())
        Reference.reachabilityFence(ring)
    }

    @Test
    fun `every buffer of the ring catches up with the paints it missed`() = withPool { pool ->
        val width = 64
        val height = 32
        val ring = FrameRing(width, height, swizzle = false, colorType = ColorType.BGRA_8888)
        val source = paintBuffer(width, height, seed = 1)
        repeat(RING_SIZE) { ring.write(arrayOf(Rectangle(0, 0, width, height)), source, pool) }

        // Chromium's buffer always holds the whole current frame; only the rects changed since. Until
        // the full-frame paints have left the ring's history a buffer catching up on one copies all.
        repeat(RING_SIZE * 2) { paint ->
            val rect = Rectangle(paint * 3 % 40, paint % 20, 8, 6)
            paint(source, width, rect, seed = paint + 10)
            val written = ring.write(arrayOf(rect), source, pool)
            if (paint >= RING_SIZE) assertFalse(written.full, "a few small rects are copied, not the frame")
            assertContentEquals(source.allBytes(), written.image.bytes(), "paint $paint")
        }
        Reference.reachabilityFence(ring)
    }

    @Test
    fun `a paint covering most of the frame goes as one full copy`() = withPool { pool ->
        val ring = FrameRing(40, 40, swizzle = false, colorType = ColorType.BGRA_8888)
        val source = paintBuffer(40, 40)
        repeat(RING_SIZE) { ring.write(arrayOf(Rectangle(0, 0, 40, 40)), source, pool) }

        val written = ring.write(arrayOf(Rectangle(0, 0, 40, 36)), source, pool)

        assertTrue(written.full)
        assertContentEquals(source.allBytes(), written.image.bytes())
        Reference.reachabilityFence(ring)
    }

    @Test
    fun `with swizzle red and blue swap and green and alpha stay`() = withPool { pool ->
        val source = paintBuffer(8, 4)
        val ring = FrameRing(8, 4, swizzle = true, colorType = ColorType.RGBA_8888)

        val out = ring.write(arrayOf(Rectangle(0, 0, 8, 4)), source, pool).image.bytes()

        val bgra = source.allBytes()
        for (i in bgra.indices step BYTES_PER_PIXEL) {
            assertEquals(bgra[i + 2], out[i], "red")
            assertEquals(bgra[i + 1], out[i + 1], "green")
            assertEquals(bgra[i], out[i + 2], "blue")
            assertEquals(bgra[i + 3], out[i + 3], "alpha")
        }
        Reference.reachabilityFence(ring)
    }

    @Test
    fun `the union of overlapping rects covers each pixel once`() {
        val rects = listOf(Rectangle(0, 0, 10, 10), Rectangle(5, 5, 10, 10), Rectangle(2, 2, 3, 3))
        val union = disjointUnion(rects)

        val covered = HashMap<Pair<Int, Int>, Int>()
        union.forEach { r ->
            for (x in r.x until r.x + r.width) for (y in r.y until r.y + r.height) covered.merge(x to y, 1, Int::plus)
        }
        val wanted = buildSet {
            rects.forEach { r -> for (x in r.x until r.x + r.width) for (y in r.y until r.y + r.height) add(x to y) }
        }
        assertEquals(wanted, covered.keys)
        assertTrue(covered.values.all { it == 1 }, "no pixel is copied twice")
    }

    @Test
    fun `a single rect is its own union`() {
        val only = listOf(Rectangle(1, 2, 3, 4))
        assertEquals(only, disjointUnion(only))
    }

    @Test
    fun `the bands cover every row once, however the rows divide`() = withPool { pool ->
        for (rows in listOf(1, 3, 4, 7, 101)) {
            val seen = IntArray(rows)
            inBands(0, rows, pool) { y0, y1 -> synchronized(seen) { for (y in y0 until y1) seen[y]++ } }
            assertTrue(seen.all { it == 1 }, "$rows rows")
        }
    }

    @Test
    fun `each level is the rounded two by two average of the one above`() = withPool { pool ->
        val view = paintBuffer(1920, 1080)
        // Held for the whole test: the level images wrap the pyramid's own buffers.
        val pyramid = FramePyramid(1920, 1080, ColorType.BGRA_8888)
        val levels = pyramid.update(view, null, pool)

        assertEquals(listOf(960 to 540, 480 to 270), levels.map { it.width to it.height })
        var above = view.allBytes()
        var aboveWidth = 1920
        levels.forEach { level ->
            val got = level.bytes()
            assertContentEquals(halved(above, aboveWidth, level.width, level.height), got)
            above = got
            aboveWidth = level.width
        }
        Reference.reachabilityFence(pyramid)
    }

    @Test
    fun `a level redone over a dirty rect matches one redone whole`() = withPool { pool ->
        val view = paintBuffer(1920, 1080, seed = 3)
        val pyramid = FramePyramid(1920, 1080, ColorType.BGRA_8888)
        pyramid.update(view, null, pool)

        val dirty = Rectangle(301, 77, 640, 401)
        paint(view, 1920, dirty, seed = 4)
        val partial = pyramid.update(view, dirty, pool).map { it.bytes() }
        val fresh = FramePyramid(1920, 1080, ColorType.BGRA_8888)
        val whole = fresh.update(view, null, pool).map { it.bytes() }

        partial.zip(whole).forEach { (a, b) -> assertContentEquals(b, a) }
        Reference.reachabilityFence(pyramid)
        Reference.reachabilityFence(fresh)
    }

    @Test
    fun `a view too narrow to halve has no levels`() = withPool { pool ->
        val levels = FramePyramid(800, 600, ColorType.BGRA_8888).update(paintBuffer(800, 600), null, pool)
        assertTrue(levels.isEmpty())
    }

    /** Fills [rect] of [buffer], [width] pixels wide, with new bytes from [seed]. */
    private fun paint(buffer: ByteBuffer, width: Int, rect: Rectangle, seed: Int) {
        val fill = kotlin.random.Random(seed)
        for (y in rect.y until rect.y + rect.height) {
            for (x in rect.x until rect.x + rect.width) {
                val at = (y * width + x) * BYTES_PER_PIXEL
                repeat(BYTES_PER_PIXEL) { buffer.put(at + it, fill.nextInt().toByte()) }
            }
        }
    }

    /** The expected level: each byte the rounded mean of its 2×2 block above. */
    private fun halved(above: ByteArray, aboveWidth: Int, width: Int, height: Int): ByteArray {
        val out = ByteArray(width * height * BYTES_PER_PIXEL)
        for (y in 0 until height) for (x in 0 until width) for (c in 0 until BYTES_PER_PIXEL) {
            fun at(dx: Int, dy: Int) =
                above[((2 * y + dy) * aboveWidth + 2 * x + dx) * BYTES_PER_PIXEL + c].toInt() and 0xff
            out[(y * width + x) * BYTES_PER_PIXEL + c] = ((at(0, 0) + at(1, 0) + at(0, 1) + at(1, 1) + 2) / 4).toByte()
        }
        return out
    }
}
