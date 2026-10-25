package org.churchpresenter.signage.tab

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import org.churchpresenter.signage.core.CecState
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.hub.PendingScreen
import org.churchpresenter.sharedui.composables.DropdownSettingsField
import org.churchpresenter.sharedui.composables.TooltipIconButton
import org.churchpresenter.sharedui.composables.bibleListCard
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.signage_approve
import org.churchpresenter.strings.generated.resources.signage_decline
import org.churchpresenter.strings.generated.resources.signage_free_space
import org.churchpresenter.strings.generated.resources.signage_identify
import org.churchpresenter.strings.generated.resources.signage_new_screen
import org.churchpresenter.strings.generated.resources.signage_new_screen_hint
import org.churchpresenter.strings.generated.resources.signage_offline
import org.churchpresenter.strings.generated.resources.signage_online
import org.churchpresenter.strings.generated.resources.signage_platform_linux_arm64
import org.churchpresenter.strings.generated.resources.signage_platform_linux_x64
import org.churchpresenter.strings.generated.resources.signage_platform_windows_x64
import org.churchpresenter.strings.generated.resources.signage_player_version
import org.churchpresenter.strings.generated.resources.signage_plays
import org.churchpresenter.strings.generated.resources.signage_factory_reset
import org.churchpresenter.strings.generated.resources.signage_factory_reset_text
import org.churchpresenter.strings.generated.resources.signage_factory_reset_title
import org.churchpresenter.strings.generated.resources.signage_nothing
import org.churchpresenter.strings.generated.resources.signage_rename
import org.churchpresenter.strings.generated.resources.signage_showing
import org.churchpresenter.strings.generated.resources.signage_showing_idle
import org.churchpresenter.strings.generated.resources.signage_synced_at
import org.churchpresenter.strings.generated.resources.signage_tv_control_unavailable
import org.churchpresenter.strings.generated.resources.signage_tv_control_working
import org.churchpresenter.strings.generated.resources.signage_wipe
import org.churchpresenter.strings.generated.resources.signage_wipe_confirm
import org.churchpresenter.strings.generated.resources.signage_wipe_text
import org.churchpresenter.strings.generated.resources.signage_wipe_title
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
            OutlinedButton(onClick = onDecline) { Text(stringResource(Res.string.signage_decline)) }
            Button(onClick = onApprove) { Text(stringResource(Res.string.signage_approve)) }
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
        RenameDialog(screen.name, onRename = { actions.onRename(screen.id, it) }, onDismiss = { renaming = false })
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
        null -> Unit
    }
}

/** What a card asks before doing. */
private enum class CardQuestion { WIPE, FACTORY_RESET }

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
            Text(screen.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TooltipIconButton(
                painter = rememberVectorPainter(Icons.Filled.Visibility),
                text = stringResource(Res.string.signage_identify),
                onClick = { actions.onIdentify(screen.id) },
                enabled = row.link != null,
            )
            TooltipIconButton(
                painter = rememberVectorPainter(Icons.Filled.Edit),
                text = stringResource(Res.string.signage_rename),
                onClick = onRename,
            )
            TooltipIconButton(
                painter = rememberVectorPainter(Icons.Filled.DeleteSweep),
                text = stringResource(Res.string.signage_wipe),
                onClick = { onAsk(CardQuestion.WIPE) },
                enabled = row.link?.features?.contains(Features.WIPE) == true,
            )
            TooltipIconButton(
                painter = rememberVectorPainter(Icons.Filled.RestartAlt),
                text = stringResource(Res.string.signage_factory_reset),
                onClick = { onAsk(CardQuestion.FACTORY_RESET) },
            )
        }
        Text(
            connectionLine(row),
            style = MaterialTheme.typography.bodyMedium,
            color = with(MaterialTheme.colorScheme) { if (row.link != null) onSurface else onSurfaceVariant },
        )
        Text(
            detailsLine(row),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        row.link?.status?.errors?.forEach { error ->
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        UpdateLine(row, onUpdate = { actions.onUpdate(screen.id) })
        PlaylistPicker(row, onPlay = { actions.onPlay(screen.id, it) })
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

@Composable
private fun detailsLine(row: ScreenRowState): String {
    val screen = row.screen
    val status = row.link?.status
    return listOfNotNull(
        stringResource(platformName(screen.platform)),
        "${screen.screenWidth}×${screen.screenHeight}",
        stringResource(Res.string.signage_player_version, screen.agentVersion),
        status?.lastSyncMs?.let { stringResource(Res.string.signage_synced_at, timeOf(it)) },
        status?.freeDiskBytes?.let { stringResource(Res.string.signage_free_space, sizeOf(it)) },
        when (status?.cec) {
            CecState.WORKING -> stringResource(Res.string.signage_tv_control_working)
            CecState.UNAVAILABLE -> stringResource(Res.string.signage_tv_control_unavailable)
            CecState.UNKNOWN, null -> null
        },
    ).joinToString(SEPARATOR)
}

internal fun platformName(platform: Platform): StringResource = when (platform) {
    Platform.LINUX_ARM64 -> Res.string.signage_platform_linux_arm64
    Platform.LINUX_X64 -> Res.string.signage_platform_linux_x64
    Platform.WINDOWS_X64 -> Res.string.signage_platform_windows_x64
}

private fun timeOf(epochMs: Long): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

private fun sizeOf(bytes: Long): String = "%.1f GB".format(bytes / BYTES_PER_GB)

private const val SEPARATOR = " · "
private const val PENDING_TINT = 0.35f
private const val BYTES_PER_GB = 1_000_000_000.0
