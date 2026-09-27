package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import org.churchpresenter.app.churchpresenter.composables.LabeledCheckbox
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField

private val NUMBER_FIELD_WIDTH = 104.dp

private const val NUMBER_CHAR_WIDTH = 7f

private const val NUMBER_PADDING = 26f

/** A titled group of rows: an uppercase accent heading with a rule down its left edge. */
@OptIn(ExperimentalLayoutApi::class)

/**
 * A number in the boxed, unit-captioned field the settings tabs already use, and an optional Auto
 * checkbox beside it.
 *
 * The same `NumberSettingsTextField` the Bible tab's sizes and margins are typed into, rather than a
 * form control of this dialog's own — one numeric field across the app means one set of habits, and
 * the caption inside the field is where the setting's name goes, exactly as it does there.
 */
@Composable
internal fun NumberControl(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    autoLabel: String? = null,
    auto: Boolean = false,
    onAutoChange: (Boolean) -> Unit = {},
    /**
     * A width of the caller's own, for a row that has to hold several of these at once.
     *
     * The derived width below is generous by design -- it is sized so a long caption is never
     * clipped -- which is right for a lone field and wrong for four of them side by side.
     */
    width: Dp? = null,
) {
    // Widened to fit its own caption when the caption is long — the label is drawn inside the box,
    // so a fixed width clipped "Intensity (%)" to "Intensit…".
    val fieldWidth = width ?: (label.length * NUMBER_CHAR_WIDTH + NUMBER_PADDING)
        .coerceAtLeast(NUMBER_FIELD_WIDTH.value).dp
    NumberSettingsTextField(
        label = label,
        initialText = value,
        onValueChange = onValueChange,
        range = range,
        modifier = Modifier.width(fieldWidth),
    )
    if (autoLabel != null) {
        // The Song tab's Auto-fit box, not a drawn square of this dialog's own.
        LabeledCheckbox(
            checked = auto,
            onCheckedChange = onAutoChange,
            label = autoLabel,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

