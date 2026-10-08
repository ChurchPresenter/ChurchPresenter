@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.ndi.NdiRuntimeStatus
import org.churchpresenter.omt.OmtRuntimeStatus
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BLANK_OUTPUT_PROFILE_ID
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.screenKey
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Marking a monitor "Don't use" from the Projection tab, and putting it back.
 *
 * The fixture displays are 1280x720 @ 1920,0 (row 0, "Display 1") and 3840x2160 @ 3200,0 (row 1,
 * "Display 2"); the mark is stored against the monitor's key, so the assertions read it by key.
 */
class ProjectionSettingsTabDontUseTest {

    private val firstScreen = screenKey(1920, 0, 1280, 720)

    private fun row(index: Int, x: Int, w: Int, h: Int, profile: String = "p0") = ScreenAssignment(
        targetDisplay = index,
        targetBoundsX = x, targetBoundsY = 0, targetBoundsW = w, targetBoundsH = h,
        activeProfileId = profile,
    )

    /** Both rows on their own monitor, following [profile]. */
    private fun bothRows(profile: String = "p0", unused: List<String> = emptyList()): AppSettings =
        withProfiles().let {
            it.copy(
                projectionSettings = it.projectionSettings.copy(
                    screenAssignments = listOf(row(1, 1920, 1280, 720, profile), row(2, 3200, 3840, 2160, profile)),
                    unusedScreens = unused,
                ),
            )
        }

    /** The first monitor already marked unused, so row 0 drives nothing. */
    private fun firstMarkedUnused(): AppSettings = bothRows().let {
        val proj = it.projectionSettings
        it.copy(
            projectionSettings = proj.copy(
                unusedScreens = listOf(firstScreen),
                screenAssignments = listOf(
                    ScreenAssignment(targetDisplay = Constants.KEY_TARGET_NONE, activeProfileId = "p0"),
                    proj.screenAssignments[1],
                ),
            ),
        )
    }

    private fun ComposeUiTest.menuItem(text: String) = onNode(hasClickAction() and hasTextExactly(text))

    private fun ComposeUiTest.openGrid(ordinal: Int) {
        gridButton(ordinal).performScrollTo().performClick()
        waitForIdle()
    }

    // ── The screen row's profile picker ─────────────────────────────────────────────────────────

    @Test
    fun `Don't use marks the row's monitor unused and sets the row to None`() = projectionTab(bothRows()) { get ->
        openGrid(Grid.profile(row = 0))
        menuItem("Don't use").performClick()
        waitForIdle()

        val proj = get().projectionSettings
        assertEquals(listOf(firstScreen), proj.unusedScreens)
        assertEquals(Constants.KEY_TARGET_NONE, proj.screenAssignments[0].targetDisplay)
        assertEquals(0, proj.screenAssignments[0].targetBoundsW, "nothing still names the monitor")
        assertEquals(2, proj.screenAssignments[1].targetDisplay, "the other row keeps its monitor")
        menuItem("Don't use").assertDoesNotExist()
    }

    @Test
    fun `a row driving no monitor has none to mark, so its picker offers no Don't use`() =
        projectionTab(firstMarkedUnused()) { get ->
            openGrid(Grid.profile(row = 0))
            menuItem("Don't use").assertDoesNotExist()
            menuItem("Foyer").performClick()
            waitForIdle()

            assertEquals("p1", get().projectionSettings.screenAssignments[0].activeProfileId, "the picker still picks")
        }

    // ── The Display menu ────────────────────────────────────────────────────────────────────────

    @Test
    fun `an unused monitor is still listed, marked not used, and picking it puts it back into use`() =
        projectionTab(firstMarkedUnused()) { get ->
            openGrid(Grid.targetDisplay(row = 0))
            menuItem("Display 1 (1280x720 @ 1920,0) — not used").performClick()
            waitForIdle()

            val proj = get().projectionSettings
            assertEquals(emptyList(), proj.unusedScreens, "the mark is cleared")
            assertEquals(1, proj.screenAssignments[0].targetDisplay)
            assertEquals(firstScreen, proj.screenAssignments[0].targetScreenKey)
        }

    @Test
    fun `a monitor in use is listed without the mark`() = projectionTab(firstMarkedUnused()) { _ ->
        openGrid(Grid.targetDisplay(row = 0))
        menuItem("Display 2 (3840x2160 @ 3200,0)").assertExists()
        menuItem("Display 2 (3840x2160 @ 3200,0) — not used").assertDoesNotExist()
    }

    // ── The Key Output menu ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the key menu leaves out an unused monitor and keeps the others' numbers`() =
        projectionTab(firstMarkedUnused()) { get ->
            openGrid(Grid.keyOutput(row = 0))
            menuItem("Display 1 (1280x720 @ 1920,0)").assertDoesNotExist()
            menuItem("Display 1 (1280x720 @ 1920,0) — not used").assertDoesNotExist()
            menuItem("Display 2 (3840x2160 @ 3200,0)").performClick()
            waitForIdle()

            assertEquals(2, get().projectionSettings.screenAssignments[0].keyTargetDisplay)
        }

    // ── The startup reconcile on this tab ───────────────────────────────────────────────────────

    @Test
    fun `new rows are never handed an unused monitor`() {
        val noRows = withProfiles().let {
            it.copy(projectionSettings = it.projectionSettings.copy(unusedScreens = listOf(firstScreen)))
        }
        projectionTab(noRows) { get ->
            waitUntil { get().projectionSettings.screenAssignments.size == 2 }

            assertEquals(
                listOf(2, Constants.KEY_TARGET_NONE),
                get().projectionSettings.screenAssignments.map { it.targetDisplay },
            )
        }
    }

    // ── The network outputs have no monitor ─────────────────────────────────────────────────────

    /** Opens the one picker on Main -- the screen rows follow Foyer -- and asserts it has no Don't use. */
    private fun ComposeUiTest.networkPickerOffersNoDontUse() {
        onNodeWithText("Main").performScrollTo().performClick()
        waitForIdle()
        menuItem("Blank").assertExists()
        menuItem("Don't use").assertDoesNotExist()
        menuItem("Blank").performClick()
        waitForIdle()
    }

    private fun withNetwork(transform: ProjectionSettings.() -> ProjectionSettings): AppSettings =
        bothRows(profile = "p1").let { it.copy(projectionSettings = it.projectionSettings.transform()) }

    @Test
    fun `a Browser Source picker never offers Don't use`() {
        val initial = withNetwork { copy(browserSourceOutputs = listOf(ScreenAssignment(activeProfileId = "p0"))) }
        projectionTab(initial) { get ->
            networkPickerOffersNoDontUse()
            val source = get().projectionSettings.browserSourceOutputs.single()
            assertEquals(BLANK_OUTPUT_PROFILE_ID, source.activeProfileId)
        }
    }

    @Test
    fun `an NDI picker never offers Don't use`() {
        val initial = withNetwork { copy(ndiOutputs = listOf(ScreenAssignment(activeProfileId = "p0"))) }
        val ready = NdiRuntimeStatus.Ready(version = "5.6.0", path = "/usr/lib/libndi.so")
        projectionTab(initial, ndiStatus = ready) { get ->
            networkPickerOffersNoDontUse()
            assertEquals(BLANK_OUTPUT_PROFILE_ID, get().projectionSettings.ndiOutputs.single().activeProfileId)
        }
    }

    @Test
    fun `an OMT picker never offers Don't use`() {
        val initial = withNetwork { copy(omtOutputs = listOf(ScreenAssignment(activeProfileId = "p0"))) }
        val ready = OmtRuntimeStatus.Ready("/app/omt/libomt.dylib", bundled = true)
        projectionTab(initial, omtStatus = ready) { get ->
            networkPickerOffersNoDontUse()
            assertEquals(BLANK_OUTPUT_PROFILE_ID, get().projectionSettings.omtOutputs.single().activeProfileId)
        }
    }
}
