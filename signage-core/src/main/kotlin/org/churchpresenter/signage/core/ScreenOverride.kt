package org.churchpresenter.signage.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

/**
 * A manual switch made in ChurchPresenter's Signage tab. The player stores it, so it holds after the
 * PC shuts down, and it beats the schedule until it ends.
 */
@Serializable
data class ScreenOverride(
    val action: OverrideAction,
    val until: Until = Until.NextChange,
    /** When it was made, in epoch milliseconds; [Until.NextChange] counts from here. */
    val setAtMs: Long,
) {
    /** Whether it still applies at [now] under [schedule]. */
    fun isActive(now: Instant, schedule: List<ScheduleBlock>, zone: ZoneId): Boolean = when (until) {
        Until.NextChange -> {
            val change = schedule.nextChangeAfter(Instant.ofEpochMilli(setAtMs), zone)
            change == null || now < change
        }
        is Until.Time -> now.toEpochMilli() < until.epochMs
        Until.Released -> true
    }
}

@Serializable
sealed interface OverrideAction {
    @Serializable
    @SerialName("play")
    data class Play(val playlistId: String) : OverrideAction

    @Serializable
    @SerialName("tv_off")
    data object TvOff : OverrideAction
}

@Serializable
sealed interface Until {
    /** Until the schedule next starts or ends a block. With no schedule, until released. */
    @Serializable
    @SerialName("next_change")
    data object NextChange : Until

    @Serializable
    @SerialName("time")
    data class Time(val epochMs: Long) : Until

    /** Until someone releases it in ChurchPresenter. */
    @Serializable
    @SerialName("released")
    data object Released : Until
}
