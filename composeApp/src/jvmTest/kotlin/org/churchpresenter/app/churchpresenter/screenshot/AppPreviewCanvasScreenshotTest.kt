@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import org.churchpresenter.core.models.tabs.Tabs
import kotlin.test.Test

class AppPreviewCanvasScreenshotTest {

    /**
     * The canvas tab with its scene **live**.
     *
     * Every sibling preview goes live before its shot; this one did not, which is why its output
     * panel rendered as a black rectangle — the audience preview with nothing being presented. That
     * is a fair picture of the app doing nothing and a poor one of the app working, and these shots
     * are exported for the website.
     */
    @Test
    fun `the canvas tab`() = appPreview("canvas", Tabs.CANVAS) {
        goLive()
    }
}
