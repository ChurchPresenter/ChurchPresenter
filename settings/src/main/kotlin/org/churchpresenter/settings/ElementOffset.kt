package org.churchpresenter.settings

import kotlinx.serialization.Serializable

/**
 * Where one element sits in the frame, when it is positioned rather than laid out in the flow.
 *
 * Both axes are an unsigned 0-100 percentage of the room the element has to move through -- the
 * frame's size less its own. **0 is flush against the left or top edge, 50 is dead centre, 100 is
 * flush against the right or bottom.** Because the reference is that room and not the whole output,
 * 100 lands flush whatever size the element turns out to be, and no configured value can push it
 * out of frame: the same property [ContentRegion] documents for its own offsets, and the reason
 * there is no warning to give an operator who drags a slider to either end.
 *
 * Unsigned deliberately. The request this answers asked for negative values to reach a position the
 * old range could not; widening the range to span the frame reaches all of them without asking
 * anyone to work out which way -3 moves a label.
 *
 * **Nullable at every use site, defaulting to null**, which means "not positioned -- lay this out
 * exactly as it always was". That matters more than it looks: an element's present position is a
 * function of settings the operator can already change (a song's `lyricsAlignment`, a Bible
 * element's `horizontalAlignment`), and three of the four elements that take one of these have no
 * room at all on one axis today, being drawn `fillMaxWidth()` or as a `Column` child. There is
 * therefore no pair of numbers that reproduces every existing layout, and any non-null default
 * would quietly move somebody's screen on upgrade. Null cannot.
 */
@Serializable
data class ElementOffset(
    val xPercent: Int = CENTRE,
    val yPercent: Int = CENTRE,
) {
    companion object {
        /** Dead centre of the frame, on both axes -- where turning positioning on starts. */
        const val CENTRE = 50

        val PERCENT_RANGE = 0..100
    }
}
