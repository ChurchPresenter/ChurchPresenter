@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The line under a matching song: where a text search found it, and the words around the match. See
 * `SongsTabTestSupport.kt` for the harness.
 */
class SongsTabSearchMatchTest {

    private val songs = listOf(
        SongFixture("1", "Amazing Grace", lyrics = listOf("[Verse 1]", "That saved a wretch like me")),
        SongFixture("2", "Holy Holy Holy", lyrics = listOf("{Chorus}", "Lord God Almighty")),
        SongFixture(
            "3",
            "Великий Бог",
            lyrics = listOf("[Куплет 1]", "Коли дивлюсь"),
            secondaryTitle = "How Great Thou Art",
            secondaryLyrics = listOf("{Chorus}", "Then sings my soul"),
        ),
        SongFixture("48", "Mercy", lyrics = listOf("[Bridge]", "Mercy found a wretch undone")),
    )

    private fun ComposeUiTest.count(text: String, substring: Boolean = false) =
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    @Test
    fun `a lyric match names its section and shows the words around it`() = songsTab(songs = songs) { _, _ ->
        search("wretch")

        assertEquals(1, count("Verse 1"), "Amazing Grace matched in its verse")
        assertEquals(1, count("Bridge"), "Mercy matched in its bridge")
        // Exactly, ellipsis and all: the lyric pane beside the list shows the same line in full.
        assertEquals(1, count("…saved a wretch like me"), "with the words around the match")
    }

    @Test
    fun `a title match says so`() = songsTab(songs = songs) { _, _ ->
        search("holy")
        // The column header is the other "Title".
        assertEquals(2, count("Title"))
    }

    @Test
    fun `a match in another language names it`() = songsTab(songs = songs) { _, _ ->
        search("sings my soul")

        assertEquals(1, count("Chorus"))
        assertEquals(1, count("Translation 2"), "a language the song does not name is numbered")
    }

    @Test
    fun `a number search draws no match line`() = songsTab(songs = songs) { _, _ ->
        search("48")

        assertEquals(listOf("Mercy"), listedTitles(songs))
        assertEquals(0, count("Bridge"))
        assertEquals(1, count("Title"), "only the column header")
    }

    @Test
    fun `clicking the match line selects its song`() = songsTab(songs = songs) { vm, _ ->
        search("wretch")

        onNodeWithText("wretch undone", substring = true).performClick()
        waitForIdle()

        val selected = vm.filteredSongItems.value.getOrNull(vm.selectedSongIndex.value)
        assertEquals("Mercy", selected?.title)
    }

    @Test
    fun `an empty box shows the list as before`() = songsTab(songs = songs) { _, _ ->
        search("wretch")
        search("")

        assertEquals(0, count("Verse 1"))
        assertTrue(listedTitles(songs).size == songs.size)
    }
}
