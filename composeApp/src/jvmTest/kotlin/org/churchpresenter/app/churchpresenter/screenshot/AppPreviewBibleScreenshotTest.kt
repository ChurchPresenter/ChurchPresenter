@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.core.models.tabs.Tabs
import kotlin.test.Test

class AppPreviewBibleScreenshotTest {

    @Test
    fun `the bible tab`() = appPreview("bible", Tabs.BIBLE) {
        onNodeWithText("In the beginning God created the heaven and the earth.", substring = true).performClick()
        waitForIdle()
        goLive()
    }
}
