@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.calendar.PresetStore
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CompanionSatelliteSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Tabs
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TabPanesWiringTest : MainDesktopComposeHarness() {

    private fun calendar() = File(dir, "calendar").apply { mkdirs() }

    private fun media(): AppSettings =
        showingOnly(Tabs.MEDIA).copy(calendarStorageDirectory = calendar().absolutePath)

    private fun loadedClip() = MediaViewModel().apply {
        loadMediaFromSchedule(File(dir, "clip.mp4").absolutePath, "Clip", Constants.MEDIA_TYPE_LOCAL)
    }

    private fun ComposeUiTest.clickEnabled(description: String) {
        onAllNodes(hasContentDescription(description) and isEnabled())[0].performClick()
        waitForIdle()
    }

    @Test
    fun `a loaded clip is added to the schedule`() {
        val wiring = Wiring()
        root(media(), wiring = wiring, media = loadedClip()) { _ ->
            clickEnabled("Add to Schedule")

            waitUntil { wiring.scheduleChanged.lastOrNull() == 1 }
        }
    }

    @Test
    fun `a loaded clip is saved as a preset`() = root(media(), media = loadedClip()) { _ ->
        clickEnabled("Save preset")
        onNodeWithText("OK").performClick()
        waitForIdle()

        val item = PresetStore(calendar()).load().presets.single().item as ScheduleItem.MediaItem
        assertEquals("Clip", item.mediaTitle)
        assertEquals(Constants.MEDIA_TYPE_LOCAL, item.mediaType)
    }

    @Test
    fun `the crossword sequence opens the crossword tab`() = root(showingOnly(Tabs.BIBLE)) { _ ->
        listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionLeft, Key.DirectionRight).forEach { press(it) }
        onAllNodesWithText("Crossword")[0].performClick()
        waitForIdle()

        waitUntil {
            listOf("Check Answers", "Across").any {
                onAllNodesWithText(it, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun `a companion connection shown in a tab gets its pane`() {
        val settings = showingOnly(Tabs.COMPANION_SURFACE).copy(
            companionSatelliteConnections = listOf(CompanionSatelliteSettings(host = "127.0.0.1", showInTab = true)),
        )
        root(settings) { _ ->
            assertTrue(onAllNodesWithText("Companion", substring = true).fetchSemanticsNodes().isNotEmpty())
        }
    }
}
