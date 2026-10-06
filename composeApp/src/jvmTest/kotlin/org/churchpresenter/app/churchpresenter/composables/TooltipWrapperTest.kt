@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

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

class TooltipWrapperTest {

    @Test
    fun `the label shows only once the control is hovered`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TooltipWrapper(tooltip = "Transpose up") {
                    Box(Modifier.testTag("step").size(40.dp))
                }
            }
        }
        onNodeWithText("Transpose up").assertDoesNotExist()

        onNodeWithTag("step").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(600)
        waitForIdle()

        onNodeWithText("Transpose up").assertExists()
    }
}
