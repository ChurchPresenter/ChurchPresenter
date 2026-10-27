@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.settings.SongColumnId
import org.churchpresenter.statistics.CCLIReportContent
import org.churchpresenter.statistics.DisplayStatistics
import org.churchpresenter.statistics.PlayEventLog
import org.churchpresenter.statistics.SongDisplayEntry
import org.churchpresenter.statistics.SongPlayEvent
import org.churchpresenter.statistics.StatisticsManager
import org.churchpresenter.statistics.VerseDisplayEntry
import org.churchpresenter.statistics.VersePlayEvent
import org.churchpresenter.theme.ThemeMode
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.test.Test

/** Help → CCLI Reports: every song and verse that went live, over a year of Sundays. */
class CcliDemoTour {
    @Test
    fun ccli() {
        TestSingletons.latchToTestHome()
        seedPlayHistory(LocalDate.now())
        val stats = StatisticsManager()
        recordTour(
            "ccli",
            tab = "CCLI Reports",
            liveLabel = "Sunday worship",
            steps = 8,
            startTab = "Songs",
            heading = "" to "",
            settings = { it.copy(songHiddenCols = it.songHiddenCols - SongColumnId.PLAY_COUNT) },
            statistics = stats,
            panel = { _, _ ->
                Box(Modifier.size(REPORT_W.dp, REPORT_H.dp)) {
                    CCLIReportContent(theme = ThemeMode.DARK, statisticsManager = stats, onDismiss = {})
                }
            },
        ) { ccliTour() }
    }

    private fun DemoTour.ccliTour() {
        say("intro")
        intro("Every song and verse you show, counted for you.")
        say("why")
        caption("A CCLI license", "Report the songs you project")
        say("automatic")
        caption("Nothing to turn on", "Each song and verse that goes live is noted")
        quietClick(text("Amazing Grace"))
        quietClick(text("Amazing grace! how sweet the sound"))
        say("go-live")
        goLive(sub = "This use of the song is counted")
        say("plays")
        note("Plays", "How often each song was used", find(hasText("Plays")) { it.left > dp(TAB_AREA_LEFT_DP) })
        say("open")
        val help = runCatching { find(hasText("Help")) { it.top < dp(MENU_TOP_DP) } }.getOrNull()
        val menu = "Help → CCLI Reports"
        if (help != null) note(menu, "The full report", help) else caption(menu, "The full report")
        openPanel("CCLI Reports", REPORT_W, REPORT_H)
        waitFor("the report to load") { panelItems(hasText("Top Songs")).isNotEmpty() }
        range()
        songs()
        bibleAndActivity()
        exports()
        closePanel()
        say("outro")
        outro()
    }

    private fun DemoTour.range() {
        say("window")
        point("This year so far", "From your own services", hasText("From:"))
        say("presets")
        val twelve = seek(hasText("Last 12 Months"))
        panelNote("Quick ranges", "3, 6 or 12 months, or all time", twelve, click = true, numbered = true)
        say("year")
        panelNote("Year", "One calendar year", seek(hasText("Year")), click = true, numbered = true)
        menusInPlace = true
        val year = popupItem(hasText(LocalDate.now().year.toString()))
        if (year != null) {
            year.node.performClick()
            ui.waitForIdle()
        }
        menusInPlace = false
        pause(0.4)
        say("dates")
        point("From and To", "Match the period CCLI asks for", hasText("To:"))
    }

    private fun DemoTour.songs() {
        say("songs-chart")
        point("Top Songs", "Your most used songs", hasText("Top Songs"))
        say("songs-table")
        point("Every song", "Author, book, CCLI number, times used", hasText("AUTHOR"))
        say("ccli-number")
        point("CCLI #", "Set in the song editor", hasText("CCLI #"))
        say("songbook")
        point("One song book", "Or all of them", hasText("All Songbooks"))
    }

    private fun DemoTour.bibleAndActivity() {
        say("bible")
        val bible = seek(hasText("Bible (", substring = true))
        panelNote("Bible", "The books you read from most", bible, click = true, numbered = true)
        pause(0.8)
        say("activity")
        panelNote("Activity", "Songs and verses over time", seek(hasText("Activity")), click = true, numbered = true)
        say("activity-cards")
        point("The totals", "Songs, verses, your busiest month", hasText("Songs presented"))
        quietPanelClick(hasText("Songs (", substring = true))
        say("remove")
        val row = seek(hasText("John Newton"))
        panelHover(row)
        val remove = panelItems(hasContentDescription("Remove from statistics")).minByOrNull {
            kotlin.math.abs(it.bounds.center.y - row.bounds.center.y)
        }
        if (remove != null) {
            panelNote("Remove a song", "Like one shown at a rehearsal", remove, click = true, numbered = true)
            pause(0.8)
            runCatching { dialogPopupItem(hasText("Cancel")).node.performClick() }
            ui.waitForIdle()
        } else {
            caption("Remove a song", "Like one shown at a rehearsal")
        }
        pause(0.3)
    }

    private fun DemoTour.exports() {
        say("export-csv")
        panelNote("Export CCLI CSV", "Save the file", seek(hasText("Export CCLI CSV")), numbered = true)
        say("csv-use")
        caption("Ready for CCLI", "Title, author, CCLI number, times used")
        say("xls")
        point("Export XLS", "To share or print", hasText("Export XLS"))
        say("clear")
        panelNote("Clear Statistics", "Asks first; can't be undone", seek(hasText("Clear Statistics")), click = true)
        pause(0.8)
        runCatching { dialogPopupItem(hasText("Cancel")).node.performClick() }
        ui.waitForIdle()
        pause(0.3)
    }

    /** [m] inside the open dropdown, not its namesake in the report under it. */
    private fun DemoTour.popupItem(m: SemanticsMatcher): Target? =
        panelItems(m and hasAnyAncestor(isPopup())).firstOrNull()
}

/**
 * Every Sunday from last November to [today]: four songs and a reading, written where
 * [StatisticsManager] reads them, so the report has a year of services to show.
 */
private fun seedPlayHistory(today: LocalDate) {
    val zone = ZoneId.systemDefault()
    var sunday = today.minusMonths(HISTORY_MONTHS).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val songEvents = mutableListOf<SongPlayEvent>()
    val verseEvents = mutableListOf<VersePlayEvent>()
    var week = 0
    while (sunday.isBefore(today)) {
        val at = sunday.atTime(LocalTime.of(10, 30)).atZone(zone).toInstant().toEpochMilli()
        setFor(week).forEachIndexed { i, song ->
            songEvents += SongPlayEvent(song.number, song.title, song.book, song.author, at + i * SONG_GAP_MS)
        }
        val (book, chapter, first) = READINGS[week % READINGS.size]
        for (v in first until first + VERSES_PER_READING) {
            verseEvents += VersePlayEvent(BIBLE, book, chapter, v, at + SERMON_AT_MS + v * 1000L)
        }
        sunday = sunday.plusWeeks(1)
        week++
    }
    val counters = DisplayStatistics(
        songDisplayCounts = songEvents.groupBy { "${it.songbook}::${it.songNumber}" }.mapValues { (_, e) ->
            SongDisplayEntry(songNumber = e[0].songNumber, title = e[0].title, songbook = e[0].songbook, count = e.size)
        },
        verseDisplayCounts = verseEvents.groupBy { "${it.bibleName}::${it.bookName}::${it.chapter}::${it.verseNumber}" }
            .mapValues { (_, e) ->
                VerseDisplayEntry(
                    bibleName = BIBLE, bookName = e[0].bookName, chapter = e[0].chapter,
                    verseNumber = e[0].verseNumber, count = e.size,
                )
            },
    )
    val dir = File(System.getProperty("user.home"), ".churchpresenter").apply { mkdirs() }
    val json = Json { encodeDefaults = true }
    File(dir, "statistics.json").writeText(json.encodeToString(counters))
    File(dir, "play_log.json").writeText(json.encodeToString(PlayEventLog(songEvents, verseEvents)))
}

/** Week [week]'s four songs: the favorites come round often, the rest now and then. */
private fun setFor(week: Int): List<DemoSong> = listOf(
    FAVORITES[week % FAVORITES.size],
    FAVORITES[(week + 2) % FAVORITES.size],
    OCCASIONAL[week % OCCASIONAL.size],
    OCCASIONAL[(week * 3 + 1) % OCCASIONAL.size],
).distinct()

private data class DemoSong(val number: Int, val title: String, val book: String, val author: String)

private val FAVORITES = listOf(
    DemoSong(12, "Amazing Grace", "Hymnal", "John Newton"),
    DemoSong(320, "Come Thou Fount Of Every Blessing", "Hymnal", "Robert Robinson"),
    DemoSong(455, "It Is Well With My Soul", "Hymnal", "Horatio Spafford"),
    DemoSong(367, "Great Is Thy Faithfulness", "Hymnal", "Thomas Chisholm"),
    DemoSong(103, "How Great Thou Art", "Hymnal", "Carl Boberg"),
)

private val OCCASIONAL = listOf(
    DemoSong(44, "Leaning On The Everlasting Arms", "Gospel Songs", "Elisha A. Hoffman"),
    DemoSong(402, "Blessed Assurance", "Hymnal", "Fanny Crosby"),
    DemoSong(78, "Holy, Holy, Holy", "Hymnal", "Reginald Heber"),
    DemoSong(14, "I Need Thee Every Hour", "Gospel Songs", "Annie S. Hawks"),
    DemoSong(472, "When I Survey The Wondrous Cross", "Hymnal", "Isaac Watts"),
    DemoSong(22, "Sweet Hour Of Prayer", "Gospel Songs", "William W. Walford"),
    DemoSong(45, "Be Thou My Vision", "Hymnal", "Dallan Forgaill"),
    DemoSong(501, "The Old Rugged Cross", "Hymnal", "George Bennard"),
    DemoSong(388, "What A Friend We Have In Jesus", "Hymnal", "Joseph Scriven"),
    DemoSong(31, "Standing On The Promises", "Gospel Songs", "R. Kelso Carter"),
    DemoSong(115, "A Mighty Fortress Is Our God", "Hymnal", "Martin Luther"),
)

private val READINGS = listOf(
    Triple("John", 15, 1), Triple("Psalms", 23, 1), Triple("Romans", 8, 28), Triple("John", 3, 14),
    Triple("Matthew", 5, 3), Triple("Psalms", 46, 1), Triple("Ephesians", 2, 4), Triple("Isaiah", 40, 28),
    Triple("Philippians", 4, 4), Triple("John", 14, 1), Triple("Hebrews", 11, 1), Triple("Psalms", 103, 1),
)

private const val BIBLE = "King James Version"
private const val HISTORY_MONTHS = 12L
private const val VERSES_PER_READING = 3
private const val SONG_GAP_MS = 6 * 60_000L
private const val SERMON_AT_MS = 40 * 60_000L
private const val REPORT_W = 1100f
private const val REPORT_H = 780f
private const val TAB_AREA_LEFT_DP = 380f
private const val MENU_TOP_DP = 60f
