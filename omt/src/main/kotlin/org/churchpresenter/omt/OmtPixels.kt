package org.churchpresenter.omt

internal const val BYTES_PER_PIXEL = 4
internal const val ALPHA_SHIFT = 24
internal const val RED_SHIFT = 16
internal const val GREEN_SHIFT = 8
internal const val BYTE_MASK = 0xFF
internal const val OPAQUE_ALPHA_BYTE = 0xFF.toByte()

/** Offsets of the four channels within one BGRA pixel. */
private const val BLUE_BYTE = 0
private const val GREEN_BYTE = 1
private const val RED_BYTE = 2
private const val ALPHA_BYTE = 3

/** Bytes one packed row of [width] BGRA pixels occupies. */
fun lineStrideBytes(width: Int): Int = width * BYTES_PER_PIXEL

/** Bytes a packed [width] x [height] BGRA frame occupies. */
fun frameSizeBytes(width: Int, height: Int): Int = lineStrideBytes(width) * height

/**
 * Packed ARGB ints — what Compose's `readPixels` produces — into BGRA bytes, written into [out]
 * rather than allocated, because this runs per frame and a fresh 1080p array is 8.3 MB of garbage
 * thirty times a second.
 *
 * [out] must hold at least `argb.size * 4` bytes. When [opaque] is set the alpha byte is written as
 * 0xFF, so the bytes on the wire say what the frame's flags do rather than relying on the codec to
 * ignore them.
 */
fun argbToBgra(argb: IntArray, out: ByteArray, opaque: Boolean) {
    for (i in argb.indices) {
        val pixel = argb[i]
        val off = i * BYTES_PER_PIXEL
        out[off + BLUE_BYTE] = (pixel and BYTE_MASK).toByte()
        out[off + GREEN_BYTE] = ((pixel shr GREEN_SHIFT) and BYTE_MASK).toByte()
        out[off + RED_BYTE] = ((pixel shr RED_SHIFT) and BYTE_MASK).toByte()
        out[off + ALPHA_BYTE] =
            if (opaque) OPAQUE_ALPHA_BYTE else ((pixel shr ALPHA_SHIFT) and BYTE_MASK).toByte()
    }
}

/**
 * BGRA bytes back into packed ARGB — the inverse of [argbToBgra].
 *
 * Reads the first [count] pixels of [bgra] into [out], which may be longer. When [opaque] is set the
 * alpha byte is ignored and 0xFF written instead: a frame sent without alpha has an undefined fourth
 * byte, and trusting it turns an ordinary camera feed into an invisible layer.
 */
fun bgraToArgb(bgra: ByteArray, out: IntArray, count: Int, opaque: Boolean) {
    for (i in 0 until count) {
        val off = i * BYTES_PER_PIXEL
        val alpha = if (opaque) BYTE_MASK else bgra[off + ALPHA_BYTE].toInt() and BYTE_MASK
        out[i] = (alpha shl ALPHA_SHIFT) or
            ((bgra[off + RED_BYTE].toInt() and BYTE_MASK) shl RED_SHIFT) or
            ((bgra[off + GREEN_BYTE].toInt() and BYTE_MASK) shl GREEN_SHIFT) or
            (bgra[off + BLUE_BYTE].toInt() and BYTE_MASK)
    }
}
