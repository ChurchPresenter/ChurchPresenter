package org.churchpresenter.liveoutput.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.ScreenHold
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The screen tile's hold menu, which stands in for the lock toggle on a screen: every item it offers,
 * what each does to the manager, and the badge the tile then wears. The other outputs keep the plain
 * toggle `LivePreviewPanelTest` covers.
 */
@OptIn(ExperimentalTestApi::class)
class ScreenHoldMenuTest {

    private val pm = PresenterManager(showPresenterWindowInitially = false)

    private fun ComposeUiTest.panel(settings: AppSettings = AppSettings()) = setContent {
        MaterialTheme {
            LivePreviewPanel(presenterManager = pm, appSettings = settings)
        }
    }

    /** Opens the tile's menu and presses [item] in it. */
    private fun ComposeUiTest.choose(item: String) {
        onNode(hasClickAction() and hasContentDescription(MENU)).performClick()
        waitForIdle()
        onNodeWithText(item).performClick()
        waitForIdle()
    }

    @Test
    fun `hold freezes the screen, says so on the tile, and release lets it go`() = runComposeUiTest {
        panel()
        onNodeWithText(HELD).assertDoesNotExist()
        choose("Hold (freeze this screen)")
        assertEquals(mapOf(0 to ScreenHold.HOLD), pm.screenHolds.value)
        onNodeWithText(HELD).assertExists()

        choose("Release this screen")
        assertTrue(pm.screenHolds.value.isEmpty())
        onNodeWithText(HELD).assertDoesNotExist()
    }

    @Test
    fun `blank turns the screen black and wears its own badge`() = runComposeUiTest {
        panel()
        choose("Blank (black)")
        assertEquals(mapOf(0 to ScreenHold.BLANK), pm.screenHolds.value)
        onNodeWithText("BLANK · press Clear ×3 to release").assertExists()
    }

    @Test
    fun `the menu locks the screen to its tab and unlocks it`() = runComposeUiTest {
        pm.setPresentingMode(Presenting.LYRICS)
        panel()
        choose("Lock screen to current tab")
        assertEquals(Presenting.LYRICS, pm.screenLocks.value[0])
        onNodeWithText("LOCKED").assertExists()

        choose("Unlock screen")
        assertNull(pm.screenLocks.value[0])
    }

    @Test
    fun `a held screen that is also locked shows the hold, which is what it draws`() = runComposeUiTest {
        pm.setScreenLock(0, Presenting.BIBLE)
        pm.setScreenHold(0, ScreenHold.HOLD)
        panel()
        onNodeWithText(HELD).assertExists()
        onNodeWithText("LOCKED").assertDoesNotExist()
    }

    @Test
    fun `hold all holds every screen, and release all is offered only once one is held`() = runComposeUiTest {
        pm.setScreenCount(2)
        panel()
        onNode(hasClickAction() and hasContentDescription(MENU)).performClick()
        waitForIdle()
        onNodeWithText("Release all holds").assertDoesNotExist()
        onNodeWithText("Hold all screens").performClick()
        waitForIdle()
        assertEquals(mapOf(0 to ScreenHold.HOLD, 1 to ScreenHold.HOLD), pm.screenHolds.value)

        choose("Release all holds")
        assertTrue(pm.screenHolds.value.isEmpty())
    }

    @Test
    fun `a stage monitor has no hold menu`() = runComposeUiTest {
        val settings = AppSettings(
            projectionSettings = ProjectionSettings(
                outputProfiles = listOf(
                    OutputProfile(id = "stage", displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR),
                ),
                screenAssignments = listOf(ScreenAssignment(activeProfileId = "stage")),
            ),
        )
        panel(settings)
        onNodeWithContentDescription(MENU).assertDoesNotExist()
    }

    private companion object {
        const val MENU = "Screen hold options"
        const val HELD = "HELD · press Clear ×3 to release"
    }
}
