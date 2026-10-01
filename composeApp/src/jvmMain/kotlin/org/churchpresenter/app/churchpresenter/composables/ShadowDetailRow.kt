package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.color
import org.churchpresenter.strings.generated.resources.shadow_size
import org.churchpresenter.strings.generated.resources.shadow_opacity
import org.jetbrains.compose.resources.stringResource

/**
 * Shadow detail controls: color, size, and opacity — labels inside each field.
 * Appears below the shadow SettingRow when shadow is enabled.
 *
 * Flowing rather than a hard row. The three fields are fixed-width, so a row narrower than they
 * come to squeezes them into their own padding instead of wrapping — which is what the per-output
 * Customize dialog's 430dp column did to every element that offers a shadow. Given room they still
 * lay out as one line, so nothing that already had the width changes.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically
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
