@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The key button on the ATEM row: whether going live also drives the switcher's upstream key.
 *
 * It is a setting, not an action -- it writes `atemSettings.goLiveKey` and nothing else -- so the test
 * reads the setting back rather than looking for a command on the wire.
 */
class LowerThirdAtemKeyToggleTest {

    private val label = "Go Live drives ATEM key"

    @Test
    fun `the key button arms the go-live key and a second press disarms it`() =
        lowerThirdTab(atemReachable = true) { reports ->
            // The ATEM row waits for the switcher to have answered once; the button appearing is that.
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
            }

            ltButton(label).performClick()
            waitForIdle()
            assertEquals(true, assertNotNull(reports.settings).atemSettings.goLiveKey)

            ltButton(label).performClick()
            waitForIdle()
            assertEquals(false, assertNotNull(reports.settings).atemSettings.goLiveKey)
        }
}
