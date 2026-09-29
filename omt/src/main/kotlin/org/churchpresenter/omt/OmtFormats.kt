package org.churchpresenter.omt

/**
 * The FourCC codes this module sends and receives in.
 *
 * Spelled out rather than computed, because they are `libomt.h`'s `OMTCodec` values — a wire
 * constant, and a sender rejects a frame that carries the wrong one. Only the two 8-bit RGB layouts
 * are listed: the app renders ARGB, and a receiver asks the library for BGRA, so every YUV format a
 * sender puts on the wire is converted before this code sees it.
 */
enum class OmtCodec(val fourCc: Int) {
    /** `BGRA` — 8 bits per channel. Whether the fourth byte means anything is [OmtVideoFrame.alpha]. */
    BGRA(0x41524742),

    /** `BGRX` — what a receiver is handed for a source that sent no alpha. The fourth byte is undefined. */
    BGRX(0x58524742),
    ;

    companion object {
        /** The codec a received frame's FourCC names, or null for one this module does not read. */
        fun ofFourCc(fourCc: Int): OmtCodec? = entries.find { it.fourCc == fourCc }
    }
}

/**
 * What an OMT output puts on the network.
 *
 * Two modes where NDI has three, and the missing one is not an omission: OMT carries alpha inside
 * the one stream, so there is nothing a separate key source would add for a receiver that can read
 * it — and every OMT receiver can.
 */
enum class OmtOutputMode {
    /** Transparency preserved, no background drawn: a lower third arrives already keyed. */
    ALPHA,

    /** The composited picture, alpha flattened away. */
    FILL,
    ;

    /** Whether frames in this mode are flagged as carrying alpha. */
    val carriesAlpha: Boolean get() = this == ALPHA
}

/**
 * The encoding quality a sender is created with — `OMTQuality`.
 *
 * [DEFAULT] is not "medium": it lets every connected receiver suggest a quality and uses the highest
 * suggestion, which is the right answer for an output whose receivers the operator does not control.
 */
enum class OmtQuality(val native: Int) {
    DEFAULT(0),
    LOW(1),
    MEDIUM(50),
    HIGH(100),
}
