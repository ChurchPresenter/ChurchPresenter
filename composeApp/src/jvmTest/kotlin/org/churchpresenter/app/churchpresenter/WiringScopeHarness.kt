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
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.sharedui.utils.ShortcutMap
import org.churchpresenter.theme.ThemeMode

internal abstract class WiringScopeHarness : MainDesktopComposeHarness() {

    internal class ScopeInputs(
        val appSettings: AppSettings,
        val presenterManager: PresenterManager = PresenterManager(),
        val live: LiveOutputCallbacks =
            LiveOutputCallbacks(presenting = {}, onVerseSelected = {}, onSongItemSelected = {}),
        val publish: MainDesktopPublishers = MainDesktopPublishers(),
        val onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
        val media: MediaViewModel? = null,
        val visibleTabs: List<Tabs> = Tabs.entries.toList(),
        val selectedTabIndex: Int = 0,
    )

    internal fun scoped(
        inputs: ScopeInputs = ScopeInputs(settings()),
        content: @Composable MainDesktopScope.() -> Unit = {},
        block: ComposeUiTest.(MainDesktopScope) -> Unit,
    ) = runComposeUiTest {
        val state = MainDesktopState(mutableStateOf(inputs.selectedTabIndex), false)
        val vms = MainDesktopViewModels(
            inputs.appSettings,
            mutableStateOf(inputs.publish),
            mutableStateOf(InstanceLinkBridge()),
            DictionaryFixture.files(),
        )
        var built: MainDesktopScope? = null
        try {
            setContent {
                val clickerScope = rememberCoroutineScope()
                val shortcuts = remember { ShortcutMap.from(inputs.appSettings.keyboardShortcutSettings) }
                val scope = remember {
                    MainDesktopScope(
                        hostWindow = null,
                        appSettings = inputs.appSettings,
                        livePreviewAppSettings = inputs.appSettings,
                        activeQuickBackground = null,
                        onQuickBackgroundPicked = {},
                        presenterManager = inputs.presenterManager,
                        statisticsManager = null,
                        verseSequenceLog = null,
                        live = inputs.live,
                        service = ServicePlanLink(),
                        publish = inputs.publish,
                        flows = RemoteControlFlows(),
                        link = InstanceLinkBridge(),
                        web = WebAccessState(),
                        onShowSettings = {},
                        onShowBackgroundSettings = {},
                        onSettingsChange = inputs.onSettingsChange,
                        theme = ThemeMode.SYSTEM,
                        qaManager = null,
                        onOpenLottieGen = { _, _ -> },
                        sttManager = null,
                        dialogDismissSignal = 0,
                        companionSatelliteViewModel = CompanionSatelliteViewModel(),
                        onRequestDeveloperMenuUnlock = {},
                        owned = MainDesktopOwned(
                            state, vms, inputs.media, inputs.visibleTabs, clickerScope, shortcuts,
                        ),
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
