@file:OptIn(ExperimentalTestApi::class)

package songlibrary.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import org.churchpresenter.core.models.songs.SongItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The two things the window leaves to whoever opened it: the editor a row opens, and whether there
 * is a way out of the window at all.
 *
 * Both are parameters with no default behaviour behind them any more, so a host that forgets one
 * gets a window missing a button rather than a crash — which is worth pinning.
 */
class HostingTest {

    @Test
    fun `a row asks the host to edit the song it belongs to`() {
        var asked: SongItem? = null
        withLibrary(songEditor = { request -> asked = request.song; Text("editing ${request.song.title}") }) { _ ->
            narrowToTitleOnly()
            onAllNodesWithContentDescription("Edit song")[0].performClick()
            waitForIdle()

            assertNotNull(asked, "the host was asked for an editor")
            assertTrue(isShowing("editing ${asked?.title}"), "and what it returned is what is on screen")
        }
    }

    @Test
    fun `the request carries the library around the song, not just the song`() {
        var request: SongEditorRequest? = null
        withLibrary(songEditor = { request = it }) { _ ->
            narrowToTitleOnly()
            onAllNodesWithContentDescription("Edit song")[0].performClick()
            waitForIdle()

            assertEquals(STOCK.size, request?.allSongs?.size, "every song, for a clash check")
            assertTrue(
                request?.songbooks?.containsAll(listOf("Hymnal", "Chorus Book")) == true,
                "and every book it could be moved to",
            )
        }
    }

    @Test
    fun `with no editor supplied a row offers no way to open one`() = withLibrary { _ ->
        narrowToTitleOnly()

        assertEquals(
            0,
            onAllNodesWithContentDescription("Edit song").fetchSemanticsNodes().size,
            "a button that could only do nothing is not drawn",
        )
        assertTrue(
            onAllNodesWithContentDescription("Delete song").fetchSemanticsNodes().isNotEmpty(),
            "deleting still works without a host editor",
        )
    }

    @Test
    fun `a window with nowhere to go back to has no Done button`() =
        withLibrary(onClose = null) { _ ->
            assertFalse(isShowing("Done"), "the standalone window is not closed from inside")
            assertTrue(isShowing("Save Changes"), "the rest of the footer is still there")
        }
}
