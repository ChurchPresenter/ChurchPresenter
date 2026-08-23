package org.churchpresenter.statistics

import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.churchpresenter.core.models.statistics.DisplayStatistics
import org.churchpresenter.core.models.statistics.PlayEventLog
import org.churchpresenter.core.models.statistics.SongDisplayEntry
import org.churchpresenter.core.models.statistics.SongKey
import org.churchpresenter.core.models.statistics.SongPlayEvent
import org.churchpresenter.core.models.statistics.StatisticsPeriod
import org.churchpresenter.core.models.statistics.VerseDisplayEntry
import org.churchpresenter.core.models.statistics.VerseKey
import org.churchpresenter.core.models.statistics.VersePlayEvent

/**
 * What was sung and read, and the CCLI report built from it.
 *
 * This is the one part of the app whose output goes to a third party: a licence report is filed
 * against these numbers, so a song counted twice, missed entirely, or attributed to the wrong
 * songbook is a reporting error rather than a cosmetic one. Two stores back it — an all-time
 * per-song tally for the statistics screen, and a timestamped event log the date-filtered report
 * is computed from — and both are written to `~/.churchpresenter/` after every go-live.
 *
 * `user.home` is swapped per test, so a "restart" is a second manager against the same files.
 * Events are stamped with the wall clock as they are recorded, so range queries here are anchored
 * on the recorded events themselves rather than on a fixed date.
 */
class StatisticsManagerTest {

    private lateinit var tempHome: File
    private var realHome: String? = null

    private val statsFile: File get() = File(tempHome, ".churchpresenter/statistics.json")
    private val logFile: File get() = File(tempHome, ".churchpresenter/play_log.json")

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-statistics-test").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        // The app data folder already exists by the time anything is recorded in a real run —
        // settings are written at startup. StatisticsManager does not create it itself; see
        // `nothing is recorded at all when the app data folder is missing -- known gap`.
        File(tempHome, ".churchpresenter").mkdirs()
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    /** A second manager against the same files — i.e. the app restarted. */
    private fun restarted() = StatisticsManager()

    /** Every event ever recorded, whenever it happened. */
    private val everything = 0L..Long.MAX_VALUE

    private fun StatisticsManager.songsIn(range: LongRange) = getAllSongsInRange(range.first, range.last)
    private fun StatisticsManager.versesIn(range: LongRange) = getAllVersesInRange(range.first, range.last)

    private fun StatisticsManager.sing(
        title: String,
        number: Int = 1,
        songbook: String = "Hymnal",
        author: String = "",
        times: Int = 1,
    ) = repeat(times) { recordSongDisplay("$songbook::$number", number, title, songbook, author) }

    private fun StatisticsManager.read(
        book: String,
        chapter: Int = 3,
        verse: Int = 16,
        bible: String = "KJV",
        times: Int = 1,
    ) = repeat(times) { recordVerseDisplay(bible, book, chapter, verse) }

    // ── Nothing recorded yet ────────────────────────────────────────────────────

    @Test
    fun `a fresh install has nothing to report`() {
        val stats = StatisticsManager()

        assertFalse(stats.hasEventLog(), "an empty log must not offer a report of nothing")
        assertNull(stats.getEarliestEventTime())
        assertTrue(stats.songsIn(everything).isEmpty())
        assertTrue(stats.versesIn(everything).isEmpty())
        assertTrue(stats.getTopSongsBySongbook().isEmpty())
        assertEquals(0, stats.getSongPlayCount("Hymnal::1"))
    }

    // ── Counting what was used ──────────────────────────────────────────────────

    @Test
    fun `singing a song counts it`() {
        val stats = StatisticsManager()

        stats.sing("Amazing Grace", times = 3)

        assertEquals(3, stats.getSongPlayCount("Hymnal::1"))
        assertTrue(stats.hasEventLog())
    }

    @Test
    fun `the same song from two songbooks is counted separately`() {
        val stats = StatisticsManager()

        stats.sing("Amazing Grace", number = 1, songbook = "Hymnal")
        stats.sing("Amazing Grace", number = 1, songbook = "Songs of Praise")

        assertEquals(1, stats.getSongPlayCount("Hymnal::1"))
        assertEquals(1, stats.getSongPlayCount("Songs of Praise::1"))
    }

    @Test
    fun `a song with no id of its own is counted under its songbook and number`() {
        val stats = StatisticsManager()

        stats.recordSongDisplay("", 42, "Amazing Grace", "Hymnal")

        assertEquals(1, stats.getSongPlayCount("Hymnal::42"), "an older song file has no stable id to key on")
    }

    @Test
    fun `reading a verse counts it`() {
        val stats = StatisticsManager()

        stats.read("John", times = 2)

        assertEquals(1, stats.versesIn(everything).size)
        assertEquals(2, stats.versesIn(everything).single().count)
    }

    @Test
    fun `the same verse in two bibles is counted separately`() {
        val stats = StatisticsManager()

        stats.read("John", bible = "KJV")
        stats.read("John", bible = "Synodal")

        assertEquals(2, stats.versesIn(everything).size)
    }

    // ── The statistics screen ───────────────────────────────────────────────────

    @Test
    fun `top songs are grouped by songbook, most used first`() {
        val stats = StatisticsManager()
        stats.sing("Rarely", number = 1, songbook = "Hymnal", times = 1)
        stats.sing("Often", number = 2, songbook = "Hymnal", times = 5)
        stats.sing("Elsewhere", number = 1, songbook = "Songs of Praise", times = 2)

        val top = stats.getTopSongsBySongbook()

        assertEquals(setOf("Hymnal", "Songs of Praise"), top.keys)
        assertEquals(listOf("Often", "Rarely"), top.getValue("Hymnal").map { it.title })
        assertEquals(listOf("Elsewhere"), top.getValue("Songs of Praise").map { it.title })
    }

    @Test
    fun `only the top few songs are offered`() {
        val stats = StatisticsManager()
        repeat(5) { stats.sing("Song $it", number = it, times = it + 1) }

        val top = stats.getTopSongsBySongbook(limit = 2)

        assertEquals(listOf("Song 4", "Song 3"), top.getValue("Hymnal").map { it.title })
    }

    @Test
    fun `top verses are grouped by bible`() {
        val stats = StatisticsManager()
        stats.read("John", bible = "KJV", times = 3)
        stats.read("Psalms", chapter = 23, verse = 1, bible = "KJV", times = 1)
        stats.read("John", bible = "Synodal", times = 1)

        val top = stats.getTopVersesByBible()

        assertEquals(setOf("KJV", "Synodal"), top.keys)
        assertEquals(listOf(3, 1), top.getValue("KJV").map { it.count }, "most read first")
    }

    @Test
    fun `only the top few verses per bible are offered`() {
        val stats = StatisticsManager()
        stats.read("John", chapter = 3, verse = 16, bible = "KJV", times = 5)
        stats.read("Psalms", chapter = 23, verse = 1, bible = "KJV", times = 4)
        stats.read("Romans", chapter = 8, verse = 28, bible = "KJV", times = 3)

        val top = stats.getTopVersesByBible(limit = 2)

        assertEquals(2, top.getValue("KJV").size)
        assertEquals(listOf(5, 4), top.getValue("KJV").map { it.count })
    }

    @Test
    fun `a range that starts and ends exactly on an event includes it`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace")
        stats.read("John", chapter = 3, verse = 16)
        val songAt = stats.songsIn(everything).single().firstUsed
        val verseAt = stats.versesIn(everything).single().firstUsed

        assertEquals(1, stats.getAllSongsInRange(songAt, songAt).size)
        assertEquals(1, stats.getAllVersesInRange(verseAt, verseAt).size)
    }

    // ── The report itself ───────────────────────────────────────────────────────

    @Test
    fun `a song used several times appears once, with its count`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace", times = 4)

        val reported = stats.songsIn(everything).single()

        assertEquals("Amazing Grace", reported.title)
        assertEquals(4, reported.count, "a licence report counts uses, not rows")
        assertEquals("Hymnal", reported.songbook)
        assertEquals(1, reported.songNumber)
    }

    @Test
    fun `first and last use bracket every time the song was used`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace", times = 3)

        val reported = stats.songsIn(everything).single()

        assertTrue(reported.firstUsed <= reported.lastUsed)
        assertEquals(reported.firstUsed, stats.getEarliestEventTime())
    }

    @Test
    fun `an author recorded on any use is reported`() {
        // The author travels with the song file, so an older event may not carry one.
        val stats = StatisticsManager()
        stats.recordSongDisplay("Hymnal::1", 1, "Amazing Grace", "Hymnal", author = "")
        stats.recordSongDisplay("Hymnal::1", 1, "Amazing Grace", "Hymnal", author = "John Newton")

        assertEquals("John Newton", stats.songsIn(everything).single().author)
    }

    @Test
    fun `songs are reported most used first`() {
        val stats = StatisticsManager()
        stats.sing("Once", number = 1, times = 1)
        stats.sing("Three times", number = 2, times = 3)
        stats.sing("Twice", number = 3, times = 2)

        assertEquals(listOf("Three times", "Twice", "Once"), stats.songsIn(everything).map { it.title })
    }

    @Test
    fun `a song with no catalogue entry reports no ccli number`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace")

        assertEquals(
            "",
            stats.songsIn(everything).single().ccliNumber,
            "an unresolvable number must be blank rather than guessed",
        )
    }

    @Test
    fun `verses are reported with their reference and count`() {
        val stats = StatisticsManager()
        stats.read("John", chapter = 3, verse = 16, times = 2)

        val reported = stats.versesIn(everything).single()

        assertEquals("KJV", reported.bibleName)
        assertEquals("John", reported.bookName)
        assertEquals(3, reported.chapter)
        assertEquals(16, reported.verseNumber)
        assertEquals(2, reported.count)
    }

    // ── Date filtering ──────────────────────────────────────────────────────────

    @Test
    fun `a range that ends before the events reports nothing`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace")
        val earliest = assertNotNull(stats.getEarliestEventTime())

        assertTrue(
            stats.getAllSongsInRange(0L, earliest - 1).isEmpty(),
            "last quarter's report must not include this quarter's services",
        )
        assertTrue(stats.getAllVersesInRange(0L, earliest - 1).isEmpty())
    }

    @Test
    fun `a range that starts after the events reports nothing`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace")
        val latest = assertNotNull(stats.getEarliestEventTime())

        assertTrue(stats.getAllSongsInRange(latest + 60_000, Long.MAX_VALUE).isEmpty())
    }

    @Test
    fun `the range includes its own endpoints`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace")
        val at = assertNotNull(stats.getEarliestEventTime())

        assertEquals(
            1,
            stats.getAllSongsInRange(at, at).size,
            "a service on the last day of the quarter belongs in that quarter's report",
        )
    }

    // ── Activity chart ──────────────────────────────────────────────────────────

    @Test
    fun `a short range is bucketed by week`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace", times = 2)
        stats.read("John")
        val now = System.currentTimeMillis()

        val points = stats.getActivityByPeriod(now - 30L * 86_400_000, now)

        assertTrue(points.isNotEmpty())
        assertTrue(points.all { it.label.isNotBlank() }, "every column needs a label")
        assertEquals(2, points.sumOf { it.songCount }, "every event in range belongs to exactly one bucket")
        assertEquals(1, points.sumOf { it.verseCount })
    }

    @Test
    fun `a year-long range is bucketed by month`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace")
        stats.read("John")
        val now = System.currentTimeMillis()

        val points = stats.getActivityByPeriod(now - 365L * 86_400_000, now)

        assertTrue(points.size in 12..14, "expected one column per month, got ${points.size}")
        assertEquals(1, points.sumOf { it.songCount })
        assertEquals(1, points.sumOf { it.verseCount })
    }

    @Test
    fun `a multi-year range is bucketed by year`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace")
        stats.read("John")
        val now = System.currentTimeMillis()

        val points = stats.getActivityByPeriod(now - 4L * 365 * 86_400_000, now)

        assertTrue(points.size in 4..6, "expected one column per year, got ${points.size}")
        assertTrue(points.all { it.label.toIntOrNull() != null }, "yearly columns are labelled with the year")
        assertEquals(1, points.sumOf { it.songCount })
        assertEquals(1, points.sumOf { it.verseCount })
    }

    @Test
    fun `an event outside the requested range is not counted in any bucket`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace")
        stats.read("John")
        val at = assertNotNull(stats.getEarliestEventTime())

        val points = stats.getActivityByPeriod(0L, at - 1)

        assertEquals(0, points.sumOf { it.songCount }, "the event happened after this range, not inside it")
        assertEquals(0, points.sumOf { it.verseCount })
    }

    @Test
    fun `each weekly column counts only the events of its own week`() {
        val now = System.currentTimeMillis()
        seedLog(
            songs = listOf(
                SongPlayEvent(1, "Amazing Grace", "Hymnal", "", now - 21L * 86_400_000),
                SongPlayEvent(2, "Be Thou My Vision", "Hymnal", "", now),
            ),
        )

        val points = StatisticsManager().getActivityByPeriod(now - 30L * 86_400_000, now)

        assertEquals(2, points.sumOf { it.songCount }, "both events are inside the range")
        assertTrue(points.all { it.songCount <= 1 }, "no column may hold both: ${points.map { it.songCount }}")
        assertEquals(2, points.count { it.songCount == 1 }, "three weeks apart is two columns")
    }

    @Test
    fun `each yearly column counts only its own year`() {
        seedLog(
            songs = listOf(
                SongPlayEvent(1, "Older Song", "Hymnal", "", stamp(LAST_YEAR, 5, 4)),
                SongPlayEvent(2, "This Year Song", "Hymnal", "", stamp(THIS_YEAR, 2, 1)),
            ),
        )

        val points = StatisticsManager()
            .getActivityByPeriod(stamp(LAST_YEAR - 2, 1, 1), stamp(THIS_YEAR, 6, 1))

        assertEquals(1, points.first { it.label == LAST_YEAR.toString() }.songCount)
        assertEquals(1, points.first { it.label == THIS_YEAR.toString() }.songCount)
        assertEquals(0, points.first { it.label == (LAST_YEAR - 1).toString() }.songCount, "a quiet year is zero")
    }

    // ── The CCLI export ─────────────────────────────────────────────────────────

    private fun exportedCsv(stats: StatisticsManager): List<String> {
        val file = File(tempHome, "ccli.csv")
        assertTrue(stats.exportCcliCsv(file, 0L, Long.MAX_VALUE), "the export reported failure")
        return file.readLines().filter { it.isNotBlank() }
    }

    @Test
    fun `the csv has the columns a licence report needs`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace")

        assertEquals(
            "Title,Author,Songbook,Song Number,CCLI Number,Times Used,First Used,Last Used",
            exportedCsv(stats).first(),
        )
    }

    @Test
    fun `each song is one row`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace", number = 1, times = 3)
        stats.sing("How Great Thou Art", number = 2, times = 1)

        val rows = exportedCsv(stats).drop(1)

        assertEquals(2, rows.size, "a song used three times is still one line on the report")
        assertTrue(rows.first().startsWith("\"Amazing Grace\""), rows.first())
        assertTrue(rows.first().contains(",3,"), "the count belongs on the row: ${rows.first()}")
    }

    @Test
    fun `a title containing a comma stays in one column`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace, How Sweet")

        val row = exportedCsv(stats).drop(1).single()

        assertTrue(row.startsWith("\"Amazing Grace, How Sweet\","), "an unquoted comma shifts every later column: $row")
    }

    @Test
    fun `a title containing a quote is escaped`() {
        val stats = StatisticsManager()
        stats.sing("""He Said "Come"""")

        val row = exportedCsv(stats).drop(1).single()

        assertTrue(row.startsWith("\"He Said \"\"Come\"\"\","), "CSV doubles an embedded quote: $row")
    }

    @Test
    fun `dates are written the way a spreadsheet reads them`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace")

        val row = exportedCsv(stats).drop(1).single()

        assertTrue(Regex(""".*,\d{4}-\d{2}-\d{2},\d{4}-\d{2}-\d{2}$""").matches(row), row)
    }

    @Test
    fun `an empty report still writes its header`() {
        assertEquals(1, exportedCsv(StatisticsManager()).size, "a report with no services is a valid answer")
    }

    /**
     * NB: a real date range, not `0..Long.MAX_VALUE` as the CSV tests use. This export also builds
     * the activity sheet, and an unbounded range puts that in its yearly branch spanning 1970 to
     * year 292278994 — one row per year. The report dialog always passes real dates, so this is not
     * reachable from the UI, but it is a sharp edge for any future caller.
     *
     * Also note this needs font metrics (POI auto-sizes the columns). That works headless wherever
     * fonts are installed — the same assumption the auto-fit tests already make — but a failure
     * here on a fontless CI image would be an environment problem rather than a product one.
     */
    @Test
    fun `the spreadsheet export writes a real file`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace")
        stats.read("John")
        val file = File(tempHome, "statistics.xls")
        val now = System.currentTimeMillis()

        assertTrue(stats.exportFilteredXls(file, now - 30L * 86_400_000, now))
        assertTrue(file.length() > 0, "an empty file would open as a corrupt workbook")
    }

    @Test
    fun `an export to a folder that does not exist reports failure rather than throwing`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace")

        assertFalse(stats.exportCcliCsv(File(tempHome, "no/such/folder/ccli.csv"), 0L, Long.MAX_VALUE))
    }

    // ── Keeping and clearing ────────────────────────────────────────────────────

    @Test
    fun `counts survive a restart`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace", times = 2)
        stats.read("John")

        val afterRestart = restarted()

        assertEquals(2, afterRestart.getSongPlayCount("Hymnal::1"), "a year of reporting data must outlive a restart")
        assertEquals(2, afterRestart.songsIn(everything).single().count)
        assertEquals(1, afterRestart.versesIn(everything).single().count)
    }

    @Test
    fun `clearing wipes both the counts and the report data`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace")
        stats.read("John")

        stats.clearStatistics()

        assertFalse(stats.hasEventLog())
        assertEquals(0, stats.getSongPlayCount("Hymnal::1"))
        assertTrue(stats.songsIn(everything).isEmpty())
        assertTrue(restarted().songsIn(everything).isEmpty(), "clearing must not come back on restart")
    }

    /**
     * Documents a KNOWN GAP: [StatisticsManager.save] writes straight to
     * `~/.churchpresenter/statistics.json` without creating the folder first, and swallows the
     * resulting failure — so on a profile where that folder does not exist yet, every service's
     * worth of reporting data is discarded without a word. Its siblings do not have this problem:
     * `QAManager` calls `parentFile?.mkdirs()` and `RemoteClientManager` calls `appDataDir.mkdirs()`
     * before writing.
     *
     * Not normally reachable, because settings are written at startup and create the folder — but
     * it makes the statistics store the only one that cannot stand on its own, and the failure is
     * completely silent. One `mkdirs()` in `save`/`saveLog` closes it, and this test then flips to
     * asserting the count survives.
     */
    @Test
    fun `nothing is recorded at all when the app data folder is missing -- known gap`() {
        File(tempHome, ".churchpresenter").deleteRecursively()
        val stats = StatisticsManager()

        stats.sing("Amazing Grace")

        assertEquals(1, stats.getSongPlayCount("Hymnal::1"), "the in-memory tally is fine")
        assertFalse(statsFile.exists(), "current behaviour: nothing reached the disk")
        assertEquals(
            0,
            restarted().getSongPlayCount("Hymnal::1"),
            "current behaviour: the whole service's reporting data is gone on restart",
        )
    }

    @Test
    fun `a corrupt statistics file starts from zero rather than failing to open`() {
        statsFile.parentFile.mkdirs()
        statsFile.writeText("not json")
        logFile.writeText("not json either")

        val stats = StatisticsManager()

        assertEquals(0, stats.getSongPlayCount("Hymnal::1"))
        assertFalse(stats.hasEventLog())
    }

    @Test
    fun `recording after a corrupt file repairs it`() {
        statsFile.parentFile.mkdirs()
        statsFile.writeText("not json")
        val stats = StatisticsManager()

        stats.sing("Amazing Grace")

        assertEquals(1, restarted().getSongPlayCount("Hymnal::1"))
    }

    // ── Periods ─────────────────────────────────────────────────────────────────

    /**
     * Two calendar years of history, written straight to the two files a manager reads at
     * construction. Recording stamps events with the wall clock, so a past year can only be seeded
     * this way.
     */
    private fun seedTwoYears() {
        val zone = ZoneId.systemDefault()
        fun stamp(year: Int, month: Int, day: Int) =
            LocalDate.of(year, month, day).atTime(10, 30).atZone(zone).toInstant().toEpochMilli()

        val songEvents = List(3) { SongPlayEvent(1, "Older Song", "Hymnal", "", stamp(LAST_YEAR, 5, 4)) } +
            List(2) { SongPlayEvent(2, "This Year Song", "Hymnal", "", stamp(THIS_YEAR, 2, 1)) }
        val verseEvents = List(4) { VersePlayEvent("KJV", "John", 3, 16, stamp(LAST_YEAR, 5, 4)) } +
            List(1) { VersePlayEvent("KJV", "Psalms", 23, 1, stamp(THIS_YEAR, 2, 1)) }

        val json = Json { encodeDefaults = true }
        statsFile.parentFile.mkdirs()
        statsFile.writeText(
            json.encodeToString(
                DisplayStatistics.serializer(),
                DisplayStatistics(
                    songDisplayCounts = mapOf(
                        "id-older" to SongDisplayEntry(1, "Older Song", "Hymnal", 3),
                        "id-newer" to SongDisplayEntry(2, "This Year Song", "Hymnal", 2),
                    ),
                    verseDisplayCounts = mapOf(
                        "KJV::John::3::16" to VerseDisplayEntry("KJV", "John", 3, 16, 4),
                        "KJV::Psalms::23::1" to VerseDisplayEntry("KJV", "Psalms", 23, 1, 1),
                    ),
                ),
            )
        )
        logFile.writeText(
            json.encodeToString(PlayEventLog.serializer(), PlayEventLog(songEvents, verseEvents))
        )
    }

    private fun yearRange(year: Int) = StatisticsPeriod.Year(year).resolve(LocalDate.of(THIS_YEAR, 6, 1), null)

    @Test
    fun `a year's top songs hold only that year's plays`() {
        seedTwoYears()
        val stats = StatisticsManager()
        val range = yearRange(LAST_YEAR)

        val songs = stats.songsIn(range.fromMs..range.toMs)

        assertEquals(listOf("Older Song"), songs.map { it.title })
        assertEquals(3, songs.single().count)
    }

    @Test
    fun `a year's top verses hold only that year's readings`() {
        seedTwoYears()
        val stats = StatisticsManager()
        val range = yearRange(THIS_YEAR)

        val verses = stats.versesIn(range.fromMs..range.toMs)

        assertEquals(listOf("Psalms"), verses.map { it.bookName })
    }

    @Test
    fun `clearing a song for one year leaves its other years alone`() {
        seedTwoYears()
        val stats = StatisticsManager()
        val range = yearRange(LAST_YEAR)

        val removed = stats.clearSong(SongKey("Hymnal", 1, "Older Song"), range.fromMs, range.toMs)

        assertEquals(3, removed, "all three of last year's plays go")
        val thisYear = yearRange(THIS_YEAR)
        assertEquals(
            listOf("This Year Song"),
            stats.songsIn(thisYear.fromMs..thisYear.toMs).map { it.title },
            "this year is untouched",
        )
        assertTrue(
            stats.getAllSongsInRange(range.fromMs, range.toMs).isEmpty(),
            "and nothing of the cleared year is left to report",
        )
    }

    @Test
    fun `clearing a song for a period drops its all-time count by what was removed`() {
        seedTwoYears()
        val stats = StatisticsManager()
        val range = yearRange(THIS_YEAR)

        stats.clearSong(SongKey("Hymnal", 2, "This Year Song"), range.fromMs, range.toMs)

        val remaining = stats.getTopSongsBySongbook().getValue("Hymnal")
        assertEquals(listOf("Older Song"), remaining.map { it.title }, "a count reduced to zero takes the row with it")
        assertEquals(3, remaining.single().count, "the other song's tally is untouched")
    }

    @Test
    fun `a song cleared for its whole history survives a restart`() {
        seedTwoYears()
        val stats = StatisticsManager()

        val removed = stats.clearSong(SongKey("Hymnal", 1, "Older Song"))

        assertEquals(3, removed)
        assertEquals(
            listOf("This Year Song"),
            restarted().getTopSongsBySongbook().getValue("Hymnal").map { it.title },
            "both files must have been written, or it comes back",
        )
    }

    @Test
    fun `clearing a verse for one year leaves its other years alone`() {
        seedTwoYears()
        val stats = StatisticsManager()
        val range = yearRange(LAST_YEAR)

        val removed = stats.clearVerse(VerseKey("KJV", "John", 3, 16), range.fromMs, range.toMs)

        assertEquals(4, removed)
        assertEquals(
            listOf("Psalms"),
            restarted().getTopVersesByBible().getValue("KJV").map { it.bookName },
        )
    }

    @Test
    fun `clearing a song that was only ever played outside the period changes nothing`() {
        seedTwoYears()
        val stats = StatisticsManager()
        val range = yearRange(THIS_YEAR)

        val removed = stats.clearSong(SongKey("Hymnal", 1, "Older Song"), range.fromMs, range.toMs)

        assertEquals(0, removed)
        assertEquals(3, stats.getTopSongsBySongbook().getValue("Hymnal").first { it.title == "Older Song" }.count)
    }

    @Test
    fun `a play recorded before the event log existed cannot be cleared by period`() {
        statsFile.parentFile.mkdirs()
        statsFile.writeText(
            Json.encodeToString(
                DisplayStatistics.serializer(),
                DisplayStatistics(songDisplayCounts = mapOf("id" to SongDisplayEntry(1, "Undated", "Hymnal", 7))),
            )
        )
        val stats = StatisticsManager()
        val range = yearRange(THIS_YEAR)

        val removed = stats.clearSong(SongKey("Hymnal", 1, "Undated"), range.fromMs, range.toMs)

        assertEquals(0, removed, "there are no dated events to remove")
        assertEquals(
            7,
            stats.getTopSongsBySongbook().getValue("Hymnal").single().count,
            "so the row stays at its full tally rather than silently losing plays",
        )
    }

    @Test
    fun `clearing the whole history of a song removes its row outright`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace", number = 12, times = 4)

        val removed = stats.clearSong(SongKey("Hymnal", 12, "Amazing Grace"))

        assertEquals(4, removed)
        assertTrue(stats.getTopSongsBySongbook().isEmpty())
        assertFalse(stats.hasEventLog())
    }

    @Test
    fun `clearing one song leaves every other song untouched`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace", number = 12, times = 4)
        stats.sing("Be Thou My Vision", number = 13, times = 2)

        stats.clearSong(SongKey("Hymnal", 12, "Amazing Grace"))

        assertEquals(
            listOf("Be Thou My Vision"),
            stats.getTopSongsBySongbook().getValue("Hymnal").map { it.title },
        )
    }

    // ── What the report window asks before it opens ─────────────────────────────

    @Test
    fun `a log holding only songs still offers a report`() {
        seedLog(songs = listOf(SongPlayEvent(1, "Amazing Grace", "Hymnal", "", stamp(LAST_YEAR, 5, 4))))
        val stats = StatisticsManager()

        assertTrue(stats.hasEventLog(), "a song-only log is still a log")
        assertEquals(stamp(LAST_YEAR, 5, 4), stats.getEarliestEventTime())
    }

    @Test
    fun `a log holding only verses still offers a report`() {
        seedLog(verses = listOf(VersePlayEvent("KJV", "John", 3, 16, stamp(LAST_YEAR, 5, 4))))
        val stats = StatisticsManager()

        assertTrue(stats.hasEventLog(), "a verse-only log is still a log")
        assertEquals(stamp(LAST_YEAR, 5, 4), stats.getEarliestEventTime())
    }

    @Test
    fun `the earliest event is the oldest of the two logs, whichever holds it`() {
        seedLog(
            songs = listOf(SongPlayEvent(1, "Amazing Grace", "Hymnal", "", stamp(THIS_YEAR, 2, 1))),
            verses = listOf(VersePlayEvent("KJV", "John", 3, 16, stamp(LAST_YEAR, 5, 4))),
        )

        assertEquals(stamp(LAST_YEAR, 5, 4), StatisticsManager().getEarliestEventTime(), "the verse is older")

        seedLog(
            songs = listOf(SongPlayEvent(1, "Amazing Grace", "Hymnal", "", stamp(LAST_YEAR, 5, 4))),
            verses = listOf(VersePlayEvent("KJV", "John", 3, 16, stamp(THIS_YEAR, 2, 1))),
        )

        assertEquals(stamp(LAST_YEAR, 5, 4), StatisticsManager().getEarliestEventTime(), "the song is older")
    }

    @Test
    fun `clearing the whole history of a verse removes its row outright`() {
        seedTwoYears()
        val stats = StatisticsManager()

        val removed = stats.clearVerse(VerseKey("KJV", "John", 3, 16))

        assertEquals(4, removed)
        assertEquals(
            listOf("Psalms"),
            restarted().getTopVersesByBible().getValue("KJV").map { it.bookName },
            "a verse cleared outright must not come back after a restart",
        )
    }

    /** The wall-clock stamp `seedTwoYears` writes its events with. */
    private fun stamp(year: Int, month: Int, day: Int) =
        LocalDate.of(year, month, day).atTime(10, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Writes a play log directly, for the queries that read one without a tally beside it. */
    private fun seedLog(songs: List<SongPlayEvent> = emptyList(), verses: List<VersePlayEvent> = emptyList()) {
        logFile.parentFile.mkdirs()
        logFile.writeText(
            Json { encodeDefaults = true }.encodeToString(PlayEventLog.serializer(), PlayEventLog(songs, verses))
        )
    }

    private companion object {
        const val THIS_YEAR = 2026
        const val LAST_YEAR = 2025
    }
}
