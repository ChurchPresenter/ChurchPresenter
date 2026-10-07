package org.churchpresenter.app.churchpresenter

import org.churchpresenter.controlin.ControlHub
import org.churchpresenter.controlin.ControlSettings
import org.churchpresenter.liveoutput.PresenterManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import org.churchpresenter.controlin.ControlMapping
import org.churchpresenter.controlin.OutputEvents
import org.churchpresenter.sharedui.models.Presenting

/**
 * Opens the MIDI and OSC ports the settings name and sends the show's events out of them
 * (`docs/SHOW_CONTROL.md`, MIDI and OSC). One of MainDesktop's wiring pieces, under the standing
 * exception AGENT.md records for them.
 */
@Composable
internal fun MainWindowScope.ControlInEffects() {
    // MIDI and OSC are dev mode only: outside it every port stays closed.
    val control = if (root.devMode) root.appSettings.control else ControlSettings()
    ControlInEffects(root.controlHub, control, root.presenterManager)
}

/** Keeps [hub]'s ports as [control] names them, and sends the show's events out of them. */
@Composable
internal fun ControlInEffects(hub: ControlHub, control: ControlSettings, presenterManager: PresenterManager) {
    DisposableEffect(hub) { onDispose { hub.close() } }
    // Opening a port can block on a device, so it is done off the UI thread.
    LaunchedEffect(control) { withContext(Dispatchers.IO) { hub.apply(control) } }
    LaunchedEffect(hub) {
        var before = emptySet<Presenting>()
        snapshotFlow { presenterManager.liveContent.value }.collect { after ->
            liveOutputEvents(before, after).forEach(hub::emit)
            before = after
        }
    }
    LaunchedEffect(hub) {
        snapshotFlow { presenterManager.previewBus.takes }.drop(1).collect { hub.emit(OutputEvents.TAKE) }
    }
}

/** The show events that took place as what is on air went from [before] to [after]. */
internal fun liveOutputEvents(before: Set<Presenting>, after: Set<Presenting>): List<String> = buildList {
    if ((after - before).isNotEmpty()) add(OutputEvents.GO_LIVE)
    if (before.isNotEmpty() && after.isEmpty()) add(OutputEvents.CLEAR)
}

/** Runs what [mapping]'s trigger is set to do, as a macro would run, from the operator's own scope. */
internal fun AppRootState.runControlMapping(mapping: ControlMapping) {
    showRunner.run(mapping.actions, "control:${mapping.id}")
}
