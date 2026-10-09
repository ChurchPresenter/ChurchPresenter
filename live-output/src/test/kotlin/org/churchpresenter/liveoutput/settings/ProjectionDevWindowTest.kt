@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.liveoutput.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.profiles.retypeNumberField
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The single-monitor development case and the controls only it shows -- how many windows to
 * simulate and each one's size -- plus the output target menu in its DeckLink-conflict dress and
 * the audio device menu dismissed without a pick.
 */
class ProjectionDevWindowTest {

    @Test
    fun `the simulated window count and a simulated window's size are written back`() {
        projectionTab(screens = emptyList()) { get ->
            // The simulated window's own size, shown on its picker -- the only 1920×1080 on the tab.
            onNodeWithText("1920×1080").performScrollTo().performClick()
            waitForIdle()
            onNodeWithText("1024×768", substring = true).performClick()
            waitForIdle()
            val window = get().projectionSettings.getAssignment(0)
            assertEquals(1024 to 768, window.devWindowWidth to window.devWindowHeight)

            retypeNumberField(showing = 1, to = 2)
            waitForIdle()
            assertEquals(2, get().projectionSettings.devWindowCount)
        }
    }

    @Test
    fun `a target in conflict with a DeckLink input still opens its menu, and picks`() = runComposeUiTest {
        val none = DisplayOption(label = "None", targetDisplay = -2, targetType = "screen")
        val port = DisplayOption(label = "DeckLink 1", targetDisplay = 0, targetType = "decklink")
        val picked = mutableListOf<DisplayOption>()
        setContent {
            MaterialTheme {
                OutputTargetDropdown(options = listOf(none, port), current = port, conflict = true) { picked += it }
            }
        }
        onNodeWithText("DeckLink 1").performClick()
        waitForIdle()
        clickOutsidePopup()
        onNodeWithText("DeckLink 1").performClick()
        waitForIdle()
        onNodeWithText("None").performClick()
        waitForIdle()
        assertEquals(listOf(none), picked)
    }

    @Test
    fun `dismissing the audio device menu keeps the device`() {
        projectionTab { get ->
            onNode(hasTestTag(AUDIO_DEVICE_BUTTON_TAG)).performScrollTo().performClick()
            waitForIdle()
            clickOutsidePopup()
            assertEquals("", get().projectionSettings.audioOutputDeviceId, "still the system default")
        }
    }
}
