package org.churchpresenter.signage.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.signage.core.Orientation
import org.churchpresenter.sharedui.composables.DropdownSettingsField
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.save
import org.churchpresenter.strings.generated.resources.signage_factory_reset
import org.churchpresenter.strings.generated.resources.signage_factory_reset_hint
import org.churchpresenter.strings.generated.resources.signage_orientation
import org.churchpresenter.strings.generated.resources.signage_orientation_hint
import org.churchpresenter.strings.generated.resources.signage_orientation_landscape
import org.churchpresenter.strings.generated.resources.signage_orientation_landscape_flipped
import org.churchpresenter.strings.generated.resources.signage_orientation_portrait
import org.churchpresenter.strings.generated.resources.signage_orientation_portrait_flipped
import org.churchpresenter.strings.generated.resources.signage_screen_name
import org.churchpresenter.strings.generated.resources.signage_screen_settings
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A screen's own settings: its name, and how it is mounted, which turns everything it shows. Factory
 * reset sits at the bottom, apart from Save, and still asks before it does anything.
 */
@Composable
internal fun ScreenSettingsDialog(
    name: String,
    orientation: Orientation,
    onSave: (name: String, orientation: Orientation) -> Unit,
    onFactoryReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf(name) }
    var mounted by remember { mutableStateOf(orientation) }
    val labels = Orientation.entries.associateWith { stringResource(labelOf(it)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.signage_screen_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.width(DIALOG_WIDTH)) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(Res.string.signage_screen_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownSettingsField(
                    value = labels.getValue(mounted),
                    options = Orientation.entries.map(labels::getValue),
                    onValueChange = { picked ->
                        labels.entries.firstOrNull { it.value == picked }?.let { mounted = it.key }
                    },
                    label = stringResource(Res.string.signage_orientation),
                )
                Hint(stringResource(Res.string.signage_orientation_hint))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                OutlinedButton(onClick = {
                    onDismiss()
                    onFactoryReset()
                }) {
                    Text(stringResource(Res.string.signage_factory_reset), color = MaterialTheme.colorScheme.error)
                }
                Hint(stringResource(Res.string.signage_factory_reset_hint))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(newName.trim(), mounted)
                    onDismiss()
                },
                enabled = newName.isNotBlank(),
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun labelOf(orientation: Orientation): StringResource = when (orientation) {
    Orientation.LANDSCAPE -> Res.string.signage_orientation_landscape
    Orientation.PORTRAIT -> Res.string.signage_orientation_portrait
    Orientation.LANDSCAPE_FLIPPED -> Res.string.signage_orientation_landscape_flipped
    Orientation.PORTRAIT_FLIPPED -> Res.string.signage_orientation_portrait_flipped
}

private val DIALOG_WIDTH = 440.dp
