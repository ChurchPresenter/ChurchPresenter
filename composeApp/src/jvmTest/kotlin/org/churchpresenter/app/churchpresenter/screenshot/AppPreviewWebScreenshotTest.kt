@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import org.churchpresenter.core.models.tabs.Tabs
import kotlin.test.Test

class AppPreviewWebScreenshotTest {

    @Test
    fun `the web tab`() = appPreview("web", Tabs.WEB)
}
