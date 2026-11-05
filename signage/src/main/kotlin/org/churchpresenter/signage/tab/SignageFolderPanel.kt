package org.churchpresenter.signage.tab

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.churchpresenter.sharedui.composables.TooltipIconButton
import org.churchpresenter.sharedui.composables.bibleListCard
import org.churchpresenter.signage.smugmug.SmugMugProgress
import org.churchpresenter.signage.smugmug.SmugMugState
import org.churchpresenter.signage.smugmug.SmugMugSync
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.signage_copy_failed
import org.churchpresenter.strings.generated.resources.signage_copying
import org.churchpresenter.strings.generated.resources.signage_file_missing
import org.churchpresenter.strings.generated.resources.signage_file_title_edit
import org.churchpresenter.strings.generated.resources.signage_folder_counts
import org.churchpresenter.strings.generated.resources.signage_folder_missing
import org.churchpresenter.strings.generated.resources.signage_folder_title_edit
import org.churchpresenter.strings.generated.resources.signage_link_title_edit
import org.churchpresenter.strings.generated.resources.signage_no_playlists
import org.churchpresenter.strings.generated.resources.signage_playlists
import org.churchpresenter.strings.generated.resources.signage_reading_playlists
import org.churchpresenter.strings.generated.resources.signage_remove
import org.churchpresenter.strings.generated.resources.signage_remove_copy_for_good
import org.churchpresenter.strings.generated.resources.signage_remove_copy_trash
import org.churchpresenter.strings.generated.resources.signage_remove_playlist
import org.churchpresenter.strings.generated.resources.signage_remove_playlist_text
import org.churchpresenter.strings.generated.resources.signage_remove_playlist_title
import org.churchpresenter.strings.generated.resources.signage_rotation_minutes
import org.churchpresenter.strings.generated.resources.signage_rotation_seconds_short
import org.churchpresenter.strings.generated.resources.signage_rotation_summary
import org.churchpresenter.strings.generated.resources.signage_rotation_title_edit
import org.churchpresenter.strings.generated.resources.signage_smugmug_downloading
import org.churchpresenter.strings.generated.resources.signage_smugmug_failed
import org.churchpresenter.strings.generated.resources.signage_smugmug_listing
import org.churchpresenter.strings.generated.resources.signage_smugmug_no_key
import org.churchpresenter.strings.generated.resources.signage_smugmug_pick_now
import org.churchpresenter.strings.generated.resources.signage_smugmug_picked
import org.churchpresenter.strings.generated.resources.signage_smugmug_picking
import org.churchpresenter.strings.generated.resources.signage_smugmug_title_edit
import org.jetbrains.compose.resources.stringResource

/** The playlists screens can play, and the Add menu that makes them. */
@Composable
internal fun SignageFolderPanel(state: SignageFolderState, actions: FolderActions, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CountedHeading(
            stringResource(Res.string.signage_playlists),
            state.playlists.size.takeIf { state.ready && it > 0 },
        )
        PlaylistList(state, actions, Modifier.fillMaxWidth().weight(1f, fill = false))
        state.copying.forEach { name ->
            Text(
                stringResource(Res.string.signage_copying, name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp),
            )
        }
        state.copyFailed?.let { name ->
            Text(
                stringResource(Res.string.signage_copy_failed, name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp),
            )
        }
    }
}

@Composable
private fun PlaylistList(state: SignageFolderState, actions: FolderActions, modifier: Modifier) {
    val desktop = actions.desktop
    var deleting by remember { mutableStateOf<PlaylistSummary?>(null) }
    var renaming by remember { mutableStateOf<PlaylistSummary?>(null) }
    Column(modifier = modifier.bibleListCard()) {
        val message = when {
            !state.ready -> stringResource(Res.string.signage_reading_playlists)
            state.playlists.isEmpty() -> stringResource(Res.string.signage_no_playlists)
            else -> null
        }
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        LazyColumn(modifier = Modifier.padding(5.dp)) {
            items(state.playlists, key = { it.id }) { playlist ->
                PlaylistRow(
                    playlist,
                    slideshow = state.slideshows[playlist.id],
                    picking = playlist.id in state.picking,
                    progress = state.pickProgress[playlist.id],
                    onRename = { renaming = playlist },
                    onDelete = { deleting = playlist },
                    onPickNew = { actions.onPickNewPhotos(playlist.id) },
                    clock = actions.clock,
                )
            }
        }
    }
    renaming?.takeIf { it.kind == SourceKind.ROTATION }?.let { playlist ->
        RotationDialog(
            entries = state.playlists.filter { it.kind != SourceKind.ROTATION },
            initial = state.settings.added.filterIsInstance<RotationPlaylist>().firstOrNull { it.id == playlist.id },
            onSave = { name, steps -> actions.onEditRotation(playlist.id, name, steps) },
            onDismiss = { renaming = null },
        )
    }
    when (val editing = renaming?.let { playlist -> state.settings.added.firstOrNull { it.id == playlist.id } }) {
        is LinkPlaylist -> LinkDialog(
            initial = editing,
            onAdd = { actions.onEditLink(editing.id, it) },
            onDismiss = { renaming = null },
            idlePlaylists = idlePlaylists(state, editing = editing.id),
        )
        is FilePlaylist -> FileDialog(
            initial = editing,
            desktop = desktop,
            onSave = { name, file -> actions.onEditFile(editing.id, name, file) },
            onDismiss = { renaming = null },
        )
        is PickedFolderPlaylist -> FolderDialog(
            initial = editing,
            location = actions.onLocate(editing.id),
            desktop = desktop,
            onSave = { name, seconds -> actions.onEditFolder(editing.id, name, seconds) },
            onDismiss = { renaming = null },
        )
        is SmugMugPlaylist -> SmugMugDialog(
            initial = editing,
            apiKey = actions.smugMug.apiKey,
            onApiKey = actions.smugMug.onApiKey,
            lookUp = actions.smugMug.lookUp,
            onSave = actions.onSaveSmugMug,
            onDismiss = { renaming = null },
            openUrl = actions.smugMug.openUrl,
        )
        is RotationPlaylist, null -> Unit
    }
    deleting?.let { playlist ->
        val copy = state.settings.added.firstOrNull { it.id == playlist.id }?.copyPath()?.let(SignageMedia::isCopy)
        ConfirmDialog(
            title = stringResource(Res.string.signage_remove_playlist_title, playlist.name),
            text = stringResource(
                when {
                    copy != true -> Res.string.signage_remove_playlist_text
                    desktop.canTrash() -> Res.string.signage_remove_copy_trash
                    else -> Res.string.signage_remove_copy_for_good
                },
            ),
            confirm = stringResource(Res.string.signage_remove),
            onConfirm = { actions.onRemove(playlist.id) },
            onDismiss = { deleting = null },
        )
    }
}

/**
 * One playlist: its kind's icon and name, its folder, file or address under it, and a folder's counts
 * beside it; a SmugMug slideshow says how its last pick went ([slideshow], [picking]) and can pick anew.
 */
@Composable
private fun PlaylistRow(
    playlist: PlaylistSummary,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    slideshow: SmugMugState? = null,
    picking: Boolean = false,
    onPickNew: () -> Unit = {},
    clock: Clock = Clock.systemDefaultZone(),
    progress: SmugMugProgress? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindTile(
            tileKindOf(playlist.kind),
            tint = if (playlist.missing) errorTint() else tintOf(tileKindOf(playlist.kind)),
        )
        Spacer(Modifier.width(12.dp))
        if (playlist.kind == SourceKind.SMUGMUG) {
            SlideshowText(playlist, slideshow, picking, clock, Modifier.weight(1f), progress)
            TooltipIconButton(
                painter = rememberVectorPainter(Icons.Filled.Refresh),
                text = stringResource(Res.string.signage_smugmug_pick_now),
                onClick = onPickNew,
                enabled = !picking,
            )
        } else {
            PlaylistText(playlist, Modifier.weight(1f))
        }
        TooltipIconButton(
            painter = rememberVectorPainter(Icons.Filled.Edit),
            text = stringResource(
                when (playlist.kind) {
                    SourceKind.WEB, SourceKind.STREAM -> Res.string.signage_link_title_edit
                    SourceKind.ROTATION -> Res.string.signage_rotation_title_edit
                    SourceKind.FOLDER -> Res.string.signage_folder_title_edit
                    SourceKind.SMUGMUG -> Res.string.signage_smugmug_title_edit
                    SourceKind.PICTURE, SourceKind.VIDEO -> Res.string.signage_file_title_edit
                },
            ),
            onClick = onRename,
        )
        TooltipIconButton(
            painter = rememberVectorPainter(Icons.Filled.Delete),
            text = stringResource(Res.string.signage_remove_playlist),
            onClick = onDelete,
        )
    }
}

/**
 * A playlist's name, a folder's path faint beside it, and under it what it holds: a folder's counts, a
 * rotation's steps, or its file or address (in red when the file or folder is gone).
 */
@Composable
private fun PlaylistText(playlist: PlaylistSummary, modifier: Modifier) {
    val folder = playlist.kind == SourceKind.FOLDER
    val meta = when {
        playlist.missing && folder -> stringResource(Res.string.signage_folder_missing, playlist.detail.orEmpty())
        playlist.missing -> stringResource(Res.string.signage_file_missing, playlist.detail.orEmpty())
        folder -> stringResource(Res.string.signage_folder_counts, playlist.pictures, playlist.videos, playlist.seconds)
        playlist.kind == SourceKind.ROTATION -> stringResource(
            Res.string.signage_rotation_summary,
            playlist.steps,
            if (playlist.seconds >= SECONDS_PER_MINUTE) {
                stringResource(Res.string.signage_rotation_minutes, playlist.seconds / SECONDS_PER_MINUTE)
            } else {
                stringResource(Res.string.signage_rotation_seconds_short, playlist.seconds)
            },
        )
        else -> playlist.detail.orEmpty()
    }
    Column(modifier = modifier) {
        PlaylistName(playlist.name, beside = playlist.detail.takeIf { folder && !playlist.missing })
        Text(
            meta,
            style = MaterialTheme.typography.bodySmall,
            color = with(MaterialTheme.colorScheme) { if (playlist.missing) error else onSurfaceVariant },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A playlist's name, semi-bold, with [beside] faint after it on the same line. */
@Composable
private fun PlaylistName(name: String, beside: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (beside != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                beside,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/**
 * A SmugMug slideshow's name, and under it how its photos stand: being picked, how many and when, or
 * (in red) why the last pick failed, with the set it keeps playing.
 */
@Composable
private fun SlideshowText(
    playlist: PlaylistSummary,
    state: SmugMugState?,
    picking: Boolean,
    clock: Clock,
    modifier: Modifier,
    progress: SmugMugProgress? = null,
) {
    val failure = state?.error
    val photos = playlist.pictures
    val line = when {
        picking && progress != null -> stringResource(
            when (progress.stage) {
                SmugMugProgress.Stage.LISTING -> Res.string.signage_smugmug_listing
                SmugMugProgress.Stage.DOWNLOADING -> Res.string.signage_smugmug_downloading
            },
            progress.done,
            progress.total,
        )
        picking -> stringResource(Res.string.signage_smugmug_picking)
        failure == SmugMugSync.NO_KEY -> stringResource(Res.string.signage_smugmug_no_key)
        failure != null -> stringResource(Res.string.signage_smugmug_failed, failure, photos)
        state?.pickedAtMs != null -> stringResource(
            Res.string.signage_smugmug_picked, photos, playlist.seconds, timeOf(state.pickedAtMs, clock),
        )
        else -> playlist.detail.orEmpty()
    }
    Column(modifier = modifier) {
        PlaylistName(playlist.name)
        Text(
            line,
            style = MaterialTheme.typography.bodySmall,
            color = with(MaterialTheme.colorScheme) { if (failure != null && !picking) error else onSurfaceVariant },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (picking) PickBar(progress)
    }
}

/**
 * A thin bar under a slideshow that is picking: how far through the galleries, then the photos, it
 * is; moving on its own until the first count comes.
 */
@Composable
private fun PickBar(progress: SmugMugProgress?) {
    val modifier = Modifier.padding(top = 5.dp, end = 8.dp).fillMaxWidth().height(3.dp)
    val share = progress?.takeIf { it.total > 0 }?.let { it.done.toFloat() / it.total }
    val colour = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = TRACK_ALPHA)
    if (share == null) {
        LinearProgressIndicator(modifier = modifier, color = colour, trackColor = track)
    } else {
        LinearProgressIndicator(
            progress = { share },
            modifier = modifier,
            color = colour,
            trackColor = track,
            drawStopIndicator = {},
        )
    }
}

/** [ms] as a time of day, or a date and time when it was not today by [clock]. */
private fun timeOf(ms: Long, clock: Clock): String {
    val at = Instant.ofEpochMilli(ms).atZone(clock.zone)
    val style = if (at.toLocalDate() == LocalDate.now(clock)) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    } else {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    }
    return style.format(at)
}

private const val SECONDS_PER_MINUTE = 60
private const val TRACK_ALPHA = 0.1f

/**
 * What a touchscreen page's screen may play while nobody uses it: any entry but a rotation, another
 * touchscreen page, or the link being edited ([editing]).
 */
internal fun idlePlaylists(state: SignageFolderState, editing: String?): List<PlaylistSummary> {
    val touchPages = state.settings.added.filterIsInstance<LinkPlaylist>()
        .filter { it.interactive != null }.map { it.id }.toSet()
    return state.playlists.filter { it.kind != SourceKind.ROTATION && it.id != editing && it.id !in touchPages }
}
