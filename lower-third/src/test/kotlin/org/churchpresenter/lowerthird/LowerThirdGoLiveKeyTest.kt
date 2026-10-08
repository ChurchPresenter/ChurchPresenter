@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lowerthird

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.churchpresenter.core.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Go Live key on the Lower Third tab: Enter on the tab root fires the chosen preset, as the Go
 * Live button does, and does nothing until a preset can play, or for the preset already on air.
 *
 * The preset is chosen from the schedule rather than by a click on its row, so the keyboard stays on
 * the tab root, where the tab put it on opening.
 */
class LowerThirdGoLiveKeyTest {

    private val welcome = ScheduleItem.LowerThirdItem(
        id = "sched-1",
        presetId = "preset-1",
        presetLabel = "Welcome",
        pauseAtFrame = false,
        pauseDurationMs = 0L,
    )

    private fun ComposeUiTest.waitUntilPlayable() {
        waitUntil("the scheduled preset finished loading", WAIT_TIMEOUT_MS) {
            onAllNodes(hasContentDescription(LowerThirdLabel.GO_LIVE) and isEnabled())
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }

    private fun ComposeUiTest.pressEnter() {
        onRoot().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
    }

    @Test
    fun `enter on the tab root fires the chosen preset`() =
        lowerThirdTab(selectedLowerThirdItem = welcome) { reports ->
            waitUntilPlayable()

            pressEnter()

            assertEquals(listOf("Welcome"), reports.live)
            assertEquals(LOWER_THIRD_LOTTIE, reports.liveJson, "the animation itself, as the button sends")
        }

    @Test
    fun `enter does nothing for the preset already on air`() =
        lowerThirdTab(selectedLowerThirdItem = welcome, liveLowerThirdName = "Welcome") { reports ->
            waitUntilPlayable()

            pressEnter()

            assertTrue(reports.live.isEmpty(), "already on air: ${reports.live}")
        }

    @Test
    fun `enter fires the chosen preset while another is on air`() =
        lowerThirdTab(selectedLowerThirdItem = welcome, liveLowerThirdName = "Speaker Name") { reports ->
            waitUntilPlayable()

            pressEnter()

            assertEquals(listOf("Welcome"), reports.live)
        }

    @Test
    fun `with no preset chosen enter fires nothing`() = lowerThirdTab { reports ->
        pressEnter()

        assertTrue(reports.live.isEmpty(), "nothing can play: ${reports.live}")
    }
}
