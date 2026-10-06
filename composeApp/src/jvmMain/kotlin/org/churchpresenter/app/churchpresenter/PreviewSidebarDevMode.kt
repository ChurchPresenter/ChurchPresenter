package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.FlowRowScope
import org.churchpresenter.strings.generated.resources.preview_mode
import org.churchpresenter.strings.generated.resources.dev_mode_only
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import org.churchpresenter.app.churchpresenter.dialogs.ControlPanelData
import org.churchpresenter.app.churchpresenter.dialogs.ControlPanelActions
import org.churchpresenter.app.churchpresenter.dialogs.ControlDialog
import org.churchpresenter.controlin.MidiPorts
import org.churchpresenter.controlin.ControlHub
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import org.churchpresenter.strings.generated.resources.tooltip_macros
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.sharedui.composables.TooltipIconButton
import org.churchpresenter.app.churchpresenter.dialogs.MacrosDialog
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.Macro
import org.jetbrains.compose.resources.stringResource

/**
 * The features that are built but not ready for production, together in one outlined box headed
 * "Dev mode only" (AGENT.md). It is drawn only in dev mode; a new unfinished feature joins it until
 * it is approved for release.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DevModeBox(content: @Composable FlowRowScope.() -> Unit) {
    Column(
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag(DEV_MODE_BOX_TAG),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            stringResource(Res.string.dev_mode_only),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) { content() }
    }
}

/** Switches preview mode, lit while it is on: what goes live is cued on Preview and taken with Take. */
@Composable
internal fun PreviewModeToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    TooltipIconButton(
        painter = rememberVectorPainter(Icons.Outlined.Preview),
        text = stringResource(Res.string.preview_mode),
        onClick = { onChange(!on) },
        buttonSize = 36.dp,
        iconTint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag(PREVIEW_MODE_TOGGLE_TAG),
    )
}

internal const val DEV_MODE_BOX_TAG = "preview_dev_mode_box"
internal const val PREVIEW_MODE_TOGGLE_TAG = "preview_mode_toggle"

/** What the sidebar's show-control buttons need: the schedule's rows, running a macro, and the MIDI/OSC hub. */
internal class SidebarShowControl(
    val rows: List<ScheduleItem> = emptyList(),
    val onRunMacro: (Macro) -> Unit = {},
    val controlHub: ControlHub? = null,
    /** Dev mode: the Dev mode only box, and what is in it, is drawn. */
    val devMode: Boolean = false,
)

/** Opens the Macros dialog: the named action lists, to run or edit. */
@Composable
internal fun MacrosButton(
    appSettings: AppSettings,
    showControl: SidebarShowControl,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var controlOpen by remember { mutableStateOf(false) }
    TooltipIconButton(
        painter = rememberVectorPainter(Icons.Outlined.PlayCircle),
        text = stringResource(Res.string.tooltip_macros),
        onClick = { open = true },
        buttonSize = 36.dp,
        iconTint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag(MACROS_BUTTON_TAG),
    )
    MacrosDialog(
        isVisible = open,
        macros = appSettings.macros,
        rows = showControl.rows,
        onMacrosChange = { macros -> onSettingsChange { it.copy(macros = macros) } },
        onRun = showControl.onRunMacro,
        onDismiss = { open = false },
        onOpenControl = showControl.controlHub?.let { { controlOpen = true } },
    )
    showControl.controlHub?.let { hub ->
        ControlSetup(controlOpen, hub, appSettings, showControl.rows, onSettingsChange) { controlOpen = false }
    }
}

/** The MIDI & OSC dialog over [hub]: the devices there are, how the ports stand, Learn, and saving. */
@Composable
private fun ControlSetup(
    visible: Boolean,
    hub: ControlHub,
    appSettings: AppSettings,
    scheduleRows: List<ScheduleItem>,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val status by hub.status.collectAsState()
    val learning by hub.isLearning.collectAsState()
    // A learned trigger arrives on the device's thread; it is handed to the dialog on the UI's.
    val uiScope = rememberCoroutineScope()
    // Asking the system for its devices can block, so it is done off the UI thread, once per opening.
    val devices by produceState(emptyList<String>() to emptyList(), visible) {
        if (visible) value = withContext(Dispatchers.IO) { MidiPorts.inputNames() to MidiPorts.outputNames() }
    }
    ControlDialog(
        isVisible = visible,
        settings = appSettings.control,
        data = ControlPanelData(status, devices.first, devices.second, scheduleRows),
        actions = ControlPanelActions(
            isLearning = learning,
            onLearn = { onTrigger -> hub.learn { trigger -> uiScope.launch { onTrigger(trigger) } } },
            onCancelLearn = hub::cancelLearn,
            onSave = { saved -> onSettingsChange { it.copy(control = saved) } },
        ),
        onDismiss = onDismiss,
    )
}

internal const val MACROS_BUTTON_TAG = "preview_macros"

/** What the sidebar's show-control buttons need from the main screen, over [rows] of the schedule. */
internal fun LiveOutputCallbacks.showControlFor(rows: List<ScheduleItem>) =
    SidebarShowControl(rows, onRunMacro, controlHub, devMode)
