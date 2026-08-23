package org.churchpresenter.core.models.statistics

import kotlinx.serialization.Serializable

/**
 * What was sung and read, as it is stored and reported.
 *
 * Two stores back the statistics screen: an all-time per-item tally ([DisplayStatistics]) and a
 * timestamped log of every play ([PlayEventLog]), both written to `~/.churchpresenter/` after every
 * go-live. **Their field names are the on-disk format** of `statistics.json` and `play_log.json`,
 * which are append-only across years and cannot be reconstructed if lost — a church three years in
 * has every service it has ever run in them. The reader is deliberately forgiving, so a field
 * renamed here reads back as its default and surfaces as a report of *zero* rather than an error.
 * Renaming a property is therefore a silent data loss, not a compile error.
 *
 * None of these are polymorphic, so no type discriminator is written and the package they live in
 * is not part of the format. `:statistics` owns the logic over them.
 */

// ── Aggregate statistics (all-time) ───────────────────────────────────────────

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
 * has no counterpart in the event log, so a song is matched on these three fields instead. A title
 * edited between plays therefore splits into two rows, exactly as it already does in the CCLI
 * report. `:statistics` carries the `key()` extensions that derive one from either store.
 */
data class SongKey(val songbook: String, val songNumber: Int, val title: String)

/** What identifies one verse. Both stores agree on this composite. */
data class VerseKey(val bibleName: String, val bookName: String, val chapter: Int, val verseNumber: Int)
