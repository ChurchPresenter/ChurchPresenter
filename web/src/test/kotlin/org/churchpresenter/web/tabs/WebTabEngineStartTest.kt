package org.churchpresenter.web.tabs

import androidx.compose.ui.test.ExperimentalTestApi
import kotlin.test.Test
import kotlin.test.assertEquals

/** The tab brings the engine up as it first opens, and only where there is an engine to bring up. */
@OptIn(ExperimentalTestApi::class)
class WebTabEngineStartTest {

    @Test
    fun `opening the tab starts the engine once`() {
        var starts = 0
        webTab(startEngine = { starts++ }) { _, _ ->
            waitForIdle()
            assertEquals(1, starts)
        }
    }

    @Test
    fun `a tab with no engine starts nothing`() {
        var starts = 0
        webTab(cefInitialized = false, startEngine = { starts++ }) { _, _ ->
            waitForIdle()
            assertEquals(0, starts)
        }
    }
}
