@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deleting a song.
 *
 * Deleting asks first, and the confirmation names the song and the file it came from — this removes a
 * file from the library folder, so the operator has to be able to see which one before agreeing.
 * Cancelling has to leave it alone, which is asserted on the library rather than on the dialog closing.
 *
 * The per-song tempo used to be set from a tile in this tab. It moved into the song editor alongside
 * the capo, and is covered by `EditSongContentTest` now.
 */
class SongsTabDeleteTest {

    private fun ComposeUiTest.clickPopup(label: String) {
        val nodes = onAllNodesWithText(label)
        nodes[nodes.fetchSemanticsNodes().size - 1].performClick()
        waitForIdle()
    }

    /** Right-clicks a row and chooses Delete, which opens the confirmation. */
    private fun ComposeUiTest.startDeleting(title: String) {
        onNodeWithText(title).performMouseInput { rightClick() }
        waitForIdle()
        clickPopup(DELETE)
    }

    // ── Deleting a song ─────────────────────────────────────────────────────────

    @Test
    fun `deleting asks first and names the song`() {
        songsTab { vm, _ ->
            startDeleting("Amazing Grace")

            assertTrue(showsContaining("Amazing Grace"), rendered().toString())
            // The file is named too — this removes it from the library folder.
            assertTrue(
                rendered().any { it.contains(".song") },
                "the operator has to see which file is going: ${rendered()}",
            )
            assertEquals(4, vm.filteredSongItems.value.size, "asking must not have deleted anything")
        }
    }

    @Test
    fun `cancelling a delete leaves the song in the library`() {
        songsTab { vm, _ ->
            startDeleting("Amazing Grace")

            clickPopup(CANCEL)

            assertEquals(4, vm.filteredSongItems.value.size)
            assertTrue(
                vm.filteredSongItems.value.any { it.title == "Amazing Grace" },
                "the song has to still be there",
            )
        }
    }

    @Test
    fun `confirming a delete removes the song and its file`() {
        songsTab { vm, _ ->
            val before = vm.filteredSongItems.value.single { it.title == "Amazing Grace" }
            val file = java.io.File(before.sourceFile)
            assertTrue(file.exists(), "the fixture should have written a real file")

            startDeleting("Amazing Grace")
            clickPopup(DELETE)

            assertFalse(
                vm.filteredSongItems.value.any { it.title == "Amazing Grace" },
                "it should be gone from the library: ${vm.filteredSongItems.value.map { it.title }}",
            )
            assertFalse(file.exists(), "and the file removed from the folder")
        }
    }

    @Test
    fun `deleting one song leaves the others alone`() {
        songsTab { vm, _ ->
            startDeleting("Amazing Grace")
            clickPopup(DELETE)

            assertEquals(3, vm.filteredSongItems.value.size)
            assertTrue(vm.filteredSongItems.value.any { it.title == "Be Thou My Vision" })
        }
    }

    private companion object {
        const val CANCEL = "Cancel"
        const val DELETE = "Delete"
    }
}
