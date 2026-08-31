package org.churchpresenter.ndi

internal const val BYTES_PER_PIXEL = 4
internal const val ALPHA_SHIFT = 24
internal const val RED_SHIFT = 16
internal const val GREEN_SHIFT = 8
internal const val BYTE_MASK = 0xFF
internal const val OPAQUE_ALPHA_BYTE = 0xFF.toByte()

/** Offsets of the four channels within one pixel, in the order NDI reads them. */
private const val BLUE_BYTE = 0
private const val GREEN_BYTE = 1
private const val RED_BYTE = 2
private const val ALPHA_BYTE = 3

/** Bytes one row of [width] pixels occupies in any of this module's formats. */
fun lineStrideBytes(width: Int): Int = width * BYTES_PER_PIXEL

/** Bytes a [width] x [height] frame occupies. */
fun frameSizeBytes(width: Int, height: Int): Int = lineStrideBytes(width) * height

/**
 * Converts packed ARGB ints — what Compose's `readPixels` produces, and what every other output in
 * this app passes around — into the byte order NDI wants, writing into [out] rather than allocating.
 *
 * [out] must hold at least `argb.size * 4` bytes. When [opaque] is set the alpha byte is written as
 * 0xFF, which is the difference between the BGRX modes and the alpha one. A receiver told the frame
 * is BGRX is entitled to ignore that byte, so this is belt and braces rather than a requirement —
 * but it costs one store per pixel and it means the bytes on the wire say the same thing as the
 * FourCC does, instead of relying on every receiver to agree about which one wins.
 *
 * Reused rather than reallocated because this runs per frame: at 1080p a fresh array would be 8.3 MB
 * of garbage 30 times a second, which is the allocation profile the Browser Source renderer was
 * explicitly fixed to avoid.
 */
fun argbToNdiBytes(argb: IntArray, out: ByteArray, opaque: Boolean) {
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
 * Converts the byte order NDI delivers back into packed ARGB — the inverse of [argbToNdiBytes], and
 * what every renderer in this app draws.
 *
 * Reads the first [count] pixels of [bgra] into [out], which must hold at least that many ints and
 * may be longer: the receiver keeps one buffer sized to the largest frame it has seen rather than
 * reallocating when a source changes resolution. When [opaque] is set the alpha byte is ignored and
 * 0xFF written instead, which is what a `BGRX` frame means — its fourth byte is undefined, and
 * trusting it turns an ordinary camera feed into an invisible layer.
 */
fun ndiBytesToArgb(bgra: ByteArray, out: IntArray, count: Int, opaque: Boolean) {
    for (i in 0 until count) {
        val off = i * BYTES_PER_PIXEL
        val alpha = if (opaque) BYTE_MASK else bgra[off + ALPHA_BYTE].toInt() and BYTE_MASK
        out[i] = (alpha shl ALPHA_SHIFT) or
            ((bgra[off + RED_BYTE].toInt() and BYTE_MASK) shl RED_SHIFT) or
            ((bgra[off + GREEN_BYTE].toInt() and BYTE_MASK) shl GREEN_SHIFT) or
            (bgra[off + BLUE_BYTE].toInt() and BYTE_MASK)
    }
}
