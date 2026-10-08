package org.churchpresenter.statistics

import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.settings.SettingsManager
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.Row

// ── All-time aggregate queries ─────────────────────────────────────────────
//
// No screen renders these two any more — the report window reads the dated event log instead.
// They stay as the only readable view of the counter store, which is still live: [getSongPlayCount]
// feeds the play-count column and its sort in the songs list, and these are what the tests assert
// recording against.

fun StatisticsManager.getTopSongsBySongbook(limit: Int = 15): Map<String, List<SongDisplayEntry>> =
    counters.songDisplayCounts.values
        .groupBy { it.songbook }
        .mapValues { (_, entries) -> entries.sortedByDescending { it.count }.take(limit) }

fun StatisticsManager.getTopVersesByBible(limit: Int = 15): Map<String, List<VerseDisplayEntry>> =
    counters.verseDisplayCounts.values
        .groupBy { it.bibleName }
        .mapValues { (_, entries) -> entries.sortedByDescending { it.count }.take(limit) }

// ── Event log queries (used by CCLI report) ───────────────────────────────

fun StatisticsManager.getEarliestEventTime(): Long? = readEventLog { eventLog ->
    val songMin = eventLog.songEvents.minOfOrNull { it.timestamp }
    val verseMin = eventLog.verseEvents.minOfOrNull { it.timestamp }
    listOfNotNull(songMin, verseMin).minOrNull()
}

fun StatisticsManager.hasEventLog(): Boolean = readEventLog { eventLog ->
    eventLog.songEvents.isNotEmpty() || eventLog.verseEvents.isNotEmpty()
}

fun StatisticsManager.getActivityByPeriod(fromMs: Long, toMs: Long): List<ActivityPoint> = readEventLog { eventLog ->
    val dayMs = 86_400_000L
    val weekMs = 7 * dayMs
    val rangeMs = toMs - fromMs

    val songEvents = eventLog.songEvents.filter { it.timestamp in fromMs..toMs }
    val verseEvents = eventLog.verseEvents.filter { it.timestamp in fromMs..toMs }

    when (activityGranularityFor(rangeMs)) {
        ActivityGranularity.WEEKLY -> {
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
        ActivityGranularity.MONTHLY -> {
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
        ActivityGranularity.YEARLY -> {
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

// ── Exports ───────────────────────────────────────────────────────────────

/**
 * A songbook+number / songbook+title lookup for CCLI numbers resolved from the on-disk song
 * catalog. The event log only stores title/songbook/number/author (see [SongPlayEvent]), not
 * a CCLI number, so this is resolved fresh from the catalog at query/export time rather than
 * stored per-event. Matching prefers songbook + song number (stable across title edits) and
 * falls back to songbook + lowercased title.
 */
internal class CcliLookup(
    private val byNumber: Map<String, String>,
    private val byTitle: Map<String, String>
) {
    fun resolve(songbook: String, songNumber: Int, title: String): String {
        if (songNumber != 0) byNumber["$songbook::$songNumber"]?.let { return it }
        return byTitle["$songbook::${title.lowercase()}"] ?: ""
    }
}

internal fun loadSongCcliLookup(): CcliLookup = try {
    val storageDir = SettingsManager().loadSettings().songSettings.storageDirectory
    if (storageDir.isBlank()) {
        CcliLookup(emptyMap(), emptyMap())
    } else {
        val cached = SongFileParser.loadCachedSongMap(storageDir)
        val songs = SongFileParser().loadSongsFromDirectory(storageDir, cached)
            .map { it.song }
            .filter { it.ccliNumber.isNotBlank() }
        val byNumber = songs.mapNotNull { song ->
            song.number.toIntOrNull()?.let { "${song.songbook}::$it" to song.ccliNumber }
        }.toMap()
        val byTitle = songs.associate { "${it.songbook}::${it.title.lowercase()}" to it.ccliNumber }
        CcliLookup(byNumber, byTitle)
    }
} catch (_: Exception) {
    CcliLookup(emptyMap(), emptyMap())
}

fun StatisticsManager.exportCcliCsv(file: File, fromMs: Long, toMs: Long): Boolean = try {
    val songs = getAllSongsInRange(fromMs, toMs)
    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val sb = StringBuilder()
    sb.appendLine("Title,Author,Songbook,Song Number,CCLI Number,Times Used,First Used,Last Used")
    for (song in songs) {
        fun esc(s: String) = csvQuote(s)
        sb.appendLine(
            "${esc(song.title)},${esc(song.author)},${esc(song.songbook)},${song.songNumber}," +
                "${esc(song.ccliNumber)},${song.count}," +
                "${dateFmt.format(Date(song.firstUsed))},${dateFmt.format(Date(song.lastUsed))}"
        )
    }
    file.writeText(sb.toString())
    true
} catch (_: Exception) { false }

fun StatisticsManager.exportFilteredXls(file: File, fromMs: Long, toMs: Long): Boolean = try {
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
    listOf("Rank", "Title", "Author", "Songbook", "Song #", "CCLI #", "Times Used", "First Used", "Last Used")
        .forEachIndexed { col, label ->
            sHeader.createCell(col).also { it.setCellValue(label); it.cellStyle = headerStyle }
        }
    getAllSongsInRange(fromMs, toMs).forEachIndexed { rank, song ->
        val row = songsSheet.createRow(rowIndex++)
        row.writeCells(
            (rank + 1).toDouble(), song.title, song.author, song.songbook,
            song.songNumber.toDouble(), song.ccliNumber, song.count.toDouble(),
            dateFmt.format(Date(song.firstUsed)), dateFmt.format(Date(song.lastUsed))
        )
    }
    for (column in 0..8) songsSheet.autoSizeColumn(column)

    val versesSheet = workbook.createSheet("Bible Verses")
    rowIndex = 0
    val vHeader = versesSheet.createRow(rowIndex++)
    listOf("Rank", "Bible", "Book", "Chapter", "Verse", "Times Used", "First Used", "Last Used")
        .forEachIndexed { col, label ->
            vHeader.createCell(col).also { it.setCellValue(label); it.cellStyle = headerStyle }
        }
    getAllVersesInRange(fromMs, toMs).forEachIndexed { rank, verse ->
        val row = versesSheet.createRow(rowIndex++)
        row.writeCells(
            (rank + 1).toDouble(), verse.bibleName, verse.bookName, verse.chapter.toDouble(),
            verse.verseNumber.toDouble(), verse.count.toDouble(),
            dateFmt.format(Date(verse.firstUsed)), dateFmt.format(Date(verse.lastUsed))
        )
    }
    for (column in 0..7) versesSheet.autoSizeColumn(column)

    val actSheet = workbook.createSheet("Activity")
    rowIndex = 0
    val aHeader = actSheet.createRow(rowIndex++)
    listOf("Period", "Song Presentations", "Bible Verse Presentations", "Total")
        .forEachIndexed { col, label ->
            aHeader.createCell(col).also { it.setCellValue(label); it.cellStyle = headerStyle }
        }
    getActivityByPeriod(fromMs, toMs).forEach { pt ->
        val row = actSheet.createRow(rowIndex++)
        row.writeCells(
            pt.label, pt.songCount.toDouble(), pt.verseCount.toDouble(),
            (pt.songCount + pt.verseCount).toDouble()
        )
    }
    for (column in 0..3) actSheet.autoSizeColumn(column)

    file.outputStream().use { workbook.write(it) }
    workbook.close()
    true
} catch (_: Exception) { false }

private fun Row.writeCells(vararg values: Any) {
    values.forEachIndexed { col, value ->
        val cell = createCell(col)
        when (value) {
            is Double -> cell.setCellValue(value)
            is String -> cell.setCellValue(value)
            else -> cell.setCellValue(value.toString())
        }
    }
}
