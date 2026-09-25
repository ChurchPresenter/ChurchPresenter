package org.churchpresenter.settings

import kotlinx.serialization.Serializable

/**
 * One element's position on each of the two outputs, or null on either while it stays in the flow.
 *
 * A pair rather than one value for the reason [SongTitleSlideNumber] keeps two of everything: a
 * title slide is laid out quite differently in a band than on a whole screen, and an offset that
 * reads well in one is rarely the one the other wants.
 */
@Serializable
data class OutputElementOffset(
    val fullScreen: ElementOffset? = null,
    val lowerThird: ElementOffset? = null,
) {
    fun forOutput(isLowerThird: Boolean): ElementOffset? = if (isLowerThird) lowerThird else fullScreen

    fun withOutput(isLowerThird: Boolean, value: ElementOffset?): OutputElementOffset =
        if (isLowerThird) copy(lowerThird = value) else copy(fullScreen = value)
}

/**
 * Where each of the title slide's elements sits, once an operator positions it rather than leaving
 * it in the stack.
 *
 * The song number is deliberately absent: it already has a placement of its own, and a richer one --
 * a corner plus a nudge inward from it, on [SongTitleSlideNumber]. The other five had nothing, so a
 * title slide could be styled freely and laid out only one way, top to bottom in a centred column.
 *
 * [tempo] is here although the request that asked for these listed only the first four. It is drawn
 * by the same loop, from the same kind of record, under the same heading in settings; leaving it out
 * would have been one credit line in five that could not be moved, for no reason an operator could
 * see.
 *
 * Nested in [SongLayoutExtras] for the reason [SongOutlines] and [SongTitleSlideNumber] are: as flat
 * fields this is ten more properties on a constructor that has none to spare.
 */
@Serializable
data class SongTitleSlideOffsets(
    val title: OutputElementOffset = OutputElementOffset(),
    val author: OutputElementOffset = OutputElementOffset(),
    val composer: OutputElementOffset = OutputElementOffset(),
    val ccli: OutputElementOffset = OutputElementOffset(),
    val tempo: OutputElementOffset = OutputElementOffset(),
)
