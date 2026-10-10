package org.churchpresenter.app.churchpresenter

import org.churchpresenter.atem.AtemClient
import org.churchpresenter.atem.AtemConnectionManager
import org.churchpresenter.core.models.companion.CompanionSurfacePlacement
import org.churchpresenter.core.models.companion.CompanionSurfaceSlot
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.liveoutput.withPreviewMode
import org.churchpresenter.schedule.ActionChoices
import org.churchpresenter.schedule.CompanionChoice
import org.churchpresenter.schedule.MessageChoice
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.AtemSettings
import org.churchpresenter.settings.messageTokens
import org.churchpresenter.showcontrol.Action
import org.churchpresenter.showcontrol.ActionRunner
import org.churchpresenter.showcontrol.MediaCommand

// The decisions ShowControlWiring takes, apart from the root state they are wired to.

internal fun showHostSettings(devMode: Boolean, settings: AppSettings): AppSettings =
    if (devMode) settings else settings.withPreviewMode(false)

/** Hands [actions] of row [rowId] to [run] one chain level below [chainDepth], unless that is too deep. */
internal fun runRowActionsChained(
    rowId: String,
    actions: List<Action>,
    chainDepth: Int,
    run: (List<Action>, String, Int) -> Unit,
) {
    if (actions.isEmpty()) return
    if (chainDepth + 1 >= ActionRunner.MAX_CHAIN_DEPTH) {
        Log.warn(SHOW_CONTROL_TAG, "Row $rowId not run: its actions step through rows more than 8 deep")
        return
    }
    run(actions, rowId, chainDepth + 1)
}

/** What a media action does: [play], [pause] or [stop] the media player. */
internal fun mediaOutlet(play: () -> Unit, pause: () -> Unit, stop: () -> Unit): (MediaCommand) -> Unit =
    { command ->
        when (command) {
            MediaCommand.PLAY -> play()
            MediaCommand.PAUSE -> pause()
            MediaCommand.STOP -> stop()
        }
    }

/** Runs [block] on the switcher [atem] names; there is none to run it on until one is set up. */
internal suspend fun runOnAtem(atem: AtemSettings, block: suspend (AtemClient) -> Unit) {
    require(atem.host.isNotBlank()) { "No ATEM switcher is set up" }
    AtemConnectionManager.use(atem.host, atem.port, needsState = false) { block(it) }
}

/** The choices the row-action editor offers: what [settings] holds, plus the lower thirds and OBS scenes found. */
internal fun actionChoices(
    settings: AppSettings,
    lowerThirds: List<String>,
    obsScenes: List<String>,
    onOpen: () -> Unit,
) = ActionChoices(
    clearGroups = settings.clearGroups.map { it.name },
    messages = settings.messageTemplates.map { MessageChoice(it.name, messageTokens(it.text)) },
    props = settings.props.map { it.name },
    lowerThirds = lowerThirds,
    obsScenes = obsScenes,
    companion = settings.companionSatelliteConnections.map { CompanionChoice(it.id, it.name) },
    macros = settings.macros.map { it.name },
    onOpen = onOpen,
)

/** True once [pressButton] answers for a surface of [press]'s connection -- a blank placement tries each. */
internal fun pressOnSurface(
    press: Action.CompanionPress,
    pressButton: (CompanionSurfaceSlot, Int) -> Any?,
): Boolean = CompanionSurfacePlacement.entries
    .filter { press.placement.isBlank() || it.name.equals(press.placement, ignoreCase = true) }
    .any { placement -> pressButton(CompanionSurfaceSlot(press.connection, placement), press.button) != null }
