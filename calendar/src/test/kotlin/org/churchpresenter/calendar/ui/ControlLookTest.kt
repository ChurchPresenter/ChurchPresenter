package org.churchpresenter.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.AppThemeWrapper
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.theme.elevationPalette
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ControlLookTest {

    @Test
    fun `a raised key clicks, lifts under the pointer, presses in, and ignores clicks while disabled`() =
        runComposeUiTest {
            var clicks = 0
            setContent {
                AppThemeWrapper(theme = ThemeMode.DARK) {
                    Column {
                        Box(
                            Modifier.size(30.dp).testTag("on")
                                .raisedKey(RoundedCornerShape(6.dp), elevationPalette().key) { clicks++ }
                        )
                        Box(
                            Modifier.size(30.dp).testTag("off")
                                .raisedKey(
                                    RoundedCornerShape(6.dp),
                                    elevationPalette().key,
                                    enabled = false,
                                ) { clicks++ }
                        )
                    }
                }
            }
            onNodeWithTag("on").performMouseInput { enter(center) }
            onNodeWithTag("on").performTouchInput { down(center) }
            waitForIdle()
            onNodeWithTag("on").performTouchInput { up() }
            onNodeWithTag("off").performClick()
            assertEquals(1, clicks)
        }

    @Test
    fun `the sheet's buttons click in every variant, and a disabled primary does not`() = runComposeUiTest {
        var clicks = 0
        setContent {
            AppThemeWrapper(theme = ThemeMode.LIGHT) {
                Column {
                    QuietButton(label = "Quiet", onClick = { clicks++ })
                    QuietButton(label = "Accent", onClick = { clicks++ }, accent = true)
                    PrimaryButton("Go", { clicks++ })
                    PrimaryButton("Blocked", { clicks++ }, enabled = false)
                    SmallIconButton(Icons.Filled.Add, "Add", { clicks++ })
                    SmallIconButton(Icons.Filled.Delete, "Remove", { clicks++ }, destructive = true)
                    DashedAddButton("New", Icons.Filled.Add, { clicks++ })
                }
            }
        }
        listOf("Quiet", "Accent", "Go", "New").forEach { onNodeWithText(it).performClick() }
        onNodeWithText("Blocked").assertIsNotEnabled().performClick()
        onNodeWithContentDescription("Add").performClick()
        onNodeWithContentDescription("Remove").performClick()
        assertEquals(6, clicks)
    }

    @Test
    fun `tabs, chips and the selector pick what is clicked, lit or not`() = runComposeUiTest {
        var tab by mutableStateOf("a")
        var chip by mutableStateOf(false)
        var pick by mutableStateOf(false)
        var option by mutableStateOf(1)
        setContent {
            AppThemeWrapper(theme = ThemeMode.DARK) {
                Column {
                    SheetTab("Tab A", tab == "a") { tab = "a" }
                    SheetTab("Tab B", tab == "b") { tab = "b" }
                    OptionChip("Chip", chip) { chip = !chip }
                    PickChip("Pick", pick) { pick = !pick }
                    SegmentedSelector(listOf(1, 2), option, { "Option $it" }, { option = it })
                }
            }
        }
        onNodeWithText("Tab B").performClick()
        onNodeWithText("Chip").performClick()
        onNodeWithText("Pick").performClick()
        onNodeWithText("Option 2").performClick()
        onNodeWithText("Chip").performClick()
        onNodeWithText("Pick").performClick()
        assertEquals("b", tab)
        assertEquals(false, chip)
        assertEquals(false, pick)
        assertEquals(2, option)
    }

    @Test
    fun `a compact field shows its resting, focused and error rims`() = runComposeUiTest {
        setContent {
            AppThemeWrapper(theme = ThemeMode.LIGHT) {
                Column {
                    CompactTextField(value = "rest", onValueChange = {})
                    CompactTextField(value = "focus", onValueChange = {}, focused = true)
                    CompactTextField(value = "error", onValueChange = {}, errorBorder = true, leading = { Text("#") })
                }
            }
        }
        listOf("rest", "focus", "error", "#").forEach { onNodeWithText(it).assertExists() }
    }

    @Test
    fun `a sheet shows its close button unless it is told not to`() = runComposeUiTest {
        var closed = 0
        var showClose by mutableStateOf(true)
        setContent {
            AppThemeWrapper(theme = ThemeMode.LIGHT) {
                SheetScaffold(title = "Sheet", onDismiss = { closed++ }, width = 300.dp, showClose = showClose) {
                    Text("Body")
                }
            }
        }
        onNodeWithContentDescription("Close").performClick()
        assertEquals(1, closed)
        showClose = false
        onNodeWithContentDescription("Close").assertDoesNotExist()
    }
}
