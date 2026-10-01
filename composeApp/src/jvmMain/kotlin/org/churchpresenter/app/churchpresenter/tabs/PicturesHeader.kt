@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.app.churchpresenter.viewmodel.PicturesViewModel
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.ui.draw.alpha
import org.churchpresenter.app.churchpresenter.composables.AddToScheduleButton
import org.churchpresenter.app.churchpresenter.composables.SavePresetButton
import org.churchpresenter.app.churchpresenter.composables.GoLiveButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.add_to_schedule
import org.churchpresenter.strings.generated.resources.save_preset
import org.churchpresenter.strings.generated.resources.go_live
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.strings.generated.resources.clear
import org.churchpresenter.strings.generated.resources.clear_recents
import org.churchpresenter.icons.generated.resources.ic_folder
import org.churchpresenter.strings.generated.resources.recent
import org.churchpresenter.strings.generated.resources.no_folder_selected
import org.churchpresenter.strings.generated.resources.select_folder
import org.churchpresenter.strings.generated.resources.pictures_arrow_key_hint
import org.churchpresenter.strings.generated.resources.pictures_reorder_hint
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.app.churchpresenter.utils.pairLabel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.ui.text.style.TextOverflow
import org.churchpresenter.app.churchpresenter.composables.RecentChip

/** The top card: the folder bar, the recent folders, the playback controls and the key hints. */
@Composable
internal fun PicturesTabScope.PicturesHeader(viewModel: PicturesViewModel) {
    Column(modifier = Modifier.fillMaxWidth().topBarCard()) {
        PicturesFolderBar(viewModel)

        PicturesRecentBar(viewModel)

        PicturesControlsBar(viewModel)

        PicturesHintRow()
    }
}

@Composable
private fun PicturesTabScope.PicturesFolderBar(viewModel: PicturesViewModel) {
    // ── Folder bar ────────────────────────────────────────────────
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RaisedButton(
            onClick = {
                viewModel.openFolderChooser(folderDialogTitle) { folderPath ->
                    onSettingsChange { s ->
                        s.copy(pictureSettings = s.pictureSettings.copy(storageDirectory = folderPath))
                    }
                    RecentPictureFolders.add(folderPath)
                }
            },
            modifier = Modifier.height(32.dp),
            shape = AppShape(7.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
        ) {
            Icon(
                painterResource(IconRes.drawable.ic_folder),
                contentDescription = null,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                stringResource(Res.string.select_folder),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = TextUnit(PICTURES_CAPTION_FONT_SP, TextUnitType.Sp),
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
        Text(
            text = viewModel.selectedFolderDisplayPath ?: stringResource(Res.string.no_folder_selected),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (onSavePreset != null) {
            SavePresetButton(
                onClick = {
                    viewModel.getScheduleData()?.let { (path, name, count) ->
                        onSavePreset(path, name, count)
                    }
                },
                enabled = viewModel.images.isNotEmpty(),
                tooltipText = stringResource(Res.string.save_preset)
            )
        }
        if (onAddToSchedule != null) {
            AddToScheduleButton(
                onClick = {
                    viewModel.getScheduleData()?.let { (path, name, count) -> onAddToSchedule(path, name, count) }
                },
                enabled = viewModel.images.isNotEmpty(),
                tooltipText = stringResource(Res.string.add_to_schedule)
            )
        }
        if (presenterManager != null) {
            GoLiveButton(
                onClick = { viewModel.goLive(presenterManager, onInstanceLinkSendProject, wentLive) },
                enabled = viewModel.images.isNotEmpty(),
                tooltipText = stringResource(Res.string.go_live)
            )
        }
    }
}

@Composable
private fun PicturesTabScope.PicturesRecentBar(viewModel: PicturesViewModel) {
    // ── Recent folders bar ────────────────────────────────────────
    val recentOrdered =
        RecentPictureFolders.pinned + RecentPictureFolders.folders.filter { it !in RecentPictureFolders.pinned }
    if (recentOrdered.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(PICTURES_RECENT_BAR_HEIGHT)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(Res.string.recent),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            TooltipArea(
                tooltip = {
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shape = MaterialTheme.shapes.extraSmall,
                        tonalElevation = 4.dp,
                    ) {
                        Text(
                            stringResource(Res.string.clear_recents),
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                tooltipPlacement = TooltipPlacement.ComponentRect(
                    anchor = Alignment.BottomCenter,
                    offset = DpOffset(0.dp, 4.dp),
                )
            ) {
                KeyIconButton(onClick = { RecentPictureFolders.clear() }, modifier = Modifier.size(20.dp)) {
                    Icon(
                        painter = painterResource(IconRes.drawable.ic_close),
                        contentDescription = stringResource(Res.string.clear),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(vertical = 5.dp, horizontal = 4.dp)
            ) {
                lazyItems(recentOrdered) { path ->
                    val isPinned = path in RecentPictureFolders.pinned
                    val isActive = viewModel.selectedFolderDisplayPath == path
                    RecentChip(
                        name = File(path).name,
                        isActive = isActive,
                        isPinned = isPinned,
                        onOpen = {
                            val folder = File(path)
                            if (folder.exists() && folder.isDirectory) {
                                viewModel.selectFolder(folder)
                                onSettingsChange { s -> s.copy(
                                    pictureSettings = s.pictureSettings.copy(storageDirectory = path),
                                ) }
                                RecentPictureFolders.add(path)
                            }
                        },
                        onTogglePin = { RecentPictureFolders.togglePin(path) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PicturesTabScope.PicturesHintRow() {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Drawn from the live bindings, so a rebind is reflected here rather than the hint
        // going on describing the arrow keys. Hidden entirely when the user has unbound
        // both pairs — an empty "  next/prev image" would be worse than no hint.
        val navLabel = shortcuts.pairLabel(ShortcutAction.PICTURES_PREVIOUS, ShortcutAction.PICTURES_NEXT)
        val rowLabel = shortcuts.pairLabel(ShortcutAction.PICTURES_ROW_UP, ShortcutAction.PICTURES_ROW_DOWN)
        if (navLabel.isNotEmpty() || rowLabel.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.pictures_arrow_key_hint, navLabel, rowLabel),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = TextUnit(PICTURES_SMALL_LABEL_FONT_SP, TextUnitType.Sp)
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "·",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
            )
        }
        Text(
            text = stringResource(Res.string.pictures_reorder_hint),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = TextUnit(PICTURES_SMALL_LABEL_FONT_SP, TextUnitType.Sp)
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
