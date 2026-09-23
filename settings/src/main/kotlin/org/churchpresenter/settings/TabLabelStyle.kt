package org.churchpresenter.settings

import kotlinx.serialization.Serializable

@Serializable
enum class TabLabelStyle {
    TEXT,
    ICONS_AND_TEXT,
    ICONS,
    ;

    /** The one after this, wrapping from [ICONS] back to [TEXT] -- what one press of the button picks. */
    fun next(): TabLabelStyle = entries[(ordinal + 1) % entries.size]
}
