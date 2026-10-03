@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.app.churchpresenter.utils.ScheduleDensity
import org.churchpresenter.calendar.CueFeed
import org.churchpresenter.calendar.FiredCue
import org.churchpresenter.calendar.model.PlanDrift
import org.churchpresenter.calendar.model.RowClock
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.RowEnd
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.time.LocalTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What a schedule row says: the live row's drift from the plan, how a row runs around its turn,
 * and the second line each kind of row draws -- a cue's included, once it has fired or was skipped.
 */
class ScheduleRowContentTest {

    @AfterTest
    fun forgetFiredCues() {
        CueFeed.clear()
    }

    private fun show(content: @Composable () -> Unit, body: ComposeUiTest.() -> Unit) = runComposeUiTest {
        setContent { MaterialTheme { Column { content() } } }
        waitForIdle()
        body()
    }

    private val song = ScheduleItem.SongItem("s", 12, "Amazing Grace", "")

    private fun liveRow(drift: PlanDrift) = @Composable {
        CompositionLocalProvider(LocalLiveDrift provides drift) {
            ScheduleRowTitleLine(song, isSelected = true, RowTiming(), RowClock(LocalTime.of(9, 45), exact = false))
        }
    }

    @Test
    fun `the live row says how far behind or ahead of plan it is, and is quiet within half a minute`() {
        show(liveRow(PlanDrift(seconds = 220, exact = true))) { onNodeWithText("3:40 behind").assertExists() }
        show(liveRow(PlanDrift(seconds = -90, exact = false))) { onNodeWithText("1:30 ahead").assertExists() }
        show(liveRow(PlanDrift(seconds = 20, exact = true))) { onNodeWithText("on plan").assertExists() }
    }

    @Test
    fun `a row says how it runs around its turn`() {
        show({ ScheduleRowTimingLine(RowTiming(repeats = 0, atEnd = RowEnd.NEXT)) }) {
            onNodeWithText("Loops · then next item").assertExists()
        }
        show({ ScheduleRowTimingLine(RowTiming(repeats = 3, atEnd = RowEnd.BLANK)) }) {
            onNodeWithText("Plays 3× · then blank").assertExists()
        }
        show({ ScheduleRowTimingLine(RowTiming(repeats = 1, atEnd = RowEnd.BLANK)) }) {
            onNodeWithText("then blank").assertExists()
        }
        show({ ScheduleRowTimingLine(RowTiming(repeats = 2)) }) { onNodeWithText("Plays 2×").assertExists() }
    }

    @Test
    fun `a song with no songbook and a lower third that never pauses have no second line`() {
        val lowerThird = ScheduleItem.LowerThirdItem("l", "p", "Pastor", pauseAtFrame = false, pauseDurationMs = 0)
        show({
            ScheduleRowDetailLine(song, ScheduleDensity.NORMAL)
            ScheduleRowDetailLine(lowerThird, ScheduleDensity.NORMAL)
        }) {
            assertTrue(onAllNodesWithText("Pause", substring = true).fetchSemanticsNodes().isEmpty())
        }
        val pausing = lowerThird.copy(pauseAtFrame = true, pauseDurationMs = 1500)
        show({ ScheduleRowDetailLine(pausing, ScheduleDensity.NORMAL) }) {
            onNodeWithText("Pause 1500ms").assertExists()
        }
    }

    @Test
    fun `a cue says when it fired, or that it was skipped because something else was live`() {
        val fired = ScheduleItem.CueItem("c1", CueAction.PROJECT, label = "Welcome")
        val skipped = ScheduleItem.CueItem("c2", CueAction.PROJECT, label = "Offering")
        CueFeed.post(FiredCue(fired, LocalTime.of(9, 45)))
        CueFeed.post(FiredCue(skipped, LocalTime.of(10, 5), skipped = true))
        show({
            ScheduleRowDetailLine(fired, ScheduleDensity.NORMAL)
            ScheduleRowDetailLine(skipped, ScheduleDensity.NORMAL)
        }) {
            onNodeWithText("Fired", substring = true).assertExists()
            onNodeWithText("something else was live", substring = true).assertExists()
        }
    }
}
