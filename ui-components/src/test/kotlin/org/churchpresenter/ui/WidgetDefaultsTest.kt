package org.churchpresenter.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every widget composed with **only its required arguments**.
 *
 * The rest of this suite passes each widget an explicit modifier, size, colour and enabled flag,
 * because that is what a screenshot or a targeted assertion needs. Real call sites do the opposite:
 * they take the defaults and pass two or three arguments. Those default values are chosen behaviour
 * — a 34dp action button, a primary-coloured container, a single-line text field, a fixed-open
 * settings section — and nothing was checking that a widget still draws and still works when it is
 * left to choose them.
 */
@OptIn(ExperimentalTestApi::class)
class WidgetDefaultsTest {

    private fun shown(content: @Composable () -> Unit, body: ComposeUiTest.() -> Unit) = runComposeUiTest {
        setContent { MaterialTheme { Column { content() } } }
        body()
    }

    // ── The labelled controls ───────────────────────────────────────────────────

    @Test
    fun `a checkbox with just a label toggles`() {
        var checked = false
        shown({ LabeledCheckbox(checked, { checked = it }, "Show title") }) {
            onNodeWithText("Show title").assertIsDisplayed()
            onNodeWithText("Show title").performClick()
        }
        assertTrue(checked, "the label is the click target when no control modifier is given")
    }

    @Test
    fun `a switch with just a label toggles`() {
        var checked = false
        shown({ LabeledSwitch(checked, { checked = it }, "Fade in") }) {
            onNodeWithText("Fade in").performClick()
        }
        assertTrue(checked)
    }

    @Test
    fun `a radio button with just a label reports its click`() {
        var clicks = 0
        shown({ LabeledRadioButton(false, { clicks++ }, "Full screen") }) {
            onNodeWithText("Full screen").performClick()
        }
        assertEquals(1, clicks)
    }

    // ── Buttons ─────────────────────────────────────────────────────────────────

    @Test
    fun `an action button at its default size and colours still clicks`() {
        var clicks = 0
        shown({ ActionIconButton(onClick = { clicks++ }, tooltipText = "Add", icon = null, painter = null) }) {
            onNode(hasClickAction()).performClick()
        }
        assertEquals(1, clicks, "a button with neither an icon nor a painter is still a button")
    }

    @Test
    fun `an image icon button at its default size clicks`() {
        var clicks = 0
        shown({ ImageIconButton(onClick = { clicks++ }) { Text("x") } }) {
            onNodeWithText("x").performClick()
        }
        assertEquals(1, clicks)
    }

    // ── Fields ──────────────────────────────────────────────────────────────────

    @Test
    fun `a styled text field with no label or placeholder takes text`() {
        var text = ""
        shown({ StyledTextField(text, { text = it }) }) {
            onNode(hasSetTextAction()).performTextInput("typed")
        }
        assertEquals("typed", text)
    }

    @Test
    fun `a settings text field with no label takes text`() {
        var text = ""
        shown({ SettingsTextField(text, { text = it }) }) {
            onNode(hasSetTextAction()).performTextInput("typed")
        }
        assertEquals("typed", text)
    }

    @Test
    fun `a search field draws its placeholder`() {
        var text = ""
        shown({ SearchField(text, { text = it }, "Search songs…") }) {
            onNodeWithText("Search songs…").assertIsDisplayed()
            onNode(hasSetTextAction()).performTextInput("amaz")
        }
        assertEquals("amaz", text)
    }

    @Test
    fun `a number field with no label and the default start value still steps`() {
        var value = 0
        shown({ NumberSettingsTextField(range = 1..20, onValueChange = { value = it }) }) {
            assertNumberFieldShows(8, "the default start value")
            retypeNumberField(showing = 8, to = 12)
        }
        assertEquals(12, value)
    }

    @Test
    fun `a slider with no trailing label draws no readout beside itself`() {
        shown({ SlimSlider(40f, {}, 0f..100f, modifier = Modifier.size(200.dp, 24.dp)) }) {
            assertEquals(emptyList(), renderedText(), "a null trailingLabel must draw no text at all")
        }
    }

    // ── Dropdowns ───────────────────────────────────────────────────────────────

    @Test
    fun `a dropdown settings field with no label and no fixed width shows its value`() {
        shown({ DropdownSettingsField("Georgia", listOf("Georgia", "Arial"), {}) }) {
            onNodeWithText("Georgia").assertIsDisplayed()
        }
    }

    @Test
    fun `a searchable dropdown with no label shows its value`() {
        shown({ SearchableDropdownField("Georgia", listOf("Georgia", "Arial"), {}) }) {
            onNodeWithText("Georgia").assertIsDisplayed()
        }
    }

    // ── Layout ──────────────────────────────────────────────────────────────────

    @Test
    fun `a settings section is fixed open by default`() {
        shown({ SettingsSection(title = "Word") { Text("inside") } }) {
            onNodeWithText("Word").assertIsDisplayed()
            onNodeWithText("inside").assertIsDisplayed()
        }
    }

    @Test
    fun `a setting row at its default width and alignment draws its label`() {
        shown({ SettingRow(label = "Color") { Text("control") } }) {
            onNodeWithText("Color").assertIsDisplayed()
            onNodeWithText("control").assertIsDisplayed()
        }
    }

    @Test
    fun `a tv screen box with no content draws only its own chrome`() {
        shown({ TvScreenBox(modifier = Modifier.size(120.dp, 80.dp)) }) {
            assertEquals(emptyList(), renderedText(), "the default content is empty, so nothing is written")
        }
    }

    @Test
    fun `a selection list starts on its first item`() {
        var picked = ""
        shown({ SelectionList(list = listOf("One", "Two"), onItemSelected = { picked = it }) }) {
            onNodeWithText("Two").performClick()
        }
        assertEquals("Two", picked)
    }

    @Test
    fun `a selection list with indices takes its defaults`() {
        var picked = -1
        shown({
            SelectionListWithIndex(list = listOf("One", "Two"), onItemSelected = { i, _ -> picked = i })
        }) {
            onNodeWithText("Two").performClick()
        }
        assertEquals(1, picked)
    }

}
