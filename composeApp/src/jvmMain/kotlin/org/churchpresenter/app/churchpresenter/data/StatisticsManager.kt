package org.churchpresenter.app.churchpresenter.data

import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.churchpresenter.app.churchpresenter.utils.AnalyticsReporter

// ── Aggregate statistics (existing, all-time) ─────────────────────────────────

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

// ── Timestamped event log ─────────────────────────────────────────────────────

@Serializable
data class SongPlayEvent(
    val songNumber: Int = 0,
    val title: String = "",
    val songbook: String = "",
    val author: String = "",
    val timestamp: Long = 0L
)

@Serializable
data class VersePlayEvent(
    val bibleName: String = "",
    val bookName: String = "",
    val chapter: Int = 0,
    val verseNumber: Int = 0,
    val timestamp: Long = 0L
)

@Serializable
data class PlayEventLog(
    val songEvents: List<SongPlayEvent> = emptyList(),
    val verseEvents: List<VersePlayEvent> = emptyList()
)

// ── Computed summaries (in-memory only) ───────────────────────────────────────

data class SongSummary(
    val songNumber: Int,
    val title: String,
    val songbook: String,
    val author: String,
    val count: Int,
    val firstUsed: Long,
    val lastUsed: Long
)

data class VerseSummary(
    val bibleName: String,
    val bookName: String,
    val chapter: Int,
    val verseNumber: Int,
    val count: Int,
    val firstUsed: Long,
    val lastUsed: Long
)

data class ActivityPoint(
    val label: String,
    val songCount: Int,
    val verseCount: Int
)

// ── Manager ───────────────────────────────────────────────────────────────────

class StatisticsManager {
    private val lock = Any()
    private val userHome = System.getProperty("user.home")
    private val appDataDir = File(userHome, ".churchpresenter")
    private val statsFile = File(appDataDir, "statistics.json")
    private val logFile = File(appDataDir, "play_log.json")

    private val jsonFormat = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private var statistics: DisplayStatistics = loadStatistics()
    private var eventLog: PlayEventLog = loadEventLog()

    private fun loadStatistics(): DisplayStatistics = try {
        if (statsFile.exists()) jsonFormat.decodeFromString(statsFile.readText()) else DisplayStatistics()
    } catch (_: Exception) { DisplayStatistics() }

    private fun loadEventLog(): PlayEventLog = try {
        if (logFile.exists()) jsonFormat.decodeFromString(logFile.readText()) else PlayEventLog()
    } catch (_: Exception) { PlayEventLog() }

    private fun save() {
        try { statsFile.writeText(jsonFormat.encodeToString(statistics)) } catch (_: Exception) {}
    }

    private fun saveLog() {
        try { logFile.writeText(jsonFormat.encodeToString(eventLog)) } catch (_: Exception) {}
    }

    // ── Recording ─────────────────────────────────────────────────────────────

    fun recordSongDisplay(songNumber: Int, title: String, songbook: String, author: String = "") {
        synchronized(lock) {
            val key = "$songbook::$songNumber"
            val existing = statistics.songDisplayCounts[key]
            statistics = statistics.copy(
                songDisplayCounts = statistics.songDisplayCounts + (key to SongDisplayEntry(
                    songNumber = songNumber, title = title, songbook = songbook,
                    count = (existing?.count ?: 0) + 1
                ))
            )
            eventLog = eventLog.copy(
                songEvents = eventLog.songEvents + SongPlayEvent(
                    songNumber = songNumber, title = title, songbook = songbook,
                    author = author, timestamp = System.currentTimeMillis()
                )
            )
            save()
            saveLog()
        }
        AnalyticsReporter.logSongDisplayed(songNumber, title, songbook)
    }

    fun recordVerseDisplay(bibleName: String, bookName: String, chapter: Int, verseNumber: Int) {
        synchronized(lock) {
            val key = "$bibleName::$bookName::$chapter::$verseNumber"
            val existing = statistics.verseDisplayCounts[key]
            statistics = statistics.copy(
                verseDisplayCounts = statistics.verseDisplayCounts + (key to VerseDisplayEntry(
                    bibleName = bibleName, bookName = bookName, chapter = chapter,
                    verseNumber = verseNumber, count = (existing?.count ?: 0) + 1
                ))
            )
            eventLog = eventLog.copy(
                verseEvents = eventLog.verseEvents + VersePlayEvent(
                    bibleName = bibleName, bookName = bookName, chapter = chapter,
                    verseNumber = verseNumber, timestamp = System.currentTimeMillis()
                )
            )
            save()
            saveLog()
        }
        AnalyticsReporter.logVerseDisplayed(bibleName, bookName, chapter, verseNumber)
    }

    // ── All-time aggregate queries (used by existing StatisticsDialog) ─────────

    fun getTopSongsBySongbook(limit: Int = 15): Map<String, List<SongDisplayEntry>> =
        statistics.songDisplayCounts.values
            .groupBy { it.songbook }
            .mapValues { (_, entries) -> entries.sortedByDescending { it.count }.take(limit) }

    fun getTopVersesByBible(limit: Int = 15): Map<String, List<VerseDisplayEntry>> =
        statistics.verseDisplayCounts.values
            .groupBy { it.bibleName }
            .mapValues { (_, entries) -> entries.sortedByDescending { it.count }.take(limit) }

    // ── Event log queries (used by CCLI report) ───────────────────────────────

    fun getEarliestEventTime(): Long? = synchronized(lock) {
        val songMin = eventLog.songEvents.minOfOrNull { it.timestamp }
        val verseMin = eventLog.verseEvents.minOfOrNull { it.timestamp }
        listOfNotNull(songMin, verseMin).minOrNull()
    }

    fun hasEventLog(): Boolean = synchronized(lock) {
        eventLog.songEvents.isNotEmpty() || eventLog.verseEvents.isNotEmpty()
    }

    fun getAllSongsInRange(fromMs: Long, toMs: Long): List<SongSummary> = synchronized(lock) {
        eventLog.songEvents
            .filter { it.timestamp in fromMs..toMs }
            .groupBy { "${it.songbook}::${it.songNumber}::${it.title}" }
            .map { (_, events) ->
                val e = events.first()
                SongSummary(
                    songNumber = e.songNumber,
                    title = e.title,
                    songbook = e.songbook,
                    author = events.firstOrNull { it.author.isNotBlank() }?.author ?: "",
                    count = events.size,
                    firstUsed = events.minOf { it.timestamp },
                    lastUsed = events.maxOf { it.timestamp }
                )
            }
            .sortedByDescending { it.count }
    }

    fun getAllVersesInRange(fromMs: Long, toMs: Long): List<VerseSummary> = synchronized(lock) {
        eventLog.verseEvents
            .filter { it.timestamp in fromMs..toMs }
            .groupBy { "${it.bibleName}::${it.bookName}::${it.chapter}::${it.verseNumber}" }
            .map { (_, events) ->
                val e = events.first()
                VerseSummary(
                    bibleName = e.bibleName,
                    bookName = e.bookName,
                    chapter = e.chapter,
                    verseNumber = e.verseNumber,
                    count = events.size,
                    firstUsed = events.minOf { it.timestamp },
                    lastUsed = events.maxOf { it.timestamp }
                )
            }
            .sortedByDescending { it.count }
    }

    fun getActivityByPeriod(fromMs: Long, toMs: Long): List<ActivityPoint> = synchronized(lock) {
        val dayMs = 86_400_000L
        val weekMs = 7 * dayMs
        val rangeMs = toMs - fromMs

        val songEvents = eventLog.songEvents.filter { it.timestamp in fromMs..toMs }
        val verseEvents = eventLog.verseEvents.filter { it.timestamp in fromMs..toMs }

        when {
            rangeMs <= 90 * dayMs -> {
                // Weekly buckets
                val weekStart = (fromMs / weekMs) * weekMs
                val numWeeks = ((toMs - weekStart) / weekMs + 1).toInt().coerceIn(1, 52)
                val labelFmt = SimpleDateFormat("MMM d", Locale.getDefault())
                (0 until numWeeks).map { i ->
                    val wStart = weekStart + i * weekMs
                    val wEnd = wStart + weekMs
                    ActivityPoint(
                        label = labelFmt.format(Date(wStart)),
                        songCount = songEvents.count { it.timestamp in wStart until wEnd },
                        verseCount = verseEvents.count { it.timestamp in wStart until wEnd }
                    )
                }
            }
            rangeMs <= 730 * dayMs -> {
                // Monthly buckets
                val zone = ZoneId.systemDefault()
                val fromLocal = Instant.ofEpochMilli(fromMs).atZone(zone).toLocalDate()
                val toLocal = Instant.ofEpochMilli(toMs).atZone(zone).toLocalDate()
                val labelFmt = DateTimeFormatter.ofPattern("MMM yy")
                val points = mutableListOf<ActivityPoint>()
                var cur = LocalDate.of(fromLocal.year, fromLocal.month, 1)
                val end = LocalDate.of(toLocal.year, toLocal.month, 1)
                while (!cur.isAfter(end)) {
                    val mStart = cur.atStartOfDay(zone).toInstant().toEpochMilli()
                    val mEnd = cur.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    points.add(ActivityPoint(
                        label = cur.format(labelFmt),
                        songCount = songEvents.count { it.timestamp in mStart until mEnd },
                        verseCount = verseEvents.count { it.timestamp in mStart until mEnd }
                    ))
                    cur = cur.plusMonths(1)
                }
                points
            }
            else -> {
                // Yearly buckets
                val zone = ZoneId.systemDefault()
                val fromYear = Instant.ofEpochMilli(fromMs).atZone(zone).year
                val toYear = Instant.ofEpochMilli(toMs).atZone(zone).year
                (fromYear..toYear).map { year ->
                    val yStart = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
                    val yEnd = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
                    ActivityPoint(
                        label = "$year",
                        songCount = songEvents.count { it.timestamp in yStart until yEnd },
                        verseCount = verseEvents.count { it.timestamp in yStart until yEnd }
                    )
                }
            }
        }
    }

    fun clearStatistics() {
        synchronized(lock) {
            statistics = DisplayStatistics()
            eventLog = PlayEventLog()
            save()
            saveLog()
        }
    }

    // ── Exports ───────────────────────────────────────────────────────────────

    fun exportStatisticsToXls(file: File): Boolean = try {
        val workbook = HSSFWorkbook()
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.LIGHT_CORNFLOWER_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(workbook.createFont().apply { bold = true })
        }

        val songsSheet = workbook.createSheet("Top Songs")
        var rowIndex = 0
        val songHeaderRow = songsSheet.createRow(rowIndex++)
        listOf("Rank", "Songbook", "Number", "Title", "Count").forEachIndexed { col, label ->
            songHeaderRow.createCell(col).also { it.setCellValue(label); it.cellStyle = headerStyle }
        }
        statistics.songDisplayCounts.values.sortedByDescending { it.count }
            .groupBy { it.songbook }.toSortedMap()
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
        (0..4).forEach { songsSheet.autoSizeColumn(it) }

        val versesSheet = workbook.createSheet("Top Verses")
        rowIndex = 0
        val verseHeaderRow = versesSheet.createRow(rowIndex++)
        listOf("Rank", "Bible", "Book", "Chapter", "Verse", "Count").forEachIndexed { col, label ->
            verseHeaderRow.createCell(col).also { it.setCellValue(label); it.cellStyle = headerStyle }
        }
        statistics.verseDisplayCounts.values.sortedByDescending { it.count }
            .groupBy { it.bibleName }.toSortedMap()
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

        file.outputStream().use { workbook.write(it) }
        workbook.close()
        true
    } catch (_: Exception) { false }

    fun exportCcliCsv(file: File, fromMs: Long, toMs: Long): Boolean = try {
        val songs = getAllSongsInRange(fromMs, toMs)
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sb = StringBuilder()
        sb.appendLine("Title,Author,Songbook,Song Number,Times Used,First Used,Last Used")
        for (song in songs) {
            fun esc(s: String) = "\"${s.replace("\"", "\"\"")}\""
            sb.appendLine("${esc(song.title)},${esc(song.author)},${esc(song.songbook)},${song.songNumber},${song.count},${dateFmt.format(Date(song.firstUsed))},${dateFmt.format(Date(song.lastUsed))}")
        }
        file.writeText(sb.toString())
        true
    } catch (_: Exception) { false }

    fun exportFilteredXls(file: File, fromMs: Long, toMs: Long): Boolean = try {
        val workbook = HSSFWorkbook()
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.LIGHT_CORNFLOWER_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(workbook.createFont().apply { bold = true })
        }
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val songsSheet = workbook.createSheet("Songs")
        var rowIndex = 0
        val sHeader = songsSheet.createRow(rowIndex++)
        listOf("Rank", "Title", "Author", "Songbook", "Song #", "Times Used", "First Used", "Last Used")
            .forEachIndexed { col, label ->
                sHeader.createCell(col).also { it.setCellValue(label); it.cellStyle = headerStyle }
            }
        getAllSongsInRange(fromMs, toMs).forEachIndexed { rank, song ->
            val row = songsSheet.createRow(rowIndex++)
            row.createCell(0).setCellValue((rank + 1).toDouble())
            row.createCell(1).setCellValue(song.title)
            row.createCell(2).setCellValue(song.author)
            row.createCell(3).setCellValue(song.songbook)
            row.createCell(4).setCellValue(song.songNumber.toDouble())
            row.createCell(5).setCellValue(song.count.toDouble())
            row.createCell(6).setCellValue(dateFmt.format(Date(song.firstUsed)))
            row.createCell(7).setCellValue(dateFmt.format(Date(song.lastUsed)))
        }
        (0..7).forEach { songsSheet.autoSizeColumn(it) }

        val versesSheet = workbook.createSheet("Bible Verses")
        rowIndex = 0
        val vHeader = versesSheet.createRow(rowIndex++)
        listOf("Rank", "Bible", "Book", "Chapter", "Verse", "Times Used", "First Used", "Last Used")
            .forEachIndexed { col, label ->
                vHeader.createCell(col).also { it.setCellValue(label); it.cellStyle = headerStyle }
            }
        getAllVersesInRange(fromMs, toMs).forEachIndexed { rank, verse ->
            val row = versesSheet.createRow(rowIndex++)
            row.createCell(0).setCellValue((rank + 1).toDouble())
            row.createCell(1).setCellValue(verse.bibleName)
            row.createCell(2).setCellValue(verse.bookName)
            row.createCell(3).setCellValue(verse.chapter.toDouble())
            row.createCell(4).setCellValue(verse.verseNumber.toDouble())
            row.createCell(5).setCellValue(verse.count.toDouble())
            row.createCell(6).setCellValue(dateFmt.format(Date(verse.firstUsed)))
            row.createCell(7).setCellValue(dateFmt.format(Date(verse.lastUsed)))
        }
        (0..7).forEach { versesSheet.autoSizeColumn(it) }

        val actSheet = workbook.createSheet("Activity")
        rowIndex = 0
        val aHeader = actSheet.createRow(rowIndex++)
        listOf("Period", "Song Presentations", "Bible Verse Presentations", "Total")
            .forEachIndexed { col, label ->
                aHeader.createCell(col).also { it.setCellValue(label); it.cellStyle = headerStyle }
            }
        getActivityByPeriod(fromMs, toMs).forEach { pt ->
            val row = actSheet.createRow(rowIndex++)
            row.createCell(0).setCellValue(pt.label)
            row.createCell(1).setCellValue(pt.songCount.toDouble())
            row.createCell(2).setCellValue(pt.verseCount.toDouble())
            row.createCell(3).setCellValue((pt.songCount + pt.verseCount).toDouble())
        }
        (0..3).forEach { actSheet.autoSizeColumn(it) }

        file.outputStream().use { workbook.write(it) }
        workbook.close()
        true
    } catch (_: Exception) { false }
}
