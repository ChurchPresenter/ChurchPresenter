package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible
import churchpresenter.composeapp.generated.resources.browse_directory
import churchpresenter.composeapp.generated.resources.ic_storage_bible
import churchpresenter.composeapp.generated.resources.ic_storage_lower_third
import churchpresenter.composeapp.generated.resources.ic_storage_media
import churchpresenter.composeapp.generated.resources.ic_storage_pictures
import churchpresenter.composeapp.generated.resources.ic_storage_presentation
import churchpresenter.composeapp.generated.resources.ic_storage_songs
import churchpresenter.composeapp.generated.resources.lower_third_size
import churchpresenter.composeapp.generated.resources.media
import churchpresenter.composeapp.generated.resources.no_directory_selected
import churchpresenter.composeapp.generated.resources.pictures
import churchpresenter.composeapp.generated.resources.presentation
import churchpresenter.composeapp.generated.resources.set_all_directories
import churchpresenter.composeapp.generated.resources.songs
import churchpresenter.composeapp.generated.resources.system_storage
import churchpresenter.composeapp.generated.resources.system_storage_linked_count
import churchpresenter.composeapp.generated.resources.system_storage_needs_attention_count
import churchpresenter.composeapp.generated.resources.system_storage_status_file_count
import churchpresenter.composeapp.generated.resources.system_storage_status_linked
import churchpresenter.composeapp.generated.resources.system_storage_status_needs_converting
import churchpresenter.composeapp.generated.resources.system_storage_status_not_found
import churchpresenter.composeapp.generated.resources.system_storage_status_not_set
import churchpresenter.composeapp.generated.resources.system_storage_status_read_only
import churchpresenter.composeapp.generated.resources.system_storage_subtitle
import churchpresenter.composeapp.generated.resources.tooltip_directory_not_found
import churchpresenter.composeapp.generated.resources.tooltip_directory_not_writable
import churchpresenter.composeapp.generated.resources.tooltip_directory_writable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.viewmodel.FileManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** What a chosen folder turned out to be, once the check on it has run. */
internal enum class DirStatus { NOT_SET, CHECKING, WRITABLE, READ_ONLY, NOT_FOUND, INVALID }

private val DirStatus.isLinked: Boolean get() = this == DirStatus.WRITABLE || this == DirStatus.READ_ONLY

private val DirStatus.needsAttention: Boolean get() = this == DirStatus.NOT_FOUND || this == DirStatus.INVALID

private fun isWritableDir(dir: java.io.File): Boolean = try {
    // File.createTempFile generates a unique name per call — concurrent checks
    // from multiple pickers on the same directory cannot collide
    val tmp = java.io.File.createTempFile(".cp_write_test", ".tmp", dir)
    if (!tmp.delete()) tmp.deleteOnExit()
    true
} catch (_: Exception) {
    false
}

private fun isReadableDir(dir: java.io.File): Boolean = try {
    // File.canRead() ignores ACLs on Windows; actually opening a directory
    // stream surfaces access-denied errors reliably
    java.nio.file.Files.newDirectoryStream(dir.toPath()).use { }
    true
} catch (_: Exception) {
    false
}

/**
 * [path]'s state, checked off the UI thread.
 *
 * Hoisted out of the row that draws it because the card's header counts the results: "5 linked,
 * 1 needs attention" is the same six answers, added up.
 */
@Composable
internal fun rememberDirStatus(path: String): DirStatus {
    var status by remember(path) {
        mutableStateOf(if (path.isEmpty()) DirStatus.NOT_SET else DirStatus.CHECKING)
    }
    LaunchedEffect(path) {
        if (path.isEmpty()) {
            status = DirStatus.NOT_SET
            return@LaunchedEffect
        }
        status = withContext(Dispatchers.IO) {
            val dir = java.io.File(path)
            try {
                when {
                    !dir.isDirectory -> DirStatus.NOT_FOUND
                    isWritableDir(dir) -> DirStatus.WRITABLE
                    isReadableDir(dir) -> DirStatus.READ_ONLY
                    else -> DirStatus.INVALID
                }
            } catch (_: Exception) {
                DirStatus.INVALID
            }
        }
    }
    return status
}

/** One row of the storage list: which content it holds, where it is, and how to move it. */
internal class StorageFolder(
    val name: String,
    val icon: DrawableResource,
    val tint: Color,
    val path: String,
    val onPathChange: (String) -> Unit,
)

/** The line under a folder's name — what the folder is, rather than where it is. */
internal class StorageStatusLine(val text: String, val color: Color)

/** The six folders, in the order the pane lists them. */
@Composable
private fun storageFolders(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
): List<StorageFolder> = listOf(
    StorageFolder(
        stringResource(Res.string.bible), Res.drawable.ic_storage_bible, MaterialTheme.semantic.contentBible,
        settings.bibleSettings.storageDirectory,
    ) { dir -> onSettingsChange { s -> s.copy(bibleSettings = s.bibleSettings.copy(storageDirectory = dir)) } },
    StorageFolder(
        stringResource(Res.string.songs), Res.drawable.ic_storage_songs, MaterialTheme.semantic.contentSongs,
        settings.songSettings.storageDirectory,
    ) { dir -> onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(storageDirectory = dir)) } },
    StorageFolder(
        stringResource(Res.string.pictures), Res.drawable.ic_storage_pictures, MaterialTheme.semantic.contentPictures,
        settings.pictureSettings.storageDirectory,
    ) { dir -> onSettingsChange { s -> s.copy(pictureSettings = s.pictureSettings.copy(storageDirectory = dir)) } },
    StorageFolder(
        stringResource(Res.string.lower_third_size), Res.drawable.ic_storage_lower_third,
        MaterialTheme.semantic.contentLowerThird, settings.streamingSettings.lowerThirdFolder,
    ) { dir -> onSettingsChange { s -> s.copy(streamingSettings = s.streamingSettings.copy(lowerThirdFolder = dir)) } },
    StorageFolder(
        stringResource(Res.string.presentation), Res.drawable.ic_storage_presentation,
        MaterialTheme.semantic.contentPresentation, settings.presentationStorageDirectory,
    ) { dir -> onSettingsChange { s -> s.copy(presentationStorageDirectory = dir) } },
    StorageFolder(
        stringResource(Res.string.media), Res.drawable.ic_storage_media, MaterialTheme.semantic.contentMedia,
        settings.mediaStorageDirectory,
    ) { dir -> onSettingsChange { s -> s.copy(mediaStorageDirectory = dir) } },
)

/**
 * Every content folder in one list: what it holds, where it points, and whether that place is
 * usable — with the two folders that have something to say about their contents saying it inline
 * under their own row rather than in a section of their own.
 */
@Composable
internal fun SystemStorageCard(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    fileManager: FileManager,
    onSetAll: (String) -> Unit,
) {
    val folders = storageFolders(settings, onSettingsChange)
    val statuses = folders.map { rememberDirStatus(it.path) }
    val bibleFiles = rememberBibleFiles(fileManager, settings.bibleSettings.storageDirectory)
    val songScan = rememberSongScan(fileManager, settings.songSettings.storageDirectory)

    val linked = statuses.count { it.isLinked }
    val problems = statuses.count { it.needsAttention } +
        (if (songScan.unsupported.isNotEmpty()) 1 else 0)

    SettingsCard(
        title = stringResource(Res.string.system_storage),
        subtitle = stringResource(Res.string.system_storage_subtitle),
        headerTrailing = {
            SettingsCardBadge(
                text = stringResource(Res.string.system_storage_linked_count, linked),
                container = MaterialTheme.semantic.successContainer,
                content = MaterialTheme.semantic.onSuccessContainer,
            )
            if (problems > 0) {
                SettingsCardBadge(
                    text = stringResource(Res.string.system_storage_needs_attention_count, problems),
                    container = MaterialTheme.semantic.warningContainer,
                    content = MaterialTheme.semantic.onWarningContainer,
                )
            }
        },
    ) {
        folders.forEachIndexed { index, folder ->
            val healthy = when (index) {
                BIBLE_ROW -> bibleFiles.value?.takeIf { it.isNotEmpty() }?.let {
                    StorageStatusLine(
                        stringResource(Res.string.system_storage_status_file_count, it.size),
                        MaterialTheme.semantic.success,
                    )
                }
                SONGS_ROW -> songScan.unsupported.takeIf { it.isNotEmpty() }?.let {
                    StorageStatusLine(
                        stringResource(Res.string.system_storage_status_needs_converting, it.size),
                        MaterialTheme.semantic.warning,
                    )
                }
                else -> null
            }
            StorageRow(
                folder = folder,
                status = statuses[index],
                statusLine = statusLineFor(statuses[index], healthy),
                onSetAll = onSetAll,
                fileManager = fileManager,
            )
            when (index) {
                BIBLE_ROW -> BibleStorageDetail(folder.path, bibleFiles, onSettingsChange)
                SONGS_ROW -> SongsStorageDetail(folder.path, songScan)
            }
            if (index < folders.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ROW_LINE_ALPHA))
            }
        }
    }
}

private const val BIBLE_ROW = 0
private const val SONGS_ROW = 1
private const val ROW_LINE_ALPHA = 0.5f
private const val ICON_TILE_ALPHA = 0.16f

/** The name block's width, which the detail rows under a folder line up against. */
private val NAME_COLUMN_WIDTH = 152.dp
internal val STORAGE_DETAIL_INDENT = 180.dp
private val CONTROL_HEIGHT = 34.dp

/** What a row says about itself: [healthy] wins when the folder is fine and has something to add. */
@Composable
private fun statusLineFor(status: DirStatus, healthy: StorageStatusLine?): StorageStatusLine = when (status) {
    DirStatus.NOT_SET -> StorageStatusLine(
        stringResource(Res.string.system_storage_status_not_set),
        MaterialTheme.colorScheme.onSurfaceVariant,
    )
    DirStatus.CHECKING -> StorageStatusLine("…", MaterialTheme.colorScheme.onSurfaceVariant)
    DirStatus.NOT_FOUND, DirStatus.INVALID -> StorageStatusLine(
        stringResource(Res.string.system_storage_status_not_found),
        MaterialTheme.colorScheme.error,
    )
    DirStatus.READ_ONLY -> StorageStatusLine(
        stringResource(Res.string.system_storage_status_read_only),
        MaterialTheme.semantic.warning,
    )
    DirStatus.WRITABLE -> healthy ?: StorageStatusLine(
        stringResource(Res.string.system_storage_status_linked),
        MaterialTheme.semantic.success,
    )
}

@Composable
private fun StorageRow(
    folder: StorageFolder,
    status: DirStatus,
    statusLine: StorageStatusLine,
    onSetAll: (String) -> Unit,
    fileManager: FileManager,
) {
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.width(NAME_COLUMN_WIDTH),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(folder.tint.copy(alpha = ICON_TILE_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(folder.icon),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = folder.tint,
                )
            }
            Column {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = statusLine.text,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusLine.color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        StoragePathField(folder.path, status, modifier = Modifier.weight(1f))
        Button(
            onClick = {
                scope.launch {
                    fileManager.chooseDirectory(currentDirectory = folder.path)?.let(folder.onPathChange)
                }
            },
            modifier = Modifier.height(CONTROL_HEIGHT),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) {
            Text(text = stringResource(Res.string.browse_directory), style = MaterialTheme.typography.labelMedium)
        }
        Button(
            onClick = { if (folder.path.isNotEmpty()) onSetAll(folder.path) },
            enabled = folder.path.isNotEmpty(),
            modifier = Modifier.height(CONTROL_HEIGHT),
            // Green while the folder is usable: the button copies *this* path over the other five,
            // so its colour is the same "this one is good" the row's dot and status line report.
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.semantic.successContainer,
                contentColor = MaterialTheme.semantic.onSuccessContainer,
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 13.dp),
        ) {
            Text(text = stringResource(Res.string.set_all_directories), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** The path itself, with the light that says whether anything is actually there. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoragePathField(path: String, status: DirStatus, modifier: Modifier = Modifier) {
    val borderColor =
        if (status.needsAttention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = modifier
            .height(CONTROL_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StorageStatusDot(status)
        Text(
            text = path.ifEmpty { stringResource(Res.string.no_directory_selected) },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = if (path.isEmpty()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StorageStatusDot(status: DirStatus) {
    if (status == DirStatus.NOT_SET) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(MaterialTheme.colorScheme.outline, CircleShape)
        )
        return
    }
    TooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 4.dp,
            ) {
                Text(
                    when (status) {
                        DirStatus.NOT_SET, DirStatus.CHECKING -> "…"
                        DirStatus.WRITABLE -> stringResource(Res.string.tooltip_directory_writable)
                        DirStatus.READ_ONLY -> stringResource(Res.string.tooltip_directory_not_writable)
                        DirStatus.NOT_FOUND -> stringResource(Res.string.tooltip_directory_not_found)
                        DirStatus.INVALID -> stringResource(Res.string.tooltip_directory_not_writable)
                    },
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomCenter,
            offset = DpOffset(0.dp, 4.dp),
        ),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(
                    when (status) {
                        DirStatus.NOT_SET, DirStatus.CHECKING -> MaterialTheme.colorScheme.onSurfaceVariant
                        DirStatus.WRITABLE -> MaterialTheme.semantic.success
                        DirStatus.READ_ONLY -> MaterialTheme.semantic.warning
                        DirStatus.NOT_FOUND, DirStatus.INVALID -> MaterialTheme.colorScheme.error
                    },
                    shape = CircleShape,
                )
        )
    }
}
