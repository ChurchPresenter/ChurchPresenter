package org.churchpresenter.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.controlin.ControlSettings
import org.churchpresenter.controlin.ControlStatus
import org.churchpresenter.controlin.PortState
import org.churchpresenter.controlin.Trigger
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import org.churchpresenter.sharedui.utils.centeredOnMainWindow
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.close
import org.churchpresenter.strings.generated.resources.control_device_none
import org.churchpresenter.strings.generated.resources.control_hint
import org.churchpresenter.strings.generated.resources.control_midi_input
import org.churchpresenter.strings.generated.resources.control_midi_output
import org.churchpresenter.strings.generated.resources.control_osc_in_port
import org.churchpresenter.strings.generated.resources.control_osc_out_host
import org.churchpresenter.strings.generated.resources.control_osc_out_port
import org.churchpresenter.strings.generated.resources.control_save
import org.churchpresenter.strings.generated.resources.control_state_failed
import org.churchpresenter.strings.generated.resources.control_state_off
import org.churchpresenter.strings.generated.resources.control_state_open
import org.churchpresenter.strings.generated.resources.control_tab_outputs
import org.churchpresenter.strings.generated.resources.control_tab_ports
import org.churchpresenter.strings.generated.resources.control_tab_triggers
import org.churchpresenter.strings.generated.resources.control_title
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.ProvideUiFontScale
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.components.SettingsTextField
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What the MIDI & OSC dialog shows: how the ports stand, the devices there are, and the schedule's rows. */
class ControlPanelData(
    val status: ControlStatus = ControlStatus(),
    val midiInputs: List<String> = emptyList(),
    val midiOutputs: List<String> = emptyList(),
    val rows: List<ScheduleItem> = emptyList(),
)

/** What the MIDI & OSC dialog can ask for: Learn from the hub, and saving what it edited. */
class ControlPanelActions(
    val isLearning: Boolean = false,
    val onLearn: ((Trigger) -> Unit) -> Unit = {},
    val onCancelLearn: () -> Unit = {},
    val onSave: (ControlSettings) -> Unit = {},
)

/** What the Triggers tab needs from its dialog: the schedule rows an action can name, and the hub's Learn. */
internal class TriggerLearning(
    val isLearning: Boolean,
    val onLearn: ((Trigger) -> Unit) -> Unit,
    val onCancel: () -> Unit,
)

/** Sets up MIDI and OSC (`docs/SHOW_CONTROL.md`, MIDI and OSC): the ports, the triggers and the outputs. */
@Composable
fun ControlDialog(
    isVisible: Boolean,
    settings: ControlSettings,
    data: ControlPanelData,
    actions: ControlPanelActions,
    onDismiss: () -> Unit,
    /** The window it opens in -- see [DialogFrame]. */
    frame: DialogFrame = appDialogFrame,
) {
    if (!isVisible) return
    val dialogState = rememberDialogState(
        position = centeredOnMainWindow(LocalMainWindowState.current, CONTROL_WIDTH, CONTROL_HEIGHT),
        width = CONTROL_WIDTH,
        height = CONTROL_HEIGHT,
    )
    frame(
        DialogFrameSpec(
            onClose = {
                actions.onCancelLearn()
                onDismiss()
            },
            state = dialogState,
            title = stringResource(Res.string.control_title),
        ),
    ) {
        ProvideUiFontScale { ControlDialogContent(settings, data, actions, onDismiss) }
    }
}

private enum class ControlTab(val label: StringResource) {
    PORTS(Res.string.control_tab_ports),
    TRIGGERS(Res.string.control_tab_triggers),
    OUTPUTS(Res.string.control_tab_outputs),
}

/** The dialog's body, apart from its window so it can be drawn headless. */
@Composable
internal fun ControlDialogContent(
    settings: ControlSettings,
    data: ControlPanelData,
    actions: ControlPanelActions,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableStateOf(ControlTab.PORTS) }
    var draft by remember(settings) { mutableStateOf(settings) }
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(stringResource(Res.string.control_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.padding(top = 4.dp))
            Text(
                stringResource(Res.string.control_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ControlTab.entries.forEach { entry ->
                    FilterChip(
                        selected = tab == entry,
                        onClick = {
                            actions.onCancelLearn()
                            tab = entry
                        },
                        label = { Text(stringResource(entry.label)) },
                        modifier = Modifier.testTag(controlTabTag(entry.name)),
                    )
                }
            }
            Spacer(Modifier.padding(top = 8.dp))
            HorizontalDivider()
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (tab) {
                    ControlTab.PORTS -> ControlPortsTab(draft, data) { draft = it }
                    ControlTab.TRIGGERS -> ControlTriggersTab(
                        mappings = draft.mappings,
                        rows = data.rows,
                        learning = TriggerLearning(actions.isLearning, actions.onLearn, actions.onCancelLearn),
                    ) { draft = draft.copy(mappings = it) }
                    ControlTab.OUTPUTS -> ControlOutputsTab(draft.outputs) { draft = draft.copy(outputs = it) }
                }
            }
            HorizontalDivider()
            Spacer(Modifier.padding(top = 12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.weight(1f))
                GhostButton(shape = AppShape(6.dp), onClick = {
                    actions.onCancelLearn()
                    onDismiss()
                }) { Text(stringResource(Res.string.close)) }
                RaisedButton(
                    shape = AppShape(6.dp),
                    onClick = {
                        actions.onSave(draft)
                    },
                    modifier = Modifier.testTag(CONTROL_SAVE_TAG),
                ) { Text(stringResource(Res.string.control_save)) }
            }
        }
    }
}

/** The four ports: a MIDI device each way, the OSC port to listen on, and where OSC is sent. */
@Composable
private fun ControlPortsTab(draft: ControlSettings, data: ControlPanelData, onChange: (ControlSettings) -> Unit) {
    val none = stringResource(Res.string.control_device_none)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PortRow(data.status.midiInput) {
            ControlDropdown(
                label = stringResource(Res.string.control_midi_input),
                value = draft.midiInput,
                options = devices(none, data.midiInputs, draft.midiInput),
                onPick = { onChange(draft.copy(midiInput = it)) },
            )
        }
        PortRow(data.status.midiOutput) {
            ControlDropdown(
                label = stringResource(Res.string.control_midi_output),
                value = draft.midiOutput,
                options = devices(none, data.midiOutputs, draft.midiOutput),
                onPick = { onChange(draft.copy(midiOutput = it)) },
            )
        }
        PortRow(data.status.oscInput) {
            IntField(stringResource(Res.string.control_osc_in_port), draft.oscInPort) {
                onChange(draft.copy(oscInPort = it.coerceAtMost(PORT_MAX)))
            }
        }
        PortRow(data.status.oscOutput) {
            SettingsTextField(
                value = draft.oscOutHost,
                onValueChange = { onChange(draft.copy(oscOutHost = it.trim())) },
                label = stringResource(Res.string.control_osc_out_host),
                modifier = Modifier.width(HOST_WIDTH),
            )
            IntField(stringResource(Res.string.control_osc_out_port), draft.oscOutPort) {
                onChange(draft.copy(oscOutPort = it.coerceAtMost(PORT_MAX)))
            }
        }
    }
}

/** One port's fields, then how it stands. */
@Composable
private fun PortRow(state: PortState, fields: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        fields()
        Text(
            stringResource(
                when (state) {
                    PortState.OFF -> Res.string.control_state_off
                    PortState.OPEN -> Res.string.control_state_open
                    PortState.FAILED -> Res.string.control_state_failed
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (state == PortState.FAILED) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** Off, then the devices there are -- and the saved one even when it is unplugged, so it still shows. */
private fun devices(none: String, names: List<String>, saved: String): List<Pair<String, String>> =
    listOf("" to none) + (names + saved).filter { it.isNotBlank() }.distinct().map { it to it }

internal const val CONTROL_SAVE_TAG = "control_save"

internal fun controlTabTag(name: String) = "control_tab_$name"

private val CONTROL_WIDTH = 720.dp
private val CONTROL_HEIGHT = 640.dp
private val HOST_WIDTH = 200.dp
private const val PORT_MAX = 65_535
