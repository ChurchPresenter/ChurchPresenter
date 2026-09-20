@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.calendar.model.CueStatus
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.theme.AppThemeWrapper
import org.churchpresenter.theme.ThemeMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The timing panel's typed fields, its disabled state, and the small cue and copy controls around it. */
class TimingPanelTypedTest {

    /** The panel on its own, over a service starting at [start]; [body] sees the latest draft. */
    private fun withPanel(
        start: String = "10:00",
        enabled: Boolean = true,
        body: ComposeUiTest.(latest: () -> TimingDraft) -> Unit,
    ) {
        var latest = TimingDraft()
        runComposeUiTest {
            setContent {
                AppThemeWrapper(theme = ThemeMode.LIGHT) {
                    CompositionLocalProvider(LocalUse24HourClock provides true) {
                        var draft by remember { mutableStateOf(TimingDraft()) }
                        TimingPanel(
                            draft = draft,
                            serviceStartTime = start,
                            enabled = enabled,
                            onChange = {
                                draft = it
                                latest = it
                            },
                        )
                    }
                }
            }
            waitForIdle()
            body {
                waitForIdle()
                latest
            }
        }
    }

    // Fields, in order: minutes before, the clock, the length, the play count.
    private fun ComposeUiTest.field(index: Int) = onAllNodes(hasSetTextAction())[index]

    @Test
    fun `minutes typed before the start become a clock time, and letters clear it`() = withPanel { latest ->
        field(0).performTextInput("12")
        assertEquals("09:48", latest().startText)

        field(0).performTextInput("x")
        assertEquals("09:48", latest().startText, "digits only, so a letter changes nothing")
    }

    @Test
    fun `a length typed in the field is the run length`() = withPanel { latest ->
        field(2).performTextInput("2:30")

        assertEquals(150, latest().runSeconds())
    }

    @Test
    fun `a play count typed in the field counts only past one`() = withPanel { latest ->
        field(3).performTextInput("3")
        assertEquals(3, latest().repeats)

        onNodeWithText("Once").performClick()
        field(3).performTextInput("1")
        assertEquals(1, latest().repeats, "one is the default, not a count")
        assertEquals("1", latest().repeatsText)
    }

    @Test
    fun `a disabled panel swallows its clicks`() = withPanel(enabled = false) { latest ->
        onNodeWithText("On time").performClick()
        onNodeWithText("Loop").performClick()

        assertEquals(TimingDraft(), latest())
    }

    @Test
    fun `with no readable start there are no offset chips to pick`() = withPanel(start = "soon") { latest ->
        assertTrue(onAllNodesWithText("min before").fetchSemanticsNodes().isEmpty())
        assertTrue(onAllNodesWithText("On time").fetchSemanticsNodes().isEmpty())

        field(0).performTextInput("9:30")
        assertEquals("9:30", latest().startText, "the clock field is the first one left")
    }

    // ── Cue status ──────────────────────────────────────────────────────────────

    private fun statusText(status: CueStatus): List<String> {
        val cue = ScheduleItem.CueItem(id = "c", action = CueAction.BLANK, absoluteTime = "09:45")
        val texts = mutableListOf<String>()
        runComposeUiTest {
            setContent {
                AppThemeWrapper(theme = ThemeMode.LIGHT) {
                    CompositionLocalProvider(LocalUse24HourClock provides true) {
                        CueStatusChip(status = status, cue = cue, startTime = "10:00")
                    }
                }
            }
            waitForIdle()
            listOf("Fired", "Next").forEach { word ->
                if (onAllNodesWithText(word, substring = true).fetchSemanticsNodes().isNotEmpty()) texts += word
            }
        }
        return texts
    }

    @Test
    fun `a cue's chip says fired, next, or nothing`() {
        assertEquals(listOf("Fired"), statusText(CueStatus(fired = true, isNext = false, minutesUntil = -5)))
        assertEquals(listOf("Next"), statusText(CueStatus(fired = false, isNext = true, minutesUntil = 12)))
        assertTrue(statusText(CueStatus(fired = false, isNext = false, minutesUntil = 40)).isEmpty())
    }

    // ── The copy sheet's count ──────────────────────────────────────────────────

    private fun stored(folder: File) = CalendarStore(folder).load().document

    @Test
    fun `the count steps up and down, and monthly says it keeps the weekday`() =
        withCalendar(documentWith(service())) { folder ->
            awaitText("Amazing Grace")
            clickIcon("Copy this service")
            awaitText("Paste on")
            clickInSheet("Weekly", anchor = "Paste on")

            clickIcon("More")
            assertTrue(shows("Creates 5 services"))
            clickIcon("Fewer")
            clickIcon("Fewer")
            assertTrue(shows("Creates 3 services"))
            assertFalse(shows("same weekday"))

            clickInSheet("Monthly", anchor = "Repeat")
            assertTrue(shows("same weekday"))

            clickLast("Create")
            assertEquals(4, stored(folder).services.size, "three copies and the original")
            assertNull(stored(folder).services.firstOrNull { it.date == "2026-09-27" }, "a month on, not a week")
        }
}
