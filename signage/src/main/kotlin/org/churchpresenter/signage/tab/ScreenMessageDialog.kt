package org.churchpresenter.signage.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.sharedui.composables.DropdownSettingsField
import org.churchpresenter.sharedui.composables.LabeledCheckbox
import org.churchpresenter.sharedui.composables.LabeledRadioButton
import org.churchpresenter.sharedui.composables.bibleListCard
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.signage_message_active
import org.churchpresenter.strings.generated.resources.signage_message_active_until
import org.churchpresenter.strings.generated.resources.signage_message_band
import org.churchpresenter.strings.generated.resources.signage_message_clear
import org.churchpresenter.strings.generated.resources.signage_message_edit
import org.churchpresenter.strings.generated.resources.signage_message_for_hour
import org.churchpresenter.strings.generated.resources.signage_message_for_hours
import org.churchpresenter.strings.generated.resources.signage_message_for_minutes
import org.churchpresenter.strings.generated.resources.signage_message_full
import org.churchpresenter.strings.generated.resources.signage_message_length
import org.churchpresenter.strings.generated.resources.signage_message_screens
import org.churchpresenter.strings.generated.resources.signage_message_show
import org.churchpresenter.strings.generated.resources.signage_message_text
import org.churchpresenter.strings.generated.resources.signage_message_title
import org.churchpresenter.strings.generated.resources.signage_message_too_old
import org.churchpresenter.strings.generated.resources.signage_message_until_cleared
import org.churchpresenter.strings.generated.resources.signage_message_until_time
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.components.SettingsTextField
import org.jetbrains.compose.resources.stringResource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** How long a message stays: until cleared, a number of minutes, or (editing one) until it was due to end. */
private sealed interface MessageLength {
    data object UntilCleared : MessageLength

    data class Minutes(val minutes: Int) : MessageLength

    data class Keep(val untilMs: Long) : MessageLength
}

private val MINUTE_CHOICES = listOf(15, 30, 60, 120, 240)

/**
 * Writes a message for the screens: its words, a band across the top or the whole screen, how long it
 * stays, and which screens get it (all to start with). [current] is the one showing, to change.
 */
@Composable
internal fun ScreenMessageDialog(
    current: SignageMessage?,
    screens: List<MessageTarget>,
    onShow: (SignageMessage) -> Unit,
    onDismiss: () -> Unit,
    nowMs: () -> Long = System::currentTimeMillis,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var text by remember { mutableStateOf(current?.text.orEmpty()) }
    var fullScreen by remember { mutableStateOf(current?.fullScreen == true) }
    val keep = current?.untilMs?.takeIf { it > nowMs() }?.let { MessageLength.Keep(it) }
    var length by remember { mutableStateOf<MessageLength>(keep ?: MessageLength.UntilCleared) }
    val picked = remember {
        mutableStateListOf<String>().apply {
            addAll(screens.filter { current?.reaches(it.id) ?: true }.map { it.id })
        }
    }
    val lengths = listOfNotNull(keep, MessageLength.UntilCleared) + MINUTE_CHOICES.map { MessageLength.Minutes(it) }
    val labels = lengths.map { lengthLabel(it, zone) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.signage_message_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.width(DIALOG_WIDTH).verticalScroll(rememberScrollState()),
            ) {
                SettingsTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = stringResource(Res.string.signage_message_text),
                    fillWidth = true,
                    modifier = Modifier.fillMaxWidth().testTag(MESSAGE_TEXT_TAG),
                )
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LabeledRadioButton(
                        selected = !fullScreen,
                        onClick = { fullScreen = false },
                        label = stringResource(Res.string.signage_message_band),
                    )
                    LabeledRadioButton(
                        selected = fullScreen,
                        onClick = { fullScreen = true },
                        label = stringResource(Res.string.signage_message_full),
                    )
                }
                DropdownSettingsField(
                    value = labels[lengths.indexOf(length).coerceAtLeast(0)],
                    options = labels,
                    onValueChange = { label -> length = lengths[labels.indexOf(label)] },
                    label = stringResource(Res.string.signage_message_length),
                    width = LENGTH_WIDTH,
                )
                Text(stringResource(Res.string.signage_message_screens), style = MaterialTheme.typography.titleSmall)
                screens.forEach { screen ->
                    LabeledCheckbox(
                        checked = screen.id in picked,
                        onCheckedChange = { on -> if (on) picked += screen.id else picked -= screen.id },
                        label = screen.name,
                        supporting = stringResource(Res.string.signage_message_too_old).takeIf { !screen.canShow },
                    )
                }
            }
        },
        confirmButton = {
            RaisedButton(
                onClick = {
                    val untilMs = when (val chosen = length) {
                        MessageLength.UntilCleared -> null
                        is MessageLength.Minutes -> nowMs() + chosen.minutes * MS_PER_MINUTE
                        is MessageLength.Keep -> chosen.untilMs
                    }
                    // Every screen ticked means every screen, so one paired later gets it too.
                    val ids = picked.toList().takeIf { it.size < screens.size }
                    onShow(SignageMessage(text.trim(), fullScreen, untilMs, ids))
                    onDismiss()
                },
                enabled = text.isNotBlank() && picked.isNotEmpty(),
            ) { Text(stringResource(Res.string.signage_message_show)) }
        },
        dismissButton = { RingedGhostButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

/**
 * The message showing now, over the screen cards: its words, where and until when, with Change and
 * Clear. Nothing once it has run out.
 */
@Composable
internal fun ActiveMessageCard(
    message: SignageMessage,
    screens: List<MessageTarget>,
    onEdit: () -> Unit,
    onClear: () -> Unit,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    if (!message.showsAt(nowMs)) return
    val reached = screens.count { message.reaches(it.id) && it.canShow }
    Row(
        modifier = Modifier.fillMaxWidth().bibleListCard().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Campaign,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(ICON_SIZE),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                message.text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val where = stringResource(Res.string.signage_message_active, reached, screens.size)
            val until = message.untilMs?.let {
                stringResource(Res.string.signage_message_active_until, timeOf(it, zone))
            }
            Text(
                listOfNotNull(where, until).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RingedGhostButton(onClick = onEdit) { Text(stringResource(Res.string.signage_message_edit)) }
        RingedGhostButton(onClick = onClear) { Text(stringResource(Res.string.signage_message_clear)) }
    }
}

@Composable
private fun lengthLabel(length: MessageLength, zone: ZoneId): String = when (length) {
    MessageLength.UntilCleared -> stringResource(Res.string.signage_message_until_cleared)
    is MessageLength.Keep -> stringResource(Res.string.signage_message_until_time, timeOf(length.untilMs, zone))
    is MessageLength.Minutes -> when {
        length.minutes < MINUTES_PER_HOUR -> stringResource(Res.string.signage_message_for_minutes, length.minutes)
        length.minutes == MINUTES_PER_HOUR -> stringResource(Res.string.signage_message_for_hour)
        else -> stringResource(Res.string.signage_message_for_hours, length.minutes / MINUTES_PER_HOUR)
    }
}

private fun timeOf(ms: Long, zone: ZoneId): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone).format(Instant.ofEpochMilli(ms))

internal const val MESSAGE_TEXT_TAG = "signage-message-text"
private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60
private val DIALOG_WIDTH = 440.dp
private val LENGTH_WIDTH: Dp = 220.dp
private val ICON_SIZE = 28.dp
