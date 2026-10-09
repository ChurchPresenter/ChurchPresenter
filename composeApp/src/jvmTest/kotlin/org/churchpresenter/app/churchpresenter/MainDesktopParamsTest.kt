@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import org.churchpresenter.core.models.shortcuts.KeyChord
import org.churchpresenter.settings.KeyboardShortcutSettings
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.sharedui.utils.ShortcutMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.bibletab.VerseSequenceLog
import org.churchpresenter.calendar.model.UpcomingLoad
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.qa.QAManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.QuickBackground
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.statistics.StatisticsManager
import org.churchpresenter.stt.STTManager
import org.churchpresenter.theme.ThemeMode
import java.io.File
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [MainDesktop] given every optional input, and then each one replaced while it stays on screen: the
 * screen must take the new value without leaving, and the tabs that only build with a manager do.
 */
class MainDesktopParamsTest : MainDesktopComposeHarness() {

    private fun callbacks(presented: MutableList<String> = mutableListOf()) = LiveOutputCallbacks(
        presenting = { presented += it.name },
        onVerseSelected = {},
        onSongItemSelected = {},
    )

    /** Every input [MainDesktop] takes, each one a state a test can replace while it is on screen. */
    private inner class Inputs(val base: AppSettings) {
        var appSettings by mutableStateOf(base)
        var preview by mutableStateOf(base)
        var quick by mutableStateOf<QuickBackground?>(null)
        var theme by mutableStateOf(ThemeMode.SYSTEM)
        var dismiss by mutableIntStateOf(0)
        var web by mutableStateOf(WebAccessState())
        var service by mutableStateOf(ServicePlanLink())
        var link by mutableStateOf(InstanceLinkBridge())
        var live by mutableStateOf(callbacks())
        var publish by mutableStateOf(MainDesktopPublishers())
        var flows by mutableStateOf(RemoteControlFlows())
        var settingsOpened = 0
        var onShowSettings by mutableStateOf<() -> Unit>({ settingsOpened++ })
        var onShowBackgrounds by mutableStateOf<() -> Unit>({})
        var onLottieGen by mutableStateOf<(String, (() -> Unit)?) -> Unit>({ _, _ -> })
        var onChange by mutableStateOf<((AppSettings) -> AppSettings) -> Unit>({})
        var onQuickPicked by mutableStateOf<(QuickBackground?) -> Unit>({})
        var onUnlock by mutableStateOf<() -> Unit>({})
        var verseLog by mutableStateOf(VerseSequenceLog(file = File(dir, "verse_sequences.json")))
        var statistics by mutableStateOf<StatisticsManager?>(null)
        var manager by mutableStateOf(PresenterManager())
        var companion by mutableStateOf(CompanionSatelliteViewModel())
        var qa by mutableStateOf<QAManager?>(null)
        var stt by mutableStateOf<STTManager?>(null)
        var shortcuts by mutableStateOf(ShortcutMap.from(base.keyboardShortcutSettings))
    }

    private fun ComposeUiTest.show(inputs: Inputs) = with(inputs) {
        setContent {
            CompositionLocalProvider(LocalShortcuts provides shortcuts) {
                MaterialTheme {
                    MainDesktop(
                        appSettings = appSettings,
                        livePreviewAppSettings = preview,
                        activeQuickBackground = quick,
                        onQuickBackgroundPicked = onQuickPicked,
                        presenterManager = manager,
                        statisticsManager = statistics,
                        verseSequenceLog = verseLog,
                        live = live,
                        service = service,
                        publish = publish,
                        flows = flows,
                        link = link,
                        web = web,
                        onShowSettings = onShowSettings,
                        onShowBackgroundSettings = onShowBackgrounds,
                        onSettingsChange = onChange,
                        theme = theme,
                        onOpenLottieGen = onLottieGen,
                        dialogDismissSignal = dismiss,
                        companionSatelliteViewModel = companion,
                        onRequestDeveloperMenuUnlock = onUnlock,
                        qaManager = qa,
                        sttManager = stt,
                    )
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.replaceEach(vararg replacements: () -> Unit) = replacements.forEach { replace ->
        replace()
        waitForIdle()
    }

    @Test
    fun `every setting and callback can be replaced while the screen stays up`() = runComposeUiTest {
        val inputs = Inputs(withOneSong())
        show(inputs)
        with(inputs) {
            replaceEach(
                { appSettings = base.copy(quickBackgroundsExpanded = !base.quickBackgroundsExpanded) },
                { preview = base.copy(analyticsReportingEnabled = !base.analyticsReportingEnabled) },
                { quick = QuickBackground(id = "q", label = "Q") },
                { theme = ThemeMode.DARK },
                { dismiss++ },
                { web = WebAccessState(serverUrl = "http://127.0.0.1:2") },
                {
                    service = ServicePlanLink(
                        upcomingServiceLoad = UpcomingLoad("s1", "Sunday", LocalDateTime.now().plusDays(1)),
                    )
                },
                { link = InstanceLinkBridge(followingHost = "10.0.0.5") },
                { live = callbacks() },
                { publish = MainDesktopPublishers(onSongsLoaded = {}) },
                { flows = RemoteControlFlows() },
                { onShowSettings = { settingsOpened += 2 } },
                { onShowBackgrounds = {} },
                { onLottieGen = { _, _ -> } },
                { onChange = {} },
                { onQuickPicked = {} },
                { onUnlock = {} },
                { appSettings = base.copy(hiddenTabs = setOf(Tabs.MEDIA.name)) },
            )
            assertEquals(0, settingsOpened, "replacing a callback calls none of them")
        }
    }

    @Test
    fun `every manager and the shortcuts can be replaced while the screen stays up`() = runComposeUiTest {
        val inputs = Inputs(withOneSong())
        show(inputs)
        with(inputs) {
            replaceEach(
                { statistics = StatisticsManager() },
                { verseLog = VerseSequenceLog(file = File(dir, "verse_sequences_2.json")) },
                { manager = PresenterManager() },
                { companion = CompanionSatelliteViewModel() },
                { qa = QAManager() },
                { stt = STTManager() },
                {
                    val take = mapOf(ShortcutAction.TAKE.name to listOf(KeyChord.of(Key.F9)))
                    shortcuts = ShortcutMap.from(KeyboardShortcutSettings(overrides = take))
                },
            )
            assertEquals(0, settingsOpened)
        }
    }

    @Test
    fun `the captions tab is built when it is the one showing`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MainDesktop(
                    appSettings = showingOnly(Tabs.STT),
                    presenterManager = PresenterManager(),
                    companionSatelliteViewModel = CompanionSatelliteViewModel(),
                    sttManager = STTManager(),
                    live = callbacks(),
                )
            }
        }
        waitForIdle()
    }

    @Test
    fun `a captions server that connects is remembered for the next start`() {
        val stt = STTManager()
        val changes = mutableListOf<(AppSettings) -> AppSettings>()
        val settings = settings().let {
            it.copy(sttSettings = it.sttSettings.copy(serverUrl = "http://stt.local:5000"))
        }
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    MainDesktop(
                        appSettings = settings,
                        presenterManager = PresenterManager(),
                        companionSatelliteViewModel = CompanionSatelliteViewModel(),
                        sttManager = stt,
                        onSettingsChange = { changes += it },
                        live = callbacks(),
                    )
                }
            }
            waitForIdle()
            stt.applyConnected()
            waitForIdle()
        }
        val saved = changes.fold(settings) { s, change -> change(s) }
        assertEquals("http://stt.local:5000", saved.sttSettings.lastConnectedUrl)
    }

    @Test
    fun `the sidebar's clear button clears the output and tells a follower`() {
        var followersCleared = 0
        val manager = PresenterManager()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    MainDesktop(
                        appSettings = settings(),
                        presenterManager = manager,
                        companionSatelliteViewModel = CompanionSatelliteViewModel(),
                        link = InstanceLinkBridge(sendClear = { followersCleared++ }),
                        live = callbacks(),
                    )
                }
            }
            waitForIdle()
            onNodeWithContentDescription("Clear Display").performClick()
            waitForIdle()
        }
        assertEquals(1, followersCleared)
    }

    @Test
    fun `the clear button with a media player and no follower pauses the player and clears`() {
        val media = MediaViewModel()
        val manager = PresenterManager()
        root(withOneSong(), presenterManager = manager, media = media) { _ ->
            manager.setShowPresenterWindow(true)
            onNodeWithContentDescription("Clear Display").performClick()
            waitForIdle()
            assertEquals(false, media.isPlaying)
            assertEquals(false, manager.isLive(Presenting.MEDIA))
        }
    }
}
