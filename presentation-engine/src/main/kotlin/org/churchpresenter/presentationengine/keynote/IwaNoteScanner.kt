package org.churchpresenter.presentationengine.keynote

/**
 * Heuristic scan of a Slide iwa payload for the presenter-notes text field (protobuf field tag
 * bytes 0xB2 0x38 followed by a varint length). Replaced by a real IWA parse in WS5; kept as the
 * fallback for undecodable documents.
 */
internal object IwaNoteScanner {

    /** The presenter-notes field tag, and the varint decoding that follows it. */
    private const val NOTE_TAG_BYTE_0 = 0xB2
    private const val NOTE_TAG_BYTE_1 = 0x38
    private const val NOTE_TAG_BYTES = 3
    private const val BYTE_MASK = 0xFF
    private const val VARINT_PAYLOAD_MASK = 0x7F
    private const val VARINT_CONTINUATION_BIT = 0x80
    private const val VARINT_PAYLOAD_BITS = 7

    /** Longer than any real note: past it a match is noise in the binary, not a note. */
    private const val MAX_NOTE_BYTES = 4096

    /** Every note-looking string in [bytes], one per line. */
    fun scan(bytes: ByteArray): String =
        (0 until bytes.size - NOTE_TAG_BYTES).asSequence()
            .filter { i ->
                (bytes[i].toInt() and BYTE_MASK) == NOTE_TAG_BYTE_0 &&
                    (bytes[i + 1].toInt() and BYTE_MASK) == NOTE_TAG_BYTE_1
            }
            .mapNotNull { i -> noteAt(bytes, i + 2) }
            .joinToString("\n")
            .trim()

    /**
     * The length-prefixed string whose varint length starts at [start], or null when the length is
     * out of range or runs past the end. The varint is decoded into an `Int` exactly as it always
     * was, so the same noise is accepted or rejected as before.
     */
    private fun noteAt(bytes: ByteArray, start: Int): String? {
        var length = 0
        var shift = 0
        var j = start
        while (j < bytes.size) {
            val b = bytes[j].toInt() and BYTE_MASK
            length = length or ((b and VARINT_PAYLOAD_MASK) shl shift)
            j++
            if (b and VARINT_CONTINUATION_BIT == 0) break
            shift += VARINT_PAYLOAD_BITS
        }
        if (length !in 1..MAX_NOTE_BYTES || j + length > bytes.size) return null
        return runCatching { String(bytes, j, length, Charsets.UTF_8) }.getOrNull()
    }
}
