package org.churchpresenter.ndi

/**
 * The NDI FourCC codes this module sends and receives in.
 *
 * The values are the SDK's own `NDI_LIB_FOURCC(ch0, ch1, ch2, ch3)` macro — `ch0 or (ch1 shl 8) or
 * (ch2 shl 16) or (ch3 shl 24)` — spelled out rather than computed, because they are a wire
 * constant and a receiver rejects a frame that carries the wrong one.
 */
enum class NdiPixelFormat(val fourCc: Int) {
    /** `BGRA` — 8 bits per channel with a real alpha channel a receiver keys on directly. */
    BGRA(0x41524742),

    /** `BGRX` — the same layout with the fourth byte ignored, i.e. an explicitly opaque frame. */
    BGRX(0x58524742),
    ;

    companion object {
        /**
         * The format a received frame's FourCC names, or null for anything else.
         *
         * Only these two can arrive: a receiver asks for `BGRX_BGRA`, so the runtime converts
         * whatever the sender actually put on the wire — including every YUV format — into one of
         * them before this code sees it. Null therefore means a runtime that answered something it
         * was not asked for, which is a frame to drop rather than one to guess at.
         */
        fun ofFourCc(fourCc: Int): NdiPixelFormat? = entries.find { it.fourCc == fourCc }
    }
}

/**
 * What an NDI output puts on the network.
 *
 * [ALPHA] is the mode worth leading with, and the reason NDI is more than "DeckLink without a
 * card": one sender carrying genuine per-pixel transparency, so OBS or a switcher receives a
 * correctly keyed layer with no second sender and no downstream keyer. SDI physically cannot do
 * that — hence the other two, which exist for gear that expects the separate signals.
 */
enum class NdiOutputMode {
    /** One BGRA sender, transparency preserved, no background drawn. */
    ALPHA,

    /** One BGRX sender: the composited picture, alpha flattened away. */
    FILL,

    /** Two BGRX senders — the picture, and a luminance key beside it under a " Key" name suffix. */
    FILL_AND_KEY,
    ;

    /** The pixel format a sender in this mode is fed. Only [ALPHA] carries alpha to the receiver. */
    val pixelFormat: NdiPixelFormat get() = if (this == ALPHA) NdiPixelFormat.BGRA else NdiPixelFormat.BGRX

    /** Whether this mode puts a second, key-signal sender on the network beside the fill. */
    val hasKeySender: Boolean get() = this == FILL_AND_KEY
}
