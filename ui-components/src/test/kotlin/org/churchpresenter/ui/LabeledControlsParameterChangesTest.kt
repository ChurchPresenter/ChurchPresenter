package org.churchpresenter.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test

/**
 * One labelled control, redrawn as each of its parameters changes underneath it.
 *
 * Every other test here composes a control in one fixed configuration. A settings tab does not: a
 * checkbox goes from enabled to disabled as the switch above it flips, its supporting line appears
 * and disappears with the mode, and its colour follows the theme. Driving those from state at a
 * single call site is what exercises the widget's re-evaluation rather than only its first draw.
 */
@OptIn(ExperimentalTestApi::class)
class LabeledControlsParameterChangesTest {

    @Test
    fun `a checkbox follows every parameter as it changes`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                var step by remember { mutableStateOf(0) }
                Column {
                    TextButton(onClick = { step++ }) { Text("next") }
                    LabeledCheckbox(
                        checked = step % 2 == 0,
                        onCheckedChange = {},
                        label = "Show title",
                        modifier = if (step > 1) Modifier else Modifier,
                        enabled = step < 2,
                        style = if (step < 2) MaterialTheme.typography.bodyMedium
                        else MaterialTheme.typography.labelSmall,
                        color = if (step < 2) Color.Unspecified else Color.Red,
                        supporting = if (step >= 1) "and its number" else null,
                        spacing = if (step >= 2) 8.dp else 0.dp,
                        controlAtEnd = step >= 3,
                    )
                }
            }
        }

        onNodeWithText("and its number").assertDoesNotExist()
        repeat(4) {
            onNodeWithText("next").performClick()
            waitForIdle()
        }
        onNodeWithText("Show title").assertExists()
        onNodeWithText("and its number").assertExists()
    }

    @Test
    fun `a switch follows every parameter as it changes`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                var step by remember { mutableStateOf(0) }
                Column {
                    TextButton(onClick = { step++ }) { Text("next") }
                    LabeledSwitch(
                        checked = step % 2 == 0,
                        onCheckedChange = {},
                        label = "Fade in",
                        enabled = step < 2,
                        style = if (step < 2) MaterialTheme.typography.bodyMedium
                        else MaterialTheme.typography.labelSmall,
                        color = if (step < 2) Color.Unspecified else Color.Red,
                        supporting = if (step >= 1) "over 400ms" else null,
                        spacing = if (step >= 2) 8.dp else 0.dp,
                        controlAtEnd = step >= 3,
                    )
                }
            }
        }

        onNodeWithText("over 400ms").assertDoesNotExist()
        repeat(4) {
            onNodeWithText("next").performClick()
            waitForIdle()
        }
        onNodeWithText("over 400ms").assertExists()
    }

    @Test
    fun `a radio button follows every parameter as it changes`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                var step by remember { mutableStateOf(0) }
                Column {
                    TextButton(onClick = { step++ }) { Text("next") }
                    LabeledRadioButton(
                        selected = step % 2 == 0,
                        onClick = {},
                        label = "Full screen",
                        enabled = step < 2,
                        style = if (step < 2) MaterialTheme.typography.bodyMedium
                        else MaterialTheme.typography.labelSmall,
                        color = if (step < 2) Color.Unspecified else Color.Red,
                        supporting = if (step >= 1) "on every output" else null,
                        spacing = if (step >= 2) 8.dp else 0.dp,
                        controlAtEnd = step >= 3,
                    )
                }
            }
        }

        onNodeWithText("on every output").assertDoesNotExist()
        repeat(4) {
            onNodeWithText("next").performClick()
            waitForIdle()
        }
        onNodeWithText("on every output").assertExists()
    }
}
