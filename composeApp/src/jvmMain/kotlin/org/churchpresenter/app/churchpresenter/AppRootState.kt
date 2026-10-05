package org.churchpresenter.app.churchpresenter

import org.churchpresenter.showcontrol.ActionRunner
import org.churchpresenter.server.InstanceLinkCommandFailure
import org.churchpresenter.core.models.songs.SongItem
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import org.churchpresenter.canvas.DeckLinkManager
import org.churchpresenter.settings.QuickBackground
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.CompanionSatelliteSettings
import org.churchpresenter.settings.ResolvedDisplay
import org.churchpresenter.settings.reconcileScreenAssignments
import org.churchpresenter.app.churchpresenter.data.LiveDurationLog
import org.churchpresenter.settings.SettingsManager
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.bibletab.VerseSequenceLog
import org.churchpresenter.converter.ui.ConverterTab
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.theme.themeFromSettings
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.bible.Bible
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.qa.QAManager
import org.churchpresenter.obs.OBSWebSocketManager
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import org.churchpresenter.server.InstanceLinkViewModel
import org.churchpresenter.stt.STTManager
import org.churchpresenter.settings.utils.AppDataDir
import org.churchpresenter.app.churchpresenter.utils.LiveMapReporter
import org.churchpresenter.app.churchpresenter.utils.UpdateCheckResult
import java.awt.GraphicsEnvironment
import java.io.File
import java.util.Locale
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * The desktop app's own state and services: the settings it edits, the managers and ViewModels
 * that live for the whole run, and every window and dialog flag.
 *
 * Created once, on the first composition of [ChurchPresenterApp], in the order the separate
 * `remember` calls used to run. The pieces of the app are extensions of it and read these under
 * the names they always had.
 */
@Stable
internal class AppRootState(
    private val application: ApplicationScope,
    val coroutineScope: CoroutineScope,
) {
    var appReady by mutableStateOf(false)
    val settingsManager = SettingsManager()
    val statisticsManager = StatisticsManager()
    val verseSequenceLog = VerseSequenceLog()
    var appSettings by mutableStateOf(settingsManager.loadSettings().let {
        it.copy(presentationRemoteSettings = it.presentationRemoteSettings.copy(remoteControlEnabled = false))
    })

    init {
        reconcileScreenAssignmentsAtStartup()
    }

    // Decided at construction so hidden outputs never open and then close again.
    val presenterManager =
        PresenterManager(showPresenterWindowInitially = !appSettings.projectionSettings.startOutputsHidden)

    // How long each thing actually stays on screen, kept beside the calendar it informs.
    val liveDurationLog = LiveDurationLog(File(AppDataDir.resolve(), "durations.json")).also { log ->
        // A reading is written when it closes -- the next row going live, or the outputs
        // clearing -- so the last song of a session had been dying with the process. The
        // app exits by System.exit from two menus and a window close, and a hook covers all
        // three (and a kill) without each of them having to remember.
        Runtime.getRuntime().addShutdownHook(Thread { log.wentBlank() })
    }

    var eulaAccepted by mutableStateOf(isEulaAccepted(appSettings.eulaAcceptedVersion, CURRENT_EULA_VERSION))
    var showSetupWizard by mutableStateOf(shouldShowSetupWizard(appSettings))
    var currentLanguage by mutableStateOf(
        resolveStartupLanguage(appSettings.language).also { Locale.setDefault(Locale.forLanguageTag(it.code)) }
    )

    var scheduleActions by mutableStateOf(ScheduleActions())
    /** The Schedule's actions as they stand now, for a lambda built earlier. */
    val currentScheduleActions get() = scheduleActions

    val mediaViewModel = MediaViewModel()
    var identifyingScreen by mutableStateOf(false)
    var theme by mutableStateOf(themeFromSettings(appSettings.theme))
    val companionServer = CompanionServer(appVersion = BuildConfig.APP_VERSION)
    val qaManager = QAManager().also { companionServer.qaManager = it }
    val sttManager = STTManager()
    val obsManager = OBSWebSocketManager()
    val companionSatelliteViewModel = CompanionSatelliteViewModel()

    /** Runs show-control action lists -- calendar cues today, cue actions and macros later. */
    val showRunner: ActionRunner by lazy { ActionRunner(appShowHost(), coroutineScope) }
    val autoConnectedIds = mutableSetOf<String>()
    val lastReconciled = mutableMapOf<String, CompanionSatelliteSettings>()

    val instanceLinkViewModel = InstanceLinkViewModel()
    var primaryBibleForInstanceLink by mutableStateOf<Bible?>(null)
    var scenesForInstanceLink by mutableStateOf<List<Scene>>(emptyList())
    val instanceLinkCommandFailures = mutableStateListOf<InstanceLinkCommandFailure>()
    var mirroredBackgroundSettings by mutableStateOf<BackgroundSettings?>(null)

    // A quick-tray pick lives only as long as the app is open: it is a live control, not a setting,
    // so it is held here rather than written back through onSettingsChange.
    var activeQuickBackground by mutableStateOf<QuickBackground?>(null)
    val screenCountForUsage = LiveMapReporter.detectScreenCount()
    val deckLinkCountForUsage =
        deckLinkOutputCount(DeckLinkManager.isAvailable()) { DeckLinkManager.listDevices().size }

    val prevTunnelWasConnected = mutableStateOf(false)
    var qaDisplayUrl by mutableStateOf("")
    var presentationDisplayUrl by mutableStateOf("")
    var presentationFrozen by mutableStateOf(false)

    val remoteSelectSongFlow = MutableSharedFlow<ScheduleItem.SongItem>(extraBufferCapacity = REMOTE_FLOW_BUFFER)
    val remoteSelectPictureFlow = MutableSharedFlow<ScheduleItem.PictureItem>(extraBufferCapacity = REMOTE_FLOW_BUFFER)
    val remoteSelectPresentationFlow =
        MutableSharedFlow<ScheduleItem.PresentationItem>(extraBufferCapacity = REMOTE_FLOW_BUFFER)
    val remoteSelectMediaFlow = MutableSharedFlow<ScheduleItem.MediaItem>(extraBufferCapacity = REMOTE_FLOW_BUFFER)

    // What the automation engine last put on screen, or null once it blanked. The engine yields
    // to a hand on the controls: if the outputs show something other than this -- a Schedule row
    // clicked, a song sent from the Songs tab -- a due cue is skipped rather than fired over the
    // operator. See CueRunner.operatorLive and LiveDurationLog.showing.
    var engineLiveItem by mutableStateOf<ScheduleItem?>(null)

    var dialogDismissSignal by mutableStateOf(0)
    var showOptionsDialog by mutableStateOf(false)
    var optionsDialogInitialTab by mutableStateOf(0)
    val openOptionsDialog: (Int) -> Unit = { tab ->
        optionsDialogInitialTab = tab
        showOptionsDialog = true
    }
    var showStatisticsDialog by mutableStateOf(false)
    var showInstanceLinkDialog by mutableStateOf(false)
    var showKeyboardShortcutsDialog by mutableStateOf(false)
    var showCustomizeThemeDialog by mutableStateOf(false)
    var showAboutDialog by mutableStateOf(false)
    var showContactDialog by mutableStateOf(false)
    var contactDialogInitialType by mutableStateOf<String?>(null)
    var showStoryPrompt by mutableStateOf(false)
    var showConverterWindow by mutableStateOf(false)
    // Which tab it opens on. The Help menu wants the converter as a whole; the setup wizard's
    // song step wants Songs, because that is the format problem it just described.
    var converterInitialTab by mutableStateOf(ConverterTab.BIBLES)
    var showSongLibraryWindow by mutableStateOf(false)
    var showCalendarWindow by mutableStateOf(false)
    // Raised to have the Calendar Manager open a new service on the Schedule tab's rows.
    var calendarNewServiceFromSchedule by mutableStateOf(0)
    // What the Schedule tab holds right now, mirrored here from the same callback that feeds the
    // Companion server. The Calendar Manager reads it to decide whether "load" would discard
    // anything, and to copy a live-built service back onto a date.
    var currentScheduleItems by mutableStateOf<List<ScheduleItem>>(emptyList())
    // Whether the Schedule's cue rows may fire. Set from the service's own switch as it is loaded
    // from the Calendar Manager, and from the Schedule tab's switch after that; the engine reads it
    // every tick, so what the tab shows armed is exactly what will fire.
    var automationArmed by mutableStateOf(true)
    var showLottieGenWindow by mutableStateOf(false)
    var showStyleEditorWindow by mutableStateOf(false)
    var showMemoryMonitorWindow by mutableStateOf(false)
    var developerMenuUnlocked by mutableStateOf(false)
    var lottieGenOutputDir by mutableStateOf<File?>(null)
    var lottieGenOnFileSaved by mutableStateOf<(() -> Unit)?>(null)
    var pendingUpdateResult by mutableStateOf<UpdateCheckResult?>(null)
    var pendingUpdateCheckWasManual by mutableStateOf(false)
    var selectedScheduleItemId by mutableStateOf<String?>(null)

    fun exitApplication() = application.exitApplication()

    fun setInstanceLinkEnabled(enabled: Boolean) {
        if (!instanceLinkEnabledChanged(appSettings.instanceLink, enabled)) return
        appSettings = appSettings.copy(instanceLink = appSettings.instanceLink.copy(enabled = enabled))
        settingsManager.saveSettings(appSettings)
    }

    /** Fits the saved screen assignments to the displays and DeckLink devices this machine has now. */
    private fun reconcileScreenAssignmentsAtStartup() {
        val screenDevicesAll = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        val primaryDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        val nonPrimaryDisplays = screenDevicesAll.filter { it != primaryDevice }.map { device ->
            val bounds = device.defaultConfiguration.bounds
            ResolvedDisplay(
                deviceIndex = screenDevicesAll.indexOf(device),
                x = bounds.x, y = bounds.y, width = bounds.width, height = bounds.height,
            )
        }
        val deckLinkCount = deckLinkOutputCount(DeckLinkManager.isAvailable()) { DeckLinkManager.listDevices().size }

        val proj = appSettings.projectionSettings
        val assignments = reconcileScreenAssignments(
            proj.screenAssignments, nonPrimaryDisplays, deckLinkCount, proj.fallbackProfileId,
        )
        if (assignments != null) {
            appSettings = appSettings.copy(
                projectionSettings = proj.copy(screenAssignments = assignments)
            )
            settingsManager.saveSettings(appSettings)
        }
    }
}

private const val REMOTE_FLOW_BUFFER = 8
