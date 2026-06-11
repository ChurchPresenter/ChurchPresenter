package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.Surface
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.go_live
import churchpresenter.composeapp.generated.resources.animation_crossfade
import churchpresenter.composeapp.generated.resources.animation_fade
import churchpresenter.composeapp.generated.resources.animation_none
import churchpresenter.composeapp.generated.resources.animation_slide_left
import churchpresenter.composeapp.generated.resources.animation_slide_right
import churchpresenter.composeapp.generated.resources.animation_type
import churchpresenter.composeapp.generated.resources.auto_scroll_interval
import churchpresenter.composeapp.generated.resources.ic_cast
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.ic_refresh
import churchpresenter.composeapp.generated.resources.ic_star
import churchpresenter.composeapp.generated.resources.ic_star_filled
import churchpresenter.composeapp.generated.resources.clear
import churchpresenter.composeapp.generated.resources.loop_off
import churchpresenter.composeapp.generated.resources.loop_on
import churchpresenter.composeapp.generated.resources.transition_duration
import churchpresenter.composeapp.generated.resources.unit_ms
import churchpresenter.composeapp.generated.resources.unit_s
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Slideshow
import churchpresenter.composeapp.generated.resources.clear_recents
import churchpresenter.composeapp.generated.resources.recent
import churchpresenter.composeapp.generated.resources.ic_pause
import churchpresenter.composeapp.generated.resources.ic_playlist_add
import churchpresenter.composeapp.generated.resources.ic_play
import churchpresenter.composeapp.generated.resources.ic_skip_next
import churchpresenter.composeapp.generated.resources.ic_skip_previous
import churchpresenter.composeapp.generated.resources.loading_slides
import churchpresenter.composeapp.generated.resources.next_image
import churchpresenter.composeapp.generated.resources.no_file_selected_presentation
import churchpresenter.composeapp.generated.resources.pause
import churchpresenter.composeapp.generated.resources.play
import churchpresenter.composeapp.generated.resources.previous_image
import churchpresenter.composeapp.generated.resources.remove
import churchpresenter.composeapp.generated.resources.select_presentation_file
import churchpresenter.composeapp.generated.resources.select_presentation_file_button
import churchpresenter.composeapp.generated.resources.slide_count
import churchpresenter.composeapp.generated.resources.slide_counter
import churchpresenter.composeapp.generated.resources.slide_number
import churchpresenter.composeapp.generated.resources.supported_formats
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.PresentationViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File
import javax.swing.filechooser.FileNameExtensionFilter
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.material3.SuggestionChip
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.image.BufferedImage
import kotlin.io.path.Path

private object RecentPresentationFiles {
    private const val MAX = 10
    private val file = java.io.File(System.getProperty("user.home"), ".churchpresenter/recent_presentation_files.json")
    private val pinnedFile = java.io.File(System.getProperty("user.home"), ".churchpresenter/pinned_presentation_files.json")
    val files = androidx.compose.runtime.mutableStateListOf<String>()
    val pinned = androidx.compose.runtime.mutableStateListOf<String>()

    init { load() }

    fun add(path: String) {
        files.remove(path)
        files.add(0, path)
        while (files.size > MAX) files.removeLast()
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
        val keep = files.filter { it in pinned }
        files.clear()
        files.addAll(keep)
        save()
    }

    private fun load() {
        try {
            if (file.exists()) {
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val list = json.decodeFromString<List<String>>(file.readText())
                files.clear()
                files.addAll(list.take(MAX))
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
            file.writeText(json.encodeToString(files.toList()))
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
fun PresentationTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    onAddToSchedule: ((filePath: String, fileName: String, slideCount: Int, fileType: String) -> Unit)? = null,
    selectedPresentationItem: ScheduleItem.PresentationItem? = null,
    presenterManager: PresenterManager? = null,
    onSlidesLoaded: ((id: String, filePath: String, fileName: String, fileType: String, slides: List<BufferedImage>) -> Unit)? = null,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    /** Externally managed viewModel — hoisted to MainDesktop so remote slide selection works across tab switches. */
    viewModel: PresentationViewModel = remember { PresentationViewModel(appSettings) }
) {
    val scope = rememberCoroutineScope()

    // React to schedule item selection
    LaunchedEffect(selectedPresentationItem) {
        selectedPresentationItem?.let { viewModel.loadPresentationByPath(it.filePath) }
    }
    val presentationFileDialogTitle = stringResource(Res.string.select_presentation_file)

    // Notify server when slides finish loading
    LaunchedEffect(viewModel.slides.size) {
        val file = viewModel.selectedPresentation
        if (file != null && viewModel.slides.isNotEmpty()) {
            val id = file.absolutePath.hashCode().toUInt().toString(16)
            onSlidesLoaded?.invoke(
                id,
                file.absolutePath,
                file.nameWithoutExtension,
                file.extension.lowercase(),
                viewModel.bufferedSlides
            )
        }
    }

    // Auto-play effect using media settings (same as PicturesTab)
    LaunchedEffect(viewModel.isPlaying, viewModel.selectedSlideIndex, viewModel.autoScrollInterval) {
        if (viewModel.isPlaying && viewModel.slides.isNotEmpty()) {
            delay((viewModel.autoScrollInterval * 1000).toLong())
            viewModel.nextSlide()
        }
    }

    // Push current slide to presenter whenever slide index changes (if presenting)
    LaunchedEffect(viewModel.selectedSlideIndex, viewModel.slides.size) {
        val mode = presenterManager?.presentingMode?.value
        val notesList = viewModel.slideNotes
        val idx = viewModel.selectedSlideIndex
        // Also push when any screen is locked to PRESENTATION, even if global mode changed
        val anyScreenOnPresentation = mode == Presenting.PRESENTATION ||
            presenterManager?.screenLocks?.value?.values?.any { it == Presenting.PRESENTATION } == true
        if (anyScreenOnPresentation && presenterManager != null && viewModel.slides.isNotEmpty()) {
            val slide = viewModel.slides.getOrNull(idx)
            presenterManager.setSelectedSlide(slide)
            presenterManager.setNextSlide(viewModel.slides.getOrNull(idx + 1))
            val notes = notesList.getOrElse(idx) { "" }
            presenterManager.setPresenterNotes(notes)
        }
    }

    // Push animation settings to presenter whenever they change
    LaunchedEffect(viewModel.animationType, viewModel.transitionDuration) {
        presenterManager?.setAnimationType(viewModel.animationType)
        presenterManager?.setTransitionDuration(viewModel.transitionDuration.toInt())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && viewModel.slides.isNotEmpty()) {
                    when (keyEvent.key) {
                        Key.DirectionLeft, Key.DirectionUp -> {
                            viewModel.previousSlide()
                            true
                        }
                        Key.DirectionRight, Key.DirectionDown -> {
                            viewModel.nextSlide()
                            true
                        }
                        Key.Spacebar -> {
                            viewModel.togglePlayPause()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        // File Selection Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    scope.launch {
                        val pptFilter = FileNameExtensionFilter(
                            "PowerPoint Files (*.ppt, *.pptx)",
                            "ppt", "pptx"
                        )
                        val keynoteFilter = FileNameExtensionFilter(
                            "Keynote Files (*.key)",
                            "key"
                        )
                        val pdfFilter = FileNameExtensionFilter(
                            "PDF Files (*.pdf)",
                            "pdf"
                        )
                        val allFilter = FileNameExtensionFilter(
                            "All Presentation Files",
                            "ppt", "pptx", "key", "pdf"
                        )
                        val files = FileChooser.platformInstance.chooseMultiple(
                            path = Path(appSettings.presentationStorageDirectory),
                            filters = listOf(allFilter, pptFilter, keynoteFilter, pdfFilter),
                            title = presentationFileDialogTitle,
                            selectDirectory = false
                        )
                        files?.forEach { file ->
                            viewModel.addPresentation(file.toFile())
                            RecentPresentationFiles.add(file.toFile().absolutePath)
                        }
                    }
                }
            ) {
                Text(stringResource(Res.string.select_presentation_file_button), style = MaterialTheme.typography.labelMedium)
            }

            Text(
                text = viewModel.selectedPresentation?.name ?: stringResource(Res.string.no_file_selected_presentation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f)
            )

            if (viewModel.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }

            Text(
                text = stringResource(Res.string.slide_count, viewModel.slides.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        PresentationRecentsRow(
            items = RecentPresentationFiles.files,
            pinned = RecentPresentationFiles.pinned,
            onClear = { RecentPresentationFiles.clear() },
            onTogglePin = { RecentPresentationFiles.togglePin(it) },
            onSelect = { path ->
                val f = java.io.File(path)
                if (f.exists()) {
                    viewModel.addPresentation(f)
                    RecentPresentationFiles.add(path)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Playback controls + action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: playback buttons + settings
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.previous_image), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    IconButton(onClick = { viewModel.previousSlide() }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_skip_previous),
                            contentDescription = stringResource(Res.string.previous_image),
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(if (viewModel.isPlaying) Res.string.pause else Res.string.play), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    IconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (viewModel.isPlaying) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(24.dp)
                            )
                    ) {
                        Icon(
                            painter = painterResource(if (viewModel.isPlaying) Res.drawable.ic_pause else Res.drawable.ic_play),
                            contentDescription = stringResource(if (viewModel.isPlaying) Res.string.pause else Res.string.play),
                            modifier = Modifier.size(28.dp),
                            tint = Color.White
                        )
                    }
                }

                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.next_image), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    IconButton(onClick = { viewModel.nextSlide() }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_skip_next),
                            contentDescription = stringResource(Res.string.next_image),
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (viewModel.slides.isNotEmpty()) {
                    Text(
                        text = stringResource(Res.string.slide_counter, viewModel.selectedSlideIndex + 1, viewModel.slides.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Loop toggle
                TooltipArea(
                    tooltip = {
                        Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                            Text(
                                stringResource(if (viewModel.isLooping) Res.string.loop_on else Res.string.loop_off),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface
                            )
                        }
                    },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    IconButton(
                        onClick = {
                            viewModel.isLooping = !viewModel.isLooping
                            onSettingsChange { s -> s.copy(presentationSettings = s.presentationSettings.copy(isLooping = viewModel.isLooping)) }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (viewModel.isLooping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (viewModel.isLooping) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
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
                var intervalText by remember(appSettings.presentationSettings.autoScrollInterval) {
                    mutableStateOf(appSettings.presentationSettings.autoScrollInterval.toInt().toString())
                }
                var durationText by remember(appSettings.presentationSettings.transitionDuration) {
                    mutableStateOf(appSettings.presentationSettings.transitionDuration.toInt().toString())
                }

                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { value ->
                        intervalText = value
                        val parsed = value.toIntOrNull()
                        if (parsed != null && parsed in 1..30) {
                            viewModel.autoScrollInterval = parsed.toFloat()
                            onSettingsChange { s -> s.copy(presentationSettings = s.presentationSettings.copy(autoScrollInterval = parsed.toFloat())) }
                        }
                    },
                    label = { Text(stringResource(Res.string.auto_scroll_interval)) },
                    suffix = { Text(stringResource(Res.string.unit_s)) },
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
                            onSettingsChange { s -> s.copy(presentationSettings = s.presentationSettings.copy(transitionDuration = parsed.toFloat())) }
                        }
                    },
                    label = { Text(stringResource(Res.string.transition_duration)) },
                    suffix = { Text(stringResource(Res.string.unit_ms)) },
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

                val currentAnimationLabel = when (appSettings.presentationSettings.animationType) {
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
                        onSettingsChange { s -> s.copy(presentationSettings = s.presentationSettings.copy(animationType = newTypeString)) }
                    }
                )
            }

            // Right: action buttons
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onAddToSchedule != null) {
                    TooltipArea(
                        tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.add_to_schedule), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                        tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                    ) {
                        IconButton(
                            onClick = {
                                val file = viewModel.selectedPresentation ?: return@IconButton
                                onAddToSchedule(
                                    file.absolutePath,
                                    file.nameWithoutExtension,
                                    viewModel.slides.size,
                                    file.extension.lowercase()
                                )
                            },
                            enabled = viewModel.selectedPresentation != null,
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
                        tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.go_live), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                        tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                    ) {
                        IconButton(
                            onClick = {
                                val slide = viewModel.slides.getOrNull(viewModel.selectedSlideIndex)
                                presenterManager.setSelectedSlide(slide)
                                presenterManager.setNextSlide(viewModel.slides.getOrNull(viewModel.selectedSlideIndex + 1))
                                presenterManager.setPresenterNotes(viewModel.slideNotes.getOrElse(viewModel.selectedSlideIndex) { "" })
                                presenterManager.setPresentingMode(Presenting.PRESENTATION)
                                presenterManager.setShowPresenterWindow(true)
                            },
                            enabled = viewModel.slides.isNotEmpty(),
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

        if (viewModel.slides.isNotEmpty()) {

            // Slide Grid - Similar to PicturesTab
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 200.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(viewModel.slides) { index, slide ->
                    SlideThumbnail(
                        slide = slide,
                        slideNumber = index + 1,
                        isSelected = viewModel.selectedSlideIndex == index,
                        onClick = { viewModel.selectSlide(index) },
                        onDoubleClick = {
                            viewModel.selectSlide(index)
                            if (presenterManager != null) {
                                val selectedSlide = viewModel.slides.getOrNull(index)
                                presenterManager.setSelectedSlide(selectedSlide)
                                presenterManager.setNextSlide(viewModel.slides.getOrNull(index + 1))
                                presenterManager.setPresenterNotes(viewModel.slideNotes.getOrElse(index) { "" })
                                presenterManager.setPresentingMode(Presenting.PRESENTATION)
                                presenterManager.setShowPresenterWindow(true)
                            }
                        }
                    )
                }
            }
        } else if (viewModel.selectedPresentation != null) {
            // Loading state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Text(
                        text = stringResource(Res.string.loading_slides),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
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
                        imageVector = Icons.Default.Slideshow,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = stringResource(Res.string.select_presentation_file),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = stringResource(Res.string.supported_formats),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // Presentation file list (if multiple files)
        if (viewModel.presentations.size > 1) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                viewModel.presentations.forEach { file ->
                    PresentationChip(
                        file = file,
                        isSelected = viewModel.selectedPresentation == file,
                        onSelect = { viewModel.selectPresentation(file) },
                        onRemove = { viewModel.removePresentation(file) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SlideThumbnail(
    slide: ImageBitmap,
    slideNumber: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .combinedClickable(onClick = onClick, onDoubleClick = onDoubleClick)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Color.Black, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = slide,
                contentDescription = stringResource(Res.string.slide_number, slideNumber),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(Res.string.slide_number, slideNumber),
            style = MaterialTheme.typography.bodySmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PresentationChip(
    file: File,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = file.nameWithoutExtension,
            style = MaterialTheme.typography.bodySmall,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
        )

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(20.dp)
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_close),
                contentDescription = stringResource(Res.string.remove),
                modifier = Modifier.size(16.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresentationRecentsRow(
    items: List<String>,
    pinned: List<String>,
    onClear: () -> Unit,
    onTogglePin: (String) -> Unit,
    onSelect: (String) -> Unit
) {
    val ordered = pinned + items.filter { it !in pinned }
    if (ordered.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.recent),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        TooltipArea(
            tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.clear_recents), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
            tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
        ) {
            IconButton(onClick = onClear, modifier = Modifier.size(20.dp)) {
                Icon(
                    painter = painterResource(Res.drawable.ic_close),
                    contentDescription = stringResource(Res.string.clear),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            lazyItems(ordered) { path ->
                val isPinned = path in pinned
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SuggestionChip(
                        onClick = { onSelect(path) },
                        label = {
                            Text(
                                text = java.io.File(path).name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                    IconButton(onClick = { onTogglePin(path) }, modifier = Modifier.size(20.dp)) {
                        Icon(
                            painter = painterResource(if (isPinned) Res.drawable.ic_star_filled else Res.drawable.ic_star),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (isPinned) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}


