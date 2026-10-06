package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import org.churchpresenter.controlin.ControlOutput
import org.churchpresenter.controlin.OutMessage
import org.churchpresenter.controlin.TriggerKinds
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.control_field_address
import org.churchpresenter.strings.generated.resources.control_field_argument
import org.churchpresenter.strings.generated.resources.control_field_channel
import org.churchpresenter.strings.generated.resources.control_field_controller
import org.churchpresenter.strings.generated.resources.control_field_level
import org.churchpresenter.strings.generated.resources.control_field_note
import org.churchpresenter.strings.generated.resources.control_kind
import org.churchpresenter.strings.generated.resources.control_output_add
import org.churchpresenter.strings.generated.resources.control_output_save
import org.churchpresenter.strings.generated.resources.control_output_when
import org.churchpresenter.strings.generated.resources.control_outputs_empty
import org.churchpresenter.strings.generated.resources.delete_saved_string
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.components.SettingsTextField
import org.jetbrains.compose.resources.stringResource

/** The saved outputs: each sends its note, control change or OSC message when its show event happens. */
@Composable
internal fun ControlOutputsTab(outputs: List<ControlOutput>, onChange: (List<ControlOutput>) -> Unit) {
    var editing by remember { mutableStateOf<ControlOutput?>(null) }
    val events = outputEventOptions()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (outputs.isEmpty() && editing == null) {
            Text(stringResource(Res.string.control_outputs_empty), style = MaterialTheme.typography.bodyMedium)
        }
        outputs.forEach { output ->
            val event = events.firstOrNull { it.first == output.on }?.second ?: output.on
            FilterChip(
                selected = editing?.id == output.id,
                onClick = { editing = output },
                label = { Text("$event  ·  ${outputLabel(output)}") },
                modifier = Modifier.testTag(controlOutputTag(output.id)),
            )
        }
        editing?.let { draft ->
            HorizontalDivider()
            OutputEditor(draft) { editing = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostButton(
                shape = AppShape(6.dp),
                onClick = { editing = ControlOutput(id = nextControlId("output", outputs.map { it.id })) },
                modifier = Modifier.testTag(CONTROL_OUTPUT_ADD_TAG),
            ) { Text(stringResource(Res.string.control_output_add)) }
            editing?.let { draft ->
                val saved = outputs.any { it.id == draft.id }
                if (saved) {
                    GhostButton(shape = AppShape(6.dp), onClick = {
                        onChange(outputs.filterNot { it.id == draft.id })
                        editing = null
                    }) { Text(stringResource(Res.string.delete_saved_string)) }
                }
                RaisedButton(
                    shape = AppShape(6.dp),
                    onClick = {
                        onChange(if (saved) outputs.map { if (it.id == draft.id) draft else it } else outputs + draft)
                        editing = null
                    },
                    modifier = Modifier.testTag(CONTROL_OUTPUT_SAVE_TAG),
                ) { Text(stringResource(Res.string.control_output_save)) }
            }
        }
    }
}

/** The event and the message of the output being added or edited. */
@Composable
private fun OutputEditor(draft: ControlOutput, onChange: (ControlOutput) -> Unit) {
    val message = draft.message
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ControlDropdown(
            label = stringResource(Res.string.control_output_when),
            value = draft.on,
            options = outputEventOptions(),
            onPick = { onChange(draft.copy(on = it)) },
        )
        ControlDropdown(
            label = stringResource(Res.string.control_kind),
            value = message.kind,
            options = outputKindOptions(),
            onPick = { onChange(draft.copy(message = OutMessage(kind = it))) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutputFields(message) { onChange(draft.copy(message = it)) }
        }
    }
}

@Composable
private fun OutputFields(message: OutMessage, onChange: (OutMessage) -> Unit) {
    if (message.kind == TriggerKinds.OSC) {
        SettingsTextField(
            value = message.address,
            onValueChange = { onChange(message.copy(address = it)) },
            label = stringResource(Res.string.control_field_address),
            modifier = Modifier.width(ADDRESS_FIELD_WIDTH),
        )
        SettingsTextField(
            value = message.argument,
            onValueChange = { onChange(message.copy(argument = it)) },
            label = stringResource(Res.string.control_field_argument),
            modifier = Modifier.width(ADDRESS_FIELD_WIDTH),
        )
    } else {
        val numberLabel = if (message.kind == TriggerKinds.MIDI_CC) {
            Res.string.control_field_controller
        } else {
            Res.string.control_field_note
        }
        IntField(stringResource(Res.string.control_field_channel), message.channel) {
            onChange(message.copy(channel = it.coerceIn(1, MIDI_CHANNELS)))
        }
        IntField(stringResource(numberLabel), message.number) {
            onChange(message.copy(number = it.coerceIn(0, MIDI_DATA_MAX)))
        }
        IntField(stringResource(Res.string.control_field_level), message.value) {
            onChange(message.copy(value = it.coerceIn(0, MIDI_DATA_MAX)))
        }
    }
}

internal const val CONTROL_OUTPUT_ADD_TAG = "control_output_add"
internal const val CONTROL_OUTPUT_SAVE_TAG = "control_output_save"

internal fun controlOutputTag(id: String) = "control_output_$id"

private const val MIDI_CHANNELS = 16
private const val MIDI_DATA_MAX = 127
private val ADDRESS_FIELD_WIDTH = 220.dp
