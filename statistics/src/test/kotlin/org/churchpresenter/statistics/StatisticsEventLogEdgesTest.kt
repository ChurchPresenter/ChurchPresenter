package org.churchpresenter.statistics

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The event-log queries at their edges: a log holding only one kind of event, events either side of
 * the asked-for range, events stored out of time order, and a clear bounded on one side only.
 *
 * A log is written to `play_log.json` before the manager is built, which is the only way to put
 * events at chosen timestamps — recording stamps them with the wall clock.
 */
class StatisticsEventLogEdgesTest {

    private fun managerWith(home: File, log: PlayEventLog): StatisticsManager {
        File(home, ".churchpresenter").apply { mkdirs() }
            .resolve("play_log.json")
            .writeText(Json.encodeToString(log))
        return StatisticsManager()
    }

    private fun songAt(timestamp: Long, number: Int = 1) =
        SongPlayEvent(songNumber = number, title = "Song $number", songbook = "Hymnal", timestamp = timestamp)

    private fun verseAt(timestamp: Long, verse: Int = 16) =
        VersePlayEvent(bibleName = "KJV", bookName = "John", chapter = 3, verseNumber = verse, timestamp = timestamp)

    @Test
    fun `an empty log has no earliest event and no log to report on`() = withStatsHome { home ->
        val manager = managerWith(home, PlayEventLog())

        assertNull(manager.getEarliestEventTime())
        assertFalse(manager.hasEventLog())
    }

    @Test
    fun `a log of verses alone still has an earliest event and a log`() = withStatsHome { home ->
        val manager = managerWith(
            home,
            PlayEventLog(verseEvents = listOf(verseAt(JAN_2026 + DAY_MS), verseAt(JAN_2026))),
        )

        assertEquals(JAN_2026, manager.getEarliestEventTime())
        assertTrue(manager.hasEventLog())
    }

    @Test
    fun `a log of songs alone takes its earliest event from the songs`() = withStatsHome { home ->
        val manager = managerWith(home, PlayEventLog(songEvents = listOf(songAt(JAN_2026 + DAY_MS), songAt(JAN_2026))))

        assertEquals(JAN_2026, manager.getEarliestEventTime())
    }

    @Test
    fun `the earliest event is the earlier of the first song and the first verse`() = withStatsHome { home ->
        val manager = managerWith(
            home,
            PlayEventLog(songEvents = listOf(songAt(JAN_2026 + DAY_MS)), verseEvents = listOf(verseAt(JAN_2026))),
        )

        assertEquals(JAN_2026, manager.getEarliestEventTime())
    }

    @Test
    fun `activity counts only the events inside the range`() = withStatsHome { home ->
        val from = JAN_2026
        val to = JAN_2026 + 14 * DAY_MS
        val manager = managerWith(
            home,
            PlayEventLog(
                songEvents = listOf(songAt(from - DAY_MS), songAt(from + DAY_MS), songAt(to + DAY_MS)),
                verseEvents = listOf(verseAt(from - DAY_MS), verseAt(from + DAY_MS), verseAt(to + DAY_MS)),
            ),
        )

        val activity = manager.getActivityByPeriod(from, to)

        assertEquals(1, activity.sumOf { it.songCount })
        assertEquals(1, activity.sumOf { it.verseCount })
    }

    @Test
    fun `a song's first and last use are found whatever order its plays were stored in`() = withStatsHome { home ->
        val manager = managerWith(
            home,
            PlayEventLog(
                songEvents = listOf(
                    songAt(JAN_2026 + DAY_MS),
                    songAt(JAN_2026),
                    songAt(JAN_2026 + 2 * DAY_MS),
                    songAt(JAN_2026 - 30 * DAY_MS),
                ),
            ),
        )

        val song = manager.getAllSongsInRange(JAN_2026 - DAY_MS, JAN_2026 + 3 * DAY_MS).single()

        assertEquals(3, song.count, "the play before the range is left out")
        assertEquals(JAN_2026, song.firstUsed)
        assertEquals(JAN_2026 + 2 * DAY_MS, song.lastUsed)
    }

    @Test
    fun `a verse's first and last use are found whatever order its plays were stored in`() = withStatsHome { home ->
        val manager = managerWith(
            home,
            PlayEventLog(
                verseEvents = listOf(
                    verseAt(JAN_2026 + DAY_MS),
                    verseAt(JAN_2026),
                    verseAt(JAN_2026 + 2 * DAY_MS),
                    verseAt(JAN_2026 + 30 * DAY_MS),
                ),
            ),
        )

        val verse = manager.getAllVersesInRange(JAN_2026 - DAY_MS, JAN_2026 + 3 * DAY_MS).single()

        assertEquals(3, verse.count, "the play after the range is left out")
        assertEquals(JAN_2026, verse.firstUsed)
        assertEquals(JAN_2026 + 2 * DAY_MS, verse.lastUsed)
    }

    @Test
    fun `a clear bounded only at its end removes everything up to it`() = withStatsHome { home ->
        val manager = managerWith(
            home,
            PlayEventLog(
                songEvents = listOf(songAt(JAN_2026), songAt(JAN_2026 + 10 * DAY_MS)),
                verseEvents = listOf(verseAt(JAN_2026), verseAt(JAN_2026 + 10 * DAY_MS)),
            ),
        )

        assertEquals(1, manager.clearSong(SongKey("Hymnal", 1, "Song 1"), toMs = JAN_2026 + DAY_MS))
        assertEquals(1, manager.clearVerse(VerseKey("KJV", "John", 3, 16), toMs = JAN_2026 + DAY_MS))

        assertEquals(JAN_2026 + 10 * DAY_MS, manager.getEarliestEventTime())
    }
}
