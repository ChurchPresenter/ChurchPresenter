package org.churchpresenter.app.churchpresenter

import kotlinx.coroutines.CoroutineScope
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.app.churchpresenter.data.VerseSequenceLog
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import org.churchpresenter.app.churchpresenter.utils.ShortcutMap
import org.churchpresenter.app.churchpresenter.viewmodel.CompanionSatelliteViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.QAManager
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.QuickBackground
import org.churchpresenter.theme.ThemeMode
import java.awt.Window as AwtWindow

/** What the main screen owns rather than takes: its state, its ViewModels, and the values derived per composition. */
internal class MainDesktopOwned(
    val state: MainDesktopState,
    val vms: MainDesktopViewModels,
    val mediaViewModel: MediaViewModel?,
    val visibleTabs: List<Tabs>,
    /** Runs the clicker-key (Page Down/Up) slide advances from the root key handler. */
    val clickerScope: CoroutineScope,
    val shortcuts: ShortcutMap,
)

/**
 * Everything MainDesktop's pieces read, for one composition: its parameters, its state and its
 * ViewModels, under the names the screen has always used for them.
 *
 * Remembered, keyed on every constructor argument: a changed argument builds a new scope, so a lambda
 * built inside one of the pieces still captures the current parameters, while unchanged arguments
 * keep the pieces' lambdas stable across recompositions. The state and the ViewModels it forwards to
 * are the remembered ones.
 */
// Wide by design: it stands in for the parameter list and locals of the one function it was split
// out of, so every piece reads them under the same names.
@Suppress("LongParameterList")
internal class MainDesktopScope(
    val hostWindow: AwtWindow?,
    val appSettings: AppSettings,
    val livePreviewAppSettings: AppSettings,
    val activeQuickBackground: QuickBackground?,
    val onQuickBackgroundPicked: (QuickBackground?) -> Unit,
    val presenterManager: PresenterManager,
    val statisticsManager: StatisticsManager?,
    val verseSequenceLog: VerseSequenceLog?,
    val live: LiveOutputCallbacks,
    val service: ServicePlanLink,
    val publish: MainDesktopPublishers,
    val flows: RemoteControlFlows,
    val link: InstanceLinkBridge,
    val web: WebAccessState,
    val onShowSettings: () -> Unit,
    val onShowBackgroundSettings: () -> Unit,
    val onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    val theme: ThemeMode,
    val qaManager: QAManager?,
    val onOpenLottieGen: (outputDir: String, onFileSaved: (() -> Unit)?) -> Unit,
    val sttManager: STTManager?,
    val dialogDismissSignal: Int,
    val companionSatelliteViewModel: CompanionSatelliteViewModel,
    val onRequestDeveloperMenuUnlock: () -> Unit,
    private val owned: MainDesktopOwned,
) {
    val state = owned.state
    val mediaViewModel = owned.mediaViewModel
    val visibleTabs = owned.visibleTabs
    val clickerScope = owned.clickerScope
    val shortcuts = owned.shortcuts

    // Clamp synchronously so no composition pass ever sees an out-of-bounds index.
    val effectiveTabIndex get() = clampedTabIndex(state.selectedTabIndex, visibleTabs)
    val currentTab get() = visibleTabs[effectiveTabIndex]
    val presentingMode: Presenting get() = presenterManager.presentingMode.value
    val mainFocusRequester = state.mainFocusRequester

    val picturesViewModel = owned.vms.picturesViewModel
    val presentationViewModel = owned.vms.presentationViewModel
    val sceneViewModel = owned.vms.sceneViewModel
    val songsViewModel = owned.vms.songsViewModel
    val bibleViewModel = owned.vms.bibleViewModel
    val bibleEngineClient = owned.vms.bibleEngineClient
    val dictionaryViewModel = owned.vms.dictionaryViewModel
    val scheduleViewModel = owned.vms.scheduleViewModel

    /** The Schedule tab's actions as they stand now, for a toolbar lambda built earlier. */
    val currentScheduleActions = state.scheduleActions

    fun selectTab(tab: Tabs) {
        state.selectedTabIndex = resolveTabSelection(tab, visibleTabs, state.selectedTabIndex)
    }

    /**
     * Pushes the presentation's current slide to the presenter — shared by the clicker keys and the
     * next/previous slide Instance Link commands. Only pushes when Presentation is actually the live
     * content, same gate PresentationTab's own slide-push effect uses.
     */
    suspend fun pushCurrentSlideIfLive() {
        val index = presentationViewModel.selectedSlideIndex
        val slideCount = presentationViewModel.slideFiles.size
        if (!shouldPushSlide(presenterManager.presentingMode.value, index, slideCount)) return
        val (bitmap, nextBitmap) = decodeSlideBitmaps(
            presentationViewModel.slideFiles,
            index,
            presentationViewModel.nextShownSlideIndex(index),
        )
        presenterManager.setSelectedSlide(bitmap)
        presenterManager.setNextSlide(nextBitmap)
        presenterManager.setPresenterNotes(presenterNotesAt(presentationViewModel.slideNotes, index))
        // Keep animated playback in sync (or cleared) so a stale animated frame from a
        // previous slide can never override the freshly pushed static slide.
        presentationViewModel.deck?.let { presenterManager.presentationShowSlide(it, index) }
            ?: presenterManager.clearPresentationPlayback()
    }

    /** Clears every output, including a "Send to Stage Monitor" lock, from the Clear shortcut. */
    fun clearOutput() {
        mediaViewModel?.pause()
        presenterManager.requestClearDisplay()
        link.sendClear?.invoke()
        // Also release any "Send to Stage Monitor" lock (e.g. from Announcements)
        // so the stage monitor goes back to following the main presenting mode.
        stageMonitorScreenIndices(appSettings.projectionSettings)
            .forEach { presenterManager.setScreenLock(it, null) }
    }
}
