package org.churchpresenter.settings

import kotlinx.serialization.Serializable

/** How much room each tab in a tab strip gets around its label -- see [TabLabelStyle]. */
@Serializable
enum class TabLabelMargin {
    SMALL,
    SMALL_NORMAL,
    NORMAL,
    NORMAL_LARGE,
    LARGE,
    ;

    /** The one after this, wrapping from [LARGE] back to [SMALL] -- what one press of the button picks. */
    fun next(): TabLabelMargin = entries[(ordinal + 1) % entries.size]
}
