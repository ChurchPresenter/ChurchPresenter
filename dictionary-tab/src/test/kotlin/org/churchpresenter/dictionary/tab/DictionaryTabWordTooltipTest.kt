@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dictionary.tab

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import org.churchpresenter.dictionary.InterlinearVerse
import org.churchpresenter.dictionary.InterlinearWord
import org.churchpresenter.dictionary.StrongsEntry
import org.churchpresenter.dictionary.DictionaryFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The hover tooltip on an interlinear word chip — how an operator reads what a word in the verse
 * means without leaving the entry they are already on.
 *
 * **A `TooltipArea` body does compose under `runComposeUiTest`**, which is what makes this suite
 * possible: hovering the chip with `performMouseInput { moveTo(center) }` draws it. Roughly two
 * dozen files here have a `TooltipArea` and none of their bodies had ever been driven.
 * `CanvasTabTestSupport` had in fact recorded the behaviour from the other side — a tooltip
 * lingering after a click and swallowing the next one — which is the same fact, written down as an
 * obstacle.
 *
 * **Every assertion is differential — a count before the hover against a count after.** The tooltip
 * repeats the number, word and transliteration that the detail pane is *already* showing for the
 * selected entry, so `assertExists` on any of them passes just as well with no tooltip at all. Only
 * the count going up is evidence that this control drew anything.
 */
class DictionaryTabWordTooltipTest {

    private val agape = DictionaryFixture.agape

    /** A Greek entry whose definition is past the tooltip's 200-character cut. */
    private val verbose = StrongsEntry(
        number = "G9001",
        word = "μακρολογία",
        transliteration = "makrologia",
        pronunciation = "mak-rol-og-ee'-ah",
        definition = "an account at length, " + "spun out with many words ".repeat(12),
        kjvUsage = "long speech",
    )

    private fun verseOf(entry: StrongsEntry, wordText: String) = InterlinearVerse(
        ref = "043003016",
        words = listOf(InterlinearWord(text = wordText, strongsNumber = entry.number)),
    )

    /** Selects [entry] in the list, which is what draws its "In Scripture" chips. */
    private fun ComposeUiTest.open(entry: StrongsEntry) {
        onAllNodesWithText(entry.number)[0].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.countOf(text: String, substring: Boolean = false) =
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes(false).size

    /** Hovers the chip labelled [wordText] and settles the tooltip's own delay. */
    private fun ComposeUiTest.hoverChip(wordText: String) {
        onAllNodesWithText(wordText)[0].performMouseInput { moveTo(center) }
        waitForIdle()
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
    }

    @Test
    fun `hovering a word chip shows what it means`() {
        dictionaryTab(verses = mapOf(agape.number to listOf(verseOf(agape, "αγαπη")))) { _, _ ->
            open(agape)
            val before = countOf(agape.number)

            hoverChip("αγαπη")

            assertEquals(
                before + 1, countOf(agape.number),
                "the tooltip adds a second place the entry's number is shown",
            )
            assertTrue(
                countOf(agape.transliteration) > 0,
                "and it names the transliteration, which is how the word is pronounced",
            )
        }
    }

    @Test
    fun `the tooltip carries the definition, not just the label`() {
        // The number and transliteration alone would not tell an operator what the word means, which
        // is the only reason to hover it mid-service.
        dictionaryTab(verses = mapOf(agape.number to listOf(verseOf(agape, "αγαπη")))) { _, _ ->
            open(agape)
            val before = countOf(agape.definition)

            hoverChip("αγαπη")

            assertEquals(before + 1, countOf(agape.definition))
        }
    }

    @Test
    fun `a long definition is cut short with an ellipsis`() {
        // Untruncated, a wordy entry's tooltip grows down the window and covers the verse the
        // operator is reading — the thing they hovered it from.
        dictionaryTab(
            verses = mapOf(verbose.number to listOf(verseOf(verbose, "μακρολογια"))),
            extraEntries = listOf(verbose),
        ) { _, _ ->
            open(verbose)
            // The detail pane already shows this entry's definition in full, so the count of the
            // whole text starts at one and the assertion below has to be that the hover does not
            // add a second — not that it is absent.
            val fullBefore = countOf(verbose.definition)

            hoverChip("μακρολογια")

            val expected = verbose.definition.take(200) + "…"
            assertTrue(countOf(expected) > 0, "the tooltip shows exactly 200 characters and an ellipsis")
            assertEquals(
                fullBefore, countOf(verbose.definition),
                "and never the whole thing — the detail pane is where that belongs",
            )
        }
    }

    @Test
    fun `a definition that fits is shown whole, with no ellipsis`() {
        // The positive twin of the test above: without it, a truncation that fired on every
        // definition would still pass there.
        dictionaryTab(verses = mapOf(agape.number to listOf(verseOf(agape, "αγαπη")))) { _, _ ->
            open(agape)

            hoverChip("αγαπη")

            assertEquals(0, countOf("${agape.definition}…"), "nothing to cut, so nothing is cut")
        }
    }

    @Test
    fun `a word the dictionary does not know gets no tooltip`() {
        // The chip is still drawn — the verse has to render whether or not every word is tagged with
        // a number this build has an entry for. It just has nothing to say about it.
        val unknown = "G9999"
        dictionaryTab(
            verses = mapOf(
                agape.number to listOf(
                    InterlinearVerse(
                        ref = "043003016",
                        words = listOf(InterlinearWord(text = "ξενον", strongsNumber = unknown)),
                    )
                )
            )
        ) { _, _ ->
            open(agape)
            assertTrue(countOf("ξενον") > 0, "the untagged word is still part of the verse")
            val before = countOf(unknown)

            hoverChip("ξενον")

            assertEquals(before, countOf(unknown), "no entry means no tooltip to draw")
        }
    }
}
