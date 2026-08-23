package org.churchpresenter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reusable text field behind every setting in the app's settings dialogs, in both its
 * labeled variant (a boxed field with an uppercase caption) and its bare variant (used inline,
 * with no caption).
 *
 * Every parameter that changes what reaches the screen or what the caller is told is exercised
 * directly here — a label, a placeholder, a trailing icon or supporting text left untested would
 * mean a settings row could silently render blank or stop reporting edits without any test
 * catching it.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsTextFieldTest {

    @Test
    fun `the label is shown, uppercased, above the field`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SettingsTextField(value = "16", onValueChange = { }, label = "Font size")
            }
        }
        onNodeWithText("FONT SIZE", substring = true).assertExists("the label must be shown in uppercase")
        onNodeWithText("16").assertExists("the current value must be shown next to its label")
    }

    @Test
    fun `without a label the field still shows and edits its value`() = runComposeUiTest {
        var current by mutableStateOf("")
        setContent {
            MaterialTheme {
                SettingsTextField(value = current, onValueChange = { current = it })
            }
        }
        onAllNodes(hasSetTextAction())[0].performTextInput("stage")
        onNodeWithText("stage").assertExists("typing into the unlabeled field must reach the caller and redraw")
    }

    @Test
    fun `typing updates the value shown on screen and notifies the caller`() = runComposeUiTest {
        var current by mutableStateOf("")
        setContent {
            MaterialTheme {
                SettingsTextField(value = current, onValueChange = { current = it }, label = "Name")
            }
        }
        onAllNodes(hasSetTextAction())[0].performTextInput("grace")
        onNodeWithText("grace").assertExists("the typed text must be pushed back through onValueChange and redrawn")
    }

    @Test
    fun `the labeled field's placeholder is shown only while the value is empty`() = runComposeUiTest {
        var current by mutableStateOf("")
        setContent {
            MaterialTheme {
                SettingsTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = "Name",
                    placeholder = { Text("Enter a name") },
                )
            }
        }
        onNodeWithText("Enter a name").assertExists("the placeholder must show while the field is empty")
        onAllNodes(hasSetTextAction())[0].performTextInput("grace")
        onNodeWithText("Enter a name").assertDoesNotExist()
    }

    @Test
    fun `the unlabeled field's placeholder is shown only while the value is empty`() = runComposeUiTest {
        var current by mutableStateOf("")
        setContent {
            MaterialTheme {
                SettingsTextField(
                    value = current,
                    onValueChange = { current = it },
                    placeholder = { Text("Search") },
                )
            }
        }
        onNodeWithText("Search").assertExists("the placeholder must show while the field is empty")
        onAllNodes(hasSetTextAction())[0].performTextInput("grace")
        onNodeWithText("Search").assertDoesNotExist()
    }

    @Test
    fun `a trailing icon is rendered beside a labeled field`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SettingsTextField(
                    value = "16",
                    onValueChange = { },
                    label = "Font size",
                    trailingIcon = { Box(Modifier.testTag("trailing")) { Text("pt") } },
                )
            }
        }
        onNodeWithTag("trailing", useUnmergedTree = true)
            .assertExists("the trailing slot must be composed for a labeled field")
    }

    @Test
    fun `a trailing icon is rendered beside an unlabeled field`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SettingsTextField(
                    value = "16",
                    onValueChange = { },
                    trailingIcon = { Box(Modifier.testTag("trailing")) { Text("pt") } },
                )
            }
        }
        onNodeWithTag("trailing", useUnmergedTree = true)
            .assertExists("the trailing slot must be composed for an unlabeled field")
    }

    @Test
    fun `supporting text is rendered below the field`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SettingsTextField(
                    value = "abc",
                    onValueChange = { },
                    label = "Host",
                    supportingText = { Text("Must include a port") },
                )
            }
        }
        onNodeWithText("Must include a port").assertExists("the supporting text slot must be composed")
    }

    @Test
    fun `an error state tints the supporting text with the theme's error color`() = runComposeUiTest {
        var supportingColor: Color? = null
        var errorColor: Color? = null
        setContent {
            MaterialTheme {
                errorColor = MaterialTheme.colorScheme.error
                SettingsTextField(
                    value = "abc",
                    onValueChange = { },
                    isError = true,
                    supportingText = {
                        supportingColor = LocalContentColor.current
                        Text("Invalid host")
                    },
                )
            }
        }
        assertEquals(errorColor, supportingColor, "isError must tint the supporting text with the error color")
    }

    @Test
    fun `a disabled field cannot be edited`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SettingsTextField(value = "abc", onValueChange = { }, label = "Name", enabled = false)
            }
        }
        onNodeWithText("abc").assertIsNotEnabled()
        val editableFields = onAllNodes(hasSetTextAction()).fetchSemanticsNodes(atLeastOneRootRequired = false)
        assertTrue(editableFields.isEmpty(), "a disabled field must not expose an edit action")
    }

    @Test
    fun `a read only field displays its value but blocks edits`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SettingsTextField(value = "abc", onValueChange = { }, label = "Name", readOnly = true)
            }
        }
        onNodeWithText("abc").assertExists("a read-only field must still display its value")
        onNodeWithText("abc").assertIsEnabled()
        val editableFields = onAllNodes(hasSetTextAction()).fetchSemanticsNodes(atLeastOneRootRequired = false)
        assertTrue(editableFields.isEmpty(), "a read-only field must not expose an edit action")
    }

    @Test
    fun `a multi-line field keeps a typed newline`() = runComposeUiTest {
        var current by mutableStateOf("")
        setContent {
            MaterialTheme {
                SettingsTextField(value = current, onValueChange = { current = it }, singleLine = false)
            }
        }
        onAllNodes(hasSetTextAction())[0].performTextInput("a\nb")
        onNodeWithText("a\nb").assertExists("singleLine = false must let a literal newline reach the value")
    }

    @Test
    fun `a password visual transformation masks the displayed text without altering the value`() = runComposeUiTest {
        var current by mutableStateOf("abc")
        setContent {
            MaterialTheme {
                SettingsTextField(
                    value = current,
                    onValueChange = { current = it },
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        }
        val shown = onAllNodes(hasSetTextAction())[0].fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.EditableText)?.text
        assertEquals("•••", shown, "the screen must show masked characters, not the raw value")
        assertEquals("abc", current, "the underlying value passed to the caller must stay unmasked")
    }

    @Test
    fun `the keyboard action wired to the ime action fires on confirm`() = runComposeUiTest {
        var searched = false
        setContent {
            MaterialTheme {
                SettingsTextField(
                    value = "grace",
                    onValueChange = { },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { searched = true }),
                )
            }
        }
        onNodeWithText("grace").performImeAction()
        assertTrue(searched, "confirming the ime action must invoke the matching KeyboardActions callback")
    }

    @Test
    fun `fillWidth expands the field to its modifier's width instead of wrapping to the label`() = runComposeUiTest {
        var wideWidth = 0
        var narrowWidth = 0
        setContent {
            MaterialTheme {
                Box(Modifier.width(300.dp)) {
                    SettingsTextField(
                        value = "",
                        onValueChange = { },
                        label = "AB",
                        modifier = Modifier.testTag("wide").fillMaxWidth(),
                        fillWidth = true,
                    )
                }
            }
        }
        wideWidth = onNodeWithTag("wide").fetchSemanticsNode().size.width

        setContent {
            MaterialTheme {
                Box(Modifier.width(300.dp)) {
                    SettingsTextField(
                        value = "",
                        onValueChange = { },
                        label = "AB",
                        modifier = Modifier.testTag("narrow"),
                    )
                }
            }
        }
        narrowWidth = onNodeWithTag("narrow").fetchSemanticsNode().size.width

        assertTrue(
            wideWidth > narrowWidth * 2,
            "fillWidth = true must expand to the parent's width (wide=$wideWidth) rather than wrap to the " +
                "label's intrinsic width (narrow=$narrowWidth)",
        )
    }

    // ── Focus ───────────────────────────────────────────────────────────────────────────────────

    /**
     * Whether any pixel along the node's top edge is [color], within a tolerance for the blending of
     * a 1dp line.
     *
     * The edge is sampled rather than one chosen pixel: which column of it is pure border depends on
     * the corner radius and on font metrics that differ across the three target platforms, so an
     * exact coordinate would be a fragile way to ask a question about the whole outline.
     */
    private fun ComposeUiTest.topEdgeHas(tag: String, color: Color): Boolean {
        val pixels = onNodeWithTag(tag).captureToImage().toPixelMap()
        return (0 until pixels.width).any { x ->
            val p = pixels[x, 0]
            abs(p.red - color.red) < 0.02f && abs(p.green - color.green) < 0.02f && abs(p.blue - color.blue) < 0.02f
        }
    }

    @Test
    fun `a field's outline switches to the accent while it holds focus`() = runComposeUiTest {
        var accent: Color? = null
        var resting: Color? = null
        setContent {
            MaterialTheme {
                accent = MaterialTheme.colorScheme.primary
                resting = MaterialTheme.colorScheme.outlineVariant
                SettingsTextField(
                    value = "16",
                    onValueChange = { },
                    label = "Font size",
                    modifier = Modifier.testTag("field").width(200.dp),
                )
            }
        }

        assertTrue(topEdgeHas("field", resting!!), "an unfocused field must draw its resting outline")
        assertTrue(!topEdgeHas("field", accent!!), "an unfocused field must not already look focused")

        onNodeWithText("16").performClick()
        waitForIdle()

        // Which of two dozen boxes on a settings screen the next keystroke lands in is otherwise
        // invisible: both variants draw the same 1dp outline in every other state.
        assertTrue(topEdgeHas("field", accent!!), "a focused field must draw its outline in the accent colour")
    }

    @Test
    fun `an invalid field keeps its error outline even while focused`() = runComposeUiTest {
        var error: Color? = null
        setContent {
            MaterialTheme {
                error = MaterialTheme.colorScheme.error
                SettingsTextField(
                    value = "nope",
                    onValueChange = { },
                    label = "Port",
                    isError = true,
                    modifier = Modifier.testTag("field").width(200.dp),
                )
            }
        }

        onNodeWithText("nope").performClick()
        waitForIdle()

        // Focus is transient and the error is the thing that needs fixing, so it outranks it.
        assertTrue(topEdgeHas("field", error!!), "an errored field must keep its error outline when focused")
    }

    @Test
    fun `an unlabeled field takes the focus outline too`() = runComposeUiTest {
        var accent: Color? = null
        setContent {
            MaterialTheme {
                accent = MaterialTheme.colorScheme.primary
                SettingsTextField(
                    value = "abc",
                    onValueChange = { },
                    modifier = Modifier.testTag("bare").width(200.dp),
                )
            }
        }

        assertTrue(!topEdgeHas("bare", accent!!), "the bare variant must start unfocused")

        onNodeWithText("abc").performClick()
        waitForIdle()

        // Both variants share one borderColor, but they are separate BasicTextField call sites and
        // the bare one is what the inline settings rows use.
        assertTrue(topEdgeHas("bare", accent!!), "the bare variant must show focus as well")
    }
}
