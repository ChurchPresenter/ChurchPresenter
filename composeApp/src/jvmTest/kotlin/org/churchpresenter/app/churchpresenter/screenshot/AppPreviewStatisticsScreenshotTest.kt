@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.data.DisplayStatistics
import org.churchpresenter.app.churchpresenter.data.PlayEventLog
import org.churchpresenter.app.churchpresenter.data.SongDisplayEntry
import org.churchpresenter.app.churchpresenter.data.SongPlayEvent
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.app.churchpresenter.data.VerseDisplayEntry
import org.churchpresenter.app.churchpresenter.data.VersePlayEvent
import org.churchpresenter.app.churchpresenter.dialogs.CCLIReportContent
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test

class AppPreviewStatisticsScreenshotTest {


    private fun dialog(
        name: String,
        width: Dp,
        height: Dp,
        drive: ComposeUiTest.() -> Unit = {},
        content: @Composable (ThemeMode) -> Unit,
    ) {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
        seedSixMonths()
        THEMES.forEach { (suffix, mode) ->
            runSkikoComposeUiTest(
                size = Size(width.value, height.value),
                density = Density(1f),
            ) {
                setContent {
                    ChurchPresenterTheme(themeMode = mode) {
                        Box(Modifier.size(width, height)) { content(mode) }
                    }
                }
                waitForIdle()
                drive()
                captureTo(File("$SCREENSHOT_ROOT/previewApp/${name}_$suffix.png"))
            }
        }
    }

    /** Clearing everything asks first — this is the prompt it asks with. */
    @Test
    fun `clear confirmation`() = ccli("ccli_report_clear_confirm") {
        onAllNodes(hasText("Clear Statistics"))[0].performClick()
        waitForIdle()
    }

    @Test
    fun `ccli report songs`() = ccli("ccli_report")

    @Test
    fun `ccli report bible`() = ccli("ccli_report_bible") {
        onAllNodes(hasText("Bible (", substring = true))[0].performClick()
        waitForIdle()
    }

    @Test
    fun `ccli report activity`() = ccli("ccli_report_activity") {
        onAllNodes(hasText("Activity", substring = true))[0].performClick()
        waitForIdle()
    }

    /**
     * 1200dp because the table needs it, and shrinking — the intuitive fix for a cramped
     * screenshot — makes it worse.
     *
     * The summary panel down the left is a fixed `width(300.dp)` and the table takes `weight(1f)`
     * of what is left. Inside the table only title, author and songbook flex (weights 2, 1.5, 1);
     * rank, CCLI number, count and the two dates are fixed, ~386dp once gaps and row padding are
     * counted. At 940 that left the three flexible columns 253dp between them, so the songbook
     * header rendered as "SONGBO" and titles and authors were ellipsised mid-word — which is what
     * the website was publishing. 1200 gives them ~513dp and they fit.
     *
     * Not fixed by this, and not a capture problem: each row reserves `end = 20.dp` for the
     * scrollbar overlaid at CenterEnd, and the date columns are wider than the dates. That gutter
     * on the right is the dialog's own layout at any window size.
     */
    private fun ccli(name: String, drive: ComposeUiTest.() -> Unit = {}) =
        dialog(name, 1200.dp, 700.dp, drive) { mode ->
            CCLIReportContent(
                theme = mode,
                statisticsManager = StatisticsManager(),
                onDismiss = {},
                today = FIXED_TODAY,
            )
        }

    /**
     * Half a year of Sunday services in the two files `StatisticsManager` reads at construction:
     * the running totals it shows in Statistics, and the timestamped log the CCLI report filters
     * by date.
     */
    private fun seedSixMonths() {
        val zone = ZoneId.systemDefault()
        val today = FIXED_TODAY
        val sundays = (0 until 26).map { week ->
            today.minusWeeks(week.toLong()).atTime(10, 30).atZone(zone).toInstant().toEpochMilli()
        }

        val songEvents = mutableListOf<SongPlayEvent>()
        val verseEvents = mutableListOf<VersePlayEvent>()
        sundays.forEachIndexed { week, stamp ->
            SERVICE_SETS[week % SERVICE_SETS.size].forEach { (number, rest) ->
                val (title, author) = rest
                songEvents += SongPlayEvent(
                    songNumber = number,
                    title = title,
                    songbook = if (number < 100) "Gospel Songs" else "Hymnal",
                    author = author,
                    timestamp = stamp,
                )
            }
            val (book, chapter, verse) = READINGS[week % READINGS.size]
            verseEvents += VersePlayEvent(
                bibleName = "King James Version",
                bookName = book,
                chapter = chapter,
                verseNumber = verse,
                timestamp = stamp,
            )
        }

        val songCounts = songEvents.groupBy { "${it.songbook}:${it.songNumber}" }
            .mapValues { (_, events) ->
                SongDisplayEntry(
                    songNumber = events.first().songNumber,
                    title = events.first().title,
                    songbook = events.first().songbook,
                    count = events.size,
                )
            }
        val verseCounts = verseEvents.groupBy { "${it.bookName} ${it.chapter}:${it.verseNumber}" }
            .mapValues { (_, events) ->
                VerseDisplayEntry(
                    bibleName = events.first().bibleName,
                    bookName = events.first().bookName,
                    chapter = events.first().chapter,
                    verseNumber = events.first().verseNumber,
                    count = events.size,
                )
            }

        val json = Json { encodeDefaults = true }
        val dir = File(System.getProperty("user.home"), ".churchpresenter").apply { mkdirs() }
        File(dir, "statistics.json").writeText(
            json.encodeToString(
                DisplayStatistics.serializer(),
                DisplayStatistics(songDisplayCounts = songCounts, verseDisplayCounts = verseCounts),
            )
        )
        File(dir, "play_log.json").writeText(
            json.encodeToString(
                PlayEventLog.serializer(),
                PlayEventLog(songEvents = songEvents, verseEvents = verseEvents),
            )
        )
    }

    private companion object {
        /**
         * The day these images are recorded as, so they do not go stale overnight.
         *
         * Both halves have to be pinned: the seeded services are dated back from here, and the
         * report's default range runs to here. A Sunday, so the services land where a real
         * schedule would put them.
         */
        val FIXED_TODAY: LocalDate = LocalDate.of(2026, 3, 1)

        /** Four rotating sets, so counts differ the way a real rota's would. */
        val SERVICE_SETS = listOf(
            listOf(
                12 to ("Amazing Grace" to "John Newton"),
                320 to ("Come Thou Fount Of Every Blessing" to "Robert Robinson"),
                455 to ("It Is Well With My Soul" to "Horatio Spafford"),
                44 to ("Leaning On The Everlasting Arms" to "Elisha A. Hoffman"),
            ),
            listOf(
                12 to ("Amazing Grace" to "John Newton"),
                78 to ("Holy, Holy, Holy" to "Reginald Heber"),
                402 to ("Blessed Assurance" to "Fanny Crosby"),
                22 to ("Sweet Hour Of Prayer" to "William W. Walford"),
            ),
            listOf(
                367 to ("Great Is Thy Faithfulness" to "Thomas Chisholm"),
                320 to ("Come Thou Fount Of Every Blessing" to "Robert Robinson"),
                501 to ("The Old Rugged Cross" to "George Bennard"),
                14 to ("I Need Thee Every Hour" to "Annie S. Hawks"),
            ),
            listOf(
                12 to ("Amazing Grace" to "John Newton"),
                472 to ("When I Survey The Wondrous Cross" to "Isaac Watts"),
                388 to ("What A Friend We Have In Jesus" to "Joseph Scriven"),
                31 to ("Standing On The Promises" to "R. Kelso Carter"),
            ),
        )

        val READINGS = listOf(
            Triple("Psalm", 23, 1),
            Triple("John", 3, 16),
            Triple("Romans", 8, 28),
            Triple("Isaiah", 40, 31),
            Triple("Matthew", 28, 6),
            Triple("Philippians", 4, 13),
        )
    }
}
