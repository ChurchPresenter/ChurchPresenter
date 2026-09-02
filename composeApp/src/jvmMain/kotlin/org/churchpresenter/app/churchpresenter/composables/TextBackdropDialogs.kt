package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.backdrop_border
import churchpresenter.composeapp.generated.resources.backdrop_border_color
import churchpresenter.composeapp.generated.resources.backdrop_border_padding
import churchpresenter.composeapp.generated.resources.backdrop_border_radius
import churchpresenter.composeapp.generated.resources.backdrop_border_width
import churchpresenter.composeapp.generated.resources.backdrop_enabled
import churchpresenter.composeapp.generated.resources.backdrop_fill_color
import churchpresenter.composeapp.generated.resources.backdrop_height_offset
import churchpresenter.composeapp.generated.resources.backdrop_line_background
import churchpresenter.composeapp.generated.resources.backdrop_opacity
import churchpresenter.composeapp.generated.resources.backdrop_vertical_offset
import churchpresenter.composeapp.generated.resources.close
import org.churchpresenter.core.models.text.TextBackdrop
import org.jetbrains.compose.resources.stringResource

private val DIALOG_WIDTH = 320.dp

/**
 * The settings behind the border button, in a dialog of its own.
 *
 * A dialog rather than a row of fields under the buttons: these settings belong to nine different
 * panels, several of which are a 48dp toolbar or an already-full properties column, and adding four
 * numeric fields to each of them would rearrange screens this feature has no business rearranging.
 * The button carries the state; the dialog carries the detail.
 */
@Composable
fun TextBorderDialog(
    backdrop: TextBackdrop,
    onChange: (TextBackdrop) -> Unit,
    onDismiss: () -> Unit,
) = BackdropDialog(
    title = stringResource(Res.string.backdrop_border),
    enabled = backdrop.border,
    onEnabledChange = { onChange(backdrop.copy(border = it)) },
    onDismiss = onDismiss,
) {
    ColorPickerField(
        label = stringResource(Res.string.backdrop_border_color),
        color = backdrop.borderColor,
        onColorChange = { onChange(backdrop.copy(borderColor = it)) },
        modifier = Modifier.fillMaxWidth(),
    )
    BackdropNumberField(
        label = stringResource(Res.string.backdrop_opacity),
        value = backdrop.borderOpacity,
        range = TextBackdrop.OPACITY_RANGE,
    ) { onChange(backdrop.copy(borderOpacity = it)) }
    BackdropNumberField(
        label = stringResource(Res.string.backdrop_border_width),
        value = backdrop.borderWidth,
        range = TextBackdrop.WIDTH_RANGE,
    ) { onChange(backdrop.copy(borderWidth = it)) }
    BackdropNumberField(
        label = stringResource(Res.string.backdrop_border_padding),
        value = backdrop.borderPadding,
        range = TextBackdrop.PADDING_RANGE,
    ) { onChange(backdrop.copy(borderPadding = it)) }
    BackdropNumberField(
        label = stringResource(Res.string.backdrop_border_radius),
        value = backdrop.borderRadius,
        range = TextBackdrop.RADIUS_RANGE,
    ) { onChange(backdrop.copy(borderRadius = it)) }
}

/** The same, for the band drawn behind each line of the text. */
@Composable
fun LineBackgroundDialog(
    backdrop: TextBackdrop,
    onChange: (TextBackdrop) -> Unit,
    onDismiss: () -> Unit,
) = BackdropDialog(
    title = stringResource(Res.string.backdrop_line_background),
    enabled = backdrop.lineBackground,
    onEnabledChange = { onChange(backdrop.copy(lineBackground = it)) },
    onDismiss = onDismiss,
) {
    ColorPickerField(
        label = stringResource(Res.string.backdrop_fill_color),
        color = backdrop.lineBackgroundColor,
        onColorChange = { onChange(backdrop.copy(lineBackgroundColor = it)) },
        modifier = Modifier.fillMaxWidth(),
    )
    BackdropNumberField(
        label = stringResource(Res.string.backdrop_opacity),
        value = backdrop.lineBackgroundOpacity,
        range = TextBackdrop.OPACITY_RANGE,
    ) { onChange(backdrop.copy(lineBackgroundOpacity = it)) }
    BackdropNumberField(
        label = stringResource(Res.string.backdrop_height_offset),
        value = backdrop.lineBackgroundHeight,
        range = TextBackdrop.HEIGHT_RANGE,
    ) { onChange(backdrop.copy(lineBackgroundHeight = it)) }
    BackdropNumberField(
        label = stringResource(Res.string.backdrop_vertical_offset),
        value = backdrop.lineBackgroundOffset,
        range = TextBackdrop.OFFSET_RANGE,
    ) { onChange(backdrop.copy(lineBackgroundOffset = it)) }
}

/**
 * The shell both share: a title, the on/off switch the button also flips, the fields, and a Close.
 *
 * The switch is here as well as on the button because the button turns the feature *on* when it
 * opens this — leaving no way to turn it off from a dialog that is already open.
 */
@Composable
private fun BackdropDialog(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    fields: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.width(DIALOG_WIDTH),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(Res.string.backdrop_enabled),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Switch(checked = enabled, onCheckedChange = onEnabledChange)
                }
                fields()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
                }
            }
        }
    }
}

@Composable
private fun BackdropNumberField(label: String, value: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    NumberSettingsTextField(
        modifier = Modifier.fillMaxWidth(),
        label = label,
        initialText = value,
        range = range,
        onValueChange = onValueChange,
    )
}
