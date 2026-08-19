@file:OptIn(ExperimentalTestApi::class)

package songlibrary.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import core.models.songs.SongField
import core.models.songs.SongLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import songlibrary.SongLibraryState
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The line the footer shows when a save could not be written.
 *
 * Driven from the state rather than from the grid, because the grid cannot reach it: a song's book
 * is chosen from a menu of books that exist, so there is no way to type a name into it that the
 * filesystem will refuse. The failure itself is real — a song book cannot be created where a file
 * of that name already sits — and this is the only place the operator is told about it.
 */
class FooterTest {

    @Test
    fun `a save that could not be written is reported, not swallowed`() {
        val folder = Files.createTempDirectory("songlibrary-footer").toFile()
        try {
            val library = SongLibrary(folder)
            library.writeNew(STOCK.first())
            val blocker = library.writeNew(song("", "Doxology", ""))

            val state = SongLibraryState(folder)
            runBlocking { state.reloadAsync(Dispatchers.Unconfined) }
            // Filing it under the name of an existing *file* leaves nowhere for the folder to go.
            val doomed = state.songs.first { it.title == "Amazing Grace" }
            state.edit(doomed.sourceFile, SongField.SONGBOOK, java.io.File(blocker.sourceFile).name)
            runBlocking { state.save(Dispatchers.Unconfined) }

            assertTrue(state.lastOutcome?.errors?.isNotEmpty() == true, "the save really did fail")

            runComposeUiTest {
                setContent { Themed { LibraryFooter(state, io = Dispatchers.Unconfined, onClose = {}) } }

                assertTrue(isShowingText("could not be saved"), "and the footer says so")
                assertTrue(isShowingText("Amazing Grace"), "naming the song that did not make it")
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `a clean footer counts the library and offers no error`() {
        val folder = Files.createTempDirectory("songlibrary-footer-clean").toFile()
        try {
            SongLibrary(folder).also { lib -> STOCK.take(3).forEach { lib.writeNew(it) } }
            val state = SongLibraryState(folder)
            runBlocking { state.reloadAsync(Dispatchers.Unconfined) }

            runComposeUiTest {
                setContent { Themed { LibraryFooter(state, io = Dispatchers.Unconfined, onClose = null) } }

                assertTrue(isShowing("3 songs"))
                assertTrue(!isShowingText("could not be saved"))
                assertTrue(!isShowing("Done"), "with nowhere to go back to there is no Done button")
            }
        } finally {
            folder.deleteRecursively()
        }
    }
}
