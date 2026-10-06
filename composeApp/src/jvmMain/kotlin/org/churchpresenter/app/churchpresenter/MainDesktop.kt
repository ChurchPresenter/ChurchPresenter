package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.bibletab.VerseSequenceLog
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import org.churchpresenter.media.viewmodel.LocalMediaViewModel
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.qa.QAManager
import org.churchpresenter.stt.STTManager
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.QuickBackground
import org.churchpresenter.theme.ThemeMode
import java.awt.Window as AwtWindow

/**
 * The main window's content: the Schedule, the tabs and the live preview, and everything that wires
 * them to each other and to the live output.
 *
 * This function only assembles the screen. Its pieces live beside it, each an extension of the
 * [MainDesktopScope] it builds here on every composition:
 * - [MainDesktopState] and [MainDesktopViewModels] — what the screen remembers and owns;
 * - [LeadingEffects], [ContentPublishWiring], [InstanceLinkMirrorWiring], [BibleWiring] and
 *   [TrailingEffects] — the effects that tie the ViewModels together, in their original order;
 * - [handleMainDesktopKey] — the window's shortcuts and hidden key sequences;
 * - [MainDesktopPanels] — the three resizable panels, holding [ScheduleSidebar], [MainTabArea] and
 *   [PreviewSidebar];
 * - [MainDesktopDialogs] — the dialogs the screen opens itself.
 *
 * @param hostWindow The hosting AWT window — lets tabs force window focus back when AWT's focus
 *   tracking wedges (see PresentationTab's focus-lost rescue banner).
 * @param livePreviewAppSettings Same as [appSettings] except backgroundSettings may be swapped for a
 *   mirrored-from-primary copy (Instance Link) — used ONLY by the live preview, never for editing or
 *   persistence, so the Options dialog still shows this instance's own local background settings.
 * @param onRequestDeveloperMenuUnlock Invoked after D is pressed seven times in a row, revealing the
 *   Developer menu in packaged builds for this session.
 */
@Composable
fun MainDesktop(
    modifier: Modifier = Modifier,
    hostWindow: AwtWindow? = null,
    appSettings: AppSettings,
    livePreviewAppSettings: AppSettings = appSettings,
    activeQuickBackground: QuickBackground? = null,
    onQuickBackgroundPicked: (QuickBackground?) -> Unit = {},
    presenterManager: PresenterManager,
    statisticsManager: StatisticsManager? = null,
    verseSequenceLog: VerseSequenceLog? = null,
    live: LiveOutputCallbacks,
    service: ServicePlanLink = ServicePlanLink(),
    publish: MainDesktopPublishers = MainDesktopPublishers(),
    flows: RemoteControlFlows = RemoteControlFlows(),
    link: InstanceLinkBridge = InstanceLinkBridge(),
    web: WebAccessState = WebAccessState(),
    onShowSettings: () -> Unit = {},
    onShowBackgroundSettings: () -> Unit = {},
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    theme: ThemeMode = ThemeMode.SYSTEM,
    qaManager: QAManager? = null,
    onOpenLottieGen: (outputDir: String, onFileSaved: (() -> Unit)?) -> Unit = { _, _ -> },
    sttManager: STTManager? = null,
    dialogDismissSignal: Int = 0,
    companionSatelliteViewModel: CompanionSatelliteViewModel,
    onRequestDeveloperMenuUnlock: () -> Unit = {},
) {
    val state = rememberMainDesktopState {
        CrashReporter.didCrashLastRun && appSettings.analyticsReportingEnabled
    }
    val vms = rememberMainDesktopViewModels(appSettings, publish, link)
    val hasCompanionTabConnections = appSettings.companionSatelliteConnections
        .any { it.showInTab && it.host.isNotBlank() }
    val visibleTabs = remember(appSettings.hiddenTabs, state.showCrosswordTab, hasCompanionTabConnections) {
        computeVisibleTabs(appSettings.hiddenTabs, state.showCrosswordTab, hasCompanionTabConnections)
    }
    val mediaViewModel = LocalMediaViewModel.current
    val clickerScope = rememberCoroutineScope()
    val shortcuts = LocalShortcuts.current
    val owned = remember(state, vms, mediaViewModel, visibleTabs, clickerScope, shortcuts) {
        MainDesktopOwned(
            state = state,
            vms = vms,
            mediaViewModel = mediaViewModel,
            visibleTabs = visibleTabs,
            clickerScope = clickerScope,
            shortcuts = shortcuts,
        )
    }
    // Remembered, keyed on everything it holds: a new scope on every recomposition would hand the
    // pieces new lambdas each time, and a click handler keyed on its lambda would restart.
    val scope = remember(
        hostWindow, appSettings, livePreviewAppSettings, activeQuickBackground, onQuickBackgroundPicked,
        presenterManager, statisticsManager, verseSequenceLog, live, service, publish, flows, link, web, onShowSettings,
        onShowBackgroundSettings, onSettingsChange, theme, qaManager, onOpenLottieGen, sttManager, dialogDismissSignal,
        companionSatelliteViewModel, onRequestDeveloperMenuUnlock, owned
    ) {
        MainDesktopScope(
            hostWindow = hostWindow,
            appSettings = appSettings,
            livePreviewAppSettings = livePreviewAppSettings,
            activeQuickBackground = activeQuickBackground,
            onQuickBackgroundPicked = onQuickBackgroundPicked,
            presenterManager = presenterManager,
            statisticsManager = statisticsManager,
            verseSequenceLog = verseSequenceLog,
            live = live,
            service = service,
            publish = publish,
            flows = flows,
            link = link,
            web = web,
            onShowSettings = onShowSettings,
            onShowBackgroundSettings = onShowBackgroundSettings,
            onSettingsChange = onSettingsChange,
            theme = theme,
            qaManager = qaManager,
            onOpenLottieGen = onOpenLottieGen,
            sttManager = sttManager,
            dialogDismissSignal = dialogDismissSignal,
            companionSatelliteViewModel = companionSatelliteViewModel,
            onRequestDeveloperMenuUnlock = onRequestDeveloperMenuUnlock,
            owned = owned,
        )
    }
    with(scope) {
        LeadingEffects()
        ContentPublishWiring()
        InstanceLinkMirrorWiring()
        BibleWiring()
        TrailingEffects()

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .focusRequester(mainFocusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent -> handleMainDesktopKey(keyEvent) }
        ) {
            MainDesktopPanels()
        }

        MainDesktopDialogs()
    }
}
