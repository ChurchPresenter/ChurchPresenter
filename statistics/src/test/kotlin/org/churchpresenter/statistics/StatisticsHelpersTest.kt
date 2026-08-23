package org.churchpresenter.statistics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.core.models.statistics.SongDisplayEntry
import org.churchpresenter.core.models.statistics.SongKey
import org.churchpresenter.core.models.statistics.SongPlayEvent
import org.churchpresenter.core.models.statistics.VerseDisplayEntry
import org.churchpresenter.core.models.statistics.VerseKey
import org.churchpresenter.core.models.statistics.VersePlayEvent

/**
 * The pure helpers behind the statistics screen and its CCLI export: how the activity chart picks
 * its bucket size from the selected range, how a CSV field is quoted, which timestamps a selected
 * period holds, and what a per-item deletion leaves behind in the all-time counters. A wrong
 * boundary makes a two-year report render as 100+ weekly bars; an unescaped quote or comma
 * corrupts the CSV a license report is built from; a mis-cleared counter reports a song the church
 * asked to have removed.
 */
class StatisticsHelpersTest {

    private val day = 86_400_000L

    @Test
    fun `short ranges bucket by week`() {
        assertEquals(ActivityGranularity.WEEKLY, activityGranularityFor(day))
        assertEquals(ActivityGranularity.WEEKLY, activityGranularityFor(90 * day), "90 days is the weekly ceiling")
    }

    @Test
    fun `medium ranges bucket by month`() {
        assertEquals(
            ActivityGranularity.MONTHLY,
            activityGranularityFor(91 * day),
            "just past 90 days flips to monthly",
        )
        assertEquals(ActivityGranularity.MONTHLY, activityGranularityFor(730 * day), "two years is the monthly ceiling")
    }

    @Test
    fun `long ranges bucket by year`() {
        assertEquals(ActivityGranularity.YEARLY, activityGranularityFor(731 * day))
        assertEquals(ActivityGranularity.YEARLY, activityGranularityFor(3650 * day))
    }

    @Test
    fun `csvQuote wraps a plain field in quotes`() {
        assertEquals("\"Amazing Grace\"", csvQuote("Amazing Grace"))
        assertEquals("\"\"", csvQuote(""))
    }

    @Test
    fun `csvQuote doubles embedded quotes`() {
        assertEquals("\"She said \"\"hi\"\"\"", csvQuote("She said \"hi\""))
    }

    @Test
    fun `csvQuote leaves commas and newlines to be protected by the surrounding quotes`() {
        assertEquals("\"Bach, J.S.\"", csvQuote("Bach, J.S."))
        assertEquals("\"line1\nline2\"", csvQuote("line1\nline2"))
    }

    // ── key() ───────────────────────────────────────────────────────────────────

    @Test
    fun `a song's play and its tally row derive the same key`() {
        val key = SongPlayEvent(12, "Amazing Grace", "Hymnal", "Newton", 1_000L).key()

        assertEquals(SongDisplayEntry(12, "Amazing Grace", "Hymnal", 4).key(), key, "clearing matches on this")
        assertEquals("Hymnal", key.songbook)
        assertEquals(12, key.songNumber)
        assertEquals("Amazing Grace", key.title)
    }

    @Test
    fun `a verse's play and its tally row derive the same key`() {
        val key = VersePlayEvent("KJV", "John", 3, 16, 1_000L).key()

        assertEquals(VerseDisplayEntry("KJV", "John", 3, 16, 4).key(), key, "clearing matches on this")
        assertEquals("KJV", key.bibleName)
        assertEquals("John", key.bookName)
        assertEquals(3, key.chapter)
        assertEquals(16, key.verseNumber)
    }

    // ── inRange ─────────────────────────────────────────────────────────────────

    @Test
    fun `an unbounded range holds every timestamp`() {
        assertTrue(1_000L.inRange(null, null))
    }

    @Test
    fun `an open-ended range holds anything from its start onwards`() {
        assertTrue(1_000L.inRange(500L, null))
        assertFalse(400L.inRange(500L, null))
    }

    @Test
    fun `a range open at the start holds anything up to its end`() {
        assertTrue(400L.inRange(null, 500L))
        assertFalse(600L.inRange(null, 500L))
    }

    @Test
    fun `a closed range includes both of its endpoints`() {
        assertTrue(500L.inRange(500L, 600L))
        assertTrue(600L.inRange(500L, 600L))
        assertFalse(601L.inRange(500L, 600L))
    }

    // ── withSongCleared / withVerseCleared ──────────────────────────────────────
    //
    // The all-time counters behind the statistics screen. Clearing a whole history drops the row;
    // clearing a period only reduces it, and the row goes when nothing is left.

    private val songKey = SongKey(songbook = "Hymnal", songNumber = 42, title = "Amazing Grace")

    private fun songCounts(vararg entries: Pair<String, SongDisplayEntry>) = entries.toMap()

    private fun songEntry(count: Int, title: String = "Amazing Grace") =
        SongDisplayEntry(songNumber = 42, title = title, songbook = "Hymnal", count = count)

    @Test
    fun `clearing a song's whole history drops its row`() {
        val result = songCounts("Hymnal::42" to songEntry(count = 7))
            .withSongCleared(songKey, removed = 7, clearAll = true)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `clearing a period reduces the count and keeps the row`() {
        val result = songCounts("Hymnal::42" to songEntry(count = 7))
            .withSongCleared(songKey, removed = 3, clearAll = false)
        assertEquals(4, result.getValue("Hymnal::42").count)
    }

    @Test
    fun `a row reduced to nothing goes rather than lingering at zero`() {
        val result = songCounts("Hymnal::42" to songEntry(count = 3))
            .withSongCleared(songKey, removed = 3, clearAll = false)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `removing more than was ever counted floors the row at gone`() {
        val result = songCounts("Hymnal::42" to songEntry(count = 2))
            .withSongCleared(songKey, removed = 9, clearAll = false)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `every other song is left exactly as it was`() {
        val result = songCounts(
            "Hymnal::42" to songEntry(count = 7),
            "Hymnal::43" to songEntry(count = 5, title = "Be Thou My Vision"),
        ).withSongCleared(songKey, removed = 7, clearAll = true)
        assertEquals(listOf("Hymnal::43"), result.keys.toList())
        assertEquals(5, result.getValue("Hymnal::43").count)
    }

    private val verseKey = VerseKey(bibleName = "KJV", bookName = "John", chapter = 3, verseNumber = 16)

    private fun verseEntry(count: Int, verseNumber: Int = 16) =
        VerseDisplayEntry(bibleName = "KJV", bookName = "John", chapter = 3, verseNumber = verseNumber, count = count)

    @Test
    fun `clearing a verse's whole history drops its row`() {
        val result = mapOf("KJV::John::3::16" to verseEntry(count = 4))
            .withVerseCleared(verseKey, removed = 4, clearAll = true)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `clearing a verse for a period reduces the count and keeps the row`() {
        val result = mapOf("KJV::John::3::16" to verseEntry(count = 4))
            .withVerseCleared(verseKey, removed = 1, clearAll = false)
        assertEquals(3, result.getValue("KJV::John::3::16").count)
    }

    @Test
    fun `a verse row reduced to nothing goes`() {
        val result = mapOf("KJV::John::3::16" to verseEntry(count = 2))
            .withVerseCleared(verseKey, removed = 2, clearAll = false)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `every other verse is left exactly as it was`() {
        val result = mapOf(
            "KJV::John::3::16" to verseEntry(count = 4),
            "KJV::John::3::17" to verseEntry(count = 2, verseNumber = 17),
        ).withVerseCleared(verseKey, removed = 4, clearAll = true)
        assertEquals(listOf("KJV::John::3::17"), result.keys.toList())
    }
}
