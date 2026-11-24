package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class ControlOptionsTest {

    @Test
    fun `a fully visible control shows its tooltip on hover`() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            MaterialTheme {
                ConditionalTooltipArea(
                    tooltip = { Text("The full name") },
                    modifier = Modifier.testTag("area"),
                    tooltipPlacement = TooltipPlacement.ComponentRect(
                        anchor = Alignment.BottomStart,
                        offset = DpOffset(0.dp, 2.dp),
                    ),
                ) { Box(Modifier.size(40.dp)) }
            }
        }
        onNodeWithTag("area").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
        onNodeWithText("The full name").assertExists()
    }

    @Test
    fun `a click handler survives switching to the other pass`() = runComposeUiTest {
        var finalPass by mutableStateOf(false)
        var clicks = 0
        setContent {
            Box(
                Modifier.size(50.dp).testTag("target").then(
                    if (finalPass) {
                        Modifier.finalPassClickable { clicks++ }
                    } else {
                        Modifier.initialPassClickable { clicks++ }
                    },
                ),
            )
        }
        onNodeWithTag("target").performClick()
        waitForIdle()
        finalPass = true
        waitForIdle()
        onNodeWithTag("target").performClick()
        waitForIdle()
        assertEquals(2, clicks)
    }

    @Test
    fun `a backdrop tooltip shows its text`() = runComposeUiTest {
        setContent { MaterialTheme { BackdropTooltip("Line background") } }
        onNodeWithText("Line background").assertExists()
    }
}
