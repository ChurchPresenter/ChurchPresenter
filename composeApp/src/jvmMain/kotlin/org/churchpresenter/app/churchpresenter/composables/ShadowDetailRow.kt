package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.color
import churchpresenter.composeapp.generated.resources.shadow_size
import churchpresenter.composeapp.generated.resources.shadow_opacity
import org.jetbrains.compose.resources.stringResource

/**
 * The height this row occupies, which its callers reserve whether or not it is showing.
 *
 * The row folds out beside a "Shadow" checkbox that is a third its height, so a row sized to its
 * contents grows by 18dp the moment the box is ticked -- and because the controls it sits among are
 * bottom-aligned, the checkbox the operator just clicked jumps upwards under the cursor. Reserving
 * the space means ticking it changes what is drawn and nothing about where anything sits.
 *
 * Measured rather than derived, so `ShadowDetailRowHeightTest` pins it: it fails if this row grows
 * past the reservation, which would bring the jump straight back.
 */
val ShadowDetailRowHeight = 42.dp

/**
 * Shadow detail controls: color, size, and opacity — labels inside each field.
 * Appears below the shadow SettingRow when shadow is enabled.
 */
@Composable
fun ShadowDetailRow(
    shadowColor: String,
    shadowSize: Int,
    shadowOpacity: Int,
    onColorChange: (String) -> Unit,
    onSizeChange: (Int) -> Unit,
    onOpacityChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ColorPickerField(
            color = shadowColor,
            onColorChange = onColorChange,
            label = stringResource(Res.string.color).removeSuffix(":"),
            modifier = Modifier.widthIn(max = 150.dp)
        )
        NumberSettingsTextField(
            label = stringResource(Res.string.shadow_size),
            initialText = shadowSize,
            onValueChange = onSizeChange,
            range = 10..500
        )
        NumberSettingsTextField(
            label = stringResource(Res.string.shadow_opacity),
            initialText = shadowOpacity,
            onValueChange = onOpacityChange,
            range = 10..100
        )
    }
}
