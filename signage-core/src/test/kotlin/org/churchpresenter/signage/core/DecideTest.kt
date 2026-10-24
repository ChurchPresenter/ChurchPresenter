package org.churchpresenter.signage.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class DecideTest {
    private val zone = ZoneId.of("America/Chicago")
    private fun instant(text: String): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()

    private fun block(id: String, days: Set<Day>, start: String, end: String, action: BlockAction) =
        ScheduleBlock(id, days, ClockTime.parse(start), ClockTime.parse(end), action)

    private val item = PlaylistItem(sha256 = "a".repeat(64), kind = MediaKind.IMAGE, durationMs = 5_000)
    private val manifest = Manifest(
        ownerId = "owner",
        screen = ScreenSettings("Lobby"),
        playlists = listOf(
            Playlist("welcome", "Welcome", listOf(item)),
            Playlist("events", "Events", listOf(item)),
            Playlist("empty", "Empty"),
        ),
        schedule = listOf(
            block("night", Day.entries.toSet(), "22:00", "06:00", BlockAction.TvOff),
            block("sunday", setOf(Day.SUN), "09:30", "12:30", BlockAction.Service),
            block("wed", setOf(Day.WED), "17:00", "20:00", BlockAction.Play("events")),
            block("broken", setOf(Day.THU), "17:00", "20:00", BlockAction.Play("empty")),
        ),
        defaultPlaylistId = "welcome",
        timeZone = "America/Chicago",
    )

    private fun playing(id: String, tv: TvPower, reason: Reason) = ScreenDecision(Show.PlaylistShow(id), tv, reason)

    private fun decideAt(time: String, override: ScreenOverride? = null, on: Manifest = manifest) =
        decide(on, override, instant(time))

    private val off = ScreenDecision(Show.Blank, TvPower.OFF, Reason.OVERRIDE)

    @Test
    fun outsideEveryBlockTheDefaultPlaylistPlays() {
        assertEquals(playing("welcome", TvPower.ON, Reason.DEFAULT), decideAt("2026-10-19T08:00"))
    }

    @Test
    fun aScheduledBlockPlaysItsPlaylist() {
        assertEquals(playing("events", TvPower.ON, Reason.SCHEDULE), decideAt("2026-10-21T18:00"))
    }

    @Test
    fun aTvOffBlockBlanksAndTurnsTheTvOff() {
        assertEquals(
            ScreenDecision(Show.Blank, TvPower.OFF, Reason.SCHEDULE),
            decideAt("2026-10-20T02:00"),
        )
    }

    @Test
    fun serviceHoursKeepPlayingButLeaveTheTvAlone() {
        assertEquals(playing("welcome", TvPower.LEAVE, Reason.SCHEDULE), decideAt("2026-10-18T10:00"))
    }

    @Test
    fun aPlaylistThatCannotPlayFallsBackToTheDefault() {
        assertEquals(playing("welcome", TvPower.ON, Reason.SCHEDULE), decideAt("2026-10-22T18:00"))
    }

    @Test
    fun withNothingPlayableTheIdleCardShows() {
        val decision = decide(manifest, null, instant("2026-10-21T18:00"), isPlayable = { false })
        assertEquals(ScreenDecision(Show.Idle, TvPower.ON, Reason.SCHEDULE), decision)
        val noDefault = manifest.copy(defaultPlaylistId = null)
        val idle = ScreenDecision(Show.Idle, TvPower.ON, Reason.DEFAULT)
        assertEquals(idle, decideAt("2026-10-19T08:00", on = noDefault))
    }

    @Test
    fun anOverrideBeatsTheScheduleUntilTheNextChange() {
        val setAt = instant("2026-10-21T17:30")
        val override = ScreenOverride(OverrideAction.Play("welcome"), Until.NextChange, setAt.toEpochMilli())
        assertEquals(playing("welcome", TvPower.ON, Reason.OVERRIDE), decideAt("2026-10-21T19:59", override))
        assertEquals(playing("welcome", TvPower.ON, Reason.DEFAULT), decideAt("2026-10-21T20:00", override))
    }

    @Test
    fun anOverrideUntilATimeEndsThen() {
        val override = ScreenOverride(
            OverrideAction.TvOff,
            Until.Time(instant("2026-10-19T09:00").toEpochMilli()),
            instant("2026-10-19T08:00").toEpochMilli(),
        )
        assertEquals(off, decideAt("2026-10-19T08:59", override))
        assertEquals(playing("welcome", TvPower.ON, Reason.DEFAULT), decideAt("2026-10-19T09:00", override))
    }

    @Test
    fun anOverrideUntilReleasedOutlastsTheSchedule() {
        val override =
            ScreenOverride(OverrideAction.Play("events"), Until.Released, instant("2026-10-19T08:00").toEpochMilli())
        assertEquals(playing("events", TvPower.ON, Reason.OVERRIDE), decideAt("2026-10-25T10:00", override))
    }

    @Test
    fun withNoScheduleNextChangeHoldsForever() {
        val bare = manifest.copy(schedule = emptyList())
        val override = ScreenOverride(OverrideAction.TvOff, Until.NextChange, 0)
        assertEquals(off, decideAt("2030-01-01T00:00", override, bare))
    }

    @Test
    fun anUnknownTimeZoneFallsBackToUtc() {
        assertEquals(ZoneId.of("UTC"), manifest.copy(timeZone = "Mars/Olympus").zoneOrUtc())
        assertEquals(zone, manifest.zoneOrUtc())
    }
}
