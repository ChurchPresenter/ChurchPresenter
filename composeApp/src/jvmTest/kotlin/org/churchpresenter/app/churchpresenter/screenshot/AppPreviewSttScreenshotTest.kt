@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import org.churchpresenter.core.models.tabs.Tabs
import kotlin.test.Test

class AppPreviewSttScreenshotTest {

    /**
     * The live-captions tab.
     *
     * Added because the website had no light/dark pair for it and was falling back to a single
     * hand-captured dark image, which meant a dark screenshot on the site's light theme — the exact
     * mismatch `AppScreenshot.astro` exists to prevent. Every other tab on that page comes from this
     * harness; this one had simply never been captured.
     *
     * `Tabs.CROSSWORD` and `Tabs.COMPANION_SURFACE` are still uncaptured. Nothing uses them yet.
     */
    @Test
    fun `the stt tab`() = appPreview("stt", Tabs.STT)
}
