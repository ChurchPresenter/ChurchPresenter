package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.go_live
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.ic_pause
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
import kotlinx.coroutines.delay
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.PresentationViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File
import org.churchpresenter.app.churchpresenter.utils.createFileChooser
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun PresentationTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    onAddToSchedule: ((filePath: String, fileName: String, slideCount: Int, fileType: String) -> Unit)? = null,
    selectedPresentationItem: ScheduleItem.PresentationItem? = null,
    presenterManager: PresenterManager? = null
) {
    val viewModel = remember { PresentationViewModel(appSettings) }

    // React to schedule item selection
    LaunchedEffect(selectedPresentationItem) {
        selectedPresentationItem?.let { viewModel.loadPresentationByPath(it.filePath) }
    }
    val presentationFileDialogTitle = stringResource(Res.string.select_presentation_file)

    // Auto-play effect using media settings (same as PicturesTab)
    LaunchedEffect(viewModel.isPlaying, viewModel.selectedSlideIndex, viewModel.autoScrollInterval) {
        if (viewModel.isPlaying && viewModel.slides.isNotEmpty()) {
            delay((viewModel.autoScrollInterval * 1000).toLong())
            viewModel.nextSlide()
        }
    }

    // Push current slide to presenter whenever slide index changes (if presenting)
    LaunchedEffect(viewModel.selectedSlideIndex, viewModel.slides.size) {
        if (presenterManager?.presentingMode?.value == Presenting.PRESENTATION && viewModel.slides.isNotEmpty()) {
            val slide = viewModel.slides.getOrNull(viewModel.selectedSlideIndex)
            presenterManager.setSelectedSlide(slide)
        }
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
                    SwingUtilities.invokeLater {
                        val chooser = createFileChooser {
                            fileSelectionMode = JFileChooser.FILES_ONLY
                            dialogTitle = presentationFileDialogTitle
                            isMultiSelectionEnabled = true

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

                            addChoosableFileFilter(allFilter)
                            addChoosableFileFilter(pptFilter)
                            addChoosableFileFilter(keynoteFilter)
                            addChoosableFileFilter(pdfFilter)
                            fileFilter = allFilter
                        }
                        val result = chooser.showOpenDialog(null)
                        if (result == JFileChooser.APPROVE_OPTION) {
                            chooser.selectedFiles.forEach { file ->
                                viewModel.addPresentation(file)
                            }
                        }
                    }
                }
            ) {
                Text(stringResource(Res.string.select_presentation_file_button))
            }

            Text(
                text = viewModel.selectedPresentation?.name ?: stringResource(Res.string.no_file_selected_presentation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f)
            )

            Text(
                text = stringResource(Res.string.slide_count, viewModel.slides.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (viewModel.slides.isNotEmpty()) {
            // Playback Controls - Similar to PicturesTab
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: Playback buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.previousSlide() },
                        enabled = viewModel.slides.isNotEmpty()
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_skip_previous),
                            contentDescription = stringResource(Res.string.previous_image),
                            modifier = Modifier.size(32.dp),
                            tint = if (viewModel.slides.isNotEmpty()) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.togglePlayPause() },
                        enabled = viewModel.slides.isNotEmpty(),
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
                                if (viewModel.isPlaying) Res.drawable.ic_pause
                                else Res.drawable.ic_play
                            ),
                            contentDescription = stringResource(
                                if (viewModel.isPlaying) Res.string.pause
                                else Res.string.play
                            ),
                            modifier = Modifier.size(28.dp),
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = { viewModel.nextSlide() },
                        enabled = viewModel.slides.isNotEmpty()
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_skip_next),
                            contentDescription = stringResource(Res.string.next_image),
                            modifier = Modifier.size(32.dp),
                            tint = if (viewModel.slides.isNotEmpty()) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    Text(
                        text = stringResource(Res.string.slide_counter, viewModel.selectedSlideIndex + 1, viewModel.slides.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Right side: Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Go Live button
                    if (presenterManager != null) {
                        Button(
                            onClick = {
                                val slide = viewModel.slides.getOrNull(viewModel.selectedSlideIndex)
                                presenterManager.setSelectedSlide(slide)
                                presenterManager.setPresentingMode(Presenting.PRESENTATION)
                                presenterManager.setShowPresenterWindow(true)
                            },
                            enabled = viewModel.slides.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = stringResource(Res.string.go_live),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    // Add to Schedule button
                    if (onAddToSchedule != null && viewModel.selectedPresentation != null) {
                        Button(
                            onClick = {
                                val file = viewModel.selectedPresentation!!
                                onAddToSchedule(
                                    file.absolutePath,
                                    file.nameWithoutExtension,
                                    viewModel.slides.size,
                                    file.extension.lowercase()
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text(
                                text = stringResource(Res.string.add_to_schedule),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                        onClick = { viewModel.selectSlide(index) }
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "⏳",
                        style = MaterialTheme.typography.displayLarge
                    )
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
                    Text(
                        text = "📊",
                        style = MaterialTheme.typography.displayLarge
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
    slide: androidx.compose.ui.graphics.ImageBitmap,
    slideNumber: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
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


