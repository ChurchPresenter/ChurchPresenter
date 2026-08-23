package org.churchpresenter.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The ways out of a widget that are not the happy one: abandoning an edit, a button drawn from a
 * painter rather than an icon, a tooltip a caller supplies itself, and the buttons left entirely at
 * their defaults.
 */
@OptIn(ExperimentalTestApi::class)
class WidgetEdgePathsTest {

    @Test
    fun `abandoning a label edit restores the label and reports nothing`() = runComposeUiTest {
        var saved: String? = null
        setContent {
            MaterialTheme {
                val state = rememberClientLabelEditor("Booth iPad")
                Column {
                    ClientLabelEditButton(state, "Booth iPad")
                    ClientLabelEditorRow(state, "Booth iPad", onSetLabel = { saved = it })
                }
            }
        }

        onNodeWithContentDescription("Set friendly name").performClick()
        waitForIdle()
        onNode(hasSetTextAction()).performTextReplacement("Discarded")
        waitForIdle()

        onNodeWithContentDescription("Cancel").performClick()
        waitForIdle()

        assertEquals(null, saved, "abandoning must report nothing")
        assertEquals(emptyList(), renderedText(), "and must close the row")
    }

    @Test
    fun `the pencil closes the row again when pressed twice`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                val state = rememberClientLabelEditor("Booth iPad")
                Column {
                    ClientLabelEditButton(state, "Booth iPad")
                    ClientLabelEditorRow(state, "Booth iPad", onSetLabel = {})
                }
            }
        }

        onNodeWithContentDescription("Set friendly name").performClick()
        waitForIdle()
        assertEquals(1, editableFieldCount(), "open")

        onNodeWithContentDescription("Set friendly name").performClick()
        waitForIdle()
        assertEquals(0, editableFieldCount(), "and shut again")
    }

    @Test
    fun `renaming the client itself gives the editor the new label to start from`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                var label by remember { mutableStateOf("Booth iPad") }
                val state = rememberClientLabelEditor(label)
                Column {
                    TextButton(onClick = { label = "Stage iPad" }) { Text("rename") }
                    ClientLabelEditButton(state, label)
                    ClientLabelEditorRow(state, label, onSetLabel = {})
                }
            }
        }

        onNodeWithText("rename").performClick()
        waitForIdle()
        onNodeWithContentDescription("Set friendly name").performClick()
        waitForIdle()

        // remember(label) is keyed on the label, so the renamed client gets a fresh editor rather
        // than one still holding the old name.
        onNodeWithText("Stage iPad").assertExists()
    }

    @Test
    fun `an action button drawn from a painter, with its own tooltip and its own colours`() {
        var clicks = 0
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    ActionIconButton(
                        onClick = { clicks++ },
                        tooltipText = "Upload",
                        painter = ColorPainter(Color.Red),
                        containerColor = Color.Green,
                        contentColor = Color.Black,
                        disabledContainerColor = Color.Gray,
                        disabledContentColor = Color.DarkGray,
                        buttonSize = 44.dp,
                        iconSize = 22.dp,
                        modifier = Modifier.size(44.dp),
                        tooltipContent = { Text("a tooltip of the caller's own") },
                    )
                }
            }
            onNodeWithContentDescription("Upload").performClick()
        }
        assertEquals(1, clicks)
    }

    @Test
    fun `a disabled action button does not report a click`() {
        var clicks = 0
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    ActionIconButton(
                        onClick = { clicks++ },
                        tooltipText = "Upload",
                        icon = Icons.Default.Tv,
                        enabled = false,
                    )
                }
            }
            onNodeWithContentDescription("Upload").performClick()
        }
        assertEquals(0, clicks, "disabled means disabled")
    }

    @Test
    fun `go live and add to schedule left entirely at their defaults still click`() {
        var live = 0
        var scheduled = 0
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Column {
                        GoLiveButton(onClick = { live++ }, tooltipText = "Go live")
                        AddToScheduleButton(onClick = { scheduled++ }, tooltipText = "Add to schedule")
                    }
                }
            }
            onNodeWithContentDescription("Go live").performClick()
            onNodeWithContentDescription("Add to schedule").performClick()
        }
        assertEquals(1, live, "enabled, undimmed and unmodified is the shape every tab uses")
        assertEquals(1, scheduled)
    }

    @Test
    fun `a selection list ignores a double click when no double-click handler is wired`() {
        val seen = mutableListOf<Int>()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    SelectionListWithIndex(
                        list = listOf("One", "Two", "Three"),
                        onItemSelected = { i, _ -> seen += i },
                    )
                }
            }
            onNodeWithText("Two").performClick()
            waitForIdle()
            onNodeWithText("Two").performClick()
            waitForIdle()
        }
        assertTrue(seen.all { it == 1 }, "both clicks are plain selections, got $seen")
        assertTrue(seen.isNotEmpty())
    }

    private fun androidx.compose.ui.test.ComposeUiTest.editableFieldCount() =
        onAllNodes(hasSetTextAction()).fetchSemanticsNodes(atLeastOneRootRequired = false).size
}
