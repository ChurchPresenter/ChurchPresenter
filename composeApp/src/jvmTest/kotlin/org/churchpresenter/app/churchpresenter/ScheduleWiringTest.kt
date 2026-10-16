@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.click
import org.churchpresenter.canvas.SceneViewModel
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.schedule.SCHEDULE_ROW_CARD_TAG
import org.churchpresenter.settings.AppSettings
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScheduleWiringTest : MainDesktopComposeHarness() {

    private val scenesFile get() = File(System.getProperty("user.home"), ".churchpresenter/scenes.json")

    @BeforeTest
    fun clearScenes() {
        scenesFile.delete()
    }

    @AfterTest
    fun removeScenes() {
        scenesFile.delete()
    }

    private fun ComposeUiTest.clickRow(label: String) {
        onAllNodes(hasText(label, substring = true) and hasAnyAncestor(hasTestTag(SCHEDULE_ROW_CARD_TAG)))[0]
            .performMouseInput { click(Offset(LABEL_START_PX, centerY)) }
        waitForIdle()
    }

    private fun Wiring.applied(base: AppSettings): AppSettings = settingsChanges.fold(base) { s, change -> change(s) }

    @Test
    fun `zooming the schedule in saves the larger zoom`() {
        val wiring = Wiring()
        val base = settings()
        root(base, wiring = wiring) { _ ->
            onAllNodes(hasContentDescription("Zoom In") and isEnabled())[0].performClick()
            waitForIdle()
        }
        assertTrue(wiring.applied(base).scheduleItemZoomPercent > base.scheduleItemZoomPercent)
    }

    @Test
    fun `clicking an announcement row loads it into the announcement settings`() {
        val wiring = Wiring()
        val base = settings()
        root(base, wiring = wiring) { actions ->
            actions.addAnnouncement(
                ScheduleItem.AnnouncementItem(id = "a", text = "Coffee after the service", textColor = "#FF0000"),
            )
            waitForIdle()
            clickRow("Coffee after the service")
        }
        val loaded = wiring.settingsChanges.map { it(base).announcementsSettings }
            .filter { it.text == "Coffee after the service" }
        assertEquals(listOf("#FF0000"), loaded.map { it.textColor })
    }

    @Test
    fun `clicking a ministry row opens no tab and changes no setting`() {
        val wiring = Wiring()
        root(wiring = wiring) { actions ->
            actions.addRow(ScheduleItem.MinistryItem(id = "m", title = "Prayer time"), null)
            actions.addAnnouncement(ScheduleItem.AnnouncementItem(id = "a", text = "Coffee after the service"))
            waitForIdle()
            val tabsBefore = wiring.tabChanges.size
            val settingsBefore = wiring.settingsChanges.size
            clickRow("Prayer time")
            assertEquals(tabsBefore, wiring.tabChanges.size)
            assertEquals(settingsBefore, wiring.settingsChanges.size)
            clickRow("Coffee after the service")
            assertTrue(wiring.settingsChanges.size > settingsBefore, "a row click does reach the screen")
        }
    }

    @Test
    fun `presenting a saved scene puts that scene on the output`() {
        val scene = SceneViewModel().addScene("Opening")
        val presenter = PresenterManager()
        root(presenterManager = presenter) { actions ->
            actions.presentScene(scene.id)
            waitForIdle()
            assertEquals(scene.id, presenter.activeScene.value?.id)
        }
    }

    private companion object {
        const val LABEL_START_PX = 4f
    }
}
