package org.churchpresenter.app.churchpresenter.data

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors

@Serializable
data class DisplayStatistics(
    val songDisplayCounts: Map<String, SongDisplayEntry> = emptyMap(),
    val verseDisplayCounts: Map<String, VerseDisplayEntry> = emptyMap()
)

@Serializable
data class SongDisplayEntry(
    val songNumber: Int = 0,
    val title: String = "",
    val songbook: String = "",
    val count: Int = 0
)

@Serializable
data class VerseDisplayEntry(
    val bibleName: String = "",
    val bookName: String = "",
    val chapter: Int = 0,
    val verseNumber: Int = 0,
    val count: Int = 0
)

class StatisticsManager {
    private val lock = Any()
    private val userHome = System.getProperty("user.home")
    private val appDataDir = File(userHome, ".churchpresenter")
    private val statsFile = File(appDataDir, "statistics.json")

    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var statistics: DisplayStatistics = loadStatistics()

    private fun loadStatistics(): DisplayStatistics {
        return try {
            if (statsFile.exists()) {
                jsonFormat.decodeFromString<DisplayStatistics>(statsFile.readText())
            } else {
                DisplayStatistics()
            }
        } catch (e: Exception) {
            DisplayStatistics()
        }
    }

    private fun save() {
        try {
            val json = jsonFormat.encodeToString(statistics)
            statsFile.writeText(json)
        } catch (_: Exception) { }
    }

    fun recordSongDisplay(songNumber: Int, title: String, songbook: String) {
        synchronized(lock) {
            val key = "$songbook::$songNumber"
            val existing = statistics.songDisplayCounts[key]
            val entry = SongDisplayEntry(
                songNumber = songNumber,
                title = title,
                songbook = songbook,
                count = (existing?.count ?: 0) + 1
            )
            statistics = statistics.copy(
                songDisplayCounts = statistics.songDisplayCounts + (key to entry)
            )
            save()
        }
    }

    fun recordVerseDisplay(bibleName: String, bookName: String, chapter: Int, verseNumber: Int) {
        synchronized(lock) {
            val key = "$bibleName::$bookName::$chapter::$verseNumber"
            val existing = statistics.verseDisplayCounts[key]
            val entry = VerseDisplayEntry(
                bibleName = bibleName,
                bookName = bookName,
                chapter = chapter,
                verseNumber = verseNumber,
                count = (existing?.count ?: 0) + 1
            )
            statistics = statistics.copy(
                verseDisplayCounts = statistics.verseDisplayCounts + (key to entry)
            )
            save()
        }
    }

    /** Returns top songs grouped by songbook, each list sorted by count descending. */
    fun getTopSongsBySongbook(limit: Int = 15): Map<String, List<SongDisplayEntry>> {
        return statistics.songDisplayCounts.values
            .groupBy { it.songbook }
            .mapValues { (_, entries) ->
                entries.sortedByDescending { it.count }.take(limit)
            }
    }

    /** Returns top verses grouped by Bible name, each list sorted by count descending. */
    fun getTopVersesByBible(limit: Int = 15): Map<String, List<VerseDisplayEntry>> {
        return statistics.verseDisplayCounts.values
            .groupBy { it.bibleName }
            .mapValues { (_, entries) ->
                entries.sortedByDescending { it.count }.take(limit)
            }
    }

    fun clearStatistics() {
        statistics = DisplayStatistics()
        save()
    }

    /**
     * Exports statistics to an XLS file (Excel 97-2003 format) at the given [file] path.
     * Returns true on success, false if an error occurred.
     */
    fun exportStatisticsToXls(file: File): Boolean {
        return try {
            val workbook = HSSFWorkbook()

            // Header cell style — light blue background, bold
            val headerStyle = workbook.createCellStyle().apply {
                fillForegroundColor = IndexedColors.LIGHT_CORNFLOWER_BLUE.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                val boldFont = workbook.createFont().apply { bold = true }
                setFont(boldFont)
            }

            // ── Songs sheet ──────────────────────────────────────────────────────────
            val songsSheet = workbook.createSheet("Top Songs")
            var rowIndex = 0
            val allSongs = statistics.songDisplayCounts.values
                .sortedByDescending { it.count }

            val songHeaderRow = songsSheet.createRow(rowIndex++)
            listOf("Rank", "Songbook", "Number", "Title", "Count").forEachIndexed { col, label ->
                songHeaderRow.createCell(col).also {
                    it.setCellValue(label)
                    it.cellStyle = headerStyle
                }
            }

            // Group by songbook and write rows
            allSongs.groupBy { it.songbook }
                .toSortedMap()
                .forEach { (_, entries) ->
                    entries.sortedByDescending { it.count }.forEachIndexed { rank, entry ->
                        val row = songsSheet.createRow(rowIndex++)
                        row.createCell(0).setCellValue((rank + 1).toDouble())
                        row.createCell(1).setCellValue(entry.songbook)
                        row.createCell(2).setCellValue(entry.songNumber.toDouble())
                        row.createCell(3).setCellValue(entry.title)
                        row.createCell(4).setCellValue(entry.count.toDouble())
                    }
                }

            // Auto-size columns
            (0..4).forEach { songsSheet.autoSizeColumn(it) }

            // ── Verses sheet ─────────────────────────────────────────────────────────
            val versesSheet = workbook.createSheet("Top Verses")
            rowIndex = 0
            val allVerses = statistics.verseDisplayCounts.values
                .sortedByDescending { it.count }

            val verseHeaderRow = versesSheet.createRow(rowIndex++)
            listOf("Rank", "Bible", "Book", "Chapter", "Verse", "Count").forEachIndexed { col, label ->
                verseHeaderRow.createCell(col).also {
                    it.setCellValue(label)
                    it.cellStyle = headerStyle
                }
            }

            allVerses.groupBy { it.bibleName }
                .toSortedMap()
                .forEach { (_, entries) ->
                    entries.sortedByDescending { it.count }.forEachIndexed { rank, entry ->
                        val row = versesSheet.createRow(rowIndex++)
                        row.createCell(0).setCellValue((rank + 1).toDouble())
                        row.createCell(1).setCellValue(entry.bibleName)
                        row.createCell(2).setCellValue(entry.bookName)
                        row.createCell(3).setCellValue(entry.chapter.toDouble())
                        row.createCell(4).setCellValue(entry.verseNumber.toDouble())
                        row.createCell(5).setCellValue(entry.count.toDouble())
                    }
                }

            (0..5).forEach { versesSheet.autoSizeColumn(it) }

            // Write file
            file.outputStream().use { workbook.write(it) }
            workbook.close()
            true
        } catch (_: Exception) {
            false
        }
    }
}
