@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.app.churchpresenter.viewmodel.DictionaryFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two book/chapter filters — one over the entry *list*, gated on whatever books have tagged
 * interlinear data at all; one over the "In Scripture" *card* section, gated on the selected entry's
 * own tagged verses — plus the EN/RU language toggle and the tab's behaviour with no schedule or
 * output to send to.
 *
 * The two filter rows share the "BOOK"/"CHAPTER" label (`DropdownSelector` uppercases it), so a test
 * that needs one has to say which: the entry-list's sits leftmost (over the list pane), the card's
 * rightmost (over the detail pane).
 *
 * See `DictionaryTabTestSupport.kt` for the harness.
 */
class DictionaryTabFiltersTest {

    private val agape = DictionaryFixture.agape

    /** The rightmost node with [label] — the card filter, since the entry-list's sits to its left. */
    private fun ComposeUiTest.rightmost(label: String) {
        val nodes = onAllNodesWithText(label, substring = true).fetchSemanticsNodes()
        val target = nodes.indices.maxByOrNull { nodes[it].boundsInRoot.left }
            ?: error("no \"$label\" control is on screen")
        onAllNodesWithText(label, substring = true)[target].performClick()
        waitForIdle()
    }

    // ── The "In Scripture" card filter ─────────────────────────────────────────

    @Test
    fun `the card filter narrows In Scripture to one book`() = dictionaryTab(
        verses = mapOf(
            agape.number to listOf(
                DictionaryFixture.verse(43, 3, 16, agape.number),
                DictionaryFixture.verse(40, 5, 3, agape.number),
            ),
        ),
        getBookName = { if (it == 43) "John" else "Matthew" },
    ) { _, _ ->
        selectEntry(agape)
        assertTrue(showsContainingText("John 3:16"))
        assertTrue(showsContainingText("Matthew 5:3"))

        rightmost("BOOK")
        onNodeWithText("John").performClick()
        waitForIdle()

        assertTrue(showsContainingText("John 3:16"))
        assertFalse(showsContainingText("Matthew 5:3"), "the other book must drop out")
    }

    // Not tested: the card filter's chapter dropdown once a book is chosen. Selecting the book
    // works (see the test below) and re-renders the chapter `DropdownSelector` for the first time,
    // but a click that opens *that* dropdown does not expand it under synthetic input — unlike
    // every other `DropdownSelector` click in this suite, which all work the same way. Not
    // pursued further; if this needs to be reached, it likely wants a `ComposeUiTest`-level repro
    // isolated from the rest of this fixture first.

    @Test
    fun `with only one book tagged the card filter is not offered at all`() = dictionaryTab(
        verses = mapOf(agape.number to listOf(DictionaryFixture.verse(43, 3, 16, agape.number))),
        getBookName = { "John" },
    ) { _, _ ->
        selectEntry(agape)

        // Only the entry-list's own filter remains — nothing worth choosing between for one book.
        assertEquals(
            1,
            onAllNodesWithText("BOOK", substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false).size,
        )
    }

    // ── The entry-list filter ───────────────────────────────────────────────────

    @Test
    fun `the entry-list filter narrows which entries are listed to those tagged in one book`() = dictionaryTab(
        booksWithGreekData = listOf(43),
        booksWithHebrewData = listOf(40),
        strongsForBookChapter = mapOf(43 to setOf(DictionaryFixture.agape.number)),
        getBookName = { if (it == 43) "John" else "Matthew" },
    ) { _, _ ->
        assertTrue(listShows(DictionaryFixture.agape), "every entry is listed to begin with")
        assertTrue(listShows(DictionaryFixture.elohim))

        val nodes = onAllNodesWithText("BOOK", substring = true).fetchSemanticsNodes()
        val leftmost = nodes.indices.minByOrNull { nodes[it].boundsInRoot.left }
            ?: error("no \"Book\" control is on screen")
        onAllNodesWithText("BOOK", substring = true)[leftmost].performClick()
        waitForIdle()
        onNodeWithText("John").performClick()
        waitForIdle()

        assertTrue(listShows(DictionaryFixture.agape), "agape is tagged in John")
        assertFalse(listShows(DictionaryFixture.elohim), "elohim is not, so it must drop out")
    }

    @Test
    fun `the entry-list filter is not offered before any book has tagged data`() = dictionaryTab { _, _ ->
        assertEquals(
            1,
            onAllNodesWithText("BOOK", substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false).size,
            "with nothing tagged, only the entry-list's own filter (fixed to All) is on screen",
        )
    }

    // ── Language ─────────────────────────────────────────────────────────────────

    /** The RU dictionary is re-read via `Res.readBytes` on `Dispatchers.IO` — a real thread hop, so
     *  a bare `waitForIdle()` can race it. Wait for the reloaded definition itself instead. */
    private fun ComposeUiTest.waitForDefinition(text: String) {
        waitUntil("the definition to read \"$text\"", timeoutMillis = 5_000) {
            showsContainingText(text)
        }
    }

    @Test
    fun `toggling the language switches the detail pane to Russian`() = dictionaryTab { vm, _ ->
        selectEntry(agape)
        assertTrue(showsContainingText(agape.definition))
        assertEquals("en", vm.dictLanguage)

        onNodeWithText("EN").performClick()
        val ruDefinition = DictionaryFixture.greekEntriesRu.first { it.number == agape.number }.definition
        waitForDefinition(ruDefinition)

        assertEquals("ru", vm.dictLanguage)
        assertTrue(showsContainingText("RU"), "the toggle now offers to switch back")
    }

    @Test
    fun `toggling back to English restores the English definition`() = dictionaryTab { vm, _ ->
        selectEntry(agape)
        onNodeWithText("EN").performClick()
        val ruDefinition = DictionaryFixture.greekEntriesRu.first { it.number == agape.number }.definition
        waitForDefinition(ruDefinition)

        onNodeWithText("RU").performClick()
        waitForDefinition(agape.definition)

        assertEquals("en", vm.dictLanguage)
    }

    // ── No schedule, no output ───────────────────────────────────────────────────

    @Test
    fun `with nowhere to add to, the schedule action is not offered at all`() =
        dictionaryTab(withOnAddToSchedule = false) { _, _ ->
            selectEntry(agape)
            assertFalse(hasDictButton(DictionaryLabel.ADD_TO_SCHEDULE))
        }

    @Test
    fun `with no output wired, Go Live is not offered at all`() =
        dictionaryTab(withOnGoLive = false) { _, _ ->
            selectEntry(agape)
            assertFalse(hasDictButton(DictionaryLabel.GO_LIVE))
        }

    @Test
    fun `neither action needs the other to be present`() =
        dictionaryTab(withOnAddToSchedule = false, withOnGoLive = true) { _, _ ->
            selectEntry(agape)
            assertFalse(hasDictButton(DictionaryLabel.ADD_TO_SCHEDULE))
            assertTrue(hasDictButton(DictionaryLabel.GO_LIVE))
        }
}
