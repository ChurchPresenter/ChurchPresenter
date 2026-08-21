@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.songlibrary.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.core.models.songs.SongField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The three dialogs, each asked what it hands back.
 *
 * Composed directly rather than driven to through the grid: what matters here is the answer the
 * dialog gives its caller — the name and the assign flag, which fields a batch edit carries, that a
 * confirmation confirms — and that is a parameter of the dialog, not of the window behind it.
 */
class DialogsTest {

    // ── New song book ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a name has to be typed before it can be created`() = runComposeUiTest {
        var created: Pair<String, Boolean>? = null
        setContent {
            Themed {
                NewSongBookDialog(existing = emptyList(), selectedCount = 0, onDismiss = {}) { name, assign ->
                    created = name to assign
                }
            }
        }

        onNodeWithText(Text.CREATE).assertIsNotEnabled()
        onNode(hasSetTextAction()).performTextInput("Anthems")
        onNodeWithText(Text.CREATE).performClick()

        assertEquals("Anthems" to false, created)
    }

    @Test
    fun `a name that already exists is refused`() = runComposeUiTest {
        var created: Pair<String, Boolean>? = null
        setContent {
            Themed {
                NewSongBookDialog(existing = listOf("Hymnal"), selectedCount = 0, onDismiss = {}) { n, a ->
                    created = n to a
                }
            }
        }

        onNode(hasSetTextAction()).performTextInput("Hymnal")
        waitForIdle()

        assertTrue(isShowing("That song book already exists"), "it says why")
        onNodeWithText(Text.CREATE).assertIsNotEnabled()
        assertNull(created)
    }

    @Test
    fun `a name that cannot be a folder is refused`() = runComposeUiTest {
        setContent {
            Themed { NewSongBookDialog(existing = emptyList(), selectedCount = 0, onDismiss = {}) { _, _ -> } }
        }

        onNode(hasSetTextAction()).performTextInput("../escape")
        waitForIdle()

        assertTrue(isShowing("That name cannot be used for a folder"))
        onNodeWithText(Text.CREATE).assertIsNotEnabled()
    }

    /** With a selection standing, filing it under the new book is the expected next move. */
    @Test
    fun `with songs selected it files them under the new book by default`() = runComposeUiTest {
        var created: Pair<String, Boolean>? = null
        setContent {
            Themed {
                NewSongBookDialog(existing = emptyList(), selectedCount = 3, onDismiss = {}) { n, a ->
                    created = n to a
                }
            }
        }

        onNode(hasSetTextAction()).performTextInput("Anthems")
        onNodeWithText(Text.CREATE).performClick()

        assertEquals("Anthems" to true, created)
    }

    @Test
    fun `and that can be turned off to make an empty book`() = runComposeUiTest {
        var created: Pair<String, Boolean>? = null
        setContent {
            Themed {
                NewSongBookDialog(existing = emptyList(), selectedCount = 3, onDismiss = {}) { n, a ->
                    created = n to a
                }
            }
        }

        onNode(hasSetTextAction()).performTextInput("Anthems")
        click("File the 3 selected songs under it")
        onNodeWithText(Text.CREATE).performClick()

        assertEquals("Anthems" to false, created)
    }

    @Test
    fun `with nothing selected there is nothing to file`() = runComposeUiTest {
        setContent {
            Themed { NewSongBookDialog(existing = emptyList(), selectedCount = 0, onDismiss = {}) { _, _ -> } }
        }

        assertTrue(!isShowing("File the 0 selected songs under it"))
    }

    @Test
    fun `cancelling asks for nothing`() = runComposeUiTest {
        var dismissed = false
        var created = false
        setContent {
            Themed {
                NewSongBookDialog(existing = emptyList(), selectedCount = 0, onDismiss = { dismissed = true }) { _, _ ->
                    created = true
                }
            }
        }

        click(Text.CANCEL)

        assertTrue(dismissed)
        assertTrue(!created)
    }

    // ── Batch edit ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `nothing can be applied until a field is ticked`() = runComposeUiTest {
        setContent {
            Themed { BatchEditDialog(count = 4, songbooks = emptyList(), onDismiss = {}) { } }
        }

        assertTrue(isShowing("Tick a field to change"), "it says what is missing")
        onNodeWithText("Apply to 4 songs").assertIsNotEnabled()
    }

    @Test
    fun `only the ticked fields are handed back`() = runComposeUiTest {
        var applied: Map<SongField, String>? = null
        setContent {
            Themed { BatchEditDialog(count = 4, songbooks = emptyList(), onDismiss = {}) { applied = it } }
        }

        click("Composer")
        typeIntoLastField("Traditional")
        click("Tune")
        typeIntoLastField("SLANE")
        click("Apply to 4 songs")

        assertEquals(
            mapOf(SongField.COMPOSER to "Traditional", SongField.TUNE to "SLANE"),
            applied,
            "untouched fields must not be written over the selection",
        )
    }

    @Test
    fun `a ticked field with no text clears that field`() = runComposeUiTest {
        var applied: Map<SongField, String>? = null
        setContent {
            Themed { BatchEditDialog(count = 2, songbooks = emptyList(), onDismiss = {}) { applied = it } }
        }

        click("Author")
        click("Apply to 2 songs")

        assertEquals(mapOf(SongField.AUTHOR to ""), applied, "ticking with no value is how a field is emptied")
    }

    @Test
    fun `unticking a field takes it back out`() = runComposeUiTest {
        var applied: Map<SongField, String>? = null
        setContent {
            Themed { BatchEditDialog(count = 2, songbooks = emptyList(), onDismiss = {}) { applied = it } }
        }

        click("Composer")
        typeIntoLastField("Excell")
        click("Composer")

        onNodeWithText("Apply to 2 songs").assertIsNotEnabled()
        assertNull(applied)
    }

    @Test
    fun `the song book field offers the books the library already has`() = runComposeUiTest {
        var applied: Map<SongField, String>? = null
        setContent {
            Themed {
                BatchEditDialog(
                    count = 2,
                    songbooks = listOf("Hymnal", "Chorus Book"),
                    onDismiss = {},
                ) { applied = it }
            }
        }

        click("Song Book")
        clickLast("No Song Book")
        clickLast("Chorus Book")
        click("Apply to 2 songs")

        assertEquals(mapOf(SongField.SONGBOOK to "Chorus Book"), applied)
    }

    @Test
    fun `the song book field stays shut until its row is ticked`() = runComposeUiTest {
        setContent {
            Themed { BatchEditDialog(count = 2, songbooks = listOf("Hymnal"), onDismiss = {}) { } }
        }

        clickLast("No Song Book")

        assertEquals(1, countShowing("No Song Book"), "an unticked field does not open its menu")
    }

    // ── Deleting ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `one song is named in the question`() = runComposeUiTest {
        var confirmed = false
        setContent {
            Themed {
                DeleteConfirmDialog(songs = listOf(STOCK.first()), onDismiss = {}) { confirmed = true }
            }
        }

        assertTrue(isShowing("Delete “Amazing Grace”?"))
        click(Text.DELETE)

        assertTrue(confirmed)
    }

    @Test
    fun `several songs are counted rather than named in the question`() = runComposeUiTest {
        setContent {
            Themed { DeleteConfirmDialog(songs = STOCK.take(4), onDismiss = {}) { } }
        }

        assertTrue(isShowing("Delete 4 songs?"))
        assertTrue(isShowing("Amazing Grace"), "and the list is shown underneath")
    }

    @Test
    fun `a long list is cut short rather than filling the screen`() = runComposeUiTest {
        val many = (1..12).map { song(it.toString(), "Song $it", "Hymnal") }
        setContent { Themed { DeleteConfirmDialog(songs = many, onDismiss = {}) { } } }

        assertTrue(isShowing("Song 1"))
        assertTrue(!isShowing("Song 12"), "past the preview the rest are summarised")
    }

    @Test
    fun `cancelling a delete confirms nothing`() = runComposeUiTest {
        var confirmed = false
        var dismissed = false
        setContent {
            Themed {
                DeleteConfirmDialog(
                    songs = listOf(STOCK.first()),
                    onDismiss = { dismissed = true },
                ) { confirmed = true }
            }
        }

        click(Text.CANCEL)

        assertTrue(dismissed)
        assertTrue(!confirmed)
    }
}
