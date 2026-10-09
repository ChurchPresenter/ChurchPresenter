@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.calendar.PresetStore
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Tabs
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Saving a tab's content as a calendar preset from the main screen: the sheet the screen opens
 * itself, what OK writes to the calendar folder, and that Cancel writes nothing.
 */
class MainDesktopSavePresetTest : MainDesktopComposeHarness() {

    private fun calendar() = File(dir, "calendar").apply { mkdirs() }

    private fun announcing(): AppSettings = showingOnly(Tabs.ANNOUNCEMENTS).copy(
        calendarStorageDirectory = calendar().absolutePath,
        announcementsSettings = AnnouncementsSettings(text = "Welcome to church"),
    )

    private fun ComposeUiTest.openSaveSheet() {
        onAllNodesWithContentDescription("Save preset")[0].performClick()
        waitForIdle()
    }

    @Test
    fun `OK saves the announcement as a preset, and a second save knows the name`() = root(announcing()) {
        openSaveSheet()
        onNodeWithText("OK").performClick()
        waitForIdle()
        assertEquals(1, PresetStore(calendar()).load().presets.size)

        openSaveSheet()
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        onNodeWithText("Cancel").assertDoesNotExist()
        assertEquals(1, PresetStore(calendar()).load().presets.size, "Cancel adds nothing")
    }
}
