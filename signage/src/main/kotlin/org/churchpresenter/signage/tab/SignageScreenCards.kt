package org.churchpresenter.signage.tab

import org.churchpresenter.signage.core.CecState
import androidx.compose.foundation.layout.FlowRow
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.PowerAction
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.hub.PendingScreen
import org.churchpresenter.sharedui.composables.DropdownSettingsField
import org.churchpresenter.sharedui.composables.TooltipIconButton
import org.churchpresenter.sharedui.composables.bibleListCard
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.signage_approve
import org.churchpresenter.strings.generated.resources.signage_decline
import org.churchpresenter.strings.generated.resources.signage_identify
import org.churchpresenter.strings.generated.resources.signage_new_screen
import org.churchpresenter.strings.generated.resources.signage_new_screen_hint
import org.churchpresenter.strings.generated.resources.signage_offline
import org.churchpresenter.strings.generated.resources.signage_online
import org.churchpresenter.strings.generated.resources.signage_platform_linux_arm64
import org.churchpresenter.strings.generated.resources.signage_platform_linux_x64
import org.churchpresenter.strings.generated.resources.signage_platform_windows_x64
import org.churchpresenter.strings.generated.resources.signage_plays
import org.churchpresenter.strings.generated.resources.signage_factory_reset
import org.churchpresenter.strings.generated.resources.signage_factory_reset_text
import org.churchpresenter.strings.generated.resources.signage_factory_reset_title
import org.churchpresenter.strings.generated.resources.signage_nothing
import org.churchpresenter.strings.generated.resources.signage_schedule
import org.churchpresenter.strings.generated.resources.signage_schedule_summary
import org.churchpresenter.strings.generated.resources.signage_screen_settings
import org.churchpresenter.strings.generated.resources.signage_showing
import org.churchpresenter.strings.generated.resources.signage_showing_idle
import org.churchpresenter.strings.generated.resources.signage_restart
import org.churchpresenter.strings.generated.resources.signage_restart_text
import org.churchpresenter.strings.generated.resources.signage_restart_title
import org.churchpresenter.strings.generated.resources.signage_shut_down
import org.churchpresenter.strings.generated.resources.signage_shut_down_text
import org.churchpresenter.strings.generated.resources.signage_shut_down_title
import org.churchpresenter.strings.generated.resources.signage_wipe
import org.churchpresenter.strings.generated.resources.signage_wipe_confirm
import org.churchpresenter.strings.generated.resources.signage_wipe_text
import org.churchpresenter.strings.generated.resources.signage_web_test
import org.churchpresenter.strings.generated.resources.signage_wipe_title
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A player waiting on the TV with its code, to approve or decline. */
@Composable
internal fun PendingScreenCard(player: PendingScreen, onApprove: () -> Unit, onDecline: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().bibleListCard()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = PENDING_TINT))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.signage_new_screen), style = MaterialTheme.typography.titleSmall)
            Text(
                listOf(
                    stringResource(platformName(player.platform)),
                    "${player.screenWidth}×${player.screenHeight}",
                    player.address,
                ).joinToString(SEPARATOR),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(Res.string.signage_new_screen_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            player.code,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton(onClick = onDecline) { Text(stringResource(Res.string.signage_decline)) }
            RaisedButton(onClick = onApprove) { Text(stringResource(Res.string.signage_approve)) }
        }
    }
}

/** A paired screen: whether it is online, what it shows, and what it plays. */
@Composable
internal fun ScreenCard(row: ScreenRowState, actions: ScreenActions) {
    val screen = row.screen
    var renaming by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf<CardQuestion?>(null) }
    var enlarged by remember { mutableStateOf(false) }
    val showsPreview = row.link?.features?.contains(Features.PREVIEW) == true
    Row(
        modifier = Modifier.fillMaxWidth().bibleListCard()
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showsPreview) {
            Box(Modifier.padding(top = 6.dp)) {
                PreviewThumbnail(currentPreview(row.link)) {
                    enlarged = true
                    actions.onPreviewOpen(screen.id)
                }
            }
        }
        ScreenDetails(row, actions, onRename = { renaming = true }, onAsk = { asking = it })
    }
    if (enlarged) {
        LargePreviewDialog(screen.name, currentPreview(row.link)) {
            enlarged = false
            actions.onPreviewOpen(null)
        }
    }
    if (renaming) {
        val plan = row.folder.settings.plan(screen.id)
        ScreenSettingsDialog(
            initial = ScreenSettingsInput(
                name = screen.name,
                orientation = plan.orientation,
                hasPin = plan.kioskPinHash != null,
                gesture = plan.kioskGesture,
                gesturePlaylistId = plan.kioskGesturePlaylistId,
                touchPlaylists = touchPlaylists(row.folder),
                canPower = row.link?.features?.contains(Features.POWER) == true,
                canTv = row.link?.let { Features.TV in it.features && it.status?.cec == CecState.WORKING } == true,
            ),
            onSave = { edit -> saveSettings(screen.id, screen.name, plan, edit, actions) },
            onAsk = { asking = it },
            onDismiss = { renaming = false },
            onTv = { on -> actions.onTv(screen.id, on) },
            deviceInfo = { DeviceDetails(row) },
        )
    }
    when (asking) {
        CardQuestion.WIPE -> ConfirmDialog(
            title = stringResource(Res.string.signage_wipe_title, screen.name),
            text = stringResource(Res.string.signage_wipe_text),
            confirm = stringResource(Res.string.signage_wipe_confirm),
            onConfirm = { actions.onWipe(screen.id) },
            onDismiss = { asking = null },
        )
        CardQuestion.FACTORY_RESET -> ConfirmDialog(
            title = stringResource(Res.string.signage_factory_reset_title, screen.name),
            text = stringResource(Res.string.signage_factory_reset_text),
            confirm = stringResource(Res.string.signage_factory_reset),
            onConfirm = { actions.onRemove(screen.id) },
            onDismiss = { asking = null },
        )
        CardQuestion.RESTART -> ConfirmDialog(
            title = stringResource(Res.string.signage_restart_title, screen.name),
            text = stringResource(Res.string.signage_restart_text),
            confirm = stringResource(Res.string.signage_restart),
            onConfirm = { actions.onPower(screen.id, PowerAction.RESTART) },
            onDismiss = { asking = null },
        )
        CardQuestion.SHUT_DOWN -> ConfirmDialog(
            title = stringResource(Res.string.signage_shut_down_title, screen.name),
            text = stringResource(Res.string.signage_shut_down_text),
            confirm = stringResource(Res.string.signage_shut_down),
            onConfirm = { actions.onPower(screen.id, PowerAction.SHUT_DOWN) },
            onDismiss = { asking = null },
        )
        CardQuestion.SCHEDULE -> ScheduleDialog(
            screenName = screen.name,
            playlists = row.folder.playlists,
            blocks = row.folder.settings.plan(screen.id).schedule,
            onSave = { actions.onSchedule(screen.id, it) },
            onDismiss = { asking = null },
        )
        null -> Unit
    }
}

/** What a card asks before doing. */
internal enum class CardQuestion { WIPE, FACTORY_RESET, SCHEDULE, RESTART, SHUT_DOWN }

/** The card's text and controls, beside the picture. */
@Composable
private fun RowScope.ScreenDetails(
    row: ScreenRowState,
    actions: ScreenActions,
    onRename: () -> Unit,
    onAsk: (CardQuestion) -> Unit,
) {
    val screen = row.screen
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(online = row.link != null)
            Spacer(Modifier.width(10.dp))
            Text(screen.name, style = MaterialTheme.typography.titleMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TooltipIconButton(
                painter = rememberVectorPainter(Icons.Filled.Visibility),
                text = stringResource(Res.string.signage_identify),
                onClick = { actions.onIdentify(screen.id) },
                enabled = row.link != null,
            )
            TooltipIconButton(
                painter = rememberVectorPainter(Icons.Filled.Schedule),
                text = stringResource(Res.string.signage_schedule),
                onClick = { onAsk(CardQuestion.SCHEDULE) },
            )
            TooltipIconButton(
                painter = rememberVectorPainter(Icons.Filled.Speed),
                text = stringResource(Res.string.signage_web_test),
                onClick = { actions.onTestWebPages(screen.id) },
                enabled = row.link?.let { Features.WEB_TEST in it.features && it.webTestStartedAtMs == null } == true,
            )
            TooltipIconButton(
                painter = rememberVectorPainter(Icons.Filled.Settings),
                text = stringResource(Res.string.signage_screen_settings),
                onClick = onRename,
            )
            TooltipIconButton(
                painter = rememberVectorPainter(Icons.Filled.DeleteSweep),
                text = stringResource(Res.string.signage_wipe),
                onClick = { onAsk(CardQuestion.WIPE) },
                enabled = row.link?.features?.contains(Features.WIPE) == true,
            )
        }
        Text(
            connectionLine(row),
            style = MaterialTheme.typography.bodyMedium,
            color = with(MaterialTheme.colorScheme) { if (row.link != null) onSurface else onSurfaceVariant },
        )
        // The rest of its details are in its settings; only a screen under strain says so here.
        ScreenHealthLine(row.link?.status, onlyWhenStrained = true)
        row.link?.status?.errors?.forEach { error ->
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        UpdateLine(row)
        WebTestLine(row)
        // Side by side where the card is wide enough; the button moves under the picker where it is not.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PlaylistPicker(row, onPlay = { actions.onPlay(screen.id, it) })
            UpdateButton(row, onUpdate = { actions.onUpdate(screen.id) })
        }
        row.folder.settings.plan(screen.id).schedule.takeIf { it.isNotEmpty() }?.let { blocks ->
            Text(
                stringResource(Res.string.signage_schedule_summary, blocks.sumOf { it.days.size }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** What the screen plays: a playlist, or nothing (its idle card). */
@Composable
private fun PlaylistPicker(row: ScreenRowState, onPlay: (String) -> Unit) {
    val playlists = row.folder.playlists
    val wanted = row.folder.settings.plan(row.screen.id).playlistId
    if (playlists.isEmpty() && wanted != ScreenPlan.NOTHING) return
    val nothing = stringResource(Res.string.signage_nothing)
    val current = when (wanted) {
        ScreenPlan.NOTHING -> nothing
        else -> (playlists.firstOrNull { it.id == wanted } ?: playlists.first()).name
    }
    DropdownSettingsField(
        value = current,
        options = playlists.map { it.name } + nothing,
        onValueChange = { name -> ScreenPlan.forChoice(name, nothing, playlists)?.let(onPlay) },
        label = stringResource(Res.string.signage_plays),
    )
}

@Composable
private fun StatusDot(online: Boolean) {
    val colour = if (online) MaterialTheme.semantic.success else MaterialTheme.colorScheme.outline
    Spacer(Modifier.size(10.dp).background(colour, CircleShape))
}

@Composable
private fun connectionLine(row: ScreenRowState): String {
    val link = row.link ?: return stringResource(Res.string.signage_offline)
    val showing = link.status?.showingPlaylistId?.let { id -> row.folder.playlists.firstOrNull { it.id == id }?.name }
    val what = if (showing != null) {
        stringResource(Res.string.signage_showing, showing)
    } else {
        stringResource(Res.string.signage_showing_idle)
    }
    return listOf(stringResource(Res.string.signage_online), what).joinToString(SEPARATOR)
}

internal fun platformName(platform: Platform): StringResource = when (platform) {
    Platform.LINUX_ARM64 -> Res.string.signage_platform_linux_arm64
    Platform.LINUX_X64 -> Res.string.signage_platform_linux_x64
    Platform.WINDOWS_X64 -> Res.string.signage_platform_windows_x64
}

internal const val SEPARATOR = " · "

private const val PENDING_TINT = 0.35f
