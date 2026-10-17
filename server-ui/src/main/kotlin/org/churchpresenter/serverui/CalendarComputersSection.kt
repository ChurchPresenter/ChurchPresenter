package org.churchpresenter.serverui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The main computer's list of the computers that joined its calendar, with adding one and joining
 * another's. [canAdd] once this computer is registered: before that there is no calendar to add to.
 */
@Composable
internal fun ComputersSection(actions: ComputerActions, clock: LocalTimeText, canAdd: Boolean) {
    val computers = actions.computers
    SubCard {
        SubCardHeader(
            icon = Icons.Outlined.Computer,
            tint = MaterialTheme.semantic.contentSongs,
            title = stringResource(Res.string.calendar_computers_title),
            count = if (canAdd) computers.size else null,
            hint = stringResource(Res.string.calendar_computers_hint),
        ) {
            CompactKey(stringResource(Res.string.calendar_computers_join), actions.onJoin)
            if (canAdd) CompactPrimary(stringResource(Res.string.calendar_computers_add), actions.onAdd)
        }
        if (canAdd) {
            if (computers.isEmpty()) {
                EmptyNote(stringResource(Res.string.calendar_computers_none))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    computers.forEach { computer -> ComputerRow(computer, clock, actions.onRemove, actions.onMakeMain) }
                }
            }
        }
    }
}

@Composable
private fun ComputerRow(
    computer: JoinedComputer,
    clock: LocalTimeText,
    onRemove: (String) -> Unit,
    onMakeMain: (String) -> Unit,
) {
    val viewer = computer.role == CalendarSyncSettings.ROLE_VIEWER
    val joined = computer.lastSeen.isNotBlank()
    val roleTint = if (viewer) MaterialTheme.semantic.success else MaterialTheme.semantic.info
    ListRow(
        dot = if (joined) MaterialTheme.semantic.success else MaterialTheme.colorScheme.outline,
        title = computer.name.ifBlank { computer.id },
        subtitle = if (joined) {
            stringResource(Res.string.calendar_computers_last_seen, clock.format(computer.lastSeen))
        } else {
            stringResource(Res.string.calendar_computers_not_joined)
        },
        chip = {
            Pill(
                stringResource(
                    if (viewer) {
                        Res.string.calendar_computers_role_viewer
                    } else {
                        Res.string.calendar_computers_role_follower
                    },
                ),
                roleTint,
                round = false,
            )
        },
    ) {
        if (!viewer && joined) {
            GhostButton(
                onClick = { onMakeMain(computer.id) },
                shape = AppShape(8.dp),
                contentPadding = COMPACT_PADDING,
                modifier = Modifier.compactButton(),
            ) {
                Text(
                    stringResource(Res.string.calendar_computers_make_main),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        RemoveButton(stringResource(Res.string.calendar_computers_remove)) { onRemove(computer.id) }
    }
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
    val seenAt = runCatching { Instant.parse(mainLastSeen) }.getOrNull()
    val seenText = seenAt?.let {
        val formatted = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale).format(it.atZone(zone))
        stringResource(Res.string.calendar_joined_main_seen, mainName, formatted)
    }
    SubCard {
        SubCardHeader(
            icon = Icons.Outlined.Computer,
            tint = MaterialTheme.semantic.contentSongs,
            title = mainName,
            count = null,
            hint = stringResource(
                if (settings.role == CalendarSyncSettings.ROLE_VIEWER) {
                    Res.string.calendar_joined_viewer
                } else {
                    Res.string.calendar_joined_follower
                },
                mainName,
            ),
        ) {
            CompactKey(stringResource(Res.string.calendar_sync_sync_now), onSyncNow)
        }
        if (seenText != null) {
            DimText(seenText, small = true)
        }
    }
    val silent = seenAt != null && Duration.between(seenAt, now) >= TAKE_OVER_AFTER
    if (settings.role == CalendarSyncSettings.ROLE_FOLLOWER && silent) {
        Banner(
            tint = MaterialTheme.semantic.warning,
            icon = Icons.Outlined.WarningAmber,
            title = stringResource(Res.string.calendar_joined_take_over_hint, mainName),
            trailing = {
                CompactPrimary(stringResource(Res.string.calendar_joined_take_over), onTakeOver)
            },
        )
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        RemoveButton(stringResource(Res.string.calendar_joined_leave), onClick = onLeave)
    }
}

private val TAKE_OVER_AFTER: Duration = Duration.ofDays(7)
