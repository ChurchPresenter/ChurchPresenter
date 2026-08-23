package org.churchpresenter.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test

/**
 * Every widget redrawn while its own arguments have not changed.
 *
 * A settings tab recomposes constantly — one control moves and the whole column is re-evaluated —
 * and a widget whose inputs are unchanged is expected to skip rather than rebuild. That skip path
 * is a real branch in each of these functions and no other test in this suite reaches it: they all
 * compose once and then change the very thing they are asserting on.
 *
 * The tick below is deliberately unrelated to every widget under it, so the enclosing scope is
 * invalidated while each widget's parameters stay identical.
 */
@OptIn(ExperimentalTestApi::class)
class WidgetSkipsRecompositionTest {

    @Test
    fun `the widget set survives a recomposition it has no part in`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                var tick by remember { mutableStateOf(0) }
                Column {
                    TextButton(onClick = { tick++ }) { Text("tick") }
                    Text("count $tick")

                    LabeledCheckbox(checked = true, onCheckedChange = {}, label = "Show title")
                    LabeledSwitch(checked = false, onCheckedChange = {}, label = "Fade in")
                    LabeledRadioButton(selected = true, onClick = {}, label = "Full screen")
                    StyledTextField(value = "steady", onValueChange = {})
                    SettingsTextField(value = "steady too", onValueChange = {})
                    SearchField(value = "", onValueChange = {}, placeholder = "Search…")
                    NumberSettingsTextField(range = 1..20, onValueChange = {})
                    SlimSlider(40f, {}, 0f..100f, modifier = Modifier.size(160.dp, 24.dp))
                    DropdownSettingsField("Georgia", listOf("Georgia", "Arial"), {})
                    SearchableDropdownField("Arial", listOf("Georgia", "Arial"), {})
                    ActionIconButton(onClick = {}, tooltipText = "Add")
                    GoLiveButton(onClick = {}, tooltipText = "Go live")
                    AddToScheduleButton(onClick = {}, tooltipText = "Add to schedule")
                    ImageIconButton(onClick = {}) { Text("img") }
                    TextStyleButtons(
                        bold = true, italic = false, underline = false, shadow = false,
                        onBoldChange = {}, onItalicChange = {},
                        onUnderlineChange = {}, onShadowChange = {},
                    )
                    HorizontalAlignmentButtons("center", {}, "left", "center", "right")
                    VerticalAlignmentButtons("middle", {}, "top", "middle", "bottom")
                    PositionButtons("above", {}, "above", "below")
                    SettingsSection(title = "Word") { Text("inside") }
                    SettingRow(label = "Color") { Text("control") }
                    SelectionList(list = listOf("One", "Two"), onItemSelected = {})
                    TvScreenBox(modifier = Modifier.size(80.dp, 50.dp))
                    ColorPickerField(color = "#FFD54F", onColorChange = {}, label = "Text")
                    ShadowDetailRow(
                        shadowColor = "#000000", onColorChange = {},
                        shadowSize = 4, onSizeChange = {},
                        shadowOpacity = 50, onOpacityChange = {},
                    )
                }
            }
        }

        onNodeWithText("count 0").assertIsDisplayed()
        repeat(3) {
            onNodeWithText("tick").performClick()
            waitForIdle()
        }

        onNodeWithText("count 3").assertIsDisplayed()
        // Still composed, still holding the values they started with. Existence rather than
        // display: the column is taller than the test window, so the widgets near the bottom are
        // laid out below the fold — which does not stop them being recomposed.
        onNodeWithText("Show title").assertExists()
        onNodeWithText("steady").assertExists()
        onNodeWithText("Georgia").assertExists()
        onNodeWithText("inside").assertExists()
        onNodeWithText("One").assertExists()
    }
}
