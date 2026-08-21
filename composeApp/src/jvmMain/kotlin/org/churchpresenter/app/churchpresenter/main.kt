package org.churchpresenter.app.churchpresenter

import org.churchpresenter.core.models.songs.SongItem
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key as composeKey
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import org.churchpresenter.app.churchpresenter.composables.DeckLinkManager
import org.churchpresenter.app.churchpresenter.utils.DevFlags
import org.churchpresenter.app.churchpresenter.utils.LottieFonts
import org.churchpresenter.app.churchpresenter.utils.SystemFonts
import org.churchpresenter.app.churchpresenter.utils.rememberScreenDevices
import presentation.engine.fonts.SlideFontRegistry
import androidx.compose.ui.window.rememberWindowState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.app_name
import churchpresenter.composeapp.generated.resources.ic_app_icon
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BackgroundSettings
import org.churchpresenter.app.churchpresenter.data.settings.CompanionSatelliteSettings
import org.churchpresenter.app.churchpresenter.data.settings.ResolvedDisplay
import org.churchpresenter.app.churchpresenter.data.settings.withWindowGeometry
import org.churchpresenter.app.churchpresenter.utils.windowPlacementFromSettings
import org.churchpresenter.app.churchpresenter.utils.windowPlacementToSettings
import org.churchpresenter.app.churchpresenter.data.settings.reconcileScreenAssignments
import org.churchpresenter.app.churchpresenter.data.settings.withBundledBible
import org.churchpresenter.app.churchpresenter.data.RemoteClientManager
import org.churchpresenter.app.churchpresenter.data.SettingsManager
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.app.churchpresenter.data.VerseSequenceLog
import org.churchpresenter.app.churchpresenter.dialogs.AboutDialog
import org.churchpresenter.app.churchpresenter.dialogs.InstanceLinkToastHost
import org.churchpresenter.app.churchpresenter.dialogs.CONTACT_TYPE_TESTIMONIAL
import org.churchpresenter.app.churchpresenter.dialogs.ContactUsDialog
import org.churchpresenter.app.churchpresenter.dialogs.ShareYourStoryDialog
import org.churchpresenter.app.churchpresenter.dialogs.ConverterWindow
import org.churchpresenter.app.churchpresenter.dialogs.SongLibraryWindow
import org.churchpresenter.app.churchpresenter.dialogs.LottieGenWindow
import org.churchpresenter.app.churchpresenter.dialogs.StyleEditorWindow
import org.churchpresenter.app.churchpresenter.dialogs.MemoryMonitorWindow
import org.churchpresenter.app.churchpresenter.dialogs.KeyboardShortcutsDialog
import org.churchpresenter.app.churchpresenter.dialogs.LicenseDialog
import org.churchpresenter.app.churchpresenter.dialogs.SetupWizardDialog
import org.churchpresenter.app.churchpresenter.dialogs.RemoteActivityNotification
import org.churchpresenter.app.churchpresenter.dialogs.RemoteActivityToastHost
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEvent
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEventDialog
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEventType
import org.churchpresenter.app.churchpresenter.dialogs.OptionsDialog
import org.churchpresenter.app.churchpresenter.presenter.BrowserSourceVideoRenderer
import org.churchpresenter.app.churchpresenter.presenter.CefManager
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.app.churchpresenter.ui.theme.LanguageProvider
import org.churchpresenter.theme.themeFromSettings
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.InstanceLinkCommandFailure
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.composables.isJavaFxAvailable
import org.churchpresenter.app.churchpresenter.composables.preWarmJavaFX
import org.churchpresenter.app.churchpresenter.composables.vlcCustomPath
import org.churchpresenter.app.churchpresenter.data.Bible
import org.churchpresenter.app.churchpresenter.server.LottieRenderCache
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.app.churchpresenter.server.LowerThirdSequencer
import org.churchpresenter.app.churchpresenter.dialogs.InstanceLinkDialog
import org.churchpresenter.app.churchpresenter.viewmodel.QAManager
import org.churchpresenter.app.churchpresenter.viewmodel.OBSWebSocketManager
import org.churchpresenter.app.churchpresenter.viewmodel.CompanionSatelliteViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.InstanceLinkViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import org.churchpresenter.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.utils.AppDataDir
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.LocalShortcuts
import org.churchpresenter.app.churchpresenter.utils.ShortcutMap
import org.churchpresenter.app.churchpresenter.utils.isSongLineMode
import org.churchpresenter.app.churchpresenter.utils.presenterScreenBounds

import org.churchpresenter.app.churchpresenter.utils.AutoStartManager
import org.churchpresenter.diagnostics.BuildIdentity
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.app.churchpresenter.utils.InstanceLinkLogSide
import org.churchpresenter.app.churchpresenter.utils.InstanceLinkLogger
import org.churchpresenter.app.churchpresenter.utils.LiveMapReporter
import org.churchpresenter.app.churchpresenter.utils.MacMenuBarActivationFix
import org.churchpresenter.app.churchpresenter.utils.UpdateCheckResult
import org.churchpresenter.app.churchpresenter.utils.UpdateChecker
import org.churchpresenter.app.churchpresenter.utils.UsageEvent
import org.churchpresenter.app.churchpresenter.utils.UsageEvents
import org.churchpresenter.app.churchpresenter.dialogs.CCLIReportDialog
import org.churchpresenter.app.churchpresenter.dialogs.UpdateAvailableDialog
import org.churchpresenter.app.churchpresenter.data.settings.answered
import org.churchpresenter.app.churchpresenter.data.settings.isDue
import org.churchpresenter.app.churchpresenter.data.settings.recordingUse
import org.churchpresenter.app.churchpresenter.data.settings.shown
import org.churchpresenter.app.churchpresenter.data.settings.stampingInstall
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.io.File
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.CoroutineExceptionHandler
import org.churchpresenter.app.churchpresenter.server.applyRemoteLiveState
import org.churchpresenter.app.churchpresenter.server.downloadMirroredBackgroundSettings
import org.churchpresenter.app.churchpresenter.server.remoteAccessDecision
import org.churchpresenter.app.churchpresenter.server.addScheduleItem
import org.churchpresenter.app.churchpresenter.server.batchEventSummary
import org.churchpresenter.app.churchpresenter.server.emitRemoteTabSelection
import org.churchpresenter.app.churchpresenter.server.RemoteApproval
import org.churchpresenter.app.churchpresenter.server.remoteApproval
import org.churchpresenter.app.churchpresenter.server.executeProjectItem
import org.churchpresenter.app.churchpresenter.server.instanceLinkBackgroundCacheDir
import org.churchpresenter.app.churchpresenter.server.qaActionType
import org.churchpresenter.app.churchpresenter.server.remoteEventLabel
import org.churchpresenter.app.churchpresenter.server.shouldMirrorRemoteBackgrounds
import org.churchpresenter.app.churchpresenter.server.shouldMirrorRemoteOutput
import org.churchpresenter.app.churchpresenter.server.shouldUseRemoteContent
import org.churchpresenter.app.churchpresenter.server.withAnnouncement

private const val MILLIS_PER_MINUTE = 60_000L
private const val CRASH_REPORT_RETRY_MS = 15_000L
private const val OPTIONS_TAB_BACKGROUND = 3
private const val UPDATE_CHECK_DELAY_MS = 5_000L
private const val STORY_PROMPT_DELAY_MS = 8_000L

private const val CURRENT_EULA_VERSION = 1

private var singleInstanceSocket: java.net.ServerSocket? = null

internal fun releaseSingleInstanceLock() {
    runCatching { singleInstanceSocket?.close() }
    singleInstanceSocket = null
}

internal fun acquireSingleInstanceLock(): Boolean {
    return try {
        val lockPort = singleInstanceLockPort(
            System.getProperty("churchpresenter.singleInstancePort"),
            Constants.SINGLE_INSTANCE_PORT,
        )
        singleInstanceSocket = java.net.ServerSocket(lockPort, 1, java.net.InetAddress.getLoopbackAddress())
        true
    } catch (_: Exception) {
        false
    }
}

/** The Bible shipped in the app's resources and installed on a first run. */
private const val BUNDLED_BIBLE_FILE = "kjv1769.spb"

/**
 * Writes the bundled KJV into the app's Bibles folder and points [settings] at it.
 *
 * A folder that cannot be written to is not on its own a reason to skip: the copy may already be
 * there from an earlier launch, and a read-only Bibles folder is a perfectly usable one — a managed
 * install, or a folder locked down after the fact, whose settings were then reset. Only when there
 * is no usable file *and* nowhere to put one is the bundle skipped and settings left pointing at no
 * Bible at all, so the setup wizard asks for a folder.
 */
private fun bundleDefaultBible(settings: AppSettings) {
    try {
        val defaultBibleDir = File(AppDataDir.resolve(), Constants.DEFAULT_BIBLES_FOLDER)
        val problem = bundledBibleSkipReason(defaultBibleDir, BUNDLED_BIBLE_FILE)
        if (problem != null) {
            CrashReporter.reportWarning(
                "Bundled KJV skipped: Bibles folder $problem",
                tags = mapOf("subsystem" to "bible_bundle", "reason" to problem)
            )
            return
        }
        val targetFile = File(defaultBibleDir, BUNDLED_BIBLE_FILE)
        if (!targetFile.exists()) {
            targetFile.writeBytes(runBlocking { Res.readBytes("files/bible_samples/$BUNDLED_BIBLE_FILE") })
        }
        SettingsManager().saveSettings(
            settings.withBundledBible(defaultBibleDir.absolutePath, BUNDLED_BIBLE_FILE)
        )
    } catch (e: Exception) {
        CrashReporter.reportException(e, "Bundling default KJV Bible")
    }
}

fun main() {
    if (shouldForceMetalRenderer(System.getProperty("os.name", ""))) {
        System.setProperty("skiko.renderApi", "METAL")
    }
    if (!acquireSingleInstanceLock()) {
        System.err.println("ChurchPresenter is already running.")
        javax.swing.JOptionPane.showMessageDialog(
            null,
            "ChurchPresenter is already running.",
            "ChurchPresenter",
            javax.swing.JOptionPane.WARNING_MESSAGE
        )
        System.exit(0)
        return
    }

    val startupSettings = SettingsManager().loadSettings()
    CrashReporter.initialize(
        startupSettings.analyticsReportingEnabled,
        // BuildConfig is generated into :composeApp, so :diagnostics is told what this build is
        // rather than reading it.
        BuildIdentity(
            versionDisplay = BuildConfig.VERSION_DISPLAY,
            appVersion = BuildConfig.APP_VERSION,
            isRelease = BuildConfig.IS_RELEASE,
        ),
    )
    CrashReporter.breadcrumb("Application started", category = "lifecycle")

    if (shouldBundleDefaultBible(startupSettings.bibleSettings)) bundleDefaultBible(startupSettings)

    val pendingUsageEvents = LiveMapReporter.eventsToReport(startupSettings, UsageEvents.unreported())
    val previousSessionMinutes = UsageEvents.lastSessionMinutes()
    LiveMapReporter.pingOnOpen(
        installId = analyticsInstallId(startupSettings.analyticsReportingEnabled) { CrashReporter.installId() },
        updateCheckInterval = startupSettings.updateCheckInterval,
        setup = {
            LiveMapReporter.setupFacts(
                startupSettings,
                screenCount = LiveMapReporter.detectScreenCount(),
                songCounts = LiveMapReporter.gatherSongCounts(startupSettings),
                sessionMinutes = previousSessionMinutes,
            )
        },
        events = pendingUsageEvents,
        onDelivered = {
            UsageEvents.markReported(pendingUsageEvents)
            if (previousSessionMinutes > 0) UsageEvents.clearSessionMinutes()
        }
    )

    val sessionStartedAt = System.currentTimeMillis()
    Runtime.getRuntime().addShutdownHook(Thread {
        UsageEvents.recordSessionMinutes(((System.currentTimeMillis() - sessionStartedAt) / MILLIS_PER_MINUTE).toInt())
    })

    val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        CrashReporter.reportException(throwable, context = "CoroutineExceptionHandler")
    }

    preWarmJavaFX()
    CrashReporter.setTag("javafx.available", isJavaFxAvailable().toString())

    CefManager.init()
    CrashReporter.setTag("jcef.available", CefManager.initialized.toString())
    if (CefManager.macOsUnsupported) CrashReporter.setTag("jcef.macos_unsupported", "true")

    io.github.vinceglb.filekit.FileKit.init(appId = "ChurchPresenter")

    Thread { AutoStartManager.syncRegistration() }.apply { isDaemon = true }.start()

    Thread {
        LottieFonts.bundledFontResources().forEach { resource ->
            LottieFonts::class.java.getResourceAsStream(resource)?.let {
                SlideFontRegistry.registerFontStream(it)
            }
        }
        SlideFontRegistry.initialize()
        SystemFonts.families()
    }.apply { isDaemon = true }.start()

    vlcCustomPath = startupSettings.projectionSettings.vlcPath

    LottieRenderCache.ensureForFolder(
        startupSettings.streamingSettings.lowerThirdFolder,
        startupSettings.atemSettings
    )

    application(exitProcessOnExit = true) {
        ChurchPresenterApp(coroutineExceptionHandler)
    }
}


/**
 * The whole desktop UI: windows, presenter outputs, the server wiring and every dialog.
 *
 * Split out of [main] so that function is only the pre-UI startup — settings, crash reporting,
 * fonts, VLC and the single-instance lock — and this is the Compose tree.
 */
@Composable
private fun ApplicationScope.ChurchPresenterApp(coroutineExceptionHandler: CoroutineExceptionHandler) {
    var appReady by remember { mutableStateOf(false) }
    val settingsManager = remember { SettingsManager() }
    val statisticsManager = remember { StatisticsManager() }
    val verseSequenceLog = remember { VerseSequenceLog() }
    var appSettings by remember {
        mutableStateOf(settingsManager.loadSettings().let {
            it.copy(presentationRemoteSettings = it.presentationRemoteSettings.copy(remoteControlEnabled = false))
        })
    }

    remember {
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
        val assignments = reconcileScreenAssignments(proj.screenAssignments, nonPrimaryDisplays, deckLinkCount)
        if (assignments != null) {
            appSettings = appSettings.copy(
                projectionSettings = proj.copy(screenAssignments = assignments)
            )
            settingsManager.saveSettings(appSettings)
        }
    }

    val presenterManager = remember { PresenterManager() }
    LaunchedEffect(appSettings.atemSettings) {
        presenterManager.setAtemRenderSettings(appSettings.atemSettings)
    }

    var eulaAccepted by remember {
        mutableStateOf(isEulaAccepted(appSettings.eulaAcceptedVersion, CURRENT_EULA_VERSION))
    }
    var showSetupWizard by remember { mutableStateOf(shouldShowSetupWizard(appSettings)) }

    var currentLanguage by remember {
        val savedLanguageCode = appSettings.language
        val language = resolveStartupLanguage(savedLanguageCode)
        Locale.setDefault(Locale.forLanguageTag(language.code))
        mutableStateOf(language)
    }

    var scheduleActions by remember { mutableStateOf(ScheduleActions()) }
    val currentScheduleActions by rememberUpdatedState(scheduleActions)

    val mediaViewModel = remember { MediaViewModel() }

    var identifyingScreen by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope { coroutineExceptionHandler }

    var theme by remember { mutableStateOf(themeFromSettings(appSettings.theme)) }
    val companionServer = remember { CompanionServer() }
    val qaManager = remember { QAManager() }
    val sttManager = remember { STTManager() }
    LaunchedEffect(appSettings.bibleEngineSettings.helpDevMode) {
        sttManager.helpDevModeEnabled = appSettings.bibleEngineSettings.helpDevMode
    }
    val obsManager = remember { OBSWebSocketManager() }
    val companionSatelliteViewModel = remember { CompanionSatelliteViewModel() }
    DisposableEffect(Unit) { onDispose { companionSatelliteViewModel.dispose() } }
    val autoConnectedIds = remember { mutableSetOf<String>() }
    LaunchedEffect(appSettings.companionSatelliteConnections.map { it.id }) {
        for (connection in appSettings.companionSatelliteConnections) {
            if (connection.autoConnect && autoConnectedIds.add(connection.id)) {
                val effective = if (needsGeneratedDeviceId(connection)) {
                    val generated = java.util.UUID.randomUUID().toString()
                    appSettings = appSettings.copy(
                        companionSatelliteConnections = withGeneratedDeviceId(
                            appSettings.companionSatelliteConnections, connection.id, generated,
                        )
                    )
                    settingsManager.saveSettings(appSettings)
                    connection.copy(deviceId = generated)
                } else connection
                companionSatelliteViewModel.connectAll(effective)
            }
        }
    }
    val lastReconciled = remember { mutableMapOf<String, CompanionSatelliteSettings>() }
    CompanionSatelliteWiring(appSettings, companionSatelliteViewModel, lastReconciled)

    val instanceLinkViewModel = remember { InstanceLinkViewModel() }
    DisposableEffect(Unit) { onDispose { instanceLinkViewModel.dispose() } }
    var primaryBibleForInstanceLink by remember { mutableStateOf<Bible?>(null) }
    var scenesForInstanceLink by remember { mutableStateOf<List<Scene>>(emptyList()) }
    fun setInstanceLinkEnabled(enabled: Boolean) {
        if (!instanceLinkEnabledChanged(appSettings.instanceLink, enabled)) return
        appSettings = appSettings.copy(instanceLink = appSettings.instanceLink.copy(enabled = enabled))
        settingsManager.saveSettings(appSettings)
    }
    LaunchedEffect(
        appSettings.instanceLink.enabled,
        appSettings.instanceLink.autoConnect,
        appSettings.instanceLink.primaryHost,
        appSettings.instanceLink.primaryPort,
        appSettings.instanceLink.apiKey,
        appSettings.instanceLink.reconnectDelayMs
    ) {
        val link = appSettings.instanceLink
        if (shouldAutoConnectInstanceLink(link)) {
            instanceLinkViewModel.connect(
                link.primaryHost, link.primaryPort, link.apiKey, link.deviceId,
                link.reconnectDelayMs.toLong()
            )
        } else if (shouldDisconnectInstanceLink(link)) {
            instanceLinkViewModel.disconnect()
        }
    }
    LaunchedEffect(instanceLinkViewModel, appSettings.instanceLink.role) {
        if (!shouldMirrorRemoteOutput(appSettings.instanceLink.role)) return@LaunchedEffect
        instanceLinkViewModel.remoteLiveState.collectLatest { state ->
            if (state == null) return@collectLatest
            applyRemoteLiveState(
                state, presenterManager, instanceLinkViewModel,
                bibleSyncMode = appSettings.instanceLink.bibleSyncMode,
                localPrimaryBible = primaryBibleForInstanceLink,
                localScenes = scenesForInstanceLink,
                onPlayRemoteMedia = { url, type ->
                    mediaViewModel.loadMedia(url, type)
                    mediaViewModel.play()
                }
            )
        }
    }
    LaunchedEffect(instanceLinkViewModel, appSettings.instanceLink.role) {
        if (!shouldMirrorRemoteOutput(appSettings.instanceLink.role)) return@LaunchedEffect
        instanceLinkViewModel.remotePresentationSlide.collectLatest { slide ->
            if (slide == null) return@collectLatest
            if (!hasFetchableSlide(slide.id)) return@collectLatest
            val bytes = instanceLinkViewModel.fetchPresentationSlideBytes(slide.id, slide.index)
            if (bytes == null) {
                InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                    mapOf("contentType" to "PRESENTATION", "resolved" to false, "reason" to "fetch_failed")
                )
                return@collectLatest
            }
            val bitmap = runCatching {
                Image.makeFromEncoded(bytes).toComposeImageBitmap()
            }.getOrNull()
            if (bitmap == null) {
                InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                    mapOf("contentType" to "PRESENTATION", "resolved" to false, "reason" to "decode_failed")
                )
                return@collectLatest
            }
            presenterManager.setSelectedSlide(bitmap)
            if (slide.isLive) {
                presenterManager.setPresentingMode(Presenting.PRESENTATION)
                presenterManager.setShowPresenterWindow(true)
            }
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                mapOf("contentType" to "PRESENTATION", "resolved" to true, "isLive" to slide.isLive)
            )
        }
    }
    LaunchedEffect(instanceLinkViewModel, appSettings.instanceLink.role) {
        if (!shouldMirrorRemoteOutput(appSettings.instanceLink.role)) return@LaunchedEffect
        var lastSeen = instanceLinkViewModel.displayClearedSignal.value
        instanceLinkViewModel.displayClearedSignal.collect { signal ->
            if (!isFreshClearSignal(signal, lastSeen)) return@collect
            lastSeen = signal
            presenterManager.requestClearDisplay()
        }
    }
    val instanceLinkCommandFailures = remember { mutableStateListOf<InstanceLinkCommandFailure>() }
    InstanceLinkFailureWiring(instanceLinkViewModel, instanceLinkCommandFailures)
    var mirroredBackgroundSettings by remember { mutableStateOf<BackgroundSettings?>(null) }
    val instanceLinkConnectionStatusForBackgrounds by instanceLinkViewModel.connectionStatus.collectAsState()
    val instanceLinkBackgroundsSignal by instanceLinkViewModel.backgroundsUpdatedSignal.collectAsState()
    LaunchedEffect(
        instanceLinkConnectionStatusForBackgrounds,
        appSettings.instanceLink.mirrorBackgrounds,
        appSettings.instanceLink.role,
        instanceLinkBackgroundsSignal
    ) {
        if (!shouldMirrorRemoteBackgrounds(
                status = instanceLinkConnectionStatusForBackgrounds,
                role = appSettings.instanceLink.role,
                mirrorBackgrounds = appSettings.instanceLink.mirrorBackgrounds
            )
        ) {
            mirroredBackgroundSettings = null
            return@LaunchedEffect
        }
        if (shouldInvalidateBackgroundCache(instanceLinkBackgroundsSignal)) {
            withContext(Dispatchers.IO) {
                instanceLinkBackgroundCacheDir.listFiles()?.forEach { it.delete() }
            }
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "cache_invalidated",
                mapOf("kind" to "backgrounds", "trigger" to "backgrounds_updated")
            )
        }
        val remote = instanceLinkViewModel.fetchBackgroundSettings() ?: return@LaunchedEffect
        mirroredBackgroundSettings = downloadMirroredBackgroundSettings(remote, instanceLinkViewModel)
    }
    val effectiveAppSettings = remember(appSettings, mirroredBackgroundSettings) {
        withMirroredBackgrounds(appSettings, mirroredBackgroundSettings)
    }
    val screenCountForUsage = remember { LiveMapReporter.detectScreenCount() }
    val deckLinkCountForUsage = remember {
        deckLinkOutputCount(DeckLinkManager.isAvailable()) { DeckLinkManager.listDevices().size }
    }

    LiveStateBroadcastWiring(
        appSettings = { appSettings },
        primaryBible = { primaryBibleForInstanceLink },
        presenterManager = presenterManager,
        companionServer = companionServer,
        screenCountForUsage = screenCountForUsage,
        deckLinkCountForUsage = deckLinkCountForUsage,
    )
    remember(qaManager) { companionServer.qaManager = qaManager; true }
    ObsSceneWiring(appSettings, companionServer, obsManager, presenterManager)
    val tunnelStatus by companionServer.tunnelManager.status.collectAsState()
    val tunnelUrl by companionServer.tunnelManager.tunnelUrl.collectAsState()
    val prevTunnelWasConnected = remember { mutableStateOf(false) }
    var qaDisplayUrl by remember { mutableStateOf("") }
    var presentationDisplayUrl by remember { mutableStateOf("") }
    LaunchedEffect(tunnelStatus) {
        val isConnected = isTunnelConnected(tunnelStatus)
        if (tunnelJustDropped(prevTunnelWasConnected.value, isConnected)) {
            companionServer.clearPresentationState()
            qaDisplayUrl = ""
            presentationDisplayUrl = ""
        }
        prevTunnelWasConnected.value = isConnected
    }
    var presentationFrozen by remember { mutableStateOf(false) }
    LaunchedEffect(
        appSettings.presentationRemoteSettings.remoteControlEnabled,
        appSettings.serverSettings.apiKeyEnabled,
        appSettings.serverSettings.apiKey
    ) {
        val activeApiKey = activeApiKey(appSettings.serverSettings)
        companionServer.updatePresentationRemoteSettings(appSettings.presentationRemoteSettings, activeApiKey)
    }
    LaunchedEffect(appSettings.presentationSettings.autoScrollInterval) {
        companionServer.updateAutoScrollInterval(appSettings.presentationSettings.autoScrollInterval.toInt())
    }
    LaunchedEffect(appSettings.presentationSettings.isLooping) {
        companionServer.updateLoopingState(appSettings.presentationSettings.isLooping)
    }
    LaunchedEffect(Unit) {
        companionServer.onPresentationFreezeToggle.collect {
            presentationFrozen = !presentationFrozen
            companionServer.broadcastFreezeChange(presentationFrozen)
            presenterManager.setSlideFrozen(presentationFrozen)
        }
    }
    MediaRemoteWiring(companionServer, mediaViewModel, presenterManager)
    val presentingModeValue = presenterManager.presentingMode.value
    LiveStatusWiring(appSettings, companionServer, presentingModeValue)
    val browserSourceServerUrlState = companionServer.serverUrl.collectAsState()
    appSettings.projectionSettings.browserSourceOutputs.indices.forEach { i ->
        composeKey(i) {
            val appSettingsState = rememberUpdatedState(effectiveAppSettings)
            val screenAssignmentState = rememberUpdatedState(
                browserSourceOutputAt(appSettings.projectionSettings.browserSourceOutputs, i)
            )
            val effectiveModeState = remember {
                derivedStateOf {
                    effectiveOutputMode(
                        presenterManager.browserSourceLocks.value, i, presenterManager.presentingMode.value,
                    )
                }
            }
            val qaDisplayUrlState = rememberUpdatedState(qaDisplayUrl)
            val bsOutput = browserSourceOutputAt(appSettings.projectionSettings.browserSourceOutputs, i)
            val renderer = remember(
                i,
                bsOutput.browserSourceWidth,
                bsOutput.browserSourceHeight,
                bsOutput.browserSourceFps
            ) {
                BrowserSourceVideoRenderer(
                    presenterManager, appSettingsState, screenAssignmentState, effectiveModeState,
                    outputIndex = i,
                    sttManager = sttManager,
                    mediaViewModel = mediaViewModel,
                    qaDisplayUrlState = qaDisplayUrlState,
                    serverUrlState = browserSourceServerUrlState,
                    width = bsOutput.browserSourceWidth,
                    height = bsOutput.browserSourceHeight,
                    fps = bsOutput.browserSourceFps,
                )
            }
            LaunchedEffect(renderer) {
                renderer.start(this)
                companionServer.registerBrowserSourceFrames(i, renderer.frames)
            }
            DisposableEffect(renderer) {
                onDispose { renderer.stop() }
            }
        }
    }
    LaunchedEffect(Unit) {
        companionServer.onPresentationGoLive.collect {
            presenterManager.setPresentingMode(Presenting.PRESENTATION)
            presenterManager.setShowPresenterWindow(true)
        }
    }
    val remoteSelectSongFlow =
        remember { kotlinx.coroutines.flow.MutableSharedFlow<ScheduleItem.SongItem>(extraBufferCapacity = 8) }
    val remoteSelectPictureFlow =
        remember { kotlinx.coroutines.flow.MutableSharedFlow<ScheduleItem.PictureItem>(extraBufferCapacity = 8) }
    val remoteSelectPresentationFlow =
        remember { kotlinx.coroutines.flow.MutableSharedFlow<ScheduleItem.PresentationItem>(extraBufferCapacity = 8) }
    var dialogDismissSignal by remember { mutableStateOf(0) }
    var showOptionsDialog by remember { mutableStateOf(false) }
    var optionsDialogInitialTab by remember { mutableStateOf(0) }
    val openOptionsDialog: (Int) -> Unit = { tab ->
        optionsDialogInitialTab = tab
        showOptionsDialog = true
    }
    var showStatisticsDialog by remember { mutableStateOf(false) }
    var showInstanceLinkDialog by remember { mutableStateOf(false) }
    var showKeyboardShortcutsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showContactDialog by remember { mutableStateOf(false) }
    var contactDialogInitialType by remember { mutableStateOf<String?>(null) }
    var showStoryPrompt by remember { mutableStateOf(false) }
    var showConverterWindow by remember { mutableStateOf(false) }
    var showSongLibraryWindow by remember { mutableStateOf(false) }
    var showLottieGenWindow by remember { mutableStateOf(false) }
    var showStyleEditorWindow by remember { mutableStateOf(false) }
    var showMemoryMonitorWindow by remember { mutableStateOf(false) }
    var developerMenuUnlocked by remember { mutableStateOf(false) }
    var lottieGenOutputDir by remember { mutableStateOf<File?>(null) }
    var lottieGenOnFileSaved by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingUpdateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var pendingUpdateCheckWasManual by remember { mutableStateOf(false) }
    var selectedScheduleItemId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            companionServer.preloadData(
                songStorageDir = appSettings.songSettings.storageDirectory,
                bibleStorageDir = appSettings.bibleSettings.storageDirectory,
                primaryBibleFileName = appSettings.bibleSettings.primaryBible
            )
            companionServer.updateApiKey(
                enabled = appSettings.serverSettings.apiKeyEnabled,
                key = appSettings.serverSettings.apiKey
            )
            companionServer.updateFileUploadEnabled(appSettings.serverSettings.fileUploadEnabled)
            companionServer.updateMaxMediaUploadMb(appSettings.serverSettings.maxMediaUploadMb)
            companionServer.updateAtemConfig(
                appSettings.atemSettings,
                appSettings.streamingSettings.lowerThirdFolder
            )
            if (appSettings.serverSettings.enabled) {
                companionServer.start(
                    port = appSettings.serverSettings.port,
                    hostOverride = appSettings.serverSettings.serverHost
                )
            }
        }
        appReady = true
        val isFirstEverUpdateCheck = isFirstEverUpdateCheck(appSettings.lastUpdateCheckTimestamp)
        if (appSettings.updateCheckInterval.isDueSince(appSettings.lastUpdateCheckTimestamp)) {
            val result = UpdateChecker.checkForUpdate(includePrereleases = appSettings.participateInPrereleases)
            appSettings = appSettings.copy(lastUpdateCheckTimestamp = System.currentTimeMillis())
            settingsManager.saveSettings(appSettings)
            if (isFirstEverUpdateCheck) {
                pendingUpdateResult = result
                pendingUpdateCheckWasManual = true
            } else if (result is UpdateCheckResult.Available) {
                pendingUpdateResult = result
                pendingUpdateCheckWasManual = false
            }
        }

        val now = System.currentTimeMillis()
        val storyPrompt = appSettings.storyPrompt.stampingInstall(now).recordingUse(now)
        appSettings = appSettings.copy(storyPrompt = storyPrompt)
        settingsManager.saveSettings(appSettings)
        if (storyPrompt.isDue(now) && pendingUpdateResult == null) {
            delay(STORY_PROMPT_DELAY_MS)
            appSettings = appSettings.copy(storyPrompt = storyPrompt.shown(System.currentTimeMillis()))
            settingsManager.saveSettings(appSettings)
            showStoryPrompt = true
        }
    }

    val screens = rememberScreenDevices()
    val savedPlacement = windowPlacementFromSettings(appSettings.windowPlacement)
    val primaryBounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
        .defaultScreenDevice.defaultConfiguration.bounds
    val isFloating = savedPlacement == WindowPlacement.Floating
    val (startX, startY) = startupWindowPosition(
        restoreGeometry = shouldRestoreWindowGeometry(isFloating, appSettings.windowX),
        savedX = appSettings.windowX, savedY = appSettings.windowY,
        primaryX = primaryBounds.x, primaryY = primaryBounds.y,
    )
    val (startWidth, startHeight) = startupWindowSize(
        isFloating = isFloating,
        savedWidth = appSettings.windowWidth, savedHeight = appSettings.windowHeight,
        primaryWidth = primaryBounds.width, primaryHeight = primaryBounds.height,
    )
    val state = rememberWindowState(
        placement = savedPlacement,
        position = WindowPosition(startX.dp, startY.dp),
        size = DpSize(startWidth.dp, startHeight.dp),
    )

    if (!appReady) {
        SplashWindow(theme = theme)
    }

    if (appReady && eulaAccepted) {
        Window(
            onCloseRequest = {
                appSettings = appSettings.withWindowGeometry(
                    placement = windowPlacementToSettings(state.placement),
                    isFloating = state.placement == WindowPlacement.Floating,
                    width = state.size.width.value.toInt(),
                    height = state.size.height.value.toInt(),
                    x = state.position.x.value.toInt(),
                    y = state.position.y.value.toInt(),
                )
                settingsManager.saveSettings(appSettings)
                if (qaManager.sessionActive) qaManager.toggleSession()
                companionServer.clearPresentationState()
                companionServer.tunnelManager.shutdown()
                exitApplication()
            },
            title = stringResource(Res.string.app_name),
            icon = painterResource(Res.drawable.ic_app_icon),
            state = state
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(MIN_MAIN_WINDOW_WIDTH, MIN_MAIN_WINDOW_HEIGHT)
            }
            MacMenuBarActivationFix()
            LanguageProvider(language = currentLanguage) {
                AppThemeWrapper(theme = theme) {
                    CompositionLocalProvider(
                        LocalMediaViewModel provides mediaViewModel,
                        LocalMainWindowState provides state,
                        LocalShortcuts provides remember(appSettings.keyboardShortcutSettings) {
                            ShortcutMap.from(appSettings.keyboardShortcutSettings)
                        }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {

                            val remoteEventQueue =
                                remember { mutableStateListOf<Triple<RemoteEvent, () -> Unit, () -> Unit>>() }

                            val remoteClientManager = remember { RemoteClientManager() }
                            val sessionAllowedClients =
                                remember { mutableStateListOf<String>() }
                            val sessionBlockedClients =
                                remember { mutableStateListOf<String>() }
                            val remoteActivityNotifications =
                                remember { mutableStateListOf<RemoteActivityNotification>() }

                            LaunchedEffect(remoteClientManager.blockedClients, sessionBlockedClients.toList()) {
                                companionServer.blockedClientIds =
                                    remoteClientManager.blockedClients + sessionBlockedClients
                            }

                            LaunchedEffect(Unit) {
                                companionServer.onAddToSchedule.collect { pending ->
                                    val clientId = pending.clientId
                                    val access = remoteAccessDecision(
                                        clientId,
                                        remoteClientManager.allowedClients, remoteClientManager.blockedClients,
                                        sessionAllowedClients, sessionBlockedClients,
                                    )
                                    val item = pending.item
                                    val add: () -> Unit = {
                                        addScheduleItem(item, currentScheduleActions) { song ->
                                            coroutineScope.launch { remoteSelectSongFlow.emit(song) }
                                        }
                                        pending.decision.complete(true)
                                    }
                                    val (eTitle, eDetail) = remoteEventLabel(item)
                                    when (val outcome = remoteApproval(
                                        access,
                                        type = RemoteEventType.ADD_TO_SCHEDULE,
                                        title = eTitle,
                                        detail = eDetail,
                                        clientId = clientId,
                                        clientLabel = remoteClientManager.getLabel(clientId),
                                    )) {
                                        RemoteApproval.Reject -> pending.decision.complete(false)
                                        is RemoteApproval.Approve -> {
                                            add()
                                            remoteActivityNotifications.add(outcome.notification)
                                        }
                                        is RemoteApproval.Ask -> remoteEventQueue.add(Triple(
                                            outcome.event, add, { pending.decision.complete(false) },
                                        ))
                                    }
                                }
                            }

                            LaunchedEffect(Unit) {
                                companionServer.onRemoveFromSchedule.collect { pending ->
                                    val clientId = pending.clientId
                                    val access = remoteAccessDecision(
                                        clientId,
                                        remoteClientManager.allowedClients, remoteClientManager.blockedClients,
                                        sessionAllowedClients, sessionBlockedClients,
                                    )
                                    val remove: () -> Unit = {
                                        currentScheduleActions.removeById(pending.id)
                                        pending.decision.complete(true)
                                    }
                                    when (val outcome = remoteApproval(
                                        access,
                                        type = RemoteEventType.REMOVE_FROM_SCHEDULE,
                                        title = pending.label,
                                        clientId = clientId,
                                        clientLabel = remoteClientManager.getLabel(clientId),
                                    )) {
                                        RemoteApproval.Reject -> pending.decision.complete(false)
                                        is RemoteApproval.Approve -> {
                                            remove()
                                            remoteActivityNotifications.add(outcome.notification)
                                        }
                                        is RemoteApproval.Ask -> remoteEventQueue.add(Triple(
                                            outcome.event, remove, { pending.decision.complete(false) },
                                        ))
                                    }
                                }
                            }

                            LaunchedEffect(Unit) {
                                companionServer.onAddBatchToSchedule.collect { pending ->
                                    val clientId = pending.clientId
                                    val access = remoteAccessDecision(
                                        clientId,
                                        remoteClientManager.allowedClients, remoteClientManager.blockedClients,
                                        sessionAllowedClients, sessionBlockedClients,
                                    )
                                    val addAll: () -> Unit = {
                                        for (item in pending.items) {
                                            addScheduleItem(item, currentScheduleActions) { song ->
                                                coroutineScope.launch { remoteSelectSongFlow.emit(song) }
                                            }
                                        }
                                        pending.decision.complete(true)
                                    }
                                    val (batchTitle, batchDetail) = batchEventSummary(pending.items)
                                    when (val outcome = remoteApproval(
                                        access,
                                        type = RemoteEventType.ADD_TO_SCHEDULE,
                                        title = batchTitle,
                                        detail = batchDetail,
                                        clientId = clientId,
                                        clientLabel = remoteClientManager.getLabel(clientId),
                                    )) {
                                        RemoteApproval.Reject -> pending.decision.complete(false)
                                        is RemoteApproval.Approve -> {
                                            addAll()
                                            remoteActivityNotifications.add(outcome.notification)
                                        }
                                        is RemoteApproval.Ask -> remoteEventQueue.add(Triple(
                                            outcome.event, addAll, { pending.decision.complete(false) },
                                        ))
                                    }
                                }
                            }

                            LaunchedEffect(Unit) {
                                companionServer.onProject.collect { pending ->
                                    val clientId = pending.clientId
                                    val access = remoteAccessDecision(
                                        clientId,
                                        remoteClientManager.allowedClients, remoteClientManager.blockedClients,
                                        sessionAllowedClients, sessionBlockedClients,
                                    )
                                    val item = pending.item
                                    val project: () -> Unit = {
                                        if (item is ScheduleItem.AnnouncementItem) {
                                            appSettings = appSettings.withAnnouncement(item)
                                        }
                                        executeProjectItem(
                                            item,
                                            currentScheduleActions,
                                            presenterManager,
                                            statisticsManager
                                        )
                                        coroutineScope.launch {
                                            emitRemoteTabSelection(
                                                item, remoteSelectSongFlow,
                                                remoteSelectPictureFlow, remoteSelectPresentationFlow,
                                            )
                                        }
                                        pending.decision.complete(true)
                                    }
                                    val (pTitle, pDetail) = remoteEventLabel(item)
                                    when (val outcome = remoteApproval(
                                        access,
                                        type = RemoteEventType.PROJECT,
                                        title = pTitle,
                                        detail = pDetail,
                                        clientId = clientId,
                                        clientLabel = remoteClientManager.getLabel(clientId),
                                    )) {
                                        RemoteApproval.Reject -> pending.decision.complete(false)
                                        is RemoteApproval.Approve -> {
                                            project()
                                            remoteActivityNotifications.add(outcome.notification)
                                        }
                                        is RemoteApproval.Ask -> remoteEventQueue.add(Triple(
                                            outcome.event, project, { pending.decision.complete(false) },
                                        ))
                                    }
                                }
                            }

                            LaunchedEffect(Unit) {
                                companionServer.onSelectSongSection.collect { req ->
                                    val sections = presenterManager.allLyricSections.value
                                    val section = sections.getOrNull(req.section) ?: return@collect
                                    presenterManager.setLyricSection(section)
                                    presenterManager.setSongDisplaySectionIndex(req.section)
                                    presenterManager.setSongDisplayLineIndex(remoteSongLineIndex(req.lineIndex))
                                    if (shouldSwitchToLyrics(presenterManager.presentingMode.value)) {
                                        presenterManager.setPresentingMode(Presenting.LYRICS)
                                        presenterManager.setShowPresenterWindow(true)
                                    }
                                }
                            }

                            LaunchedEffect(Unit) {
                                companionServer.onClear.collect {
                                    mediaViewModel.pause()
                                    presenterManager.requestClearDisplay()
                                }
                            }

                            LaunchedEffect(Unit) {
                                LowerThirdSequencer.onShow.collect { req ->
                                    presenterManager.setLottieContent(
                                        req.json, req.pauseAtFrame, req.pauseFrame, req.pauseDurationMs, req.name
                                    )
                                    presenterManager.setPresentingMode(Presenting.LOWER_THIRD)
                                    presenterManager.setShowPresenterWindow(true)
                                }
                            }
                            LaunchedEffect(Unit) {
                                LowerThirdSequencer.onClear.collect {
                                    if (shouldClearAfterLowerThird(presenterManager.presentingMode.value)) {
                                        presenterManager.requestClearDisplay()
                                    }
                                }
                            }
                            LaunchedEffect(Unit) {
                                companionServer.onQADisplay.collect { question ->
                                    if (question != null) {
                                        presenterManager.setDisplayedQuestion(question)
                                        presenterManager.setShowQRCodeOnDisplay(false)
                                        presenterManager.setPresentingMode(Presenting.QA)
                                    } else {
                                        presenterManager.setDisplayedQuestion(null)
                                        presenterManager.setPresentingMode(Presenting.NONE)
                                    }
                                }
                            }

                            LaunchedEffect(Unit) {
                                companionServer.onQAAdminRequest.collect { pending ->
                                    val clientId = pending.clientId
                                    val access = remoteAccessDecision(
                                        clientId,
                                        remoteClientManager.allowedClients, remoteClientManager.blockedClients,
                                        sessionAllowedClients, sessionBlockedClients,
                                    )
                                    when (val outcome = remoteApproval(
                                        access,
                                        type = qaActionType(pending.action),
                                        title = remoteEventTitle(pending.text),
                                        clientId = clientId,
                                        clientLabel = remoteClientManager.getLabel(clientId),
                                    )) {
                                        RemoteApproval.Reject -> pending.decision.complete(false)
                                        is RemoteApproval.Approve -> {
                                            pending.decision.complete(true)
                                            remoteActivityNotifications.add(outcome.notification)
                                        }
                                        is RemoteApproval.Ask -> remoteEventQueue.add(Triple(
                                            outcome.event,
                                            { pending.decision.complete(true) },
                                            { pending.decision.complete(false) },
                                        ))
                                    }
                                }
                            }

                            LaunchedEffect(Unit) {
                                companionServer.onPresentationRemoteConnect.collect { pending ->
                                    val clientId = pending.clientId
                                    val access = remoteAccessDecision(
                                        clientId,
                                        remoteClientManager.allowedClients, remoteClientManager.blockedClients,
                                        sessionAllowedClients, sessionBlockedClients,
                                    )
                                    when (val outcome = remoteApproval(
                                        access,
                                        type = RemoteEventType.PRESENTATION_CONNECT,
                                        title = "",
                                        clientId = clientId,
                                        clientLabel = remoteClientManager.getLabel(clientId),
                                    )) {
                                        RemoteApproval.Reject -> pending.decision.complete(false)
                                        is RemoteApproval.Approve -> {
                                            pending.decision.complete(true)
                                            remoteActivityNotifications.add(outcome.notification)
                                        }
                                        is RemoteApproval.Ask -> remoteEventQueue.add(Triple(
                                            outcome.event,
                                            { pending.decision.complete(true) },
                                            { pending.decision.complete(false) },
                                        ))
                                    }
                                }
                            }

                            LaunchedEffect(Unit) {
                                companionServer.onQaAdminConnect.collect { pending ->
                                    val clientId = pending.clientId
                                    val access = remoteAccessDecision(
                                        clientId,
                                        remoteClientManager.allowedClients, remoteClientManager.blockedClients,
                                        sessionAllowedClients, sessionBlockedClients,
                                    )
                                    when (val outcome = remoteApproval(
                                        access,
                                        type = RemoteEventType.QA_ADMIN_CONNECT,
                                        title = "",
                                        clientId = clientId,
                                        clientLabel = remoteClientManager.getLabel(clientId),
                                    )) {
                                        RemoteApproval.Reject -> pending.decision.complete(false)
                                        is RemoteApproval.Approve -> {
                                            pending.decision.complete(true)
                                            remoteActivityNotifications.add(outcome.notification)
                                        }
                                        is RemoteApproval.Ask -> remoteEventQueue.add(Triple(
                                            outcome.event,
                                            { pending.decision.complete(true) },
                                            { pending.decision.complete(false) },
                                        ))
                                    }
                                }
                            }

                            LaunchedEffect(Unit) {
                                companionServer.onBibleHold.collect { hold ->
                                    presenterManager.setBibleHold(hold)
                                }
                            }

                            LaunchedEffect(Unit) {
                                snapshotFlow { presenterManager.presentingMode.value }
                                    .collect { mode ->
                                        if (shouldBroadcastDisplayCleared(mode)) {
                                            companionServer.broadcastDisplayCleared()
                                        }
                                    }
                            }

                            LaunchedEffect(Unit) {
                                snapshotFlow { presenterManager.songDisplaySectionIndex.value }
                                    .collect { index ->
                                        if (shouldBroadcastSongSection(presenterManager.presentingMode.value)) {
                                            companionServer.broadcastSongSectionSelected(index)
                                        }
                                    }
                            }

                            LaunchedEffect(Unit) {
                                companionServer.onInstantAction.collect { action ->
                                    val type = remoteActionType(action.actionType)
                                    remoteActivityNotifications.add(
                                        RemoteActivityNotification(
                                            type = type,
                                            title = action.title,
                                            detail = action.detail,
                                            clientId = action.clientId,
                                            clientLabel = remoteClientManager.getLabel(action.clientId)
                                        )
                                    )
                                }
                            }

                            NavigationTopBar(
                                currentTheme = theme,
                                onAbout = { showAboutDialog = true },
                                onContactUs = { showContactDialog = true },
                                onGettingStarted = { showSetupWizard = true },
                                onStatistics = { showStatisticsDialog = true },
                                onConnectToInstance = { showInstanceLinkDialog = true },
                                onDisconnectInstance = { instanceLinkViewModel.disconnect() },
                                isInstanceLinkConnected = isInstanceLinkActive(
                                    instanceLinkViewModel.connectionStatus.collectAsState().value
                                ),
                                onConverter = { showConverterWindow = true },
                                onSongLibrary = { showSongLibraryWindow = true },
                                onHelp = {
                                    Desktop.getDesktop()
                                        .browse(URI("https://churchpresenter.org/wiki"))
                                },
                                onHowToBlog = {
                                    Desktop.getDesktop()
                                        .browse(URI("https://churchpresenter.org/blog"))
                                },
                                onCheckForUpdates = {
                                    coroutineScope.launch {
                                        pendingUpdateResult = UpdateChecker.checkForUpdate(
                                            includePrereleases = appSettings.participateInPrereleases
                                        )
                                        pendingUpdateCheckWasManual = true
                                        appSettings = appSettings.copy(
                                            lastUpdateCheckTimestamp = System.currentTimeMillis()
                                        )
                                        settingsManager.saveSettings(appSettings)
                                    }
                                },
                                onKeyboardShortcuts = { showKeyboardShortcutsDialog = true },
                                theme = {
                                    appSettings = appSettings.copy(theme = it.toString())
                                    theme = it
                                    settingsManager.saveSettings(appSettings)
                                },
                                onLanguageChange = { language ->
                                    currentLanguage = language
                                    appSettings = appSettings.copy(language = language.code)
                                    settingsManager.saveSettings(appSettings)
                                    Locale.setDefault(Locale.forLanguageTag(language.code))
                                },
                                onSettings = { openOptionsDialog(0) },
                                onExit = { exitApplication() },
                                onAddToSchedule = { },
                                onNewSchedule = { currentScheduleActions.newSchedule() },
                                onOpenSchedule = { currentScheduleActions.openSchedule() },
                                onSaveSchedule = { currentScheduleActions.saveSchedule() },
                                onSaveScheduleAs = { currentScheduleActions.saveScheduleAs() },
                                onCloseSchedule = { currentScheduleActions.newSchedule() },
                                onRemoveFromSchedule = {
                                    selectedScheduleItemId?.let {
                                        currentScheduleActions.removeSelected()
                                        selectedScheduleItemId = null
                                    }
                                },
                                onClearSchedule = {
                                    currentScheduleActions.clearSchedule()
                                    selectedScheduleItemId = null
                                },
                                showDeveloperMenu = shouldShowDeveloperMenu(
                                    BuildConfig.IS_RELEASE, DevFlags.forceDevWindow, developerMenuUnlocked,
                                ),
                                isPresenterWindowVisible = presenterManager.showPresenterWindow.value,
                                onSetPresenterWindowVisible = { presenterManager.setShowPresenterWindow(it) },
                                isDevWindowAlwaysOnTop = presenterManager.devWindowAlwaysOnTop.value,
                                onSetDevWindowAlwaysOnTop = { presenterManager.setDevWindowAlwaysOnTop(it) },
                                onOpenStyleEditor = { showStyleEditorWindow = true },
                                onOpenMemoryMonitor = { showMemoryMonitorWindow = true },
                                onOpenStoryPrompt = { showStoryPrompt = true },
                            )
                            if (CrashReporter.didCrashLastRun && CrashReporter.videoBackgroundsDisabled) {
                                var showBanner by remember { mutableStateOf(true) }
                                if (showBanner) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(
                                            MaterialTheme.colorScheme.errorContainer
                                        ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text =
                                                "Video backgrounds disabled after ${CrashReporter.consecutiveCrashes}" +
                                                    " consecutive crashes.  [Re-enable]  [Dismiss]",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.onPreviewKeyEvent {
                                                showBanner = false; true
                                            }
                                        )
                                    }
                                    LaunchedEffect(Unit) {
                                        delay(CRASH_REPORT_RETRY_MS)
                                        showBanner = false
                                    }
                                }
                            }

                            val instanceLinkStatus = instanceLinkViewModel.connectionStatus.collectAsState().value
                            val instanceLinkIsControllerConnected =
                                isControllerConnected(instanceLinkStatus, appSettings.instanceLink.role)
                            val instanceLinkUsesRemoteContent =
                                shouldUseRemoteContent(instanceLinkStatus, appSettings.instanceLink.role)
                            MainDesktop(
                                hostWindow = window,
                                instanceLinkConnectionStatus =
                                    instanceLinkViewModel.connectionStatus.collectAsState().value,
                                instanceLinkNextRetryAtMs = instanceLinkViewModel.nextRetryAtMs.collectAsState().value,
                                instanceLinkBibleUpdatedSignal =
                                    instanceLinkViewModel.bibleUpdatedSignal.collectAsState().value,
                                instanceLinkSecondaryBibleUpdatedSignal =
                                    instanceLinkViewModel.secondaryBibleUpdatedSignal.collectAsState().value,
                                instanceLinkFollowingHost = appSettings.instanceLink.primaryHost,
                                connectedInstanceLinkFollowerCount =
                                    companionServer.connectedInstanceLinkFollowers.collectAsState().value.size,
                                onInstanceLinkConnect = {
                                    val link = appSettings.instanceLink
                                    setInstanceLinkEnabled(true)
                                    instanceLinkViewModel.connect(
                                        link.primaryHost, link.primaryPort, link.apiKey, link.deviceId,
                                        link.reconnectDelayMs.toLong()
                                    )
                                },
                                onInstanceLinkDisconnect = {
                                    setInstanceLinkEnabled(false)
                                    instanceLinkViewModel.disconnect()
                                },
                                instanceLinkRemoteSchedule =
                                    instanceLinkViewModel.remoteSchedule.collectAsState().value,
                                instanceLinkRemoteSongCatalog =
                                    instanceLinkViewModel.remoteSongCatalog.collectAsState().value,
                                instanceLinkFetchSongDetail = { number, songbook ->
                                    instanceLinkViewModel.fetchSongDetail(number, songbook)
                                },
                                instanceLinkFetchBibleFile = { instanceLinkViewModel.fetchBibleFile() },
                                instanceLinkBibleSyncMode = appSettings.instanceLink.bibleSyncMode,
                                instanceLinkFetchSecondaryBibleFile = {
                                    instanceLinkViewModel.fetchSecondaryBibleFile()
                                },
                                instanceLinkFetchBibleTranslations = { instanceLinkViewModel.fetchBibleTranslations() },
                                instanceLinkOnSecondaryBibleFilePathChanged = { path ->
                                    companionServer.updateSecondaryBibleFilePath(path)
                                },
                                instanceLinkOnBibleFilePathsChanged = { paths ->
                                    companionServer.updateBibleFilePaths(paths)
                                },
                                instanceLinkSendAddToSchedule = if (canPushToSchedule(appSettings.instanceLink)) {
                                    { item -> instanceLinkViewModel.sendAddToSchedule(item) }
                                } else null,
                                instanceLinkSendRemoveFromSchedule = if (canPushToSchedule(appSettings.instanceLink)) {
                                    { id -> instanceLinkViewModel.sendRemoveFromSchedule(id) }
                                } else null,
                                instanceLinkRole = appSettings.instanceLink.role,
                                instanceLinkSendProject = if (instanceLinkIsControllerConnected) {
                                    { item -> instanceLinkViewModel.sendProject(item) }
                                } else null,
                                instanceLinkSendVerse = if (instanceLinkIsControllerConnected) {
                                    { bookName, chapter, verseNumber, verseText, verseRange ->
                                        instanceLinkViewModel.sendSelectBibleVerse(
                                            bookName,
                                            chapter,
                                            verseNumber,
                                            verseText,
                                            verseRange
                                        )
                                    }
                                } else null,
                                instanceLinkSendSongSection = if (instanceLinkIsControllerConnected) {
                                    { number, section, lineIndex ->
                                        instanceLinkViewModel.sendSelectSongSection(number, section, lineIndex)
                                    }
                                } else null,
                                instanceLinkSendClear = if (instanceLinkIsControllerConnected) {
                                    { instanceLinkViewModel.sendClear() }
                                } else null,
                                instanceLinkSendBibleHold = if (instanceLinkIsControllerConnected) {
                                    { hold -> instanceLinkViewModel.sendBibleHold(hold) }
                                } else null,
                                instanceLinkSendNextPicture = if (instanceLinkIsControllerConnected) {
                                    { instanceLinkViewModel.sendNextPicture() }
                                } else null,
                                instanceLinkSendPreviousPicture = if (instanceLinkIsControllerConnected) {
                                    { instanceLinkViewModel.sendPreviousPicture() }
                                } else null,
                                instanceLinkSendNextSlide = if (instanceLinkIsControllerConnected) {
                                    { instanceLinkViewModel.sendNextSlide() }
                                } else null,
                                instanceLinkSendPreviousSlide = if (instanceLinkIsControllerConnected) {
                                    { instanceLinkViewModel.sendPreviousSlide() }
                                } else null,
                                instanceLinkFetchPictureImageBytes = if (instanceLinkUsesRemoteContent) {
                                    { folderId, index -> instanceLinkViewModel.fetchPictureImageBytes(folderId, index) }
                                } else null,
                                instanceLinkFetchPresentationSlideBytes = if (instanceLinkUsesRemoteContent) {
                                    { id, index -> instanceLinkViewModel.fetchPresentationSlideBytes(id, index) }
                                } else null,
                                instanceLinkMediaStreamUrl = run {
                                    val link = appSettings.instanceLink
                                    if (instanceLinkUsesRemoteContent) {
                                        ({ itemId: String ->
                                            instanceLinkMediaStreamUrl(
                                                link.primaryHost, link.primaryPort, link.apiKey, itemId,
                                            )
                                        })
                                    } else null
                                },
                                onVerseSelected = { verses -> presenterManager.setSelectedVerses(verses) },
                                onSongItemSelected = { section ->
                                    presenterManager.setLyricSection(section)
                                    if (isSongLineMode(appSettings.songSettings)) {
                                        presenterManager.setDisplayedLyricSection(section)
                                    }
                                },
                                onAllSectionsChanged = { presenterManager.setAllLyricSections(it) },
                                onSectionIndexChanged = { presenterManager.setSongDisplaySectionIndex(it) },
                                onLineIndexChanged = { presenterManager.setSongDisplayLineIndex(it) },
                                appSettings = appSettings,
                                livePreviewAppSettings = effectiveAppSettings,
                                presenterManager = presenterManager,
                                statisticsManager = statisticsManager,
                                verseSequenceLog = verseSequenceLog,
                                onScheduleActionsReady = { scheduleActions = it },
                                presenting = { mode ->
                                    presenterManager.setPresentingMode(mode)
                                    if (shouldShowPresenterWindowFor(mode)) {
                                        presenterManager.setShowPresenterWindow(true)
                                    }
                                },
                                onScheduleItemSelected = { itemId -> selectedScheduleItemId = itemId },
                                onShowSettings = { openOptionsDialog(0) },
                                onShowBackgroundSettings = { openOptionsDialog(OPTIONS_TAB_BACKGROUND) },
                                onSettingsChange = { updateFn ->
                                    appSettings = updateFn(appSettings)
                                    settingsManager.saveSettings(appSettings)
                                },
                                theme = theme,
                                onSongsLoaded = { songs -> companionServer.updateSongs(songs) },
                                onScenesChanged = { scenes -> scenesForInstanceLink = scenes },
                                onBibleLoaded = { bible, translation ->
                                    primaryBibleForInstanceLink = bible
                                    companionServer.updateBible(
                                        bible,
                                        translation,
                                        filePath = bibleFilePath(
                                            appSettings.bibleSettings.storageDirectory,
                                            translation
                                        )
                                    )
                                },
                                onScheduleChanged = { items -> companionServer.updateSchedule(items) },
                                onPresentationSlidesLoaded = { id, filePath, fileName, fileType, slides, notes ->
                                    companionServer.updatePresentation(id, filePath, fileName, fileType, slides, notes)
                                },
                                onPicturesLoaded = { folderId, folderName, folderPath, imageFiles ->
                                    companionServer.updatePictures(folderId, folderName, folderPath, imageFiles)
                                },
                                selectPictureImageFlow = kotlinx.coroutines.flow.flow {
                                    companionServer.onSelectPicture.collect { req ->
                                        emit(req.folderId to req.index)
                                    }
                                },
                                resolveImageFile = { folderId, index ->
                                    companionServer.getImageFile(folderId, index)
                                },
                                selectSlideFlow = kotlinx.coroutines.flow.flow {
                                    companionServer.onSelectSlide.collect { req ->
                                        emit(req.id to req.index)
                                    }
                                },
                                selectBibleVerseFlow = kotlinx.coroutines.flow.flow {
                                    companionServer.onSelectBibleVerse.collect { req ->
                                        emit(req)
                                    }
                                },
                                remoteSelectSongFlow = remoteSelectSongFlow,
                                remoteSelectPictureFlow = remoteSelectPictureFlow,
                                remoteSelectPresentationFlow = remoteSelectPresentationFlow,
                                nextPictureFlow = kotlinx.coroutines.flow.flow {
                                    companionServer.onNextPicture.collect { emit(Unit) }
                                },
                                previousPictureFlow = kotlinx.coroutines.flow.flow {
                                    companionServer.onPreviousPicture.collect { emit(Unit) }
                                },
                                nextSlideFlow = kotlinx.coroutines.flow.flow {
                                    companionServer.onNextSlide.collect { emit(Unit) }
                                },
                                previousSlideFlow = kotlinx.coroutines.flow.flow {
                                    companionServer.onPreviousSlide.collect { emit(Unit) }
                                },
                                uploadPresentationFlow = kotlinx.coroutines.flow.flow {
                                    companionServer.onPresentationUploaded.collect { file ->
                                        emit(file)
                                    }
                                },
                                serverUrl = companionServer.serverUrl.collectAsState().value,
                                qaManager = qaManager,
                                tunnelStatus = tunnelStatus,
                                tunnelUrl = tunnelUrl ?: "",
                                onStartTunnel = {
                                    companionServer.tunnelManager.start(appSettings.serverSettings.port)
                                },
                                onStopTunnel = { companionServer.tunnelManager.stop() },
                                qaDisplayUrl = qaDisplayUrl,
                                onQaDisplayUrlChanged = { qaDisplayUrl = it },
                                presentationDisplayUrl = presentationDisplayUrl,
                                onPresentationDisplayUrlChanged = { presentationDisplayUrl = it },
                                onSlideChanged = { id, index, total, isPlaying ->
                                    companionServer.broadcastSlideChange(id, index, total, isPlaying)
                                },
                                remotePresentationPlayPauseFlow = companionServer.onPresentationPlayPause,
                                remotePresentationLoopToggleFlow = companionServer.onPresentationLoopToggle,
                                remotePresentationGotoFlow = companionServer.onPresentationGoto,
                                presentationFrozen = presentationFrozen,
                                onFreezeToggle = {
                                    presentationFrozen = !presentationFrozen
                                    companionServer.broadcastFreezeChange(presentationFrozen)
                                    presenterManager.setSlideFrozen(presentationFrozen)
                                },
                                onClearPresentation = {
                                    companionServer.clearPresentationState()
                                    presenterManager.requestClearDisplay()
                                },
                                onOpenLottieGen = { outputDir, onSaved ->
                                    if (isUsableOutputDir(outputDir)) {
                                        lottieGenOutputDir = File(outputDir)
                                        lottieGenOnFileSaved = onSaved
                                        showLottieGenWindow = true
                                    } else {
                                        javax.swing.JOptionPane.showMessageDialog(
                                            null,
                                            "Please set a Lower Third folder in Settings first.",
                                            "No Folder Configured",
                                            javax.swing.JOptionPane.WARNING_MESSAGE
                                        )
                                    }
                                },
                                sttManager = sttManager,
                                dialogDismissSignal = dialogDismissSignal,
                                companionSatelliteViewModel = companionSatelliteViewModel,
                                onRequestDeveloperMenuUnlock = { developerMenuUnlocked = true }
                            )
                            OptionsDialog(
                                isVisible = showOptionsDialog,
                                initialTab = optionsDialogInitialTab,
                                initialSettings = appSettings,
                                theme = theme,
                                settingsManager = settingsManager,
                                companionServer = companionServer,
                                remoteClientManager = remoteClientManager,
                                presenterManager = presenterManager,
                                onDismiss = { showOptionsDialog = false; dialogDismissSignal++ },
                                onSave = { updated ->
                                    appSettings = updated
                                    settingsManager.saveSettings(updated)
                                    companionServer.preloadData(
                                        songStorageDir = updated.songSettings.storageDirectory,
                                        bibleStorageDir = updated.bibleSettings.storageDirectory,
                                        primaryBibleFileName = updated.bibleSettings.primaryBible
                                    )
                                    companionServer.updateApiKey(
                                        enabled = updated.serverSettings.apiKeyEnabled,
                                        key = updated.serverSettings.apiKey
                                    )
                                    companionServer.updateFileUploadEnabled(updated.serverSettings.fileUploadEnabled)
                                    companionServer.updateMaxMediaUploadMb(updated.serverSettings.maxMediaUploadMb)
                                    companionServer.updateAtemConfig(
                                        updated.atemSettings,
                                        updated.streamingSettings.lowerThirdFolder
                                    )
                                },
                                onIdentifyScreen = {
                                    identifyingScreen = true
                                    coroutineScope.launch {
                                        delay(UPDATE_CHECK_DELAY_MS)
                                        identifyingScreen = false
                                    }
                                },
                                onIdentifyBrowserSource = { index ->
                                    presenterManager.identifyBrowserSourceOutput(index)
                                },
                                onOpenLottieGen = { outputDir, onSaved ->
                                    if (isUsableOutputDir(outputDir)) {
                                        lottieGenOutputDir = File(outputDir)
                                        lottieGenOnFileSaved = onSaved
                                        showLottieGenWindow = true
                                    } else {
                                        javax.swing.JOptionPane.showMessageDialog(
                                            null,
                                            "Please set a Lower Third folder in Settings first.",
                                            "No Folder Configured",
                                            javax.swing.JOptionPane.WARNING_MESSAGE
                                        )
                                    }
                                },
                                obsManager = obsManager,
                                companionSatelliteViewModel = companionSatelliteViewModel
                            )
                            KeyboardShortcutsDialog(
                                isVisible = showKeyboardShortcutsDialog,
                                settings = appSettings,
                                onSave = { updated ->
                                    appSettings = updated
                                    settingsManager.saveSettings(updated)
                                },
                                onDismiss = { showKeyboardShortcutsDialog = false; dialogDismissSignal++ }
                            )
                            CCLIReportDialog(
                                isVisible = showStatisticsDialog,
                                theme = theme,
                                statisticsManager = statisticsManager,
                                onDismiss = { showStatisticsDialog = false; dialogDismissSignal++ }
                            )
                            InstanceLinkDialog(
                                isVisible = showInstanceLinkDialog,
                                settings = appSettings.instanceLink,
                                connectionStatus = instanceLinkViewModel.connectionStatus.collectAsState().value,
                                remoteLiveState = instanceLinkViewModel.remoteLiveState.collectAsState().value,
                                remoteScheduleCount = instanceLinkViewModel.remoteSchedule.collectAsState().value.size,
                                lastMessageAtMs = instanceLinkViewModel.lastMessageAtMs.collectAsState().value,
                                onConnect = { edited ->
                                    val link = edited.copy(enabled = true)
                                    appSettings = appSettings.copy(instanceLink = link)
                                    settingsManager.saveSettings(appSettings)
                                    instanceLinkViewModel.connect(
                                        link.primaryHost, link.primaryPort, link.apiKey, link.deviceId,
                                        link.reconnectDelayMs.toLong()
                                    )
                                },
                                onSave = { edited ->
                                    appSettings = appSettings.copy(instanceLink = edited)
                                    settingsManager.saveSettings(appSettings)
                                },
                                onDisconnect = {
                                    setInstanceLinkEnabled(false)
                                    instanceLinkViewModel.disconnect()
                                },
                                onDismiss = { showInstanceLinkDialog = false; dialogDismissSignal++ }
                            )
                            AboutDialog(
                                isVisible = showAboutDialog,
                                onDismiss = { showAboutDialog = false; dialogDismissSignal++ },
                                appSettings = appSettings,
                                theme = theme
                            )
                            ContactUsDialog(
                                isVisible = showContactDialog,
                                onDismiss = {
                                    showContactDialog = false
                                    contactDialogInitialType = null
                                    dialogDismissSignal++
                                },
                                initialTypeKey = contactDialogInitialType
                            )
                            ShareYourStoryDialog(
                                isVisible = showStoryPrompt,
                                onShare = {
                                    appSettings = appSettings.copy(storyPrompt = appSettings.storyPrompt.answered())
                                    settingsManager.saveSettings(appSettings)
                                    showStoryPrompt = false
                                    contactDialogInitialType = CONTACT_TYPE_TESTIMONIAL
                                    showContactDialog = true
                                },
                                onDismiss = { showStoryPrompt = false; dialogDismissSignal++ }
                            )
                            if (showConverterWindow) {
                                ConverterWindow(
                                    theme = theme,
                                    onClose = { showConverterWindow = false }
                                )
                            }
                            if (showSongLibraryWindow) {
                                SongLibraryWindow(
                                    theme = theme,
                                    songStorageDirectory = appSettings.songSettings.storageDirectory,
                                    // What it writes lands in the songs folder, which SongsViewModel
                                    // already watches -- so the list behind this window reloads on
                                    // its own rather than on close.
                                    onClose = { showSongLibraryWindow = false }
                                )
                            }
                            if (showLottieGenWindow) {
                                val screenBounds = presenterScreenBounds()
                                LottieGenWindow(
                                    theme = theme,
                                    outputDir = lottieGenOutputDir,
                                    onClose = { showLottieGenWindow = false },
                                    onFileSaved = {
                                        UsageEvents.record(UsageEvent.LOWER_THIRD_GENERATED)
                                        lottieGenOnFileSaved?.invoke()
                                    },
                                    canvasWidth = screenBounds.width,
                                    canvasHeight = screenBounds.height
                                )
                            }
                            MemoryMonitorWindow(
                                isVisible = showMemoryMonitorWindow,
                                theme = theme,
                                onClose = { showMemoryMonitorWindow = false }
                            )
                            if (showStyleEditorWindow) {
                                StyleEditorWindow(
                                    theme = theme,
                                    onClose = { showStyleEditorWindow = false }
                                )
                            }
                            UpdateAvailableDialog(
                                result = pendingUpdateResult,
                                isManualCheck = pendingUpdateCheckWasManual,
                                participateInPrereleases = appSettings.participateInPrereleases,
                                onParticipateInPrereleasesChange = { enabled ->
                                    appSettings = appSettings.copy(participateInPrereleases = enabled)
                                    settingsManager.saveSettings(appSettings)
                                    coroutineScope.launch {
                                        pendingUpdateResult = UpdateChecker.checkForUpdate(includePrereleases = enabled)
                                    }
                                },
                                updateCheckInterval = appSettings.updateCheckInterval,
                                onUpdateCheckIntervalChange = { interval ->
                                    appSettings = appSettings.copy(updateCheckInterval = interval)
                                    settingsManager.saveSettings(appSettings)
                                },
                                onDismiss = { pendingUpdateResult = null }
                            )

                            val currentRemote = remoteEventQueue.firstOrNull()
                            val currentClientId = currentRemote?.first?.clientId ?: ""
                            RemoteEventDialog(
                                event = currentRemote?.first,
                                queueSize = remoteEventQueue.size,
                                isClientKnownAllowed = remoteClientManager.isAllowed(currentClientId),
                                isClientKnownBlocked = remoteClientManager.isBlocked(currentClientId),
                                isInstanceLinkFollower = isInstanceLinkFollowerClient(
                                    currentClientId,
                                    companionServer.connectedInstanceLinkFollowers.collectAsState().value,
                                ),
                                onAllow = {
                                    currentRemote?.second?.invoke()
                                    if (remoteEventQueue.isNotEmpty()) remoteEventQueue.removeAt(0)
                                },
                                onAllowForSession = {
                                    if (currentClientId.isNotBlank() && !sessionAllowedClients.contains(
                                            currentClientId
                                        )
                                    ) {
                                        sessionAllowedClients.add(currentClientId)
                                    }
                                    val clientToAllow = currentClientId
                                    val toApprove = remoteEventQueue.filter {
                                        remoteEventTargetsClient(it.first.clientId, clientToAllow)
                                    }
                                    toApprove.forEach { it.second.invoke() }
                                    remoteEventQueue.removeAll(toApprove)
                                },
                                onAllowPermanently = {
                                    remoteClientManager.allowPermanently(currentClientId)
                                    val clientToAllow = currentClientId
                                    val toApprove = remoteEventQueue.filter {
                                        remoteEventTargetsClient(it.first.clientId, clientToAllow)
                                    }
                                    toApprove.forEach { it.second.invoke() }
                                    remoteEventQueue.removeAll(toApprove)
                                },
                                onBlockForSession = {
                                    if (currentClientId.isNotBlank() && !sessionBlockedClients.contains(
                                            currentClientId
                                        )
                                    ) {
                                        sessionBlockedClients.add(currentClientId)
                                    }
                                    val clientToBlock = currentClientId
                                    val toRemove = remoteEventQueue.filter {
                                        remoteEventTargetsClient(it.first.clientId, clientToBlock)
                                    }
                                    toRemove.forEach { it.third.invoke() }
                                    remoteEventQueue.removeAll(toRemove)
                                },
                                onBlockPermanently = {
                                    remoteClientManager.blockPermanently(currentClientId)
                                    val clientToBlock = currentClientId
                                    val toRemove = remoteEventQueue.filter {
                                        remoteEventTargetsClient(it.first.clientId, clientToBlock)
                                    }
                                    toRemove.forEach { it.third.invoke() }
                                    remoteEventQueue.removeAll(toRemove)
                                },
                                onDeny = {
                                    currentRemote?.third?.invoke()
                                    if (remoteEventQueue.isNotEmpty()) remoteEventQueue.removeAt(0)
                                }
                            )

                            InstanceLinkToastHost(
                                failures = instanceLinkCommandFailures,
                                onDismiss = { failure -> instanceLinkCommandFailures.remove(failure) }
                            )
                            RemoteActivityToastHost(
                                notifications = remoteActivityNotifications,
                                connectedInstanceLinkFollowers =
                                    companionServer.connectedInstanceLinkFollowers.collectAsState().value,
                                onDismiss = { n -> remoteActivityNotifications.remove(n) },
                                onDismissAll = { remoteActivityNotifications.clear() },
                                onBlockForSession = { n ->
                                    val cid = n.clientId
                                    if (cid.isNotBlank() && !sessionBlockedClients.contains(cid)) {
                                        sessionBlockedClients.add(cid)
                                        sessionAllowedClients.remove(cid)
                                    }
                                    remoteActivityNotifications.removeAll { it.clientId == cid }
                                }
                            )
                        } // end Box (window content)
                    }
                }
            }
        }

        LaunchedEffect(mediaViewModel.mediaFinished) {
            if (mediaViewModel.mediaFinished) {
                presenterManager.requestClearDisplay()
                mediaViewModel.clearFinished()
            }
        }

        PresenterWindows(
            screens = screens,
            presenterManager = presenterManager,
            mediaViewModel = mediaViewModel,
            appSettings = effectiveAppSettings,
            identifyingScreen = identifyingScreen,
            serverUrl = companionServer.serverUrl.collectAsState().value,
            qaDisplayUrl = qaDisplayUrl,
            sttManager = sttManager,
        )
    }

    if (appReady && eulaAccepted && showSetupWizard) {
        SetupWizardDialog(
            theme = theme,
            selectedLanguage = currentLanguage,
            alwaysOnTop = !showOptionsDialog,
            onLanguageSelected = { language ->
                currentLanguage = language
                appSettings = appSettings.copy(language = language.code)
                settingsManager.saveSettings(appSettings)
                Locale.setDefault(Locale.forLanguageTag(language.code))
            },
            onThemeSelected = { newTheme ->
                theme = newTheme
                appSettings = appSettings.copy(theme = newTheme.toString())
                settingsManager.saveSettings(appSettings)
            },
            onOpenSettings = { openOptionsDialog(0) },
            onDismiss = {
                val updated = appSettings.copy(setupWizardShown = true)
                settingsManager.saveSettings(updated)
                appSettings = updated
                showSetupWizard = false
            }
        )
    }

    LicenseDialog(
        isVisible = appReady && !eulaAccepted,
        onAccept = {
            val updated = appSettings.copy(eulaAcceptedVersion = CURRENT_EULA_VERSION)
            settingsManager.saveSettings(updated)
            appSettings = updated
            eulaAccepted = true
        },
        onDecline = { exitApplication() }
    )
}
