package org.churchpresenter.app.churchpresenter

import org.churchpresenter.settings.effectiveAdminPassword
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.obs.obsSceneFor
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.obs.OBSWebSocketManager
import org.churchpresenter.liveoutput.PresenterManager
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
        snapshotFlow { presenterManager.lastLive.value }
            .collect { mode ->
                val sceneName = obsSceneFor(mode, appSettings.obsSettings) ?: return@collect
                obsManager.setScene(sceneName)
            }
    }
    // Sync QA settings to server — admin auth takes the Q&A password, or the server API key without one
    LaunchedEffect(
        appSettings.serverSettings.apiKeyEnabled,
        appSettings.serverSettings.apiKey,
        appSettings.qaSettings.adminPassword,
        appSettings.qaSettings.rateLimitCooldownSeconds,
        appSettings.qaSettings.votingEnabled
    ) {
        companionServer.qaAdminPassword = appSettings.qaSettings.effectiveAdminPassword(appSettings.serverSettings)
        companionServer.qaAdminPasswordTrusted = appSettings.qaSettings.adminPassword.isNotEmpty()
        companionServer.qaCooldownSeconds = appSettings.qaSettings.rateLimitCooldownSeconds
        companionServer.qaVotingEnabled = appSettings.qaSettings.votingEnabled
    }
}
