package org.churchpresenter.signage.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Serializable
enum class Day(val dayOfWeek: DayOfWeek) {
    @SerialName("mon") MON(DayOfWeek.MONDAY),

    @SerialName("tue") TUE(DayOfWeek.TUESDAY),

    @SerialName("wed") WED(DayOfWeek.WEDNESDAY),

    @SerialName("thu") THU(DayOfWeek.THURSDAY),

    @SerialName("fri") FRI(DayOfWeek.FRIDAY),

    @SerialName("sat") SAT(DayOfWeek.SATURDAY),

    @SerialName("sun") SUN(DayOfWeek.SUNDAY),
    ;

    companion object {
        fun of(dayOfWeek: DayOfWeek): Day = entries.first { it.dayOfWeek == dayOfWeek }
    }
}

/**
 * One weekly block: on each of [days], from [start] until [end]. An [end] at or before [start] runs
 * past midnight into the next day, so `22:00–06:00` is overnight and `00:00–00:00` is the whole day.
 * [days] are the days the block *starts* on.
 */
@Serializable
data class ScheduleBlock(
    val id: String,
    val days: Set<Day>,
    val start: ClockTime,
    val end: ClockTime,
    val action: BlockAction,
) {
    val crossesMidnight: Boolean get() = end <= start

    fun covers(at: LocalDateTime): Boolean {
        val day = Day.of(at.dayOfWeek)
        val time = ClockTime.of(at.hour, at.minute)
        return if (!crossesMidnight) {
            day in days && time >= start && time < end
        } else {
            (day in days && time >= start) || (Day.of(at.dayOfWeek.minus(1)) in days && time < end)
        }
    }

    /** The minutes of the week (0 = Monday 00:00) this block covers. */
    internal fun minutesOfWeek(): Set<Int> = buildSet {
        val length = if (crossesMidnight) {
            ClockTime.MINUTES_PER_DAY - start.minuteOfDay + end.minuteOfDay
        } else {
            end.minuteOfDay - start.minuteOfDay
        }
        days.forEach { day ->
            val first = day.ordinal * ClockTime.MINUTES_PER_DAY + start.minuteOfDay
            for (offset in 0 until length) add((first + offset) % MINUTES_PER_WEEK)
        }
    }

    companion object {
        const val MINUTES_PER_WEEK = 7 * ClockTime.MINUTES_PER_DAY
    }
}

/** What a schedule block does while it runs. */
@Serializable
sealed interface BlockAction {
    /** Plays a playlist; turns the TV on when the block starts. */
    @Serializable
    @SerialName("play")
    data class Play(val playlistId: String) : BlockAction

    /** Turns the TV off and stops drawing. */
    @Serializable
    @SerialName("tv_off")
    data object TvOff : BlockAction

    /**
     * Service hours: the TV is on another HDMI input. The player keeps playing the default playlist
     * but sends nothing over CEC, so it never pulls the TV off the service input.
     */
    @Serializable
    @SerialName("service")
    data object Service : BlockAction
}

/** The block running at [now], if any. Blocks are not meant to overlap; the first listed wins. */
fun List<ScheduleBlock>.activeAt(now: Instant, zone: ZoneId): ScheduleBlock? {
    val local = LocalDateTime.ofInstant(now, zone)
    return firstOrNull { it.covers(local) }
}

/**
 * The first moment strictly after [after] at which some block starts or ends, or null when the
 * schedule is empty. A wall-clock time that falls in a daylight-saving gap moves forward by the gap.
 */
fun List<ScheduleBlock>.nextChangeAfter(after: Instant, zone: ZoneId): Instant? {
    val today = LocalDate.ofInstant(after, zone)
    return (-1L..LOOKAHEAD_DAYS).asSequence()
        .map(today::plusDays)
        .flatMap { date -> asSequence().flatMap { block -> block.edgesOn(date, zone) } }
        .filter { it > after }
        .minOrNull()
}

/** When this block starts and ends if it starts on [date]; nothing when it does not run that day. */
private fun ScheduleBlock.edgesOn(date: LocalDate, zone: ZoneId): Sequence<Instant> {
    if (Day.of(date.dayOfWeek) !in days) return emptySequence()
    val endDate = if (crossesMidnight) date.plusDays(1) else date
    return sequenceOf(date.atTime(start.toLocalTime()), endDate.atTime(end.toLocalTime()))
        .map { it.atZone(zone).toInstant() }
}

/** Pairs of block ids that cover the same minute of the week. The editor refuses these. */
fun List<ScheduleBlock>.overlaps(): List<Pair<String, String>> = buildList {
    val covered = this@overlaps.map { it to it.minutesOfWeek() }
    for (i in covered.indices) {
        for (j in i + 1 until covered.size) {
            val (a, aMinutes) = covered[i]
            val (b, bMinutes) = covered[j]
            if (aMinutes.any { it in bMinutes }) add(a.id to b.id)
        }
    }
}

private const val LOOKAHEAD_DAYS = 8L
