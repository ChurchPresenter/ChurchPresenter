@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.signage.tab

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
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
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Rotation and Schedule dialogs, and what a screen card says of its health, schedule and test. */
class RotationAndScheduleUiTest {
    private val entries = listOf(
        PlaylistSummary("folder:", "Signage", 2, 0),
        PlaylistSummary("link:1", "Weather", kind = SourceKind.WEB),
        PlaylistSummary("file:1", "Welcome", kind = SourceKind.PICTURE),
    )

    private fun ComposeUiTest.show(content: @Composable () -> Unit) = setContent {
        ChurchPresenterTheme(ThemeMode.LIGHT) { Surface(color = MaterialTheme.colorScheme.background) { content() } }
    }

    @Test
    fun aRotationIsBuiltStepByStepAndSavedOnlyWithANameAndAStep() = runComposeUiTest {
        var saved: Pair<String, List<RotationStep>>? = null
        show { RotationDialog(entries, null, { name, steps -> saved = name to steps }, {}) }
        onNodeWithText("Save").assertIsNotEnabled()
        onAllNodesWithText("Name")[0].performTextInput("Evenings")
        onNodeWithText("Save").assertIsNotEnabled()
        onNodeWithText("Add step").performClick()
        onNodeWithText("Add step").performClick()
        onNodeWithText("Add step").performClick()
        // Three of the first entry, a folder: one round each, no time to give.
        assertEquals(3, onAllNodesWithText("One round of its pictures").fetchSemanticsNodes().size)
        onAllNodesWithContentDescription("Remove step")[2].performClick()
        onAllNodesWithContentDescription("Move down")[0].performClick()
        onAllNodesWithContentDescription("Move up")[1].performClick()
        onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals("Evenings" to listOf(RotationStep("folder:"), RotationStep("folder:")), saved)
    }

    @Test
    fun anEditedRotationKeepsItsStepsAndTimes() = runComposeUiTest {
        var saved: List<RotationStep>? = null
        val rotation = RotationPlaylist("rotation:1", "Days", listOf(RotationStep("link:1", 30), RotationStep("gone")))
        show { RotationDialog(entries, rotation, { _, steps -> saved = steps }, {}) }
        onNodeWithText("Edit rotation").assertExists()
        onNodeWithText("Missing entry").assertExists()
        // The editable fields: the name, then the first step's seconds.
        val seconds = onAllNodes(hasSetTextAction())[1]
        // The number field puts a 0 back in an emptied field, so its text is replaced whole.
        seconds.performTextReplacement("90")
        waitForIdle()
        onNodeWithText("Save").performClick()
        assertEquals(listOf(RotationStep("link:1", 90), RotationStep("gone")), saved)
    }

    @Test
    fun aScheduleTimeIsAddedCheckedAndSaved() = runComposeUiTest {
        var saved: List<ScheduleBlock>? = null
        show { ScheduleDialog("Lobby", entries, emptyList(), { saved = it }, {}) }
        onNodeWithText("Schedule for Lobby").assertExists()
        onNodeWithText("Add a time").performClick()
        // A new time: weekdays, eight to five, the first playlist.
        onNodeWithText(dayName(DayOfWeek.SATURDAY)).performClick()
        // The editable fields: From, then To.
        val to = onAllNodes(hasSetTextAction())[1]
        to.performTextClearance()
        to.performTextInput("25:00")
        onNodeWithText("Save").assertIsNotEnabled()
        to.performTextClearance()
        to.performTextInput("06:00")
        onNodeWithText("Save").assertIsEnabled().performClick()
        val block = saved!!.single()
        assertEquals(setOf(Day.MON, Day.TUE, Day.WED, Day.THU, Day.FRI, Day.SAT), block.days)
        assertEquals(ClockTime.parse("08:00") to ClockTime.parse("06:00"), block.start to block.end)
        assertEquals(BlockAction.Play("folder:"), block.action)
        assertTrue(block.crossesMidnight)
    }

    @Test
    fun aTimeWithNoDayCannotBeSavedAndARemovedTimeIsGone() = runComposeUiTest {
        var saved: List<ScheduleBlock>? = null
        val block =
            ScheduleBlock("b", setOf(Day.SUN), ClockTime.parse("09:00"), ClockTime.parse("12:00"), BlockAction.Service)
        show { ScheduleDialog("Lobby", entries, listOf(block), { saved = it }, {}) }
        onNodeWithText("Service (TV on another input)").assertExists()
        onNodeWithText(dayName(DayOfWeek.SUNDAY)).performClick()
        onNodeWithText("Save").assertIsNotEnabled()
        onNodeWithContentDescription("Remove time").performClick()
        onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals(emptyList(), saved)
    }

    @Test
    fun aCardShowsHealthTheScheduleAndWhatDrewItsTest() = runComposeUiTest {
        val schedule = listOf(
            ScheduleBlock("b", setOf(Day.MON, Day.TUE), ClockTime.parse("08:00"), ClockTime.parse("09:00"), TV_OFF),
        )
        val settings = SignageSettings(screens = mapOf("p" to ScreenPlan(null, schedule)))
        val folder = SignageFolderState(Path("/s"), entries, settings, ready = true)
        val screen = PairedScreen("p", "Lobby", "h", Platform.LINUX_ARM64, "26.0.32", 1920, 1080, 0)
        val status = PlayerMessage.Status(
            cec = CecState.WORKING, cpuPercent = 23, memoryPercent = 61, temperatureC = 54.6, uptimeSeconds = 270_000,
        )
        val result = PlayerMessage.WebTestResult(listOf(WebTestRun(15, 15.0, 30.0)), gpu = false)
        val link =
            ScreenLink(
                "10.0.0.5", 0, "v", session = 1,
                features = listOf(Features.WEB_TEST), status = status, webTest = result,
            )
        val scheduled = mutableListOf<List<ScheduleBlock>>()
        val actions = ScreenActions({ _, _ -> }, {}, { _, _ -> }, {}, onSchedule = { _, blocks -> scheduled += blocks })
        show { ScreenCard(ScreenRowState(screen, link, folder), actions) }
        onNodeWithText("Processor 23% · Memory 61% · 54 °C · up 3 d").assertExists()
        onNodeWithText("drawn in software", substring = true).assertExists()
        onNodeWithText("On a schedule: 2 times a week").assertExists()
        onNodeWithContentDescription("Schedule").performClick()
        onNodeWithText("Save").performClick()
        assertEquals(listOf(schedule), scheduled)
    }

    @Test
    fun uptimeReadsInDaysHoursOrMinutes() = runComposeUiTest {
        show { ScreenHealthLine(PlayerMessage.Status(uptimeSeconds = 7_300, memoryPercent = 95)) }
        onNodeWithText("Memory 95% · up 2 h").assertExists()
    }

    @Test
    fun aShortUptimeIsInMinutesAndNoHealthIsNoLine() = runComposeUiTest {
        show {
            ScreenHealthLine(PlayerMessage.Status(uptimeSeconds = 600))
            ScreenHealthLine(PlayerMessage.Status())
            ScreenHealthLine(null)
        }
        onNodeWithText("up 10 min").assertExists()
        assertEquals(1, onAllNodesWithText("up", substring = true).fetchSemanticsNodes().size)
    }

    private companion object {
        val TV_OFF = BlockAction.TvOff
    }

    private fun dayName(day: DayOfWeek) = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
}
