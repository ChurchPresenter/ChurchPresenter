@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The tooltip that only exists while the pointer is over the thing it describes.
 *
 * Every other test here reads a tooltip through the content description its anchor carries, which
 * is what assistive technology sees. The tooltip's own content is a separate composable that is not
 * built until a hover, so nothing was composing it.
 */
@OptIn(ExperimentalTestApi::class)
class TooltipHoverTest {

    @Test
    fun `hovering a tooltip area builds the tooltip`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ConditionalTooltipArea(tooltip = { Text("what this does") }) {
                    Text("hover me")
                }
            }
        }

        onNodeWithText("hover me").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(2_000)
        waitForIdle()

        assertTrue(
            renderedText().any { it.contains("what this does") },
            "the tooltip is built on hover, got ${renderedText()}",
        )
    }
}
