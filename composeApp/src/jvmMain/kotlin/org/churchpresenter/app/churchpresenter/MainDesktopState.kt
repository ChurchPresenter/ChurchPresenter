package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import org.churchpresenter.schedule.ScheduleTabActions
import org.churchpresenter.core.models.schedule.ScheduleItem

/**
 * The main screen's own UI state: which tab is showing, which schedule row each tab was last handed,
 * which dialog is open, and how far along each hidden key sequence is.
 *
 * Each selected item travels with a version that is bumped on every hand-over, so a tab reacts to
 * the same row being chosen twice.
 */
@Stable
internal class MainDesktopState(
    selectedTabIndexState: MutableState<Int>,
    showCrashFeedbackInitially: Boolean,
) {
    /** The Schedule tab's own actions, handed up once it has composed. */
    var scheduleActions by mutableStateOf(ScheduleTabActions())

    var selectedBibleVerseItem by mutableStateOf<ScheduleItem.BibleVerseItem?>(null)
    var selectedBibleVerseItemVersion by mutableStateOf(0)
    var selectedSongItem by mutableStateOf<ScheduleItem.SongItem?>(null)
    var selectedSongItemVersion by mutableStateOf(0)
    var selectedPictureItem by mutableStateOf<ScheduleItem.PictureItem?>(null)
    var selectedPictureItemVersion by mutableStateOf(0)
    var selectedPresentationItem by mutableStateOf<ScheduleItem.PresentationItem?>(null)
    var selectedPresentationItemVersion by mutableStateOf(0)
    var selectedMediaItem by mutableStateOf<ScheduleItem.MediaItem?>(null)
    var selectedMediaItemVersion by mutableStateOf(0)
    var selectedLowerThirdItem by mutableStateOf<ScheduleItem.LowerThirdItem?>(null)
    var selectedLowerThirdItemVersion by mutableStateOf(0)
    var selectedWebsiteItem by mutableStateOf<ScheduleItem.WebsiteItem?>(null)
    var selectedWebsiteItemVersion by mutableStateOf(0)

    var showCrosswordTab by mutableStateOf(false)
    var selectedTabIndex by selectedTabIndexState

    var showAddLabelDialog by mutableStateOf(false)
    /** The item a tab's Save preset is naming, or null while that dialog is closed. */
    var presetToSave by mutableStateOf<ScheduleItem?>(null)
    var editingLabelItem by mutableStateOf<ScheduleItem.LabelItem?>(null)
    var showAddWebsiteDialog by mutableStateOf(false)
    var showKonamiEasterEgg by mutableStateOf(false)
    /** Invites feedback on the launch after an unexpected shutdown (opt-in analytics only). */
    var showCrashFeedback by mutableStateOf(showCrashFeedbackInitially)

    var konamiProgress by mutableStateOf(0)
    var crosswordProgress by mutableStateOf(0)
    var developerUnlockProgress by mutableStateOf(0)

    val mainFocusRequester = FocusRequester()

    /** Hands [item] to the tab that opens it, bumping its version; false for a type no tab opens this way. */
    fun select(item: ScheduleItem): Boolean {
        when (item) {
            is ScheduleItem.SongItem -> { selectedSongItem = item; selectedSongItemVersion++ }
            is ScheduleItem.BibleVerseItem -> { selectedBibleVerseItem = item; selectedBibleVerseItemVersion++ }
            is ScheduleItem.PictureItem -> { selectedPictureItem = item; selectedPictureItemVersion++ }
            is ScheduleItem.PresentationItem -> {
                selectedPresentationItem = item
                selectedPresentationItemVersion++
            }
            is ScheduleItem.MediaItem -> { selectedMediaItem = item; selectedMediaItemVersion++ }
            is ScheduleItem.LowerThirdItem -> { selectedLowerThirdItem = item; selectedLowerThirdItemVersion++ }
            is ScheduleItem.WebsiteItem -> { selectedWebsiteItem = item; selectedWebsiteItemVersion++ }
            else -> return false
        }
        return true
    }
}

@Composable
internal fun rememberMainDesktopState(showCrashFeedbackInitially: () -> Boolean): MainDesktopState {
    val selectedTabIndexState = rememberSaveable { mutableStateOf(0) }
    return remember { MainDesktopState(selectedTabIndexState, showCrashFeedbackInitially()) }
}
