package org.churchpresenter.calendar.model

import java.time.LocalTime

/** A run-of-show row's clock time, and whether it can be trusted. */
data class RowClock(
    /** The wall-clock time; the caller formats it, as the clock format is a preference. */
    val time: LocalTime,
    /**
     * False once some earlier row has no planned length.
     *
     * The clock still advances — a plan with three of its ten rows estimated is still worth showing
     * times for — but the caller draws an approximate time dimmed, so nobody reads it as a promise.
     */
    val exact: Boolean,
)
