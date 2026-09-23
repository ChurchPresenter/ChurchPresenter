package org.churchpresenter.theme.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.theme.colorSchemeFor
import org.churchpresenter.theme.elevationPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RaisedControlsTest {

    @Test
    fun `raised, key and ghost buttons click, and do nothing while disabled`() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme(colorScheme = colorSchemeFor(ThemeMode.DARK)) {
                Column {
                    RaisedButton(onClick = { clicks++ }) { Text("Raised") }
                    KeyButton(onClick = { clicks++ }) { Text("Key") }
                    GhostButton(onClick = { clicks++ }) { Text("Ghost") }
                    RaisedButton(onClick = { clicks++ }, enabled = false) { Text("Off") }
                }
            }
        }
        listOf("Raised", "Key", "Ghost").forEach { onNodeWithText(it).performClick() }
        onNodeWithText("Off").assertIsNotEnabled().performClick()
        assertEquals(3, clicks)
    }

    @Test
    fun `key buttons take a filled, bordered, destructive or explicit fill`() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme(colorScheme = colorSchemeFor(ThemeMode.LIGHT)) {
                Column {
                    KeyButton(
                        onClick = { clicks++ },
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Blue),
                        border = BorderStroke(1.dp, Color.Red),
                    ) { Text("Filled") }
                    KeyButton(
                        onClick = { clicks++ },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Delete") }
                    KeyButton(onClick = { clicks++ }, fill = elevationPalette().danger) { Text("Reset") }
                    KeyButton(onClick = { clicks++ }, enabled = false, fill = elevationPalette().danger) {
                        Text("None")
                    }
                }
            }
        }
        listOf("Filled", "Delete", "Reset", "None").forEach { onNodeWithText(it).performClick() }
        assertEquals(3, clicks)
    }

    @Test
    fun `a ghost button presses in, and a filled one keeps its fill`() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme {
                Column {
                    GhostButton(onClick = { clicks++ }, modifier = Modifier.testTag("ghost")) { Text("Cancel") }
                    GhostButton(
                        onClick = { clicks++ },
                        colors = ButtonDefaults.textButtonColors(containerColor = Color.Green),
                        border = BorderStroke(1.dp, Color.Red),
                    ) { Text("Tinted") }
                }
            }
        }
        onNodeWithTag("ghost").performTouchInput { down(center) }
        waitForIdle()
        onNodeWithTag("ghost").performTouchInput { up() }
        onNodeWithText("Tinted").performClick()
        assertEquals(2, clicks)
    }

    @Test
    fun `a switch reports its new state, and a read-only or disabled one does not change`() = runComposeUiTest {
        var on by mutableStateOf(false)
        var dark by mutableStateOf(true)
        setContent {
            MaterialTheme(colorScheme = colorSchemeFor(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
                Column {
                    RaisedSwitch(checked = on, onCheckedChange = { on = it }, modifier = Modifier.testTag("switch"))
                    RaisedSwitch(checked = true, onCheckedChange = null, modifier = Modifier.testTag("readonly"))
                    RaisedSwitch(checked = false, onCheckedChange = { on = true }, enabled = false,
                        modifier = Modifier.testTag("disabled"))
                }
            }
        }
        onNodeWithTag("switch").assertIsOff().performClick()
        assertTrue(on)
        onNodeWithTag("switch").assertIsOn()
        on = false
        onNodeWithTag("disabled").performClick()
        onNodeWithTag("readonly").performClick()
        assertEquals(false, on)
        dark = false
        on = true
        onNodeWithTag("switch").assertIsOn()
    }

    @Test
    fun `icon keys click, a transparent unbordered one stays flat, and disabled ones ignore clicks`() =
        runComposeUiTest {
            var clicks = 0
            setContent {
                MaterialTheme(colorScheme = colorSchemeFor(ThemeMode.DARK)) {
                    Column {
                        KeyIconButton(onClick = { clicks++ }, modifier = Modifier.testTag("flat")) {
                            Text("flat")
                        }
                        KeyIconButton(
                            onClick = { clicks++ },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Blue),
                        ) { Text("filled") }
                        KeyIconButton(onClick = { clicks++ }, enabled = false) {
                            Text("flat off")
                        }
                        KeyIconButton(
                            onClick = { clicks++ },
                            enabled = false,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Blue),
                        ) { Text("filled off") }
                        RaisedIconButton(onClick = { clicks++ }) { Text("raised") }
                        RaisedIconButton(
                            onClick = { clicks++ },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Transparent),
                        ) { Text("clear") }
                        RaisedIconButton(
                            onClick = { clicks++ },
                            colors = IconButtonDefaults.outlinedIconButtonColors(),
                            border = BorderStroke(1.dp, Color.Gray),
                        ) { Text("outlined") }
                        RaisedIconButton(onClick = { clicks++ }, enabled = false) {
                            Text("raised off")
                        }
                    }
                }
            }
            onNodeWithTag("flat").performMouseInput { enter(center) }
            waitForIdle()
            listOf("flat", "filled", "flat off", "filled off", "raised", "clear", "outlined", "raised off").forEach {
                onNodeWithText(it).performClick()
            }
            assertEquals(5, clicks)
        }

    @Test
    fun `a checkbox and a radio button report their choice, in the accent or a caller's color`() =
        runComposeUiTest {
            var checked by mutableStateOf(false)
            var picked by mutableStateOf(false)
            setContent {
                MaterialTheme(colorScheme = colorSchemeFor(ThemeMode.DARK)) {
                    Column {
                        RaisedCheckbox(checked, { checked = it }, Modifier.testTag("box"))
                        RaisedCheckbox(
                            true,
                            null,
                            Modifier.testTag("tinted"),
                            colors = CheckboxDefaults.colors(checkedColor = Color.Magenta),
                        )
                        RaisedCheckbox(false, { checked = true }, Modifier.testTag("boxOff"), enabled = false)
                        RaisedRadioButton(picked, { picked = true }, Modifier.testTag("radio"))
                        RaisedRadioButton(true, null, Modifier.testTag("radioRead"))
                    }
                }
            }
            onNodeWithTag("box").assertIsOff().performClick()
            assertTrue(checked)
            onNodeWithTag("box").assertIsOn()
            onNodeWithTag("tinted").assertExists()
            onNodeWithTag("radio").performClick()
            assertTrue(picked)
            onNodeWithTag("radio").assertIsSelected()
            onNodeWithTag("radioRead").performClick()
        }

    @Test
    fun `chips click in either state, in the accent or a caller's selected color`() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme(colorScheme = colorSchemeFor(ThemeMode.LIGHT)) {
                Column {
                    RaisedFilterChip(selected = true, onClick = { clicks++ }, label = { Text("On") },
                        leadingIcon = { Text("<") }, trailingIcon = { Text(">") })
                    RaisedFilterChip(selected = false, onClick = { clicks++ }, label = { Text("Off") })
                    RaisedFilterChip(selected = true, onClick = { clicks++ }, label = { Text("Warn") },
                        selectedContainerColor = Color.Red)
                    RaisedChip(onClick = { clicks++ }, fill = elevationPalette().key, label = { Text("Word") },
                        enabled = false)
                }
            }
        }
        listOf("On", "Off", "Warn", "Word").forEach { onNodeWithText(it).performClick() }
        assertEquals(3, clicks)
    }

    @Test
    fun `a segment track selects the option clicked`() = runComposeUiTest {
        var picked by mutableStateOf("a")
        setContent {
            MaterialTheme {
                SegmentTrack {
                    listOf("a", "b").forEach { option ->
                        SegmentTrackItem(selected = picked == option, onClick = { picked = option },
                            modifier = Modifier.size(40.dp, 24.dp).testTag(option)) { Text(option) }
                    }
                }
            }
        }
        onNodeWithTag("b").performMouseInput { enter(center) }
        onNodeWithTag("b").performClick()
        assertEquals("b", picked)
        onNodeWithTag("b").assertIsSelected()
    }

    @Test
    fun `the sunken outlined field takes typing, with the well or a caller's colors`() = runComposeUiTest {
        var value by mutableStateOf("")
        setContent {
            MaterialTheme {
                Column {
                    SunkenOutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Name") })
                    SunkenOutlinedTextField(
                        value = "x",
                        onValueChange = {},
                        colors = OutlinedTextFieldDefaults.colors(),
                    )
                }
            }
        }
        onNodeWithText("Name").assertExists()
        onAllNodes(hasSetTextAction())[0].performTextReplacement("sunday")
        assertEquals("sunday", value)
    }
}
