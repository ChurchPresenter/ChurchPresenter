package org.churchpresenter.app.churchpresenter.utils

/**
 * The schedule card density ladder: five fixed rungs rather than a continuous zoom -- Compact,
 * Normal and Detailed, plus one step smaller and one step larger than those. The two outer rungs
 * reuse their neighbour's name in the UI: they change how much room a card takes, not what it
 * shows,
 so a fourth and fifth label would name a distinction that is not there. The persisted setting stays a plain `Int` (`AppSettings.scheduleItemZoomPercent`,
 * pre-dating this ladder) so a value saved under the old 11-rung 70-150 scheme still resolves to the
 * nearest rung here. Extracted from ScheduleTab so the rung math is tested without the Compose
 * controls.
 */

internal enum class ScheduleDensity(val percent: Int) {
    EXTRA_COMPACT(55),
    COMPACT(70),
    NORMAL(100),
    DETAILED(150),
    EXTRA_DETAILED(200),
}

/**
 * The rung nearest [percent], so a value persisted before or after this ladder still resolves.
 *
 * The three middle rungs keep the bands the old 70-150 scheme used, so nothing anyone has saved
 * moves; the two outer rungs are only reachable by stepping past the ends with the +/- controls.
 */
internal fun scheduleDensityFor(percent: Int): ScheduleDensity = when {
    percent <= 60 -> ScheduleDensity.EXTRA_COMPACT
    percent <= 90 -> ScheduleDensity.COMPACT
    percent < 120 -> ScheduleDensity.NORMAL
    percent <= 175 -> ScheduleDensity.DETAILED
    else -> ScheduleDensity.EXTRA_DETAILED
}

/** The next rung up from [percent] (clamped at Detailed). */
internal fun scheduleZoomIn(percent: Int): Int {
    val density = scheduleDensityFor(percent)
    val next = ScheduleDensity.entries.getOrElse(density.ordinal + 1) { density }
    return next.percent
}

/** The next rung down from [percent] (clamped at Compact). */
internal fun scheduleZoomOut(percent: Int): Int {
    val density = scheduleDensityFor(percent)
    val prev = ScheduleDensity.entries.getOrElse(density.ordinal - 1) { density }
    return prev.percent
}

/** Whether there is a higher rung to move into. */
internal fun scheduleCanZoomIn(percent: Int): Boolean =
    scheduleDensityFor(percent) != ScheduleDensity.entries.last()

/** Whether there is a lower rung to move out to. */
internal fun scheduleCanZoomOut(percent: Int): Boolean =
    scheduleDensityFor(percent) != ScheduleDensity.entries.first()

/** The two smallest rungs show the title alone; the rest add the secondary (grey) detail line. */
internal fun scheduleShowDetailLine(percent: Int): Boolean = when (scheduleDensityFor(percent)) {
    ScheduleDensity.EXTRA_COMPACT, ScheduleDensity.COMPACT -> false
    else -> true
}

/** Only the two largest rungs show the type-kind chip and (when present) the file path. */
internal fun scheduleShowKindDetails(percent: Int): Boolean = when (scheduleDensityFor(percent)) {
    ScheduleDensity.DETAILED, ScheduleDensity.EXTRA_DETAILED -> true
    else -> false
}
