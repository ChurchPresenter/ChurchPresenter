@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.signage.tab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.dp
import org.churchpresenter.sharedui.screenshot.captureComponent
import org.churchpresenter.signage.core.BlockAction
import org.churchpresenter.signage.core.CecState
import org.churchpresenter.signage.core.ClockTime
import org.churchpresenter.signage.core.Day
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.core.PlayerMessage
import org.churchpresenter.signage.core.ScheduleBlock
import org.churchpresenter.signage.core.WebTestRun
import org.churchpresenter.signage.hub.PairedScreen
import org.churchpresenter.signage.hub.ScreenLink
import kotlin.io.path.Path
import kotlin.test.Test

/**
 * Rotations, schedules and a screen's health, in both themes: a rotation in the list, the Rotation
 * and Schedule dialogs, and screen cards with their health, schedule and web test.
 */
class SignageRotationScreenshotTest {
    private val rotation = RotationPlaylist(
        "rotation:1",
        "Lobby evenings",
        listOf(
            RotationStep("link:1", 60),
            RotationStep("folder:Events"),
            RotationStep("file:1", 15),
            RotationStep("link:2", 120),
        ),
    )
    private val entries = listOf(
        PlaylistSummary("folder:", "Signage", 6, 1),
        PlaylistSummary("folder:Events", "Events", 4, 0),
        PlaylistSummary("file:1", "Welcome", kind = SourceKind.PICTURE, detail = "welcome-banner.png"),
        PlaylistSummary("link:1", "Weather", kind = SourceKind.WEB, detail = "https://weather.example.org"),
        PlaylistSummary("link:2", "Live", kind = SourceKind.STREAM, detail = "https://youtube.com/@gracechurch/live"),
    )
    private val week = setOf(Day.MON, Day.TUE, Day.WED, Day.THU, Day.FRI)
    private val schedule = listOf(
        ScheduleBlock("a", week, ClockTime.parse("08:00"), ClockTime.parse("17:00"), BlockAction.Play("rotation:1")),
        ScheduleBlock("b", Day.entries.toSet(), ClockTime.parse("22:00"), ClockTime.parse("06:00"), BlockAction.TvOff),
        ScheduleBlock("c", setOf(Day.SUN), ClockTime.parse("09:30"), ClockTime.parse("12:30"), BlockAction.Service),
    )
    private val folder = SignageFolderState(
        folder = Path("/Users/church/Documents/ChurchPresenter/Signage"),
        playlists = entries +
            PlaylistSummary("rotation:1", "Lobby evenings", kind = SourceKind.ROTATION, steps = 4, seconds = 195),
        settings = SignageSettings(screens = mapOf("p" to ScreenPlan("folder:", schedule)), added = listOf(rotation)),
        ready = true,
    )
    private val screen = PairedScreen("p", "Lobby", "h", Platform.LINUX_ARM64, "26.0.32", 1920, 1080, 0)
    private val status = PlayerMessage.Status(
        showingPlaylistId = "folder:", cec = CecState.WORKING,
        cpuPercent = 23, memoryPercent = 61, temperatureC = 54.5, uptimeSeconds = 270_000,
    )
    private val link =
        ScreenLink(
            "192.168.2.78", 0, "v", session = 1,
            features = listOf(Features.WEB, Features.WEB_TEST), status = status,
        )

    @Composable
    private fun Card(link: ScreenLink) = Box(Modifier.width(CARD_WIDTH.dp)) {
        ScreenCard(ScreenRowState(screen, link, folder), ScreenActions({ _, _ -> }, {}, { _, _ -> }, {}))
    }

    @Test
    fun `a rotation in the list`() = captureComponent(SECTION, "rotation_row") {
        Box(Modifier.size(PANEL_WIDTH.dp, PANEL_HEIGHT.dp)) {
            SignageFolderPanel(folder, FolderActions({}, {}, FakeDesktop().desktop), Modifier)
        }
    }

    @Test
    fun `the rotation dialog`() = captureComponent(SECTION, "rotation_dialog", rootIndex = 1) {
        RotationDialog(entries, rotation, { _, _ -> }, {})
    }

    @Test
    fun `the schedule dialog`() = captureComponent(SECTION, "schedule_dialog", rootIndex = 1) {
        ScheduleDialog("Lobby", folder.playlists, schedule, {}, {})
    }

    @Test
    fun `a card with its health, schedule and test`() = captureComponent(SECTION, "card_health") {
        val runs = listOf(WebTestRun(15, 15.0, 30.0), WebTestRun(30, 29.4, 48.0))
        val result = PlayerMessage.WebTestResult(runs, gpu = true)
        Card(link.copy(webTest = result))
    }

    @Test
    fun `a card whose screen is hot and nearly out of memory`() = captureComponent(SECTION, "card_strained") {
        Card(link.copy(status = status.copy(temperatureC = 83.0, memoryPercent = 92, uptimeSeconds = 5_400)))
    }

    private companion object {
        const val SECTION = "signage-rotations"
        const val PANEL_WIDTH = 520
        const val PANEL_HEIGHT = 600
        const val CARD_WIDTH = 560
    }
}
