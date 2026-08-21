@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Crossword tab — the bundled puzzle game.
 *
 * The puzzles are real: two `.xwp` files ship with the app and the tab decodes them itself, so
 * these tests load what a user would actually see rather than a fixture that could drift from the
 * format the decoder expects. That also means the assertions stay away from any particular puzzle's
 * words — what is pinned is the shell around them: which level opens, that both directions of clues
 * are listed, and that checking an unfinished grid says so without advancing anyone.
 *
 * Progress saving is deliberately not asserted. The tab debounces it with a 500ms `delay` before
 * calling `onSettingsChange`, so a test proving a keystroke was persisted would be buying that
 * assertion with half a second of wall clock — a duration rather than work. The serialisation
 * either side of it (`serializeInput`/`deserializeInput`) is private to the file; if it ever needs
 * cover it should be widened to `internal` and tested directly rather than through a wait.
 */
class CrosswordTabTest {

    /** Composes the tab over [settings], feeding changes back as the app does, then runs [block]. */
    @OptIn(ExperimentalTestApi::class)
    private fun crosswordTab(
        initial: AppSettings = AppSettings(),
        block: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            var settings by remember { mutableStateOf(initial) }
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
        block()
    }

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
}
