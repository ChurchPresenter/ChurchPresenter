@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.slides.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.churchpresenter.sharedui.filechooser.FileChooser
import org.churchpresenter.sharedui.testing.RecentFilesSwap
import org.churchpresenter.slides.data.RecentPresentationFiles
import org.churchpresenter.slides.pdfDeck
import org.churchpresenter.slides.tempDir
import java.io.File
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Path as NioPath

/**
 * The Pictures tab's Select Folder and the Presentation tab's Select File: both open
 * `FileChooser.platformInstance`, a native dialog with nothing to click headless, stood in for the
 * way `QATabFileChooserTest` does. Both also remember what was picked, so the recent-files
 * singletons are pointed at temp files through [RecentFilesSwap] for each test.
 */
class SlidesFileChooserTest {

    private class FakeChooser(private val picked: String?) : FileChooser() {
        /** Bumped as the dialog answers: the flows run in a launched coroutine, which a click does not await. */
        @Volatile
        var answered: Int = 0
            private set

        override suspend fun chooseImpl(
            path: NioPath,
            filters: List<FileNameExtensionFilter>,
            title: String,
            selectDirectory: Boolean,
            multiple: Boolean,
        ): List<NioPath>? = picked?.let { listOf(Path(it)) }.also { answered++ }

        override suspend fun saveImpl(
            location: NioPath,
            suggestedName: String,
            filters: List<FileNameExtensionFilter>,
            title: String,
        ): NioPath? = picked?.let { Path(it) }.also { answered++ }
    }

    private val folders = RecentFilesSwap(
        readPaths = { RecentPictureFolders.file to RecentPictureFolders.pinnedFile },
        writePaths = { f, p -> RecentPictureFolders.file = f; RecentPictureFolders.pinnedFile = p },
        entries = RecentPictureFolders.folders,
        pinned = RecentPictureFolders.pinned,
        prefix = "cp-slides-chooser-folders",
    )

    private val decks = RecentFilesSwap(
        readPaths = { RecentPresentationFiles.file to RecentPresentationFiles.pinnedFile },
        writePaths = { f, p -> RecentPresentationFiles.file = f; RecentPresentationFiles.pinnedFile = p },
        entries = RecentPresentationFiles.files,
        pinned = RecentPresentationFiles.pinned,
        prefix = "cp-slides-chooser-decks",
    )

    private val scratch = tempDir("cp-slides-chooser")

    @BeforeTest
    fun swapIn() {
        folders.install()
        decks.install()
    }

    @AfterTest
    fun swapOut() {
        unmockkObject(FileChooser.Companion)
        folders.restore()
        decks.restore()
        scratch.deleteRecursively()
    }

    private fun givenChooserReturns(picked: String?): FakeChooser {
        mockkObject(FileChooser.Companion)
        return FakeChooser(picked).also { every { FileChooser.platformInstance } returns it }
    }

    private fun ComposeUiTest.awaitAnswered(chooser: FakeChooser) =
        waitUntil("the file dialog to have been answered", ANSWER_TIMEOUT_MS) { chooser.answered == 1 }

    @Test
    fun `Select Folder shows the chosen folder's pictures and remembers the folder`() {
        val chosen = pictureFolder()
        val chooser = givenChooserReturns(chosen.absolutePath)

        picturesTab(folder = null) { vm, reports ->
            onNodeWithText(PictureLabel.SELECT_FOLDER).performClick()
            awaitAnswered(chooser)
            waitUntil("the chosen folder's pictures", ANSWER_TIMEOUT_MS) { vm.images.size == 3 }
            // Saved and remembered after the pictures are asked for, a step later on the tab's own
            // dispatcher: waited for too, or a fast load reads the settings before they are written.
            waitUntil("the folder saved and remembered", ANSWER_TIMEOUT_MS) {
                reports.settingsAfterChange?.pictureSettings?.storageDirectory == chosen.absolutePath &&
                    chosen.absolutePath in RecentPictureFolders.folders
            }
        }
    }

    @Test
    fun `a cancelled folder dialog changes nothing`() {
        val chooser = givenChooserReturns(null)

        picturesTab(folder = null) { vm, reports ->
            onNodeWithText(PictureLabel.SELECT_FOLDER).performClick()
            awaitAnswered(chooser)

            assertTrue(vm.images.isEmpty())
            assertEquals(0, reports.settingsChanges)
            assertTrue(RecentPictureFolders.folders.isEmpty())
        }
    }

    @Test
    fun `Select File adds the chosen deck and remembers it`() {
        val deck = pdfDeck(scratch, pages = 2, name = "sermon.pdf")
        val chooser = givenChooserReturns(deck.absolutePath)

        presentationTab { vm, _ ->
            onNodeWithText(SELECT_FILE).performClick()
            awaitAnswered(chooser)
            waitUntil("the chosen deck to be added", ANSWER_TIMEOUT_MS) { vm.presentations.isNotEmpty() }
            // Remembered just after it is added, so waited for rather than read at once.
            waitUntil("the deck remembered", ANSWER_TIMEOUT_MS) { deck.absolutePath in RecentPresentationFiles.files }

            assertEquals(listOf(File(deck.absolutePath)), vm.presentations)
        }
    }

    private companion object {
        const val ANSWER_TIMEOUT_MS = 5_000L

        /** `select_presentation_file_button`, as the tab renders it. */
        const val SELECT_FILE = "Select File"
    }
}
