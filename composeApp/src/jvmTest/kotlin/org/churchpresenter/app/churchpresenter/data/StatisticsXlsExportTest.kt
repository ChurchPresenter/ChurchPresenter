package org.churchpresenter.app.churchpresenter.data

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Sheet
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The all-time statistics spreadsheet.
 *
 * This is the workbook the statistics screen exports: two sheets, one ranking every song that has
 * been sung and one every verse that has been read, taken from the all-time tally rather than the
 * timestamped log the CCLI report uses (that one is covered by [StatisticsManagerTest]). It is
 * handed to whoever plans the services, so what matters is that a number in it means what the
 * column says: the ranking restarts within each songbook, and a song sung under two songbooks is
 * two rows rather than one.
 *
 * It is also the only export written entirely by hand with POI — the file is opened in Excel and
 * Numbers, so an unreadable workbook or a count written as text is a failure the app cannot see.
 *
 * `user.home` is swapped per test, as the manager resolves its files from it at construction.
 */
class StatisticsXlsExportTest {

    private lateinit var tempHome: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-statistics-xls-test").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        File(tempHome, ".churchpresenter").mkdirs()
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    private fun StatisticsManager.sing(
        title: String,
        number: Int = 1,
        songbook: String = "Hymnal",
        times: Int = 1,
    ) = repeat(times) { recordSongDisplay("$songbook::$number", number, title, songbook) }

    private fun StatisticsManager.read(
        book: String,
        chapter: Int = 3,
        verse: Int = 16,
        bible: String = "KJV",
        times: Int = 1,
    ) = repeat(times) { recordVerseDisplay(bible, book, chapter, verse) }

    /** Exports [stats] and hands back the workbook, read from the file as Excel would read it. */
    private fun exported(stats: StatisticsManager): HSSFWorkbook {
        val file = File(tempHome, "statistics.xls")
        assertTrue(stats.exportStatisticsToXls(file), "the export reported failure")
        assertTrue(file.length() > 0, "the export reported success but wrote nothing")
        return file.inputStream().use { HSSFWorkbook(it) }
    }

    /** Every row of [sheet] below the header, each cell as the string or number it was written as. */
    private fun rows(sheet: Sheet): List<List<String>> =
        (1..sheet.lastRowNum).mapNotNull { sheet.getRow(it) }.map { row ->
            (0 until row.lastCellNum).map { col ->
                val cell = row.getCell(col)
                when {
                    cell == null -> ""
                    cell.cellType == CellType.NUMERIC ->
                        cell.numericCellValue.toInt().toString()
                    else -> cell.stringCellValue
                }
            }
        }

    private fun header(sheet: Sheet): List<String> =
        (0 until sheet.getRow(0).lastCellNum).map { sheet.getRow(0).getCell(it).stringCellValue }

    // ── The shape of the workbook ───────────────────────────────────────────────

    @Test
    fun `the workbook has a sheet for songs and one for verses`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace")
        stats.read("John")

        val workbook = exported(stats)

        assertEquals(2, workbook.numberOfSheets)
        assertEquals("Top Songs", workbook.getSheetName(0))
        assertEquals("Top Verses", workbook.getSheetName(1))
    }

    @Test
    fun `each sheet names its columns`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace")
        stats.read("John")

        val workbook = exported(stats)

        assertEquals(listOf("Rank", "Songbook", "Number", "Title", "Count"), header(workbook.getSheetAt(0)))
        assertEquals(listOf("Rank", "Bible", "Book", "Chapter", "Verse", "Count"), header(workbook.getSheetAt(1)))
    }

    @Test
    fun `an export with nothing recorded is still a valid workbook`() {
        val workbook = exported(StatisticsManager())

        assertEquals(2, workbook.numberOfSheets, "a church that has just installed the app can still press export")
        assertTrue(rows(workbook.getSheetAt(0)).isEmpty())
        assertTrue(rows(workbook.getSheetAt(1)).isEmpty())
    }

    // ── What the song sheet says ────────────────────────────────────────────────

    @Test
    fun `a song is listed with its songbook, number and count`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace", number = 42, songbook = "Hymnal", times = 3)

        val row = rows(exported(stats).getSheetAt(0)).single()

        assertEquals(listOf("1", "Hymnal", "42", "Amazing Grace", "3"), row)
    }

    @Test
    fun `songs are ranked with the most sung first`() {
        val stats = StatisticsManager()
        stats.sing("Sung once", number = 1, times = 1)
        stats.sing("Sung five times", number = 2, times = 5)
        stats.sing("Sung three times", number = 3, times = 3)

        val sheet = rows(exported(stats).getSheetAt(0))

        assertEquals(listOf("Sung five times", "Sung three times", "Sung once"), sheet.map { it[3] })
        assertEquals(listOf("1", "2", "3"), sheet.map { it[0] }, "the rank column has to agree with the order")
        assertEquals(listOf("5", "3", "1"), sheet.map { it[4] })
    }

    @Test
    fun `the ranking restarts within each songbook`() {
        val stats = StatisticsManager()
        stats.sing("Hymnal favourite", number = 1, songbook = "Hymnal", times = 5)
        stats.sing("Hymnal other", number = 2, songbook = "Hymnal", times = 2)
        stats.sing("Praise favourite", number = 1, songbook = "Songs of Praise", times = 4)

        val sheet = rows(exported(stats).getSheetAt(0))

        assertEquals(
            listOf("Hymnal" to "1", "Hymnal" to "2", "Songs of Praise" to "1"),
            sheet.map { it[1] to it[0] },
            "rank 1 means 'most sung in this songbook', not 'most sung overall'",
        )
    }

    @Test
    fun `songbooks are listed in a stable order`() {
        val stats = StatisticsManager()
        stats.sing("C song", songbook = "Zion Hymns")
        stats.sing("A song", songbook = "Ancient Hymns")
        stats.sing("B song", songbook = "Modern Hymns")

        val sheet = rows(exported(stats).getSheetAt(0))

        assertEquals(
            listOf("Ancient Hymns", "Modern Hymns", "Zion Hymns"),
            sheet.map { it[1] },
            "two exports of the same data must not shuffle the rows",
        )
    }

    @Test
    fun `the same song in two songbooks is two rows`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace", number = 1, songbook = "Hymnal", times = 2)
        stats.sing("Amazing Grace", number = 1, songbook = "Songs of Praise", times = 1)

        val sheet = rows(exported(stats).getSheetAt(0))

        assertEquals(2, sheet.size, "merging them would credit one songbook with the other's use")
        assertEquals(listOf("2", "1"), sheet.map { it[4] })
    }

    @Test
    fun `counts and numbers are written as numbers rather than text`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace", number = 42, times = 3)

        val row = exported(stats).getSheetAt(0).getRow(1)

        listOf(0, 2, 4).forEach {
            assertEquals(
                CellType.NUMERIC,
                row.getCell(it).cellType,
                "column $it has to sort and total in the spreadsheet, so it cannot be text",
            )
        }
    }

    // ── What the verse sheet says ───────────────────────────────────────────────

    @Test
    fun `a verse is listed with its bible, reference and count`() {
        val stats = StatisticsManager()
        stats.read("John", chapter = 3, verse = 16, bible = "KJV", times = 4)

        val row = rows(exported(stats).getSheetAt(1)).single()

        assertEquals(listOf("1", "KJV", "John", "3", "16", "4"), row)
    }

    @Test
    fun `verses are ranked within each bible`() {
        val stats = StatisticsManager()
        stats.read("John", verse = 16, bible = "KJV", times = 5)
        stats.read("Psalms", chapter = 23, verse = 1, bible = "KJV", times = 2)
        stats.read("John", verse = 16, bible = "NIV", times = 9)

        val sheet = rows(exported(stats).getSheetAt(1))

        assertEquals(
            listOf("KJV" to "1", "KJV" to "2", "NIV" to "1"),
            sheet.map { it[1] to it[0] },
        )
        assertEquals(
            listOf("5", "2", "9"),
            sheet.map { it[5] },
            "the same verse read from two translations is counted under each",
        )
    }

    @Test
    fun `two verses of the same chapter are separate rows`() {
        val stats = StatisticsManager()
        stats.read("John", chapter = 3, verse = 16)
        stats.read("John", chapter = 3, verse = 17)

        val sheet = rows(exported(stats).getSheetAt(1))

        assertEquals(listOf("16", "17"), sheet.map { it[4] }.sorted())
    }

    // ── What survives a restart, and what fails ─────────────────────────────────

    @Test
    fun `the export reports what was recorded before the app was restarted`() {
        StatisticsManager().sing("Amazing Grace", times = 2)

        val row = rows(exported(StatisticsManager()).getSheetAt(0)).single()

        assertEquals("2", row[4], "the tally is on disk — a restart between the service and the export is normal")
    }

    @Test
    fun `an export to a folder that does not exist reports failure rather than throwing`() {
        val stats = StatisticsManager()
        stats.sing("Amazing Grace")

        assertFalse(
            stats.exportStatisticsToXls(File(tempHome, "no/such/folder/statistics.xls")),
            "the dialog needs a false to show an error rather than claiming the file was written",
        )
    }
}
