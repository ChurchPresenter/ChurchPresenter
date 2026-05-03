package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
import churchpresenter.composeapp.generated.resources.ic_cast
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
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.PicturesViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && viewModel.images.isNotEmpty()) {
                    when (keyEvent.key) {
                        Key.DirectionLeft, Key.DirectionUp -> { viewModel.previousImage(); true }
                        Key.DirectionRight, Key.DirectionDown -> { viewModel.nextImage(); true }
                        Key.Spacebar -> { viewModel.togglePlayPause(); true }
                        else -> false
                    }
                } else false
            }
    ) {
        // Folder selection row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = {
                viewModel.openFolderChooser(folderDialogTitle) { folderPath ->
                    onSettingsChange { s ->
                        s.copy(pictureSettings = s.pictureSettings.copy(storageDirectory = folderPath))
                    }
                }
            }) {
                Text("📁 ${stringResource(Res.string.select_folder)}", style = MaterialTheme.typography.labelMedium)
            }

            Text(
                text = viewModel.selectedFolder?.absolutePath
                    ?: stringResource(Res.string.no_folder_selected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "${viewModel.images.size} ${stringResource(Res.string.images_suffix)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Playback controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: playback buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TooltipArea(
                        tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.previous_image), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                        tooltipPlacement = TooltipPlacement.CursorPoint()
                    ) {
                        IconButton(
                            onClick = { viewModel.previousImage() },
                            enabled = viewModel.images.isNotEmpty()
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_skip_previous),
                                contentDescription = stringResource(Res.string.previous_image),
                                modifier = Modifier.size(32.dp),
                                tint = if (viewModel.images.isNotEmpty())
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    }

                    TooltipArea(
                        tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(if (viewModel.isPlaying) Res.string.pause else Res.string.play), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                        tooltipPlacement = TooltipPlacement.CursorPoint()
                    ) {
                        IconButton(
                            onClick = { viewModel.togglePlayPause() },
                            enabled = viewModel.images.isNotEmpty(),
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (viewModel.isPlaying) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(24.dp)
                                )
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (viewModel.isPlaying) Res.drawable.ic_pause else Res.drawable.ic_play
                                ),
                                contentDescription = stringResource(
                                    if (viewModel.isPlaying) Res.string.pause else Res.string.play
                                ),
                                modifier = Modifier.size(28.dp),
                                tint = Color.White
                            )
                        }
                    }

                    TooltipArea(
                        tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.next_image), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                        tooltipPlacement = TooltipPlacement.CursorPoint()
                    ) {
                        IconButton(
                            onClick = { viewModel.nextImage() },
                            enabled = viewModel.images.isNotEmpty()
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_skip_next),
                                contentDescription = stringResource(Res.string.next_image),
                                modifier = Modifier.size(32.dp),
                                tint = if (viewModel.images.isNotEmpty())
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    }

                    Text(
                        text = stringResource(
                            Res.string.image_counter,
                            viewModel.selectedImageIndex + 1,
                            viewModel.images.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    TooltipArea(
                        tooltip = {
                            Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                Text(
                                    stringResource(if (viewModel.isLooping) Res.string.loop_on else Res.string.loop_off),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        tooltipPlacement = TooltipPlacement.CursorPoint()
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.isLooping = !viewModel.isLooping
                                onSettingsChange { s ->
                                    s.copy(pictureSettings = s.pictureSettings.copy(isLooping = viewModel.isLooping))
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (viewModel.isLooping)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (viewModel.isLooping)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_refresh),
                                contentDescription = stringResource(if (viewModel.isLooping) Res.string.loop_on else Res.string.loop_off),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Inline slideshow settings
                    if (appSettings != null) {
                        var intervalText by remember(appSettings.pictureSettings.autoScrollInterval) {
                            mutableStateOf(appSettings.pictureSettings.autoScrollInterval.toInt().toString())
                        }
                        var durationText by remember(appSettings.pictureSettings.transitionDuration) {
                            mutableStateOf(appSettings.pictureSettings.transitionDuration.toInt().toString())
                        }

                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { value ->
                                intervalText = value
                                val parsed = value.toIntOrNull()
                                if (parsed != null && parsed in 1..30) {
                                    viewModel.autoScrollInterval = parsed.toFloat()
                                    onSettingsChange { s ->
                                        s.copy(pictureSettings = s.pictureSettings.copy(autoScrollInterval = parsed.toFloat()))
                                    }
                                }
                            },
                            label = { Text(stringResource(Res.string.auto_scroll_interval)) },
                            suffix = { Text("s") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(150.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )

                        OutlinedTextField(
                            value = durationText,
                            onValueChange = { value ->
                                durationText = value
                                val parsed = value.toIntOrNull()
                                if (parsed != null && parsed in 100..2000) {
                                    viewModel.transitionDuration = parsed.toFloat()
                                    onSettingsChange { s ->
                                        s.copy(pictureSettings = s.pictureSettings.copy(transitionDuration = parsed.toFloat()))
                                    }
                                }
                            },
                            label = { Text(stringResource(Res.string.transition_duration)) },
                            suffix = { Text("ms") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(170.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )

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
                            modifier = Modifier.width(160.dp),
                            label = stringResource(Res.string.animation_type),
                            items = listOf(crossfadeText, fadeText, slideLeftText, slideRightText, noneText),
                            selected = currentAnimationLabel,
                            onSelectedChange = { selected ->
                                val newTypeString = when (selected) {
                                    fadeText -> Constants.ANIMATION_FADE
                                    slideLeftText -> Constants.ANIMATION_SLIDE_LEFT
                                    slideRightText -> Constants.ANIMATION_SLIDE_RIGHT
                                    noneText -> Constants.ANIMATION_NONE
                                    else -> Constants.ANIMATION_CROSSFADE
                                }
                                viewModel.animationType = when (selected) {
                                    fadeText -> AnimationType.FADE
                                    slideLeftText -> AnimationType.SLIDE_LEFT
                                    slideRightText -> AnimationType.SLIDE_RIGHT
                                    noneText -> AnimationType.NONE
                                    else -> AnimationType.CROSSFADE
                                }
                                onSettingsChange { s ->
                                    s.copy(pictureSettings = s.pictureSettings.copy(animationType = newTypeString))
                                }
                            }
                        )
                    }
                }

                // Right: action buttons
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onAddToSchedule != null) {
                        TooltipArea(
                            tooltip = {
                                Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                    Text(stringResource(Res.string.add_to_schedule), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            tooltipPlacement = TooltipPlacement.CursorPoint()
                        ) {
                            IconButton(
                                onClick = {
                                    viewModel.getScheduleData()?.let { (path, name, count) ->
                                        onAddToSchedule(path, name, count)
                                    }
                                },
                                enabled = viewModel.images.isNotEmpty(),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            ) {
                                Icon(painter = painterResource(Res.drawable.ic_playlist_add), contentDescription = stringResource(Res.string.add_to_schedule), modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    if (presenterManager != null) {
                        TooltipArea(
                            tooltip = {
                                Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                    Text(stringResource(Res.string.go_live), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            tooltipPlacement = TooltipPlacement.CursorPoint()
                        ) {
                            IconButton(
                                onClick = { viewModel.goLive(presenterManager) },
                                enabled = viewModel.images.isNotEmpty(),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            ) {
                                Icon(painter = painterResource(Res.drawable.ic_cast), contentDescription = stringResource(Res.string.go_live), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

        Spacer(modifier = Modifier.height(16.dp))

        if (viewModel.images.isNotEmpty()) {
            // Image grid
            val gridState = rememberLazyGridState()

            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(viewModel.images) { imageFile ->
                    val isSelected = viewModel.images.indexOf(imageFile) == viewModel.selectedImageIndex

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .combinedClickable(
                                onClick = { viewModel.selectImage(viewModel.images.indexOf(imageFile)) },
                                onDoubleClick = {
                                    viewModel.selectImage(viewModel.images.indexOf(imageFile))
                                    if (presenterManager != null) viewModel.goLive(presenterManager)
                                }
                            )
                            .padding(4.dp)
                    ) {
                        viewModel.thumbnails[imageFile]?.let { bitmap ->
                            Image(
                                bitmap = bitmap,
                                contentDescription = imageFile.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } ?: Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(Res.string.loading),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // File name overlay
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(4.dp)
                        ) {
                            Text(
                                text = imageFile.nameWithoutExtension,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                maxLines = 1,
                                modifier = Modifier.align(Alignment.Center)
                            )
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
                    Text(text = "📷", style = MaterialTheme.typography.displayLarge)
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
