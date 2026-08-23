@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dictionary.tab

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.dictionary.DictionaryFixture
import org.churchpresenter.ui.showsContainingText
import org.churchpresenter.ui.showsExactly
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The tab drawn with nothing wired to it — no settings, no schedule, no Bible, no lookup.
 *
 * Every callback the tab takes is optional, and each missing one changes what is drawn: no
 * `onGoLive`/`onAddToSchedule` means no action buttons, no `onVerseClick` means a verse reference is
 * text rather than a link, no `onWordClick` means the word chips are inert, and no `getEntry` means
 * the definition is a paragraph rather than a set of Strong's links. Those are the states a
 * read-only or preview placement of the tab is actually in.
 */
class DictionaryTabDefaultsTest {

    private val agape = DictionaryFixture.agape

    @Test
    fun `with nothing wired the entry list still loads and opens`() = bareDictionaryTab { _ ->
        assertTrue(showsContainingText(agape.number))

        onAllNodesWithText(agape.number)[0].performClick()
        waitForIdle()

        assertTrue(showsContainingText(agape.pronunciation), "the detail pane must open")
        assertTrue(showsContainingText(agape.definition))
    }

    @Test
    fun `without a schedule or output there are no action buttons`() = bareDictionaryTab { _ ->
        onAllNodesWithText(agape.number)[0].performClick()
        waitForIdle()

        assertFalse(showsExactly(DictionaryLabel.GO_LIVE), "nothing to go live to")
        assertFalse(showsExactly(DictionaryLabel.ADD_TO_SCHEDULE), "nothing to add to")
    }

    @Test
    fun `a tagged verse renders without a Bible, a book name or a way to open it`() = bareDictionaryTab(
        verses = mapOf(agape.number to listOf(DictionaryFixture.verse(43, 3, 16, agape.number))),
    ) { _ ->
        onAllNodesWithText(agape.number)[0].performClick()
        waitForIdle()

        assertTrue(showsContainingText(DictionaryLabel.IN_SCRIPTURE))
        // No getBookName, so the row names the book by its id rather than rendering blank.
        assertTrue(showsContainingText("Book 43"), "the row must fall back to the book id")
        assertFalse(hasDictButton(DictionaryLabel.GO_TO_VERSE), "no onVerseClick, so no way out of the row")
    }

    @Test
    fun `the original-language words are shown but inert`() = bareDictionaryTab(
        verses = mapOf(agape.number to listOf(DictionaryFixture.verse(43, 3, 16, agape.number))),
    ) { _ ->
        onAllNodesWithText(agape.number)[0].performClick()
        waitForIdle()

        // The chip is drawn — it is the word in the original — but with no onWordClick it is not a
        // link, so clicking it must not move the selection off the entry being read.
        assertTrue(showsContainingText("ἀγάπη"))
        onAllNodesWithText("ἀγάπη")[0].performClick()
        waitForIdle()
        assertTrue(showsContainingText(agape.pronunciation), "the open entry must not have changed")
    }
}
