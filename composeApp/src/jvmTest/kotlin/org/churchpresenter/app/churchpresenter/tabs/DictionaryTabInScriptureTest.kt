@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.dictionary.InterlinearVerse
import org.churchpresenter.dictionary.InterlinearWord
import org.churchpresenter.dictionary.DictionaryFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The detail pane's "In Scripture" section — every verse where the selected word appears in the
 * original, and the two ways out of it: tapping a reference to open that verse in the Bible tab, and
 * tapping a word to look it up.
 *
 * The section had gone untested because the harness stubbed the interlinear repository to return
 * nothing for every entry, which is the right default (the real one reads 4–8 MB of JSON) but meant
 * none of this drew. The harness takes verses now.
 *
 * The rules worth pinning: the verse list is paged, so a word appearing in hundreds of verses must
 * not try to draw them all at once, and "Show N more" has to name the number actually left. A row
 * falls back to "Book <id>" when no book-name resolver is wired, rather than rendering blank. And the
 * two tap targets report the reference and the Strong's number they were drawn from — a row that
 * reports the wrong verse sends the operator to the wrong passage.
 */
class DictionaryTabInScriptureTest {

    private val agape = DictionaryFixture.agape

    /** One tagged verse, with [words] in the original — the highlighted one plus its neighbours. */
    private fun verse(
        book: Int,
        chapter: Int,
        verseNumber: Int,
        words: List<InterlinearWord> = listOf(InterlinearWord(text = "ἀγάπη", strongsNumber = agape.number)),
    ) = InterlinearVerse(ref = "%03d%03d%03d".format(book, chapter, verseNumber), words = words)

    /**
     * Clicks "Show N more".
     *
     * Scrolled to first: with a full page of verse cards above it the button is off-screen, and an
     * off-screen node is still in the tree — so it is "found" and the click is simply swallowed.
     */
    private fun ComposeUiTest.showMore() {
        onNodeWithText("Show ", substring = true).performScrollTo()
        onNodeWithText("Show ", substring = true).performClick()
        waitForIdle()
    }

    private fun openAgape(test: ComposeUiTest) = with(test) {
        onAllNodesWithText(agape.number)[0].performClick()
        waitForIdle()
    }

    // ── Whether the section draws ───────────────────────────────────────────────────────────────

    @Test
    fun `with no tagged verses the section says so`() {
        dictionaryTab { _, _ ->
            openAgape(this)

            assertTrue(showsExactly(DictionaryLabel.NO_TAGGED_VERSES), renderedText().toString())
        }
    }

    @Test
    fun `a tagged verse is listed by its reference`() {
        dictionaryTab(
            verses = mapOf(agape.number to listOf(verse(43, 3, 16))),
            getBookName = { if (it == 43) "John" else null },
        ) { _, _ ->
            openAgape(this)

            assertTrue(showsContainingText("John 3:16"), renderedText().toString())
            assertFalse(showsExactly(DictionaryLabel.NO_TAGGED_VERSES))
        }
    }

    @Test
    fun `without a book-name resolver a row falls back to the book id`() {
        dictionaryTab(verses = mapOf(agape.number to listOf(verse(43, 3, 16)))) { _, _ ->
            openAgape(this)

            // Better a numbered book than a blank reference.
            assertTrue(showsContainingText("Book 43 3:16"), renderedText().toString())
        }
    }

    @Test
    fun `the verse text is shown when a bible is loaded`() {
        dictionaryTab(
            verses = mapOf(agape.number to listOf(verse(43, 3, 16))),
            getBookName = { "John" },
            getVerseText = { _, _, _ -> "For God so loved the world" },
        ) { _, _ ->
            openAgape(this)

            assertTrue(showsContainingText("For God so loved the world"), renderedText().toString())
        }
    }

    @Test
    fun `with no bible loaded the row still lists the reference`() {
        dictionaryTab(
            verses = mapOf(agape.number to listOf(verse(43, 3, 16))),
            getBookName = { "John" },
            getVerseText = { _, _, _ -> null },
        ) { _, _ ->
            openAgape(this)

            assertTrue(showsContainingText("John 3:16"), renderedText().toString())
        }
    }

    @Test
    fun `the count of tagged verses is reported`() {
        dictionaryTab(
            verses = mapOf(agape.number to listOf(verse(43, 3, 16), verse(43, 3, 17), verse(40, 5, 3))),
            getBookName = { "John" },
        ) { _, _ ->
            openAgape(this)

            assertTrue(showsContainingText("3 verses"), renderedText().toString())
        }
    }

    // ── Paging ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a word in more verses than one page is not drawn all at once`() {
        // 50 to a page, so 60 verses means 50 drawn and 10 held back.
        val many = (1..60).map { verse(43, 3, it) }

        dictionaryTab(verses = mapOf(agape.number to many), getBookName = { "John" }) { vm, _ ->
            openAgape(this)

            assertEquals(50, vm.interlinearDisplayLimit)
            assertTrue(showsContainingText("Show 10 more"), renderedText().toString())
            assertTrue(showsContainingText("John 3:50"), "the first page should reach verse 50")
            assertFalse(showsContainingText("John 3:51"), "and stop there")
        }
    }

    @Test
    fun `show more draws the rest and stops offering`() {
        val many = (1..60).map { verse(43, 3, it) }

        dictionaryTab(verses = mapOf(agape.number to many), getBookName = { "John" }) { vm, _ ->
            openAgape(this)

            showMore()

            assertEquals(100, vm.interlinearDisplayLimit)
            assertTrue(showsContainingText("John 3:60"), renderedText().toString())
            assertFalse(showsContainingText("Show "), "nothing left to show")
        }
    }

    @Test
    fun `exactly one page's worth offers nothing more`() {
        val exactly = (1..50).map { verse(43, 3, it) }

        dictionaryTab(verses = mapOf(agape.number to exactly), getBookName = { "John" }) { _, _ ->
            openAgape(this)

            assertFalse(showsContainingText("Show "), renderedText().toString())
        }
    }

    // ── The two tap targets ─────────────────────────────────────────────────────────────────────

    @Test
    fun `tapping a reference reports that verse`() {
        dictionaryTab(
            verses = mapOf(agape.number to listOf(verse(43, 3, 16))),
            getBookName = { "John" },
        ) { _, reports ->
            openAgape(this)

            onNodeWithText("John 3:16", substring = true).performClick()
            waitForIdle()

            assertEquals(listOf(Triple(43, 3, 16)), reports.verseClicks)
        }
    }

    @Test
    fun `tapping a neighbouring word reports its strongs number`() {
        dictionaryTab(
            verses = mapOf(
                agape.number to listOf(
                    verse(
                        43, 3, 16,
                        words = listOf(
                            InterlinearWord(text = "ἀγάπη", strongsNumber = agape.number),
                            InterlinearWord(text = "λόγος", strongsNumber = NEIGHBOUR_NUMBER),
                        ),
                    )
                )
            ),
            getBookName = { "John" },
        ) { _, reports ->
            openAgape(this)

            // Deliberately a word no dictionary entry in the fixture spells: "χάρις" would also
            // match the charis row in the list and the lookup would be ambiguous.
            onNodeWithText("λόγος").performClick()
            waitForIdle()

            assertEquals(
                listOf(NEIGHBOUR_NUMBER),
                reports.wordClicks,
                "the chip has to report the word it was drawn from",
            )
        }
    }

    private companion object {
        /** A Strong's number the fixture dictionary does not contain, for a neighbouring word. */
        const val NEIGHBOUR_NUMBER = "G3056"
    }

    @Test
    fun `switching entries resets the page back to the first`() {
        val many = (1..60).map { verse(43, 3, it) }

        dictionaryTab(verses = mapOf(agape.number to many), getBookName = { "John" }) { vm, _ ->
            openAgape(this)
            showMore()
            assertEquals(100, vm.interlinearDisplayLimit)

            onAllNodesWithText(DictionaryFixture.charis.number)[0].performClick()
            waitForIdle()

            assertEquals(50, vm.interlinearDisplayLimit, "a new entry starts at page one")
        }
    }
}
