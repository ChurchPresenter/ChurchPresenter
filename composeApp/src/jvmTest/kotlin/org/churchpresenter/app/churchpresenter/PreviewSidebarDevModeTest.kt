@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.controlin.ControlHub
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The dev-mode box, the preview-mode toggle and the sidebar's macro button, drawn closed. */
class PreviewSidebarDevModeTest {

    @Test
    fun `the dev mode box shows its heading and what is put in it`() = runComposeUiTest {
        setContent { MaterialTheme { DevModeBox { Text("inside") } } }
        onNodeWithTag(DEV_MODE_BOX_TAG).assertExists()
        onNodeWithText("inside").assertExists()
    }

    @Test
    fun `the preview toggle offers the opposite of what it shows`() = runComposeUiTest {
        val picked = mutableListOf<Boolean>()
        var on by mutableStateOf(false)
        setContent { MaterialTheme { PreviewModeToggle(on) { picked += it } } }
        onNodeWithTag(PREVIEW_MODE_TOGGLE_TAG).performClick()
        on = true
        waitForIdle()
        onNodeWithTag(PREVIEW_MODE_TOGGLE_TAG).performClick()
        assertEquals(listOf(true, false), picked)
    }

    @Test
    fun `the macros button is drawn with and without a MIDI hub, closed until pressed`() = runComposeUiTest {
        val hub = ControlHub(onMapping = {})
        var withHub by mutableStateOf(false)
        setContent {
            MaterialTheme {
                MacrosButton(
                    appSettings = AppSettings(),
                    showControl = SidebarShowControl(
                        rows = listOf(ScheduleItem.AnnouncementItem("a1", "Welcome")),
                        controlHub = if (withHub) hub else null,
                        devMode = true,
                    ),
                    onSettingsChange = {},
                )
            }
        }
        onNodeWithTag(MACROS_BUTTON_TAG).assertExists()
        withHub = true
        waitForIdle()
        onNodeWithTag(MACROS_BUTTON_TAG).assertExists()
        withHub = false
        waitForIdle()
        onNodeWithTag(MACROS_BUTTON_TAG).assertExists()
        hub.close()
    }

    @Test
    fun `the show control defaults are empty and off`() {
        val control = SidebarShowControl()
        assertTrue(control.rows.isEmpty())
        assertNull(control.controlHub)
        assertEquals(false, control.devMode)
    }
}
