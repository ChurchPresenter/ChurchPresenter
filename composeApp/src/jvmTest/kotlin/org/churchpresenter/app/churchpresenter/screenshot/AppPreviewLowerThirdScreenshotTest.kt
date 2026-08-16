@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import kotlin.test.Test

class AppPreviewLowerThirdScreenshotTest {

    @Test
    fun `the lower third tab`() = appPreview("lower_third", Tabs.LOWER_THIRD) {
        onAllNodes(hasText("Guest Speaker", substring = true))[0].performClick()
        waitForIdle()
        goLive()
    }
}
