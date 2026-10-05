package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.remote.AppShowHost
import org.churchpresenter.app.churchpresenter.remote.ShowOutlets
import org.churchpresenter.app.churchpresenter.remote.executeProjectItem
import org.churchpresenter.app.churchpresenter.viewmodel.cuedModeOf
import org.churchpresenter.atem.AtemConnectionManager
import org.churchpresenter.core.models.companion.CompanionSurfacePlacement
import org.churchpresenter.core.models.companion.CompanionSurfaceSlot
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.showcontrol.Action
import org.churchpresenter.showcontrol.MediaCommand
import org.churchpresenter.showcontrol.ShowHost

/**
 * The show-control host over the root's own state (`docs/SHOW_CONTROL.md`, Actions): what an
 * action does to the schedule, the media, OBS, the ATEM and Companion. One of MainDesktop's wiring
 * pieces, under the standing exception AGENT.md records for them.
 */
internal fun AppRootState.appShowHost(): ShowHost = AppShowHost(
    presenterManager = presenterManager,
    settings = { appSettings },
    outlets = ShowOutlets(
        rows = { currentScheduleItems },
        currentRowId = { engineLiveItem?.id ?: selectedScheduleItemId },
        goLive = this::projectFromCalendar,
        toPreview = { item ->
            executeProjectItem(
                item,
                currentScheduleActions,
                presenterManager.previewBus.forNewItem(cuedModeOf(item)),
                statisticsManager,
            )
        },
        media = { command ->
            when (command) {
                MediaCommand.PLAY -> mediaViewModel.play()
                MediaCommand.PAUSE -> mediaViewModel.pause()
                MediaCommand.STOP -> mediaViewModel.stop()
            }
        },
        obsScene = obsManager::setScene,
        companion = ::pressCompanionButton,
        atem = { block ->
            val atem = appSettings.atemSettings
            require(atem.host.isNotBlank()) { "No ATEM switcher is set up" }
            AtemConnectionManager.use(atem.host, atem.port, needsState = false) { block(it) }
        },
        log = { Log.warn(SHOW_CONTROL_TAG, it) },
    ),
)

/** Presses the button [press] names on the first surface of its connection that is showing. */
private fun AppRootState.pressCompanionButton(press: Action.CompanionPress): Boolean {
    val placements = CompanionSurfacePlacement.entries
        .filter { press.placement.isBlank() || it.name.equals(press.placement, ignoreCase = true) }
    return placements.any { placement ->
        companionSatelliteViewModel.pressButton(CompanionSurfaceSlot(press.connection, placement), press.button) != null
    }
}

private const val SHOW_CONTROL_TAG = "ShowControl"
