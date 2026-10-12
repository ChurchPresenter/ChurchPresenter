@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.happypath

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import kotlin.test.Test

private const val NOTICE = "Coffee and cake in the hall after the service"

class AnnouncementsHappyPathTest : HappyPathHarness() {

    private fun library(announcements: AnnouncementsSettings = AnnouncementsSettings()) =
        settings(Tabs.ANNOUNCEMENTS).copy(
            announcementsSettings = announcements.copy(animationType = Constants.ANIMATION_NONE),
        )

    /** The countdown panel's own control: it sits left of the announcement's, which share its names. */
    private fun ComposeUiTest.clickTimerControl(description: String) {
        val matcher = hasContentDescription(description) and isEnabled()
        await("the timer's \"$description\" is enabled") {
            onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        val nodes = onAllNodes(matcher)
        val leftmost = nodes.fetchSemanticsNodes().withIndex()
            .filter { it.value.boundsInRoot.width > 0f && it.value.boundsInRoot.left > SCHEDULE_PANEL_RIGHT }
            .minBy { it.value.boundsInRoot.left }
        nodes[leftmost.index].performClick()
        waitForIdle()
    }

    @Test
    fun `typed announcement text goes live`() = happyPath(library()) { app ->
        onAllNodes(hasSetTextAction())[0].performTextReplacement(NOTICE)
        waitForIdle()
        goLive()

        await("the announcement is on air") { app.presenter.isLive(Presenting.ANNOUNCEMENTS) }
        awaitOnOutput(app, NOTICE)
    }

    @Test
    fun `a countdown counts down on the output`() =
        happyPath(library(AnnouncementsSettings(timerMode = Constants.TIMER_MODE_DURATION, timerMinutes = 5))) { app ->
            clickTimerControl("Go Live")
            clickTimerControl("Start")

            await("the countdown is running") {
                app.presenter.announcementsOutput.timerRunning.value &&
                    app.presenter.announcementsOutput.timerRemainingSeconds.value in 1..300
            }
            awaitOnOutput(app, "05:00")
        }

    @Test
    fun `a finished countdown shows its end message`() = happyPath(
        library(
            AnnouncementsSettings(
                timerMode = Constants.TIMER_MODE_DURATION,
                timerSeconds = 1,
                timerExpiredText = "Service is starting",
            ),
        ),
    ) { app ->
        clickTimerControl("Go Live")
        clickTimerControl("Start")

        await("the countdown ran out") { app.presenter.announcementsOutput.timerRemainingSeconds.value == 0 }
        awaitOnOutput(app, "Service is starting")
    }

    @Test
    fun `an announcement added to the schedule becomes a row`() = happyPath(library()) { app ->
        onAllNodes(hasSetTextAction())[0].performTextReplacement(NOTICE)
        waitForIdle()
        clickTabControl("Add to Schedule")

        await("the schedule holds the announcement") { app.schedule().isNotEmpty() }
    }

    private companion object {
        /** The schedule sidebar's right edge at the harness's density; the tab's panels start past it. */
        const val SCHEDULE_PANEL_RIGHT = 300f
    }
}
