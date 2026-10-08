package org.churchpresenter.statistics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * A row's delete, held until the confirmation is answered. The label names it in the prompt; [clear]
 * removes it from the given range.
 */
internal class PendingClear(val label: String, val clear: (from: Long, to: Long) -> Unit)

/** What the report holds for the range last loaded, and whether there is an event log at all. */
@Stable
internal class ReportData(private val stats: StatisticsManager) {
    var songs by mutableStateOf(emptyList<SongSummary>())
    var verses by mutableStateOf(emptyList<VerseSummary>())
    var activity by mutableStateOf(emptyList<ActivityPoint>())
    var earliestEvent by mutableStateOf(stats.getEarliestEventTime())
    var hasLog by mutableStateOf(stats.hasEventLog())

    suspend fun load(from: Long, to: Long) {
        songs = withContext(Dispatchers.IO) { stats.getAllSongsInRange(from, to) }
        verses = withContext(Dispatchers.IO) { stats.getAllVersesInRange(from, to) }
        activity = withContext(Dispatchers.IO) { stats.getActivityByPeriod(from, to) }
        earliestEvent = stats.getEarliestEventTime()
        hasLog = stats.hasEventLog()
    }
}

/** The years the date pickers offer: from the first logged event's (or this one) to [today]'s. */
internal fun reportYearRange(earliestEvent: Long?, zone: ZoneId, today: LocalDate): IntRange {
    val startYear = if (earliestEvent != null) {
        java.time.Instant.ofEpochMilli(earliestEvent).atZone(zone).year
    } else {
        today.year
    }
    return startYear..today.year
}

/** Clearing one song's uses: named by its title. */
internal fun songClear(statisticsManager: StatisticsManager, song: SongSummary): PendingClear {
    val key = SongKey(song.songbook, song.songNumber, song.title)
    return PendingClear(song.title) { from, to -> statisticsManager.clearSong(key, from, to) }
}

/** Clearing one verse's uses: named by its reference. */
internal fun verseClear(statisticsManager: StatisticsManager, verse: VerseSummary): PendingClear {
    val key = VerseKey(verse.bibleName, verse.bookName, verse.chapter, verse.verseNumber)
    return PendingClear("${verse.bookName} ${verse.chapter}:${verse.verseNumber}") { from, to ->
        statisticsManager.clearVerse(key, from, to)
    }
}
