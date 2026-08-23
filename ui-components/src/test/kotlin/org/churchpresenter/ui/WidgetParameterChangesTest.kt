package org.churchpresenter.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The widgets redrawn as their own arguments change underneath them, at one call site.
 *
 * This is the shape a settings tab puts them in and the one the rest of this suite does not: those
 * tests compose a widget in a fixed configuration and then change only the value they are about to
 * assert on. Here a single step counter drives the label, the enabled flag, the size and the
 * optional slots together, so each widget is re-evaluated with genuinely different inputs rather
 * than drawn once.
 */
@OptIn(ExperimentalTestApi::class)
class WidgetParameterChangesTest {

    /** Composes [content] over a step counter, clicks through [steps] of it, then runs [after]. */
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
    fun `an action button changes icon, tooltip and enabled state`() {
        var clicks = 0
        stepping(content = { step ->
            ActionIconButton(
                onClick = { clicks++ },
                tooltipText = if (step < 2) "Add" else "Remove",
                icon = if (step % 2 == 0) Icons.Default.Tv else null,
                enabled = step != 1,
                buttonSize = if (step < 3) 34.dp else 40.dp,
                iconSize = if (step < 3) 16.dp else 20.dp,
            )
        }) {
            onNodeWithContentDescription("Remove").performClick()
        }
        assertEquals(1, clicks, "still the same button after four rounds of changes")
    }

    @Test
    fun `a go live button dims and re-enables`() {
        var clicks = 0
        stepping(content = { step ->
            GoLiveButton(
                onClick = { clicks++ },
                tooltipText = "Go live",
                enabled = step != 2,
                dimmed = step >= 3,
            )
        }) {
            onNodeWithContentDescription("Go live").performClick()
        }
        assertEquals(1, clicks)
    }

    @Test
    fun `a styled text field changes label, placeholder, line mode and enabled state`() {
        stepping(content = { step ->
            StyledTextField(
                value = "steady",
                onValueChange = {},
                label = if (step >= 1) "Server" else "",
                placeholder = if (step >= 2) "host:port" else "",
                enabled = step != 2,
                singleLine = step < 3,
                minLines = if (step < 3) 1 else 2,
            )
        }) {
            onNodeWithText("steady").assertExists()
        }
    }

    @Test
    fun `a settings text field changes label, read-only and error state`() {
        stepping(content = { step ->
            SettingsTextField(
                value = "steady",
                onValueChange = {},
                label = if (step >= 1) "API key" else "",
                enabled = step != 2,
                readOnly = step == 3,
                isError = step >= 3,
                singleLine = step < 4,
            )
        }) {
            onNodeWithText("steady").assertExists()
        }
    }

    @Test
    fun `a dropdown settings field changes value, label and fixed width`() {
        stepping(content = { step ->
            DropdownSettingsField(
                value = if (step < 2) "Georgia" else "Arial",
                options = listOf("Georgia", "Arial"),
                onValueChange = {},
                label = if (step >= 1) "Font" else "",
                width = if (step >= 3) 180.dp else null,
            )
        }) {
            onNodeWithText("Arial").assertExists()
        }
    }

    @Test
    fun `a searchable dropdown changes value, label, icon and clear handler`() {
        var cleared = 0
        stepping(content = { step ->
            SearchableDropdownField(
                value = if (step < 2) "Georgia" else "Arial",
                options = listOf("Georgia", "Arial"),
                onValueChange = {},
                label = if (step >= 1) "Font" else "",
                leadingIcon = if (step >= 2) ({ Text("*") }) else null,
                fillWidth = step >= 3,
                clearOnFocus = step >= 3,
                onClear = if (step >= 3) ({ cleared++ }) else null,
            )
        }) {
            onNodeWithText("Arial").assertExists()
        }
        assertEquals(0, cleared, "nothing was cleared; the handler only had to be accepted")
    }

    @Test
    fun `a slider changes range, enabled state and readout`() {
        stepping(content = { step ->
            SlimSlider(
                value = 40f,
                onValueChange = {},
                valueRange = if (step < 2) 0f..100f else 0f..200f,
                modifier = Modifier.size(160.dp, 24.dp),
                enabled = step != 2,
                trailingLabel = if (step >= 1) "$step%" else null,
                onValueChangeFinished = if (step >= 3) ({ }) else null,
            )
        }) {
            onNodeWithText("4%").assertExists()
        }
    }

    @Test
    fun `a number field changes label and range`() {
        var value = 0
        stepping(content = { step ->
            NumberSettingsTextField(
                label = if (step >= 1) "Font size" else "",
                initialText = 8,
                range = if (step < 2) 1..20 else 1..200,
                onValueChange = { value = it },
            )
        }) {
            retypeNumberField(showing = 8, to = 30)
        }
        assertEquals(30, value, "the widened range is the one in force after the changes")
    }

    @Test
    fun `a text style row changes its four flags and its button size`() {
        val changed = mutableListOf<String>()
        stepping(content = { step ->
            TextStyleButtons(
                bold = step >= 1,
                italic = step >= 2,
                underline = step >= 3,
                shadow = step >= 4,
                onBoldChange = { changed += "bold" },
                onItalicChange = { changed += "italic" },
                onUnderlineChange = { changed += "underline" },
                onShadowChange = { changed += "shadow" },
                buttonSize = if (step < 3) 28.dp else 32.dp,
            )
        }) {
            onNodeWithText("B").performClick()
        }
        assertEquals(listOf("bold"), changed)
    }

    @Test
    fun `a colour field changes its colour and its label`() {
        stepping(content = { step ->
            ColorPickerField(
                color = if (step < 2) "#FFD54F" else "transparent",
                onColorChange = {},
                label = if (step >= 1) "Text color" else "",
            )
        }) {
            onNodeWithText("TEXT COLOR").assertExists()
        }
    }

    @Test
    fun `a settings section changes title, trailing content and expansion`() {
        var expanded = true
        stepping(content = { step ->
            SettingsSection(
                title = if (step < 2) "Word" else "Definition",
                headerTrailing = if (step >= 1) ({ Text("trailing") }) else null,
                collapsible = step >= 2,
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) { Text("inside") }
        }) {
            onNodeWithText("Definition").assertExists()
            onNodeWithText("trailing").assertExists()
        }
        assertTrue(expanded, "nothing collapsed it; the callback only had to be wired")
    }

    @Test
    fun `a selection list changes its items and its selection`() {
        var picked = ""
        stepping(content = { step ->
            SelectionList(
                list = if (step < 2) listOf("One", "Two") else listOf("One", "Two", "Three"),
                selectedIndex = step % 2,
                onItemSelected = { picked = it },
            )
        }) {
            onNodeWithText("Three").performClick()
        }
        assertEquals("Three", picked)
    }

    @Test
    fun `an image icon button changes size and enabled state`() {
        var clicks = 0
        stepping(content = { step ->
            ImageIconButton(
                onClick = { clicks++ },
                enabled = step != 2,
                size = if (step < 3) 40.dp else 48.dp,
            ) { Text("img") }
        }) {
            onNodeWithText("img").performClick()
        }
        assertEquals(1, clicks)
    }

    @Test
    fun `error and success buttons change label and enabled state`() {
        var errors = 0
        var successes = 0
        stepping(content = { step ->
            ErrorButton(
                text = if (step < 2) "Disconnect" else "Stop",
                onClick = { errors++ },
                enabled = step != 1,
            )
            SuccessButton(
                text = if (step < 2) "Connect" else "Start",
                onClick = { successes++ },
                enabled = step != 1,
            )
        }) {
            onNodeWithText("Stop").performClick()
            onNodeWithText("Start").performClick()
        }
        assertEquals(1, errors)
        assertEquals(1, successes)
    }
}
