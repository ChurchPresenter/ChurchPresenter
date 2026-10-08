package org.churchpresenter.schedule

import androidx.compose.runtime.Composable
import org.churchpresenter.core.models.schedule.TimerModes
import org.churchpresenter.showcontrol.Action
import org.churchpresenter.showcontrol.MediaCommand
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.action_field_button
import org.churchpresenter.strings.generated.resources.action_field_connection
import org.churchpresenter.strings.generated.resources.action_field_duration
import org.churchpresenter.strings.generated.resources.action_field_key
import org.churchpresenter.strings.generated.resources.action_field_keyer
import org.churchpresenter.strings.generated.resources.action_field_me
import org.churchpresenter.strings.generated.resources.action_field_message
import org.churchpresenter.strings.generated.resources.action_field_mode
import org.churchpresenter.strings.generated.resources.action_field_prop
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
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MessageFields(action: Action.Message, messages: List<MessageChoice>, onChange: (Action) -> Unit) {
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
        TextField(token, action.tokens[token].orEmpty()) {
            onChange(action.copy(tokens = action.tokens + (token to it)))
        }
    }
    TextField(stringResource(Res.string.action_field_duration), action.durationSeconds?.toString().orEmpty(), MEDIUM) {
        onChange(action.copy(durationSeconds = it.filter(Char::isDigit).toIntOrNull()))
    }
}

@Composable
internal fun PropFields(action: Action.Prop, props: List<String>, onChange: (Action) -> Unit) {
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
internal fun TimerFields(action: Action.Timer, onChange: (Action) -> Unit) {
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
internal fun MediaPicker(action: Action.Media, onChange: (Action) -> Unit) {
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
internal fun AtemKeyFields(action: Action.AtemKey, onChange: (Action) -> Unit) {
    Picker(
        stringResource(Res.string.action_field_key),
        action.downstream.toString(),
        listOf(
            "true" to stringResource(Res.string.action_key_downstream),
            "false" to stringResource(Res.string.action_key_upstream),
        ),
    ) { onChange(action.copy(downstream = it.toBoolean())) }
    if (!action.downstream) {
        CountField(stringResource(Res.string.action_field_me), action.mixEffect) {
            onChange(action.copy(mixEffect = it))
        }
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
internal fun CompanionFields(
    action: Action.CompanionPress,
    connections: List<CompanionChoice>,
    onChange: (Action) -> Unit,
) {
    Picker(
        stringResource(Res.string.action_field_connection),
        action.connection,
        connections.map { it.id to it.name },
    ) { onChange(action.copy(connection = it)) }
    CountField(stringResource(Res.string.action_field_button), action.button) { onChange(action.copy(button = it)) }
}
