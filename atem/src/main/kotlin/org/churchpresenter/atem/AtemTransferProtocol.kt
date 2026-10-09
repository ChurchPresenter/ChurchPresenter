package org.churchpresenter.atem

import java.nio.ByteBuffer

// The media-pool transfer protocol's own rules: how much of the frame data the next granted chunk
// carries, and what a rejected transfer is reported as.

private const val RLE_WORD_BYTES = 8

private const val RLE_TWO_WORDS_BYTES = 16

/**
 * How much to put in the next chunk: never ending mid RLE block, so the length is shortened
 * when an RLE header starts 8 or 16 bytes before the chunk end (header+count+pattern = 24B unit).
 */
internal fun AtemClient.chunkLengthAt(
    dataBuf: ByteBuffer,
    dataSize: Int,
    bytesSent: Int,
    chunkSize: Int,
): Int {
    val len = minOf(chunkSize, dataSize - bytesSent)
    if (bytesSent + len >= dataSize) return len
    val endsOnHeader = { back: Int ->
        len >= back && dataBuf.getLong(bytesSent + len - back) == AtemFrameEncoder.RLE_HEADER
    }
    return when {
        endsOnHeader(RLE_WORD_BYTES) -> len - RLE_WORD_BYTES
        endsOnHeader(RLE_TWO_WORDS_BYTES) -> len - RLE_TWO_WORDS_BYTES
        else -> len
    }
}

/**
 * The failure for an FTDE the transfer cannot retry past. Clip frames after index 0 usually
 * fail because the device's clip pool ran out of capacity, which is worth saying outright.
 */
internal fun AtemClient.transferRejected(
    code: Int,
    name: String?,
    frameIndex: Int,
    retries: Int,
): AtemProtocolException {
    val what = if (name == null) "clip frame $frameIndex" else "still"
    val hint = if (name == null && frameIndex > 0) {
        " — the clip may exceed the ATEM's clip pool capacity; try a shorter duration or lower fps"
    } else {
        ""
    }
    return if (code == FTDE_CODE_RETRY) {
        AtemProtocolException("ATEM stayed busy uploading $what after $retries retries$hint")
    } else {
        AtemProtocolException("ATEM rejected $what (error code $code)$hint")
    }
}
