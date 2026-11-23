package org.churchpresenter.appsettings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.selection_goes_live
import org.churchpresenter.strings.generated.resources.configure_shortcuts
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SelectionSettings(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onKeyboardShortcuts: () -> Unit,
) {
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.selection_goes_live), modifier = Modifier.weight(1f))
        Switch(settings.selectionGoesLive, onCheckedChange = { enabled ->
            onSettingsChange { it.copy(selectionGoesLive = enabled) }
        })
        TextButton(onClick = onKeyboardShortcuts) { Text(stringResource(Res.string.configure_shortcuts)) }
    }
}
