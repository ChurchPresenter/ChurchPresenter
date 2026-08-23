@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import kotlin.test.Test
import org.churchpresenter.ui.screenshot.RENDER_TIMEOUT_MS

class AppPreviewPresentationScreenshotTest {

    @Test
    fun `the presentation tab`() = appPreview("presentation", Tabs.PRESENTATION) {
        onAllNodes(hasText("Sermon", substring = true))[0].performTouchInput { doubleClick() }
        waitUntil("the deck rasterised", RENDER_TIMEOUT_MS) {
            Snapshot.sendApplyNotifications()
            onAllNodes(hasText("Slide 6")).fetchSemanticsNodes(false).isNotEmpty()
        }
        onAllNodes(hasText("Slide 3"))[0].performClick()
        waitForIdle()
        goLive()
    }
}
