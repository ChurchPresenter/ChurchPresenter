package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CompanionSatelliteSettings
import org.churchpresenter.app.churchpresenter.viewmodel.CompanionSatelliteViewModel

/**
 * Reconciles the configured Companion Satellite connections with the ones actually running, so an
 * edited or removed connection is applied without a restart.
 *
 * Lifted out of `main()`: an ordinary effect with no window attached, so unlike the rest of
 * main.kt it can be composed — and tested — on its own.
 */
@Composable
internal fun CompanionSatelliteWiring(
    appSettings: AppSettings,
    companionSatelliteViewModel: CompanionSatelliteViewModel,
    lastReconciled: MutableMap<String, CompanionSatelliteSettings>,
) {
    LaunchedEffect(appSettings.companionSatelliteConnections) {
        for (connection in appSettings.companionSatelliteConnections) {
            val hasLiveSlot = companionSatelliteViewModel.connectionStates.keys.any { it.connectionId == connection.id }
            // A connection seen before by this effect with different settings than last time
            // was actively edited by the user just now (not merely observed for the first time
            // at startup) — treat that the same as toggling the placement checkbox itself: an
            // explicit action, so it should connect even if autoConnect is off and nothing was
            // live yet. A brand-new/never-before-seen connection still only auto-connects when
            // autoConnect is set, preserving startup's opt-in-only behavior (handled primarily
            // by the auto-connect-once effect above).
            if (shouldConnectCompanion(
                    hasLiveSlot = hasLiveSlot,
                    autoConnect = connection.autoConnect,
                    lastSeen = lastReconciled[connection.id],
                    current = connection,
                )
            ) {
                companionSatelliteViewModel.connectAll(connection)
            }
            lastReconciled[connection.id] = connection
        }
    }
}
