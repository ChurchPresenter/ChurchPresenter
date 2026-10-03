@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongListRowsMatchChipsTest {

    private fun ComposeUiTest.count(text: String) =
        onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).size

    private fun ComposeUiTest.openMenuOn(title: String) {
        onNodeWithText(title).performMouseInput { rightClick() }
        waitForIdle()
    }

    private fun ComposeUiTest.clickLast(label: String) {
        val nodes = onAllNodesWithText(label)
        nodes[nodes.fetchSemanticsNodes().size - 1].performClick()
        waitForIdle()
    }

    private val headerless = listOf(SongFixture("5", "Plain", lyrics = listOf("just some plain words here")))

    private val chorusOnly =
        listOf(SongFixture("7", "Holy Holy Holy", lyrics = listOf("{Chorus}", "Lord God Almighty")))

    private val bilingual = listOf(
        SongFixture(
            "3",
            "Великий Бог",
            lyrics = listOf("[Куплет 1]", "Коли дивлюсь"),
            secondaryTitle = "How Great Thou Art",
            secondaryLyrics = listOf("[Verse 1]", "O Lord my God"),
        ),
    )

    private val wretched = listOf(
        SongFixture("1", "Amazing Grace", lyrics = listOf("[Verse 1]", "That saved a wretch like me")),
        SongFixture("48", "Mercy", lyrics = listOf("[Bridge]", "Mercy found a wretch undone")),
    )

    @Test
    fun `a match under no section header is labelled Lyrics`() = songsTab(songs = headerless) { _, _ ->
        val before = count("Lyrics")

        search("plain words")

        assertEquals(before + 1, count("Lyrics"), rendered().toString())
    }

    @Test
    fun `a chorus match in the song's own language names the chorus`() = songsTab(songs = chorusOnly) { _, _ ->
        val before = count("Chorus")

        search("Almighty")

        assertEquals(before + 1, count("Chorus"), rendered().toString())
        assertEquals(0, count("Translation 2"), "the primary language is not named")
    }

    @Test
    fun `a match in a translation's title names that translation`() = songsTab(songs = bilingual) { _, _ ->
        val before = count("Translation 2")
        val titles = count("Title")

        search("Great Thou")

        assertEquals(before + 1, count("Translation 2"), rendered().toString())
        assertEquals(titles + 1, count("Title"), "a title chip joins the column header")
    }

    @Test
    fun `double-clicking a match line sends its song live`() = songsTab(songs = wretched) { vm, reports ->
        search("wretch")

        onNodeWithText("wretch undone", substring = true).performMouseInput { doubleClick() }
        waitForIdle()

        assertEquals("Mercy", vm.filteredSongItems.value.getOrNull(vm.selectedSongIndex.value)?.title)
        assertTrue(Presenting.LYRICS in reports.presenting, reports.presenting.toString())
    }

    @Test
    fun `Edit Song from the menu opens the editor on that row's song`() {
        var editing: SongItem? = null
        songsTab(
            songEditor = { request -> if (request.isVisible && !request.isNewSong) editing = request.song },
        ) { _, _ ->
            assertNull(editing)

            openMenuOn("Be Thou My Vision")
            clickLast("Edit Song")

            assertEquals("Be Thou My Vision", editing?.title)
        }
    }

    @Test
    fun `the editor closes when it is dismissed`() {
        var request: SongEditorRequest? = null
        songsTab(songEditor = { r -> if (!r.isNewSong) request = r }) { _, _ ->
            openMenuOn("Amazing Grace")
            clickLast("Edit Song")
            assertEquals(true, request?.isVisible)

            runOnIdle { request?.onDismiss?.invoke() }
            waitForIdle()

            assertEquals(false, request?.isVisible)
        }
    }

    @Test
    fun `the editor is handed the library's songbooks and songs`() {
        var request: SongEditorRequest? = null
        songsTab(songEditor = { r -> if (!r.isNewSong) request = r }) { _, _ ->
            openMenuOn("Amazing Grace")
            clickLast("Edit Song")

            assertEquals(setOf("Hymnal", "Chorus Book"), request?.songbooks?.toSet())
            assertEquals(4, request?.existingSongs?.size)
        }
    }

    @Test
    fun `Delete from the menu asks before deleting`() = songsTab { vm, _ ->
        openMenuOn("Be Thou My Vision")
        clickLast("Delete")

        assertTrue(rendered().any { it.contains(".song") }, rendered().toString())
        assertEquals(4, vm.filteredSongItems.value.size)
    }

    @Test
    fun `the menu removes a favourite it was opened on`() = songsTab { vm, _ ->
        val id = vm.filteredSongItems.value.first { it.title == "Amazing Love" }.songId
        vm.toggleFavorite(id)
        waitForIdle()

        openMenuOn("Amazing Love")
        clickLast("Remove from favorites")

        assertFalse(id in vm.favorites.value)
    }
}
