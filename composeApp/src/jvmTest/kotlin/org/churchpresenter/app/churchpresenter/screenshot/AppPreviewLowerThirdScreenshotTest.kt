@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performClick
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import kotlin.test.Test

class AppPreviewLowerThirdScreenshotTest {

    @Test
    fun `the lower third tab`() = appPreview("lower_third", Tabs.LOWER_THIRD) {
        onAllNodes(hasText("Guest Speaker", substring = true))[0].performClick()
        // Choosing a preset only starts the Lottie parse. Until it finishes — off the composition
        // thread, so `waitForIdle` returns long before — the preview box holds a spinner and every
        // action in the tab is disabled, and going live from there puts nothing on the output.
        waitUntil("the chosen preset finished loading", 5_000L) {
            onAllNodes(hasContentDescription("Go Live") and isEnabled())
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        goLive()
    }
}
