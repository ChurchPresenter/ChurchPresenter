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
 * The playlist panel on its own, its playlists and its actions changing between frames as the tab's
 * do: each control acts with the actions showing when it is pressed.
 */
class SignageFolderPanelAloneTest {
    private val first = SignageFolderState(
        playlists = listOf(
            PlaylistSummary("folder:", "Signage", 1, 0, detail = "/s"),
            PlaylistSummary("folder:Events", "Events", 0, 1, detail = "/s/Events"),
        ),
        ready = true,
    )

    @Test
    fun eachControlActsWithTheActionsShowingNow() = runComposeUiTest {
        val earlier = FakeDesktop(chosen = Path("/earlier"))
        val later = FakeDesktop(chosen = Path("/later"))
        val calls = mutableListOf<String>()
        fun actions(tag: String, desktop: FakeDesktop) = FolderActions(
            desktop = desktop.desktop,
            onAddFolder = { calls += "$tag add $it" },
            onRemove = { calls += "$tag remove $it" },
        )
        var state by mutableStateOf(first)
        var actions by mutableStateOf(actions("first", earlier))
        setContent {
            ChurchPresenterTheme(ThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background) { SignageFolderPanel(state, actions, Modifier) }
            }
        }
        onNodeWithText("Add").performClick()
        onNodeWithText("Folder…").performClick()
        waitUntil { calls.isNotEmpty() }
        state = first.copy(copying = listOf("Easter"))
        actions = actions("later", later)
        waitForIdle()
        onNodeWithText("Add").performClick()
        onNodeWithText("Folder…").performClick()
        onAllNodesWithContentDescription("Remove playlist")[1].performClick()
        state = first.copy(copyFailed = "Easter")
        waitForIdle()
        onNodeWithText("Remove").performClick()
        waitUntil { calls.size == 3 }

        assertEquals(
            listOf("first add ${Path("/earlier")}", "later add ${Path("/later")}", "later remove folder:Events"),
            calls,
        )
    }
}
