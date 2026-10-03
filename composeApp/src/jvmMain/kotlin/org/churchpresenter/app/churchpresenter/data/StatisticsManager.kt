package org.churchpresenter.app.churchpresenter.data

import org.churchpresenter.songs.SongPlayCounts
import org.churchpresenter.core.models.io.writeTextAtomically
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


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

class StatisticsManager : SongPlayCounts {
    private val lock = Any()
    private val files = StatisticsFiles()
    private var statistics: DisplayStatistics = files.loadStatistics()
    private var eventLog: PlayEventLog = files.loadEventLog()

    /** The all-time counters as they stand; [getTopSongsBySongbook] and its verse twin read these. */
    internal val counters: DisplayStatistics get() = statistics

    /** Runs [read] over the dated event log, under the same lock recording takes. */
    internal fun <T> readEventLog(read: (PlayEventLog) -> T): T = synchronized(lock) { read(eventLog) }

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
            files.save(statistics, eventLog)
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
            files.save(statistics, eventLog)
        }
    }

    override fun getSongPlayCount(songId: String): Int =
        synchronized(lock) { statistics.songDisplayCounts[songId]?.count ?: 0 }

    fun getAllSongsInRange(fromMs: Long, toMs: Long): List<SongSummary> {
        // Build the catalog CCLI lookup outside the lock so recording (go-live) isn't blocked
        // by catalog file I/O.
        val ccliLookup = loadSongCcliLookup()
        return readEventLog { eventLog ->
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

    fun getAllVersesInRange(fromMs: Long, toMs: Long): List<VerseSummary> = readEventLog { eventLog ->
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
        files.save(statistics, eventLog)
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
        files.save(statistics, eventLog)
        removed
    }

    fun clearStatistics() {
        synchronized(lock) {
            statistics = DisplayStatistics()
            eventLog = PlayEventLog()
            files.save(statistics, eventLog)
        }
    }


}

/** Where the counters and the event log are kept: `~/.churchpresenter/statistics.json` and `play_log.json`. */
private class StatisticsFiles {
    private val userHome = System.getProperty("user.home")
    private val appDataDir = File(userHome, ".churchpresenter")
    private val statsFile = File(appDataDir, "statistics.json")
    private val logFile = File(appDataDir, "play_log.json")

    private val jsonFormat = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun loadStatistics(): DisplayStatistics = try {
        if (statsFile.exists()) jsonFormat.decodeFromString(statsFile.readText()) else DisplayStatistics()
    } catch (_: Exception) { DisplayStatistics() }

    fun loadEventLog(): PlayEventLog = try {
        if (logFile.exists()) jsonFormat.decodeFromString(logFile.readText()) else PlayEventLog()
    } catch (_: Exception) { PlayEventLog() }

    /** Writes both files, each whole or not at all; a failed write leaves the last good copy. */
    fun save(statistics: DisplayStatistics, eventLog: PlayEventLog) {
        try { statsFile.writeTextAtomically(jsonFormat.encodeToString(statistics)) } catch (_: Exception) {}
        try { logFile.writeTextAtomically(jsonFormat.encodeToString(eventLog)) } catch (_: Exception) {}
    }
}
