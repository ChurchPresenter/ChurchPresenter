package org.churchpresenter.signage.core

import java.time.Instant
import java.time.ZoneId

/** What a screen should be doing right now. */
data class ScreenDecision(
    val show: Show,
    val tv: TvPower,
    val reason: Reason,
)

sealed interface Show {
    data class PlaylistShow(val playlistId: String) : Show

    /** The built-in idle card: nothing playable is on the player. */
    data object Idle : Show

    /** Nothing drawn: the TV is meant to be off. */
    data object Blank : Show
}

/**
 * The TV power the player wants. The player sends CEC only when this changes, never repeatedly, and
 * never for [LEAVE].
 */
enum class TvPower { ON, OFF, LEAVE }

enum class Reason { OVERRIDE, SCHEDULE, DEFAULT }

/**
 * Decides what a screen shows: a live manual override first, then the schedule block running now,
 * then the default playlist. A playlist that cannot play falls back to the default playlist, and that
 * to the idle card, so the screen is never black unless it is meant to be off.
 *
 * Pure: the player calls it every second and on every change, and ChurchPresenter's Signage tab calls
 * it to show what each screen should be doing.
 *
 * @param isPlayable whether a playlist can play now; the player passes one that also checks its files
 *   are on disk.
 */
fun decide(
    manifest: Manifest,
    override: ScreenOverride?,
    now: Instant,
    isPlayable: (Playlist) -> Boolean = { it.items.isNotEmpty() },
): ScreenDecision {
    val zone = manifest.zoneOrUtc()

    fun play(playlistId: String?, reason: Reason, tv: TvPower): ScreenDecision {
        val wanted = manifest.playlist(playlistId)?.takeIf(isPlayable)
        val chosen = wanted ?: manifest.playlist(manifest.defaultPlaylistId)?.takeIf(isPlayable)
        val show = chosen?.let { Show.PlaylistShow(it.id) } ?: Show.Idle
        return ScreenDecision(show, tv, reason)
    }

    if (override != null && override.isActive(now, manifest.schedule, zone)) {
        return when (val action = override.action) {
            is OverrideAction.Play -> play(action.playlistId, Reason.OVERRIDE, TvPower.ON)
            OverrideAction.TvOff -> ScreenDecision(Show.Blank, TvPower.OFF, Reason.OVERRIDE)
        }
    }
    return when (val action = manifest.schedule.activeAt(now, zone)?.action) {
        is BlockAction.Play -> play(action.playlistId, Reason.SCHEDULE, TvPower.ON)
        BlockAction.TvOff -> ScreenDecision(Show.Blank, TvPower.OFF, Reason.SCHEDULE)
        BlockAction.Service -> play(manifest.defaultPlaylistId, Reason.SCHEDULE, TvPower.LEAVE)
        null -> play(manifest.defaultPlaylistId, Reason.DEFAULT, TvPower.ON)
    }
}

/** The manifest's zone, or UTC when the id is not one this JVM knows. */
fun Manifest.zoneOrUtc(): ZoneId = runCatching { ZoneId.of(timeZone) }.getOrDefault(ZoneId.of("UTC"))
