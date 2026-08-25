@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.core.models.tabs.Tabs
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test

class AppPreviewTimersScreenshotTest {

    private fun timer(name: String, timer: AnnouncementsSettings) =
        appPreview(
            name,
            Tabs.ANNOUNCEMENTS,
            settings = { it.copy(announcementsSettings = timer) },
        ) {
            clickInPanel("Go Live")
            // Clock and count-to-a-time start themselves on go-live, so their button is already
            // "Pause" by now and there is nothing to press.
            runCatching { clickInPanel("Start") }
        }

    private fun base() = AnnouncementsSettings(animationType = Constants.ANIMATION_NONE)

    @Test
    fun `a countdown timer`() = timer(
        "timer_countdown",
        base().copy(timerMode = Constants.TIMER_MODE_DURATION, timerMinutes = 5),
    )

    @Test
    fun `a count-up timer`() = timer(
        "timer_count_up",
        base().copy(timerMode = Constants.TIMER_MODE_COUNT_UP),
    )
}
