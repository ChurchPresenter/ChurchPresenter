package org.churchpresenter.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import org.churchpresenter.sharedui.composables.DropdownSettingsField
import org.churchpresenter.showcontrol.Action
import org.churchpresenter.showcontrol.MediaCommand
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.action_field_button
import org.churchpresenter.strings.generated.resources.action_field_connection
import org.churchpresenter.strings.generated.resources.action_field_duration
import org.churchpresenter.strings.generated.resources.action_field_group
import org.churchpresenter.strings.generated.resources.action_field_key
import org.churchpresenter.strings.generated.resources.action_field_keyer
import org.churchpresenter.strings.generated.resources.action_field_layer
import org.churchpresenter.strings.generated.resources.action_field_macro
import org.churchpresenter.strings.generated.resources.action_field_macro_slot
import org.churchpresenter.strings.generated.resources.action_field_me
import org.churchpresenter.strings.generated.resources.action_field_message
import org.churchpresenter.strings.generated.resources.action_field_mode
import org.churchpresenter.strings.generated.resources.action_field_preset
import org.churchpresenter.strings.generated.resources.action_field_prop
import org.churchpresenter.strings.generated.resources.action_field_row
import org.churchpresenter.strings.generated.resources.action_field_scene
import org.churchpresenter.strings.generated.resources.action_field_seconds
import org.churchpresenter.strings.generated.resources.action_field_switch
import org.churchpresenter.strings.generated.resources.action_field_text
import org.churchpresenter.strings.generated.resources.action_field_until
import org.churchpresenter.strings.generated.resources.action_key_downstream
import org.churchpresenter.strings.generated.resources.action_key_upstream
import org.churchpresenter.strings.generated.resources.action_media_pause
import org.churchpresenter.strings.generated.resources.action_media_play
import org.churchpresenter.strings.generated.resources.action_media_stop
import org.churchpresenter.strings.generated.resources.action_message_typed
import org.churchpresenter.strings.generated.resources.action_switch_off
import org.churchpresenter.strings.generated.resources.action_switch_on
import org.churchpresenter.strings.generated.resources.action_switch_toggle
import org.churchpresenter.strings.generated.resources.action_timer_clock
import org.churchpresenter.strings.generated.resources.action_timer_count_up
import org.churchpresenter.strings.generated.resources.action_timer_countdown
import org.churchpresenter.strings.generated.resources.action_timer_until
import org.churchpresenter.strings.generated.resources.clear_layer_announcements
import org.churchpresenter.strings.generated.resources.clear_layer_captions
import org.churchpresenter.strings.generated.resources.clear_layer_graphics
import org.churchpresenter.strings.generated.resources.clear_layer_media
import org.churchpresenter.strings.generated.resources.clear_layer_messages
import org.churchpresenter.strings.generated.resources.clear_layer_props
import org.churchpresenter.strings.generated.resources.clear_layer_slide
import org.churchpresenter.theme.components.SettingsTextField
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The fields of [action] that say what it does -- none for one that needs nothing, like Take. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RowActionFields(
    action: Action,
    choices: ActionChoices,
    rows: List<ScheduleItem>,
    onChange: (Action) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        when (action) {
            is Action.GoLive -> RowPicker(action.rowId, rows) { onChange(action.copy(rowId = it)) }
            is Action.ToPreview -> RowPicker(action.rowId, rows) { onChange(action.copy(rowId = it)) }
            is Action.Clear -> Picker(
                stringResource(Res.string.action_field_layer),
                action.layer,
                ROW_ACTION_LAYERS.map { it to layerLabel(it) },
            ) { onChange(action.copy(layer = it)) }
            is Action.ClearGroup -> NamePicker(Res.string.action_field_group, action.group, choices.clearGroups) {
                onChange(action.copy(group = it))
            }
            is Action.Message -> MessageFields(action, choices.messages, onChange)
            is Action.Prop -> PropFields(action, choices.props, onChange)
            is Action.LowerThird -> NamePicker(Res.string.action_field_preset, action.preset, choices.lowerThirds) {
                onChange(action.copy(preset = it))
            }
            is Action.Timer -> TimerFields(action, onChange)
            is Action.Media -> MediaPicker(action, onChange)
            is Action.ObsScene -> NamePicker(Res.string.action_field_scene, action.scene, choices.obsScenes) {
                onChange(action.copy(scene = it))
            }
            is Action.AtemKey -> AtemKeyFields(action, onChange)
            is Action.AtemMacro -> CountField(stringResource(Res.string.action_field_macro_slot), action.index) {
                onChange(action.copy(index = it))
            }
            is Action.CompanionPress -> CompanionFields(action, choices.companion, onChange)
            is Action.Wait -> TextField(stringResource(Res.string.action_field_seconds), action.seconds.secondsText()) {
                it.toDoubleOrNull()?.let { seconds -> onChange(action.copy(seconds = seconds)) }
            }
            is Action.RunMacro -> NamePicker(Res.string.action_field_macro, action.name, choices.macros) {
                onChange(action.copy(name = it))
            }
            else -> Unit
        }
    }
}

@Composable
private fun RowPicker(rowId: String, rows: List<ScheduleItem>, onPick: (String) -> Unit) {
    Picker(
        stringResource(Res.string.action_field_row),
        rowId,
        rows.filter { it.isContent() }.map { it.id to it.displayText },
    ) { onPick(it) }
}

@Composable
private fun MessageFields(action: Action.Message, messages: List<MessageChoice>, onChange: (Action) -> Unit) {
    val typed = stringResource(Res.string.action_message_typed)
    Picker(
        stringResource(Res.string.action_field_message),
        action.template,
        listOf("" to typed) + messages.map { it.name to it.name },
    ) { onChange(action.copy(template = it, tokens = emptyMap())) }
    val template = messages.firstOrNull { it.name == action.template }
    if (action.template.isBlank()) {
        TextField(stringResource(Res.string.action_field_text), action.text, WIDE) { onChange(action.copy(text = it)) }
    }
    template?.tokens?.forEach { token ->
        TextField(token, action.tokens[token].orEmpty()) { onChange(action.copy(tokens = action.tokens + (token to it))) }
    }
    TextField(stringResource(Res.string.action_field_duration), action.durationSeconds?.toString().orEmpty(), MEDIUM) {
        onChange(action.copy(durationSeconds = it.filter(Char::isDigit).toIntOrNull()))
    }
}

@Composable
private fun PropFields(action: Action.Prop, props: List<String>, onChange: (Action) -> Unit) {
    NamePicker(Res.string.action_field_prop, action.prop, props) { onChange(action.copy(prop = it)) }
    Picker(
        stringResource(Res.string.action_field_switch),
        action.on.toString(),
        listOf(
            "true" to stringResource(Res.string.action_switch_on),
            "false" to stringResource(Res.string.action_switch_off),
            "null" to stringResource(Res.string.action_switch_toggle),
        ),
    ) { onChange(action.copy(on = it.toBooleanStrictOrNull())) }
}

@Composable
private fun TimerFields(action: Action.Timer, onChange: (Action) -> Unit) {
    Picker(
        stringResource(Res.string.action_field_mode),
        action.mode,
        listOf(
            TimerModes.DURATION to stringResource(Res.string.action_timer_countdown),
            TimerModes.CLOCK to stringResource(Res.string.action_timer_until),
            TimerModes.COUNT_UP to stringResource(Res.string.action_timer_count_up),
            TimerModes.CLOCK_DISPLAY to stringResource(Res.string.action_timer_clock),
        ),
    ) { onChange(action.copy(mode = it)) }
    when (action.mode) {
        TimerModes.DURATION -> TextField(stringResource(Res.string.action_field_seconds), action.seconds.toString()) {
            onChange(action.copy(seconds = it.filter(Char::isDigit).toIntOrNull() ?: 0))
        }
        TimerModes.CLOCK -> TextField(stringResource(Res.string.action_field_until), action.until) {
            onChange(action.copy(until = it.take(TIME_CHARS)))
        }
        else -> Unit
    }
}

@Composable
private fun MediaPicker(action: Action.Media, onChange: (Action) -> Unit) {
    Picker(
        stringResource(Res.string.action_field_mode),
        action.command.name,
        listOf(
            MediaCommand.PLAY.name to stringResource(Res.string.action_media_play),
            MediaCommand.PAUSE.name to stringResource(Res.string.action_media_pause),
            MediaCommand.STOP.name to stringResource(Res.string.action_media_stop),
        ),
    ) { onChange(action.copy(command = MediaCommand.valueOf(it))) }
}

@Composable
private fun AtemKeyFields(action: Action.AtemKey, onChange: (Action) -> Unit) {
    Picker(
        stringResource(Res.string.action_field_key),
        action.downstream.toString(),
        listOf(
            "true" to stringResource(Res.string.action_key_downstream),
            "false" to stringResource(Res.string.action_key_upstream),
        ),
    ) { onChange(action.copy(downstream = it.toBoolean())) }
    if (!action.downstream) {
        CountField(stringResource(Res.string.action_field_me), action.mixEffect) { onChange(action.copy(mixEffect = it)) }
    }
    CountField(stringResource(Res.string.action_field_keyer), action.keyer) { onChange(action.copy(keyer = it)) }
    Picker(
        stringResource(Res.string.action_field_switch),
        action.on.toString(),
        listOf(
            "true" to stringResource(Res.string.action_switch_on),
            "false" to stringResource(Res.string.action_switch_off),
        ),
    ) { onChange(action.copy(on = it.toBoolean())) }
}

@Composable
private fun CompanionFields(action: Action.CompanionPress, connections: List<CompanionChoice>, onChange: (Action) -> Unit) {
    Picker(
        stringResource(Res.string.action_field_connection),
        action.connection,
        connections.map { it.id to it.name },
    ) { onChange(action.copy(connection = it)) }
    CountField(stringResource(Res.string.action_field_button), action.button) { onChange(action.copy(button = it)) }
}

/**
 * A pick from [names], or -- when there is nothing to pick from, or what is set is not among them
 * -- the name typed.
 */
@Composable
private fun NamePicker(
    label: StringResource,
    value: String,
    names: List<String>,
    onPick: (String) -> Unit,
) {
    if (names.isEmpty() || (value.isNotBlank() && value !in names)) {
        TextField(stringResource(label), value, WIDE, onPick)
    } else {
        Picker(stringResource(label), value, names.map { it to it }, onPick)
    }
}

/** A dropdown of [options] -- value to label -- showing [value]'s label. */
@Composable
private fun Picker(label: String, value: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    val shown = options.firstOrNull { it.first == value }?.second ?: value
    DropdownSettingsField(
        value = shown,
        options = options.map { it.second },
        onValueChange = { picked -> options.firstOrNull { it.second == picked }?.let { onPick(it.first) } },
        label = label,
        modifier = Modifier.testTag(rowActionFieldTag(label)),
    )
}

/** A number counted from one on screen and from zero in the action, as switchers and surfaces count. */
@Composable
private fun CountField(label: String, zeroBased: Int, onChange: (Int) -> Unit) {
    TextField(label, (zeroBased + 1).toString()) { typed ->
        typed.filter(Char::isDigit).toIntOrNull()?.takeIf { it >= 1 }?.let { onChange(it - 1) }
    }
}

@Composable
private fun TextField(label: String, value: String, width: Dp = NARROW, onChange: (String) -> Unit) {
    SettingsTextField(
        value = value,
        onValueChange = onChange,
        label = label,
        modifier = Modifier.width(width).testTag(rowActionFieldTag(label)),
    )
}

/** What [layer] -- as the live show names it -- is called where an operator clears it. */
@Composable
internal fun layerLabel(layer: String): String = stringResource(
    when (layer) {
        "MEDIA" -> Res.string.clear_layer_media
        "CAPTIONS" -> Res.string.clear_layer_captions
        "GRAPHICS" -> Res.string.clear_layer_graphics
        "PROPS" -> Res.string.clear_layer_props
        "ANNOUNCEMENTS" -> Res.string.clear_layer_announcements
        "MESSAGES" -> Res.string.clear_layer_messages
        else -> Res.string.clear_layer_slide
    },
)

internal fun rowActionFieldTag(label: String) = "row_action_field_$label"

private val NARROW = 72.dp
private val MEDIUM = 110.dp
private val WIDE = 220.dp
private const val TIME_CHARS = 5
