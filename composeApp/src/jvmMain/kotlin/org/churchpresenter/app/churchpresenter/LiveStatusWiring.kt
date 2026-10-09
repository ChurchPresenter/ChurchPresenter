package org.churchpresenter.app.churchpresenter

import org.churchpresenter.server.updateBackgroundSettings
import org.churchpresenter.server.updateBrowserSourceOutputs
import org.churchpresenter.server.updatePresentationLiveStatus
import org.churchpresenter.server.updateTransposeControls
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.offersTranspose
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.profileFor
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.server.CompanionServer

/** The Browser Source outputs whose profile [offersTranspose]. */
internal fun transposeControlOutputs(projection: ProjectionSettings): Set<Int> =
    projection.browserSourceOutputs.indices.filterTo(mutableSetOf()) { i ->
        projection.profileFor(projection.browserSourceOutputs[i])?.offersTranspose() == true
    }

/**
 * Tells connected companions what is live and keeps the Browser Source outputs and background
 * settings in step with the app's own.
 *
 * Lifted out of `main()`: ordinary effects with no window attached, so unlike the rest of main.kt
 * they can be composed — and tested — on their own.
 */
@Composable
internal fun LiveStatusWiring(
    appSettings: AppSettings,
    companionServer: CompanionServer,
    slideContentValue: Presenting,
) {
    LaunchedEffect(slideContentValue) {
        companionServer.updatePresentationLiveStatus(isPresentationLive(slideContentValue))
    }
    // ── Browser Source outputs (OBS/vMix overlay) ─────────────────────────────
    // Each output gets its own off-screen renderer (BrowserSourceVideoRenderer) that
    // renders the same BiblePresenter/SongPresenter/AnnouncementsPresenter/PicturePresenter/
    // StageMonitorScreen composables used everywhere else, streamed to CompanionServer as
    // PNG frames — pixel-identical to the native output, no separate styling logic to
    // maintain. PresenterManager itself never leaves this scope; only each renderer's
    // frame flow crosses into CompanionServer.
    LaunchedEffect(appSettings.projectionSettings.browserSourceOutputs) {
        companionServer.updateBrowserSourceOutputs(appSettings.projectionSettings.browserSourceOutputs)
    }
    LaunchedEffect(appSettings.projectionSettings) {
        companionServer.updateTransposeControls(transposeControlOutputs(appSettings.projectionSettings))
    }
    LaunchedEffect(appSettings.backgroundSettings) {
        companionServer.updateBackgroundSettings(appSettings.backgroundSettings)
    }
}
