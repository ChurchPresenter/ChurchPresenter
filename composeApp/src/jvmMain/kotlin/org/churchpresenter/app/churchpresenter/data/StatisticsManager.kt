package org.churchpresenter.app.churchpresenter.data

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
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.Row


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
    val ccliNumber: String,
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

// ── Item identity ─────────────────────────────────────────────────────────────

/**
 * What identifies one song across both stores.
 *
 * The aggregate map is keyed by the catalog `songId`, which is not a field of [SongDisplayEntry] and
 * has no counterpart in the event log, so a song is matched on these three fields instead — the same
 * grouping [getAllSongsInRange] uses. A title edited between plays therefore splits into two rows,
 * exactly as it already does in the CCLI report.
 */
data class SongKey(val songbook: String, val songNumber: Int, val title: String)

/** What identifies one verse. Both stores agree on this composite. */
data class VerseKey(val bibleName: String, val bookName: String, val chapter: Int, val verseNumber: Int)

internal fun SongPlayEvent.key() = SongKey(songbook, songNumber, title)
internal fun SongDisplayEntry.key() = SongKey(songbook, songNumber, title)
internal fun VersePlayEvent.key() = VerseKey(bibleName, bookName, chapter, verseNumber)
internal fun VerseDisplayEntry.key() = VerseKey(bibleName, bookName, chapter, verseNumber)

internal enum class ActivityGranularity { WEEKLY, MONTHLY, YEARLY }

private const val WEEKLY_MAX_DAYS = 90
private const val MONTHLY_MAX_DAYS = 730

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

/** Chooses the activity-chart bucket size from the selected range: up to ~3 months → weekly, up to
 *  ~2 years → monthly, longer → yearly. */
internal fun activityGranularityFor(rangeMs: Long): ActivityGranularity {
    val dayMs = 86_400_000L
    return when {
        rangeMs <= WEEKLY_MAX_DAYS * dayMs -> ActivityGranularity.WEEKLY
        rangeMs <= MONTHLY_MAX_DAYS * dayMs -> ActivityGranularity.MONTHLY
        else -> ActivityGranularity.YEARLY
    }
}

/** Whether this timestamp falls within the bounds; a null bound is unbounded. */
internal fun Long.inRange(fromMs: Long?, toMs: Long?): Boolean =
    (fromMs == null || this >= fromMs) && (toMs == null || this <= toMs)

/**
 * Applies a per-item deletion to the all-time song counters: clearing the whole history drops the
 * entry outright, otherwise its count falls by [removed] and the entry goes once it reaches zero.
 */
internal fun Map<String, SongDisplayEntry>.withSongCleared(
    key: SongKey,
    removed: Int,
    clearAll: Boolean
): Map<String, SongDisplayEntry> = mapNotNull { (mapKey, entry) ->
    if (entry.key() != key) return@mapNotNull mapKey to entry
    if (clearAll) return@mapNotNull null
    val count = (entry.count - removed).coerceAtLeast(0)
    if (count == 0) null else mapKey to entry.copy(count = count)
}.toMap()

/** The verse counterpart of [withSongCleared]. */
internal fun Map<String, VerseDisplayEntry>.withVerseCleared(
    key: VerseKey,
    removed: Int,
    clearAll: Boolean
): Map<String, VerseDisplayEntry> = mapNotNull { (mapKey, entry) ->
    if (entry.key() != key) return@mapNotNull mapKey to entry
    if (clearAll) return@mapNotNull null
    val count = (entry.count - removed).coerceAtLeast(0)
    if (count == 0) null else mapKey to entry.copy(count = count)
}.toMap()

/** RFC-4180 CSV field: wrap in double quotes and double any embedded quote. */
internal fun csvQuote(s: String): String = "\"${s.replace("\"", "\"\"")}\""

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

    fun recordSongDisplay(songId: String, songNumber: Int, title: String, songbook: String, author: String = "") {
        synchronized(lock) {
            val key = songId.ifBlank { "$songbook::$songNumber" }
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
    }

    // ── All-time aggregate queries ─────────────────────────────────────────────
    //
    // No screen renders these two any more — the report window reads the dated event log instead.
    // They stay as the only readable view of the counter store, which is still live: [getSongPlayCount]
    // feeds the play-count column and its sort in the songs list, and these are what the tests assert
    // recording against.

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

    fun getSongPlayCount(songId: String): Int =
        synchronized(lock) { statistics.songDisplayCounts[songId]?.count ?: 0 }

    fun getAllSongsInRange(fromMs: Long, toMs: Long): List<SongSummary> {
        // Build the catalog CCLI lookup outside the lock so recording (go-live) isn't blocked
        // by catalog file I/O.
        val ccliLookup = loadSongCcliLookup()
        return synchronized(lock) {
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
                        ccliNumber = ccliLookup.resolve(e.songbook, e.songNumber, e.title),
                        count = events.size,
                        firstUsed = events.minOf { it.timestamp },
                        lastUsed = events.maxOf { it.timestamp }
                    )
                }
                .sortedByDescending { it.count }
        }
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

    /**
     * Deletes one song's plays. A null range clears all of its history; otherwise only the events
     * timestamped within [fromMs]..[toMs], and the all-time counter is reduced by however many were
     * removed. Returns the number of events removed.
     *
     * Plays recorded before the event log existed have no dated events, so nothing can be removed
     * for them and the row stays — there is genuinely nothing dated to delete.
     */
    fun clearSong(key: SongKey, fromMs: Long? = null, toMs: Long? = null): Int = synchronized(lock) {
        val clearAll = fromMs == null && toMs == null
        val kept = eventLog.songEvents.filterNot { it.key() == key && it.timestamp.inRange(fromMs, toMs) }
        val removed = eventLog.songEvents.size - kept.size
        eventLog = eventLog.copy(songEvents = kept)
        statistics = statistics.copy(
            songDisplayCounts = statistics.songDisplayCounts.withSongCleared(key, removed, clearAll)
        )
        save()
        saveLog()
        removed
    }

    /** The verse counterpart of [clearSong]. */
    fun clearVerse(key: VerseKey, fromMs: Long? = null, toMs: Long? = null): Int = synchronized(lock) {
        val clearAll = fromMs == null && toMs == null
        val kept = eventLog.verseEvents.filterNot { it.key() == key && it.timestamp.inRange(fromMs, toMs) }
        val removed = eventLog.verseEvents.size - kept.size
        eventLog = eventLog.copy(verseEvents = kept)
        statistics = statistics.copy(
            verseDisplayCounts = statistics.verseDisplayCounts.withVerseCleared(key, removed, clearAll)
        )
        save()
        saveLog()
        removed
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

    /**
     * A songbook+number / songbook+title lookup for CCLI numbers resolved from the on-disk song
     * catalog. The event log only stores title/songbook/number/author (see [SongPlayEvent]), not
     * a CCLI number, so this is resolved fresh from the catalog at query/export time rather than
     * stored per-event. Matching prefers songbook + song number (stable across title edits) and
     * falls back to songbook + lowercased title.
     */
    private class CcliLookup(
        private val byNumber: Map<String, String>,
        private val byTitle: Map<String, String>
    ) {
        fun resolve(songbook: String, songNumber: Int, title: String): String {
            if (songNumber != 0) byNumber["$songbook::$songNumber"]?.let { return it }
            return byTitle["$songbook::${title.lowercase()}"] ?: ""
        }
    }

    private fun loadSongCcliLookup(): CcliLookup = try {
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

    fun exportCcliCsv(file: File, fromMs: Long, toMs: Long): Boolean = try {
        val songs = getAllSongsInRange(fromMs, toMs)
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sb = StringBuilder()
        sb.appendLine("Title,Author,Songbook,Song Number,CCLI Number,Times Used,First Used,Last Used")
        for (song in songs) {
            fun esc(s: String) = csvQuote(s)
            sb.appendLine("${esc(song.title)},${esc(song.author)},${esc(song.songbook)},${song.songNumber},${esc(song.ccliNumber)},${song.count},${dateFmt.format(Date(song.firstUsed))},${dateFmt.format(Date(song.lastUsed))}")
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
}
