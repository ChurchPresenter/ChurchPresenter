package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.app.churchpresenter.viewmodel.PresentationViewModel
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import org.churchpresenter.theme.AppShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.strings.generated.resources.loading_slides
import org.churchpresenter.strings.generated.resources.remove
import org.churchpresenter.strings.generated.resources.select_presentation_file
import org.churchpresenter.strings.generated.resources.presentation_focus_lost
import org.churchpresenter.strings.generated.resources.media_vlc_install
import org.churchpresenter.strings.generated.resources.media_vlc_arch_mismatch
import org.churchpresenter.strings.generated.resources.media_vlc_load_failed
import org.churchpresenter.strings.generated.resources.presentation_static_note
import org.churchpresenter.strings.generated.resources.presentation_error_password_protected
import org.churchpresenter.strings.generated.resources.presentation_error_empty_document
import org.churchpresenter.strings.generated.resources.presentation_error_library_missing
import org.churchpresenter.strings.generated.resources.presentation_error_render_failed
import org.churchpresenter.strings.generated.resources.supported_formats
import org.churchpresenter.app.churchpresenter.composables.FocusLostBanner
import org.churchpresenter.presentationengine.model.LayerSpec
import org.churchpresenter.app.churchpresenter.data.RecentPresentationFiles
import org.churchpresenter.core.models.presentation.PresentationLoadError
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.raisedHover

/* The Presentation tab's body: the slide grid, or the loading, error and empty states, and the open files. */

@Composable
internal fun PresentationTabScope.PresentationBody(viewModel: PresentationViewModel) {
    // ── Slide content + right sidebar ────────────────────────────
    Row(modifier = Modifier.fillMaxSize()) {
        // ── Left: slide grid / states ────────────────────────────
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (viewModel.slideFiles.isNotEmpty()) {
                PresentationSlideGrid(viewModel, Modifier.weight(1f).fillMaxWidth())
            } else if (viewModel.selectedPresentation != null) {
                PresentationLoadingOrError(viewModel, Modifier.weight(1f).fillMaxWidth())
            } else {
                PresentationEmptyState(Modifier.weight(1f).fillMaxWidth())
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
                        val palette = elevationPalette()
                        val openFill = if (viewModel.selectedPresentation == f) palette.selected else palette.key
                        Row(
                            modifier = Modifier
                                .raisedHover(AppShape(8.dp), openFill, palette, lift = 2.dp)
                                .clickable { viewModel.selectPresentation(f) }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                f.nameWithoutExtension,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = openFill.ink,
                                maxLines = 1
                            )
                            KeyIconButton(onClick = {
                                val inRecents = f.absolutePath in RecentPresentationFiles.files
                                val inPinned = f.absolutePath in RecentPresentationFiles.pinned
                                viewModel.removePresentation(f, isInRecentsOrPinned = inRecents || inPinned)
                            }, modifier = Modifier.size(16.dp)) {
                                Icon(
                                    painterResource(IconRes.drawable.ic_close),
                                    contentDescription = stringResource(Res.string.remove),
                                    modifier = Modifier.size(10.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresentationTabScope.PresentationSlideGrid(viewModel: PresentationViewModel, gridModifier: Modifier) {
    // Embedded video degrades gracefully with no VLC (the slide just shows its
    // static poster forever, see EmbeddedVideoDecoder.start()) — this banner only
    // tells the operator why, so it's not a silent surprise during a live service.
    val deckHasVideo = viewModel.deck?.slides
        ?.any { slide -> slide.layers.any { it is LayerSpec.Media } } == true
    var vlcBannerDismissed by remember(viewModel.loadGeneration) { mutableStateOf(false) }
    if (deckHasVideo && !vlcAvailable && !vlcBannerDismissed) {
        VlcMissingBanner(
            detail = when {
                vlcArchMismatch -> stringResource(Res.string.media_vlc_arch_mismatch)
                vlcLoadFailed -> stringResource(Res.string.media_vlc_load_failed)
                else -> stringResource(Res.string.media_vlc_install)
            },
            onDismiss = { vlcBannerDismissed = true }
        )
    }
    // Focus-lost rescue: arrow keys and clicker keys only reach this tab's
    // key handler while something inside the tab holds keyboard focus AND the
    // window itself is focused. One click on the banner brings both back.
    FocusLostBanner(focusRescue, stringResource(Res.string.presentation_focus_lost))
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 200.dp),
        modifier = gridModifier
            .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
            .bibleListCard()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 18.dp)
    ) {
        itemsIndexed(viewModel.slideFiles) { index, slideFile ->
            PresentationSlideCell(viewModel, index, slideFile)
        }
    }
}

@Composable
private fun PresentationTabScope.PresentationSlideCell(viewModel: PresentationViewModel, index: Int, slideFile: File) {
    // Decode off the composition thread — big decks scrolled fast used
    // to jank the whole UI decoding full-res JPEGs during layout.
    // slideFile can be deleted out from under this (removePresentation
    // invalidates the shared disk cache synchronously, before slideFiles
    // is cleared), so a missing/corrupt file just stays a blank thumbnail
    // instead of crashing the grid.
    val bitmap by produceState<ImageBitmap?>(initialValue = null, slideFile) {
        value = withContext(Dispatchers.IO) {
            try {
                org.jetbrains.skia.Image.makeFromEncoded(slideFile.readBytes()).toComposeImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
    SlideThumbnail(
        slide = bitmap,
        slideNumber = index + 1,
        buildCount = viewModel.deck?.slides?.getOrNull(index)?.timeline?.stepCount ?: 0,
        isSelected = viewModel.selectedSlideIndex == index,
        isHidden = index in viewModel.hiddenSlides,
        onToggleHidden = { viewModel.toggleSlideHidden(index) },
        onClick = {
            viewModel.selectSlide(index)
            // Keep arrow keys working after a mouse selection.
            focusRequester.requestFocus()
        },
        onDoubleClick = {
            viewModel.selectSlide(index)
            if (presenterManager != null) {
                goLiveAtSlide(viewModel, presenterManager, index)
            }
        }
    )
}

/** Puts slide [index] live from a double-click on its thumbnail. */
private fun PresentationTabScope.goLiveAtSlide(
    viewModel: PresentationViewModel,
    presenterManager: PresenterManager,
    index: Int,
) {
    scope.launch {
        val cur = viewModel.slideFiles.getOrNull(index)?.let { f ->
            withContext(Dispatchers.IO) {
                try {
                    org.jetbrains.skia.Image.makeFromEncoded(f.readBytes()).toComposeImageBitmap()
                } catch (_: Exception) {
                    null
                }
            }
        }
        val nextFile = viewModel.nextShownSlideIndex(index)
            ?.let { viewModel.slideFiles.getOrNull(it) }
        val next = nextFile?.let { f ->
            withContext(Dispatchers.IO) {
                try {
                    org.jetbrains.skia.Image.makeFromEncoded(f.readBytes()).toComposeImageBitmap()
                } catch (_: Exception) {
                    null
                }
            }
        }
        presenterManager.setSelectedSlide(cur)
        presenterManager.setLiveSlide(viewModel.selectedPresentation?.name, index)
        presenterManager.setNextSlide(next)
        presenterManager.setPresenterNotes(viewModel.slideNotes.getOrElse(index) { "" })
    }
    presenterManager.setPresentingMode(Presenting.PRESENTATION)
    viewModel.deck?.let { presenterManager.presentationShowSlide(it, index) }
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
}

@Composable
private fun PresentationTabScope.PresentationLoadingOrError(viewModel: PresentationViewModel, modifier: Modifier) {
    val currentLoadError = viewModel.loadError
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (!viewModel.isLoading && currentLoadError != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(
                        when (currentLoadError) {
                            PresentationLoadError.PASSWORD_PROTECTED -> Res.string.presentation_error_password_protected
                            PresentationLoadError.EMPTY_DOCUMENT -> Res.string.presentation_error_empty_document
                            PresentationLoadError.LIBRARY_MISSING -> Res.string.presentation_error_library_missing
                            PresentationLoadError.RENDER_FAILED -> Res.string.presentation_error_render_failed
                        }
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Text(
                    stringResource(Res.string.loading_slides),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun PresentationEmptyState(modifier: Modifier) {
    Box(
        modifier = modifier
            .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
            .bibleListCard(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.select_presentation_file),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Text(
                stringResource(Res.string.supported_formats),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
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
