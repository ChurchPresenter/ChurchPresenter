@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.signage.tab

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The folder panel on its own, its folder and its actions changing between frames as the tab's do:
 * each control acts on the folder showing when it is pressed.
 */
class SignageFolderPanelAloneTest {
    private val first = SignageFolderState(
        folder = Path("/first"),
        playlists = listOf(
            PlaylistSummary("folder:", "Signage", 1, 0),
            PlaylistSummary("folder:Events", "Events", 0, 1),
        ),
        ready = true,
    )
    private val second = first.copy(folder = Path("/second"))

    @Test
    fun eachControlActsOnTheFolderShowingNow() = runComposeUiTest {
        val earlier = FakeDesktop(chosen = Path("/chosen"))
        val later = FakeDesktop(chosen = Path("/chosen"))
        val changes = mutableListOf<String>()
        var state by mutableStateOf(first)
        var actions by mutableStateOf(FolderActions({ changes += "first $it" }, {}, earlier.desktop))
        setContent {
            ChurchPresenterTheme(ThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background) { SignageFolderPanel(state, actions, Modifier) }
            }
        }
        onNodeWithText("Open folder").performClick()
        waitUntil { earlier.opened.isNotEmpty() }
        state = first.copy(settings = first.settings.copy(pictureSeconds = 12))
        waitForIdle()
        state = second
        actions = FolderActions({ changes += "later $it" }, {}, later.desktop)
        waitForIdle()
        onNodeWithText("Open folder").performClick()
        onNodeWithText("Change…").performClick()
        waitUntil { later.opened.isNotEmpty() && changes.isNotEmpty() }
        onAllNodesWithContentDescription("Delete playlist")[1].performClick()
        state = second.copy(settings = second.settings.copy(pictureSeconds = 15))
        waitForIdle()
        onNodeWithText("Delete").performClick()
        waitUntil { later.deletes.isNotEmpty() }

        assertEquals(listOf(Path("/first")), earlier.opened)
        assertEquals(listOf(Path("/second")), later.opened)
        assertEquals(listOf("later /chosen"), changes)
        assertEquals(listOf(Path("/second") to "folder:Events"), later.deletes)
    }
}
