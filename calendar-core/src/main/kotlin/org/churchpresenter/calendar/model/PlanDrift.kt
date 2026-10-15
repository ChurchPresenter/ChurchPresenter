package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.RowTiming
import java.time.LocalTime

/**
 * How far a running schedule is from its plan: [seconds] is positive when the service is behind,
 * negative when it is ahead.
 *
 * [exact] is the live row's own clock's -- false once a row of unknown length has been passed,
 * which makes the number a guess and the caller draws it as one.
 */
data class PlanDrift(val seconds: Int, val exact: Boolean) {
    fun isBehind(): Boolean = seconds > 0
    fun isAhead(): Boolean = seconds < 0
}

/**
 * The drift of the row that is live, or null when nothing can say -- the row is not one the plan
 * has a time for, or the schedule has no pinned row to reckon from and [clocks] is empty.
 *
 * Two things add up. The row went live at [liveSince] where the plan said [clocks] -- a row that
 * started three minutes late is three minutes behind, and stays so while it runs its planned
 * length. Once [now] is past the end of that length the row is *overrunning*, and the drift grows
 * with the clock: a row planned for five minutes that has been up for eight is three behind even
 * if it started on time. The two agree at the boundary, so the number never jumps.
 *
 * A row with no planned length can only say how late it started.
 */
fun planDrift(
    liveRowId: String,
    liveSince: LocalTime,
    now: LocalTime,
    clocks: Map<String, RowClock>,
    timing: Map<String, RowTiming>,
): PlanDrift? {
    val planned = clocks[liveRowId] ?: return null
    val startDrift = secondsBetween(planned.time, liveSince)
    val plan = timing[liveRowId] ?: RowTiming.DEFAULT
    val runs = plan.runSeconds?.let { it * plan.repeats.coerceAtLeast(1) }
    val drift = if (runs == null) {
        startDrift
    } else {
        maxOf(startDrift, secondsBetween(planned.time.plusSeconds(runs.toLong()), now))
    }
    return PlanDrift(drift, planned.exact)
}

/**
 * [to] minus [from] on a 24-hour clock, taken the short way round, so a service that runs across
 * midnight is still one service rather than a day behind.
 */
internal fun secondsBetween(from: LocalTime, to: LocalTime): Int {
    val raw = to.toSecondOfDay() - from.toSecondOfDay()
    return when {
        raw > HALF_DAY_SECONDS -> raw - DAY_SECONDS
        raw < -HALF_DAY_SECONDS -> raw + DAY_SECONDS
        else -> raw
    }
}

private const val DAY_SECONDS = 86_400
private const val HALF_DAY_SECONDS = DAY_SECONDS / 2
