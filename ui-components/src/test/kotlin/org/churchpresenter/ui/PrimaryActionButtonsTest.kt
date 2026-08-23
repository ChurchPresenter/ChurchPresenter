package org.churchpresenter.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The action-row icon button family shared by every tab's primary actions: [ActionIconButton]
 * (the shared building block), and the two callers that skin it — [GoLiveButton] and
 * [AddToScheduleButton]. These two are pinned to a specific size/shape the user has explicitly
 * rejected changing before, so these tests only observe behaviour through the public API; they
 * never touch the composable's own sizing or styling.
 */
@OptIn(ExperimentalTestApi::class)
class PrimaryActionButtonsTest {

    // ── ActionIconButton ───────────────────────────────────────────────────────────────────────

    @Test
    fun `an icon vector is shown with the tooltip text as its content description`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ActionIconButton(onClick = { }, tooltipText = "Go live", icon = Icons.Default.Tv)
            }
        }
        onNodeWithContentDescription("Go live").assertExists("the vector icon must expose the label")
    }

    @Test
    fun `a painter icon is shown with the tooltip text as its content description`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ActionIconButton(onClick = { }, tooltipText = "Add", painter = ColorPainter(Color.Red))
            }
        }
        onNodeWithContentDescription("Add").assertExists("the painter icon must expose the label")
    }

    @Test
    fun `with neither an icon nor a painter, no icon is shown`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ActionIconButton(onClick = { }, tooltipText = "Nothing")
            }
        }
        onNodeWithContentDescription("Nothing", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `clicking the button invokes onClick`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                ActionIconButton(onClick = { clicked = true }, tooltipText = "Go live", icon = Icons.Default.Tv)
            }
        }
        onNode(hasClickAction()).performClick()
        assertTrue(clicked, "clicking the button must invoke the caller's onClick")
    }

    @Test
    fun `an enabled button (the default) can be clicked`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ActionIconButton(onClick = { }, tooltipText = "Go live", icon = Icons.Default.Tv)
            }
        }
        onNode(hasClickAction()).assertIsEnabled()
    }

    @Test
    fun `a disabled button reports itself disabled and ignores clicks`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                ActionIconButton(
                    onClick = { clicked = true },
                    tooltipText = "Go live",
                    icon = Icons.Default.Tv,
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
                ActionIconButton(
                    onClick = { },
                    tooltipText = "Go live",
                    icon = Icons.Default.Tv,
                    buttonSize = 50.dp,
                    modifier = Modifier.testTag("btn"),
                )
            }
        }
        val size = onNodeWithTag("btn").fetchSemanticsNode().size
        assertEquals(50, size.width, "the button must be laid out at the requested buttonSize")
        assertEquals(50, size.height, "the button must be laid out at the requested buttonSize")
    }

    @Test
    fun `iconSize controls the rendered icon's size`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ActionIconButton(
                    onClick = { },
                    tooltipText = "Go live",
                    icon = Icons.Default.Tv,
                    iconSize = 24.dp,
                    buttonSize = 50.dp,
                )
            }
        }
        val size = onNodeWithContentDescription("Go live", useUnmergedTree = true).fetchSemanticsNode().size
        assertEquals(24, size.width, "the icon must be laid out at the requested iconSize")
        assertEquals(24, size.height, "the icon must be laid out at the requested iconSize")
    }

    @Test
    fun `custom colors do not stop the button from rendering and working`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                ActionIconButton(
                    onClick = { clicked = true },
                    tooltipText = "Go live",
                    icon = Icons.Default.Tv,
                    containerColor = Color.Magenta,
                    contentColor = Color.Yellow,
                    disabledContainerColor = Color.Gray,
                    disabledContentColor = Color.DarkGray,
                )
            }
        }
        onNode(hasClickAction()).performClick()
        assertTrue(clicked, "custom colors must not interfere with clicking")
    }

    @Test
    fun `hovering shows the tooltip text by default`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ActionIconButton(onClick = { }, tooltipText = "Go live now", icon = Icons.Default.Tv)
            }
        }
        onNodeWithText("Go live now", useUnmergedTree = true).assertDoesNotExist()

        onNode(hasClickAction()).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(600)
        waitForIdle()

        onNodeWithText("Go live now", useUnmergedTree = true).assertExists("the tooltip must appear once hovered")
    }

    @Test
    fun `hovering shows the custom tooltipContent instead of the fallback text`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ActionIconButton(
                    onClick = { },
                    tooltipText = "Fallback text",
                    icon = Icons.Default.Tv,
                    tooltipContent = { Text("Custom tip") },
                )
            }
        }
        onNode(hasClickAction()).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(600)
        waitForIdle()

        onNodeWithText("Custom tip", useUnmergedTree = true).assertExists("the custom slot must be composed")
        onNodeWithText("Fallback text", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    // ── GoLiveButton ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `GoLiveButton shows the tooltip text as its content description`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                GoLiveButton(onClick = { }, tooltipText = "Go live")
            }
        }
        onNodeWithContentDescription("Go live").assertExists()
    }

    @Test
    fun `GoLiveButton invokes onClick when clicked`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                GoLiveButton(onClick = { clicked = true }, tooltipText = "Go live")
            }
        }
        onNode(hasClickAction()).performClick()
        assertTrue(clicked)
    }

    @Test
    fun `GoLiveButton is enabled by default`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                GoLiveButton(onClick = { }, tooltipText = "Go live")
            }
        }
        onNode(hasClickAction()).assertIsEnabled()
    }

    @Test
    fun `a disabled GoLiveButton reports itself disabled and ignores clicks`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                GoLiveButton(onClick = { clicked = true }, tooltipText = "Go live", enabled = false)
            }
        }
        val button = onNode(hasClickAction())
        button.assertIsNotEnabled()
        button.performClick()
        assertFalse(clicked)
    }

    @Test
    fun `a dimmed GoLiveButton still renders and can still be clicked`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                GoLiveButton(onClick = { clicked = true }, tooltipText = "Go live", dimmed = true)
            }
        }
        onNodeWithContentDescription("Go live").assertExists()
        onNode(hasClickAction()).performClick()
        assertTrue(clicked, "dimmed = true must not stop the button from working")
    }

    // ── AddToScheduleButton ────────────────────────────────────────────────────────────────────

    @Test
    fun `AddToScheduleButton shows the tooltip text as its content description`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                AddToScheduleButton(onClick = { }, tooltipText = "Add to schedule")
            }
        }
        onNodeWithContentDescription("Add to schedule").assertExists()
    }

    @Test
    fun `AddToScheduleButton invokes onClick when clicked`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                AddToScheduleButton(onClick = { clicked = true }, tooltipText = "Add to schedule")
            }
        }
        onNode(hasClickAction()).performClick()
        assertTrue(clicked)
    }

    @Test
    fun `AddToScheduleButton is enabled by default`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                AddToScheduleButton(onClick = { }, tooltipText = "Add to schedule")
            }
        }
        onNode(hasClickAction()).assertIsEnabled()
    }

    @Test
    fun `a disabled AddToScheduleButton reports itself disabled and ignores clicks`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                AddToScheduleButton(onClick = { clicked = true }, tooltipText = "Add to schedule", enabled = false)
            }
        }
        val button = onNode(hasClickAction())
        button.assertIsNotEnabled()
        button.performClick()
        assertFalse(clicked)
    }
}
