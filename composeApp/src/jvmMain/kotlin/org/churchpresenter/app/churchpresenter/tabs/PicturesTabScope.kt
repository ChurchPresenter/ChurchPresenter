package org.churchpresenter.app.churchpresenter.tabs

import java.io.File
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Stable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.app.churchpresenter.viewmodel.PicturesViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.utils.ShortcutMap
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.input.key.KeyEvent

/** Everything the Pictures tab's pieces read, for one composition, and its key handler. */
@Suppress("LongParameterList")
internal class PicturesTabScope(
    val appSettings: AppSettings?,
    val onAddToSchedule: ((folderPath: String, folderName: String, imageCount: Int) -> Unit)?,
    val onSavePreset: ((folderPath: String, folderName: String, imageCount: Int) -> Unit)?,
    val onInstanceLinkSendProject: ((ScheduleItem) -> Unit)?,
    val onInstanceLinkSendNextPicture: (() -> Unit)?,
    val onInstanceLinkSendPreviousPicture: (() -> Unit)?,
    val presenterManager: PresenterManager?,
    val onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    val viewModel: PicturesViewModel,
    val folderDialogTitle: String,
    val gridState: LazyGridState,
    val shortcuts: ShortcutMap,
    val wentLive: (ScheduleItem) -> Unit,
) {
    /** Previous/next, row up/down and play/pause; Instance Link next/previous with an empty folder. */
    fun handleKey(keyEvent: KeyEvent): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false
        if (viewModel.images.isEmpty()) {
            // Instance Link Controller mode: next/prev must still reach the primary's own
            // live folder even though this Controller's own list is empty — the normal case,
            // since Controller mode doesn't mirror the primary's content.
            val hasInstanceLinkNav = onInstanceLinkSendNextPicture != null || onInstanceLinkSendPreviousPicture != null
            return if (hasInstanceLinkNav) {
                when {
                    shortcuts.matches(ShortcutAction.PICTURES_PREVIOUS, keyEvent) -> {
                        viewModel.previousImage(onInstanceLinkSendPreviousPicture)
                        true
                    }
                    shortcuts.matches(ShortcutAction.PICTURES_NEXT, keyEvent) -> {
                        viewModel.nextImage(onInstanceLinkSendNextPicture)
                        true
                    }
                    else -> false
                }
            } else false
        }
        val columnCount = (gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.column } ?: 0) + 1
        return when {
            shortcuts.matches(ShortcutAction.PICTURES_PREVIOUS, keyEvent) -> {
                viewModel.previousImage(onInstanceLinkSendPreviousPicture)
                true
            }
            shortcuts.matches(ShortcutAction.PICTURES_NEXT, keyEvent) -> {
                viewModel.nextImage(onInstanceLinkSendNextPicture)
                true
            }
            shortcuts.matches(ShortcutAction.PICTURES_ROW_UP, keyEvent) -> {
                val target = viewModel.selectedImageIndex - columnCount
                if (target >= 0) viewModel.selectImage(target)
                true
            }
            shortcuts.matches(ShortcutAction.PICTURES_ROW_DOWN, keyEvent) -> {
                val target = viewModel.selectedImageIndex + columnCount
                if (target < viewModel.images.size) viewModel.selectImage(target)
                true
            }
            shortcuts.matches(ShortcutAction.PICTURES_PLAY_PAUSE, keyEvent) -> { viewModel.togglePlayPause(); true }
            else -> false
        }
    }
}

/** Shift+click+drag reorder state: a ghost follows the cursor, with no real-time swaps. */
@Stable
internal class PictureDragState {
    var draggingFile by mutableStateOf<File?>(null)
    var draggingFromIndex by mutableStateOf(-1)
    var dropTargetIndex by mutableStateOf<Int?>(null)
    var isDragActive by mutableStateOf(false)
    var dragCursorInGrid by mutableStateOf(Offset.Zero)
}
