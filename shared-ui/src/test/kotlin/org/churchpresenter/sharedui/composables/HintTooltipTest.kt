@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test

/** The tooltip a control that is present but unusable explains itself with. */
class HintTooltipTest {

    @Test
    fun `the hint appears once the pointer rests on the control, and not before`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                HintTooltip("Needs a second screen") {
                    Box(Modifier.size(40.dp).testTag("control"))
                }
            }
        }
        onNodeWithText("Needs a second screen").assertDoesNotExist()
        onNodeWithTag("control").performMouseInput { moveTo(center) }
        // TooltipArea shows after its delay, which the test clock runs through rather than waits for.
        mainClock.advanceTimeBy(TOOLTIP_DELAY_MS)
        onNodeWithText("Needs a second screen").assertExists()
    }

    private companion object {
        /** Past TooltipArea's default 500 ms delay. */
        const val TOOLTIP_DELAY_MS = 600L
    }
}
