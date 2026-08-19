@file:OptIn(ExperimentalTestApi::class)

package songlibrary.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two kinds of cell the grid is made of.
 *
 * A grid cell is text until it is clicked and a field while it is being typed in, and what it does
 * on the way out of that field is the whole point: Enter and clicking away keep the edit, Escape
 * throws it away. Getting that wrong writes a mistyped number to disk because a row scrolled.
 */
class CellsTest {

    @Test
    fun `a cell is text until it is clicked`() = runComposeUiTest {
        setContent { Themed { EditableCell(value = "Watts", onCommit = {}) } }

        assertTrue(isShowing("Watts"))
        assertEquals(0, onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size, "no field yet")

        onNodeWithText("Watts").performClick()

        assertEquals(1, onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size, "now it is one")
    }

    @Test
    fun `Enter keeps what was typed`() = runComposeUiTest {
        var committed: String? = null
        setContent { Themed { EditableCell(value = "Watts", onCommit = { committed = it }) } }

        onNodeWithText("Watts").performClick()
        onNode(hasSetTextAction()).performTextReplacement("Wesley")
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }

        assertEquals("Wesley", committed)
    }

    @Test
    fun `Escape throws it away`() = runComposeUiTest {
        var committed: String? = null
        setContent { Themed { EditableCell(value = "Watts", onCommit = { committed = it }) } }

        onNodeWithText("Watts").performClick()
        onNode(hasSetTextAction()).performTextReplacement("Wesley")
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Escape) }

        assertNull(committed, "an abandoned edit must not reach the caller")
        assertTrue(isShowing("Watts"), "and the cell goes back to what it was")
    }

    @Test
    fun `clicking another cell keeps the edit rather than losing it`() = runComposeUiTest {
        var committed: String? = null
        setContent {
            Themed {
                Column(Modifier.width(300.dp)) {
                    EditableCell(value = "Watts", onCommit = { committed = it })
                    EditableCell(value = "Newton", onCommit = {})
                }
            }
        }

        onNodeWithText("Watts").performClick()
        onNode(hasSetTextAction()).performTextReplacement("Wesley")
        // Focus moves to the other cell, which is how a row is left in a real grid.
        onNodeWithText("Newton").performClick()
        waitForIdle()

        assertEquals("Wesley", committed)
    }

    @Test
    fun `typing the same text again asks for no write`() = runComposeUiTest {
        var commits = 0
        setContent { Themed { EditableCell(value = "Watts", onCommit = { commits++ }) } }

        onNodeWithText("Watts").performClick()
        onNode(hasSetTextAction()).performTextReplacement("Watts")
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }

        assertEquals(0, commits, "an unchanged cell is not an edit")
    }

    // ── The song book cell ──────────────────────────────────────────────────────────────────────

    @Test
    fun `the song book cell picks from the books the library has`() = runComposeUiTest {
        var picked: String? = null
        setContent {
            Themed {
                SongbookCell(
                    value = "Hymnal",
                    songbooks = listOf("Hymnal", "Chorus Book"),
                    onPick = { picked = it },
                    onNewBook = {},
                )
            }
        }

        onNodeWithText("Hymnal").performClick()
        waitForIdle()
        clickLast("Chorus Book")

        assertEquals("Chorus Book", picked)
    }

    @Test
    fun `it can take a song out of every book`() = runComposeUiTest {
        var picked: String? = null
        setContent {
            Themed {
                SongbookCell(value = "Hymnal", songbooks = listOf("Hymnal"), onPick = { picked = it }, onNewBook = {})
            }
        }

        onNodeWithText("Hymnal").performClick()
        waitForIdle()
        clickLast("No Song Book")

        assertEquals("", picked, "no book is the empty string, not a book called that")
    }

    @Test
    fun `it offers to make a book that does not exist yet`() = runComposeUiTest {
        var asked = false
        setContent {
            Themed {
                SongbookCell(value = "", songbooks = listOf("Hymnal"), onPick = {}, onNewBook = { asked = true })
            }
        }

        clickLast("No Song Book")
        waitForIdle()
        clickLast(Text.NEW_BOOK_MENU)

        assertTrue(asked)
    }
}
