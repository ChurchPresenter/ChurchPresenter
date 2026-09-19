package org.churchpresenter.core.models.schedule

import kotlinx.serialization.Serializable

/**
 * How one schedule row runs: when it starts, how long, how many times, and what happens when it
 * ends. Kept beside the list, keyed by row id, rather than on the row -- a song is a song wherever
 * it goes; that it starts on its own at 9:45 and loops is a fact about *this* plan.
 *
 * The all-defaults value is a row the operator cues by hand, that runs its own length once and
 * stays up -- which is every row until someone says otherwise, so a missing entry means exactly
 * that.
 */
@Serializable
data class RowTiming(
    /** `09:45` on the wall clock to start on its own; empty to wait to be cued. */
    val startAt: String = "",
    /** How long it runs, in seconds; null to use the item's own length. */
    val runSeconds: Int? = null,
    /** 1 once, 0 until something else goes live, N that many times. */
    val repeats: Int = 1,
    /** A [RowEnd] constant: what happens when the run is over. */
    val atEnd: String = RowEnd.HOLD,
) {
    fun startsOnItsOwn(): Boolean = startAt.isNotEmpty()
    fun loops(): Boolean = repeats == 0

    /** Whether this says anything a missing entry would not. */
    fun isDefault(): Boolean = this == RowTiming()

    companion object {
        val DEFAULT: RowTiming = RowTiming()
    }
}

/** What a row does when its run is over. Strings so a file written by a later version still opens. */
object RowEnd {
    /** Stays on screen until the operator advances. */
    const val HOLD = "hold"
    /** Moves to the next item on its own. */
    const val NEXT = "next"
    /** Clears the outputs. */
    const val BLANK = "blank"
}
