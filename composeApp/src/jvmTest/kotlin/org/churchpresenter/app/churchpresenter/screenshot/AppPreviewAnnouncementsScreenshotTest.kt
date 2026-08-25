@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.core.models.tabs.Tabs
import kotlin.test.Test

class AppPreviewAnnouncementsScreenshotTest {

    @Test
    fun `the announcements tab`() = appPreview("announcements", Tabs.ANNOUNCEMENTS) {
        onAllNodes(hasText("Timer 05:00", substring = true))[0].performClick()
        waitForIdle()
        onAllNodes(hasSetTextAction())[0]
            .performTextReplacement("Christ is risen — He is risen indeed!")
        waitForIdle()
        goLive()
    }
}
