@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import org.churchpresenter.core.models.tabs.Tabs
import kotlin.test.Test

class AppPreviewQAScreenshotTest {

    @Test
    fun `the q and a tab`() = appPreview("qa", Tabs.QA) {
        goLiveOnRow("How do we know the resurrection actually happened?")
    }
}
