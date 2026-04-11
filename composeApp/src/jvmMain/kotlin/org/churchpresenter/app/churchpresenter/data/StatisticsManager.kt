package org.churchpresenter.app.churchpresenter.data

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
}
