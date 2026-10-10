@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import org.churchpresenter.dictionary.DictionaryFixture
import org.churchpresenter.dictionary.data.DictionaryFiles
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.qa.QAManager
import org.churchpresenter.schedule.ScheduleTabActions
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.sharedui.utils.ShortcutMap
import org.churchpresenter.stt.STTManager
import org.churchpresenter.theme.ThemeMode

internal abstract class MainDesktopScopeHarness : MainDesktopComposeHarness() {

    protected fun noLive() = LiveOutputCallbacks(presenting = {}, onVerseSelected = {}, onSongItemSelected = {})

    internal fun pane(
        appSettings: AppSettings = settings(),
        actions: ScheduleTabActions = ScheduleTabActions(),
        presenterManager: PresenterManager = PresenterManager(),
        live: LiveOutputCallbacks = noLive(),
        onOpenLottieGen: (outputDir: String, onFileSaved: (() -> Unit)?) -> Unit = { _, _ -> },
        showCrashFeedback: Boolean = false,
        dictionaryFiles: DictionaryFiles = DictionaryFixture.files(),
        qaManager: QAManager? = null,
        sttManager: STTManager? = null,
        content: @Composable MainDesktopScope.() -> Unit,
        block: ComposeUiTest.(MainDesktopScope) -> Unit = {},
    ) = runComposeUiTest {
        val state = MainDesktopState(mutableStateOf(0), showCrashFeedback).apply { scheduleActions = actions }
        val vms = MainDesktopViewModels(
            appSettings,
            mutableStateOf(MainDesktopPublishers()),
            mutableStateOf(InstanceLinkBridge()),
            dictionaryFiles,
        )
        var built: MainDesktopScope? = null
        try {
            setContent {
                val clickerScope = rememberCoroutineScope()
                val shortcuts = remember { ShortcutMap.from(appSettings.keyboardShortcutSettings) }
                val scope = remember {
                    MainDesktopScope(
                        hostWindow = null,
                        appSettings = appSettings,
                        livePreviewAppSettings = appSettings,
                        activeQuickBackground = null,
                        onQuickBackgroundPicked = {},
                        presenterManager = presenterManager,
                        statisticsManager = null,
                        verseSequenceLog = null,
                        live = live,
                        service = ServicePlanLink(),
                        publish = MainDesktopPublishers(),
                        flows = RemoteControlFlows(),
                        link = InstanceLinkBridge(),
                        web = WebAccessState(),
                        onShowSettings = {},
                        onShowBackgroundSettings = {},
                        onSettingsChange = {},
                        theme = ThemeMode.SYSTEM,
                        qaManager = qaManager,
                        onOpenLottieGen = onOpenLottieGen,
                        sttManager = sttManager,
                        dialogDismissSignal = 0,
                        companionSatelliteViewModel = CompanionSatelliteViewModel(),
                        onRequestDeveloperMenuUnlock = {},
                        owned = MainDesktopOwned(state, vms, null, Tabs.entries.toList(), clickerScope, shortcuts),
                    )
                }
                built = scope
                CompositionLocalProvider(LocalShortcuts provides shortcuts) {
                    MaterialTheme { scope.content() }
                }
            }
            waitForIdle()
            block(checkNotNull(built))
        } finally {
            vms.dispose()
        }
    }
}
