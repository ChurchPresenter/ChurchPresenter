package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.type
import org.churchpresenter.app.churchpresenter.data.RecentPresentationFiles
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.app.churchpresenter.utils.ShortcutMap
import org.churchpresenter.app.churchpresenter.viewmodel.PresentationViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import java.awt.Window as AwtWindow
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.server.TunnelStatus
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.CoroutineScope
import org.churchpresenter.app.churchpresenter.composables.FocusLostRescueState
import org.churchpresenter.app.churchpresenter.PresentationSlidesLoaded

/**
 * Everything the Presentation tab's pieces read, for one composition: its parameters, the focus
 * and remote-dialog state it remembers, and its step-aware navigation.
 */
@Suppress("LongParameterList")
internal class PresentationTabScope(
    val hostWindow: AwtWindow?,
    val appSettings: AppSettings,
    val onAddToSchedule: ((filePath: String, fileName: String, slideCount: Int, fileType: String) -> Unit)?,
    val onSavePreset: ((filePath: String, fileName: String, slideCount: Int, fileType: String) -> Unit)?,
    val onInstanceLinkSendProject: ((ScheduleItem) -> Unit)?,
    val onInstanceLinkSendNextSlide: (() -> Unit)?,
    val onInstanceLinkSendPreviousSlide: (() -> Unit)?,
    val instanceLinkFetchPresentationSlideBytes: (suspend (id: String, index: Int) -> ByteArray?)?,
    val selectedPresentationItem: ScheduleItem.PresentationItem?,
    val selectedPresentationItemVersion: Int,
    val presenterManager: PresenterManager?,
    val onSlidesLoaded: PresentationSlidesLoaded?,
    val onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    val tunnelStatus: TunnelStatus,
    val tunnelUrl: String,
    val serverUrl: String,
    val presentationDisplayUrl: String,
    val onPresentationDisplayUrlChanged: (String) -> Unit,
    val onStartTunnel: () -> Unit,
    val onStopTunnel: () -> Unit,
    val presentationFrozen: Boolean,
    val onFreezeToggle: () -> Unit,
    val onClearPresentation: () -> Unit,
    val vlcAvailable: Boolean,
    val vlcArchMismatch: Boolean,
    val vlcLoadFailed: Boolean,
    val scope: CoroutineScope,
    val focusRequester: FocusRequester,
    val focusRescue: FocusLostRescueState,
    val shortcuts: ShortcutMap,
    val wentLive: (ScheduleItem) -> Unit,
    val presentationFileDialogTitle: String,
    showRemoteDialogState: MutableState<Boolean>,
) {
    var showRemoteDialog by showRemoteDialogState

    // Step-aware navigation: while the live output is showing exactly the selected slide of the
    // selected deck, next/prev first advances/rewinds its build steps (PowerPoint click
    // semantics). The identity/visibility guard lives in PresenterManager — in every other
    // situation (not live, cleared display, different deck/slide) arrows change slides.
    fun goNext(viewModel: PresentationViewModel) {
        val deck = viewModel.deck
        val stepped = deck != null && presenterManager
            ?.advancePresentationStep(deck, viewModel.selectedSlideIndex) == true
        if (!stepped) viewModel.nextSlide(onInstanceLinkSendNextSlide)
    }
    fun goPrevious(viewModel: PresentationViewModel) {
        val deck = viewModel.deck
        val stepped = deck != null && presenterManager
            ?.rewindPresentationStep(deck, viewModel.selectedSlideIndex) == true
        if (!stepped) viewModel.previousSlide(onInstanceLinkSendPreviousSlide)
    }

    /** The tab's key handler: slides, play/pause and blank, and Instance Link next/previous. */
    fun handleKey(viewModel: PresentationViewModel, keyEvent: KeyEvent): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false
        if (viewModel.slideFiles.isEmpty()) {
            // Instance Link Controller mode: next/prev must still reach the primary's own
            // live presentation even though this Controller's own slide list is empty — the
            // normal case, since Controller mode doesn't mirror the primary's content.
            val hasInstanceLinkNav = onInstanceLinkSendNextSlide != null || onInstanceLinkSendPreviousSlide != null
            return if (hasInstanceLinkNav) {
                when {
                    goesBack(shortcuts, keyEvent) -> { viewModel.previousSlide(onInstanceLinkSendPreviousSlide); true }
                    goesForward(shortcuts, keyEvent) -> { viewModel.nextSlide(onInstanceLinkSendNextSlide); true }
                    else -> false
                }
            } else false
        }
        return when {
            goesBack(shortcuts, keyEvent) -> { goPrevious(viewModel); true }
            goesForward(shortcuts, keyEvent) -> { goNext(viewModel); true }
            shortcuts.matches(ShortcutAction.PRESENTATION_PLAY_PAUSE, keyEvent) -> { viewModel.togglePlayPause(); true }
            // Clicker blank-screen button ('b' or '.' depending on model): toggle the
            // same Blank Output state as the eye button — a truly blank output
            // (PowerPoint's own 'B'), NOT Clear Display, which shows the configured
            // background instead.
            shortcuts.matches(ShortcutAction.PRESENTATION_BLANK, keyEvent) -> {
                if (presenterManager?.presentingMode?.value == Presenting.PRESENTATION) {
                    onFreezeToggle()
                    true
                } else false
            }
            else -> false
        }
    }
}

/** Loads what the Schedule hands the tab, publishes what it loads, and keeps the live slide in step. */
@Composable
internal fun PresentationTabScope.PresentationLoadEffects(viewModel: PresentationViewModel) {
    LaunchedEffect(selectedPresentationItem, selectedPresentationItemVersion) {
        selectedPresentationItem?.let { item ->
            val file = File(item.filePath)
            if (file.exists()) {
                viewModel.loadPresentationByPath(item.filePath)
                focusRequester.requestFocus()
            } else if (instanceLinkFetchPresentationSlideBytes != null) {
                // A mirrored schedule item's local path only exists on the primary's disk (e.g. a
                // network drive mounted differently, or not mounted at all, here) — fetch bytes over
                // Instance Link instead, same reasoning as MediaTab's instanceLinkMediaStreamUrl.
                // item.id is the schedule UUID the primary already maps to its own file-hash id
                // (CompanionServer._scheduleItemToPresentationId), populated when this exact item was
                // added to the primary's schedule — which is how it got mirrored to us in the first place.
                viewModel.loadPresentationFromRemote(
                    scheduleItemId = item.id,
                    filePath = item.filePath,
                    slideCount = item.slideCount,
                    fetchBytes = { index -> instanceLinkFetchPresentationSlideBytes(item.id, index) }
                )
                focusRequester.requestFocus()
            }
        }
    }


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
                onSlidesLoaded?.invoke(
                    id,
                    f.absolutePath,
                    f.nameWithoutExtension,
                    f.extension.lowercase(),
                    viewModel.slideFiles.toList(),
                    viewModel.slideNotes.toList(),
                )
            }
            // Arrow-key navigation needs the tab to hold keyboard focus. Previously only the
            // schedule-item path requested it, so decks opened via the file dialog or the
            // recents bar had dead arrow keys until the user clicked into a focusable control.
            focusRequester.requestFocus()
        }
    }
}

@Composable
internal fun PresentationTabScope.PresentationLiveEffects(viewModel: PresentationViewModel) {
    LaunchedEffect(viewModel.isPlaying, viewModel.selectedSlideIndex, viewModel.autoScrollInterval) {
        if (viewModel.isPlaying && viewModel.slideFiles.isNotEmpty()) {
            delay((viewModel.autoScrollInterval * PRESENTATION_MILLIS_PER_SECOND).toLong())
            // Auto-play steps through builds too: only when the live slide has no build step
            // left does the interval move to the next slide (same identity guard as goNext).
            val deck = viewModel.deck
            val liveStepAdvanced = deck != null && presenterManager
                ?.advancePresentationStep(deck, viewModel.selectedSlideIndex) == true
            if (!liveStepAdvanced) viewModel.nextSlide()
        }
    }

    LaunchedEffect(viewModel.selectedSlideIndex, viewModel.slideFiles.size) {
        val mode = presenterManager?.presentingMode?.value
        val idx = viewModel.selectedSlideIndex
        val enterAtLastStep = viewModel.consumeEnteredViaPreviousSlide()
        val anyScreenOnPresentation = mode == Presenting.PRESENTATION ||
            presenterManager?.screenLocks?.value?.values?.any { it == Presenting.PRESENTATION } == true
        if (anyScreenOnPresentation && viewModel.slideFiles.isNotEmpty()) {
            val bitmap = viewModel.slideFiles.getOrNull(idx)?.let { f ->
                withContext(Dispatchers.IO) {
                    try {
                        org.jetbrains.skia.Image.makeFromEncoded(f.readBytes()).toComposeImageBitmap()
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            val nextFile = viewModel.nextShownSlideIndex(idx)?.let { viewModel.slideFiles.getOrNull(it) }
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
            // Animated playback: point the player at the new slide (no-op → static path
            // when the slide has no timeline or the deck is remote/unparsed).
            viewModel.deck?.let { presenterManager.presentationShowSlide(it, idx, enterAtLastStep) }
                ?: presenterManager.clearPresentationPlayback()
        }
    }

    // Hiding or showing a slide can change which one comes next, so the stage monitor's "next"
    // is refreshed -- and only that. Re-pushing the live slide here restarted its animation.
    LaunchedEffect(viewModel.hiddenSlides) {
        val onPresentation = presenterManager?.presentingMode?.value == Presenting.PRESENTATION ||
            presenterManager?.screenLocks?.value?.values?.any { it == Presenting.PRESENTATION } == true
        if (!onPresentation) return@LaunchedEffect
        val nextFile = viewModel.nextShownSlideIndex()?.let { viewModel.slideFiles.getOrNull(it) }
        val nextBitmap = nextFile?.let { f ->
            withContext(Dispatchers.IO) {
                try {
                    org.jetbrains.skia.Image.makeFromEncoded(f.readBytes()).toComposeImageBitmap()
                } catch (_: Exception) {
                    null
                }
            }
        }
        presenterManager.setNextSlide(nextBitmap)
    }

    LaunchedEffect(viewModel.animationType, viewModel.transitionDuration) {
        presenterManager?.setAnimationType(viewModel.animationType)
        presenterManager?.setTransitionDuration(viewModel.transitionDuration.toInt())
    }
}
