package org.churchpresenter.settings

import kotlinx.serialization.Serializable

/**
 * Walks the song number inward from the corner [SongSettings.songNumberCorner] pins it to.
 *
 * [xPercent]/[yPercent] are an unsigned 0-100: a percentage of the full room between the corner and
 * the opposite edge of the box the number sits in -- 0 is flush against the corner (today's
 * default), 100 is flush against the far side. Which edge is "opposite," on both axes, is decided
 * by the corner itself at render time, so there is no sign to get backwards: raising either number
 * always walks the number further into the frame. See `Modifier.songNumberCornerOffset` in
 * `:composeApp`'s `presenter/ContentRegionModifier.kt`.
 */
@Serializable
data class SongNumberOffset(
    val xPercent: Int = 0,
    val yPercent: Int = 0,
) {
    companion object {
        val PERCENT_RANGE = 0..100
    }
}
