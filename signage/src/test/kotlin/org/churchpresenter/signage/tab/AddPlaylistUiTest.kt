@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.signage.tab

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.signage.core.MediaKind
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The PLAYLISTS list's Add menu, the Link dialog, and renaming and removing what was added. */
class AddPlaylistUiTest {
    private val state = SignageFolderState(
        playlists = listOf(
            PlaylistSummary("folder:", "Signage", 1, 0),
            PlaylistSummary("link:1", "Weather", kind = SourceKind.WEB, detail = "https://weather.example.org"),
            PlaylistSummary("file:1", "Old flyer", kind = SourceKind.PICTURE, detail = "flyer.jpg", missing = true),
            PlaylistSummary("rotation:1", "Evenings", kind = SourceKind.ROTATION, steps = 1, seconds = 60),
            PlaylistSummary("dir:1", "Lobby", 3, 1, detail = "Folders/Lobby", seconds = 8),
            PlaylistSummary("dir:2", "Old event", detail = "Folders/Gone", missing = true),
        ),
        settings = SignageSettings(
            added = listOf(
                LinkPlaylist("link:1", "Weather", "https://weather.example.org", LinkKind.WEB),
                FilePlaylist("file:1", "Old flyer", "flyer.jpg", MediaKind.IMAGE),
                RotationPlaylist("rotation:1", "Evenings", listOf(RotationStep("link:1", 60))),
                PickedFolderPlaylist("dir:1", "Lobby", "Folders/Lobby", pictureSeconds = 8),
                PickedFolderPlaylist("dir:2", "Old event", "Folders/Gone"),
            ),
        ),
        ready = true,
    )
    private val links = mutableListOf<NewLink>()
    private val files = mutableListOf<String>()
    private val folders = mutableListOf<String>()
    private val linkEdits = mutableListOf<Pair<String, NewLink>>()
    private val fileEdits = mutableListOf<Triple<String, String, String?>>()
    private val folderEdits = mutableListOf<Pair<String, String>>()
    private val removed = mutableListOf<String>()
    private val rotations = mutableListOf<String>()

    private fun panel(
        desktop: FakeDesktop,
        state: SignageFolderState = this.state,
        block: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        val actions = FolderActions(
            desktop = desktop.desktop,
            onAddLink = { links += it },
            onAddFile = { files += it.toString() },
            onAddFolder = { folders += it.toString() },
            onEditLink = { id, link -> linkEdits += id to link },
            onEditFile = { id, name, file -> fileEdits += Triple(id, name, file?.toString()) },
            onEditFolder = { id, name, seconds -> folderEdits += id to "$name $seconds" },
            onLocate = { id -> LOCATED.takeIf { id == "dir:1" } },
            onRemove = { removed += it },
            onAddRotation = { name, steps -> rotations += "add $name ${steps.size}" },
            onEditRotation = { id, name, steps -> rotations += "edit $id $name ${steps.size}" },
        )
        setContent {
            ChurchPresenterTheme(ThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background) { SignageFolderPanel(state, actions, Modifier) }
            }
        }
        block()
    }

    private fun ComposeUiTest.openLinkDialog() {
        onNodeWithText("Add").performClick()
        onNodeWithText("Link…").performClick()
    }

    private fun ComposeUiTest.address() = field(LINK_ADDRESS_TAG)

    /** The dialog's Add button: the panel's own Add menu button comes first. */
    private fun ComposeUiTest.dialogAdd() = onAllNodesWithText("Add").onLast()

    @Test
    fun theAddMenuAddsAPictureOrVideo() = panel(FakeDesktop(chosen = Path("/pictures/welcome.png"))) {
        onNodeWithText("Add").performClick()
        onNodeWithText("Picture or video…").performClick()
        waitUntil { files.isNotEmpty() }
        assertEquals(listOf(Path("/pictures/welcome.png").toString()), files)
    }

    @Test
    fun theFolderEntryAddsTheChosenFolder() {
        val desktop = FakeDesktop(chosen = Path("/pictures/Events"))
        panel(desktop) {
            onNodeWithText("Add").performClick()
            onNodeWithText("Folder…").performClick()
            waitUntil { folders.isNotEmpty() }
        }
        assertEquals(listOf(Path("/pictures/Events").toString()), folders)
        assertEquals("Choose a folder of pictures and videos", desktop.chooser.lastTitle)
    }

    @Test
    fun aStreamIsGuessedAndAddedWithTheSiteAsItsName() = panel(FakeDesktop()) {
        openLinkDialog()
        dialogAdd().assertIsNotEnabled()
        address().performTextInput("https://www.youtube.com/@gracechurch/live")
        onNodeWithText("Video or live stream").assertIsSelected()
        assertTrue(onAllNodesWithText("Smooth motion").fetchSemanticsNodes().isEmpty())
        dialogAdd().assertIsEnabled().performClick()
        val expected = NewLink("youtube.com", "https://www.youtube.com/@gracechurch/live", LinkKind.STREAM)
        assertEquals(listOf(expected), links)
    }

    @Test
    fun aWebPageChosenByHandCanMoveSmoothly() = panel(FakeDesktop()) {
        openLinkDialog()
        address().performTextInput("https://example.org/live.m3u8")
        onNodeWithText("Web page").performClick()
        onNodeWithText("Web page").assertIsSelected()
        onNodeWithText("Smooth motion").performClick()
        field(LINK_NAME_TAG).performTextInput("Ticker")
        dialogAdd().performClick()
        assertEquals(listOf(NewLink("Ticker", "https://example.org/live.m3u8", LinkKind.WEB, 0, smooth = true)), links)
    }

    @Test
    fun aRotationIsAddedFromTheMenuAndEditedFromItsRow() = panel(FakeDesktop()) {
        onNodeWithText("Add").performClick()
        onNodeWithText("Rotation…").performClick()
        // A new rotation offers every entry but a rotation.
        field(ROTATION_NAME_TAG).performTextInput("Mornings")
        onNodeWithText("Add step").performClick()
        onNodeWithText("Save").performClick()
        onNodeWithContentDescription("Edit rotation").performClick()
        onNodeWithText("Edit rotation").assertExists()
        onNodeWithText("Save").performClick()
        assertEquals(listOf("add Mornings 1", "edit rotation:1 Evenings 1"), rotations)
    }

    @Test
    fun cancellingAddsNothing() = panel(FakeDesktop()) {
        openLinkDialog()
        address().performTextInput("https://example.org")
        onNodeWithText("Cancel").performClick()
        assertEquals(emptyList(), links)
    }

    @Test
    fun anAddedPlaylistIsEditedAndRemovedAndAMissingFileSaysSo() = panel(FakeDesktop(chosen = NEW_FILE)) {
        onNodeWithText("File not found: flyer.jpg").assertExists()
        onNodeWithText("https://weather.example.org").assertExists()
        // A link opens the Link dialog, filled in.
        onNodeWithContentDescription("Edit link").performClick()
        address().assertTextContains("https://weather.example.org")
        val name = field(LINK_NAME_TAG)
        name.performTextClearance()
        name.performTextInput("Forecast")
        onNodeWithText("Save").performClick()
        assertEquals(listOf("link:1" to NewLink("Forecast", "https://weather.example.org", LinkKind.WEB)), linkEdits)
        // A file: its name, and another file chosen.
        onNodeWithContentDescription("Edit picture or video").performClick()
        onNodeWithText("flyer.jpg").assertExists()
        onNodeWithText("Change file…").performClick()
        waitUntil { onAllNodesWithText(NEW_FILE.toString()).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Save").performClick()
        assertEquals(
            listOf<Triple<String, String, String?>>(Triple("file:1", "Old flyer", NEW_FILE.toString())),
            fileEdits,
        )
        onAllNodesWithContentDescription("Remove playlist")[2].performClick()
        onNodeWithText("Remove Old flyer?").assertExists()
        onNodeWithText("Remove").performClick()
        assertEquals(listOf("file:1"), removed)
    }

    @Test
    fun aFolderShowsItsPathAndCountsAndAMissingOneSaysSo() = panel(FakeDesktop()) {
        onNodeWithText("Folders/Lobby").assertExists()
        onNodeWithText("3 pictures · 1 videos · 8 s each").assertExists()
        onNodeWithText("Folder not found: Folders/Gone").assertExists()
        // A missing folder has no counts to show: only Signage's and Lobby's.
        assertEquals(2, onAllNodesWithText("pictures ·", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun aFolderIsRenamedAndItsCopyOpens() {
        val desktop = FakeDesktop()
        panel(desktop) {
            onAllNodesWithContentDescription("Edit folder")[1].performClick()
            onNodeWithText("Edit folder").assertExists()
            // In the list and in the dialog.
            onAllNodesWithText("Folders/Lobby").assertCountEquals(2)
            onNodeWithText("Open folder").performClick()
            waitUntil { desktop.opened.isNotEmpty() }
            val name = field(FILE_NAME_TAG)
            name.performTextClearance()
            name.performTextInput("Foyer")
            onNodeWithText("Save").performClick()
        }
        assertEquals(listOf(LOCATED), desktop.opened)
        assertEquals(listOf("dir:1" to "Foyer 8"), folderEdits)
    }

    @Test
    fun aFoldersSecondsPerPictureAreSaved() = panel(FakeDesktop()) {
        onAllNodesWithContentDescription("Edit folder")[1].performClick()
        onNode(hasSetTextAction() and hasText("8")).performTextReplacement("20")
        onNodeWithText("Save").performClick()
        assertEquals(listOf("dir:1" to "Lobby 20"), folderEdits)
    }

    @Test
    fun helpExplainsHowSignageWorks() = panel(FakeDesktop()) {
        onNodeWithText("Help").performClick()
        onNodeWithText("How signage works").assertExists()
        onNodeWithText("Folders that stay up to date").assertExists()
        onNodeWithText("Running the screens from more than one computer").assertExists()
        onNodeWithText("Close").performClick()
        onNodeWithText("How signage works").assertDoesNotExist()
    }

    @Test
    fun aFolderWhoseCopyCannotBeFoundHasNothingToOpen() = panel(FakeDesktop()) {
        onAllNodesWithContentDescription("Edit folder")[2].performClick()
        onNodeWithText("Open folder").assertIsNotEnabled()
        onNodeWithText("Cancel").performClick()
        onNodeWithText("Edit folder").assertDoesNotExist()
        assertEquals(emptyList(), folderEdits)
    }

    @Test
    fun aFileRenamedWithoutChoosingKeepsItsCopy() = panel(FakeDesktop(chosen = null)) {
        onNodeWithContentDescription("Edit picture or video").performClick()
        onNodeWithText("Change file…").performClick()
        waitForIdle()
        onNodeWithText("Save").performClick()
        assertEquals(listOf<Triple<String, String, String?>>(Triple("file:1", "Old flyer", null)), fileEdits)
    }

    @Test
    fun removingACopySaysWhereItGoes() {
        panel(FakeDesktop()) {
            onAllNodesWithContentDescription("Remove playlist")[4].performClick()
            onNodeWithText("Its copy in the signage folder goes to the Trash", substring = true).assertExists()
            onNodeWithText("Cancel").performClick()
            // A link has no copy.
            onAllNodesWithContentDescription("Remove playlist")[1].performClick()
            onNodeWithText("The file or page itself is not touched", substring = true).assertExists()
        }
        panel(FakeDesktop(trash = false)) {
            onAllNodesWithContentDescription("Remove playlist")[4].performClick()
            onNodeWithText("deleted for good", substring = true).assertExists()
        }
    }

    @Test
    fun aCopyRunningAndOneThatFailedAreShown() =
        panel(FakeDesktop(), state.copy(copying = listOf("Easter"), copyFailed = "Advent")) {
            onNodeWithText("Copying Easter into the signage folder…").assertExists()
            onNodeWithText("Could not copy Advent into the signage folder.").assertExists()
        }

    private companion object {
        val LOCATED = Path("/signage/Folders/Lobby")
        val NEW_FILE = Path("/pictures/new.jpg")
    }
}
