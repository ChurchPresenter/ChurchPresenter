package org.churchpresenter.app.churchpresenter.data

import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two files the usage history lives in.
 *
 * `statistics.json` is the all-time tally and `play_log.json` the timestamped log the licence report
 * is computed from. Both are append-only across years — a church three years in has every service
 * it has ever run in them, and there is no way to reconstruct either if they are lost. So the shape
 * on disk is a compatibility contract with every past build that wrote it: a field renamed here
 * reads back as its default under `ignoreUnknownKeys`, which shows up as a report of zero rather
 * than as an error.
 *
 * The manager is deliberately forgiving on load — an unreadable file yields an empty history rather
 * than a crash at startup — so the danger is silence, and what these pin is that a file written by
 * an older build still counts, and that a file this build wrote can be read by a build that
 * predates any given field.
 */
class StatisticsFileFormatTest {

    private lateinit var tempHome: File
    private var realHome: String? = null

    private val statsFile: File get() = File(tempHome, ".churchpresenter/statistics.json")
    private val logFile: File get() = File(tempHome, ".churchpresenter/play_log.json")

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-statistics-format-test").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        File(tempHome, ".churchpresenter").mkdirs()
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    // ── The all-time tally on disk ──────────────────────────────────────────────

    @Test
    fun `a tally written by an older build is read`() {
        statsFile.writeText(
            """{"songDisplayCounts":{"Hymnal::42":{"songNumber":42,"title":"Amazing Grace","songbook":"Hymnal","count":7}},
                "verseDisplayCounts":{"KJV::John::3::16":{"bibleName":"KJV","bookName":"John","chapter":3,"verseNumber":16,"count":4}}}""",
        )

        val stats = StatisticsManager()

        assertEquals(7, stats.getSongPlayCount("Hymnal::42"), "three years of counting must not restart at zero")
        assertEquals(4, stats.getTopVersesByBible().getValue("KJV").single().count)
    }

    @Test
    fun `a tally entry missing the fields a newer build added still counts`() {
        statsFile.writeText("""{"songDisplayCounts":{"Hymnal::42":{"count":7}}}""")

        val entry = StatisticsManager().getTopSongsBySongbook().values.single().single()

        assertEquals(7, entry.count, "the count is the field the report needs; the rest is labelling")
        assertEquals("", entry.title)
        assertEquals(0, entry.songNumber)
    }

    @Test
    fun `a tally carrying a field this build does not know is still read`() {
        statsFile.writeText(
            """{"songDisplayCounts":{"Hymnal::42":{"count":7,"someFutureField":"x"}},"futureTopLevel":[]}""",
        )

        assertEquals(7, StatisticsManager().getSongPlayCount("Hymnal::42"), "a downgrade must not erase the history")
    }

    @Test
    fun `an unreadable tally starts empty rather than failing the launch`() {
        statsFile.writeText("""{"songDisplayCounts":{"Hymnal::42":{"count":""")

        val stats = StatisticsManager()

        assertEquals(0, stats.getSongPlayCount("Hymnal::42"))
        assertTrue(stats.getTopSongsBySongbook().isEmpty(), "the app has to open on Sunday whatever this file says")
    }

    @Test
    fun `what this build writes is what the next one reads`() {
        val first = StatisticsManager()
        repeat(2) { first.recordSongDisplay("Hymnal::42", 42, "Amazing Grace", "Hymnal") }
        first.recordVerseDisplay("KJV", "John", 3, 16)

        val decoded = json.decodeFromString(DisplayStatistics.serializer(), statsFile.readText())

        assertEquals(
            SongDisplayEntry(songNumber = 42, title = "Amazing Grace", songbook = "Hymnal", count = 2),
            decoded.songDisplayCounts.getValue("Hymnal::42"),
        )
        assertEquals(
            VerseDisplayEntry(bibleName = "KJV", bookName = "John", chapter = 3, verseNumber = 16, count = 1),
            decoded.verseDisplayCounts.getValue("KJV::John::3::16"),
        )
    }

    @Test
    fun `the tally is keyed so two songbooks never share a row`() {
        val stats = StatisticsManager()
        stats.recordSongDisplay("Hymnal::1", 1, "Amazing Grace", "Hymnal")
        stats.recordSongDisplay("Songs of Praise::1", 1, "Amazing Grace", "Songs of Praise")

        val keys = json.decodeFromString(DisplayStatistics.serializer(), statsFile.readText()).songDisplayCounts.keys

        assertEquals(setOf("Hymnal::1", "Songs of Praise::1"), keys, "the key is the identity — merging them loses a song")
    }

    // ── The event log on disk ───────────────────────────────────────────────────

    @Test
    fun `a log written by an older build is reported on`() {
        logFile.writeText(
            """{"songEvents":[{"songNumber":42,"title":"Amazing Grace","songbook":"Hymnal","author":"Newton","timestamp":1000}],
                "verseEvents":[{"bibleName":"KJV","bookName":"John","chapter":3,"verseNumber":16,"timestamp":2000}]}""",
        )

        val stats = StatisticsManager()

        assertEquals(1000L, stats.getEarliestEventTime(), "the report's date range is built from these timestamps")
        assertEquals("Newton", stats.getAllSongsInRange(0L, 5000L).single().author)
        assertEquals("John", stats.getAllVersesInRange(0L, 5000L).single().bookName)
    }

    @Test
    fun `an event with no author recorded is still reported`() {
        // The author field postdates the log itself — events from before it read as blank.
        logFile.writeText(
            """{"songEvents":[{"songNumber":42,"title":"Amazing Grace","songbook":"Hymnal","timestamp":1000}]}""",
        )

        val summary = StatisticsManager().getAllSongsInRange(0L, 5000L).single()

        assertEquals("", summary.author)
        assertEquals("Amazing Grace", summary.title, "a missing author must not cost the song its row")
    }

    @Test
    fun `an event with no timestamp lands at the beginning of time rather than being dropped`() {
        logFile.writeText("""{"songEvents":[{"songNumber":42,"title":"Amazing Grace","songbook":"Hymnal"}]}""")

        val stats = StatisticsManager()

        assertEquals(0L, stats.getEarliestEventTime())
        assertEquals(
            1,
            stats.getAllSongsInRange(0L, Long.MAX_VALUE).size,
            "an undated event is still evidence the song was sung",
        )
    }

    @Test
    fun `a log with no verse events at all is read`() {
        logFile.writeText("""{"songEvents":[{"title":"Amazing Grace","songbook":"Hymnal","timestamp":1000}]}""")

        val stats = StatisticsManager()

        assertTrue(stats.hasEventLog())
        assertTrue(stats.getAllVersesInRange(0L, Long.MAX_VALUE).isEmpty())
    }

    @Test
    fun `an unreadable log leaves the report empty rather than failing the launch`() {
        logFile.writeText("""{"songEvents":[{"title":"Amazing""")

        val stats = StatisticsManager()

        assertTrue(stats.getAllSongsInRange(0L, Long.MAX_VALUE).isEmpty())
        assertEquals(null, stats.getEarliestEventTime())
    }

    @Test
    fun `every event this build writes carries what the report needs`() {
        val stats = StatisticsManager()
        stats.recordSongDisplay("Hymnal::42", 42, "Amazing Grace", "Hymnal", "John Newton")
        stats.recordVerseDisplay("KJV", "John", 3, 16)

        val log = json.decodeFromString(PlayEventLog.serializer(), logFile.readText())

        val song = log.songEvents.single()
        assertEquals(42, song.songNumber)
        assertEquals("Amazing Grace", song.title)
        assertEquals("Hymnal", song.songbook)
        assertEquals("John Newton", song.author)
        assertTrue(song.timestamp > 0L, "an event with no time cannot be filtered into a reporting period")

        val verse = log.verseEvents.single()
        assertEquals(VersePlayEvent("KJV", "John", 3, 16, verse.timestamp), verse)
    }

    @Test
    fun `the log keeps every showing rather than collapsing repeats`() {
        val stats = StatisticsManager()
        repeat(3) { stats.recordSongDisplay("Hymnal::42", 42, "Amazing Grace", "Hymnal") }

        assertEquals(
            3,
            json.decodeFromString(PlayEventLog.serializer(), logFile.readText()).songEvents.size,
            "a report for one month has to be able to count showings inside it",
        )
    }

    // ── Clearing ────────────────────────────────────────────────────────────────

    @Test
    fun `clearing empties both files rather than only the tally`() {
        val stats = StatisticsManager()
        stats.recordSongDisplay("Hymnal::42", 42, "Amazing Grace", "Hymnal")
        stats.recordVerseDisplay("KJV", "John", 3, 16)

        stats.clearStatistics()

        assertEquals(DisplayStatistics(), json.decodeFromString(DisplayStatistics.serializer(), statsFile.readText()))
        assertEquals(
            PlayEventLog(),
            json.decodeFromString(PlayEventLog.serializer(), logFile.readText()),
            "a cleared history that comes back on the next launch is worse than not clearing at all",
        )
        assertTrue(!StatisticsManager().hasEventLog())
    }
}
