@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.signage.tab

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The signage folder panel: where the folder is, how long a picture stays, and deleting a playlist. */
class SignageFolderPanelTest {
    private val world = SignageWorld()

    @AfterTest
    fun cleanUp() = world.close()

    private fun ComposeUiTest.waitForText(text: String, substring: Boolean = false) =
        waitUntil(timeoutMillis = SignageWorld.WAIT_MS) {
            onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }

    private fun ComposeUiTest.deleteDialogFor(index: Int) {
        onAllNodesWithContentDescription("Delete playlist")[index].performClick()
        waitForIdle()
    }

    private fun withPlaylists(desktop: FakeDesktop = FakeDesktop(), block: ComposeUiTest.() -> Unit) {
        world.file("welcome.jpg")
        world.file("Events/flyer.png")
        world.controller.start()
        signageTab(world, desktop) {
            waitForText("Events")
            block()
        }
    }

    @Test
    fun anEmptyFolderSaysSo() {
        world.controller.start()
        signageTab(world) { waitForText("No pictures or videos here yet") }
    }

    @Test
    fun theFolderOpensInTheFileManager() {
        val desktop = FakeDesktop()
        withPlaylists(desktop) {
            onNodeWithText("Open folder").performClick()
            waitUntil(timeoutMillis = SignageWorld.WAIT_MS) { desktop.opened.isNotEmpty() }
            assertEquals(world.folder, desktop.opened.single())
        }
    }

    @Test
    fun anotherFolderCanBeChosen() {
        val other = world.root.resolve("Other")
        val desktop = FakeDesktop(chosen = other)
        withPlaylists(desktop) {
            onNodeWithText("Change…").performClick()
            waitUntil(timeoutMillis = SignageWorld.WAIT_MS) { world.controller.state.value.folder == other }
            assertEquals(world.folder, desktop.chooser.lastPath)
        }
    }

    @Test
    fun cancellingTheChooserKeepsTheFolder() {
        val desktop = FakeDesktop(chosen = null)
        withPlaylists(desktop) {
            onNodeWithText("Change…").performClick()
            waitUntil(timeoutMillis = SignageWorld.WAIT_MS) { desktop.chooser.callCount == 1 }
            waitForIdle()
            assertEquals(world.folder, world.controller.state.value.folder)
        }
    }

    @Test
    fun theSecondsPerPictureAreKept() = withPlaylists {
        onNode(hasSetTextAction() and hasText("10")).performTextReplacement("25")
        waitUntil(timeoutMillis = SignageWorld.WAIT_MS) { world.controller.state.value.settings.pictureSeconds == 25 }
    }

    @Test
    fun aFolderPlaylistGoesToTheTrashAfterAsking() {
        val desktop = FakeDesktop()
        withPlaylists(desktop) {
            deleteDialogFor(1)
            onNodeWithText("Delete Events?").assertExists()
            onNodeWithText("everything in it go to the Trash", substring = true).assertExists()
            onNodeWithText("Cancel").performClick()
            assertEquals(emptyList(), desktop.deletes)

            deleteDialogFor(1)
            onNodeWithText("Delete").performClick()
            waitUntil(timeoutMillis = SignageWorld.WAIT_MS) { desktop.deletes.isNotEmpty() }
            assertEquals(world.folder to "folder:Events", desktop.deletes.single())
            onNodeWithText("Some files could not be deleted", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun theFoldersOwnPlaylistSaysTheFoldersInsideItStay() = withPlaylists {
        deleteDialogFor(0)
        onNodeWithText("the folders inside it stay", substring = true).assertExists()
    }

    @Test
    fun withNoTrashItSaysTheFilesGoForGood() = withPlaylists(FakeDesktop(trash = false)) {
        deleteDialogFor(1)
        onNodeWithText("deleted for good", substring = true).assertExists()
    }

    @Test
    fun filesThatCouldNotBeDeletedAreReported() = withPlaylists(FakeDesktop(deleted = false)) {
        deleteDialogFor(1)
        onNodeWithText("Delete").performClick()
        waitForText("Some files could not be deleted", substring = true)
    }
}
