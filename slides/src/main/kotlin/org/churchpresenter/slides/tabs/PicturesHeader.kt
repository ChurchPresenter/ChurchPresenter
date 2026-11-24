@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package org.churchpresenter.slides.tabs

import org.churchpresenter.slides.viewmodel.PicturesViewModel
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.guideTarget
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.utils.pairLabel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.ui.text.style.TextOverflow
import org.churchpresenter.sharedui.composables.topBarCard
import org.churchpresenter.hosanna.composables.button.IconButton
import org.churchpresenter.hosanna.composables.button.StandardButton
import org.churchpresenter.hosanna.composables.chip.FavoriteFileChip
import org.churchpresenter.hosanna.composables.feedback.Tooltip
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.hosanna.composables.model.IconButtonSize
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Tv
import org.churchpresenter.icons.generated.resources.ic_playlist_add
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.sharedui.utils.label
import org.churchpresenter.strings.generated.resources.recent_pin
import org.churchpresenter.strings.generated.resources.recent_unpin

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
        StandardButton(
            stringResource(Res.string.select_folder),
            { chooseFolder(viewModel) },
            Modifier.guideTarget(GuideTargets.PICTURES_SELECT_FOLDER),
            icon = painterResource(IconRes.drawable.ic_folder),
        )
        Text(
            text = viewModel.selectedFolderDisplayPath ?: stringResource(Res.string.no_folder_selected),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val hasImages = viewModel.images.isNotEmpty()
        if (onSavePreset != null) {
            val savePresetStr = stringResource(Res.string.save_preset)
            Tooltip(savePresetStr) {
                IconButton(
                    rememberVectorPainter(Icons.Filled.BookmarkAdd),
                    savePresetStr,
                    {
                        viewModel.getScheduleData()?.let { (path, name, count) ->
                            onSavePreset(path, name, count)
                        }
                    },
                    hierarchy = ButtonHierarchy.Secondary,
                    enabled = hasImages,
                )
            }
        }
        if (onAddToSchedule != null) {
            val addScheduleStr = stringResource(Res.string.add_to_schedule)
            Tooltip(addScheduleStr, Modifier.guideTarget(GuideTargets.PICTURES_ADD_TO_SCHEDULE)) {
                IconButton(
                    painterResource(IconRes.drawable.ic_playlist_add),
                    addScheduleStr,
                    {
                        viewModel.getScheduleData()?.let { (path, name, count) ->
                            onAddToSchedule(path, name, count)
                        }
                    },
                    hierarchy = ButtonHierarchy.Primary,
                    intent = ButtonIntent.Neutral,
                    enabled = hasImages,
                )
            }
        }
        if (presenterManager != null) {
            val goLiveStr = stringResource(Res.string.go_live)
            val goLiveKey = LocalShortcuts.current.label(ShortcutAction.GO_LIVE).ifEmpty { null }
            Tooltip(goLiveStr, Modifier.guideTarget(GuideTargets.PICTURES_GO_LIVE), shortcut = goLiveKey) {
                IconButton(
                    rememberVectorPainter(Icons.Default.Tv),
                    goLiveStr,
                    { viewModel.goLive(presenterManager, onInstanceLinkSendProject, wentLive) },
                    hierarchy = ButtonHierarchy.Primary,
                    enabled = hasImages,
                )
            }
        }
    }
}

@Composable
private fun PicturesTabScope.PicturesRecentBar(viewModel: PicturesViewModel) {
    // ── Recent folders bar ────────────────────────────────────────
    val scope = rememberCoroutineScope()
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
            val clearStr = stringResource(Res.string.clear_recents)
            Tooltip(clearStr) {
                IconButton(
                    painterResource(IconRes.drawable.ic_close),
                    clearStr,
                    { RecentPictureFolders.clear() },
                    size = IconButtonSize.Small,
                    hierarchy = ButtonHierarchy.Tertiary,
                    intent = ButtonIntent.Neutral,
                )
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
                    FavoriteFileChip(
                        name = File(path).name,
                        onClick = {
                            val folder = File(path)
                            // A recent folder on a share can take seconds to answer: ask off the event thread.
                            scope.launch {
                                if (!withContext(Dispatchers.IO) { folder.isDirectory }) return@launch
                                viewModel.selectFolder(folder)
                                onSettingsChange { s -> s.copy(
                                    pictureSettings = s.pictureSettings.copy(storageDirectory = path),
                                ) }
                                RecentPictureFolders.add(path)
                            }
                        },
                        favorite = isPinned,
                        onFavoriteChange = { RecentPictureFolders.togglePin(path) },
                        starContentDescription = stringResource(
                            if (isPinned) Res.string.recent_unpin else Res.string.recent_pin,
                        ),
                        icon = painterResource(IconRes.drawable.ic_folder),
                        open = isActive,
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

/** Asks for a folder of pictures, opens it and remembers it. */
internal fun PicturesTabScope.chooseFolder(viewModel: PicturesViewModel) {
    viewModel.openFolderChooser(folderDialogTitle) { folderPath ->
        onSettingsChange { s ->
            s.copy(pictureSettings = s.pictureSettings.copy(storageDirectory = folderPath))
        }
        RecentPictureFolders.add(folderPath)
    }
}
