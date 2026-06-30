package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import org.churchpresenter.app.churchpresenter.composables.initialPassCombinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_refresh
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.animation_crossfade
import churchpresenter.composeapp.generated.resources.animation_fade
import churchpresenter.composeapp.generated.resources.animation_none
import churchpresenter.composeapp.generated.resources.animation_slide_left
import churchpresenter.composeapp.generated.resources.animation_slide_right
import churchpresenter.composeapp.generated.resources.animation_type
import churchpresenter.composeapp.generated.resources.auto_scroll_interval
import churchpresenter.composeapp.generated.resources.go_live
import androidx.compose.material.icons.filled.Tv
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.ic_star
import churchpresenter.composeapp.generated.resources.ic_star_filled
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.clear
import churchpresenter.composeapp.generated.resources.ok
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import churchpresenter.composeapp.generated.resources.clear_recents
import churchpresenter.composeapp.generated.resources.ic_folder
import churchpresenter.composeapp.generated.resources.recent
import churchpresenter.composeapp.generated.resources.ic_pause
import churchpresenter.composeapp.generated.resources.ic_playlist_add
import churchpresenter.composeapp.generated.resources.ic_play
import churchpresenter.composeapp.generated.resources.ic_skip_next
import churchpresenter.composeapp.generated.resources.ic_skip_previous
import churchpresenter.composeapp.generated.resources.image_counter
import churchpresenter.composeapp.generated.resources.images_suffix
import churchpresenter.composeapp.generated.resources.loading
import churchpresenter.composeapp.generated.resources.loop_off
import churchpresenter.composeapp.generated.resources.loop_on
import churchpresenter.composeapp.generated.resources.next_image
import churchpresenter.composeapp.generated.resources.no_folder_selected
import churchpresenter.composeapp.generated.resources.pause
import churchpresenter.composeapp.generated.resources.play
import churchpresenter.composeapp.generated.resources.previous_image
import churchpresenter.composeapp.generated.resources.select_folder
import churchpresenter.composeapp.generated.resources.select_folder_to_view
import churchpresenter.composeapp.generated.resources.select_image_folder_dialog
import churchpresenter.composeapp.generated.resources.transition_duration
import churchpresenter.composeapp.generated.resources.unit_s
import churchpresenter.composeapp.generated.resources.unit_ms
import churchpresenter.composeapp.generated.resources.pictures_arrow_key_hint
import churchpresenter.composeapp.generated.resources.pictures_reorder_hint
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.PicturesViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.material3.SuggestionChip
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private object RecentPictureFolders {
    private const val MAX = 10
    private val file = java.io.File(System.getProperty("user.home"), ".churchpresenter/recent_picture_folders.json")
    private val pinnedFile = java.io.File(System.getProperty("user.home"), ".churchpresenter/pinned_picture_folders.json")
    val folders = androidx.compose.runtime.mutableStateListOf<String>()
    val pinned = androidx.compose.runtime.mutableStateListOf<String>()

    init { load() }

    fun add(path: String) {
        folders.remove(path)
        folders.add(0, path)
        while (folders.size > MAX) folders.removeLast()
        save()
    }

    fun togglePin(path: String) {
        if (path in pinned) {
            pinned.remove(path)
        } else {
            pinned.remove(path)
            pinned.add(0, path)
        }
        savePinned()
    }

    fun clear() {
        val keep = folders.filter { it in pinned }
        folders.clear()
        folders.addAll(keep)
        save()
    }

    private fun load() {
        try {
            if (file.exists()) {
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val list = json.decodeFromString<List<String>>(file.readText())
                folders.clear()
                folders.addAll(list.take(MAX))
            }
        } catch (_: Exception) {}
        try {
            if (pinnedFile.exists()) {
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val list = json.decodeFromString<List<String>>(pinnedFile.readText())
                pinned.clear()
                pinned.addAll(list)
            }
        } catch (_: Exception) {}
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            val json = kotlinx.serialization.json.Json { encodeDefaults = true }
            file.writeText(json.encodeToString(folders.toList()))
        } catch (_: Exception) {}
    }

    private fun savePinned() {
        try {
            pinnedFile.parentFile?.mkdirs()
            val json = kotlinx.serialization.json.Json { encodeDefaults = true }
            pinnedFile.writeText(json.encodeToString(pinned.toList()))
        } catch (_: Exception) {}
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PicturesTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings? = null,
    onAddToSchedule: ((folderPath: String, folderName: String, imageCount: Int) -> Unit)? = null,
    selectedPictureItem: ScheduleItem.PictureItem? = null,
    presenterManager: PresenterManager? = null,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    viewModel: PicturesViewModel = remember { PicturesViewModel(appSettings) }
) {
    val scope = rememberCoroutineScope()
    val folderDialogTitle = stringResource(Res.string.select_image_folder_dialog)


    // Auto-scroll effect
    LaunchedEffect(viewModel.isPlaying, viewModel.selectedImageIndex, viewModel.autoScrollInterval) {
        if (viewModel.isPlaying && viewModel.images.isNotEmpty()) {
            delay((viewModel.autoScrollInterval * 1000).toLong())
            viewModel.nextImage()
        }
    }

    // Load folder when a picture schedule item is selected
    LaunchedEffect(selectedPictureItem) {
        selectedPictureItem?.let { pictureItem ->
            val folder = File(pictureItem.folderPath)
            if (folder.exists() && folder.isDirectory) {
                viewModel.selectFolder(folder)
            }
        }
    }

    // Sync presenter image when selection or presenting mode changes
    LaunchedEffect(viewModel.selectedImageIndex, presenterManager?.presentingMode) {
        presenterManager?.let { viewModel.syncWithPresenter(it) }
    }

    // Sync animation settings to presenter whenever they change
    LaunchedEffect(viewModel.animationType, viewModel.transitionDuration) {
        presenterManager?.setAnimationType(viewModel.animationType)
        presenterManager?.setTransitionDuration(viewModel.transitionDuration.toInt())
    }

    // Hoisted so onPreviewKeyEvent can read column count for row-based Up/Down navigation
    val gridState = rememberLazyGridState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && viewModel.images.isNotEmpty()) {
                    val columnCount = (gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.column } ?: 0) + 1
                    when (keyEvent.key) {
                        Key.DirectionLeft -> { viewModel.previousImage(); true }
                        Key.DirectionRight -> { viewModel.nextImage(); true }
                        Key.DirectionUp -> {
                            val target = viewModel.selectedImageIndex - columnCount
                            if (target >= 0) viewModel.selectImage(target)
                            true
                        }
                        Key.DirectionDown -> {
                            val target = viewModel.selectedImageIndex + columnCount
                            if (target < viewModel.images.size) viewModel.selectImage(target)
                            true
                        }
                        Key.Spacebar -> { viewModel.togglePlayPause(); true }
                        else -> false
                    }
                } else false
            }
    ) {
        // ── Folder bar ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    viewModel.openFolderChooser(folderDialogTitle) { folderPath ->
                        onSettingsChange { s ->
                            s.copy(pictureSettings = s.pictureSettings.copy(storageDirectory = folderPath))
                        }
                        RecentPictureFolders.add(folderPath)
                    }
                },
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(7.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp)
            ) {
                Icon(painterResource(Res.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    stringResource(Res.string.select_folder),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = androidx.compose.ui.unit.TextUnit(12.5f, androidx.compose.ui.unit.TextUnitType.Sp),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                )
            }
            Text(
                text = viewModel.selectedFolder?.absolutePath ?: stringResource(Res.string.no_folder_selected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            if (viewModel.images.isNotEmpty()) {
                Text(
                    text = "${viewModel.images.size} ${stringResource(Res.string.images_suffix)}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ── Recent folders bar ────────────────────────────────────────
        val recentOrdered = RecentPictureFolders.pinned + RecentPictureFolders.folders.filter { it !in RecentPictureFolders.pinned }
        if (recentOrdered.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(Res.string.recent),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.clear_recents), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    IconButton(onClick = { RecentPictureFolders.clear() }, modifier = Modifier.size(20.dp)) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_close),
                            contentDescription = stringResource(Res.string.clear),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    lazyItems(recentOrdered) { path ->
                        val isPinned = path in RecentPictureFolders.pinned
                        val isActive = viewModel.selectedFolder?.absolutePath == path
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(26.dp)
                                    .background(
                                        if (isActive) MaterialTheme.colorScheme.surfaceVariant
                                        else Color.Transparent,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isActive) MaterialTheme.colorScheme.outline
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        val folder = java.io.File(path)
                                        if (folder.exists() && folder.isDirectory) {
                                            viewModel.selectFolder(folder)
                                            onSettingsChange { s -> s.copy(pictureSettings = s.pictureSettings.copy(storageDirectory = path)) }
                                            RecentPictureFolders.add(path)
                                        }
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = java.io.File(path).name,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                                    color = if (isActive) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    maxLines = 1
                                )
                            }
                            IconButton(onClick = { RecentPictureFolders.togglePin(path) }, modifier = Modifier.size(20.dp)) {
                                Icon(
                                    painter = painterResource(if (isPinned) Res.drawable.ic_star_filled else Res.drawable.ic_star),
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = if (isPinned) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        // ── Playback controls bar ─────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Transport controls (inner gap: 4dp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.previous_image), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    IconButton(
                        onClick = { viewModel.previousImage() },
                        enabled = viewModel.images.isNotEmpty(),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_skip_previous),
                            contentDescription = stringResource(Res.string.previous_image),
                            modifier = Modifier.size(16.dp),
                            tint = if (viewModel.images.isNotEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(if (viewModel.isPlaying) Res.string.pause else Res.string.play), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    FilledIconButton(
                        onClick = { viewModel.togglePlayPause() },
                        enabled = viewModel.images.isNotEmpty(),
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            painter = painterResource(if (viewModel.isPlaying) Res.drawable.ic_pause else Res.drawable.ic_play),
                            contentDescription = stringResource(if (viewModel.isPlaying) Res.string.pause else Res.string.play),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.next_image), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    IconButton(
                        onClick = { viewModel.nextImage() },
                        enabled = viewModel.images.isNotEmpty(),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_skip_next),
                            contentDescription = stringResource(Res.string.next_image),
                            modifier = Modifier.size(16.dp),
                            tint = if (viewModel.images.isNotEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            // Image counter
            if (viewModel.images.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.image_counter, viewModel.selectedImageIndex + 1, viewModel.images.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.widthIn(min = 60.dp)
                )
            }

            // Loop button
            TooltipArea(
                tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(if (viewModel.isLooping) Res.string.loop_on else Res.string.loop_off), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
            ) {
                IconButton(
                    onClick = {
                        viewModel.isLooping = !viewModel.isLooping
                        onSettingsChange { s -> s.copy(pictureSettings = s.pictureSettings.copy(isLooping = viewModel.isLooping)) }
                    },
                    modifier = Modifier.size(28.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = if (viewModel.isLooping) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )
                ) {
                    Icon(painterResource(Res.drawable.ic_refresh), contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }

            // Divider
            Box(modifier = Modifier.width(1.dp).height(22.dp).background(MaterialTheme.colorScheme.outlineVariant))

            // Settings display boxes
            if (appSettings != null) {
                var editingInterval by remember { mutableStateOf(false) }
                var editingTransition by remember { mutableStateOf(false) }
                var intervalInput by remember(appSettings.pictureSettings.autoScrollInterval) {
                    mutableStateOf(appSettings.pictureSettings.autoScrollInterval.toInt().toString())
                }
                var transitionInput by remember(appSettings.pictureSettings.transitionDuration) {
                    mutableStateOf(appSettings.pictureSettings.transitionDuration.toInt().toString())
                }

                // Auto-scroll interval
                Column(
                    modifier = Modifier
                        .height(42.dp)
                        .width(120.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .clickable { editingInterval = true }
                        .padding(start = 11.dp, end = 11.dp, top = 0.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(Res.string.auto_scroll_interval).uppercase(),
                        fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp),
                        lineHeight = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        letterSpacing = 0.9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${appSettings.pictureSettings.autoScrollInterval.toInt()} ${stringResource(Res.string.unit_s)}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                if (editingInterval) {
                    AlertDialog(
                        onDismissRequest = { editingInterval = false },
                        title = { Text(stringResource(Res.string.auto_scroll_interval)) },
                        text = {
                            OutlinedTextField(
                                value = intervalInput,
                                onValueChange = { intervalInput = it },
                                suffix = { Text(stringResource(Res.string.unit_s)) },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                            )
                        },
                        confirmButton = {
                            TextButton(
                                shape = RoundedCornerShape(6.dp),
                                onClick = {
                                intervalInput.toIntOrNull()?.coerceIn(1, 30)?.let { v ->
                                    viewModel.autoScrollInterval = v.toFloat()
                                    onSettingsChange { s -> s.copy(pictureSettings = s.pictureSettings.copy(autoScrollInterval = v.toFloat())) }
                                }
                                editingInterval = false
                            }) { Text(stringResource(Res.string.ok)) }
                        },
                        dismissButton = {
                            TextButton(shape = RoundedCornerShape(6.dp), onClick = { editingInterval = false }) { Text(stringResource(Res.string.cancel)) }
                        }
                    )
                }

                // Transition duration
                Column(
                    modifier = Modifier
                        .height(42.dp)
                        .width(120.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .clickable { editingTransition = true }
                        .padding(start = 11.dp, end = 11.dp, top = 0.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(Res.string.transition_duration).uppercase(),
                        fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp),
                        lineHeight = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        letterSpacing = 0.9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${appSettings.pictureSettings.transitionDuration.toInt()} ${stringResource(Res.string.unit_ms)}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                if (editingTransition) {
                    AlertDialog(
                        onDismissRequest = { editingTransition = false },
                        title = { Text(stringResource(Res.string.transition_duration)) },
                        text = {
                            OutlinedTextField(
                                value = transitionInput,
                                onValueChange = { transitionInput = it },
                                suffix = { Text(stringResource(Res.string.unit_ms)) },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                            )
                        },
                        confirmButton = {
                            TextButton(
                                shape = RoundedCornerShape(6.dp),
                                onClick = {
                                transitionInput.toIntOrNull()?.coerceIn(100, 2000)?.let { v ->
                                    viewModel.transitionDuration = v.toFloat()
                                    onSettingsChange { s -> s.copy(pictureSettings = s.pictureSettings.copy(transitionDuration = v.toFloat())) }
                                }
                                editingTransition = false
                            }) { Text(stringResource(Res.string.ok)) }
                        },
                        dismissButton = {
                            TextButton(shape = RoundedCornerShape(6.dp), onClick = { editingTransition = false }) { Text(stringResource(Res.string.cancel)) }
                        }
                    )
                }

                // Animation type dropdown
                val crossfadeText = stringResource(Res.string.animation_crossfade)
                val fadeText = stringResource(Res.string.animation_fade)
                val slideLeftText = stringResource(Res.string.animation_slide_left)
                val slideRightText = stringResource(Res.string.animation_slide_right)
                val noneText = stringResource(Res.string.animation_none)
                val currentAnimationLabel = when (appSettings.pictureSettings.animationType) {
                    Constants.ANIMATION_FADE -> fadeText
                    Constants.ANIMATION_SLIDE_LEFT -> slideLeftText
                    Constants.ANIMATION_SLIDE_RIGHT -> slideRightText
                    Constants.ANIMATION_NONE -> noneText
                    else -> crossfadeText
                }
                DropdownSelector(
                    modifier = Modifier.width(120.dp),
                    label = stringResource(Res.string.animation_type),
                    items = listOf(crossfadeText, fadeText, slideLeftText, slideRightText, noneText),
                    selected = currentAnimationLabel,
                    onSelectedChange = { selected ->
                        viewModel.animationType = when (selected) {
                            fadeText -> AnimationType.FADE
                            slideLeftText -> AnimationType.SLIDE_LEFT
                            slideRightText -> AnimationType.SLIDE_RIGHT
                            noneText -> AnimationType.NONE
                            else -> AnimationType.CROSSFADE
                        }
                        onSettingsChange { s ->
                            val newType = when (selected) {
                                fadeText -> Constants.ANIMATION_FADE
                                slideLeftText -> Constants.ANIMATION_SLIDE_LEFT
                                slideRightText -> Constants.ANIMATION_SLIDE_RIGHT
                                noneText -> Constants.ANIMATION_NONE
                                else -> Constants.ANIMATION_CROSSFADE
                            }
                            s.copy(pictureSettings = s.pictureSettings.copy(animationType = newType))
                        }
                    }
                )
            }

            Spacer(Modifier.weight(1f))

            // Right action buttons
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (onAddToSchedule != null) {
                    TooltipArea(
                        tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.add_to_schedule), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                        tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                    ) {
                        IconButton(
                            onClick = { viewModel.getScheduleData()?.let { (path, name, count) -> onAddToSchedule(path, name, count) } },
                            enabled = viewModel.images.isNotEmpty(),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary,
                                disabledContainerColor = MaterialTheme.colorScheme.outlineVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            ),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_playlist_add),
                                contentDescription = stringResource(Res.string.add_to_schedule),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                if (presenterManager != null) {
                    TooltipArea(
                        tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.go_live), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                        tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                    ) {
                        IconButton(
                            onClick = { viewModel.goLive(presenterManager) },
                            enabled = viewModel.images.isNotEmpty(),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.outlineVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            ),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Tv,
                                contentDescription = stringResource(Res.string.go_live),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ── Nav hint bar ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(Res.string.pictures_arrow_key_hint),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = androidx.compose.ui.unit.TextUnit(11.5f, androidx.compose.ui.unit.TextUnitType.Sp)
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Text(
                text = "·",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
            )
            Text(
                text = stringResource(Res.string.pictures_reorder_hint),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = androidx.compose.ui.unit.TextUnit(11.5f, androidx.compose.ui.unit.TextUnitType.Sp)
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ── Thumbnail grid ────────────────────────────────────────────
        if (viewModel.images.isNotEmpty()) {
            // Drag-to-reorder state: shift+click+drag, ghost approach (no real-time swaps)
            var draggingFile by remember { mutableStateOf<File?>(null) }
            var draggingFromIndex by remember { mutableStateOf(-1) }
            var dropTargetIndex by remember { mutableStateOf<Int?>(null) }
            var isDragActive by remember { mutableStateOf(false) }
            var dragCursorInGrid by remember { mutableStateOf(Offset.Zero) }

            Box(modifier = Modifier.fillMaxSize().clip(RectangleShape)) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(200.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 18.dp)
                ) {
                    items(viewModel.images, key = { it.absolutePath }) { imageFile ->
                        val index = viewModel.images.indexOf(imageFile)
                        val isSelected = index == viewModel.selectedImageIndex
                        val isDraggingThis = draggingFile == imageFile
                        val isDropTarget = isDragActive && dropTargetIndex == index && !isDraggingThis

                        val borderColor = when {
                            isDropTarget -> MaterialTheme.colorScheme.tertiary
                            isSelected -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }

                        Column(
                            modifier = Modifier
                                .animateItem()
                                .alpha(if (isDraggingThis) 0.35f else 1f)
                                .border(2.dp, borderColor, RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .pointerInput(imageFile) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val pressEvent = awaitPointerEvent(PointerEventPass.Initial)
                                            if (pressEvent.type != PointerEventType.Press) continue
                                            if (!pressEvent.keyboardModifiers.isShiftPressed) continue

                                            pressEvent.changes.forEach { it.consume() }
                                            val startPos = pressEvent.changes.first().position

                                            val idx = viewModel.images.indexOf(imageFile)
                                            val itemInfo = gridState.layoutInfo.visibleItemsInfo
                                                .firstOrNull { it.index == idx }
                                            draggingFile = imageFile
                                            draggingFromIndex = idx
                                            isDragActive = true
                                            dropTargetIndex = idx
                                            dragCursorInGrid = if (itemInfo != null) {
                                                Offset(
                                                    itemInfo.offset.x + startPos.x,
                                                    itemInfo.offset.y + startPos.y
                                                )
                                            } else startPos

                                            var lastPos = startPos
                                            var dragging = true
                                            while (dragging) {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                event.changes.forEach { it.consume() }
                                                when (event.type) {
                                                    PointerEventType.Move -> {
                                                        val pos = event.changes.firstOrNull()?.position ?: continue
                                                        dragCursorInGrid += pos - lastPos
                                                        lastPos = pos
                                                        val target = gridState.layoutInfo.visibleItemsInfo
                                                            .firstOrNull { info ->
                                                                dragCursorInGrid.x >= info.offset.x &&
                                                                dragCursorInGrid.x <= info.offset.x + info.size.width &&
                                                                dragCursorInGrid.y >= info.offset.y &&
                                                                dragCursorInGrid.y <= info.offset.y + info.size.height
                                                            }
                                                        if (target != null) dropTargetIndex = target.index
                                                    }
                                                    PointerEventType.Release -> {
                                                        val from = draggingFromIndex
                                                        val to = dropTargetIndex ?: from
                                                        if (from >= 0 && from != to) viewModel.moveImage(from, to)
                                                        draggingFile = null
                                                        draggingFromIndex = -1
                                                        dropTargetIndex = null
                                                        isDragActive = false
                                                        dragCursorInGrid = Offset.Zero
                                                        dragging = false
                                                    }
                                                    else -> {}
                                                }
                                            }
                                        }
                                    }
                                }
                                .initialPassCombinedClickable(
                                    onClick = {
                                        if (!isDragActive) viewModel.selectImage(viewModel.images.indexOf(imageFile))
                                    },
                                    onDoubleClick = {
                                        if (!isDragActive) {
                                            viewModel.selectImage(viewModel.images.indexOf(imageFile))
                                            if (presenterManager != null) viewModel.goLive(presenterManager)
                                        }
                                    }
                                )
                        ) {
                            // Thumbnail image area
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(148.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                viewModel.thumbnails[imageFile]?.let { bitmap ->
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = imageFile.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } ?: Text(
                                    text = stringResource(Res.string.loading),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Nameplate below image
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = imageFile.nameWithoutExtension,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = androidx.compose.ui.unit.TextUnit(11.5f, androidx.compose.ui.unit.TextUnitType.Sp),
                                        fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold
                                                     else androidx.compose.ui.text.font.FontWeight.Medium
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Floating drag preview — follows cursor, rendered above the grid
                if (isDragActive) {
                    draggingFile?.let { file ->
                        viewModel.thumbnails[file]?.let { bitmap ->
                            Box(
                                modifier = Modifier
                                    .size(150.dp)
                                    .zIndex(10f)
                                    .graphicsLayer {
                                        translationX = dragCursorInGrid.x - 75.dp.toPx()
                                        translationY = dragCursorInGrid.y - 75.dp.toPx()
                                        scaleX = 1.08f
                                        scaleY = 1.08f
                                        shadowElevation = 16f
                                    }
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            ) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }

            // Auto-scroll to selected item in grid
            LaunchedEffect(viewModel.selectedImageIndex) {
                if (viewModel.selectedImageIndex in viewModel.images.indices) {
                    gridState.animateScrollToItem(viewModel.selectedImageIndex)
                }
            }
        } else {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = stringResource(Res.string.select_folder_to_view),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

