package org.churchpresenter.dialogs

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.controlin.ANY
import org.churchpresenter.controlin.ControlOutput
import org.churchpresenter.controlin.OutMessage
import org.churchpresenter.controlin.OutputEvents
import org.churchpresenter.controlin.Trigger
import org.churchpresenter.controlin.TriggerKinds
import org.churchpresenter.sharedui.composables.DropdownSettingsField
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.control_any
import org.churchpresenter.strings.generated.resources.control_event_clear
import org.churchpresenter.strings.generated.resources.control_event_go_live
import org.churchpresenter.strings.generated.resources.control_event_take
import org.churchpresenter.strings.generated.resources.control_kind_cc
import org.churchpresenter.strings.generated.resources.control_kind_msc
import org.churchpresenter.strings.generated.resources.control_kind_note
import org.churchpresenter.strings.generated.resources.control_kind_osc
import org.churchpresenter.strings.generated.resources.control_label_cc
import org.churchpresenter.strings.generated.resources.control_label_msc
import org.churchpresenter.strings.generated.resources.control_label_note
import org.churchpresenter.theme.components.SettingsTextField
import org.jetbrains.compose.resources.stringResource

/** A whole-number field: digits only. A blank is [ANY] when [anyWhenBlank], else 0. */
@Composable
internal fun IntField(
    label: String,
    value: Int,
    anyWhenBlank: Boolean = false,
    width: Dp = FIELD_WIDTH,
    onChange: (Int) -> Unit,
) {
    SettingsTextField(
        value = if (anyWhenBlank && value == ANY) "" else value.toString(),
        onValueChange = { typed ->
            val digits = typed.filter(Char::isDigit).take(MAX_DIGITS)
            onChange(digits.toIntOrNull() ?: if (anyWhenBlank) ANY else 0)
        },
        label = label,
        modifier = Modifier.width(width).testTag(controlFieldTag(label)),
    )
}

/** A dropdown of [options] -- value to label -- showing the label of [value]. */
@Composable
internal fun ControlDropdown(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onPick: (String) -> Unit,
) {
    DropdownSettingsField(
        value = options.firstOrNull { it.first == value }?.second ?: value,
        options = options.map { it.second },
        onValueChange = { picked -> options.firstOrNull { it.second == picked }?.let { onPick(it.first) } },
        label = label,
        modifier = Modifier.testTag(controlFieldTag(label)),
    )
}

/** The kinds a trigger or an output can be, in the order a menu offers them. */
@Composable
internal fun triggerKindOptions(): List<Pair<String, String>> = listOf(
    TriggerKinds.MIDI_NOTE to stringResource(Res.string.control_kind_note),
    TriggerKinds.MIDI_CC to stringResource(Res.string.control_kind_cc),
    TriggerKinds.MSC to stringResource(Res.string.control_kind_msc),
    TriggerKinds.OSC to stringResource(Res.string.control_kind_osc),
)

/** The kinds an output can send: MIDI show control only comes in, so it is left out. */
@Composable
internal fun outputKindOptions(): List<Pair<String, String>> =
    triggerKindOptions().filter { it.first != TriggerKinds.MSC }

/** The show events an output can wait for. */
@Composable
internal fun outputEventOptions(): List<Pair<String, String>> = listOf(
    OutputEvents.GO_LIVE to stringResource(Res.string.control_event_go_live),
    OutputEvents.TAKE to stringResource(Res.string.control_event_take),
    OutputEvents.CLEAR to stringResource(Res.string.control_event_clear),
)

/** What a trigger listens for, in a line: "Note 60, channel 1". */
@Composable
internal fun triggerLabel(trigger: Trigger): String {
    val channel = if (trigger.channel == 0) stringResource(Res.string.control_any) else trigger.channel.toString()
    return when (trigger.kind) {
        TriggerKinds.MIDI_NOTE -> stringResource(Res.string.control_label_note, trigger.number, channel)
        TriggerKinds.MIDI_CC -> stringResource(Res.string.control_label_cc, trigger.number, channel)
        TriggerKinds.MSC -> stringResource(Res.string.control_label_msc, trigger.address, trigger.cue).trim()
        else -> trigger.address
    }
}

/** What an output sends, in a line: "Note 36, channel 1", or the OSC address. */
@Composable
internal fun outputLabel(output: ControlOutput): String = outputMessageLabel(output.message)

@Composable
private fun outputMessageLabel(message: OutMessage): String =
    triggerLabel(Trigger(message.kind, message.channel, message.number, address = message.address))

/** An id no [taken] entry has, counting up from one after [prefix]. */
internal fun nextControlId(prefix: String, taken: List<String>): String =
    generateSequence(taken.size + 1) { it + 1 }.map { "$prefix$it" }.first { it !in taken }

internal fun controlFieldTag(label: String) = "control_field_$label"

internal val FIELD_WIDTH = 140.dp
private const val MAX_DIGITS = 6
