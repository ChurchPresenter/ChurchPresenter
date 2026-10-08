package org.churchpresenter.settings

import kotlinx.serialization.Serializable

/**
 * A prop: a persistent overlay that sits over the slide and the lower third and stays up while the
 * content changes under it (`docs/SHOW_CONTROL.md`, Props). Defined here; switching one on or off
 * is a live action, not a settings change.
 */
@Serializable
data class PropDefinition(
    val id: String,
    val name: String,
    val kind: PropKind = PropKind.IMAGE,
    val corner: PropCorner = PropCorner.TOP_RIGHT,
    /** Its height, in percent of the output's height. */
    val sizePercent: Int = DEFAULT_PROP_SIZE_PERCENT,
    /** The picture an [PropKind.IMAGE] prop shows. */
    val imagePath: String = "",
    /** The words a [PropKind.BADGE] shows, e.g. "LIVE". */
    val text: String = "",
    /** The time of day, "HH:mm", a [PropKind.COUNTDOWN] counts down to. */
    val countdownTo: String = "",
)

/** What a prop draws. */
@Serializable
enum class PropKind {
    /** A picture, e.g. the church's logo bug. */
    IMAGE,

    /** The time of day, in the announcements' clock format. */
    CLOCK,

    /** The time left until [PropDefinition.countdownTo]. */
    COUNTDOWN,

    /** A short word on a coloured plate, e.g. "LIVE". */
    BADGE,
}

/** Which corner of the output a prop sits in. */
@Serializable
enum class PropCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

const val DEFAULT_PROP_SIZE_PERCENT = 8
