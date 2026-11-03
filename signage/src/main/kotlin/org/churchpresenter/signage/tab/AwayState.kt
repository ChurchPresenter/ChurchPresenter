package org.churchpresenter.signage.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.signage_age_days
import org.churchpresenter.strings.generated.resources.signage_age_hours
import org.churchpresenter.strings.generated.resources.signage_age_minutes
import org.churchpresenter.strings.generated.resources.signage_age_now
import org.churchpresenter.strings.generated.resources.signage_last_seen
import org.churchpresenter.strings.generated.resources.signage_last_seen_at
import org.churchpresenter.strings.generated.resources.signage_looking
import org.churchpresenter.strings.generated.resources.signage_offline
import org.jetbrains.compose.resources.stringResource

/** What a screen that is not connected is doing, as its card says it. */
internal enum class AwayState {
    /** ChurchPresenter has just started looking (or the screen just left): it is expected back soon. */
    LOOKING,

    /** It has been away longer than a screen takes to come back: something needs checking. */
    WAITING,
}

/**
 * [row]'s state while it is not connected, at [nowMs]; null while it is. A screen gets
 * [LOOKING_FOR_MS] from when ChurchPresenter began looking ([ScreenRowState.lookingSinceMs]) or from
 * when it went away, whichever is later, before it counts as waiting.
 */
internal fun awayState(row: ScreenRowState, nowMs: Long): AwayState? {
    if (row.link != null) return null
    val since = maxOf(row.lookingSinceMs, row.screen.lastSeenAtMs ?: 0L)
    return if (nowMs - since < LOOKING_FOR_MS) AwayState.LOOKING else AwayState.WAITING
}

/** The time now, read again every [tickMs], for lines that say how long ago something was. */
@Composable
internal fun rememberNow(tickMs: Long = NOW_TICK_MS): Long {
    val now by produceState(System.currentTimeMillis(), tickMs) {
        while (true) {
            delay(tickMs)
            value = System.currentTimeMillis()
        }
    }
    return now
}

/** The connection line of a card whose screen is [state]: looking for it, or away since when and where. */
@Composable
internal fun awayLine(row: ScreenRowState, state: AwayState, nowMs: Long): String {
    if (state == AwayState.LOOKING) return stringResource(Res.string.signage_looking)
    val offline = stringResource(Res.string.signage_offline)
    val seenAt = row.screen.lastSeenAtMs ?: return offline
    val age = ageText(nowMs - seenAt)
    val seen = row.screen.lastAddress?.let { stringResource(Res.string.signage_last_seen_at, age, it) }
        ?: stringResource(Res.string.signage_last_seen, age)
    return offline + SEPARATOR + seen
}

/** [ms] ago in the largest whole unit: just now, minutes, hours or days. */
@Composable
internal fun ageText(ms: Long): String {
    val minutes = ms / MS_PER_MINUTE
    return when {
        minutes < 1 -> stringResource(Res.string.signage_age_now)
        minutes < MINUTES_PER_HOUR -> stringResource(Res.string.signage_age_minutes, minutes.toInt())
        minutes < MINUTES_PER_DAY -> stringResource(Res.string.signage_age_hours, (minutes / MINUTES_PER_HOUR).toInt())
        else -> stringResource(Res.string.signage_age_days, (minutes / MINUTES_PER_DAY).toInt())
    }
}

/** How long a screen may be away before its card says to check it: past the player's longest retry. */
internal const val LOOKING_FOR_MS = 2L * 60_000L
private const val NOW_TICK_MS = 5_000L
private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 24L * 60L
