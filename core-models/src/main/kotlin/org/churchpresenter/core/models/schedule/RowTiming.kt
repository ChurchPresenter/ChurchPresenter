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
    /**
     * True when this row goes live as the row before it finishes -- its turn in the order.
     *
     * The third way a row can start, beside a clock time and waiting to be cued. It says on the
     * row itself what used to be implied only by the *previous* row's [RowEnd.NEXT]: a reader of
     * the plan could not tell a row waiting for its turn from one the operator drives by hand,
     * and neither could the run of show's clock column.
     */
    val followsPrevious: Boolean = false,
    /** How long it runs, in seconds; null to use the item's own length. */
    val runSeconds: Int? = null,
    /** 1 once, 0 until something else goes live, N that many times. */
    val repeats: Int = 1,
    /** A [RowEnd] constant: what happens when the run is over. */
    val atEnd: String = RowEnd.HOLD,
    /**
     * Seconds spent off screen just before this row -- a poem, a solo, a prayer -- that the plan
     * counts and the Schedule never sees, because [ScheduleItem.MinistryItem]s are not loaded.
     * Written in as a service is loaded, so the Schedule's clock column agrees with the calendar's.
     */
    val leadSeconds: Int = 0,
) {
    fun startsOnItsOwn(): Boolean = startAt.isNotEmpty()

    /** Whether the row runs itself somehow -- on the clock, or as its turn comes. */
    fun startsWithoutCue(): Boolean = startsOnItsOwn() || followsPrevious
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
