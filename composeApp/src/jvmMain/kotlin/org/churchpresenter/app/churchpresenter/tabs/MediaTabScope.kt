package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import churchpresenter.composeapp.generated.resources.pause
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.server.followerMediaUrl
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import org.churchpresenter.app.churchpresenter.utils.ShortcutMap
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import androidx.compose.ui.input.key.KeyEvent

/** Which kind of source the tab is choosing, and the address typed for a network one. */
@Stable
internal class MediaTabState {
    var selectedSourceType by mutableStateOf(Constants.MEDIA_TYPE_LOCAL)
    var urlInput by mutableStateOf("")
}

/** Everything the Media tab's pieces read, for one composition, and its key handler. */
@Suppress("LongParameterList")
internal class MediaTabScope(
    val appSettings: AppSettings,
    val onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    val onAddToSchedule: ((mediaUrl: String, mediaTitle: String, mediaType: String, subtitleUrl: String) -> Unit)?,
    val onSavePreset: ((mediaUrl: String, mediaTitle: String, mediaType: String) -> Unit)?,
    val presenterManager: PresenterManager?,
    val onInstanceLinkSendProject: ((ScheduleItem) -> Unit)?,
    val viewModel: MediaViewModel,
    val state: MediaTabState,
    val scope: CoroutineScope,
    val sourceTypeItems: List<SegmentedButtonItem<String>>,
    val selectFileLabel: String,
    val mediaFilesLabel: String,
    val shortcuts: ShortcutMap,
    val wentLive: (ScheduleItem) -> Unit,
) {
    var selectedSourceType by state::selectedSourceType
    var urlInput by state::urlInput

    /** Clear Output, play/pause and mute. */
    fun handleKey(keyEvent: KeyEvent): Boolean {
        return if (keyEvent.type == KeyEventType.KeyDown) {
            when {
                // Clear Output is a global action, but it is claimed here too so the media
                // is paused before the display clears — the root handler pauses via a
                // nullable ViewModel reference that this tab already holds directly.
                shortcuts.matches(ShortcutAction.CLEAR_OUTPUT, keyEvent) && presenterManager != null -> {
                    viewModel.pause(); presenterManager.requestClearDisplay(); true
                }
                viewModel.isLoaded && shortcuts.matches(ShortcutAction.MEDIA_PLAY_PAUSE, keyEvent) -> {
                    viewModel.togglePlayPause()
                    true
                }
                viewModel.isLoaded && shortcuts.matches(ShortcutAction.MEDIA_MUTE, keyEvent) -> {
                    viewModel.toggleMute()
                    true
                }
                else -> false
            }
        } else false
    }
}

/** Loads what the Schedule hands the tab, blanking the output while a live file changes. */
@Composable
internal fun MediaTabScope.MediaTabEffects(
    selectedMediaItem: ScheduleItem.MediaItem?,
    selectedMediaItemVersion: Int,
    instanceLinkMediaStreamUrl: ((itemId: String) -> String)?,
    focusRequester: FocusRequester,
) {
    LaunchedEffect(selectedMediaItem, selectedMediaItemVersion) {
        selectedMediaItem?.let {
            // Blanked while the new file loads, so the output does not sit on the last frame of the
            // old one — and put back at the end of this block. Both halves matter: `setPresentingMode`
            // is the only thing that resets the clear flag, and it is not called again here because
            // the mode is already MEDIA. Presenting a media row from the schedule sets the mode in
            // the same handler that sets the item, so this effect always found itself "already
            // live", always blanked, and nothing ever turned it back on. (#602)
            val wasLive = presenterManager?.presentingMode?.value == Presenting.MEDIA
            if (wasLive) presenterManager.requestClearDisplay()
            when (it.mediaType) {
                Constants.MEDIA_TYPE_URL -> { selectedSourceType = Constants.MEDIA_TYPE_URL; urlInput = it.mediaUrl }
                else -> selectedSourceType = Constants.MEDIA_TYPE_LOCAL
            }
            // Local file when it resolves here, the primary's stream when it doesn't — see
            // followerMediaUrl.
            val effectiveUrl = followerMediaUrl(
                mediaType = it.mediaType,
                localUrl = it.mediaUrl,
                remoteStreamUrl = instanceLinkMediaStreamUrl?.invoke(it.id)
            )
            viewModel.loadMediaFromSchedule(
                url = effectiveUrl,
                title = it.mediaTitle,
                type = it.mediaType,
                subtitleUrl = it.subtitleUrl
            )
            // The other half of the blanking above: the new file is loaded, so show it.
            if (wasLive) presenterManager.setPresentingMode(Presenting.MEDIA)
            focusRequester.requestFocus()
        }
    }
}
