package org.churchpresenter.app.churchpresenter.server

import java.nio.ByteBuffer

/**
 * A frame encoded for the ATEM media pool: 10-bit YUVA 4:2:2, RLE-compressed.
 *
 * @param data   RLE-encoded YUVA bytes (what gets sent in FTDa chunks)
 * @param rawLen pre-RLE length in bytes (width*height*4) — the FTSD size field
 */
class EncodedFrame(val data: ByteArray, val rawLen: Int)

/**
 * Converts ARGB pixels to the ATEM's native upload format.
 *
 * Ports of sofie-atem-connection's converters (lib/converters/rgbaToYuv422.ts and rle.ts):
 *  - 10-bit YUVA 4:2:2 at broadcast levels, BT.709 for >=720p, BT.601 below
 *  - RLE: runs of >3 identical 8-byte blocks become [RLE_HEADER][count u64][block u64].
 *    Lower thirds are mostly flat/transparent, so this typically shrinks frames >90%.
 */
object AtemFrameEncoder {

    /** 8-byte marker introducing an RLE run. Cannot occur in YUVA data (alpha tops out at 0x3A…). */
    const val RLE_HEADER = -0x0101010101010102L   // 0xFEFEFEFEFEFEFEFE

    /** Convert + compress one ARGB frame. */
    fun encodeFrame(width: Int, height: Int, argbPixels: IntArray): EncodedFrame {
        require(argbPixels.size == width * height) {
            "Pixel buffer is ${argbPixels.size} pixels, expected ${width}×${height}"
        }
        val raw = argbToYuv422(width, height, argbPixels)
        return EncodedFrame(encodeRLE(raw), raw.size)
    }

    /**
     * Convert ARGB ints to 10-bit YUVA 4:2:2: each pixel pair packs into two big-endian
     * 32-bit words — (A1, Cb, Y1) and (A2, Cr, Y2).
     */
    fun argbToYuv422(width: Int, height: Int, pixels: IntArray): ByteArray {
        val kr = if (height >= 720) 0.2126 else 0.299
        val kb = if (height >= 720) 0.0722 else 0.114
        val kg = 1.0 - kr - kb
        val kri = 1.0 - kr
        val kbi = 1.0 - kb
        val yRange = 219.0
        val halfCbCrRange = 224.0 / 2
        val yOffset = (16 shl 8).toDouble()
        val cbCrOffset = (128 shl 8).toDouble()
        val krOKbi = kr / kbi * halfCbCrRange
        val kgOKbi = kg / kbi * halfCbCrRange
        val kbOKri = kb / kri * halfCbCrRange
        val kgOKri = kg / kri * halfCbCrRange

        fun genColor(rawA: Int, uv16: Double, y16: Double): Int {
            val a = ((rawA shl 2) * 219) / 255 + (16 shl 2)
            val y = (Math.round(y16) shr 6).toInt()
            val uv = (Math.round(uv16) shr 6).toInt()
            return (a shl 20) or (uv shl 10) or y
        }

        val out = ByteArray(width * height * 4)
        var o = 0
        var i = 0
        while (i < pixels.size) {
            val px1 = pixels[i]
            val px2 = pixels[i + 1]
            val a1 = (px1 ushr 24) and 0xFF
            val r1 = (px1 shr 16) and 0xFF
            val g1 = (px1 shr 8) and 0xFF
            val b1 = px1 and 0xFF
            val a2 = (px2 ushr 24) and 0xFF
            val r2 = (px2 shr 16) and 0xFF
            val g2 = (px2 shr 8) and 0xFF
            val b2 = px2 and 0xFF

            val y16a = yOffset + kr * yRange * r1 + kg * yRange * g1 + kb * yRange * b1
            val cb16 = cbCrOffset + (-krOKbi * r1 - kgOKbi * g1 + halfCbCrRange * b1)
            val y16b = yOffset + kr * yRange * r2 + kg * yRange * g2 + kb * yRange * b2
            val cr16 = cbCrOffset + (halfCbCrRange * r1 - kgOKri * g1 - kbOKri * b1)

            val word1 = genColor(a1, cb16, y16a)
            val word2 = genColor(a2, cr16, y16b)
            out[o]     = ((word1 ushr 24) and 0xFF).toByte()
            out[o + 1] = ((word1 shr 16) and 0xFF).toByte()
            out[o + 2] = ((word1 shr 8) and 0xFF).toByte()
            out[o + 3] = (word1 and 0xFF).toByte()
            out[o + 4] = ((word2 ushr 24) and 0xFF).toByte()
            out[o + 5] = ((word2 shr 16) and 0xFF).toByte()
            out[o + 6] = ((word2 shr 8) and 0xFF).toByte()
            out[o + 7] = (word2 and 0xFF).toByte()

            i += 2
            o += 8
        }
        return out
    }

    /**
     * RLE-compress YUVA data (8-byte block granularity). Direct port of
     * sofie-atem-connection's encodeRLE; output is never larger than the input.
     */
    fun encodeRLE(data: ByteArray): ByteArray {
        val result = ByteArray(data.size)
        val src = ByteBuffer.wrap(data)
        val dst = ByteBuffer.wrap(result)
        var lastBlock = src.getLong(0)
        var identicalCount = 0
        var differentCount = 0
        var resultOffset = -8

        var sourceOffset = 8
        while (sourceOffset < data.size) {
            val block = src.getLong(sourceOffset)

            if (block == lastBlock) {
                ++identicalCount
                if (differentCount > 0) {
                    System.arraycopy(
                        data, sourceOffset - 8 * (differentCount + 1),
                        result, resultOffset + 8, differentCount * 8
                    )
                    resultOffset += differentCount * 8
                    differentCount = 0
                }
                sourceOffset += 8
                continue
            }
            if (identicalCount > 2) {
                resultOffset += 8; dst.putLong(resultOffset, RLE_HEADER)
                resultOffset += 8; dst.putLong(resultOffset, (identicalCount + 1).toLong())
                resultOffset += 8; dst.putLong(resultOffset, lastBlock)
            } else if (identicalCount > 0) {
                for (j in 0..identicalCount) {
                    resultOffset += 8; dst.putLong(resultOffset, lastBlock)
                }
            } else {
                ++differentCount
            }
            lastBlock = block
            identicalCount = 0
            sourceOffset += 8
        }

        if (identicalCount > 2) {
            resultOffset += 8; dst.putLong(resultOffset, RLE_HEADER)
            resultOffset += 8; dst.putLong(resultOffset, (identicalCount + 1).toLong())
            resultOffset += 8; dst.putLong(resultOffset, lastBlock)
        } else if (identicalCount > 0) {
            for (j in 0..identicalCount) {
                resultOffset += 8; dst.putLong(resultOffset, lastBlock)
            }
        } else {
            ++differentCount
            System.arraycopy(
                data, data.size - 8 * differentCount,
                result, resultOffset + 8, differentCount * 8
            )
            resultOffset += differentCount * 8
        }

        return result.copyOf(resultOffset + 8)
    }
}
