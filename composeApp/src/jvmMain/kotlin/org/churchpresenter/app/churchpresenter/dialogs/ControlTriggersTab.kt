package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import org.churchpresenter.controlin.ANY
import org.churchpresenter.controlin.ControlMapping
import org.churchpresenter.controlin.Trigger
import org.churchpresenter.controlin.TriggerKinds
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.schedule.ActionListEditor
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.control_field_address
import org.churchpresenter.strings.generated.resources.control_field_channel
import org.churchpresenter.strings.generated.resources.control_field_command
import org.churchpresenter.strings.generated.resources.control_field_controller
import org.churchpresenter.strings.generated.resources.control_field_cue
import org.churchpresenter.strings.generated.resources.control_field_note
import org.churchpresenter.strings.generated.resources.control_field_value
import org.churchpresenter.strings.generated.resources.control_kind
import org.churchpresenter.strings.generated.resources.control_learn
import org.churchpresenter.strings.generated.resources.control_learning
import org.churchpresenter.strings.generated.resources.control_name
import org.churchpresenter.strings.generated.resources.control_trigger_add
import org.churchpresenter.strings.generated.resources.control_trigger_save
import org.churchpresenter.strings.generated.resources.control_triggers_empty
import org.churchpresenter.strings.generated.resources.delete_saved_string
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.components.SettingsTextField
import org.jetbrains.compose.resources.stringResource

/** The saved triggers: each runs its actions when its note, control change, command or address arrives. */
@Composable
internal fun ControlTriggersTab(
    mappings: List<ControlMapping>,
    rows: List<ScheduleItem>,
    learning: TriggerLearning,
    onChange: (List<ControlMapping>) -> Unit,
) {
    var editing by remember { mutableStateOf<ControlMapping?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (mappings.isEmpty() && editing == null) {
            Text(stringResource(Res.string.control_triggers_empty), style = MaterialTheme.typography.bodyMedium)
        }
        mappings.forEach { mapping ->
            FilterChip(
                selected = editing?.id == mapping.id,
                onClick = {
                    learning.onCancel()
                    editing = mapping
                },
                label = { Text(mapping.name + triggerSuffix(mapping)) },
                modifier = Modifier.testTag(controlTriggerTag(mapping.id)),
            )
        }
        editing?.let { draft ->
            HorizontalDivider()
            TriggerEditor(draft, rows, learning) { editing = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostButton(
                shape = AppShape(6.dp),
                onClick = {
                    editing = ControlMapping(
                        id = nextControlId("trigger", mappings.map { it.id }),
                        trigger = Trigger(TriggerKinds.MIDI_NOTE),
                    )
                },
                modifier = Modifier.testTag(CONTROL_TRIGGER_ADD_TAG),
            ) { Text(stringResource(Res.string.control_trigger_add)) }
            editing?.let { draft ->
                val saved = mappings.any { it.id == draft.id }
                if (saved) {
                    GhostButton(shape = AppShape(6.dp), onClick = {
                        onChange(mappings.filterNot { it.id == draft.id })
                        editing = null
                    }) { Text(stringResource(Res.string.delete_saved_string)) }
                }
                RaisedButton(
                    shape = AppShape(6.dp),
                    onClick = {
                        learning.onCancel()
                        onChange(if (saved) mappings.map { if (it.id == draft.id) draft else it } else mappings + draft)
                        editing = null
                    },
                    modifier = Modifier.testTag(CONTROL_TRIGGER_SAVE_TAG),
                ) { Text(stringResource(Res.string.control_trigger_save)) }
            }
        }
    }
}

@Composable
private fun triggerSuffix(mapping: ControlMapping): String {
    val label = triggerLabel(mapping.trigger)
    return if (mapping.name.isBlank()) label else "  ·  $label"
}

/** The name, the trigger and the actions of the mapping being added or edited. */
@Composable
private fun TriggerEditor(
    draft: ControlMapping,
    rows: List<ScheduleItem>,
    learning: TriggerLearning,
    onChange: (ControlMapping) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsTextField(
            value = draft.name,
            onValueChange = { onChange(draft.copy(name = it)) },
            label = stringResource(Res.string.control_name),
            fillWidth = true,
            modifier = Modifier.fillMaxWidth().testTag(CONTROL_TRIGGER_NAME_TAG),
        )
        ControlDropdown(
            label = stringResource(Res.string.control_kind),
            value = draft.trigger.kind,
            options = triggerKindOptions(),
            onPick = { kind -> onChange(draft.copy(trigger = Trigger(kind))) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TriggerFields(draft.trigger) { onChange(draft.copy(trigger = it)) }
            Spacer(Modifier.weight(1f))
            LearnButton(learning) { learned -> onChange(draft.copy(trigger = learned)) }
        }
        ActionListEditor(draft.actions, rows, onChange = { onChange(draft.copy(actions = it)) })
    }
}

/** The fields of [trigger] that its kind has. */
@Composable
private fun TriggerFields(trigger: Trigger, onChange: (Trigger) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (trigger.kind) {
            TriggerKinds.MIDI_NOTE -> {
                IntField(stringResource(Res.string.control_field_channel), trigger.channel) {
                    onChange(trigger.copy(channel = it.coerceIn(0, MIDI_CHANNELS)))
                }
                IntField(stringResource(Res.string.control_field_note), trigger.number) {
                    onChange(trigger.copy(number = it.coerceIn(0, MIDI_DATA_MAX)))
                }
            }
            TriggerKinds.MIDI_CC -> {
                IntField(stringResource(Res.string.control_field_channel), trigger.channel) {
                    onChange(trigger.copy(channel = it.coerceIn(0, MIDI_CHANNELS)))
                }
                IntField(stringResource(Res.string.control_field_controller), trigger.number) {
                    onChange(trigger.copy(number = it.coerceIn(0, MIDI_DATA_MAX)))
                }
                IntField(stringResource(Res.string.control_field_value), trigger.value, anyWhenBlank = true) {
                    onChange(trigger.copy(value = if (it == ANY) ANY else it.coerceIn(0, MIDI_DATA_MAX)))
                }
            }
            TriggerKinds.MSC -> {
                SettingsTextField(
                    value = trigger.address,
                    onValueChange = { onChange(trigger.copy(address = it.uppercase())) },
                    label = stringResource(Res.string.control_field_command),
                    modifier = Modifier.width(FIELD_WIDTH),
                )
                SettingsTextField(
                    value = trigger.cue,
                    onValueChange = { onChange(trigger.copy(cue = it)) },
                    label = stringResource(Res.string.control_field_cue),
                    modifier = Modifier.width(FIELD_WIDTH),
                )
            }
            else -> SettingsTextField(
                value = trigger.address,
                onValueChange = { onChange(trigger.copy(address = it)) },
                label = stringResource(Res.string.control_field_address),
                modifier = Modifier.width(ADDRESS_WIDTH),
            )
        }
    }
}

/** Waits for the next thing that arrives and takes it as the trigger. */
@Composable
private fun LearnButton(learning: TriggerLearning, onLearned: (Trigger) -> Unit) {
    if (learning.isLearning) {
        Text(stringResource(Res.string.control_learning), style = MaterialTheme.typography.bodySmall)
    }
    GhostButton(
        shape = AppShape(6.dp),
        onClick = { if (learning.isLearning) learning.onCancel() else learning.onLearn(onLearned) },
        modifier = Modifier.testTag(CONTROL_LEARN_TAG),
    ) { Text(stringResource(Res.string.control_learn)) }
}

internal const val CONTROL_TRIGGER_ADD_TAG = "control_trigger_add"
internal const val CONTROL_TRIGGER_SAVE_TAG = "control_trigger_save"
internal const val CONTROL_TRIGGER_NAME_TAG = "control_trigger_name"
internal const val CONTROL_LEARN_TAG = "control_learn"

internal fun controlTriggerTag(id: String) = "control_trigger_$id"

private const val MIDI_CHANNELS = 16
private const val MIDI_DATA_MAX = 127
private val ADDRESS_WIDTH = 300.dp
