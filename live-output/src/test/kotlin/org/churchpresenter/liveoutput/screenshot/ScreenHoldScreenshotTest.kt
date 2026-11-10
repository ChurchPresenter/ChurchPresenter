@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.liveoutput.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.ScreenHold
import org.churchpresenter.liveoutput.preview.LivePreviewPanel
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.screenshot.captureComponent
import kotlin.test.Test

/**
 * A screen's tile while it is held, blanked or locked, and the hold menu its corner button opens, in
 * both themes.
 */
class ScreenHoldScreenshotTest {

    @Composable
    private fun Panel(manager: PresenterManager) {
        Box(Modifier.width(PANEL_WIDTH)) {
            LivePreviewPanel(presenterManager = manager, appSettings = AppSettings())
        }
    }

    private fun manager(hold: ScreenHold? = null, lock: Presenting? = null) = PresenterManager().apply {
        setScreenCount(1)
        setScreenHold(0, hold)
        setScreenLock(0, lock)
    }

    @Test
    fun `a held screen`() = captureComponent(SECTION, "tile_held") { Panel(manager(ScreenHold.HOLD)) }

    @Test
    fun `a blanked screen`() = captureComponent(SECTION, "tile_blank") { Panel(manager(ScreenHold.BLANK)) }

    @Test
    fun `a screen locked to its tab`() = captureComponent(SECTION, "tile_locked") {
        Panel(manager(lock = Presenting.LYRICS))
    }

    @Test
    fun `the menu of a screen nothing holds`() = captureComponent(
        SECTION,
        "menu_open",
        rootIndex = 1,
        drive = { onNodeWithContentDescription(MENU).performClick() },
    ) {
        Panel(manager())
    }

    @Test
    fun `the menu of a held, locked screen`() = captureComponent(
        SECTION,
        "menu_held",
        rootIndex = 1,
        drive = { onNodeWithContentDescription(MENU).performClick() },
    ) {
        Panel(manager(ScreenHold.HOLD, Presenting.LYRICS))
    }

    private companion object {
        const val SECTION = "screenHold"
        const val MENU = "Screen hold options"
        val PANEL_WIDTH = 320.dp
    }
}
