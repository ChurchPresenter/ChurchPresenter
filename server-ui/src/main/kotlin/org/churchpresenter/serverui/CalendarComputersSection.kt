package org.churchpresenter.serverui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.churchpresenter.calendar.sync.JoinedComputer
import org.churchpresenter.settings.CalendarSyncSettings
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.calendar_computers_add
import org.churchpresenter.strings.generated.resources.calendar_computers_hint
import org.churchpresenter.strings.generated.resources.calendar_computers_join
import org.churchpresenter.strings.generated.resources.calendar_computers_last_seen
import org.churchpresenter.strings.generated.resources.calendar_computers_make_main
import org.churchpresenter.strings.generated.resources.calendar_computers_none
import org.churchpresenter.strings.generated.resources.calendar_computers_not_joined
import org.churchpresenter.strings.generated.resources.calendar_computers_remove
import org.churchpresenter.strings.generated.resources.calendar_computers_role_follower
import org.churchpresenter.strings.generated.resources.calendar_computers_role_viewer
import org.churchpresenter.strings.generated.resources.calendar_computers_title
import org.churchpresenter.strings.generated.resources.calendar_joined_follower
import org.churchpresenter.strings.generated.resources.calendar_joined_leave
import org.churchpresenter.strings.generated.resources.calendar_joined_main_default
import org.churchpresenter.strings.generated.resources.calendar_joined_main_seen
import org.churchpresenter.strings.generated.resources.calendar_joined_take_over
import org.churchpresenter.strings.generated.resources.calendar_joined_take_over_hint
import org.churchpresenter.strings.generated.resources.calendar_joined_viewer
import org.churchpresenter.strings.generated.resources.calendar_sync_sync_now
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.KeyButton
import org.jetbrains.compose.resources.stringResource
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The main computer's list of the computers that joined its calendar, and the button to add one. */
@Composable
internal fun ComputersSection(
    computers: List<JoinedComputer>,
    clock: LocalTimeText,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onMakeMain: (String) -> Unit,
) {
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(Res.string.calendar_computers_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = stringResource(Res.string.calendar_computers_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (computers.isEmpty()) {
        Text(
            text = stringResource(Res.string.calendar_computers_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
    computers.forEach { computer -> ComputerRow(computer, clock, onRemove, onMakeMain) }
    SmallButton(text = stringResource(Res.string.calendar_computers_add), onClick = onAdd)
}

@Composable
private fun ComputerRow(
    computer: JoinedComputer,
    clock: LocalTimeText,
    onRemove: (String) -> Unit,
    onMakeMain: (String) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            val role = stringResource(
                if (computer.role == CalendarSyncSettings.ROLE_VIEWER) {
                    Res.string.calendar_computers_role_viewer
                } else {
                    Res.string.calendar_computers_role_follower
                },
            )
            Text("${computer.name.ifBlank { computer.id }} · $role", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (computer.lastSeen.isBlank()) {
                    stringResource(Res.string.calendar_computers_not_joined)
                } else {
                    stringResource(Res.string.calendar_computers_last_seen, clock.format(computer.lastSeen))
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (computer.role == CalendarSyncSettings.ROLE_FOLLOWER && computer.lastSeen.isNotBlank()) {
            GhostButton(onClick = { onMakeMain(computer.id) }) {
                Text(
                    stringResource(Res.string.calendar_computers_make_main),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        GhostButton(
            onClick = { onRemove(computer.id) },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text(stringResource(Res.string.calendar_computers_remove), style = MaterialTheme.typography.labelSmall) }
    }
}

/** The button a computer joins another's calendar from. */
@Composable
internal fun JoinCalendarButton(onClick: () -> Unit) {
    SmallButton(text = stringResource(Res.string.calendar_computers_join), onClick = onClick)
}

/**
 * What a computer that joined another's calendar shows in place of the phone list: whose calendar
 * it follows, when that computer last synced, and -- once it has been silent a week -- taking over.
 */
@Composable
internal fun JoinedSection(
    settings: CalendarSyncSettings,
    mainLastSeen: String,
    now: Instant,
    locale: Locale,
    zone: ZoneId,
    onSyncNow: () -> Unit,
    onLeave: () -> Unit,
    onTakeOver: () -> Unit,
) {
    val mainName = settings.mainName.ifBlank { stringResource(Res.string.calendar_joined_main_default) }
    Text(
        text = stringResource(
            if (settings.role == CalendarSyncSettings.ROLE_VIEWER) {
                Res.string.calendar_joined_viewer
            } else {
                Res.string.calendar_joined_follower
            },
            mainName,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val seenAt = runCatching { Instant.parse(mainLastSeen) }.getOrNull()
    if (seenAt != null) {
        val formatted = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale).format(seenAt.atZone(zone))
        Text(
            text = stringResource(Res.string.calendar_joined_main_seen, mainName, formatted),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        KeyButton(shape = AppShape(6.dp), contentPadding = SMALL_PADDING, onClick = onSyncNow) {
            Text(stringResource(Res.string.calendar_sync_sync_now), style = MaterialTheme.typography.labelSmall)
        }
        GhostButton(
            onClick = onLeave,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text(stringResource(Res.string.calendar_joined_leave), style = MaterialTheme.typography.labelSmall) }
    }
    val silent = seenAt != null && Duration.between(seenAt, now) >= TAKE_OVER_AFTER
    if (settings.role == CalendarSyncSettings.ROLE_FOLLOWER && silent) {
        Text(
            text = stringResource(Res.string.calendar_joined_take_over_hint, mainName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        SmallButton(text = stringResource(Res.string.calendar_joined_take_over), onClick = onTakeOver)
    }
}

private val TAKE_OVER_AFTER: Duration = Duration.ofDays(7)
