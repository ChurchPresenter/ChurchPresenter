package org.churchpresenter.appsettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.runtime.Composable
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
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        GeneralToggleRow(Res.string.selection_goes_live, checked = settings.selectionGoesLive) { enabled ->
            onSettingsChange { it.copy(selectionGoesLive = enabled) }
        }
        RaisedButton(onClick = onKeyboardShortcuts) { Text(stringResource(Res.string.configure_shortcuts)) }
    }
}
