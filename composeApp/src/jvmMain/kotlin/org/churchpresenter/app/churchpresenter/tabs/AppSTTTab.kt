package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.churchpresenter.app.churchpresenter.dialogs.STTSettingsDialog
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.stt.STTManager
import org.churchpresenter.stt.STTTab

/**
 * The `:stt` tab with the app's own parts filled in: what the output is presenting, and the caption
 * settings dialog, which edits the output profiles the app owns.
 */
@Composable
fun AppSTTTab(
    sttManager: STTManager,
    presenterManager: PresenterManager,
    presenting: (Presenting) -> Unit,
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    STTTab(
        modifier = modifier,
        sttManager = sttManager,
        captionsLive = remember(presenterManager) { derivedStateOf { presenterManager.isLive(Presenting.STT) } },
        presenting = presenting,
        appSettings = appSettings,
        onSettingsChange = onSettingsChange,
        settingsDialog = { onDismiss ->
            STTSettingsDialog(
                appSettings = appSettings,
                onSettingsChange = onSettingsChange,
                onDismiss = onDismiss,
            )
        },
    )
}
