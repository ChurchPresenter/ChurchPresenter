@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.data.settings.SongSettings
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.LocalShortcuts
import org.churchpresenter.app.churchpresenter.utils.ShortcutMap
import org.churchpresenter.app.churchpresenter.viewmodel.SongsViewModel
import java.io.File
import java.nio.file.Files

/**
 * Harness and fixtures shared by the `SongsTab` test classes.
 *
 * **Why this tab is testable at all.** `tabs/` sat at 0% because a tab needs a real view model, and
 * `SongsViewModel` used to load its songs asynchronously on a shared dispatcher — so a test either
 * raced it or polled a wall clock for it. Since `ioDispatcher` became injectable (issue #56), the
 * view model loads *synchronously* from a temp songbook folder, which makes `SongsTab` ordinary
 * Compose: build the model, compose the tab, assert on what is on screen.
 *
 * Nothing is stubbed. Songs are written to disk with the real [SongFileParser] and read back through
 * the real load path, so the fixtures cannot drift from the file format the app actually writes.
 * `SongsTab` needs no `PresenterManager` and no host window — only a view model, an `AppSettings`,
 * and the section-selected callback.
 */

// ── Fixtures ────────────────────────────────────────────────────────────────────────────────────

/** One song as it will be written to disk. */
internal data class SongFixture(
    val number: String,
    val title: String,
    val songbook: String = "Hymnal",
    val author: String = "",
    val lyrics: List<String> = listOf("[Verse 1]", "a line of $title"),
    /** The second language, for the bilingual layouts. Empty for a single-language song. */
    val secondaryLyrics: List<String> = emptyList(),
    val secondaryTitle: String = "",
)

/**
 * The only configuration in which left and right walk between songs.
 *
 * `isSongLineMode` is true if **any** of the four display modes is "line", and two of them —
 * `lowerThirdDisplayMode` and `lowerThirdLookAheadDisplayMode` — default to it. So out of the box the
 * arrow keys (and per-line lyric clicks) are already in line mode, and every one of these four has to
 * say "verse" before the tab treats a section as one whole click target instead of per-line ones.
 */
internal fun verseMode() = SongSettings(
    fullscreenDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE,
    lowerThirdDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE,
    lookAheadDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE,
    lowerThirdLookAheadDisplayMode = Constants.SONG_DISPLAY_MODE_VERSE,
)

internal val defaultSongs = listOf(
    SongFixture(number = "1", title = "Amazing Grace", author = "John Newton"),
    SongFixture(number = "2", title = "Be Thou My Vision", author = "Dallan Forgaill"),
    SongFixture(number = "12", title = "Amazing Love", author = "Charles Wesley"),
    SongFixture(number = "3", title = "How Great Thou Art", songbook = "Chorus Book"),
)

// ── Harness ─────────────────────────────────────────────────────────────────────────────────────

/** What the tab reported back, so a test asserts on the choice rather than on a stub. */
internal class TabReports {
    var selectedSection: LyricSection? = null
    val allSections = mutableListOf<List<LyricSection>>()
    var sectionIndex: Int? = null
    var lineIndex: Int? = null
    val scheduled = mutableListOf<String>()
    val presenting = mutableListOf<Presenting>()
    var settingsChanges = 0

    /** The temp songbook folder backing this run — for a test that wants to add a file on disk and
     *  prove a reload actually reads it, rather than merely that a callback fired. */
    lateinit var songsDir: File

    /**
     * The settings the tab's most recent change would produce.
     *
     * The tab never holds settings — it hands the host a transform — so the harness applies that
     * transform to the settings it was composed with and keeps the result, letting a test assert the
     * intended settings rather than that a callback fired.
     */
    var settingsAfterChange: AppSettings? = null
}

/**
 * Writes [songs] into a temp songbook folder, builds a real [SongsViewModel] over it, composes
 * `SongsTab`, and runs [block].
 *
 * The view model uses immediate dispatchers, so by the time [block] runs the songs are loaded — the
 * body can assert straight away without waiting for anything.
 */
@OptIn(ExperimentalTestApi::class)
internal fun songsTab(
    songs: List<SongFixture> = defaultSongs,
    songSettings: SongSettings = SongSettings(),
    /**
     * Which table columns are hidden, or null for the app's own default (tune, plays, author,
     * composer). Lives on [AppSettings] rather than [SongSettings], so it is its own parameter.
     */
    hiddenCols: Set<String>? = null,
    /** Metronome tempo per song id, which lives on [AppSettings] rather than [SongSettings]. */
    songBpm: Map<String, Int>? = null,
    /**
     * Whether a stage monitor screen is configured.
     *
     * The metronome-tempo tile is drawn only when one is — the tempo exists to drive that screen's
     * flashing dot — so a test for the tile has to turn this on.
     */
    stageMonitor: Boolean = false,
    /**
     * True when the tab is what is live on the output.
     *
     * It changes what the arrow keys do: while presenting they step *within* the live song and push
     * each step out, and while not presenting left/right move between songs instead.
     */
    isPresenting: Boolean = false,
    statistics: org.churchpresenter.app.churchpresenter.data.StatisticsManager? = null,
    /** Whether the tab is given somewhere to add a song to the schedule — off to test that the
     *  add-to-schedule actions are hidden rather than merely disabled when there is nowhere to send it. */
    withOnAddToSchedule: Boolean = true,
    /**
     * The schedule row the operator has clicked, and its version.
     *
     * Held as state the caller owns so a test can set it *after* the tab is composed — which is how
     * it arrives in the app — and can bump the version to re-fire the same song, the case the
     * version exists for. Left null and 0 by default, which is the tab with nothing selected.
     */
    scheduleSelection: MutableState<ScheduleItem.SongItem?> = mutableStateOf(null),
    scheduleSelectionVersion: MutableState<Int> = mutableStateOf(0),
    /** Null keeps the plain MaterialTheme every other test composes under; set to shoot a theme. */
    themeMode: ThemeMode? = null,
    /** The bindings the tab resolves its key handler and nav hint through; the shipped set unless overridden. */
    shortcuts: ShortcutMap = ShortcutMap.DEFAULT,
    block: ComposeUiTest.(vm: SongsViewModel, reports: TabReports) -> Unit,
) {
    val dir = Files.createTempDirectory("cp-songs-tab").toFile()
    try {
        val parser = SongFileParser()
        songs.forEach { s ->
            val book = File(dir, s.songbook).apply { mkdirs() }
            parser.writeSongFile(
                SongItem(
                    number = s.number,
                    title = s.title,
                    songbook = s.songbook,
                    author = s.author,
                    lyrics = s.lyrics,
                    secondaryTitle = s.secondaryTitle,
                    secondaryLyrics = s.secondaryLyrics,
                ),
                File(book, "${s.number} - ${s.title}.song").absolutePath,
            )
        }
        val settings = AppSettings(songSettings = songSettings.copy(storageDirectory = dir.absolutePath))
            .let { if (hiddenCols != null) it.copy(songHiddenCols = hiddenCols) else it }
            .let { if (songBpm != null) it.copy(songBpm = songBpm) else it }
            .let {
                if (!stageMonitor) it
                else it.copy(
                    projectionSettings = it.projectionSettings.copy(
                        screenAssignments = listOf(
                            ScreenAssignment(displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR),
                        ),
                    ),
                )
            }
        val vm = SongsViewModel(
            settings,
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            enableFolderWatcher = false,
        )
        val reports = TabReports()
        reports.songsDir = dir
        runComposeUiTest {
            setContent {
                ThemedForTest(themeMode) {
                  CompositionLocalProvider(LocalShortcuts provides shortcuts) {
                    SongsTab(
                        viewModel = vm,
                        appSettings = settings,
                        onSettingsChange = { transform ->
                            reports.settingsChanges++
                            reports.settingsAfterChange = transform(reports.settingsAfterChange ?: settings)
                        },
                        onAddToSchedule =
                            if (withOnAddToSchedule) { { _, title, _, _ -> reports.scheduled += title } } else null,
                        onSongItemSelected = { reports.selectedSection = it },
                        onAllSectionsChanged = { reports.allSections += it },
                        onSectionIndexChanged = { reports.sectionIndex = it },
                        onLineIndexChanged = { reports.lineIndex = it },
                        onPresenting = { reports.presenting += it },
                        isPresenting = isPresenting,
                        statisticsManager = statistics,
                        selectedSongItem = scheduleSelection.value,
                        selectedSongItemVersion = scheduleSelectionVersion.value,
                    )
                  }
                }
            }
            block(vm, reports)
        }
    } finally {
        dir.deleteRecursively()
    }
}

// ── Labels, as the tab renders them ─────────────────────────────────────────────────────────────

internal object SongsLabel {
    const val SEARCH_PLACEHOLDER = "Search songs..."
    const val ALL_SONGBOOKS = "All Song Books"
    const val CONTAINS = "Contains"
    const val STARTS_WITH = "Starts With"
    const val EXACT_MATCH = "Exact Match"
    const val ADD_TO_SCHEDULE = "Add to Schedule"
    const val FAVORITES = "Favorites"
    const val NEW_SONG = "New Song"
}

// ── Reading and driving what was rendered ───────────────────────────────────────────────────────

/** Every string on screen. */
internal fun ComposeUiTest.rendered(): List<String> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { t -> t.text } }

internal fun ComposeUiTest.shows(text: String): Boolean = rendered().any { it == text }

/**
 * Substring match, for the controls whose label and value land in one node.
 *
 * `DropdownSelector` merges its semantics, so the songbook filter renders as the single string
 * "SONG BOOKAll Song Books" rather than as a caption and a value — an exact match on either half
 * finds nothing.
 */
internal fun ComposeUiTest.showsContaining(fragment: String): Boolean =
    rendered().any { it.contains(fragment) }

/**
 * The search box: the tab's only freely-typed field.
 *
 * Addressed as the single node taking typed text rather than by its caption, because the placeholder
 * is a separate `Text` inside a `BasicTextField` decoration box and disappears once anything is typed.
 */
internal fun ComposeUiTest.searchBox() = onAllNodes(hasSetTextAction())[0]

internal fun ComposeUiTest.search(query: String) {
    searchBox().performTextReplacement(query)
    waitForIdle()
}

/**
 * The song titles currently listed, **in the order the tab shows them**.
 *
 * Ordered by vertical position rather than by walking the fixture list, so a change to how the tab
 * sorts its rows is visible here. (An earlier version of this helper filtered the fixtures by
 * presence, which silently made every ordering assertion a presence check.) Only titles belonging to
 * [from] are returned, so the surrounding chrome and the lyric pane do not leak in.
 */
internal fun ComposeUiTest.listedTitles(from: List<SongFixture> = defaultSongs): List<String> {
    val titles = from.map { it.title }.toSet()
    return onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .mapNotNull { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
            if (text in titles) node.boundsInRoot.top to text!! else null
        }
        .sortedBy { it.first }
        .map { it.second }
        .distinct()
}

@Composable
private fun ThemedForTest(themeMode: ThemeMode?, content: @Composable () -> Unit) {
    if (themeMode == null) MaterialTheme(content = content)
    else ChurchPresenterTheme(themeMode = themeMode, content = content)
}
