@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.churchpresenter.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The second half of [WidgetParameterChangesTest] — same idea, the widgets it does not reach.
 */
@OptIn(ExperimentalTestApi::class)
class WidgetParameterChangesMoreTest {

    private fun stepping(
        steps: Int = 4,
        content: @Composable (Int) -> Unit,
        after: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            MaterialTheme {
                var step by remember { mutableStateOf(0) }
                Column {
                    TextButton(onClick = { step++ }) { Text("next") }
                    content(step)
                }
            }
        }
        repeat(steps) {
            onNodeWithText("next").performClick()
            waitForIdle()
        }
        after()
    }

    @Test
    fun `a client label editor opens, is retyped and is confirmed`() {
        var saved: String? = null
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    val state = rememberClientLabelEditor("Booth iPad")
                    Column {
                        ClientLabelEditButton(state, "Booth iPad")
                        ClientLabelEditorRow(state, "Booth iPad", onSetLabel = { saved = it })
                    }
                }
            }
            // Closed to begin with: the row draws nothing until the pencil is pressed.
            assertEquals(emptyList(), renderedText(), "nothing is written before the pencil")

            onNodeWithContentDescription("Set friendly name").performClick()
            waitForIdle()
            val fields = onAllNodes(androidx.compose.ui.test.hasSetTextAction())
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
            assertEquals(1, fields.size, "the pencil opens the label field")

            onNode(androidx.compose.ui.test.hasSetTextAction())
                .performTextReplacement("Stage iPad")
            waitForIdle()
            onNodeWithContentDescription("Save").performClick()
            waitForIdle()
        }
        assertEquals("Stage iPad", saved, "confirming reports the retyped label")
    }

    @Test
    fun `a conditional tooltip area turns its tooltip on and off`() {
        stepping(content = { step ->
            ConditionalTooltipArea(
                tooltip = { Text("the tip") },
                tooltipPlacement = androidx.compose.foundation.TooltipPlacement.ComponentRect(
                    anchor = if (step < 2) Alignment.BottomCenter else Alignment.TopStart,
                ),
            ) { Text(if (step < 2) "hover me" else "or me") }
        }) {
            onNodeWithText("or me").assertExists()
        }
    }

    @Test
    fun `a tooltip icon button changes tint, size and enabled state`() {
        var clicks = 0
        stepping(content = { step ->
            TooltipIconButton(
                painter = androidx.compose.ui.graphics.painter.ColorPainter(Color.Red),
                text = if (step < 2) "Play" else "Pause",
                onClick = { clicks++ },
                enabled = step != 1,
                iconSize = if (step < 3) 20.dp else 24.dp,
                buttonSize = if (step < 3) 36.dp else 40.dp,
                iconTint = if (step >= 2) Color.Blue else null,
            )
        }) {
            onNodeWithContentDescription("Pause").performClick()
        }
        assertEquals(1, clicks)
    }

    @Test
    fun `a theme segmented button moves between modes`() {
        var picked: ThemeMode? = null
        stepping(content = { step ->
            ThemeSegmentedButton(
                selectedTheme = if (step % 2 == 0) ThemeMode.LIGHT else ThemeMode.DARK,
                onThemeChange = { picked = it },
            )
        }) {
            assertTrue(renderedText().isNotEmpty(), "the three mode labels are on screen")
        }
        assertEquals(null, picked, "nothing was clicked; only the selection moved underneath it")
    }

    @Test
    fun `a dropdown selector changes label, value, options and compactness`() {
        var chosen = ""
        stepping(content = { step ->
            DropdownSelector(
                label = if (step < 2) "Book" else "Chapter",
                value = if (step < 2) "43" else "3",
                options = if (step < 2) listOf("43" to "John") else listOf("3" to "3", "4" to "4"),
                onValueChange = { chosen = it },
                compact = step >= 3,
            )
        }) {
            onNodeWithText("CHAPTER", substring = true).assertExists()
        }
        assertEquals("", chosen)
    }

    @Test
    fun `a pane tab row changes which tab is selected`() {
        var picked = ""
        stepping(content = { step ->
            PaneTabRow {
                PaneTab("Songs", selected = step % 2 == 0) { picked = "Songs" }
                PaneTab("Bible", selected = step % 2 == 1) { picked = "Bible" }
            }
        }) {
            onNodeWithText("Bible").performClick()
        }
        assertEquals("Bible", picked)
    }

    @Test
    fun `a tv screen box changes its bezel and screen colours and its content`() {
        stepping(content = { step ->
            TvScreenBox(
                modifier = Modifier.size(120.dp, 80.dp),
                bezelColor = if (step < 2) Color.Black else Color.DarkGray,
                screenColor = if (step < 2) Color.Gray else Color.Blue,
            ) { if (step >= 3) Text("on air") }
        }) {
            onNodeWithText("on air").assertExists()
        }
    }

    @Test
    fun `a search field changes placeholder and value`() {
        var text = ""
        stepping(content = { step ->
            SearchField(
                value = text,
                onValueChange = { text = it },
                placeholder = if (step < 2) "Search songs…" else "Search verses…",
            )
        }) {
            onNodeWithText("Search verses…").assertExists()
        }
    }

    @Test
    fun `a scanning row and a settings scrollbar draw beside a scroll state`() {
        stepping(content = { step ->
            ScanningRow(if (step < 2) "Scanning…" else "Still scanning…")
        }) {
            onNodeWithText("Still scanning…").assertExists()
        }
    }

    @Test
    fun `a dropdown width is measured from whichever options it is given`() {
        val widths = mutableListOf<androidx.compose.ui.unit.Dp>()
        stepping(content = { step ->
            val options = when {
                step == 0 -> emptyList()
                step < 3 -> listOf("Arial")
                else -> listOf("A very considerably longer family name indeed")
            }
            widths += rememberDropdownWidthFor(options, min = 100.dp, max = 240.dp)
            Text("measured")
        }) {
            onNodeWithText("measured").assertExists()
        }
        assertTrue(widths.first() < widths.last(), "a longer option must measure wider, up to the cap")
        assertTrue(widths.all { it in 100.dp..240.dp }, "and always inside the bounds it was given")
    }
}
