package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.alpha
import org.churchpresenter.app.churchpresenter.composables.FocusLostBanner
import org.churchpresenter.app.churchpresenter.LocalWentLive
import org.churchpresenter.app.churchpresenter.composables.focusRescuePressHook
import org.churchpresenter.app.churchpresenter.composables.rememberFocusLostRescue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import churchpresenter.composeapp.generated.resources.tab_focus_lost
import churchpresenter.composeapp.generated.resources.select_folder_to_view
import churchpresenter.composeapp.generated.resources.select_image_folder_dialog
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.utils.LocalShortcuts
import org.churchpresenter.app.churchpresenter.viewmodel.PicturesViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.stringResource
import java.awt.Window as AwtWindow
import java.io.File
import kotlinx.coroutines.delay

internal const val PICTURES_MILLIS_PER_SECOND = 1000
internal const val PICTURES_CAPTION_FONT_SP = 12.5f
internal const val PICTURES_SMALL_LABEL_FONT_SP = 11.5f
internal const val PICTURES_MAX_AUTO_SCROLL_SECONDS = 30
internal val PICTURES_RECENT_BAR_HEIGHT = 40.dp
internal val PICTURES_TRANSPORT_KEY_SIZE = 30.dp
internal val PICTURES_PLAY_KEY_SIZE = 38.dp
internal val PICTURES_LOOP_KEY_SIZE = 28.dp
internal val PICTURES_SETTING_BOX_WIDTH = 150.dp
internal const val PICTURES_MIN_TRANSITION_MS = 100
internal const val PICTURES_MAX_TRANSITION_MS = 2000
internal const val PICTURES_DRAGGED_ITEM_ALPHA = 0.35f
internal const val PICTURES_DRAGGED_ITEM_Z_INDEX = 10f
internal const val PICTURES_DRAGGED_ITEM_SCALE = 1.08f
internal const val PICTURES_DRAGGED_ITEM_ELEVATION = 16f

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun PicturesTab(
    modifier: Modifier = Modifier,
    /** The hosting AWT window — used by the focus-lost rescue to heal AWT focus (see
     *  composables/FocusLostRescue.kt). */
    hostWindow: AwtWindow? = null,
    appSettings: AppSettings? = null,
    onAddToSchedule: ((folderPath: String, folderName: String, imageCount: Int) -> Unit)? = null,
    /** Save preset, to the left of Add to Schedule: the same folder, kept for the Calendar Manager. */
    onSavePreset: ((folderPath: String, folderName: String, imageCount: Int) -> Unit)? = null,
    /** Instance Link Controller mode — non-null only when connected and controlling. See
     *  PicturesViewModel.goLive for why this always sends the whole folder via PROJECT. */
    onInstanceLinkSendProject: ((ScheduleItem) -> Unit)? = null,
    /** Instance Link Controller mode — advance/retreat whatever the primary currently has live, no
     *  id needed. Non-null only when connected and controlling. */
    onInstanceLinkSendNextPicture: (() -> Unit)? = null,
    onInstanceLinkSendPreviousPicture: (() -> Unit)? = null,
    /** Fetches one image's raw bytes from the Instance Link primary by folder hash + index — non-null
     *  only while connected. Used when a mirrored schedule item's folderPath doesn't resolve on this
     *  machine (e.g. a network drive mounted differently, or not mounted at all, here). */
    instanceLinkFetchPictureImageBytes: (suspend (folderId: String, index: Int) -> ByteArray?)? = null,
    selectedPictureItem: ScheduleItem.PictureItem? = null,
    /**
     * Bumped by the caller on every schedule click, so clicking the *same* item twice re-runs the
     * effect below. Keyed on the item alone, an unchanged item is an unchanged key and the second
     * click does nothing.
     */
    selectedPictureItemVersion: Int = 0,
    presenterManager: PresenterManager? = null,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    viewModel: PicturesViewModel = remember { PicturesViewModel(appSettings) }
) {
    val folderDialogTitle = stringResource(Res.string.select_image_folder_dialog)
    val focusRequester = remember { FocusRequester() }
    PicturesEffects(
        viewModel = viewModel,
        focusRequester = focusRequester,
        selectedPictureItem = selectedPictureItem,
        selectedPictureItemVersion = selectedPictureItemVersion,
        instanceLinkFetchPictureImageBytes = instanceLinkFetchPictureImageBytes,
        presenterManager = presenterManager,
    )

    // Hoisted so onPreviewKeyEvent can read column count for row-based Up/Down navigation
    val gridState = rememberLazyGridState()

    // Focus-lost rescue: arrow-key image navigation only works while the tab holds keyboard
    // focus AND the window is focused — full machinery in composables/FocusLostRescue.kt
    // (shared with Presentation/Bible/Songs).
    val focusRescue = rememberFocusLostRescue(hostWindow, focusRequester)
    val shortcuts = LocalShortcuts.current
    val wentLive = LocalWentLive.current
    // Remembered, keyed on everything it holds: a new scope on every recomposition would hand the
    // pieces new lambdas each time, and a click handler keyed on its lambda would restart.
    val tabScope = remember(
        appSettings, onAddToSchedule, onSavePreset, onInstanceLinkSendProject, onInstanceLinkSendNextPicture,
        onInstanceLinkSendPreviousPicture, presenterManager, onSettingsChange, viewModel, folderDialogTitle, gridState,
        shortcuts, wentLive
    ) {
        PicturesTabScope(
            appSettings = appSettings,
            onAddToSchedule = onAddToSchedule,
            onSavePreset = onSavePreset,
            onInstanceLinkSendProject = onInstanceLinkSendProject,
            onInstanceLinkSendNextPicture = onInstanceLinkSendNextPicture,
            onInstanceLinkSendPreviousPicture = onInstanceLinkSendPreviousPicture,
            presenterManager = presenterManager,
            onSettingsChange = onSettingsChange,
            viewModel = viewModel,
            folderDialogTitle = folderDialogTitle,
            gridState = gridState,
            shortcuts = shortcuts,
            wentLive = wentLive,
        )
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onFocusChanged { focusRescue.onFocusChanged(it.hasFocus) }
            .focusRescuePressHook(focusRescue)
            .focusable()
            .onPreviewKeyEvent { keyEvent -> tabScope.handleKey(keyEvent) }
    ) {
        tabScope.PicturesHeader()
        FocusLostBanner(focusRescue, stringResource(Res.string.tab_focus_lost))

        // ── Thumbnail grid ────────────────────────────────────────────
        if (viewModel.images.isNotEmpty()) {
            tabScope.PicturesGrid()
        } else {
            PicturesEmptyState()
        }
    }
}

/** Auto-scroll, loading a scheduled folder, and keeping the presenter in step. */
@Composable
private fun PicturesEffects(
    viewModel: PicturesViewModel,
    focusRequester: FocusRequester,
    selectedPictureItem: ScheduleItem.PictureItem?,
    selectedPictureItemVersion: Int,
    instanceLinkFetchPictureImageBytes: (suspend (folderId: String, index: Int) -> ByteArray?)?,
    presenterManager: PresenterManager?,
) {
    // Auto-scroll effect
    LaunchedEffect(viewModel.isPlaying, viewModel.selectedImageIndex, viewModel.autoScrollInterval) {
        if (viewModel.isPlaying && viewModel.images.isNotEmpty()) {
            delay((viewModel.autoScrollInterval * PICTURES_MILLIS_PER_SECOND).toLong())
            viewModel.nextImage()
        }
    }

    // Load folder when a picture schedule item is selected
    LaunchedEffect(selectedPictureItem, selectedPictureItemVersion) {
        selectedPictureItem?.let { pictureItem ->
            val folder = File(pictureItem.folderPath)
            if (folder.exists() && folder.isDirectory) {
                viewModel.selectFolder(folder)
                // A single dropped picture opens on itself rather than on the folder's first (#652).
                viewModel.selectImagePath(pictureItem.imagePath)
                focusRequester.requestFocus()
            } else if (instanceLinkFetchPictureImageBytes != null) {
                // A mirrored schedule item's local path only exists on the primary's disk (e.g. a
                // network drive mounted differently, or not mounted at all, here) — fetch bytes over
                // Instance Link instead, same reasoning as MediaTab's instanceLinkMediaStreamUrl.
                val folderId = pictureItem.folderPath.hashCode().toUInt().toString(16)
                viewModel.loadPictureFromRemote(
                    folderId = folderId,
                    folderPath = pictureItem.folderPath,
                    imageCount = pictureItem.imageCount,
                    presenterManager = presenterManager,
                    fetchBytes = { index -> instanceLinkFetchPictureImageBytes(folderId, index) }
                )
                focusRequester.requestFocus()
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
}

@Composable
private fun PicturesEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize()
            .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
            .bibleListCard(),
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
