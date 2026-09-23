package org.churchpresenter.theme.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.theme.colorSchemeFor
import org.churchpresenter.theme.elevationPalette
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RaisedControlsParametersTest {

    @Test
    fun `buttons given every parameter still click, and hover and press their surface`() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme(colorScheme = colorSchemeFor(ThemeMode.DARK)) {
                val shape = RoundedCornerShape(4.dp)
                val border = BorderStroke(1.dp, Color.Red)
                val padding = PaddingValues(2.dp)
                Column {
                    RaisedButton(
                        onClick = { clicks++ }, modifier = Modifier.testTag("raised"), enabled = true, shape = shape,
                        colors = ButtonDefaults.buttonColors(), border = border, contentPadding = padding,
                        interactionSource = MutableInteractionSource(),
                    ) { Text("R") }
                    KeyButton(
                        onClick = { clicks++ }, modifier = Modifier, enabled = true, shape = shape,
                        colors = ButtonDefaults.outlinedButtonColors(), border = border, contentPadding = padding,
                        interactionSource = MutableInteractionSource(), fill = elevationPalette().key,
                    ) { Text("K") }
                    GhostButton(
                        onClick = { clicks++ }, modifier = Modifier, enabled = false, shape = shape,
                        colors = ButtonDefaults.textButtonColors(), border = border, contentPadding = padding,
                        interactionSource = MutableInteractionSource(),
                    ) { Text("G") }
                }
            }
        }
        onNodeWithTag("raised").performMouseInput { enter(center) }
        onNodeWithTag("raised").performTouchInput { down(center) }
        waitForIdle()
        onNodeWithTag("raised").performTouchInput { up() }
        onNodeWithText("K").performClick()
        onNodeWithText("G").assertIsNotEnabled()
        assertEquals(2, clicks)
    }

    @Test
    fun `icon keys and toggles given every parameter behave as with their defaults`() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme(colorScheme = colorSchemeFor(ThemeMode.LIGHT)) {
                val shape = RoundedCornerShape(4.dp)
                Column {
                    KeyIconButton(
                        onClick = { clicks++ }, modifier = Modifier, enabled = true, shape = shape,
                        colors = IconButtonDefaults.iconButtonColors(), interactionSource = MutableInteractionSource(),
                    ) { Text("I") }
                    RaisedIconButton(
                        onClick = { clicks++ }, modifier = Modifier, enabled = true, shape = shape,
                        colors = IconButtonDefaults.filledIconButtonColors(), border = null,
                        interactionSource = MutableInteractionSource(),
                    ) { Text("F") }
                    RaisedSwitch(
                        checked = true, onCheckedChange = { clicks++ }, modifier = Modifier.testTag("sw"),
                        enabled = true, interactionSource = MutableInteractionSource(),
                    )
                    RaisedCheckbox(
                        checked = true, onCheckedChange = { clicks++ }, modifier = Modifier.testTag("cb"),
                        enabled = true, colors = CheckboxDefaults.colors(),
                        interactionSource = MutableInteractionSource(),
                    )
                    RaisedRadioButton(
                        selected = false, onClick = { clicks++ }, modifier = Modifier.testTag("rb"),
                        enabled = true, colors = RadioButtonDefaults.colors(),
                        interactionSource = MutableInteractionSource(),
                    )
                    RaisedRadioButton(selected = true, onClick = { clicks++ }, enabled = false)
                    RaisedCheckbox(checked = false, onCheckedChange = null)
                }
            }
        }
        onNodeWithText("I").performClick()
        onNodeWithText("F").performClick()
        onNodeWithTag("sw").performClick()
        onNodeWithTag("cb").performClick()
        onNodeWithTag("rb").performClick()
        assertEquals(5, clicks)
    }

    @Test
    fun `chips and the segment track given every parameter`() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme(colorScheme = colorSchemeFor(ThemeMode.DARK)) {
                val shape = RoundedCornerShape(4.dp)
                Column {
                    RaisedFilterChip(
                        selected = true, onClick = { clicks++ }, label = { Text("A") }, modifier = Modifier,
                        enabled = true, leadingIcon = null, trailingIcon = null, shape = shape,
                        selectedContainerColor = Color.Blue, selectedLabelColor = Color.White,
                        interactionSource = MutableInteractionSource(),
                    )
                    RaisedChip(
                        onClick = { clicks++ }, fill = elevationPalette().accent, label = { Text("B") },
                        modifier = Modifier, enabled = true, leadingIcon = { Text("<") }, trailingIcon = { Text(">") },
                        shape = shape, interactionSource = MutableInteractionSource(),
                    )
                    RaisedChip(onClick = { clicks++ }, fill = elevationPalette().key, label = { Text("C") })
                    SegmentTrack(modifier = Modifier, shape = shape) {
                        SegmentTrackItem(selected = false, onClick = { clicks++ }) { Text("D") }
                        SegmentTrackItem(selected = true, onClick = { clicks++ }, modifier = Modifier.size(20.dp)) {
                            Text("E")
                        }
                    }
                    SegmentTrack { SegmentTrackItem(selected = false, onClick = { clicks++ }) { Text("F") } }
                }
            }
        }
        listOf("A", "B", "C", "D", "E", "F").forEach { onNodeWithText(it).performClick() }
        assertEquals(6, clicks)
    }

    @Test
    fun `the sunken outlined field given every parameter, and with only its value`() = runComposeUiTest {
        setContent {
            MaterialTheme(colorScheme = colorSchemeFor(ThemeMode.DARK)) {
                Column {
                    SunkenOutlinedTextField(
                        value = "secret",
                        onValueChange = {},
                        modifier = Modifier.testTag("full"),
                        enabled = false,
                        readOnly = true,
                        textStyle = TextStyle(fontSize = 12.sp),
                        label = { Text("Label") },
                        placeholder = { Text("Hint") },
                        leadingIcon = { Text("L") },
                        trailingIcon = { Text("T") },
                        prefix = { Text("P") },
                        suffix = { Text("S") },
                        supportingText = { Text("Help") },
                        isError = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions.Default,
                        keyboardActions = KeyboardActions.Default,
                        singleLine = true,
                        maxLines = 1,
                        minLines = 1,
                        shape = RoundedCornerShape(2.dp),
                        colors = OutlinedTextFieldDefaults.colors(),
                    )
                    SunkenOutlinedTextField(value = "plain", onValueChange = {})
                }
            }
        }
        onNodeWithText("Help").assertExists()
        onNodeWithTag("full").assertIsNotEnabled()
        onNodeWithText("plain").assertExists()
    }
}
