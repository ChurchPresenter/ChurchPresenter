@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.ui.DropdownSettingsField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.ui.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.ShadowDetailRow
import org.churchpresenter.ui.SlimSlider
import kotlin.test.Test

/**
 * The labelled settings fields the Announcements tab's bars are built from — a colour swatch, two
 * flavours of dropdown, a number stepper, a slider, and the shadow row that combines three of them.
 *
 * Shot here rather than only through that tab: every one of them also appears in the settings
 * dialog, the canvas source panel and the Bible/song settings, so one image per state beats the same
 * field turning up inside a dozen tab screenshots.
 */
class SettingsFieldsScreenshotTest {

    private fun field(name: String, width: Dp = 220.dp, content: @Composable () -> Unit) =
        captureComponent(SECTION, name) { Box(Modifier.width(width)) { content() } }

    // ── ColorPickerField ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a colour chosen`() = field("color_picker") {
        ColorPickerField(color = "#FFD54F", onColorChange = {}, label = "Text Color")
    }

    /** "transparent" and a fully-transparent hex both draw the chequerboard rather than a swatch. */
    @Test
    fun `a transparent colour`() = field("color_picker_transparent") {
        ColorPickerField(color = "transparent", onColorChange = {}, label = "Background")
    }

    @Test
    fun `a colour with no label`() = field("color_picker_no_label") {
        ColorPickerField(color = "#1B2A5B", onColorChange = {})
    }

    // ── DropdownSettingsField ───────────────────────────────────────────────────────────────────

    @Test
    fun `a dropdown closed`() = field("dropdown_closed") {
        DropdownSettingsField(
            value = CLOCK_FORMATS[0],
            options = CLOCK_FORMATS,
            onValueChange = {},
            label = "Clock Format",
        )
    }

    @Test
    fun `a dropdown open`() = captureComponent(
        SECTION,
        "dropdown_open",
        rootIndex = 1,
        drive = { openByText(CLOCK_FORMATS[0]) },
    ) {
        Box(Modifier.width(220.dp)) {
            DropdownSettingsField(
                value = CLOCK_FORMATS[0],
                options = CLOCK_FORMATS,
                onValueChange = {},
                label = "Clock Format",
            )
        }
    }

    /** A fixed width pins the chevron to the right edge instead of letting it trail the value. */
    @Test
    fun `a dropdown given a fixed width`() = field("dropdown_fixed_width", width = 280.dp) {
        DropdownSettingsField(
            value = CLOCK_FORMATS[3],
            options = CLOCK_FORMATS,
            onValueChange = {},
            label = "Clock Format",
            width = 260.dp,
        )
    }

    // ── FontSettingsDropdown ────────────────────────────────────────────────────────────────────

    /** A fixed font list, not the machine's: the installed set differs from box to box. */
    @Test
    fun `a font picker closed`() = field("font_picker") {
        FontSettingsDropdown(label = "Font", value = "Georgia", fonts = FONTS, onValueChange = {})
    }

    /**
     * The panel the field opens: every installed family, grouped, each drawn in itself, with the
     * verse preview underneath. Its states are shot one by one in `FontPickerScreenshotTest`; this
     * is the pair — a field on a settings row with its panel over it, which is what an operator sees.
     */
    @Test
    fun `a font picker open`() = captureComponent(
        SECTION,
        "font_picker_open",
        rootIndex = 1,
        drive = { openByText("Georgia") },
    ) {
        Box(Modifier.width(220.dp)) {
            FontSettingsDropdown(label = "Font", value = "Georgia", fonts = FONTS, onValueChange = {})
        }
    }

    // ── NumberSettingsTextField ─────────────────────────────────────────────────────────────────

    @Test
    fun `a number field`() = field("number_field", width = 140.dp) {
        NumberSettingsTextField(label = "Font Size", initialText = 48, range = 8..200, onValueChange = {})
    }

    @Test
    fun `a number field with no label`() = field("number_field_no_label", width = 140.dp) {
        NumberSettingsTextField(initialText = 3, range = 0..99, onValueChange = {})
    }

    /** Out of range: the field keeps what was typed and turns its border red. */
    @Test
    fun `a number outside the allowed range`() = captureComponent(
        SECTION,
        "number_field_invalid",
        drive = {
            onAllNodes(hasSetTextAction())[0].performTextReplacement("999")
            waitForIdle()
        },
    ) {
        Box(Modifier.width(140.dp)) {
            NumberSettingsTextField(label = "Font Size", initialText = 48, range = 8..200, onValueChange = {})
        }
    }

    // ── SlimSlider ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a slider part way along`() = field("slider", width = 300.dp) {
        SlimSlider(value = 12f, onValueChange = {}, valueRange = 0.5f..30f, trailingLabel = "12.0s")
    }

    @Test
    fun `a slider at its minimum`() = field("slider_min", width = 300.dp) {
        SlimSlider(value = 0.5f, onValueChange = {}, valueRange = 0.5f..30f, trailingLabel = "0.5s")
    }

    @Test
    fun `a slider at its maximum`() = field("slider_max", width = 300.dp) {
        SlimSlider(value = 30f, onValueChange = {}, valueRange = 0.5f..30f, trailingLabel = "30.0s")
    }

    @Test
    fun `a slider with no value label`() = field("slider_no_label", width = 300.dp) {
        SlimSlider(value = 12f, onValueChange = {}, valueRange = 0.5f..30f)
    }

    @Test
    fun `a slider disabled`() = field("slider_disabled", width = 300.dp) {
        SlimSlider(
            value = 12f,
            onValueChange = {},
            valueRange = 0.5f..30f,
            trailingLabel = "12.0s",
            enabled = false,
        )
    }

    // ── ShadowDetailRow ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `the shadow row`() = field("shadow_row", width = 460.dp) {
        ShadowDetailRow(
            shadowColor = "#000000",
            shadowSize = 100,
            shadowOpacity = 78,
            onColorChange = {},
            onSizeChange = {},
            onOpacityChange = {},
        )
    }

    /** Clicks a field open by one of the strings drawn inside it — the field itself has no label. */
    private fun ComposeUiTest.openByText(text: String) {
        onNodeWithText(text).performClick()
        waitForIdle()
    }

    private companion object {
        const val SECTION = "settingsFields"

        val CLOCK_FORMATS = listOf(
            "12-hour with seconds",
            "12-hour",
            "24-hour with seconds",
            "24-hour",
        )

        val FONTS = listOf("Arial", "Courier New", "Georgia", "Times New Roman", "Verdana")
    }
}
