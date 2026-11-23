package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import org.churchpresenter.appsettings.SystemSettingsTab
import org.churchpresenter.dialogs.KeyboardShortcutsDialog
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.settings.AppSettings

@Composable
internal fun ShortcutSystemSettings(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    companionServer: CompanionServer,
) {
    var shortcuts by remember { mutableStateOf(false) }
    SystemSettingsTab(settings, onSettingsChange, companionServer, onKeyboardShortcuts = { shortcuts = true })
    KeyboardShortcutsDialog(isVisible = shortcuts, settings = settings,
        onSave = { updated -> onSettingsChange { updated } }, onDismiss = { shortcuts = false })
}
