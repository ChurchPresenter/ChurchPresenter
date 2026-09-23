@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.ndi.NdiRuntimeStatus
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The NDI Outputs card: the virtual outputs put on the network as NDI sources.
 *
 * The runtime status is pinned by [projectionTab] rather than read from the machine -- the card
 * draws a "get the runtime" prompt when it is missing, and whether it is missing is a property of
 * whoever is running the suite.
 */
class ProjectionSettingsTabNdiTest {

    private fun withNdi(count: Int): AppSettings = withProfiles().let {
        it.copy(
            projectionSettings = it.projectionSettings.copy(
                ndiOutputs = List(count) { ScreenAssignment() },
            ),
        )
    }

    private fun output(get: () -> AppSettings, index: Int = 0): ScreenAssignment =
        get().projectionSettings.ndiOutputs[index]

    /**
     * A runtime the card is satisfied with.
     *
     * Without one the card draws only the prompt to go and get it -- there is nothing to configure
     * until an output can actually be sent -- so every test that wants rows pins this.
     */
    private val ready = NdiRuntimeStatus.Ready(version = "5.6.0", path = "/usr/lib/libndi.so")

    @Test
    fun `with no runtime installed the card offers a way to get one`() {
        projectionTab(ndiStatus = NdiRuntimeStatus.NotInstalled) { _ ->
            onNodeWithText("Get the NDI Runtime").assertExists()
            // "Check again" is on the Camera Capture card too, so it is counted rather than found.
            assertEquals(2, onAllNodesWithText("Check again").fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `an added output renders a full row of controls`() {
        projectionTab(initial = withNdi(1), ndiStatus = ready) { _ ->
            onNodeWithText("Enabled").assertExists()
            onNodeWithText("Resolution").assertExists()
            onNodeWithText("Frame rate").assertExists()
            onNodeWithText("Mode").assertExists()
        }
    }

    @Test
    fun `each added output gets its own row`() {
        projectionTab(initial = withNdi(3), ndiStatus = ready) { _ ->
            onAllNodesWithText("Mode").assertCountEquals(3)
        }
    }

    @Test
    fun `the mode offers alpha, fill only and fill plus key`() {
        projectionTab(initial = withNdi(1), ndiStatus = ready) { _ ->
            // Alpha carries genuine per-pixel transparency, so a lower third arrives already
            // keyed; the other two are for gear that wants discrete signals.
            onAllNodesWithText("Alpha (transparent)").onLast().performScrollTo().performClick()
            waitForIdle()

            assertTrue(onAllNodesWithText("Fill only").fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test
    fun `an NDI output takes a profile like any other output`() {
        projectionTab(initial = withNdi(1), ndiStatus = ready) { get ->
            onAllNodes(hasText("None")).onLast().performScrollTo().performClick()
            waitForIdle()
            onNodeWithText("Foyer").performClick()
            waitForIdle()

            assertEquals("p1", output(get).activeProfileId)
        }
    }

    @Test
    fun `Add Output appends an NDI output`() {
        projectionTab(initial = withNdi(0), ndiStatus = ready) { get ->
            // The NDI card's own Add Output, which is the last of the two on the tab.
            onAllNodesWithText("Add Output").onLast().performScrollTo().performClick()
            waitForIdle()

            assertEquals(1, get().projectionSettings.ndiOutputs.size)
        }
    }

    @Test
    fun `Remove asks before taking an NDI output away`() {
        projectionTab(initial = withNdi(2), ndiStatus = ready) { get ->
            onAllNodesWithText("Remove")[0].performScrollTo().performClick()
            waitForIdle()
            assertEquals(2, get().projectionSettings.ndiOutputs.size, "nothing yet")

            onAllNodesWithText("Remove").onLast().performClick()
            waitForIdle()

            assertEquals(1, get().projectionSettings.ndiOutputs.size)
        }
    }
}
