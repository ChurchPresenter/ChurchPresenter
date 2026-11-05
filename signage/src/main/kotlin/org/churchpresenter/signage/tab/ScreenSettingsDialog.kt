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
import org.churchpresenter.strings.generated.resources.signage_touch_page
import org.churchpresenter.strings.generated.resources.signage_touch_gesture
import org.churchpresenter.strings.generated.resources.signage_touch_gesture_hint
import org.churchpresenter.strings.generated.resources.signage_touch_none
import org.churchpresenter.strings.generated.resources.signage_touch_target
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.components.SettingsTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import org.churchpresenter.strings.generated.resources.signage_mounting
import org.churchpresenter.strings.generated.resources.signage_power_title
import org.churchpresenter.theme.keyboardFocusRing
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
    /** The line under the title: the screen's name and what it runs on. */
    subtitle: String? = null,
) {
    var newName by remember { mutableStateOf(initial.name) }
    var mounted by remember { mutableStateOf(initial.orientation) }
    val touch = remember { TouchChoices(initial) }
    val labels = Orientation.entries.associateWith { stringResource(labelOf(it)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            SignageDialogTitle(
                stringResource(Res.string.signage_screen_settings),
                subtitle,
                icon = Icons.Filled.Settings,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.width(DIALOG_WIDTH).verticalScroll(rememberScrollState()),
            ) {
                SettingsTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = stringResource(Res.string.signage_screen_name),
                    fillWidth = true,
                    modifier = Modifier.fillMaxWidth().testTag(SCREEN_NAME_TAG),
                )
                Column {
                    FieldCaption(
                        stringResource(Res.string.signage_mounting),
                        info = stringResource(Res.string.signage_orientation_hint),
                    )
                    Row(
                        modifier = Modifier.selectableGroup().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Orientation.entries.forEach { orientation ->
                            MountTile(
                                orientation = orientation,
                                label = labels.getValue(orientation),
                                selected = mounted == orientation,
                                onClick = { mounted = orientation },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
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
    InfoSwitch(
        title = stringResource(Res.string.signage_touch_page),
        info = stringResource(Res.string.signage_touch_gesture) + ". " +
            stringResource(Res.string.signage_touch_gesture_hint),
        checked = touch.gesture,
        onChange = { touch.gesture = it },
    )
    if (!touch.gesture) return
    val pages = initial.touchPlaylists
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = PANEL_WASH), RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (pages.isEmpty()) Hint(stringResource(Res.string.signage_touch_none))
        // Where it switches to and the PIN it asks for, side by side: a PIN is
        // eight digits at most.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (pages.isNotEmpty()) {
                val current = pages.firstOrNull { it.id == touch.playlistId } ?: pages.first()
                PickerField(
                    value = current.name,
                    name = stringResource(Res.string.signage_touch_target),
                    options = pages.map { PickerOption(it.name) { KindTile(tileKindOf(it.kind), size = 22.dp) } },
                    onPick = { touch.playlistId = pages[it].id },
                    leading = { KindTile(tileKindOf(current.kind), size = 22.dp) },
                    height = 42.dp,
                    modifier = Modifier.weight(1f),
                )
            }
            PinField(initial.hasPin, touch)
        }
        val status = when {
            touch.removing -> Res.string.signage_kiosk_pin_removing
            initial.hasPin -> Res.string.signage_kiosk_pin_is_set
            else -> Res.string.signage_kiosk_pin_none
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Hint(stringResource(status))
            Spacer(Modifier.width(4.dp))
            InfoHint(stringResource(Res.string.signage_kiosk_pin_hint))
        }
    }
}

/**
 * One way the TV can hang, as a tile: a little screen in that shape with an arrow to its top, and its
 * name under it.
 */
@Composable
private fun MountTile(
    orientation: Orientation,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val ink = if (selected) scheme.primary else scheme.onSurfaceVariant
    val portrait = orientation == Orientation.PORTRAIT || orientation == Orientation.PORTRAIT_FLIPPED
    val flipped = orientation == Orientation.LANDSCAPE_FLIPPED || orientation == Orientation.PORTRAIT_FLIPPED
    Column(
        modifier = modifier
            .keyboardFocusRing(shape)
            .clip(shape)
            .background(
                if (selected) scheme.primary.copy(alpha = CHOICE_WASH) else scheme.onSurface.copy(alpha = PANEL_WASH),
            )
            .border(1.5.dp, if (selected) scheme.primary else scheme.outlineVariant, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.size(if (portrait) 22.dp else 38.dp, if (portrait) 38.dp else 22.dp)
                    .border(2.dp, ink, RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(12.dp).rotate(if (flipped) HALF_TURN else 0f),
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = ink,
            textAlign = TextAlign.Center,
        )
    }
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
    // The TV's own power, over HDMI, where it answers: at once, without changing what plays.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(Res.string.signage_device), Modifier.weight(1f))
        if (initial.canTv) {
            KeyButton(onClick = { onTv(true) }) { OneLine(stringResource(Res.string.signage_tv_on)) }
            KeyButton(onClick = { onTv(false) }) { OneLine(stringResource(Res.string.signage_tv_off)) }
        }
    }
    info()
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = DANGER_EDGE), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.signage_power_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(6.dp))
            InfoHint(stringResource(Res.string.signage_power_hint))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton(onClick = { onAsk(CardQuestion.RESTART) }, enabled = canPower) {
                OneLine(stringResource(Res.string.signage_restart))
            }
            KeyButton(onClick = { onAsk(CardQuestion.SHUT_DOWN) }, enabled = canPower) {
                OneLine(stringResource(Res.string.signage_shut_down))
            }
            Spacer(Modifier.weight(1f))
            // The error red makes it the danger key.
            KeyButton(
                onClick = { onAsk(CardQuestion.FACTORY_RESET) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { OneLine(stringResource(Res.string.signage_factory_reset)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = modifier)
}

/** A button's label, kept to one line. */
@Composable
private fun OneLine(text: String) {
    Text(text, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
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
private val DIALOG_WIDTH = 540.dp
private const val PANEL_WASH = 0.03f
private const val CHOICE_WASH = 0.10f
private const val DANGER_EDGE = 0.4f
private const val HALF_TURN = 180f
private val PIN_WIDTH = 120.dp
