@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongsEmptyStatesTest {

    private fun androidx.compose.ui.test.ComposeUiTest.pickStartsWith() {
        onNodeWithText("FILTER", substring = true).performClick()
        waitForIdle()
        onNodeWithText("Starts With").performClick()
        waitForIdle()
    }

    @Test
    fun `an empty library offers its folder and its settings`() {
        var opened = 0
        songsTab(songs = emptyList(), onOpenSettings = { opened++ }) { _, _ ->
            assertTrue(shows("No song library yet"), rendered().toString())
            assertTrue(shows("Choose folder"))
            onNodeWithText("Open Settings").performClick()
            waitForIdle()
            assertEquals(1, opened)
        }
    }

    @Test
    fun `a search that only reads titles offers to search the lyrics`() = songsTab { vm, _ ->
        pickStartsWith()
        search("zzzz-nothing")

        assertTrue(shows("No songs match “zzzz-nothing”"), rendered().toString())
        assertTrue(shows("Check the spelling, or search the lyrics instead."))

        onNodeWithText("Search lyrics").performClick()
        waitForIdle()

        assertEquals(Constants.CONTAINS, vm.filterType.value)
        assertTrue(shows("Nothing in the lyrics either. Check the spelling."), rendered().toString())
        assertFalse(shows("Search lyrics"))
    }

    @Test
    fun `Clear search brings the whole list back`() = songsTab { _, _ ->
        search("zzzz-nothing")
        onNodeWithText("Clear search").performClick()
        waitForIdle()

        assertFalse(shows("No songs match “zzzz-nothing”"))
        assertTrue(showsContaining("Amazing Grace"), rendered().toString())
    }

    @Test
    fun `with no song selected the lyrics side says so, and the go-live hint stays away`() = songsTab { _, _ ->
        search("zzzz-nothing")
        searchBox().performClick()
        waitForIdle()

        assertTrue(shows("No song selected"), rendered().toString())
        assertTrue(shows("Pick a song from the list to see its lyrics."))
        assertTrue(onAllNodes(hasText("Searching —", substring = true)).fetchSemanticsNodes(false).isEmpty())
    }

    @Test
    fun `a newly loaded library counts its songs`() = runComposeUiTest {
        setContent { SongsLoaded(1248) }
        assertTrue(onAllNodes(hasText("1248 songs loaded")).fetchSemanticsNodes(false).isNotEmpty())
        assertTrue(
            onAllNodes(hasText("Pick a song from the list, or search for one, to see its lyrics."))
                .fetchSemanticsNodes(false).isNotEmpty(),
        )
    }

    @Test
    fun `one song is counted as one`() = runComposeUiTest {
        setContent { SongsLoaded(1) }
        assertTrue(onAllNodes(hasText("1 song loaded")).fetchSemanticsNodes(false).isNotEmpty())
    }
}
