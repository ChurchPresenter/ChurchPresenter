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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.ic_folder
import churchpresenter.composeapp.generated.resources.animation_crossfade
import churchpresenter.composeapp.generated.resources.animation_fade
import churchpresenter.composeapp.generated.resources.animation_none
import churchpresenter.composeapp.generated.resources.animation_slide_left
import churchpresenter.composeapp.generated.resources.animation_slide_right
import churchpresenter.composeapp.generated.resources.animation_type
import churchpresenter.composeapp.generated.resources.auto_scroll_interval
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.clear
import churchpresenter.composeapp.generated.resources.clear_recents
import churchpresenter.composeapp.generated.resources.go_live
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.ic_playlist_add
import churchpresenter.composeapp.generated.resources.ic_refresh
import churchpresenter.composeapp.generated.resources.ic_pause
import churchpresenter.composeapp.generated.resources.ic_play
import churchpresenter.composeapp.generated.resources.ic_skip_next
import churchpresenter.composeapp.generated.resources.ic_skip_previous
import churchpresenter.composeapp.generated.resources.ic_star
import churchpresenter.composeapp.generated.resources.ic_star_filled
import churchpresenter.composeapp.generated.resources.loading_slides
import churchpresenter.composeapp.generated.resources.loop_off
import churchpresenter.composeapp.generated.resources.loop_on
import churchpresenter.composeapp.generated.resources.next_image
import churchpresenter.composeapp.generated.resources.no_file_selected_presentation
import churchpresenter.composeapp.generated.resources.ok
import churchpresenter.composeapp.generated.resources.pause
import churchpresenter.composeapp.generated.resources.pictures_arrow_key_hint
import churchpresenter.composeapp.generated.resources.play
import churchpresenter.composeapp.generated.resources.presentation_freeze_output
import churchpresenter.composeapp.generated.resources.presentation_remote_control
import churchpresenter.composeapp.generated.resources.presentation_remote_copy_url
import churchpresenter.composeapp.generated.resources.presentation_remote_enable
import churchpresenter.composeapp.generated.resources.presentation_remote_password_hint
import churchpresenter.composeapp.generated.resources.presentation_unfreeze_output
import churchpresenter.composeapp.generated.resources.previous_image
import churchpresenter.composeapp.generated.resources.qa_downloading_tunnel
import churchpresenter.composeapp.generated.resources.qa_enable_public_access
import churchpresenter.composeapp.generated.resources.qa_disable_public_access
import churchpresenter.composeapp.generated.resources.qa_local
import churchpresenter.composeapp.generated.resources.qa_public
import churchpresenter.composeapp.generated.resources.qa_public_access
import churchpresenter.composeapp.generated.resources.qa_public_access_description
import churchpresenter.composeapp.generated.resources.qa_qr_code_shows
import churchpresenter.composeapp.generated.resources.qa_retry
import churchpresenter.composeapp.generated.resources.qa_starting_tunnel
import churchpresenter.composeapp.generated.resources.recent
import churchpresenter.composeapp.generated.resources.remove
import churchpresenter.composeapp.generated.resources.select_presentation_file
import churchpresenter.composeapp.generated.resources.select_presentation_file_button
import churchpresenter.composeapp.generated.resources.slide_count
import churchpresenter.composeapp.generated.resources.loading_slides_progress
import churchpresenter.composeapp.generated.resources.slide_counter
import churchpresenter.composeapp.generated.resources.slide_number
import churchpresenter.composeapp.generated.resources.presentation_static_note
import churchpresenter.composeapp.generated.resources.supported_formats
import churchpresenter.composeapp.generated.resources.transition_duration
import churchpresenter.composeapp.generated.resources.unit_ms
import churchpresenter.composeapp.generated.resources.unit_s
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.presenter.generateQRCodeBitmap
import org.churchpresenter.app.churchpresenter.server.TunnelStatus
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.PresentationViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onSlidesLoaded: ((id: String, filePath: String, fileName: String, fileType: String, slideFiles: List<File>) -> Unit)? = null,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    viewModel: PresentationViewModel = remember { PresentationViewModel(appSettings) },
    tunnelStatus: TunnelStatus = TunnelStatus.Idle,
    tunnelUrl: String = "",
    serverUrl: String = "",
    presentationDisplayUrl: String = "",
    onPresentationDisplayUrlChanged: (String) -> Unit = {},
    onStartTunnel: () -> Unit = {},
    onStopTunnel: () -> Unit = {},
    presentationFrozen: Boolean = false,
    onFreezeToggle: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedPresentationItem) {
        selectedPresentationItem?.let { viewModel.loadPresentationByPath(it.filePath) }
    }

    val presentationFileDialogTitle = stringResource(Res.string.select_presentation_file)

    // Startup: remove slide caches for presentations not in recents or pinned
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val keepPaths = (RecentPresentationFiles.files + RecentPresentationFiles.pinned).toSet()
            PresentationViewModel.cleanupOrphanedCaches(keepPaths)
        }
    }

    // Fires on every load completion (fresh render or cache hit) — never fires O(N) times per slide
    LaunchedEffect(viewModel.loadGeneration) {
        if (viewModel.loadGeneration > 0) {
            val f = viewModel.selectedPresentation
            if (f != null && viewModel.slideFiles.isNotEmpty()) {
                val id = f.absolutePath.hashCode().toUInt().toString(16)
                onSlidesLoaded?.invoke(id, f.absolutePath, f.nameWithoutExtension, f.extension.lowercase(), viewModel.slideFiles.toList())
            }
        }
    }

    LaunchedEffect(viewModel.isPlaying, viewModel.selectedSlideIndex, viewModel.autoScrollInterval) {
        if (viewModel.isPlaying && viewModel.slideFiles.isNotEmpty()) {
            delay((viewModel.autoScrollInterval * 1000).toLong())
            viewModel.nextSlide()
        }
    }

    LaunchedEffect(viewModel.selectedSlideIndex, viewModel.slideFiles.size) {
        val mode = presenterManager?.presentingMode?.value
        val idx = viewModel.selectedSlideIndex
        val anyScreenOnPresentation = mode == Presenting.PRESENTATION ||
            presenterManager?.screenLocks?.value?.values?.any { it == Presenting.PRESENTATION } == true
        if (anyScreenOnPresentation && viewModel.slideFiles.isNotEmpty()) {
            val bitmap = viewModel.slideFiles.getOrNull(idx)?.let { f ->
                withContext(Dispatchers.IO) {
                    org.jetbrains.skia.Image.makeFromEncoded(f.readBytes()).toComposeImageBitmap()
                }
            }
            val nextBitmap = viewModel.slideFiles.getOrNull(idx + 1)?.let { f ->
                withContext(Dispatchers.IO) {
                    org.jetbrains.skia.Image.makeFromEncoded(f.readBytes()).toComposeImageBitmap()
                }
            }
            presenterManager.setSelectedSlide(bitmap)
            presenterManager.setNextSlide(nextBitmap)
            presenterManager.setPresenterNotes(viewModel.slideNotes.getOrElse(idx) { "" })
        }
    }

    LaunchedEffect(viewModel.animationType, viewModel.transitionDuration) {
        presenterManager?.setAnimationType(viewModel.animationType)
        presenterManager?.setTransitionDuration(viewModel.transitionDuration.toInt())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && viewModel.slideFiles.isNotEmpty()) {
                    when (keyEvent.key) {
                        Key.DirectionLeft, Key.DirectionUp -> { viewModel.previousSlide(); true }
                        Key.DirectionRight, Key.DirectionDown -> { viewModel.nextSlide(); true }
                        Key.Spacebar -> { viewModel.togglePlayPause(); true }
                        else -> false
                    }
                } else false
            }
    ) {
        // ── File bar ──────────────────────────────────────────────────
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
                    scope.launch {
                        val allFilter = FileNameExtensionFilter("All Presentation Files", "ppt", "pptx", "key", "pdf")
                        val pptFilter = FileNameExtensionFilter("PowerPoint Files (*.ppt, *.pptx)", "ppt", "pptx")
                        val keynoteFilter = FileNameExtensionFilter("Keynote Files (*.key)", "key")
                        val pdfFilter = FileNameExtensionFilter("PDF Files (*.pdf)", "pdf")
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
                    stringResource(Res.string.select_presentation_file_button),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
            Text(
                text = viewModel.selectedPresentation?.name ?: stringResource(Res.string.no_file_selected_presentation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (viewModel.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            if (viewModel.slideFiles.isNotEmpty()) {
                val slideCountText = if (viewModel.isLoading && viewModel.totalSlides > 0)
                    stringResource(Res.string.loading_slides_progress, viewModel.slideFiles.size, viewModel.totalSlides)
                else
                    stringResource(Res.string.slide_count, viewModel.slideFiles.size)
                Text(
                    text = slideCountText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ── Recent files bar ──────────────────────────────────────────
        val recentOrdered = RecentPresentationFiles.pinned + RecentPresentationFiles.files.filter { it !in RecentPresentationFiles.pinned }
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
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.clear_recents), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    IconButton(onClick = { RecentPresentationFiles.clear() }, modifier = Modifier.size(20.dp)) {
                        Icon(painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.clear), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    lazyItems(recentOrdered) { path ->
                        val isPinned = path in RecentPresentationFiles.pinned
                        val isActive = viewModel.selectedPresentation?.absolutePath == path
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Box(
                                modifier = Modifier
                                    .height(26.dp)
                                    .background(
                                        if (isActive) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .border(1.dp, if (isActive) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                    .clickable {
                                        val f = java.io.File(path)
                                        if (f.exists()) {
                                            viewModel.addPresentation(f)
                                            RecentPresentationFiles.add(path)
                                        }
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = java.io.File(path).name,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    maxLines = 1
                                )
                            }
                            IconButton(onClick = { RecentPresentationFiles.togglePin(path) }, modifier = Modifier.size(20.dp)) {
                                Icon(
                                    painter = painterResource(if (isPinned) Res.drawable.ic_star_filled else Res.drawable.ic_star),
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
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
            // Transport (inner gap: 4dp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.previous_image), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    IconButton(onClick = { viewModel.previousSlide() }, modifier = Modifier.size(30.dp)) {
                        Icon(painterResource(Res.drawable.ic_skip_previous), contentDescription = stringResource(Res.string.previous_image), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(if (viewModel.isPlaying) Res.string.pause else Res.string.play), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    FilledIconButton(
                        onClick = { viewModel.togglePlayPause() },
                        enabled = viewModel.slideFiles.isNotEmpty(),
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(painterResource(if (viewModel.isPlaying) Res.drawable.ic_pause else Res.drawable.ic_play), contentDescription = stringResource(if (viewModel.isPlaying) Res.string.pause else Res.string.play), modifier = Modifier.size(15.dp))
                    }
                }
                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.next_image), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    IconButton(onClick = { viewModel.nextSlide() }, modifier = Modifier.size(30.dp)) {
                        Icon(painterResource(Res.drawable.ic_skip_next), contentDescription = stringResource(Res.string.next_image), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            }

            if (viewModel.slideFiles.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.slide_counter, viewModel.selectedSlideIndex + 1, viewModel.slideFiles.size),
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
                        onSettingsChange { s -> s.copy(presentationSettings = s.presentationSettings.copy(isLooping = viewModel.isLooping)) }
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

            // Freeze button
            TooltipArea(
                tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(if (presentationFrozen) Res.string.presentation_unfreeze_output else Res.string.presentation_freeze_output), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
            ) {
                IconButton(
                    onClick = onFreezeToggle,
                    modifier = Modifier.size(28.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (presentationFrozen) MaterialTheme.colorScheme.errorContainer else Color.Transparent,
                        contentColor = if (presentationFrozen) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                ) {
                    Icon(if (presentationFrozen) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }

            // Divider
            Box(modifier = Modifier.width(1.dp).height(22.dp).background(MaterialTheme.colorScheme.outlineVariant))

            // Clickable display boxes + animation dropdown
            var editingInterval by remember { mutableStateOf(false) }
            var editingTransition by remember { mutableStateOf(false) }
            var intervalInput by remember(appSettings.presentationSettings.autoScrollInterval) {
                mutableStateOf(appSettings.presentationSettings.autoScrollInterval.toInt().toString())
            }
            var transitionInput by remember(appSettings.presentationSettings.transitionDuration) {
                mutableStateOf(appSettings.presentationSettings.transitionDuration.toInt().toString())
            }

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
                Text(stringResource(Res.string.auto_scroll_interval).uppercase(), fontSize = 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), maxLines = 1)
                Text("${appSettings.presentationSettings.autoScrollInterval.toInt()} ${stringResource(Res.string.unit_s)}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (editingInterval) {
                AlertDialog(
                    onDismissRequest = { editingInterval = false },
                    title = { Text(stringResource(Res.string.auto_scroll_interval)) },
                    text = { OutlinedTextField(value = intervalInput, onValueChange = { intervalInput = it }, suffix = { Text(stringResource(Res.string.unit_s)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
                    confirmButton = { TextButton(shape = RoundedCornerShape(6.dp), onClick = { intervalInput.toIntOrNull()?.coerceIn(1, 30)?.let { v -> viewModel.autoScrollInterval = v.toFloat(); onSettingsChange { s -> s.copy(presentationSettings = s.presentationSettings.copy(autoScrollInterval = v.toFloat())) } }; editingInterval = false }) { Text(stringResource(Res.string.ok)) } },
                    dismissButton = { TextButton(shape = RoundedCornerShape(6.dp), onClick = { editingInterval = false }) { Text(stringResource(Res.string.cancel)) } }
                )
            }

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
                Text(stringResource(Res.string.transition_duration).uppercase(), fontSize = 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), maxLines = 1)
                Text("${appSettings.presentationSettings.transitionDuration.toInt()} ${stringResource(Res.string.unit_ms)}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (editingTransition) {
                AlertDialog(
                    onDismissRequest = { editingTransition = false },
                    title = { Text(stringResource(Res.string.transition_duration)) },
                    text = { OutlinedTextField(value = transitionInput, onValueChange = { transitionInput = it }, suffix = { Text(stringResource(Res.string.unit_ms)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
                    confirmButton = { TextButton(shape = RoundedCornerShape(6.dp), onClick = { transitionInput.toIntOrNull()?.coerceIn(100, 2000)?.let { v -> viewModel.transitionDuration = v.toFloat(); onSettingsChange { s -> s.copy(presentationSettings = s.presentationSettings.copy(transitionDuration = v.toFloat())) } }; editingTransition = false }) { Text(stringResource(Res.string.ok)) } },
                    dismissButton = { TextButton(shape = RoundedCornerShape(6.dp), onClick = { editingTransition = false }) { Text(stringResource(Res.string.cancel)) } }
                )
            }

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
                        s.copy(presentationSettings = s.presentationSettings.copy(animationType = when (selected) {
                            fadeText -> Constants.ANIMATION_FADE
                            slideLeftText -> Constants.ANIMATION_SLIDE_LEFT
                            slideRightText -> Constants.ANIMATION_SLIDE_RIGHT
                            noneText -> Constants.ANIMATION_NONE
                            else -> Constants.ANIMATION_CROSSFADE
                        }))
                    }
                }
            )

            Spacer(Modifier.weight(1f))

            // Action buttons
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (onAddToSchedule != null) {
                    TooltipArea(
                        tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.add_to_schedule), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                        tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                    ) {
                        IconButton(
                            onClick = {
                                val f = viewModel.selectedPresentation ?: return@IconButton
                                onAddToSchedule(f.absolutePath, f.nameWithoutExtension, viewModel.slideFiles.size, f.extension.lowercase())
                            },
                            enabled = viewModel.selectedPresentation != null,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary,
                                disabledContainerColor = MaterialTheme.colorScheme.outlineVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            ),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(painterResource(Res.drawable.ic_playlist_add), contentDescription = stringResource(Res.string.add_to_schedule), modifier = Modifier.size(16.dp))
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
                                val idx = viewModel.selectedSlideIndex
                                scope.launch {
                                    val bitmap = viewModel.slideFiles.getOrNull(idx)?.let { f ->
                                        withContext(Dispatchers.IO) {
                                            org.jetbrains.skia.Image.makeFromEncoded(f.readBytes()).toComposeImageBitmap()
                                        }
                                    }
                                    val nextBitmap = viewModel.slideFiles.getOrNull(idx + 1)?.let { f ->
                                        withContext(Dispatchers.IO) {
                                            org.jetbrains.skia.Image.makeFromEncoded(f.readBytes()).toComposeImageBitmap()
                                        }
                                    }
                                    presenterManager.setSelectedSlide(bitmap)
                                    presenterManager.setNextSlide(nextBitmap)
                                    presenterManager.setPresenterNotes(viewModel.slideNotes.getOrElse(idx) { "" })
                                }
                                presenterManager.setPresentingMode(Presenting.PRESENTATION)
                                presenterManager.setShowPresenterWindow(true)
                            },
                            enabled = viewModel.slideFiles.isNotEmpty(),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.outlineVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            ),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Tv, contentDescription = stringResource(Res.string.go_live), modifier = Modifier.size(15.dp))
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.pictures_arrow_key_hint),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ── Slide content + right sidebar ────────────────────────────
        Row(modifier = Modifier.fillMaxSize()) {
            // ── Left: slide grid / states ────────────────────────────
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (viewModel.slideFiles.isNotEmpty()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 200.dp),
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 18.dp)
                    ) {
                        itemsIndexed(viewModel.slideFiles) { index, slideFile ->
                            val bitmap = remember(slideFile) {
                                org.jetbrains.skia.Image.makeFromEncoded(slideFile.readBytes()).toComposeImageBitmap()
                            }
                            SlideThumbnail(
                                slide = bitmap,
                                slideNumber = index + 1,
                                isSelected = viewModel.selectedSlideIndex == index,
                                onClick = { viewModel.selectSlide(index) },
                                onDoubleClick = {
                                    viewModel.selectSlide(index)
                                    if (presenterManager != null) {
                                        scope.launch {
                                            val cur = viewModel.slideFiles.getOrNull(index)?.let { f ->
                                                withContext(Dispatchers.IO) {
                                                    org.jetbrains.skia.Image.makeFromEncoded(f.readBytes()).toComposeImageBitmap()
                                                }
                                            }
                                            val next = viewModel.slideFiles.getOrNull(index + 1)?.let { f ->
                                                withContext(Dispatchers.IO) {
                                                    org.jetbrains.skia.Image.makeFromEncoded(f.readBytes()).toComposeImageBitmap()
                                                }
                                            }
                                            presenterManager.setSelectedSlide(cur)
                                            presenterManager.setNextSlide(next)
                                            presenterManager.setPresenterNotes(viewModel.slideNotes.getOrElse(index) { "" })
                                        }
                                        presenterManager.setPresentingMode(Presenting.PRESENTATION)
                                        presenterManager.setShowPresenterWindow(true)
                                    }
                                }
                            )
                        }
                    }
                } else if (viewModel.selectedPresentation != null) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Text(stringResource(Res.string.loading_slides), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(Res.string.select_presentation_file), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text(stringResource(Res.string.supported_formats), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            Text(
                                stringResource(Res.string.presentation_static_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                }

                // Multi-file chip row
                if (viewModel.presentations.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        viewModel.presentations.forEach { f ->
                            Row(
                                modifier = Modifier
                                    .background(
                                        if (viewModel.selectedPresentation == f) MaterialTheme.colorScheme.surfaceVariant
                                        else Color.Transparent,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .border(1.dp, if (viewModel.selectedPresentation == f) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                    .clickable { viewModel.selectPresentation(f) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(f.nameWithoutExtension, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                IconButton(onClick = {
                                    val inRecents = f.absolutePath in RecentPresentationFiles.files
                                    val inPinned = f.absolutePath in RecentPresentationFiles.pinned
                                    viewModel.removePresentation(f, isInRecentsOrPinned = inRecents || inPinned)
                                }, modifier = Modifier.size(16.dp)) {
                                    Icon(painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.remove), modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            }

            // ── Right sidebar: remote control ─────────────────────────
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val sidebarScroll = rememberScrollState()
            val clipboardManager = LocalClipboardManager.current
            Column(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
                    .verticalScroll(sidebarScroll)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header row with toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(Res.string.presentation_remote_control),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = appSettings.presentationRemoteSettings.remoteControlEnabled,
                        onCheckedChange = { enabled ->
                            onSettingsChange { s -> s.copy(presentationRemoteSettings = s.presentationRemoteSettings.copy(remoteControlEnabled = enabled)) }
                        }
                    )
                }

                if (appSettings.presentationRemoteSettings.remoteControlEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // QR code
                    val qrBaseUrl = presentationDisplayUrl.ifEmpty { serverUrl }
                    val qrUrl = if (qrBaseUrl.isNotEmpty()) {
                        val pw = appSettings.presentationRemoteSettings.remotePassword
                        if (pw.isNotEmpty()) "$qrBaseUrl/presentation-remote?password=$pw"
                        else "$qrBaseUrl/presentation-remote"
                    } else ""

                    if (qrUrl.isNotEmpty()) {
                        val qrBitmap = remember(qrUrl) { generateQRCodeBitmap(qrUrl, 180) }
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap,
                                contentDescription = null,
                                modifier = Modifier.size(180.dp).align(Alignment.CenterHorizontally)
                            )
                        }
                        Text(
                            stringResource(Res.string.qa_qr_code_shows),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        SelectionContainer {
                            Text(
                                qrUrl,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 3,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedButton(
                            onClick = { clipboardManager.setText(AnnotatedString(qrUrl)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(Res.string.presentation_remote_copy_url), style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Tunnel / public access section
                    Text(
                        stringResource(Res.string.qa_public_access),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    when (tunnelStatus) {
                        is TunnelStatus.Idle -> {
                            Text(
                                stringResource(Res.string.qa_public_access_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            OutlinedButton(
                                onClick = onStartTunnel,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(stringResource(Res.string.qa_enable_public_access), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        is TunnelStatus.Downloading -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(stringResource(Res.string.qa_downloading_tunnel), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is TunnelStatus.Starting -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(stringResource(Res.string.qa_starting_tunnel), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is TunnelStatus.Connected -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { onPresentationDisplayUrlChanged(serverUrl) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = if (presentationDisplayUrl.isEmpty() || presentationDisplayUrl == serverUrl)
                                        ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    else ButtonDefaults.outlinedButtonColors()
                                ) { Text(stringResource(Res.string.qa_local), style = MaterialTheme.typography.labelSmall) }
                                OutlinedButton(
                                    onClick = { onPresentationDisplayUrlChanged(tunnelUrl) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = if (presentationDisplayUrl == tunnelUrl)
                                        ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    else ButtonDefaults.outlinedButtonColors()
                                ) { Text(stringResource(Res.string.qa_public), style = MaterialTheme.typography.labelSmall) }
                            }
                            OutlinedButton(
                                onClick = onStopTunnel,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(stringResource(Res.string.qa_disable_public_access), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        is TunnelStatus.Error -> {
                            Text(
                                tunnelStatus.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            OutlinedButton(
                                onClick = onStartTunnel,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Text(stringResource(Res.string.qa_retry), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Password
                    OutlinedTextField(
                        value = appSettings.presentationRemoteSettings.remotePassword,
                        onValueChange = { pw ->
                            onSettingsChange { s -> s.copy(presentationRemoteSettings = s.presentationRemoteSettings.copy(remotePassword = pw)) }
                        },
                        label = { Text(stringResource(Res.string.presentation_remote_password_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SlideThumbnail(
    slide: ImageBitmap,
    slideNumber: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit = {}
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onDoubleClick = onDoubleClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = slide,
                contentDescription = stringResource(Res.string.slide_number, slideNumber),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = stringResource(Res.string.slide_number, slideNumber),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                maxLines = 1
            )
        }
    }
}
