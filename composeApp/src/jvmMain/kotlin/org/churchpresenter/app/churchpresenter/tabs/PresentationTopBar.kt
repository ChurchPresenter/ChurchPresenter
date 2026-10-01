package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.app.churchpresenter.viewmodel.PresentationViewModel
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.Surface
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.add_to_schedule
import org.churchpresenter.strings.generated.resources.save_preset
import org.churchpresenter.icons.generated.resources.ic_folder
import org.churchpresenter.icons.generated.resources.ic_stop
import org.churchpresenter.strings.generated.resources.tooltip_presentation_remote
import org.churchpresenter.strings.generated.resources.clear
import org.churchpresenter.strings.generated.resources.clear_recents
import org.churchpresenter.strings.generated.resources.go_live
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.strings.generated.resources.no_file_selected_presentation
import org.churchpresenter.strings.generated.resources.presentation_clear
import org.churchpresenter.strings.generated.resources.presentation_freeze_output
import org.churchpresenter.strings.generated.resources.presentation_unfreeze_output
import org.churchpresenter.strings.generated.resources.recent
import org.churchpresenter.strings.generated.resources.select_presentation_file_button
import org.churchpresenter.strings.generated.resources.loading_slides_progress
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import org.churchpresenter.sharedui.composables.ActionIconButton
import org.churchpresenter.sharedui.composables.AddToScheduleButton
import org.churchpresenter.sharedui.composables.SavePresetButton
import org.churchpresenter.sharedui.composables.GoLiveButton
import org.churchpresenter.app.churchpresenter.data.RecentPresentationFiles
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.sharedui.composables.RecentChip

/* The Presentation tab's top bar: the file bar, the recent files, and the playback controls. */

@Composable
internal fun PresentationTabScope.PresentationTopBar(viewModel: PresentationViewModel) {
    Column(modifier = Modifier.fillMaxWidth().topBarCard()) {
        PresentationFileBar(viewModel)
        PresentationRecentFiles(viewModel)
        PresentationControlsBar(viewModel)
    }
}

/** Open, the file's name and loading progress, remote, blank, clear, Save preset, Add to schedule and Go live. */
@Composable
private fun PresentationTabScope.PresentationFileBar(viewModel: PresentationViewModel) {
    // ── File bar ──────────────────────────────────────────────────
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OpenPresentationButton(viewModel)
        Text(
            text = viewModel.selectedPresentationDisplayName
                ?: stringResource(Res.string.no_file_selected_presentation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (viewModel.isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        if (viewModel.isLoading && viewModel.totalSlides > 0) {
            Text(
                text = stringResource(
                    Res.string.loading_slides_progress,
                    viewModel.slideFiles.size,
                    viewModel.totalSlides,
                ),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        PresentationOutputButtons(viewModel)
        if (onSavePreset != null) {
            SavePresetButton(
                onClick = {
                    val f = viewModel.selectedPresentation ?: return@SavePresetButton
                    onSavePreset(
                        f.absolutePath,
                        f.nameWithoutExtension,
                        viewModel.slideFiles.size,
                        f.extension.lowercase(),
                    )
                },
                enabled = viewModel.selectedPresentation != null,
                tooltipText = stringResource(Res.string.save_preset)
            )
        }
        if (onAddToSchedule != null) {
            AddToScheduleButton(
                onClick = {
                    val f = viewModel.selectedPresentation ?: return@AddToScheduleButton
                    onAddToSchedule(
                        f.absolutePath,
                        f.nameWithoutExtension,
                        viewModel.slideFiles.size,
                        f.extension.lowercase(),
                    )
                },
                enabled = viewModel.selectedPresentation != null,
                tooltipText = stringResource(Res.string.add_to_schedule)
            )
        }
        if (presenterManager != null) {
            PresentationGoLiveButton(viewModel, presenterManager)
        }
    }
}

@Composable
private fun PresentationTabScope.OpenPresentationButton(viewModel: PresentationViewModel) {
    RaisedButton(
        onClick = {
            scope.launch {
                val allFilter =
                    FileNameExtensionFilter("All Presentation Files", "ppt", "pptx", "key", "pdf")
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
        shape = AppShape(7.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
    ) {
        Icon(painterResource(IconRes.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            stringResource(Res.string.select_presentation_file_button),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
private fun PresentationTabScope.PresentationGoLiveButton(
    viewModel: PresentationViewModel,
    presenterManager: PresenterManager,
) {
    GoLiveButton(
        onClick = {
            val idx = viewModel.selectedSlideIndex
            scope.launch {
                val bitmap = viewModel.slideFiles.getOrNull(idx)?.let { f ->
                    withContext(Dispatchers.IO) {
                        try {
                            org.jetbrains.skia.Image.makeFromEncoded(f.readBytes()).toComposeImageBitmap()
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                val nextFile = viewModel.nextShownSlideIndex(idx)
                    ?.let { viewModel.slideFiles.getOrNull(it) }
                val nextBitmap = nextFile?.let { f ->
                    withContext(Dispatchers.IO) {
                        try {
                            org.jetbrains.skia.Image.makeFromEncoded(f.readBytes()).toComposeImageBitmap()
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                presenterManager.setSelectedSlide(bitmap)
                presenterManager.setLiveSlide(viewModel.selectedPresentation?.name, idx)
                presenterManager.setNextSlide(nextBitmap)
                presenterManager.setPresenterNotes(viewModel.slideNotes.getOrElse(idx) { "" })
            }
            presenterManager.setPresentingMode(Presenting.PRESENTATION)
            viewModel.deck?.let { presenterManager.presentationShowSlide(it, idx) }
            presenterManager.setShowPresenterWindow(true)
            viewModel.selectedPresentation?.let { f ->
                wentLive(presentationRow(f, viewModel.slideFiles.size))
            }
            viewModel.selectedPresentation?.let { f ->
                onInstanceLinkSendProject?.invoke(
                    ScheduleItem.PresentationItem(
                        id = java.util.UUID.randomUUID().toString(),
                        filePath = f.absolutePath,
                        fileName = f.nameWithoutExtension,
                        slideCount = viewModel.slideFiles.size,
                        fileType = f.extension.lowercase()
                    )
                )
            }
        },
        enabled = viewModel.slideFiles.isNotEmpty(),
        tooltipText = stringResource(Res.string.go_live)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresentationTabScope.PresentationRecentFiles(viewModel: PresentationViewModel) {
    // ── Recent files bar ──────────────────────────────────────────
    val recentOrdered =
        RecentPresentationFiles.pinned + RecentPresentationFiles.files.filter { it !in RecentPresentationFiles.pinned }
    if (recentOrdered.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
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
                KeyIconButton(onClick = { RecentPresentationFiles.clear() }, modifier = Modifier.size(20.dp)) {
                    Icon(
                        painterResource(IconRes.drawable.ic_close),
                        contentDescription = stringResource(Res.string.clear),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                lazyItems(recentOrdered) { path ->
                    val isPinned = path in RecentPresentationFiles.pinned
                    val isActive = viewModel.selectedPresentationDisplayPath == path
                    RecentChip(
                        name = File(path).name,
                        isActive = isActive,
                        isPinned = isPinned,
                        onOpen = {
                            val f = File(path)
                            if (f.exists()) {
                                viewModel.addPresentation(f)
                                RecentPresentationFiles.add(path)
                            }
                        },
                        onTogglePin = { RecentPresentationFiles.togglePin(path) },
                    )
                }
            }
        }
    }
}

/** Remote, blank and clear. */
@Composable
private fun PresentationTabScope.PresentationOutputButtons(viewModel: PresentationViewModel) {
    ActionIconButton(
        onClick = { showRemoteDialog = true },
        tooltipText = stringResource(Res.string.tooltip_presentation_remote),
        icon = Icons.Default.SettingsRemote,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    )
    if (presenterManager != null) {
        ActionIconButton(
            onClick = onFreezeToggle,
            enabled = viewModel.slideFiles.isNotEmpty(),
            tooltipText = stringResource(
                if (presentationFrozen) {
                    Res.string.presentation_unfreeze_output
                } else {
                    Res.string.presentation_freeze_output
                }
            ),
            icon = if (presentationFrozen) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            containerColor = if (presentationFrozen) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            contentColor = if (presentationFrozen) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            }
        )
    }
    ActionIconButton(
        onClick = { viewModel.clearPresentations(); onClearPresentation() },
        enabled = viewModel.slideFiles.isNotEmpty(),
        tooltipText = stringResource(Res.string.presentation_clear),
        painter = painterResource(IconRes.drawable.ic_stop),
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    )
}
