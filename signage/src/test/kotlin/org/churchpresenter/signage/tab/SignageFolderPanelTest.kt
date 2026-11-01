@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.signage.tab

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import kotlin.test.AfterTest
import kotlin.test.Test

/** The playlist panel over a running controller: what it says when empty. */
class SignageFolderPanelTest {
    private val world = SignageWorld(folders = emptyList())

    @AfterTest
    fun cleanUp() = world.close()

    @Test
    fun noPlaylistsSaysSo() {
        world.controller.start()
        signageTab(world) {
            waitUntil(timeoutMillis = SignageWorld.WAIT_MS) {
                onAllNodesWithText("No playlists yet", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }
}
