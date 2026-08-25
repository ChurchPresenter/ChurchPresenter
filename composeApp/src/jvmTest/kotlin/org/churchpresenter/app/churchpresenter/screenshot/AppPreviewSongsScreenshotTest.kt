@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import org.churchpresenter.core.models.tabs.Tabs
import kotlin.test.Test

class AppPreviewSongsScreenshotTest {

    @Test
    fun `the songs tab`() = appPreview("songs", Tabs.SONGS) {
        onAllNodes(hasText("Amazing Grace"))[0].performClick()
        waitForIdle()
        onAllNodes(hasText("Amazing grace! how sweet the sound", substring = true))[0].performClick()
        waitForIdle()
        goLive()
    }
}
