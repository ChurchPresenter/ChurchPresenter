@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import java.io.File
import java.nio.file.Files

/**
 * Harness and fixtures shared by the `StatisticsTab` test classes.
 *
 * The tab has no settings of its own — it is a read-only view over [StatisticsManager], plus one
 * button that empties it. That shapes everything here: there is nothing to assert in `AppSettings`,
 * so every assertion is either about what is on screen or about the manager's own state.
 *
 * **`StatisticsManager` reads `user.home` in its field initialisers** and writes `statistics.json`
 * and `play_log.json` under it, so each test gets its own home ([withStatsHome]) and builds the
 * manager inside it. Without that the tests would read the developer's real play history — which
 * would make them non-deterministic and, worse, the Clear button test would wipe it.
 *
 * The manager is seeded through its **public recording API** rather than by writing its JSON, so the
 * fixtures exercise the same path the app uses and cannot drift from the format.
 */
internal fun <T> withStatsHome(block: (home: File) -> T): T {
    val home = Files.createTempDirectory("cp-stats-tab").toFile()
    val previous = System.getProperty("user.home")
    System.setProperty("user.home", home.absolutePath)
    return try {
        block(home)
    } finally {
        System.setProperty("user.home", previous)
        home.deleteRecursively()
    }
}

/**
 * Renders the tab over a [StatisticsManager] built inside an isolated home, which [seed] may fill
 * before the first composition.
 */
@OptIn(ExperimentalTestApi::class)
internal fun statisticsTab(
    seed: StatisticsManager.() -> Unit = {},
    block: ComposeUiTest.(stats: StatisticsManager) -> Unit,
) = withStatsHome {
    val stats = StatisticsManager().apply(seed)
    runComposeUiTest {
        setContent { MaterialTheme { StatisticsTab(statisticsManager = stats) } }
        block(stats)
    }
}

// ── Seeding ─────────────────────────────────────────────────────────────────────────────────────

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

// ── Labels, as the tab renders them ─────────────────────────────────────────────────────────────

internal object StatsLabel {
    const val TOP_SONGS = "Top Songs"
    const val TOP_VERSES = "Top Verses"
    const val CLEAR = "Clear Statistics"
    const val EMPTY = "—"

    /** The heading a section carries: plain when the songbook or Bible has no name. */
    fun heading(base: String, group: String): String =
        if (group.isNotEmpty()) "$base ($group)" else base
}

// ── Reading the rendered table ──────────────────────────────────────────────────────────────────

/** Every string the tab renders, in traversal order. */
internal fun ComposeUiTest.renderedLines(): List<String> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { t -> t.text } }

/**
 * The rows of the section headed [heading], as `rank to "title"` pairs.
 *
 * Rows publish three separate `Text` nodes — the rank, the name and the count — with nothing tying
 * them together in the tree, so they are grouped by geometry: everything on the same horizontal band
 * belongs to one row, and the bands below a heading and above the next one belong to that section.
 */
internal fun ComposeUiTest.rowsUnder(heading: String): List<Triple<String, String, String>> {
    val all = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .map {
            it.boundsInRoot to (it.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { t -> t.text } ?: "")
        }
    val headings =
        all.filter { it.second.startsWith(StatsLabel.TOP_SONGS) || it.second.startsWith(StatsLabel.TOP_VERSES) }
        .sortedBy { it.first.top }
    val start = headings.firstOrNull { it.second == heading } ?: error("no section headed \"$heading\"")
    val nextTop = headings.firstOrNull { it.first.top > start.first.top }?.first?.top ?: Float.MAX_VALUE

    return all.filter { it.first.top > start.first.top && it.first.top < nextTop }
        .groupBy { it.first.top }
        .toSortedMap()
        .values
        .mapNotNull { band ->
            val cells = band.sortedBy { it.first.left }.map { it.second }
            if (cells.size == 3) Triple(cells[0], cells[1], cells[2]) else null
        }
}

/**
 * The tab's only interactive element. `onNode` fails unless there is exactly one clickable on
 * screen, so using this to reach Clear also asserts that nothing else has become clickable.
 */
internal fun ComposeUiTest.clearButton() = onNode(hasClickAction())
