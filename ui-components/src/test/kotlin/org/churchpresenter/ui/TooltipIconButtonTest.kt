package org.churchpresenter.ui

import androidx.compose.foundation.layout.offset
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The hoverable icon button used throughout the app's toolbars — an [IconButton][androidx.compose.material3.IconButton]
 * wrapped in [ConditionalTooltipArea] so its label appears on hover and hides itself once the
 * pointer leaves or the button scrolls off-screen.
 *
 * The hover-driven tooltip is exercised here rather than skipped: [runComposeUiTest] backs its
 * coroutines with a virtual test clock, so [ComposeUiTest.mainClock.advanceTimeBy][androidx.compose.ui.test.MainTestClock]
 * fast-forwards past `TooltipArea`'s internal 500ms hover delay deterministically, without any
 * real wall-clock wait.
 */
@OptIn(ExperimentalTestApi::class)
class TooltipIconButtonTest {

    @Test
    fun `the icon uses the tooltip text as its content description`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TooltipIconButton(painter = ColorPainter(Color.Red), text = "Delete", onClick = { })
            }
        }
        onNodeWithContentDescription("Delete").assertExists("the icon must expose the label for accessibility")
    }

    @Test
    fun `clicking the button invokes onClick`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                TooltipIconButton(painter = ColorPainter(Color.Red), text = "Delete", onClick = { clicked = true })
            }
        }
        onNode(hasClickAction()).performClick()
        assertTrue(clicked, "clicking the button must invoke the caller's onClick")
    }

    @Test
    fun `an enabled button (the default) can be clicked`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TooltipIconButton(painter = ColorPainter(Color.Red), text = "Delete", onClick = { })
            }
        }
        onNode(hasClickAction()).assertIsEnabled()
    }

    @Test
    fun `a disabled button reports itself disabled and ignores clicks`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                TooltipIconButton(
                    painter = ColorPainter(Color.Red),
                    text = "Delete",
                    onClick = { clicked = true },
                    enabled = false,
                )
            }
        }
        val button = onNode(hasClickAction())
        button.assertIsNotEnabled()
        button.performClick()
        assertFalse(clicked, "a disabled button must not invoke onClick")
    }

    @Test
    fun `buttonSize controls the rendered button's size`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TooltipIconButton(
                    painter = ColorPainter(Color.Red),
                    text = "Delete",
                    onClick = { },
                    buttonSize = 80.dp,
                    modifier = Modifier.testTag("btn"),
                )
            }
        }
        val size = onNodeWithTag("btn").fetchSemanticsNode().size
        assertEquals(80, size.width, "the button must be laid out at the requested buttonSize")
        assertEquals(80, size.height, "the button must be laid out at the requested buttonSize")
    }

    @Test
    fun `iconSize controls the rendered icon's size`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TooltipIconButton(
                    painter = ColorPainter(Color.Red),
                    text = "Delete",
                    onClick = { },
                    iconSize = 40.dp,
                    buttonSize = 60.dp,
                )
            }
        }
        val size = onNodeWithContentDescription("Delete", useUnmergedTree = true).fetchSemanticsNode().size
        assertEquals(40, size.width, "the icon image must be laid out at the requested iconSize")
        assertEquals(40, size.height, "the icon image must be laid out at the requested iconSize")
    }

    @Test
    fun `a custom iconTint and colors do not stop the button from rendering and working`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                TooltipIconButton(
                    painter = ColorPainter(Color.Red),
                    text = "Delete",
                    onClick = { clicked = true },
                    iconTint = Color.Blue,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Green),
                )
            }
        }
        onNodeWithContentDescription("Delete").assertExists()
        onNode(hasClickAction()).performClick()
        assertTrue(clicked, "a custom iconTint/colors must not interfere with clicking")
    }

    @Test
    fun `hovering over the button shows its tooltip text`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TooltipIconButton(painter = ColorPainter(Color.Red), text = "Delete song", onClick = { })
            }
        }
        onNodeWithText("Delete song", useUnmergedTree = true).assertDoesNotExist()

        onNode(hasClickAction()).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(600)
        waitForIdle()

        onNodeWithText("Delete song", useUnmergedTree = true).assertExists("the tooltip must appear once hovered")
    }

    @Test
    fun `moving the pointer away hides the tooltip`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TooltipIconButton(painter = ColorPainter(Color.Red), text = "Delete song", onClick = { })
            }
        }
        val button = onNode(hasClickAction())
        button.performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(600)
        waitForIdle()
        onNodeWithText("Delete song", useUnmergedTree = true).assertExists()

        button.performMouseInput { moveTo(Offset(-100f, -100f)) }
        waitForIdle()
        onNodeWithText("Delete song", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the tooltip stays hidden while the button sits right of the visible area`() = runComposeUiTest {
        assertTooltipSuppressedWhenOffscreen(offsetX = 3000.dp)
    }

    @Test
    fun `the tooltip stays hidden while the button sits left of the visible area`() = runComposeUiTest {
        assertTooltipSuppressedWhenOffscreen(offsetX = (-3000).dp)
    }

    @Test
    fun `the tooltip stays hidden while the button sits above the visible area`() = runComposeUiTest {
        assertTooltipSuppressedWhenOffscreen(offsetY = (-3000).dp)
    }

    @Test
    fun `the tooltip stays hidden while the button sits below the visible area`() = runComposeUiTest {
        assertTooltipSuppressedWhenOffscreen(offsetY = 3000.dp)
    }

    private fun ComposeUiTest.assertTooltipSuppressedWhenOffscreen(
        offsetX: Dp = 0.dp,
        offsetY: Dp = 0.dp,
    ) {
        setContent {
            MaterialTheme {
                TooltipIconButton(
                    painter = ColorPainter(Color.Red),
                    text = "Delete song",
                    onClick = { },
                    modifier = Modifier.offset(x = offsetX, y = offsetY),
                )
            }
        }
        onNode(hasClickAction()).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(600)
        waitForIdle()
        onNodeWithText("Delete song", useUnmergedTree = true)
            .assertDoesNotExist()
    }
}
