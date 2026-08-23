@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.statistics.ActivityPoint
import org.churchpresenter.statistics.StatisticsManager
import org.churchpresenter.statistics.SongSummary
import org.churchpresenter.statistics.VerseSummary
import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Harness, fixtures and labels shared by the `CCLIReportDialog` test classes.
 *
 * **What these tests can and cannot reach.** `CCLIReportDialog` itself wraps its whole body in a
 * `DialogWindow` — a real AWT window — and the suite runs with `java.awt.headless=true`, so composing
 * the public entry point throws `HeadlessException`. What the dialog *shows*, though, is three
 * content composables over plain data ([SongsReportContent], [BibleReportContent], [ActivityContent]),
 * and those are ordinary Compose with no window in them. They were widened `private` → `internal`
 * (no behaviour change) and are driven directly here, which reaches the tables, the two chart kinds,
 * the stat cards and every empty state through their real callers.
 *
 * Left uncovered, and deliberately: the `DialogWindow` block itself, and the date pickers, preset
 * buttons and CSV/XLS export handlers that live inside it. Those need either a display or a native
 * file dialog. The date arithmetic behind the presets (`fromMs`/`toMs`/`applyPreset`) is local to the
 * dialog composable and would have to be lifted to a top-level function before it could be tested;
 * that is a worthwhile follow-up, not something these tests reach.
 *
 * **Dates.** The tables format with `SimpleDateFormat("MMM d, yyyy")` in the default locale and zone,
 * so expected strings are built with [formatDate] — the same formatter over the same fixture instant —
 * rather than hardcoded. A hardcoded "Jan 5, 2026" would pass in one timezone and fail in another;
 * this still proves the cell shows *that* song's `firstUsed` in *that* column.
 */

// ── Isolated home + seeding ─────────────────────────────────────────────────────────────────────

/**
 * Runs [block] with `user.home` pointed at a fresh temp directory.
 *
 * [StatisticsManager] resolves `user.home` in its field initialisers and writes `statistics.json`
 * and `play_log.json` under it, so without this a test would read — and the clear tests would
 * destroy — the developer's real play history.
 */
internal fun <T> withStatsHome(block: (home: File) -> T): T {
    val home = Files.createTempDirectory("cp-stats").toFile()
    val previous = System.getProperty("user.home")
    System.setProperty("user.home", home.absolutePath)
    return try {
        block(home)
    } finally {
        System.setProperty("user.home", previous)
        home.deleteRecursively()
    }
}

/** Records [times] displays of one song, which is how it earns its count. */
internal fun StatisticsManager.playSong(
    number: Int,
    title: String,
    songbook: String,
    times: Int = 1,
) = repeat(times) {
    recordSongDisplay(
        songId = "$songbook#$number",
        songNumber = number,
        title = title,
        songbook = songbook,
    )
}

/** Records [times] displays of one verse. */
internal fun StatisticsManager.playVerse(
    bible: String,
    book: String,
    chapter: Int,
    verse: Int,
    times: Int = 1,
) = repeat(times) {
    recordVerseDisplay(bibleName = bible, bookName = book, chapter = chapter, verseNumber = verse)
}

// ── Harness ─────────────────────────────────────────────────────────────────────────────────────

/** Renders [content] on its own, the way the dialog's tab body would. */
@OptIn(ExperimentalTestApi::class)
internal fun reportContent(
    content: @Composable () -> Unit,
    block: ComposeUiTest.() -> Unit,
) = runComposeUiTest {
    setContent { MaterialTheme { content() } }
    block()
}

// ── Fixtures ────────────────────────────────────────────────────────────────────────────────────

/** A fixed instant, so nothing here depends on the wall clock. */
internal const val JAN_2026 = 1_767_225_600_000L
internal const val DAY_MS = 86_400_000L

internal fun song(
    title: String,
    count: Int = 1,
    author: String = "Charles Wesley",
    songbook: String = "Hymnal",
    ccli: String = "12345",
    number: Int = 1,
    firstUsed: Long = JAN_2026,
    lastUsed: Long = JAN_2026 + DAY_MS,
) = SongSummary(
    songNumber = number,
    title = title,
    songbook = songbook,
    author = author,
    ccliNumber = ccli,
    count = count,
    firstUsed = firstUsed,
    lastUsed = lastUsed,
)

internal fun verse(
    book: String,
    chapter: Int = 3,
    number: Int = 16,
    count: Int = 1,
    bible: String = "KJV",
    firstUsed: Long = JAN_2026,
    lastUsed: Long = JAN_2026 + DAY_MS,
) = VerseSummary(
    bibleName = bible,
    bookName = book,
    chapter = chapter,
    verseNumber = number,
    count = count,
    firstUsed = firstUsed,
    lastUsed = lastUsed,
)

internal fun activity(label: String, songs: Int, verses: Int) =
    ActivityPoint(label = label, songCount = songs, verseCount = verses)

/** The tables' own format, applied to the same instant a fixture carries. */
internal fun formatDate(ms: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ms))

// ── Labels, as the dialog renders them ──────────────────────────────────────────────────────────

internal object CcliLabel {
    const val NO_DATA = "No data for this period."
    const val SONGS_CHART = "Top Songs"
    const val BIBLE_CHART = "Top Bible Books"
    const val ACTIVITY_TITLE = "Presentations Over Time"
    const val LEGEND_SONGS = "Songs"
    const val LEGEND_BIBLE = "Bible Verses"
    const val STAT_SONGS = "Songs presented"
    const val STAT_VERSES = "Bible verses"
    const val STAT_BUSIEST = "Busiest period"
    const val BLANK = "—"
    const val EXPORT_SUCCESS = "Report exported successfully."
    const val EXPORT_ERROR = "Failed to export report."

    /** The subtitle under a chart heading, which reports both totals. */
    fun songsSummary(unique: Int, plays: Int) = "$unique unique songs · $plays total plays"
    fun bibleSummary(unique: Int, plays: Int) = "$unique unique verses · $plays total plays"
}

/** The left-hand chart column is 300.dp wide; everything right of it is the table. */
private const val CHART_PANEL_WIDTH = 300f

// ── Reading what was rendered ───────────────────────────────────────────────────────────────────

private fun ComposeUiTest.textNodes() =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .map {
            it.boundsInRoot to (it.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { t -> t.text } ?: "")
        }

/** Every string on screen, in traversal order. */
internal fun ComposeUiTest.renderedText(): List<String> = textNodes().map { it.second }

/**
 * The ranked labels in the left-hand chart, top to bottom.
 *
 * The chart and the table both render the same titles, so they are told apart by position: the chart
 * occupies a fixed-width column on the left. Each chart row publishes its label and its value as two
 * nodes side by side; only the label — the leftmost of the pair — is returned.
 */
internal fun ComposeUiTest.chartLabels(): List<String> =
    textNodes()
        .filter { it.first.left < CHART_PANEL_WIDTH }
        .groupBy { it.first.top }
        .toSortedMap()
        .values
        .mapNotNull { band -> band.minByOrNull { it.first.left }?.second }

/**
 * The chart's ranked rows as `label to value`, top to bottom.
 *
 * A row publishes its label and its total as two nodes on the same horizontal band, so a band
 * carrying exactly two is a row; the heading and its subtitle sit alone on theirs and drop out.
 */
internal fun ComposeUiTest.chartRows(): List<Pair<String, String>> =
    textNodes()
        .filter { it.first.left < CHART_PANEL_WIDTH }
        .groupBy { it.first.top }
        .toSortedMap()
        .values
        .mapNotNull { band ->
            val cells = band.sortedBy { it.first.left }
            if (cells.size == 2) cells[0].second to cells[1].second else null
        }

/** The strings rendered in the table, right of the chart column. */
internal fun ComposeUiTest.tableText(): List<String> =
    textNodes().filter { it.first.left >= CHART_PANEL_WIDTH }.map { it.second }
