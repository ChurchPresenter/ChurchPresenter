package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
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
    fun `an action button takes every color, size and tooltip option`() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme {
                ActionIconButton(
                    onClick = { clicks++ }, tooltipText = "Go live", icon = Icons.Default.Tv, painter = null,
                    enabled = true, containerColor = Color.Red, contentColor = Color.White,
                    disabledContainerColor = Color.Gray, disabledContentColor = Color.DarkGray,
                    buttonSize = 40.dp, iconSize = 20.dp, modifier = Modifier,
                    tooltipContent = { Text("Send it to the screen") },
                )
            }
        }
        onNodeWithContentDescription("Go live").performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun `a tooltip icon button takes every size, tint and color option`() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme {
                TooltipIconButton(
                    painter = ColorPainter(Color.Blue), text = "Refresh", onClick = { clicks++ },
                    modifier = Modifier, enabled = true, iconSize = 18.dp, buttonSize = 30.dp,
                    iconTint = Color.Green, colors = IconButtonDefaults.iconButtonColors(),
                )
            }
        }
        onNodeWithContentDescription("Refresh").performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }

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
