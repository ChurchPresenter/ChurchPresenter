package org.churchpresenter.app.churchpresenter.data

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where the CCLI number on the licence report comes from.
 *
 * The event log deliberately does not store one — see [SongPlayEvent], which holds only title,
 * songbook, number and author — so every number in the report is resolved at export time by reading
 * the song catalog off disk and matching against it. That matching is the whole report: a song that
 * fails to match is filed with an empty CCLI column, which is a licence report that does not credit
 * the song, and a song that matches the WRONG catalog entry credits somebody else's.
 *
 * Two keys, in order: songbook + song number first (stable when a title is edited), then songbook +
 * lowercased title. A song sung before its catalog entry existed simply gets no number rather than
 * failing the export.
 *
 * `user.home` is swapped per test — the manager resolves its files from it at construction, and the
 * catalog path is read from settings on every query.
 */
class StatisticsCcliLookupTest {

    private lateinit var tempHome: File
    private var realHome: String? = null
    private lateinit var catalog: File

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-statistics-ccli-test").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        File(tempHome, ".churchpresenter").mkdirs()
        catalog = File(tempHome, "songs").also { it.mkdirs() }
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    /** Points the app's song library at [dir], as the songs settings tab does. */
    private fun useCatalog(dir: File?) {
        val manager = SettingsManager()
        val settings = manager.loadSettings()
        manager.saveSettings(
            settings.copy(songSettings = settings.songSettings.copy(storageDirectory = dir?.absolutePath ?: "")),
        )
    }

    /** Writes a `.song` file into [songbook] (a folder under the catalog, or the catalog root). */
    private fun catalogSong(
        number: String,
        title: String,
        ccli: String,
        songbook: String = "Hymnal",
        author: String = "",
    ) {
        val dir = if (songbook.isEmpty()) catalog else File(catalog, songbook).also { it.mkdirs() }
        SongFileParser().writeSongFile(
            SongItem(number = number, title = title, ccliNumber = ccli, author = author, lyrics = listOf("line")),
            File(dir, "$number - $title.song").absolutePath,
        )
    }

    private fun sung(title: String, number: Int = 1, songbook: String = "Hymnal", author: String = ""): StatisticsManager =
        StatisticsManager().also { it.recordSongDisplay("$songbook::$number", number, title, songbook, author) }

    private fun ccliOf(stats: StatisticsManager, title: String): String =
        stats.getAllSongsInRange(0L, Long.MAX_VALUE).first { it.title == title }.ccliNumber

    // ── Matching against the catalog ────────────────────────────────────────────

    @Test
    fun `a song is credited with the number in the catalog`() {
        catalogSong(number = "42", title = "Amazing Grace", ccli = "22025")
        useCatalog(catalog)

        assertEquals("22025", ccliOf(sung("Amazing Grace", number = 42), "Amazing Grace"))
    }

    @Test
    fun `a song renamed since it was sung is still matched by its number`() {
        // The event log holds the title as it was on the day; the catalog holds it as it is now.
        catalogSong(number = "42", title = "Amazing Grace (My Chains Are Gone)", ccli = "22025")
        useCatalog(catalog)

        assertEquals(
            "22025",
            ccliOf(sung("Amazing Grace", number = 42), "Amazing Grace"),
            "matching on the number first is what makes an edited title harmless",
        )
    }

    @Test
    fun `a song with no number is matched by its title`() {
        catalogSong(number = "0", title = "Amazing Grace", ccli = "22025")
        useCatalog(catalog)

        assertEquals(
            "22025",
            ccliOf(sung("Amazing Grace", number = 0), "Amazing Grace"),
            "songs imported without numbering still have to reach the report",
        )
    }

    @Test
    fun `a title is matched whatever its case`() {
        catalogSong(number = "0", title = "Amazing Grace", ccli = "22025")
        useCatalog(catalog)

        assertEquals("22025", ccliOf(sung("AMAZING GRACE", number = 0), "AMAZING GRACE"))
    }

    @Test
    fun `the same number in two songbooks does not cross over`() {
        catalogSong(number = "1", title = "Hymnal one", ccli = "1111", songbook = "Hymnal")
        catalogSong(number = "1", title = "Praise one", ccli = "2222", songbook = "Songs of Praise")
        useCatalog(catalog)

        val stats = sung("Praise one", number = 1, songbook = "Songs of Praise")

        assertEquals(
            "2222",
            ccliOf(stats, "Praise one"),
            "crediting the other songbook's song would file the report against the wrong licence",
        )
    }

    // ── When there is nothing to match against ──────────────────────────────────

    @Test
    fun `a song that is not in the catalog is reported without a number`() {
        catalogSong(number = "42", title = "Amazing Grace", ccli = "22025")
        useCatalog(catalog)

        assertEquals(
            "",
            ccliOf(sung("Something Else", number = 99), "Something Else"),
            "an unmatched song still belongs on the report, just without a number",
        )
    }

    @Test
    fun `a catalog song with no number of its own contributes nothing`() {
        catalogSong(number = "42", title = "Amazing Grace", ccli = "")
        useCatalog(catalog)

        assertEquals("", ccliOf(sung("Amazing Grace", number = 42), "Amazing Grace"))
    }

    @Test
    fun `no song library configured still produces a report`() {
        useCatalog(null)

        val summaries = sung("Amazing Grace", number = 42).getAllSongsInRange(0L, Long.MAX_VALUE)

        assertEquals(1, summaries.size, "a church that presents from the schedule only still owes a report")
        assertEquals("", summaries.single().ccliNumber)
    }

    @Test
    fun `a song library folder that has been moved away still produces a report`() {
        useCatalog(File(tempHome, "songs-on-a-drive-that-is-not-plugged-in"))

        val summaries = sung("Amazing Grace", number = 42).getAllSongsInRange(0L, Long.MAX_VALUE)

        assertEquals(1, summaries.size, "the export must not fail on the day the library is unreachable")
        assertEquals("", summaries.single().ccliNumber)
    }

    // ── What reaches the exported file ──────────────────────────────────────────

    @Test
    fun `the resolved number is written into the exported report`() {
        catalogSong(number = "42", title = "Amazing Grace", ccli = "22025", author = "John Newton")
        useCatalog(catalog)
        val stats = sung("Amazing Grace", number = 42, author = "John Newton")

        val file = File(tempHome, "ccli.csv")
        assertTrue(stats.exportCcliCsv(file, 0L, Long.MAX_VALUE))

        val row = file.readLines().drop(1).single()
        assertTrue("22025" in row, "the number is the one column a licence body actually reads: $row")
        assertTrue("Amazing Grace" in row)
    }

    @Test
    fun `songs sung more than once are one row carrying one number`() {
        catalogSong(number = "42", title = "Amazing Grace", ccli = "22025")
        useCatalog(catalog)
        val stats = StatisticsManager()
        repeat(3) { stats.recordSongDisplay("Hymnal::42", 42, "Amazing Grace", "Hymnal") }

        val summary = stats.getAllSongsInRange(0L, Long.MAX_VALUE).single()

        assertEquals(3, summary.count, "the count is what the licence is reported against")
        assertEquals("22025", summary.ccliNumber)
    }
}
