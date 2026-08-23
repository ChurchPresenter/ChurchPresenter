package org.churchpresenter.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The remaining arms of widgets whose other arms the suite already covers: the three style toggles
 * beside Bold, the font catalog re-measured as the installed list and the kept family change, and a
 * labelled control clicked on its control rather than on its label.
 */
@OptIn(ExperimentalTestApi::class)
class WidgetRemainingPathsTest {

    @Test
    fun `every style toggle reports its own flag`() {
        val changed = mutableListOf<Pair<String, Boolean>>()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    TextStyleButtons(
                        bold = false, italic = true, underline = false, shadow = true,
                        onBoldChange = { changed += "bold" to it },
                        onItalicChange = { changed += "italic" to it },
                        onUnderlineChange = { changed += "underline" to it },
                        onShadowChange = { changed += "shadow" to it },
                    )
                }
            }
            onNodeWithText("B").performClick()
            onNodeWithText("I").performClick()
            onNodeWithText("U").performClick()
            onNodeWithText("S").performClick()
        }
        assertEquals(
            listOf("bold" to true, "italic" to false, "underline" to true, "shadow" to false),
            changed,
            "each toggle must report the flipped value of its own flag, not of the row",
        )
    }

    @Test
    fun `the font catalog is re-measured when the installed list or the kept family changes`() {
        val snapshots = mutableListOf<FontCatalogSnapshot>()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    var step by remember { mutableStateOf(0) }
                    Column {
                        TextButton(onClick = { step++ }) { Text("next") }
                        val families = when {
                            step == 0 -> emptyList()
                            step < 3 -> listOf("Arial", "Georgia")
                            else -> listOf("Arial", "Georgia", "Courier New")
                        }
                        snapshots += rememberFontCatalog(families, keep = if (step >= 2) "Georgia" else "")
                        Text("step $step")
                    }
                }
            }
            repeat(4) {
                onNodeWithText("next").performClick()
                waitForIdle()
            }
            onNodeWithText("step 4").assertExists()
        }
        assertTrue(snapshots.first().faces.isEmpty(), "nothing installed, nothing described")
        assertTrue(snapshots.last().faces.isNotEmpty(), "the widened list is described")
    }

    @Test
    fun `a labelled control is toggled by its control as well as by its label`() {
        val reports = mutableListOf<Boolean>()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    LabeledCheckbox(
                        checked = false,
                        onCheckedChange = { reports += it },
                        label = "Show title",
                        controlModifier = Modifier.size(24.dp),
                        spacing = 6.dp,
                        controlAtEnd = true,
                        color = Color.Blue,
                        supporting = "and its number",
                    )
                }
            }
            onNodeWithText("Show title").performClick()
        }
        assertEquals(listOf(true), reports, "the label reaches the control even when it is drawn last")
    }

    @Test
    fun `a switch and a radio button are toggled with the control drawn last too`() {
        val reports = mutableListOf<String>()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Column {
                        LabeledSwitch(
                            checked = false,
                            onCheckedChange = { reports += "switch" },
                            label = "Fade in",
                            controlModifier = Modifier.size(40.dp, 24.dp),
                            spacing = 6.dp,
                            controlAtEnd = true,
                            color = Color.Blue,
                            supporting = "over 400ms",
                        )
                        LabeledRadioButton(
                            selected = false,
                            onClick = { reports += "radio" },
                            label = "Full screen",
                            controlModifier = Modifier.size(24.dp),
                            spacing = 6.dp,
                            controlAtEnd = true,
                            color = Color.Blue,
                            supporting = "on every output",
                        )
                    }
                }
            }
            onNodeWithText("Fade in").performClick()
            onNodeWithText("Full screen").performClick()
        }
        assertEquals(listOf("switch", "radio"), reports)
    }

    @Test
    fun `the label editor keeps what is typed into it while it is open`() {
        var saved: String? = null
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    val state = rememberClientLabelEditor("Booth iPad")
                    Column {
                        ClientLabelEditButton(state, "Booth iPad")
                        ClientLabelEditorRow(
                            state, "Booth iPad",
                            onSetLabel = { saved = it },
                            modifier = Modifier.size(280.dp, 48.dp),
                        )
                    }
                }
            }
            onNodeWithContentDescription("Set friendly name").performClick()
            waitForIdle()
            onNode(hasSetTextAction()).performTextInput("!")
            waitForIdle()
            val typed = onNode(hasSetTextAction())
                .fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText]
                .text
            assertTrue(typed.contains("!"), "the keystroke reached the field, got \"$typed\"")

            onNodeWithContentDescription("Save").performClick()
            waitForIdle()
            assertEquals(typed, saved, "saving reports exactly what the field held")
        }
    }

    @Test
    fun `a disabled labelled control reports nothing`() {
        val reports = mutableListOf<Boolean>()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Column {
                        LabeledCheckbox(false, { reports += it }, "Checkbox", enabled = false)
                        LabeledSwitch(false, { reports += it }, "Switch", enabled = false)
                        LabeledRadioButton(false, { reports += true }, "Radio", enabled = false)
                    }
                }
            }
            onNodeWithText("Checkbox").performClick()
            onNodeWithText("Switch").performClick()
            onNodeWithText("Radio").performClick()
        }
        assertEquals(emptyList(), reports, "a disabled control must not report anything")
    }
}
