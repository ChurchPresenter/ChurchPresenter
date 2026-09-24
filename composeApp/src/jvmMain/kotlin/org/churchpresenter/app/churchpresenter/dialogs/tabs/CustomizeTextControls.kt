package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.LabeledCheckbox

/**
 * The colour, face and on/off controls of the Customize dialog's form.
 *
 * Split from `CustomizeForm.kt`, which sat one function over detekt's `TooManyFunctions` threshold
 * for a file. The line is drawn where the two halves already differ: this file holds what styles
 * *text* -- its colour, its typeface, its bold/italic/underline/shadow quartet -- and
 * `CustomizeForm.kt` keeps the numbers, sliders, alignments and choice rows that arrange it.
 *
 * Every one of these is a thin call onto a shared composable the settings tabs already use, which
 * is the point: one numeric field, one colour picker and one style quartet across the app means one
 * set of habits.
 */

private val FONT_FIELD_WIDTH = 190.dp

private val COLOR_FIELD_WIDTH = 132.dp

/** A colour swatch and its hex. */
@Composable
internal fun ColorControl(label: String, color: String, onColorChange: (String) -> Unit) {
    ColorPickerField(
        label = label,
        color = color,
        onColorChange = onColorChange,
        modifier = Modifier.width(COLOR_FIELD_WIDTH),
    )
}

/** An on/off setting, drawn as the [LabeledCheckbox] every settings tab uses for a boolean. */
@Composable
internal fun ToggleControl(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    LabeledCheckbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        label = label,
        style = MaterialTheme.typography.bodySmall,
    )
}

/** The font picker, carrying its own label the way it does on every settings tab. */
@Composable
internal fun FontControl(
    label: String,
    value: String,
    fonts: List<String>,
    onValueChange: (String) -> Unit,
) {
    FontSettingsDropdown(
        label = label,
        value = value,
        fonts = fonts,
        onValueChange = onValueChange,
        modifier = Modifier.width(FONT_FIELD_WIDTH),
        fillWidth = true,
    )
}
