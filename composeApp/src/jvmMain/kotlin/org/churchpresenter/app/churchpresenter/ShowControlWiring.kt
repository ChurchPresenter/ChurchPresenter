package org.churchpresenter.app.churchpresenter

import org.churchpresenter.liveoutput.withPreviewMode
import org.churchpresenter.showcontrol.ActionRunner
import org.churchpresenter.app.churchpresenter.remote.AppShowHost
import org.churchpresenter.app.churchpresenter.remote.ShowOutlets
import org.churchpresenter.app.churchpresenter.remote.executeProjectItem
import org.churchpresenter.liveoutput.PreviewBus
import org.churchpresenter.liveoutput.cuedModeOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.schedule.ActionChoices
import org.churchpresenter.schedule.CompanionChoice
import org.churchpresenter.schedule.MessageChoice
import org.churchpresenter.settings.Macro
import org.churchpresenter.settings.macroNamed
import org.churchpresenter.settings.messageTokens
import java.io.File
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
import org.churchpresenter.atem.AtemConnectionManager
import org.churchpresenter.core.models.schedule.ScheduleItem
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
    // Preview mode is dev mode only; outside it the host sees it off, as the outputs do.
    settings = { if (devMode) appSettings else appSettings.withPreviewMode(false) },
    outlets = ShowOutlets(
        rows = { currentScheduleItems },
        currentRowId = { lastLiveRowId ?: selectedScheduleItemId },
        // A row an action puts live does not run its own actions: two rows naming each other loop.
        goLive = { item, plays -> projectFromCalendar(item, plays, runActions = false) },
        // A row next or previous reaches does, as one stepped to by hand does, a chain level deeper.
        rowActions = { item, depth -> runRowActionsNow(item, depth) },
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
        macro = { name -> appSettings.macros.macroNamed(name)?.actions },
        log = { Log.warn(SHOW_CONTROL_TAG, it) },
    ),
)

/**
 * Runs the [actions] of the schedule row [item] as it reaches the air: now, or -- when it has just
 * gone to Preview -- on the Take that puts it there. Firing the row again starts them over.
 */
internal fun AppRootState.runRowActions(item: ScheduleItem, actions: List<Action>) {
    if (devMode) presenterManager.previewBus.runOnAir(item, actions) { list, key -> showRunner.run(list, key) }
}

/**
 * Runs the actions of the schedule row [item] now -- it has gone straight to air. [chainDepth] is
 * how many row-action lists led here; past [ActionRunner.MAX_CHAIN_DEPTH] the chain stops, so two
 * rows that step to each other cannot loop.
 */
internal fun AppRootState.runRowActionsNow(item: ScheduleItem, chainDepth: Int = -1) {
    if (!devMode) return
    val actions = currentScheduleActions.currentActions()[item.id].orEmpty()
    if (actions.isEmpty()) return
    if (chainDepth + 1 >= ActionRunner.MAX_CHAIN_DEPTH) {
        Log.warn(SHOW_CONTROL_TAG, "Row ${item.id} not run: its actions step through rows more than 8 deep")
        return
    }
    showRunner.run(actions, item.id, chainDepth + 1)
}

/** Runs [macro]'s actions; pressing it again while it is still going starts it over. */
internal fun AppRootState.runMacro(macro: Macro) {
    if (!devMode) return
    showRunner.run(macro.actions, "macro:${macro.id}")
}

/** Hands [actions] to [run], keyed by [item]'s id, once [item] reaches the air -- see [PreviewBus.onAir]. */
internal fun PreviewBus.runOnAir(item: ScheduleItem, actions: List<Action>, run: (List<Action>, String) -> Unit) {
    if (actions.isEmpty()) return
    onAir(cuedModeOf(item)) { run(actions, item.id) }
}

/**
 * Stops the action lists still running -- their waits included -- whenever an operator clears the
 * outputs. A clear that a list asks for itself, or that a media file ending asks for, leaves them be.
 */
@Composable
internal fun MainWindowScope.ShowControlEffects() {
    LaunchedEffect(root) {
        snapshotFlow { root.presenterManager.operatorClears.intValue }
            .drop(1)
            .collect { root.showRunner.cancelAll() }
    }
}

/** What the row-action editor offers: the saved things in settings, the lower thirds on disk, OBS and Companion. */
@Composable
internal fun MainWindowScope.rememberActionChoices(): ActionChoices {
    val settings = root.appSettings
    val folder = settings.streamingSettings.lowerThirdFolder
    val lowerThirds by produceState(emptyList<String>(), folder) {
        value = withContext(Dispatchers.IO) { lowerThirdPresetNames(File(folder)) }
    }
    val obsScenes = root.obsManager.scenes.value
    return remember(settings, lowerThirds, obsScenes) {
        ActionChoices(
            clearGroups = settings.clearGroups.map { it.name },
            messages = settings.messageTemplates.map { MessageChoice(it.name, messageTokens(it.text)) },
            props = settings.props.map { it.name },
            lowerThirds = lowerThirds,
            obsScenes = obsScenes,
            companion = settings.companionSatelliteConnections.map { CompanionChoice(it.id, it.name) },
            macros = settings.macros.map { it.name },
            onOpen = root.obsManager::requestScenes,
        )
    }
}

/** The lower-third presets in [folder], by the name a lower-third action runs them by. */
internal fun lowerThirdPresetNames(folder: File): List<String> =
    folder.listFiles()?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
        ?.map { it.nameWithoutExtension }?.sorted().orEmpty()

/** Presses the button [press] names on the first surface of its connection that is showing. */
private fun AppRootState.pressCompanionButton(press: Action.CompanionPress): Boolean {
    val placements = CompanionSurfacePlacement.entries
        .filter { press.placement.isBlank() || it.name.equals(press.placement, ignoreCase = true) }
    return placements.any { placement ->
        companionSatelliteViewModel.pressButton(CompanionSurfaceSlot(press.connection, placement), press.button) != null
    }
}

private const val SHOW_CONTROL_TAG = "ShowControl"
