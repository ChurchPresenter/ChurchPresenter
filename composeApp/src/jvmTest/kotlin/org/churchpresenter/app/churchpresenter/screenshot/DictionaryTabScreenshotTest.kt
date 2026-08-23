@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.dictionary.InterlinearVerse
import org.churchpresenter.dictionary.InterlinearWord
import org.churchpresenter.dictionary.StrongsEntry
import org.churchpresenter.app.churchpresenter.tabs.DictionaryLabel
import org.churchpresenter.app.churchpresenter.tabs.clickLanguageFilter
import org.churchpresenter.app.churchpresenter.tabs.dictButton
import org.churchpresenter.app.churchpresenter.tabs.dictSearch
import org.churchpresenter.app.churchpresenter.tabs.dictionaryTab
import org.churchpresenter.app.churchpresenter.tabs.selectEntry
import org.churchpresenter.dictionary.DictionaryFixture
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import org.churchpresenter.ui.screenshot.captureTo
import org.churchpresenter.ui.screenshot.stackedThemes

/**
 * Every state of the Strong's dictionary tab, in both themes.
 *
 * Driven on `DictionaryFixture` — the same four-entry corpus the view-model suites use — rather than
 * the bundled ~14k entries, so what a reviewer sees in the list is something they can read in the
 * test. The interlinear repository is stubbed for the same reason it is everywhere else here: the
 * real one reads several megabytes of JSON per test and exhausted the test JVM's heap.
 *
 * Hebrew and Greek entries are shot separately on purpose: their numbers are drawn in the theme's
 * two original-language accents, and one image cannot show both.
 */
class DictionaryTabScreenshotTest {

    private fun shoot(
        name: String,
        verses: Map<String, List<InterlinearVerse>> = emptyMap(),
        getVerseText: ((Int, Int, Int) -> String?)? = null,
        getBookName: ((Int) -> String?)? = null,
        booksWithGreekData: List<Int> = emptyList(),
        booksWithHebrewData: List<Int> = emptyList(),
        chaptersForBook: Map<Int, List<Int>> = emptyMap(),
        versesInChapter: Map<Pair<Int, Int>, List<Int>> = emptyMap(),
        strongsForBookChapter: Map<Int, Set<String>> = emptyMap(),
        withOnAddToSchedule: Boolean = true,
        withOnGoLive: Boolean = true,
        width: Dp? = null,
        rootIndex: Int = 0,
        drive: ComposeUiTest.() -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        dictionaryTab(
            verses = verses,
            getVerseText = getVerseText,
            getBookName = getBookName,
            booksWithGreekData = booksWithGreekData,
            booksWithHebrewData = booksWithHebrewData,
            chaptersForBook = chaptersForBook,
            versesInChapter = versesInChapter,
            strongsForBookChapter = strongsForBookChapter,
            withOnAddToSchedule = withOnAddToSchedule,
            withOnGoLive = withOnGoLive,
            width = width,
            themeMode = mode,
        ) { _, _ ->
            drive()
            waitForIdle()
            captureTo(file, rootIndex)
        }
    }

    // ── The list ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `nothing opened yet`() = shoot("browsing")

    @Test
    fun `filtered to Hebrew`() = shoot("filter_hebrew") { clickLanguageFilter(DictionaryLabel.HEBREW) }

    @Test
    fun `filtered to Greek`() = shoot("filter_greek") { clickLanguageFilter(DictionaryLabel.GREEK) }

    @Test
    fun `searched by word`() = shoot("search") { dictSearch("love") }

    @Test
    fun `a search that finds nothing`() = shoot("no_results") { dictSearch("zzzz") }

    // ── An entry open ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a Hebrew entry`() = shoot("entry_hebrew") { selectEntry(ELOHIM) }

    @Test
    fun `a Greek entry`() = shoot("entry_greek") { selectEntry(AGAPE) }

    /** Two entries opened, so Back has somewhere to go. */
    @Test
    fun `history, with Back available`() = shoot("history") {
        selectEntry(ELOHIM)
        selectEntry(AGAPE)
    }

    @Test
    fun `stepped back through the history`() = shoot("history_back") {
        selectEntry(ELOHIM)
        selectEntry(AGAPE)
        dictButton(DictionaryLabel.BACK).performClick()
        waitForIdle()
    }

    @Test
    fun `with nowhere to schedule it and nothing to go live on`() =
        shoot("no_actions", withOnAddToSchedule = false, withOnGoLive = false) { selectEntry(AGAPE) }

    // ── In Scripture ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an entry with tagged verses`() = shoot(
        "in_scripture",
        verses = mapOf(AGAPE.number to LOVE_VERSES),
        getVerseText = { _, chapter, verse -> "Verse text for chapter $chapter verse $verse" },
        getBookName = { id -> BOOK_NAMES[id] },
    ) { selectEntry(AGAPE) }

    /** No Bible loaded, so a row can name neither the book nor the words of the verse. */
    @Test
    fun `tagged verses with no Bible to read them from`() = shoot(
        "in_scripture_no_bible",
        verses = mapOf(AGAPE.number to LOVE_VERSES),
    ) { selectEntry(AGAPE) }

    /** More verses than a page holds — the section pages, and says how many are left. */
    @Test
    fun `more tagged verses than one page`() = shoot(
        "in_scripture_paged",
        verses = mapOf(AGAPE.number to MANY_VERSES),
        getVerseText = { _, chapter, verse -> "Verse text for chapter $chapter verse $verse" },
        getBookName = { id -> BOOK_NAMES[id] },
    ) { selectEntry(AGAPE) }

    // Not shot: an entry with no tagged verses. That is what `entry_hebrew` already is — the corpus
    // has no interlinear data unless a test passes some — and the two images are identical.

    // ── The two scripture filters ───────────────────────────────────────────────────────────────
    // There are two, and they are not the same control: one narrows the *entry list* to words tagged
    // in a passage, the other narrows the *verse cards* of the entry already open. Both are always on
    // screen, so what is worth shooting is each one's menu open and each one applied — the closed row
    // looks the same whether or not any interlinear data exists behind it.

    @Test
    fun `the entry-list book menu`() = shoot(
        "list_filter_book_menu",
        booksWithGreekData = GREEK_BOOKS,
        booksWithHebrewData = HEBREW_BOOKS,
        chaptersForBook = CHAPTERS,
        getBookName = { id -> BOOK_NAMES[id] },
        rootIndex = 1,
    ) { openFilter(0) }

    @Test
    fun `the entry-list chapter menu, once a book is chosen`() = shoot(
        "list_filter_chapter_menu",
        booksWithGreekData = GREEK_BOOKS,
        booksWithHebrewData = HEBREW_BOOKS,
        chaptersForBook = CHAPTERS,
        versesInChapter = VERSES,
        strongsForBookChapter = mapOf(43 to setOf(AGAPE.number)),
        getBookName = { id -> BOOK_NAMES[id] },
        rootIndex = 1,
    ) {
        openFilter(0)
        chooseOption("John")
        openFilter(1)
    }

    /** A book chosen: only the words tagged in it are still listed, and a chapter filter appears. */
    @Test
    fun `the entry list narrowed to one book`() = shoot(
        "list_filter_applied",
        booksWithGreekData = GREEK_BOOKS,
        booksWithHebrewData = HEBREW_BOOKS,
        chaptersForBook = CHAPTERS,
        versesInChapter = VERSES,
        strongsForBookChapter = mapOf(43 to setOf(AGAPE.number)),
        getBookName = { id -> BOOK_NAMES[id] },
    ) {
        openFilter(0)
        chooseOption("John")
    }

    /** The open entry's own verse cards, filtered by book — a different control from the list's. */
    @Test
    fun `the In Scripture card filter menu`() = shoot(
        "in_scripture_filter_menu",
        verses = mapOf(AGAPE.number to LOVE_VERSES),
        getVerseText = { _, chapter, verse -> "Verse text for chapter $chapter verse $verse" },
        getBookName = { id -> BOOK_NAMES[id] },
        rootIndex = 1,
    ) {
        selectEntry(AGAPE)
        openFilter(lastFilterIndex())
    }

    @Test
    fun `the verse cards narrowed to one book`() = shoot(
        "in_scripture_filter_applied",
        verses = mapOf(AGAPE.number to LOVE_VERSES),
        getVerseText = { _, chapter, verse -> "Verse text for chapter $chapter verse $verse" },
        getBookName = { id -> BOOK_NAMES[id] },
    ) {
        selectEntry(AGAPE)
        openFilter(lastFilterIndex())
        chooseOption("1 Corinthians")
    }

    // ── Driving the filters ─────────────────────────────────────────────────────────────────────

    /**
     * Opens the nth filter dropdown, left to right.
     *
     * They all carry the same "BOOK"/"CHAPTER" captions and the entry list's sit left of the detail
     * pane's, so position is what tells them apart — as it does for the operator.
     */
    private fun ComposeUiTest.openFilter(index: Int) {
        val nodes = onAllNodes(isFilter).fetchSemanticsNodes(atLeastOneRootRequired = false)
        val order = nodes.indices.sortedBy { nodes[it].boundsInRoot.left }
        onAllNodes(isFilter)[order[index]].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.lastFilterIndex(): Int =
        onAllNodes(isFilter).fetchSemanticsNodes(atLeastOneRootRequired = false).size - 1

    /** Picks an option out of whichever menu is open. */
    private fun ComposeUiTest.chooseOption(label: String) {
        onAllNodesWithText(label)[0].performClick()
        waitForIdle()
    }

    // ── Panel widths ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a narrow panel`() = shoot("narrow_panel", width = 620.dp) { selectEntry(AGAPE) }

    private companion object {
        const val SECTION = "dictionaryTab"

        val ELOHIM: StrongsEntry = DictionaryFixture.elohim
        val AGAPE: StrongsEntry = DictionaryFixture.agape

        val BOOK_NAMES = mapOf(1 to "Genesis", 43 to "John", 46 to "1 Corinthians", 62 to "1 John")

        val GREEK_BOOKS = listOf(43, 46, 62)
        val HEBREW_BOOKS = listOf(1)
        val CHAPTERS = mapOf(43 to listOf(1, 3, 15), 46 to listOf(13), 62 to listOf(4), 1 to listOf(1))
        val VERSES = mapOf((43 to 15) to listOf(9, 12, 13), (43 to 3) to listOf(16))

        /** A filter dropdown: the tab's only controls carrying a caption and a value in one node. */
        val isFilter = hasText("BOOK", substring = true) or hasText("CHAPTER", substring = true)

        private fun verse(book: Int, chapter: Int, verseNumber: Int) = InterlinearVerse(
            ref = "%03d%03d%03d".format(book, chapter, verseNumber),
            words = listOf(
                InterlinearWord(text = "ἡ", strongsNumber = "G3588"),
                InterlinearWord(text = "ἀγάπη", strongsNumber = AGAPE.number),
                InterlinearWord(text = "τοῦ", strongsNumber = "G3588"),
                InterlinearWord(text = "Θεοῦ", strongsNumber = "G2316"),
            ),
        )

        val LOVE_VERSES = listOf(verse(43, 15, 13), verse(46, 13, 4), verse(62, 4, 8))

        /** Enough to overflow the first page, so "Show N more" appears with a real number on it. */
        val MANY_VERSES = (1..14).map { verse(46, 13, it) }
    }
}
