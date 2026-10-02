@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.crosswordtab

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.sharedui.testing.renderedText
import org.churchpresenter.sharedui.testing.showsExactly
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import kotlinx.coroutines.runBlocking
import kotlin.test.assertNotNull
import org.churchpresenter.crosswordtab.data.RenderedCrossword
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

/**
 * The Crossword tab — the bundled puzzle game.
 *
 * The puzzles are real: two `.xwp` files ship with the app and the tab decodes them itself, so
 * these tests load what a user would actually see rather than a fixture that could drift from the
 * format the decoder expects. That also means the assertions stay away from any particular puzzle's
 * words — what is pinned is the shell around them: which level opens, that both directions of clues
 * are listed, and that checking an unfinished grid says so without advancing anyone.
 *
 * Progress saving is asserted on the test clock: the tab debounces it with a 500ms `delay`, and
 * the v2 test dispatcher runs that delay when the test advances `mainClock`, so the assertion costs
 * no wall-clock time.
 */
class CrosswordTabTest {

    /** Composes the tab over [settings], feeding changes back as the app does, then runs [block]. */
    @OptIn(ExperimentalTestApi::class)
    private fun crosswordTab(
        initial: AppSettings = AppSettings(),
        block: ComposeUiTest.() -> Unit,
    ) = crosswordTabWithSettings(initial) { _ -> block() }

    /** As [crosswordTab], also handing [block] the settings as the tab last wrote them. */
    @OptIn(ExperimentalTestApi::class)
    private fun crosswordTabWithSettings(
        initial: AppSettings = AppSettings(),
        block: ComposeUiTest.(settings: () -> AppSettings) -> Unit,
    ) = runComposeUiTest {
        var settings by mutableStateOf(initial)
        setContent {
            MaterialTheme {
                CrosswordTab(
                    appSettings = settings,
                    onSettingsChange = { transform -> settings = transform(settings) },
                )
            }
        }
        // The puzzles are read from resources on first composition; wait for the load to finish
        // rather than for a duration.
        waitUntil("the puzzles to load") { !showsExactly("Loading puzzles…") }
        block { settings }
    }

    /** Level [level] as the tab decodes it, for the answers a test fills in. */
    private fun level(level: Int): RenderedCrossword = assertNotNull(runBlocking { loadLevelFile(level) })

    /** Every open square of [puzzle] with its answer. */
    private fun answers(puzzle: RenderedCrossword): Map<Pair<Int, Int>, Char> =
        puzzle.grid.flatMapIndexed { r, row ->
            row.mapIndexedNotNull { c, cell -> cell.answer?.let { (r to c) to it } }
        }.toMap()

    /** What the square at [r], [c] shows: its clue number, if any, then the letter typed in it. */
    private fun ComposeUiTest.square(r: Int, c: Int): String =
        onNodeWithTag(crosswordCellTag(r, c)).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }

    /** The first open square of level 0, top-left first. */
    private fun firstOpenSquare(): Pair<Int, Int> = answers(level(0)).keys.first()

    @Test
    fun `the bundled puzzles load and the first level opens`() = crosswordTab {
        assertFalse(showsExactly("No crossword puzzles available yet."), "puzzles shipped")
        assertTrue(showsExactly("Level 0"), "opens on the first level: ${renderedText().take(8)}")
    }

    @Test
    fun `both directions of clues are listed`() = crosswordTab {
        // A crossword with only one direction of clues is unsolvable.
        assertTrue(showsExactly("Across"))
        assertTrue(showsExactly("Down"))
    }

    @Test
    fun `the answer check is offered`() = crosswordTab {
        assertTrue(showsExactly("Check Answers"), "got ${renderedText().take(10)}")
    }

    @Test
    fun `checking an empty grid says it is wrong rather than passing it`() = crosswordTab {
        onNodeWithText("Check Answers").performClick()
        waitForIdle()

        assertTrue(showsExactly("Wrong — try again"), "got ${renderedText().take(12)}")
        assertFalse(showsExactly("Correct! Level complete!"), "an empty grid is not a solved one")
    }

    @Test
    fun `a player returns to the level they had reached`() =
        crosswordTab(initial = AppSettings(crosswordUnlockedLevel = 1)) {
            // Progress is per-level, so reopening the tab has to land where the player left off
            // rather than sending them back to the beginning. (The label is the raw index.)
            assertTrue(showsExactly("Level 1"), "got ${renderedText().take(8)}")
        }

    @Test
    fun `an unlocked level beyond the last puzzle falls back to the last one`() =
        crosswordTab(initial = AppSettings(crosswordUnlockedLevel = 99)) {
            // Someone who finished every level must not open onto a missing puzzle when more
            // levels ship later — or, worse, an index past the end.
            assertTrue(showsExactly("Level 1"), "the last shipped level: ${renderedText().take(8)}")
        }

    @Test
    fun `saved letters are restored into the grid`() = crosswordTab(
        initial = AppSettings(crosswordProgress = mapOf(0 to "3,3:Y")),
    ) {
        // The grid draws each filled cell as its letter, so a restored answer shows up as one.
        // (3,3) is an open square in level 0 — a blocked one would render nothing whatever was
        // stored against it, which would make this pass for the wrong reason.
        assertTrue(
            showsExactly("Y"),
            "the saved letter is back in the grid: ${renderedText().take(20)}",
        )
    }

    @Test
    fun `a corrupt progress string is ignored rather than crashing the tab`() = crosswordTab(
        // Every entry is malformed a different way: no colon, a non-numeric column, an empty pair.
        // The coordinates name (3,3), an open square, so a parser that let any of them through
        // would put a visible letter on the grid.
        initial = AppSettings(crosswordProgress = mapOf(0 to "3,3Y|3,x:Y|:|")),
    ) {
        // Settings are hand-editable JSON on disk, so the parser has to survive nonsense.
        assertTrue(showsExactly("Level 0"), "the tab still opened")
        assertEquals(
            0,
            renderedText().count { it == "Y" },
            "and nothing was placed from the unparseable entries: ${renderedText().take(20)}",
        )
    }

    @Test
    fun `a letter typed into a square shows there and is saved once typing settles`() =
        crosswordTabWithSettings { settings ->
            val (r, c) = firstOpenSquare()
            onNodeWithTag(crosswordCellTag(r, c)).performClick()
            onNodeWithTag(crosswordCellTag(r, c)).performKeyInput { pressKey(Key.Q) }
            waitForIdle()
            assertTrue(square(r, c).endsWith("Q"), "the letter is drawn, upper-cased: ${square(r, c)}")

            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            assertEquals("$r,$c:Q", settings().crosswordProgress[0], "the square is saved against level 0")
        }

    @Test
    fun `backspace clears a square and a digit is ignored`() = crosswordTabWithSettings(
        initial = AppSettings(crosswordProgress = mapOf(0 to firstOpenSquare().let { (r, c) -> "$r,$c:Q" })),
    ) { settings ->
        val (r, c) = firstOpenSquare()
        onNodeWithTag(crosswordCellTag(r, c)).performClick()
        onNodeWithTag(crosswordCellTag(r, c)).performKeyInput { pressKey(Key.Five) }
        waitForIdle()
        assertTrue(square(r, c).endsWith("Q"), "a digit is not a crossword letter: ${square(r, c)}")

        onNodeWithTag(crosswordCellTag(r, c)).performKeyInput { pressKey(Key.Backspace) }
        waitForIdle()
        assertFalse(square(r, c).contains("Q"), "backspace empties the square: ${square(r, c)}")
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        assertEquals("", settings().crosswordProgress[0], "and the emptied grid is what is saved")
    }

    @Test
    fun `a solved level unlocks the next and moves on to it`() {
        val solved = serializeInput(answers(level(0)))
        crosswordTabWithSettings(initial = AppSettings(crosswordProgress = mapOf(0 to solved))) { settings ->
            onNodeWithText("Check Answers").performClick()
            waitForIdle()

            assertEquals(1, settings().crosswordUnlockedLevel, "solving level 0 opens level 1")
            assertTrue(showsExactly("Level 1"), "and the tab moves on: ${renderedText().take(8)}")
        }
    }

    @Test
    fun `the level buttons step between the levels already unlocked`() =
        crosswordTab(initial = AppSettings(crosswordUnlockedLevel = 1)) {
            onNodeWithContentDescription("Previous level").performClick()
            waitForIdle()
            assertTrue(showsExactly("Level 0"), "back one: ${renderedText().take(8)}")

            onNodeWithContentDescription("Next level").performClick()
            waitForIdle()
            assertTrue(showsExactly("Level 1"), "and forward again: ${renderedText().take(8)}")
        }
}
