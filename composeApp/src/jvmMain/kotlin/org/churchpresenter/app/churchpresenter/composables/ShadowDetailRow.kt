package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
 * Shadow detail controls: color, size, and intensity — each with a label above.
 * Appears below the Color + TextStyleButtons row when shadow is enabled.
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
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(Res.string.color),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ColorPickerField(
                color = shadowColor,
                onColorChange = onColorChange
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(Res.string.shadow_size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            NumberSettingsTextField(
                modifier = Modifier.width(60.dp),
                initialText = shadowSize,
                onValueChange = onSizeChange,
                range = 10..500
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(Res.string.shadow_opacity),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            NumberSettingsTextField(
                modifier = Modifier.width(60.dp),
                initialText = shadowOpacity,
                onValueChange = onOpacityChange,
                range = 10..100
            )
        }
    }
}
