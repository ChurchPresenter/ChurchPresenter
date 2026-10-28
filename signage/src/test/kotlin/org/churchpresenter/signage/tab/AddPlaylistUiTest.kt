@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.signage.tab

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
        folder = Path("/signage"),
        playlists = listOf(
            PlaylistSummary("folder:", "Signage", 1, 0),
            PlaylistSummary("link:1", "Weather", kind = SourceKind.WEB, detail = "https://weather.example.org"),
            PlaylistSummary("file:1", "Old flyer", kind = SourceKind.PICTURE, detail = "flyer.jpg", missing = true),
            PlaylistSummary("rotation:1", "Evenings", kind = SourceKind.ROTATION, steps = 1, seconds = 60),
        ),
        settings = SignageSettings(
            added = listOf(
                LinkPlaylist("link:1", "Weather", "https://weather.example.org", LinkKind.WEB),
                FilePlaylist("file:1", "Old flyer", "flyer.jpg", MediaKind.IMAGE),
                RotationPlaylist("rotation:1", "Evenings", listOf(RotationStep("link:1", 60))),
            ),
        ),
        ready = true,
    )
    private val links = mutableListOf<NewLink>()
    private val files = mutableListOf<String>()
    private val linkEdits = mutableListOf<Pair<String, NewLink>>()
    private val fileEdits = mutableListOf<Triple<String, String, String>>()
    private val removed = mutableListOf<String>()
    private val rotations = mutableListOf<String>()

    private fun panel(desktop: FakeDesktop, block: ComposeUiTest.() -> Unit) = runComposeUiTest {
        val actions = FolderActions(
            onChangeFolder = {},
            onPictureSeconds = {},
            desktop = desktop.desktop,
            onAddLink = { links += it },
            onAddFile = { files += it.toString() },
            onEditLink = { id, link -> linkEdits += id to link },
            onEditFile = { id, name, file -> fileEdits += Triple(id, name, file.toString()) },
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

    private fun ComposeUiTest.address() = onAllNodesWithText("Address")[0]

    /** The dialog's Add button: the panel's own Add menu button comes first. */
    private fun ComposeUiTest.dialogAdd() = onAllNodesWithText("Add").onLast()

    @Test
    fun theAddMenuAddsAPictureOrVideoAndOpensTheFolder() = panel(FakeDesktop(chosen = Path("/pictures/welcome.png"))) {
        onNodeWithText("Add").performClick()
        onNodeWithText("Picture or video…").performClick()
        waitUntil { files.isNotEmpty() }
        assertEquals(listOf("/pictures/welcome.png"), files)
        onNodeWithText("Add").performClick()
        onNodeWithText("Folder…").performClick()
    }

    @Test
    fun theFolderEntryOpensTheSignageFolder() {
        val desktop = FakeDesktop()
        panel(desktop) {
            onNodeWithText("Add").performClick()
            onNodeWithText("Folder…").performClick()
            waitUntil { desktop.opened.isNotEmpty() }
        }
        assertEquals(listOf(Path("/signage")), desktop.opened)
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
        onAllNodesWithText("Name")[0].performTextInput("Ticker")
        dialogAdd().performClick()
        assertEquals(listOf(NewLink("Ticker", "https://example.org/live.m3u8", LinkKind.WEB, 0, smooth = true)), links)
    }

    @Test
    fun aRotationIsAddedFromTheMenuAndEditedFromItsRow() = panel(FakeDesktop()) {
        onNodeWithText("Add").performClick()
        onNodeWithText("Rotation…").performClick()
        // A new rotation offers every entry but a rotation.
        onAllNodesWithText("Name")[0].performTextInput("Mornings")
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
        val name = onAllNodesWithText("Name")[0]
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
        assertEquals(listOf(Triple("file:1", "Old flyer", NEW_FILE.toString())), fileEdits)
        onAllNodesWithContentDescription("Remove playlist")[1].performClick()
        onNodeWithText("Remove Old flyer?").assertExists()
        onNodeWithText("Remove").performClick()
        assertEquals(listOf("file:1"), removed)
        // A folder is still deleted from disk, not removed from the list.
        assertEquals(1, onAllNodesWithContentDescription("Delete playlist").fetchSemanticsNodes().size)
    }

    private companion object {
        val NEW_FILE = Path("/pictures/new.jpg")
    }
}
