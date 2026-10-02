package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

// `{ gen }` is not dead code: capturing the generation makes every recomposition hand the child a
// new lambda, which is what forces it to recompose with the flipped state.
@Suppress("UNUSED_EXPRESSION")
@OptIn(ExperimentalTestApi::class)
class SharedRecomposeTest {

    private class Flags {
        var theme by mutableStateOf(ThemeMode.LIGHT)
        var on by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        var atEnd by mutableStateOf(false)
        var generation by mutableIntStateOf(0)

        fun flip() {
            theme = if (theme == ThemeMode.LIGHT) ThemeMode.DARK else ThemeMode.LIGHT
            on = !on
            atEnd = !atEnd
            generation++
        }
    }

    @Test
    fun `the action buttons follow their state as it changes`() = runComposeUiTest {
        val f = Flags()
        var lives = 0
        setContent {
            ChurchPresenterTheme(themeMode = f.theme) {
                val gen = f.generation
                Column {
                    GoLiveButton(
                        onClick = { lives++; gen }, tooltipText = "Go Live", enabled = f.enabled, dimmed = f.on,
                    )
                    AddToScheduleButton(
                        onClick = { gen }, tooltipText = "Add", enabled = f.enabled, modifier = Modifier,
                    )
                    SavePresetButton(onClick = { gen }, tooltipText = "Save", enabled = f.enabled, modifier = Modifier)
                }
            }
        }
        repeat(3) { f.flip(); waitForIdle() }
        onNodeWithContentDescription("Go Live").performClick()
        waitForIdle()
        assertEquals(1, lives)
        f.enabled = false
        waitForIdle()
        onNodeWithContentDescription("Go Live").performClick()
        waitForIdle()
        assertEquals(1, lives)
    }

    @Test
    fun `the labeled controls follow their state as it changes`() = runComposeUiTest {
        val f = Flags()
        setContent {
            ChurchPresenterTheme(themeMode = f.theme) {
                val gen = f.generation
                Column {
                    LabeledCheckbox(
                        checked = f.on, onCheckedChange = { f.on = it; gen }, label = "Box",
                        enabled = f.enabled, supporting = if (f.atEnd) "help" else null, controlAtEnd = f.atEnd,
                    )
                    LabeledRadioButton(
                        selected = f.on, onClick = { gen }, label = "Radio",
                        enabled = f.enabled, supporting = if (f.atEnd) "help" else null, controlAtEnd = f.atEnd,
                    )
                    LabeledSwitch(
                        checked = f.on, onCheckedChange = { gen }, label = "Switch",
                        enabled = f.enabled, supporting = if (f.atEnd) "help" else null, controlAtEnd = f.atEnd,
                    )
                }
            }
        }
        repeat(3) { f.flip(); waitForIdle() }
        onNodeWithText("Box").performClick()
        waitForIdle()
        assertEquals(false, f.on)
    }

    @Test
    fun `the text style row follows its state as it changes`() = runComposeUiTest {
        val f = Flags()
        setContent {
            ChurchPresenterTheme(themeMode = f.theme) {
                val gen = f.generation
                TextStyleButtons(
                    bold = f.on, italic = !f.on, underline = f.on, shadow = !f.on,
                    onBoldChange = { gen }, onItalicChange = { gen },
                    onUnderlineChange = { gen }, onShadowChange = { gen },
                    buttonSize = if (f.on) 30.dp else 28.dp,
                    strikethrough = f.on,
                    onStrikethroughChange = if (f.atEnd) { { gen } } else null,
                    showShadow = !f.atEnd,
                    showUnderline = f.atEnd,
                    outline = TextOutline(enabled = f.on),
                    onOutlineChange = { gen },
                )
            }
        }
        repeat(3) { f.flip(); waitForIdle() }
        onNodeWithText("B").assertExists()
    }

    @Test
    fun `the settings fields follow their state as they change`() = runComposeUiTest {
        val f = Flags()
        setContent {
            ChurchPresenterTheme(themeMode = f.theme) {
                val gen = f.generation
                Column {
                    SettingsSection(
                        title = "Section $gen", collapsible = f.atEnd, expanded = f.on, onExpandedChange = { gen },
                    ) { DropdownSettingsField("One", listOf("One", "Two"), { gen }, label = if (f.on) "L" else "") }
                    SettingRow("Row", width = if (f.on) 100.dp else 120.dp) { ColorPickerField("#FF0000", { gen }) }
                    SettingSwitchRow("Switch", f.on, { gen })
                    NumberSettingsTextField(label = "N", initialText = gen, range = 0..100, onValueChange = {})
                }
            }
        }
        repeat(3) { f.flip(); waitForIdle() }
        onNodeWithText("Section 3").assertExists()
    }
}
