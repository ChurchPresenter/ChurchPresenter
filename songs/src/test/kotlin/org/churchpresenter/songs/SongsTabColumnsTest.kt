@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which columns the song list shows, and the menu that turns them on and off.
 *
 * The library is a table an operator tailors to their songbook — number, title, songbook, tune, play
 * count, author, composer — and four of those are hidden by default. Two rules matter. A hidden
 * column must actually leave the table, not just lose its header, because a hidden column that still
 * takes width squeezes the titles the operator is reading. And **Title cannot be hidden**: the list
 * would become rows of numbers with nothing to identify them, which is why its menu item is disabled
 * while it is visible rather than merely ignored on click.
 *
 * Every toggle also has to reach settings, since a column layout the operator sets is expected to
 * survive a restart.
 */
class SongsTabColumnsTest {

    /** Nothing hidden, so a test can hide rather than unhide. */
    private val allShown = emptySet<String>()

    /** Opens the column menu from the header's Tune button. */
    private fun ComposeUiTest.openColumnMenu() {
        onNodeWithContentDescription(FILTER_COLUMNS).performClick()
        waitForIdle()
    }

    /**
     * Clicks a column's menu item.
     *
     * Matched as the last node with that label: the same word is a column *header* in the table
     * behind the popup, and the popup composes after it.
     */
    private fun ComposeUiTest.clickColumnItem(label: String) {
        val nodes = onAllNodesWithText(label)
        nodes[nodes.fetchSemanticsNodes().size - 1].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.headerCount(label: String) =
        onAllNodesWithText(label).fetchSemanticsNodes(atLeastOneRootRequired = false).size

    /**
     * Whether the table itself is drawing [label] as a column header, ignoring the open menu.
     *
     * The menu does not close when an item is clicked, so its own item keeps the label on screen —
     * a bare "is this text anywhere" check would say the column is still there. With the menu open a
     * shown column appears twice (header + item) and a hidden one once (item only).
     */
    private fun ComposeUiTest.tableShowsColumn(label: String) = headerCount(label) > 1

    // ── What the table shows ────────────────────────────────────────────────────────────────────

    @Test
    fun `the default layout hides the four optional columns`() {
        songsTab { _, _ ->
            assertTrue(shows(Col.TITLE), rendered().toString())
            assertTrue(shows(Col.NUMBER))
            assertFalse(shows(Col.TUNE), "tune is off by default")
            assertFalse(shows(Col.PLAY_COUNT))
            assertFalse(shows(Col.COMPOSER))
        }
    }

    @Test
    fun `a column turned on in settings appears`() {
        songsTab(hiddenCols = allShown) { _, _ ->
            assertTrue(shows(Col.TUNE), rendered().toString())
            assertTrue(shows(Col.PLAY_COUNT))
            assertTrue(shows(Col.COMPOSER))
            assertTrue(shows(Col.AUTHOR))
        }
    }

    @Test
    fun `an author column shows the authors`() {
        songsTab(hiddenCols = allShown) { _, _ ->
            // The column is only useful if the cells come with it.
            assertTrue(showsContaining("John Newton"), rendered().toString())
        }
    }

    // ── The menu ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the menu lists every column`() {
        songsTab(hiddenCols = allShown) { _, _ ->
            openColumnMenu()

            // Each label is now on screen twice: once as a header, once as a menu item.
            assertEquals(2, headerCount(Col.TUNE), rendered().toString())
            assertEquals(2, headerCount(Col.COMPOSER))
        }
    }

    @Test
    fun `hiding a column removes it from the table and saves the choice`() {
        songsTab(hiddenCols = allShown) { _, reports ->
            openColumnMenu()
            clickColumnItem(Col.TUNE)

            assertFalse(tableShowsColumn(Col.TUNE), "the column has to leave the table: ${rendered()}")
            assertEquals(
                setOf("tune"),
                reports.settingsAfterChange?.songHiddenCols,
                "and the choice has to survive a restart",
            )
        }
    }

    @Test
    fun `unhiding a column brings it back`() {
        songsTab { _, reports ->
            // "tune" starts hidden by default.
            assertFalse(shows(Col.TUNE))

            openColumnMenu()
            clickColumnItem(Col.TUNE)

            assertTrue(tableShowsColumn(Col.TUNE), rendered().toString())
            assertFalse(
                reports.settingsAfterChange?.songHiddenCols?.contains("tune") ?: true,
                "it should no longer be in the hidden set",
            )
        }
    }

    @Test
    fun `hiding two columns keeps both out`() {
        songsTab(hiddenCols = allShown) { _, reports ->
            openColumnMenu()
            // Not reopened between the two: the menu stays open after an item is clicked, and
            // clicking the Tune button again would toggle it shut.
            clickColumnItem(Col.TUNE)
            clickColumnItem(Col.COMPOSER)

            assertFalse(tableShowsColumn(Col.TUNE), rendered().toString())
            assertFalse(tableShowsColumn(Col.COMPOSER))
            assertEquals(setOf("tune", "composer"), reports.settingsAfterChange?.songHiddenCols)
        }
    }

    // ── Title is protected ──────────────────────────────────────────────────────────────────────

    @Test
    fun `the title column cannot be hidden`() {
        songsTab(hiddenCols = allShown) { _, reports ->
            openColumnMenu()

            // Disabled rather than silently ignored, so the operator can see why nothing happened.
            onAllNodes(hasText(Col.TITLE))[headerCount(Col.TITLE) - 1].assertIsNotEnabled()

            clickColumnItem(Col.TITLE)

            assertTrue(tableShowsColumn(Col.TITLE), "the list would be unidentifiable rows: ${rendered()}")
            assertEquals(null, reports.settingsAfterChange, "and nothing should have been saved")
        }
    }

    private object Col {
        const val NUMBER = "Number"
        const val SONG_BOOK = "Song Book"
        const val TITLE = "Title"
        const val TUNE = "Tune"
        const val PLAY_COUNT = "Plays"
        const val AUTHOR = "Author"
        const val COMPOSER = "Composer"
    }

    private companion object {
        const val FILTER_COLUMNS = "Filter columns"
    }
}
