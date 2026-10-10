package org.churchpresenter.lowerthird.render

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.ByteBuffer

private const val INT_BYTES = 4

/**
 * The cache's frame codec: run-length coding over whole 32-bit ARGB pixels. A record is
 * `[count i32][...]` — count > 0 is a run of one repeated pixel value, count < 0 is |count| literal
 * pixel values. Lower thirds are mostly transparent, so frames typically shrink >90%.
 */
internal object ArgbRle {
    /** Runs shorter than this stay literal — a run record costs 8 bytes. */
    private const val MIN_RUN = 3

    /** Writes pixels [from, until) as one negative-length literal record. */
    private fun flushLiteralRun(buf: ByteBuffer, pixels: IntArray, from: Int, until: Int) {
        buf.putInt(-(until - from))
        for (j in from until until) buf.putInt(pixels[j])
    }

    internal fun encodeArgbRle(pixels: IntArray): ByteArray {
        // Worst case is alternating length-1 literal records and minimum runs, which stays
        // at parity with raw size; headroom covers record headers.
        val buf = ByteBuffer.allocate(pixels.size * 4 + pixels.size / MIN_RUN * 4 + 16)
        var i = 0
        var literalStart = -1
        while (i < pixels.size) {
            var runEnd = i + 1
            val value = pixels[i]
            while (runEnd < pixels.size && pixels[runEnd] == value) runEnd++
            val runLen = runEnd - i
            if (runLen >= MIN_RUN) {
                if (literalStart >= 0) flushLiteralRun(buf, pixels, literalStart, i)
                literalStart = -1
                buf.putInt(runLen)
                buf.putInt(value)
            } else if (literalStart < 0) {
                literalStart = i
            }
            i = if (runLen >= MIN_RUN) runEnd else i + runLen
        }
        if (literalStart >= 0) flushLiteralRun(buf, pixels, literalStart, pixels.size)
        return buf.array().copyOf(buf.position())
    }

    internal fun decodeArgbRle(payload: ByteArray, pixelCount: Int): IntArray {
        val buf = ByteBuffer.wrap(payload)
        val out = IntArray(pixelCount)
        var o = 0
        // Every read checks there is an int left, so a payload cut short ends the loop and is reported
        // below as a truncated frame rather than escaping as a BufferUnderflowException.
        while (buf.remaining() >= INT_BYTES && o < pixelCount) {
            val count = buf.int
            if (count > 0) {
                if (buf.remaining() < INT_BYTES) break
                val value = buf.int
                out.fill(value, o, minOf(o + count, pixelCount))
                o += count
            } else {
                var n = -count
                while (n-- > 0 && o < pixelCount && buf.remaining() >= INT_BYTES) out[o++] = buf.int
            }
        }
        if (o < pixelCount) throw IOException("Truncated RLE frame: got $o of $pixelCount pixels")
        return out
    }

    /** Bilinear ARGB scale (used only for same-aspect raster mismatches at ATEM upload time). */
    internal fun scaleArgb(src: IntArray, sw: Int, sh: Int, dw: Int, dh: Int): IntArray {
        val srcImg = BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB)
        srcImg.setRGB(0, 0, sw, sh, src, 0, sw)
        val dstImg = BufferedImage(dw, dh, BufferedImage.TYPE_INT_ARGB)
        val g = dstImg.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.drawImage(srcImg, 0, 0, dw, dh, null)
        } finally {
            g.dispose()
        }
        return dstImg.getRGB(0, 0, dw, dh, null, 0, dw)
    } }
