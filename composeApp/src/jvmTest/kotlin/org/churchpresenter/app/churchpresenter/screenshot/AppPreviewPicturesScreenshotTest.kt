@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import kotlin.test.Test
import org.churchpresenter.ui.screenshot.RENDER_TIMEOUT_MS

class AppPreviewPicturesScreenshotTest {

    @Test
    fun `the pictures tab`() = appPreview("pictures", Tabs.PICTURES) {
        waitUntil("thumbnails decoded", RENDER_TIMEOUT_MS) {
            Snapshot.sendApplyNotifications()
            onAllNodes(hasText("Loading...")).fetchSemanticsNodes(false).isEmpty()
        }
        onAllNodes(hasText("04 Church"))[0].performClick()
        waitForIdle()
        goLive()
    }
}
