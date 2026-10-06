package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.churchpresenter.app.churchpresenter.dialogs.QARemoteDialog
import org.churchpresenter.server.TunnelStatus
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.qa.QAManager
import org.churchpresenter.qa.QATab
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting

/**
 * The `:qa` tab with the app's own parts filled in: the live output through
 * [PresenterManager.qaOutput], and the remote-access dialog, which needs the server and the tunnel.
 */
@Composable
fun AppQATab(
    qaManager: QAManager,
    presenterManager: PresenterManager,
    serverUrl: String,
    presenting: (Presenting) -> Unit,
    modifier: Modifier = Modifier,
    appSettings: AppSettings = AppSettings(),
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    tunnelStatus: TunnelStatus = TunnelStatus.Idle,
    tunnelUrl: String = "",
    onStartTunnel: () -> Unit = {},
    onStopTunnel: () -> Unit = {},
    qaDisplayUrl: String = "",
    onQaDisplayUrlChanged: (String) -> Unit = {},
) {
    QATab(
        modifier = modifier,
        qaManager = qaManager,
        output = presenterManager.qaOutput,
        serverUrl = serverUrl,
        presenting = presenting,
        appSettings = appSettings,
        onSettingsChange = onSettingsChange,
        remoteDialog = { onDismiss ->
            QARemoteDialog(
                serverUrl = serverUrl,
                qaDisplayUrl = qaDisplayUrl,
                onQaDisplayUrlChanged = onQaDisplayUrlChanged,
                apiKeyEnabled = appSettings.serverSettings.apiKeyEnabled,
                apiKey = appSettings.serverSettings.apiKey,
                tunnelStatus = tunnelStatus,
                tunnelUrl = tunnelUrl,
                onStartTunnel = onStartTunnel,
                onStopTunnel = onStopTunnel,
                qaSettings = appSettings.qaSettings,
                onSettingsChange = onSettingsChange,
                onDismiss = onDismiss,
            )
        },
    )
}
