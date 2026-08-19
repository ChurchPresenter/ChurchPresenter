@file:OptIn(ExperimentalTestApi::class)

package songlibrary.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The window itself, driven the way a person drives it.
 *
 * Every assertion here is about what the folder or the screen ends up holding — a save that really
 * writes the file, a delete that really removes it, a filter that really drops rows — rather than
 * that a composable rendered without throwing.
 */
class SongLibraryAppTest {

    // ── What the grid shows ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the grid lists every song under the folder`() = withLibrary { _ ->
        assertEquals(STOCK.map { it.title }.toSet(), rowTitles().toSet())
        assertTrue(isShowing("6 songs"), "the footer counts what is on screen")
    }

    @Test
    fun `a folder with no songs says so instead of showing an empty grid`() =
        withLibrary(songs = emptyList()) { _ ->
            waitUntil("the empty state appeared", 10_000L) { isShowing(Text.EMPTY_LIBRARY) }
            assertFalse(isShowing(Text.RESET_FILTERS), "nothing is filtered, so nothing to reset")
        }

    @Test
    fun `searching narrows the grid to what matches`() = withLibrary { _ ->
        typeSearch("newton")

        assertEquals(listOf("Amazing Grace"), rowTitles())
        assertTrue(isShowing("1 of 6 songs"), "the subhead says how much is hidden")
    }

    @Test
    fun `a search that matches nothing offers to clear itself`() = withLibrary { _ ->
        typeSearch("bagpipes")

        assertTrue(isShowing(Text.NO_MATCHES))
        click(Text.RESET_FILTERS)
        assertEquals(STOCK.size, rowTitles().size)
    }

    @Test
    fun `the song book filter shows one book at a time`() = withLibrary { _ ->
        click(Text.ALL_BOOKS)
        clickLast("Chorus Book")

        assertEquals(listOf("Here I Am to Worship"), rowTitles())
    }

    @Test
    fun `turning a column off takes it out of the grid, and Show all brings it back`() =
        withLibrary { _ ->
            assertTrue(isShowing("John Newton"), "the author column starts visible")

            // Asserted with the menu still open. Clicking the Columns button a second time does
            // not close it: the panel opens over its own anchor, so that click lands on the
            // panel's first row, "Show all" — see the test below.
            click(Text.COLUMNS)
            clickLast("Author")
            assertFalse(isShowing("John Newton"), "the column and its cells went")

            click(Text.SHOW_ALL)
            assertTrue(isShowing("John Newton"), "and came back")
        }

    /**
     * The columns panel opens over the button that opens it, so the button cannot be used to close
     * it — a second press lands on "Show all" and silently restores every column the operator just
     * turned off. Pinned as it behaves today; if the panel is ever moved clear of its anchor this
     * is the test that should be rewritten rather than deleted.
     */
    @Test
    fun `pressing Columns again lands on Show all rather than closing the panel`() =
        withLibrary { _ ->
            click(Text.COLUMNS)
            clickLast("Author")
            assertFalse(isShowing("John Newton"))

            click(Text.COLUMNS)

            assertTrue(isShowing("John Newton"), "the hidden column came back on its own")
        }

    // ── Selecting ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `ticking a row brings up the bulk bar, and clearing puts it away`() = withLibrary { _ ->
        assertFalse(isShowing(Text.BATCH_EDIT), "nothing is selected yet")

        tickRow(0)
        assertTrue(isShowing("1 selected"))

        click(Text.CLEAR)
        assertFalse(isShowing(Text.BATCH_EDIT))
    }

    @Test
    fun `the header tick selects every row on screen`() = withLibrary { _ ->
        selectAll()

        assertTrue(isShowing("${STOCK.size} selected"))
    }

    // ── Editing ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `typing in a cell marks the library unsaved without touching the file`() =
        withLibrary { folder ->
            retypeCell("John Newton", "Charles Wesley")

            assertTrue(isShowing("Charles Wesley"), "the grid shows what was typed")
            assertTrue(isShowing("1 unsaved"))
            assertTrue(
                fileFor(folder, "Amazing Grace").readText().contains("John Newton"),
                "nothing reaches the disk until Save",
            )
        }

    @Test
    fun `saving writes the edit to the file`() = withLibrary { folder ->
        retypeCell("John Newton", "Charles Wesley")
        click(Text.SAVE)

        waitUntil("the save finished", 10_000L) { !isShowing("1 unsaved") }
        assertTrue(fileFor(folder, "Amazing Grace").readText().contains("Charles Wesley"))
    }

    @Test
    fun `reverting puts the cell back`() = withLibrary { _ ->
        retypeCell("John Newton", "Charles Wesley")
        click(Text.REVERT)

        assertTrue(isShowing("John Newton"))
        assertFalse(isShowing("1 unsaved"))
    }

    @Test
    fun `a new song is written to the folder and appears in the grid`() = withLibrary { folder ->
        click(Text.NEW_SONG)

        waitUntil("the new row appeared", 10_000L) { isShowing(Text.NEW_SONG) && isShowing("7 songs") }
        assertTrue(
            folder.walkTopDown().any { it.extension == "song" && it.readText().contains(Text.NEW_SONG) },
            "a file was written for it",
        )
    }

    // ── Deleting ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `deleting a selection removes the rows and their files`() = withLibrary { folder ->
        val doomed = fileFor(folder, "Doxology")
        tickRow(0)
        clickFirst(Text.DELETE)
        clickLast("Delete")

        waitUntil("the row went", 10_000L) { !isShowing("Doxology") }
        assertFalse(doomed.exists(), "the file went with it")
    }

    @Test
    fun `cancelling a delete keeps the song`() = withLibrary { folder ->
        tickRow(0)
        clickFirst(Text.DELETE)
        clickLast(Text.CANCEL)

        assertTrue(isShowing("Doxology"))
        assertTrue(fileFor(folder, "Doxology").exists())
    }

    // ── Song books ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `creating a song book makes the folder`() = withLibrary { folder ->
        click(Text.ALL_BOOKS)
        click(Text.NEW_BOOK_MENU)
        typeIntoLastField("Anthems")
        clickLast(Text.CREATE)

        waitUntil("the folder appeared", 10_000L) { File(folder, "Anthems").isDirectory }
    }

    @Test
    fun `a batch edit writes one field across the whole selection`() = withLibrary { folder ->
        selectAll()
        click(Text.BATCH_EDIT)
        // The dialog offers one row per field; ticking Composer is what makes it writable.
        clickLast("Composer")
        typeIntoLastField("Traditional")
        clickContaining("Apply to")

        waitUntil("every row took the value", 10_000L) { countShowing("Traditional") >= STOCK.size - 1 }
    }

    @Test
    fun `moving a song to another book from its own row`() = withLibrary { _ ->
        clickFirst("Hymnal")
        clickLast("Chorus Book")

        assertTrue(isShowing("1 unsaved"), "the move is an edit like any other")
        click(Text.ALL_BOOKS)
        clickLast("Chorus Book")
        assertEquals(2, rowTitles().size, "the song is filed with the chorus book now")
    }

    @Test
    fun `taking a song out of every book from its own row`() = withLibrary { _ ->
        clickFirst("Hymnal")
        clickLast("No Song Book")

        assertTrue(isShowing("1 unsaved"))
        click(Text.ALL_BOOKS)
        clickLast("No Song Book")
        assertEquals(2, rowTitles().size, "it joins the song that was already loose")
    }

    private fun ComposeUiTest.typeSearch(query: String) {
        onNode(hasSetTextAction()).performTextInput(query)
        waitForIdle()
    }

    private fun fileFor(folder: File, title: String): File =
        folder.walkTopDown().first { it.extension == "song" && it.readText().contains("title: $title") }
}
