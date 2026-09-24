@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The assignment grid: one row per output, what each row's controls write, and the two layouts the
 * tab falls into depending on what displays the machine has.
 *
 * Ported from the `ProjectionSettingsTab*` suites. A row is three controls now -- target display,
 * key output, profile -- because the display mode and the content selection moved onto the profile
 * the third of those picks.
 */
class ProjectionSettingsTabGridTest {

    private fun assignments(n: Int) = withProfiles().copy(
        projectionSettings = ProjectionSettings(
            outputProfiles = withProfiles().projectionSettings.outputProfiles,
            screenAssignments = List(n) { ScreenAssignment() },
        ),
    )

    private fun getAssignments(s: org.churchpresenter.settings.AppSettings) =
        s.projectionSettings.screenAssignments

    // ── Layout ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `two external displays give a row each`() {
        // Two externals beside the primary, so two assignment rows -- the primary is the operator's
        // own screen and never gets one.
        projectionTab(screens = twoExternalScreens()) { _ ->
            gridButtons().assertCountEquals(Grid.gridButtonCount(rows = 2) + trailingButtons())
        }
    }

    @Test
    fun `one external display gives one row`() {
        projectionTab(screens = oneExternalScreen()) { _ ->
            gridButtons().assertCountEquals(Grid.gridButtonCount(rows = 1) + trailingButtons())
        }
    }

    /**
     * The button count follows the pinned VLC probe rather than the machine.
     *
     * Pinning this is the point: the two counts above are exact, so anything that appears or
     * disappears with the host's VLC makes them pass on a developer's machine and fail on CI. Here
     * the card is told there is no VLC and the expectation says so too, and the difference between
     * the two runs is exactly the one dropdown.
     */
    @Test
    fun `a machine without VLC draws one button fewer, and the count knows it`() {
        projectionTab(screens = oneExternalScreen(), vlcInstalled = false) { _ ->
            gridButtons().assertCountEquals(
                Grid.gridButtonCount(rows = 1) + trailingButtons(vlcInstalled = false),
            )
        }
    }

    @Test
    fun `a machine with no external display falls back to a dev window`() {
        projectionTab(screens = noExternalScreens()) { _ ->
            onNodeWithText("Dev Window", substring = true).assertExists()
        }
    }

    @Test
    fun `each external display names itself and its resolution in the target dropdown`() {
        projectionTab(screens = twoExternalScreens()) { _ ->
            onNodeWithText("D1 (1280x720)").assertExists()
        }
    }

    // ── What the row's controls write ───────────────────────────────────────────────────────────

    @Test
    fun `picking a profile writes it onto the assignment`() {
        projectionTab(assignments(1), screens = oneExternalScreen()) { get ->
            chooseFromDropdown(Grid.profile(0), "Foyer")

            assertEquals("p1", getAssignments(get())[0].activeProfileId)
        }
    }

    @Test
    fun `a second row takes its own profile`() {
        projectionTab(assignments(2)) { get ->
            chooseFromDropdown(Grid.profile(0), "Main")
            chooseFromDropdown(Grid.profile(1), "Foyer")

            assertEquals("p0", getAssignments(get())[0].activeProfileId)
            assertEquals("p1", getAssignments(get())[1].activeProfileId, "one row must not write another")
        }
    }

    @Test
    fun `an assignment starts on no profile at all`() {
        projectionTab(assignments(1), screens = oneExternalScreen()) { get ->
            assertNull(
                getAssignments(get())[0].activeProfileId,
                "an unassigned output points at nothing until one is picked",
            )
        }
    }

    /**
     * The grid itself has no add button: its rows come from the displays the machine reports. The
     * "Add Output" below it belongs to the Browser Source card, which does add one.
     */
    @Test
    fun `Add Output adds a browser source, not an assignment row`() {
        projectionTab(assignments(1), screens = oneExternalScreen()) { get ->
            val rows = getAssignments(get()).size
            gridButton(Grid.addBrowserSourceOutput(rows)).performScrollTo().performClick()
            waitForIdle()

            assertEquals(1, get().projectionSettings.browserSourceOutputs.size)
            assertEquals(rows, getAssignments(get()).size, "the assignment grid is untouched")
        }
    }

    @Test
    fun `the tab offers an Identify button`() {
        projectionTab { _ ->
            gridButton(Grid.IDENTIFY).assertExists()
        }
    }

    @Test
    fun `every profile in the document is offered to every output`() {
        projectionTab(assignments(1), screens = oneExternalScreen()) { _ ->
            gridButton(Grid.profile(0)).performScrollTo().performClick()
            waitForIdle()

            onNodeWithText("Main").assertExists()
            onNodeWithText("Foyer").assertExists()
        }
    }

    /**
     * How many labelled buttons follow the grid: Add Output, the NDI card's three with no runtime
     * installed, the audio-device dropdown, the VLC Browse button, and the Camera Capture card's two.
     *
     * The dropdown follows what [projectionTab] **pinned**, not what the machine has. This used to
     * read the global `isVlcAvailable` — which was right while the card read it too, and became a
     * one-off the moment the card started taking it as a parameter: on a developer's machine the two
     * agreed and the count passed, and on CI, which has no VLC, the pinned card drew a dropdown the
     * count was not expecting.
     */
    private fun trailingButtons(vlcInstalled: Boolean = true): Int =
        1 + 3 + (if (vlcInstalled) 1 else 0) + 1 + 2

    @Test
    fun `the grid's ordinals are where Grid says they are`() {
        // Pins the arithmetic the rest of this file addresses controls by: if a row gains or loses
        // a control, this fails first and names the reason rather than every test failing obscurely.
        projectionTab(assignments(2)) { _ ->
            assertTrue(Grid.targetDisplay(1) == Grid.targetDisplay(0) + Grid.CONTROLS_PER_ROW)
            assertEquals(Grid.keyOutput(0), Grid.targetDisplay(0) + 1)
            assertEquals(Grid.profile(0), Grid.targetDisplay(0) + 2)
        }
    }
}
