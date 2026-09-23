package org.churchpresenter.settings

import kotlinx.serialization.Serializable

/**
 * How a picture or video meets the output when their shapes differ -- a 4:3 source on a 16:9
 * screen. The names are the ones a Canvas source's scale is saved under.
 */
@Serializable
enum class OutputScaleMode {
    /** The whole picture, with bars where it does not reach. */
    FIT,

    /** Covers the output without distorting, cropping whatever overhangs. */
    FILL,

    /** Covers the output by distorting the picture to its shape. */
    STRETCH,
    ;

    /** The one after this, wrapping from [STRETCH] back to [FIT] -- what one press of the button picks. */
    fun next(): OutputScaleMode = entries[(ordinal + 1) % entries.size]
}
