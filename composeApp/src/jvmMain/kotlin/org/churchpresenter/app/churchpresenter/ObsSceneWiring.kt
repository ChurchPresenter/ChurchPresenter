package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.obsSceneFor
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.app.churchpresenter.viewmodel.OBSWebSocketManager
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager

/**
 * Keeps OBS in step with what is live: connects when the settings say so, and switches scenes as
 * the presented content changes.
 *
 * Lifted out of `main()`: an ordinary effect with no window attached, so unlike the rest of
 * main.kt it can be composed — and tested — on its own.
 */
@Composable
internal fun ObsSceneWiring(
    appSettings: AppSettings,
    companionServer: CompanionServer,
    obsManager: OBSWebSocketManager,
    presenterManager: PresenterManager,
) {
    LaunchedEffect(
        appSettings.obsSettings.enabled,
        appSettings.obsSettings.host,
        appSettings.obsSettings.port,
        appSettings.obsSettings.password
    ) {
        if (shouldConnectObs(appSettings.obsSettings)) {
            obsManager.connect(
                appSettings.obsSettings.host,
                appSettings.obsSettings.port,
                appSettings.obsSettings.password
            )
        } else {
            obsManager.disconnect()
        }
    }
    // Switch OBS scene when presenting mode changes
    LaunchedEffect(Unit) {
        snapshotFlow { presenterManager.presentingMode.value }
            .collect { mode ->
                val sceneName = obsSceneFor(mode, appSettings.obsSettings) ?: return@collect
                obsManager.setScene(sceneName)
            }
    }
    // Sync QA settings to server — admin auth reuses the server API key, just like the presentation remote
    LaunchedEffect(appSettings.serverSettings.apiKeyEnabled,
        appSettings.serverSettings.apiKey,
        appSettings.qaSettings.rateLimitCooldownSeconds,
        appSettings.qaSettings.votingEnabled) {
        companionServer.qaAdminPassword = activeApiKey(appSettings.serverSettings)
        companionServer.qaCooldownSeconds = appSettings.qaSettings.rateLimitCooldownSeconds
        companionServer.qaVotingEnabled = appSettings.qaSettings.votingEnabled
    }
}
