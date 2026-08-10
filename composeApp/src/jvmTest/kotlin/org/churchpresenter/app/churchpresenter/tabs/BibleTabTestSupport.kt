@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.churchpresenter.app.churchpresenter.ui.theme.ChurchPresenterTheme
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.app.churchpresenter.data.SpbFixture
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleTranslationSettings
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.BibleEngineClient
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import org.churchpresenter.app.churchpresenter.data.CrossReferenceRepository
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.app.churchpresenter.data.VerseSequenceLog
import java.io.File
import java.nio.file.Files

/**
 * Harness and fixtures shared by the `BibleTab` test classes.
 *
 * **Why this tab is testable at all.** `BibleTab` needs a real [BibleViewModel], which loads its
 * modules off disk. Its scope and its file reads used to be hardcoded to `Dispatchers.Main` and
 * `Dispatchers.IO`, so a test could only poll a wall clock for the load — the same shape that made
 * the Songs tests flaky under contention (issue #56). Both are now injectable, exactly as on
 * `SongsViewModel`, so passing immediate dispatchers loads the Bible *synchronously* and the tab
 * becomes ordinary Compose: build the model, compose the tab, assert on what is on screen.
 *
 * Nothing is stubbed. The `.spb` module is written with the real [SpbFixture] and read back through
 * the real load path, so a fixture cannot drift from the format the app actually parses. `BibleTab`
 * needs no host window and no `PresenterManager` — those parameters are optional and the tab renders
 * its browse/search UI without them. STT is optional too, but passing a connected [STTManager] is
 * what draws the auto-follow panel (see the `stt` parameter), and a connected manager needs no socket
 * since [STTManager.applyConnected] is the same transition its own socket callback runs.
 */

// ── Fixtures ────────────────────────────────────────────────────────────────────────────────────

/**
 * Genesis, Psalms and John, with verse text distinct enough that a search can only match one.
 *
 * Deliberately not [SpbFixture.sampleContent]: the browse columns are asserted against, so the
 * shape here is chosen to be unambiguous — Genesis 1 has three verses and Genesis 2 has one, no two
 * verses share wording, and every book has a verse in its first chapter so that selecting a book
 * always lands on something. The chapter counts are the real ones for Psalms 23 and John 3, because
 * those are the references the smart search is exercised with.
 */
internal val bibleFixture = SpbFixture.buildContent(
    title = "Test Bible",
    books = listOf(
        SpbFixture.Book(1, "Genesis", 2),
        SpbFixture.Book(19, "Psalms", 23),
        SpbFixture.Book(43, "John", 3),
    ),
    verses = listOf(
        SpbFixture.Verse(1, 1, 1, "In the beginning God created the heaven and the earth."),
        SpbFixture.Verse(1, 1, 2, "And the earth was without form, and void."),
        SpbFixture.Verse(1, 1, 3, "And God said, Let there be light."),
        SpbFixture.Verse(1, 2, 1, "Thus the heavens were finished."),
        SpbFixture.Verse(19, 1, 1, "Blessed is the man that walketh not in the counsel."),
        SpbFixture.Verse(19, 23, 1, "The LORD is my shepherd; I shall not want."),
        SpbFixture.Verse(43, 1, 1, "In the beginning was the Word."),
        SpbFixture.Verse(43, 3, 16, "For God so loved the world."),
    ),
)

/** File name of the optional second module — see `bibleTab`'s `secondContent`. */
internal const val SECOND_MODULE = "second.spb"

// ── Harness ─────────────────────────────────────────────────────────────────────────────────────

/** What the tab reported back, so a test asserts on the choice rather than on a stub. */
internal class BibleReports {
    val selectedVerses = mutableListOf<List<SelectedVerse>>()
    val scheduled = mutableListOf<String>()
    val presenting = mutableListOf<Presenting>()
    var settingsChanges = 0

    /**
     * The settings the tab's most recent change would produce.
     *
     * The tab never holds settings — it hands the host a transform — so the harness applies that
     * transform and keeps the result, letting a test assert the intended settings rather than that
     * a callback fired.
     */
    var settingsAfterChange: AppSettings? = null

    /** The most recent go-live selection, or null if the tab has sent none. */
    val live: List<SelectedVerse>? get() = selectedVerses.lastOrNull()
}

/**
 * Writes [content] as the primary Bible into a temp folder, builds a real [BibleViewModel] over it,
 * composes `BibleTab`, and runs [block].
 *
 * `storageDirectory` is set explicitly so nothing resolves under the real `user.home`. The view
 * model uses immediate dispatchers, so the module is loaded before [block] runs.
 */
@OptIn(ExperimentalTestApi::class)
internal fun bibleTab(
    content: String = bibleFixture,
    /**
     * A second module, written alongside the first and configured as the secondary translation.
     *
     * Needed by anything exercising a translation change — the swap button does nothing with one
     * module configured. Give it wording that differs from [content] so a test can tell which
     * module the tab is reading from.
     */
    secondContent: String? = null,
    settings: (AppSettings) -> AppSettings = { it },
    /**
     * Passed to the tab as its [STTManager] when non-null.
     *
     * The auto-follow panel is drawn only when the Bible engine is enabled in settings AND an STT
     * connection is up, so a test that wants it passes a manager with `applyConnected()` already
     * called. Left null everywhere else, which is how the tab looks at first launch.
     */
    stt: STTManager? = null,
    /**
     * Passed as the tab's [PresenterManager] when non-null.
     *
     * Going live releases Bible Hold on it, which is the only reason a test needs one.
     */
    presenter: PresenterManager? = null,
    /**
     * Passed as the tab's [StatisticsManager] when non-null.
     *
     * It resolves `~/.churchpresenter` at construction, so a test that passes one must isolate
     * `user.home` first — see [bibleTabWithStatistics].
     */
    statistics: StatisticsManager? = null,
    /**
     * Passed as the tab's [VerseSequenceLog] when non-null.
     *
     * Its default store is under `~/.churchpresenter`, so build one over a temp file rather than
     * calling `VerseSequenceLog()`.
     */
    sequenceLog: VerseSequenceLog? = null,
    /**
     * Passed as the tab's [CrossReferenceRepository] when non-null.
     *
     * Left null the tab falls back to the shared instance over the real 3 MB dataset, which would
     * make a test depend on what TSK happens to say about a verse. Cross-reference tests build
     * their own over a fixture instead.
     */
    crossReferences: CrossReferenceRepository? = null,
    /** Instance Link Controller mode: non-null makes the tab mirror every go-live to the primary. */
    onInstanceLinkSendVerse: ((SelectedVerse) -> Unit)? = null,
    onInstanceLinkSendBibleHold: ((Boolean) -> Unit)? = null,
    /**
     * Constrains the tab's width.
     *
     * The search row lays itself out from `BoxWithConstraints`, stacking into a column below 440dp
     * and sitting in one row above it, so which of the two branches a test sees is decided here.
     * Left null everywhere else, which gives the tab the whole test window — the wide branch.
     */
    width: Dp? = null,
    selectedVerseItem: ScheduleItem.BibleVerseItem? = null,
    bibleEngineClient: BibleEngineClient? = null,
    /** Null keeps the plain MaterialTheme every other test composes under; set to shoot a theme. */
    themeMode: ThemeMode? = null,
    block: ComposeUiTest.(vm: BibleViewModel, reports: BibleReports) -> Unit,
) {
    val dir = Files.createTempDirectory("cp-bible-tab").toFile()
    try {
        SpbFixture.spbFile(dir, content = content)
        secondContent?.let { SpbFixture.spbFile(dir, name = SECOND_MODULE, content = it) }
        val initialSettings = settings(
            AppSettings(
                bibleSettings = BibleSettings(
                    storageDirectory = dir.absolutePath,
                    primaryBible = "test.spb",
                    secondaryBible = if (secondContent != null) SECOND_MODULE else "",
                    translations = if (secondContent == null) emptyList() else listOf(
                        BibleTranslationSettings(fileName = "test.spb"),
                        BibleTranslationSettings(fileName = SECOND_MODULE),
                    ),
                )
            )
        )
        val vm = BibleViewModel(
            initialSettings,
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val reports = BibleReports()
        runComposeUiTest {
            setContent {
                // The tab holds no settings of its own — the host applies its transform and hands
                // the result back. Doing that here rather than only recording it is what lets a
                // test drive a settings change through to its effect on the tab: a swap, for
                // instance, has to reach BibleViewModel.updateSettings to reload anything.
                var appSettings by remember { mutableStateOf(initialSettings) }
                ThemedForTest(themeMode) {
                    Box(modifier = width?.let { Modifier.width(it) } ?: Modifier) {
                    BibleTab(
                        viewModel = vm,
                        appSettings = appSettings,
                        onSettingsChange = { transform ->
                            reports.settingsChanges++
                            val updated = transform(appSettings)
                            reports.settingsAfterChange = updated
                            appSettings = updated
                        },
                        onAddToSchedule = { book, chapter, verse, _, _, _ ->
                            reports.scheduled += "$book $chapter:$verse"
                        },
                        onVerseSelected = { reports.selectedVerses += it },
                        onPresenting = { reports.presenting += it },
                        sttManager = stt,
                        presenterManager = presenter,
                        statisticsManager = statistics,
                        verseSequenceLog = sequenceLog,
                        crossReferences = crossReferences,
                        onInstanceLinkSendVerse = onInstanceLinkSendVerse?.let { send ->
                            { book, chapter, verseNumber, verseText, verseRange ->
                                send(
                                    SelectedVerse(
                                        bookName = book,
                                        chapter = chapter,
                                        verseNumber = verseNumber,
                                        verseText = verseText,
                                        verseRange = verseRange,
                                    )
                                )
                            }
                        },
                        onInstanceLinkSendBibleHold = onInstanceLinkSendBibleHold,
                        selectedVerseItem = selectedVerseItem,
                        bibleEngineClient = bibleEngineClient,
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

internal object BibleLabel {
    const val BOOK = "Book"
    const val CHAPTER = "Chapter"
    const val VERSE = "Verse"
    const val GO_LIVE = "Go Live"
    const val ADD_TO_SCHEDULE = "Add to Schedule"
    const val HISTORY = "History"
    const val CROSS_REFS = "Refs"
    const val CROSS_REFS_EMPTY = "No cross references"
    const val OFTEN_NEXT = "Often next"
    const val CLEAR_HISTORY = "Clear"
    const val ENTIRE_BIBLE = "Entire Bible"
    const val CURRENT_BOOK = "Current Book"
    const val CONTAINS_PHRASE = "Contains Phrase"
    const val EXACT_MATCH = "Exact Match"
    const val NO_PRIMARY = "No Primary Bible Configured"
    const val SWAP = "Swap"
    /** The mic button's tooltip, which is what it offers to do next. */
    const val STT_CONNECT = "Connect"
    const val STT_DISCONNECT = "Disconnect"
    const val SEARCH = "Search"
    const val SEARCH_PLACEHOLDER = "Reference or text — e.g. John 3:16, mat 1, or a word"
}

// ── Reading and driving what was rendered ───────────────────────────────────────────────────────

// renderedText/showsExactly/showsContainingText live in TabRenderedText.kt — they are shared with
// the other tab suites in this package.

/**
 * The smart-search box.
 *
 * Addressed as the node taking typed text rather than by its caption: the placeholder is a separate
 * `Text` inside the `BasicTextField` decoration box and vanishes as soon as anything is typed.
 */
internal fun ComposeUiTest.bibleSearchBox() = onAllNodes(hasSetTextAction())[0]

internal fun ComposeUiTest.bibleSearch(query: String) {
    bibleSearchBox().performTextReplacement(query)
    waitForIdle()
}

/**
 * The action-row buttons, addressed by the content description [ActionIconButton] gives them —
 * which is the tooltip text, so the label a user would see is also the test's selector.
 */
internal fun ComposeUiTest.actionButton(label: String) = onNodeWithContentDescription(label)

internal fun ComposeUiTest.hasActionButton(label: String): Boolean =
    onAllNodesWithContentDescription(label)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .isNotEmpty()

/**
 * The right-hand (live chapter) copy of a verse line, in split-browse mode.
 *
 * Both panels show the same chapter, so the line appears twice with identical text; they are told
 * apart by position — the live panel is the right-hand one.
 */
internal fun ComposeUiTest.livePanelVerse(text: String): SemanticsNodeInteraction {
    val nodes = onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false)
    val rightmost = nodes.indices.maxByOrNull { nodes[it].boundsInRoot.left }
        ?: error("no verse line reading \"$text\" is on screen")
    return onAllNodesWithText(text)[rightmost]
}

/** How many times a verse line is on screen — two when the live panel mirrors the browser. */
internal fun ComposeUiTest.countOnScreen(text: String): Int =
    onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).size

/** The search box's clear (×) button — tagged in the tab because its icon is decorative. */
internal fun ComposeUiTest.clearSearchButton() = onNodeWithTag("bible_searchClear")

/**
 * The verse lines currently listed, in the order shown.
 *
 * The verse column renders each line as `"<number>. <text>"` (the convention `viewmodel/VerseLine.kt`
 * parses), so a line is identified by that shape rather than by matching fixture text — which keeps
 * the ordering assertions honest instead of quietly becoming presence checks.
 */
internal fun ComposeUiTest.listedVerseLines(): List<String> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .mapNotNull { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)
                ?.joinToString("") { it.text } ?: return@mapNotNull null
            if (Regex("""^\d+\.\s""").containsMatchIn(text)) node.boundsInRoot.top to text else null
        }
        .sortedBy { it.first }
        .map { it.second }
        .distinct()

@Composable
private fun ThemedForTest(themeMode: ThemeMode?, content: @Composable () -> Unit) {
    if (themeMode == null) MaterialTheme(content = content)
    else ChurchPresenterTheme(themeMode = themeMode, content = content)
}
