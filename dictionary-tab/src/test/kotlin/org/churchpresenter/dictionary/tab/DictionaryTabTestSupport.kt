@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dictionary.tab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import org.churchpresenter.dictionary.DictionaryFixture
import org.churchpresenter.dictionary.InterlinearRepository
import org.churchpresenter.dictionary.InterlinearVerse
import org.churchpresenter.dictionary.StrongsEntry
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.ui.showsContainingText

/**
 * Harness and fixtures shared by the `DictionaryTab` test classes.
 *
 * The real dictionary is ~14k entries across four bundled JSON files, so these reuse
 * [DictionaryFixture] — the same miniature corpus the `DictionaryViewModel*` suites are built on.
 * That keeps every assertion about data a reader of the test can actually see, and keeps the load
 * off the critical path.
 *
 * `InterlinearRepository` is stubbed the same way [DictionaryViewModelInterlinearTest] stubs it: the
 * real one reads 4–8 MB of bundled JSON, and letting the tab pull it once per test exhausted the
 * test JVM's heap — which surfaced as `OutOfMemoryError` in whatever unrelated class happened to run
 * next, not here.
 *
 * `DictionaryViewModel.load()` is asynchronous, so the harness waits for the entries to arrive
 * before running the test body — on the entries themselves, not on a timer.
 */

// ── Harness ─────────────────────────────────────────────────────────────────────────────────────

/** What the tab reported back, so a test asserts on the choice rather than on a stub. */
internal class DictionaryReports {
    /** number, word, transliteration, definition — exactly what the schedule would be given. */
    val scheduled = mutableListOf<List<String>>()
    val live = mutableListOf<StrongsEntry>()
    val wordClicks = mutableListOf<String>()
    val verseClicks = mutableListOf<Triple<Int, Int, Int>>()
}

/**
 * Stubs the bundled dictionary files, builds a real [DictionaryViewModel], composes `DictionaryTab`
 * over it once its entries have loaded, and runs [block].
 *
 * The mock on `Res` is a JVM-wide object mock, so it is always removed again — leaving it installed
 * would leak into whatever test class runs next.
 */
@OptIn(ExperimentalTestApi::class)
internal fun dictionaryTab(
    /**
     * Interlinear verses per Strong's number, for the "In Scripture" section.
     *
     * Empty by default — that is the state every other suite here runs in, and it keeps the stubbed
     * repository returning nothing for every entry. Passing verses is what makes the section draw.
     */
    verses: Map<String, List<InterlinearVerse>> = emptyMap(),
    /** Resolves the verse text a row shows; null models a Bible that is not loaded. */
    getVerseText: ((bookId: Int, chapter: Int, verse: Int) -> String?)? = null,
    /** Resolves a book's name; null makes rows fall back to "Book <id>". */
    getBookName: ((bookId: Int) -> String?)? = null,
    /**
     * Which books/chapters have tagged data at all — drives the entry-*list*'s own book/chapter/
     * verse filter row (a separate control from the detail pane's "In Scripture" card filter, which
     * is driven by [verses] instead since it only ever looks at the selected entry's own verses).
     */
    booksWithGreekData: List<Int> = emptyList(),
    booksWithHebrewData: List<Int> = emptyList(),
    chaptersForBook: Map<Int, List<Int>> = emptyMap(),
    versesInChapter: Map<Pair<Int, Int>, List<Int>> = emptyMap(),
    /** Which Strong's numbers the entry-list filter should keep once a book is chosen — keyed by
     *  book id only; this harness does not model chapter/verse-level granularity. */
    strongsForBookChapter: Map<Int, Set<String>> = emptyMap(),
    withOnAddToSchedule: Boolean = true,
    withOnGoLive: Boolean = true,
    /** Extra Greek entries, for a test needing a shape the standing corpus does not have. */
    extraEntries: List<StrongsEntry> = emptyList(),
    width: Dp? = null,
    themeMode: ThemeMode? = null,
    block: ComposeUiTest.(vm: DictionaryViewModel, reports: DictionaryReports) -> Unit,
) {
    mockkConstructor(InterlinearRepository::class)
    coEvery { anyConstructed<InterlinearRepository>().ensureGreekLoaded() } returns Unit
    coEvery { anyConstructed<InterlinearRepository>().ensureHebrewLoaded() } returns Unit
    every { anyConstructed<InterlinearRepository>().getVersesForEntry(any()) } returns emptyList()
    verses.forEach { (number, forEntry) ->
        every { anyConstructed<InterlinearRepository>().getVersesForEntry(number) } returns forEntry
    }
    every { anyConstructed<InterlinearRepository>().getBooksWithGreekData() } returns booksWithGreekData
    every { anyConstructed<InterlinearRepository>().getBooksWithHebrewData() } returns booksWithHebrewData
    every { anyConstructed<InterlinearRepository>().getChaptersForBook(any()) } answers {
        chaptersForBook[firstArg()] ?: emptyList()
    }
    every { anyConstructed<InterlinearRepository>().getVersesInChapter(any(), any()) } answers {
        versesInChapter[firstArg<Int>() to secondArg<Int>()] ?: emptyList()
    }
    every { anyConstructed<InterlinearRepository>().getStrongsForBookChapter(any(), any(), any()) } answers {
        strongsForBookChapter[firstArg()] ?: emptySet()
    }
    val vm = DictionaryViewModel(DictionaryFixture.catalog(extraEntries))
    val reports = DictionaryReports()
    try {
        runComposeUiTest {
            setContent {
                ThemedForTest(themeMode) {
                    // The tab paints no ground of its own — in the app it sits on the window's
                    // `colorScheme.background`.
                    Surface(color = MaterialTheme.colorScheme.background) {
                    Box(modifier = width?.let { Modifier.width(it) } ?: Modifier) {
                    DictionaryTab(
                        viewModel = vm,
                        onAddToSchedule = if (withOnAddToSchedule) { { number, word, transliteration, definition ->
                            reports.scheduled += listOf(number, word, transliteration, definition)
                        } } else null,
                        onGoLive = if (withOnGoLive) { { reports.live += it } } else null,
                        getVerseText = getVerseText,
                        getBookName = getBookName,
                        onWordClick = { reports.wordClicks += it },
                        onVerseClick = { book, chapter, verse ->
                            reports.verseClicks += Triple(book, chapter, verse)
                        },
                    )
                    }
                    }
                }
            }
            // The tab kicks off the load itself; wait for the entries rather than for a duration.
            waitUntil("the dictionary to load") { vm.entries.isNotEmpty() }
            block(vm, reports)
        }
    } finally {
        runCatching { vm.dispose() }
        unmockkConstructor(InterlinearRepository::class)
    }
}

/**
 * The tab composed with **nothing but a view model** — every optional parameter left at its default.
 *
 * This is a real caller shape, not a coverage trick: the tab is drawn with `appSettings = null` and
 * no callbacks in the setup wizard's preview and wherever it is shown read-only, and those defaults
 * are what decide whether a word chip is clickable, whether a verse row offers "go to verse" and
 * whether the definition is rendered as a plain paragraph or as Strong's links. [dictionaryTab]
 * passes all of them, so none of those branches is otherwise reached.
 */
@OptIn(ExperimentalTestApi::class)
internal fun bareDictionaryTab(
    verses: Map<String, List<InterlinearVerse>> = emptyMap(),
    extraEntries: List<StrongsEntry> = emptyList(),
    block: ComposeUiTest.(vm: DictionaryViewModel) -> Unit,
) {
    mockkConstructor(InterlinearRepository::class)
    coEvery { anyConstructed<InterlinearRepository>().ensureGreekLoaded() } returns Unit
    coEvery { anyConstructed<InterlinearRepository>().ensureHebrewLoaded() } returns Unit
    every { anyConstructed<InterlinearRepository>().getVersesForEntry(any()) } returns emptyList()
    verses.forEach { (number, forEntry) ->
        every { anyConstructed<InterlinearRepository>().getVersesForEntry(number) } returns forEntry
    }
    every { anyConstructed<InterlinearRepository>().getBooksWithGreekData() } returns emptyList()
    every { anyConstructed<InterlinearRepository>().getBooksWithHebrewData() } returns emptyList()
    val vm = DictionaryViewModel(DictionaryFixture.catalog(extraEntries))
    try {
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        DictionaryTab(viewModel = vm)
                    }
                }
            }
            waitUntil("the dictionary to load") { vm.entries.isNotEmpty() }
            block(vm)
        }
    } finally {
        runCatching { vm.dispose() }
        unmockkConstructor(InterlinearRepository::class)
    }
}

@Composable
private fun ThemedForTest(themeMode: ThemeMode?, content: @Composable () -> Unit) {
    if (themeMode == null) MaterialTheme(content = content)
    else ChurchPresenterTheme(themeMode = themeMode, content = content)
}

// ── Labels, as the tab renders them ─────────────────────────────────────────────────────────────

internal object DictionaryLabel {
    const val SEARCH_HINT = "Search by number, word, or definition…"
    const val ALL = "All"
    const val HEBREW = "Hebrew"
    const val GREEK = "Greek"
    const val NO_RESULTS = "No results found"
    const val SELECT_ENTRY = "Select an entry to view details"
    const val TRANSLITERATION = "Transliteration:"
    const val PRONUNCIATION = "Pronunciation:"
    const val DEFINITION = "Definition"
    const val KJV_USAGE = "KJV Usage"
    const val BACK = "Back"
    const val FORWARD = "Forward"
    const val GO_LIVE = "Go Live"
    const val ADD_TO_SCHEDULE = "Add to Schedule"
    const val IN_SCRIPTURE = "In Scripture"
    const val NO_TAGGED_VERSES = "No tagged verses found for this entry"
    const val GO_TO_VERSE = "Go to verse"
}

// ── Reading and driving what was rendered ───────────────────────────────────────────────────────
// (renderedText/showsExactly/showsContainingText are shared — see TabRenderedText.kt)

/** A button, addressed by the content description its tooltip gives it. */
internal fun ComposeUiTest.dictButton(label: String): SemanticsNodeInteraction =
    onNodeWithContentDescription(label)

internal fun ComposeUiTest.hasDictButton(label: String): Boolean =
    onAllNodesWithContentDescription(label)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .isNotEmpty()

/** The search box — the tab's only freely-typed field. */
internal fun ComposeUiTest.dictSearchField() = onAllNodes(hasSetTextAction())[0]

internal fun ComposeUiTest.dictSearch(query: String) {
    dictSearchField().performTextReplacement(query)
    waitForIdle()
}

/**
 * Matches the list row for [entry].
 *
 * A row merges its number, word and transliteration into one node, but they stay *separate* text
 * entries inside it — so a match on the concatenation finds nothing, and a match on any one of them
 * also hits the detail pane, which repeats all three. Requiring two of them together identifies the
 * row and nothing else.
 */
internal fun rowOf(entry: StrongsEntry) =
    hasText(entry.number) and hasText(entry.transliteration)

/** Whether [entry] is in the list right now. */
internal fun ComposeUiTest.listShows(entry: StrongsEntry): Boolean =
    onAllNodes(rowOf(entry)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

/**
 * Whether the detail pane is showing [entry].
 *
 * Keyed on the pronunciation because the number, word and transliteration are on the list row too —
 * asserting on those would pass whether or not the entry was ever opened, and would fail when
 * checking that a *previous* entry is no longer on show.
 */
internal fun ComposeUiTest.detailShows(entry: StrongsEntry): Boolean =
    showsContainingText(entry.pronunciation)

/**
 * Clicks one of the three language chips above the list.
 *
 * Addressed as the topmost node with that label: once interlinear data is available the tab also
 * shows book and chapter filters, which have an "All" of their own, so a bare lookup is ambiguous.
 */
internal fun ComposeUiTest.clickLanguageFilter(label: String) {
    val nodes = onAllNodesWithText(label).fetchSemanticsNodes(atLeastOneRootRequired = false)
    val topmost = nodes.indices.minByOrNull { nodes[it].boundsInRoot.top }
        ?: error("no chip labelled \"$label\" is on screen")
    onAllNodesWithText(label)[topmost].performClick()
    waitForIdle()
}

/** Opens an entry from the list. */
internal fun ComposeUiTest.selectEntry(entry: StrongsEntry) {
    onNode(rowOf(entry)).performClick()
    waitForIdle()
}
