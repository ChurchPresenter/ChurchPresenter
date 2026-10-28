package org.churchpresenter.signage.tab

import org.churchpresenter.strings.generated.resources.signage_tv_on
import org.churchpresenter.strings.generated.resources.signage_tv_off
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.churchpresenter.sharedui.composables.DropdownSettingsField
import org.churchpresenter.sharedui.composables.LabeledSwitch
import org.churchpresenter.signage.core.KioskPin
import org.churchpresenter.signage.core.Orientation
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.save
import org.churchpresenter.strings.generated.resources.signage_device
import org.churchpresenter.strings.generated.resources.signage_factory_reset
import org.churchpresenter.strings.generated.resources.signage_kiosk_pin_hint
import org.churchpresenter.strings.generated.resources.signage_kiosk_pin_is_set
import org.churchpresenter.strings.generated.resources.signage_kiosk_pin_new
import org.churchpresenter.strings.generated.resources.signage_kiosk_pin_none
import org.churchpresenter.strings.generated.resources.signage_kiosk_pin_remove
import org.churchpresenter.strings.generated.resources.signage_kiosk_pin_removing
import org.churchpresenter.strings.generated.resources.signage_orientation
import org.churchpresenter.strings.generated.resources.signage_orientation_hint
import org.churchpresenter.strings.generated.resources.signage_orientation_landscape
import org.churchpresenter.strings.generated.resources.signage_orientation_landscape_flipped
import org.churchpresenter.strings.generated.resources.signage_orientation_portrait
import org.churchpresenter.strings.generated.resources.signage_orientation_portrait_flipped
import org.churchpresenter.strings.generated.resources.signage_power_hint
import org.churchpresenter.strings.generated.resources.signage_restart
import org.churchpresenter.strings.generated.resources.signage_screen_name
import org.churchpresenter.strings.generated.resources.signage_screen_settings
import org.churchpresenter.strings.generated.resources.signage_shut_down
import org.churchpresenter.strings.generated.resources.signage_touch
import org.churchpresenter.strings.generated.resources.signage_touch_gesture
import org.churchpresenter.strings.generated.resources.signage_touch_gesture_hint
import org.churchpresenter.strings.generated.resources.signage_touch_none
import org.churchpresenter.strings.generated.resources.signage_touch_target
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.components.SettingsTextField
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A screen's own settings: its name and how it is mounted, which turns everything it shows. Under
 * Touchscreen: whether holding its corner switches to a touch page, to which, and the PIN it asks for
 * (never shown, only replaced or removed). Under Device: what [deviceInfo] says of it, and Restart,
 * Shut down and Factory reset, apart from Save, each asked about before it is done.
 */
@Composable
internal fun ScreenSettingsDialog(
    initial: ScreenSettingsInput,
    onSave: (ScreenSettingsEdit) -> Unit,
    /** Restart, shut down or factory reset, each asked about on the card before it is done. */
    onAsk: (CardQuestion) -> Unit,
    onDismiss: () -> Unit,
    /** Turns the TV on (true) or to standby now; offered only where [ScreenSettingsInput.canTv]. */
    onTv: (Boolean) -> Unit = {},
    /** The device's address, model, player and health, as the card knows them. */
    deviceInfo: @Composable () -> Unit = {},
) {
    var newName by remember { mutableStateOf(initial.name) }
    var mounted by remember { mutableStateOf(initial.orientation) }
    val touch = remember { TouchChoices(initial) }
    val labels = Orientation.entries.associateWith { stringResource(labelOf(it)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.signage_screen_settings)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.width(DIALOG_WIDTH).verticalScroll(rememberScrollState()),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SettingsTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = stringResource(Res.string.signage_screen_name),
                        fillWidth = true,
                        modifier = Modifier.weight(1f).testTag(SCREEN_NAME_TAG),
                    )
                    DropdownSettingsField(
                        value = labels.getValue(mounted),
                        options = Orientation.entries.map(labels::getValue),
                        onValueChange = { picked ->
                            labels.entries.firstOrNull { it.value == picked }?.let { mounted = it.key }
                        },
                        label = stringResource(Res.string.signage_orientation),
                    )
                }
                Hint(stringResource(Res.string.signage_orientation_hint))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TouchSection(initial, touch)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DeviceSection(initial, onTv, deviceInfo) { question ->
                    onDismiss()
                    onAsk(question)
                }
            }
        },
        confirmButton = {
            RaisedButton(
                onClick = {
                    onSave(
                        ScreenSettingsEdit(
                            newName.trim(), mounted, touch.pinChange, touch.gesture, touch.playlistId,
                        ),
                    )
                    onDismiss()
                },
                enabled = newName.isNotBlank() && touch.pinIsValid,
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = { RingedGhostButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

/** The Touchscreen section's choices while the dialog is open. */
private class TouchChoices(initial: ScreenSettingsInput) {
    var gesture by mutableStateOf(initial.gesture)
    var playlistId by mutableStateOf(initial.gesturePlaylistId)
    var newPin by mutableStateOf("")
    var removing by mutableStateOf(false)

    val pinChange: PinChange
        get() = when {
            newPin.isNotEmpty() -> PinChange.Set(newPin)
            removing -> PinChange.Remove
            else -> PinChange.Keep
        }

    val pinIsValid: Boolean get() = newPin.isEmpty() || KioskPin.isValid(newPin)
}

/**
 * Whether holding the corner switches to a touch page; while it does, which playlist (when there is a
 * choice) and the PIN it asks for.
 */
@Composable
private fun TouchSection(initial: ScreenSettingsInput, touch: TouchChoices) {
    SectionTitle(stringResource(Res.string.signage_touch))
    LabeledSwitch(
        checked = touch.gesture,
        onCheckedChange = { touch.gesture = it },
        label = stringResource(Res.string.signage_touch_gesture),
        supporting = stringResource(Res.string.signage_touch_gesture_hint),
        controlAtEnd = true,
        spacing = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    )
    if (!touch.gesture) return
    val pages = initial.touchPlaylists
    if (pages.isEmpty()) Hint(stringResource(Res.string.signage_touch_none))
    // Where it switches to and the PIN it asks for, side by side: a PIN is
    // eight digits at most.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (pages.isNotEmpty()) {
            val current = pages.firstOrNull { it.id == touch.playlistId } ?: pages.first()
            DropdownSettingsField(
                value = current.name,
                options = pages.map { it.name },
                onValueChange = { name -> touch.playlistId = pages.firstOrNull { it.name == name }?.id },
                label = stringResource(Res.string.signage_touch_target),
            )
        }
        PinField(initial.hasPin, touch)
    }
    val status = when {
        touch.removing -> Res.string.signage_kiosk_pin_removing
        initial.hasPin -> Res.string.signage_kiosk_pin_is_set
        else -> Res.string.signage_kiosk_pin_none
    }
    Hint(stringResource(status) + " " + stringResource(Res.string.signage_kiosk_pin_hint))
}

/** A new PIN, short as the PIN is, and removing the one set. */
@Composable
private fun PinField(hasPin: Boolean, touch: TouchChoices) {
    SettingsTextField(
        value = touch.newPin,
        onValueChange = { typed ->
            touch.newPin = typed.filter(Char::isDigit).take(KioskPin.MAX_DIGITS)
            if (touch.newPin.isNotEmpty()) touch.removing = false
        },
        label = stringResource(Res.string.signage_kiosk_pin_new),
        isError = !touch.pinIsValid,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        fillWidth = true,
        modifier = Modifier.width(PIN_WIDTH).testTag(SCREEN_PIN_TAG),
    )
    if (hasPin && !touch.removing) {
        KeyButton(onClick = {
            touch.removing = true
            touch.newPin = ""
        }) { OneLine(stringResource(Res.string.signage_kiosk_pin_remove)) }
    }
}

/** The device itself: what the card knows of it, and Restart, Shut down and Factory reset. */
@Composable
private fun DeviceSection(
    initial: ScreenSettingsInput,
    onTv: (Boolean) -> Unit,
    info: @Composable () -> Unit,
    onAsk: (CardQuestion) -> Unit,
) {
    val canPower = initial.canPower
    SectionTitle(stringResource(Res.string.signage_device))
    info()
    // The TV's own power, over HDMI, where it answers: at once, without changing what plays.
    if (initial.canTv) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton(onClick = { onTv(true) }) { OneLine(stringResource(Res.string.signage_tv_on)) }
            KeyButton(onClick = { onTv(false) }) { OneLine(stringResource(Res.string.signage_tv_off)) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KeyButton(onClick = { onAsk(CardQuestion.RESTART) }, enabled = canPower) {
            OneLine(stringResource(Res.string.signage_restart))
        }
        KeyButton(onClick = { onAsk(CardQuestion.SHUT_DOWN) }, enabled = canPower) {
            OneLine(stringResource(Res.string.signage_shut_down))
        }
        // The error red makes it the danger key.
        KeyButton(
            onClick = { onAsk(CardQuestion.FACTORY_RESET) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { OneLine(stringResource(Res.string.signage_factory_reset)) }
    }
    Hint(stringResource(Res.string.signage_power_hint))
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

/** A button's label, kept to one line. */
@Composable
private fun OneLine(text: String) {
    Text(text, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun labelOf(orientation: Orientation): StringResource = when (orientation) {
    Orientation.LANDSCAPE -> Res.string.signage_orientation_landscape
    Orientation.PORTRAIT -> Res.string.signage_orientation_portrait
    Orientation.LANDSCAPE_FLIPPED -> Res.string.signage_orientation_landscape_flipped
    Orientation.PORTRAIT_FLIPPED -> Res.string.signage_orientation_portrait_flipped
}

/** The dialog's text fields, whose captions are drawn apart from the box a test types into. */
internal const val SCREEN_NAME_TAG = "signage-screen-name"
internal const val SCREEN_PIN_TAG = "signage-screen-pin"
private val DIALOG_WIDTH = 440.dp
private val PIN_WIDTH = 120.dp
