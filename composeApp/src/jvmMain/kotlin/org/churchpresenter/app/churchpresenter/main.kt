package org.churchpresenter.app.churchpresenter

import androidx.compose.animation.fadeOut
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key as composeKey
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import org.churchpresenter.app.churchpresenter.BuildConfig
import org.churchpresenter.app.churchpresenter.composables.DeckLinkManager
import org.churchpresenter.app.churchpresenter.utils.LottieFonts
import org.churchpresenter.app.churchpresenter.utils.findScreenIndexByBounds
import org.churchpresenter.app.churchpresenter.utils.rememberScreenDevices
import presentation.engine.fonts.SlideFontRegistry
import androidx.compose.ui.window.rememberWindowState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.app_name
import churchpresenter.composeapp.generated.resources.ic_app_icon
import churchpresenter.composeapp.generated.resources.loading
import churchpresenter.composeapp.generated.resources.presenter_view_title
import churchpresenter.composeapp.generated.resources.key_output_title
import churchpresenter.composeapp.generated.resources.screen_number
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BackgroundSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleSyncMode
import org.churchpresenter.app.churchpresenter.data.settings.CompanionSatelliteSettings
import org.churchpresenter.app.churchpresenter.data.settings.InstanceLinkRole
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.data.Language
import org.churchpresenter.app.churchpresenter.data.RemoteClientManager
import org.churchpresenter.app.churchpresenter.data.SettingsManager
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.app.churchpresenter.dialogs.AboutDialog
import org.churchpresenter.app.churchpresenter.dialogs.InstanceLinkToastHost
import org.churchpresenter.app.churchpresenter.dialogs.ContactUsDialog
import org.churchpresenter.app.churchpresenter.dialogs.ConverterWindow
import org.churchpresenter.app.churchpresenter.dialogs.LottieGenWindow
import org.churchpresenter.app.churchpresenter.dialogs.KeyboardShortcutsDialog
import org.churchpresenter.app.churchpresenter.dialogs.LicenseDialog
import org.churchpresenter.app.churchpresenter.dialogs.SetupWizardDialog
import org.churchpresenter.app.churchpresenter.dialogs.RemoteActivityNotification
import org.churchpresenter.app.churchpresenter.dialogs.RemoteActivityToastHost
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEvent
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEventDialog
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEventType
import org.churchpresenter.app.churchpresenter.dialogs.OptionsDialog
import org.churchpresenter.app.churchpresenter.presenter.DeckLinkComposeOutput
import org.churchpresenter.app.churchpresenter.presenter.BrowserSourceVideoRenderer
import org.churchpresenter.app.churchpresenter.presenter.AnnouncementsPresenter
import org.churchpresenter.app.churchpresenter.presenter.QAPresenter
import org.churchpresenter.app.churchpresenter.presenter.STTPresenter
import org.churchpresenter.app.churchpresenter.presenter.DictionaryPresenter
import org.churchpresenter.app.churchpresenter.presenter.QAQRCodePresenter
import org.churchpresenter.app.churchpresenter.presenter.CefManager
import org.churchpresenter.app.churchpresenter.presenter.WebsitePresenter
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.app.churchpresenter.presenter.LowerThirdPresenter
import org.churchpresenter.app.churchpresenter.presenter.MediaPresenter
import org.churchpresenter.app.churchpresenter.presenter.PicturePresenter
import org.churchpresenter.app.churchpresenter.presenter.PresentationPresenter
import org.churchpresenter.app.churchpresenter.presenter.SlidePresenter
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.presenter.ScenePresenter
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.models.Scene
import org.churchpresenter.app.churchpresenter.models.Question
import org.churchpresenter.app.churchpresenter.models.QuestionStatus
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.churchpresenter.app.churchpresenter.ui.theme.LanguageProvider
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.InstanceLinkCommandFailure
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.composables.preWarmJavaFX
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import org.churchpresenter.app.churchpresenter.composables.vlcCustomPath
import org.churchpresenter.app.churchpresenter.data.Bible
import org.churchpresenter.app.churchpresenter.server.LottieRenderCache
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.app.churchpresenter.server.LowerThirdSequencer
import org.churchpresenter.app.churchpresenter.server.TunnelStatus
import org.churchpresenter.app.churchpresenter.server.InstanceLinkStatus
import org.churchpresenter.app.churchpresenter.server.LiveStateDto
import org.churchpresenter.app.churchpresenter.dialogs.InstanceLinkDialog
import org.churchpresenter.app.churchpresenter.viewmodel.QAManager
import org.churchpresenter.app.churchpresenter.viewmodel.OBSWebSocketManager
import org.churchpresenter.app.churchpresenter.viewmodel.CompanionSatelliteViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.InstanceLinkViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.presenterScreenBounds

import org.churchpresenter.app.churchpresenter.utils.AutoStartManager
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import org.churchpresenter.app.churchpresenter.utils.InstanceLinkLogSide
import org.churchpresenter.app.churchpresenter.utils.InstanceLinkLogger
import org.churchpresenter.app.churchpresenter.utils.LiveMapReporter
import org.churchpresenter.app.churchpresenter.utils.MacMenuBarActivationFix
import org.churchpresenter.app.churchpresenter.utils.UpdateCheckResult
import org.churchpresenter.app.churchpresenter.utils.UpdateChecker
import org.churchpresenter.app.churchpresenter.dialogs.StatisticsDialog
import org.churchpresenter.app.churchpresenter.dialogs.UpdateAvailableDialog
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.io.File
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.CoroutineExceptionHandler
import org.churchpresenter.app.churchpresenter.models.SelectedVerse


private const val CURRENT_EULA_VERSION = 1

private var singleInstanceSocket: java.net.ServerSocket? = null

/**
 * Attempt to bind a local port to enforce single-instance.
 * Returns true if this is the first instance, false if another is already running.
 */
private fun acquireSingleInstanceLock(): Boolean {
    return try {
        // Bind to a fixed localhost port — if it's already taken, another instance is running.
        // The system property override exists for development/testing (e.g. running an
        // InstanceLink follower side by side with a primary on one machine, combined with
        // -Duser.home to isolate its settings and caches).
        val lockPort = System.getProperty("churchpresenter.singleInstancePort")?.toIntOrNull()
            ?: Constants.SINGLE_INSTANCE_PORT
        singleInstanceSocket = java.net.ServerSocket(lockPort, 1, java.net.InetAddress.getLoopbackAddress())
        true
    } catch (_: Exception) {
        false
    }
}

fun main() {
    // Ensure Skiko uses Metal on macOS — prevents OPENGL fallback crash
    if (System.getProperty("os.name", "").lowercase().contains("mac")) {
        System.setProperty("skiko.renderApi", "METAL")
    }
    // Enforce single instance — exit immediately if another is already running
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

    // Install crash reporting before anything else
    val startupSettings = SettingsManager().loadSettings()
    CrashReporter.initialize(startupSettings.analyticsReportingEnabled)
    CrashReporter.breadcrumb("Application started", category = "lifecycle")

    // First run only: if no Bible has been configured yet, bundle the KJV 1769
    // into a default folder and set it as the primary Bible so the app works
    // out of the box without requiring the user to pick a Bible folder first.
    if (startupSettings.bibleSettings.storageDirectory.isEmpty() && startupSettings.bibleSettings.primaryBible.isEmpty()) {
        try {
            val defaultBibleDir = File(System.getProperty("user.home"), ".churchpresenter/Bibles")
            defaultBibleDir.mkdirs()
            val targetFile = File(defaultBibleDir, "kjv1769.spb")
            if (!targetFile.exists()) {
                targetFile.writeBytes(runBlocking { Res.readBytes("files/bible_samples/kjv1769.spb") })
            }
            SettingsManager().saveSettings(
                startupSettings.copy(
                    bibleSettings = startupSettings.bibleSettings.copy(
                        storageDirectory = defaultBibleDir.absolutePath,
                        primaryBible = "kjv1769.spb"
                    )
                )
            )
        } catch (e: Exception) {
            CrashReporter.reportException(e, "Bundling default KJV Bible")
        }
    }

    // Pass the install id only when analytics is enabled, so opted-out users
    // still send an anonymous geo ping but no persistent identifier.
    LiveMapReporter.pingOnOpen(
        installId = if (startupSettings.analyticsReportingEnabled) CrashReporter.installId() else null,
        updateCheckInterval = startupSettings.updateCheckInterval
    )

    // Catch exceptions thrown inside coroutines / Compose lambdas —
    // these never reach Thread.setDefaultUncaughtExceptionHandler on their own.
    val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        CrashReporter.reportException(throwable, context = "CoroutineExceptionHandler")
    }

    // Pre-warm JavaFX on a background thread before UI starts
    preWarmJavaFX()

    // Initialize JCEF (Chromium) for embedded web browsing
    CefManager.init()
    // Startup config tag: whether the embedded browser engine loaded (aids triage of web errors).
    CrashReporter.setTag("jcef.available", CefManager.initialized.toString())
    if (CefManager.macOsUnsupported) CrashReporter.setTag("jcef.macos_unsupported", "true")

    // Initialize FileKit so native file dialogs can resolve app directories
    io.github.vinceglb.filekit.FileKit.init(appId = "ChurchPresenter")

    // Repair a stale login-launch registration if the install path changed (e.g. after an update)
    Thread { AutoStartManager.syncRegistration() }.apply { isDaemon = true }.start()

    // Register bundled fonts with AWT and scan platform font dirs so slide rendering (POI/PDF)
    // resolves real typefaces instead of silently substituting the JVM default. Runs in the
    // background — the registry substitutes safely for any slide rendered before it finishes.
    Thread {
        LottieFonts.bundledFontResources().forEach { resource ->
            LottieFonts::class.java.getResourceAsStream(resource)?.let {
                SlideFontRegistry.registerFontStream(it)
            }
        }
        SlideFontRegistry.initialize()
    }.apply { isDaemon = true }.start()

    // Set custom VLC path from saved settings before any composable checks isVlcAvailable
    vlcCustomPath = startupSettings.projectionSettings.vlcPath

    // Pre-render lower-third frame caches in the background so playback starts on raw
    // frames and "Send to ATEM" is instant even before the Lower Third tab is opened
    LottieRenderCache.ensureForFolder(
        startupSettings.streamingSettings.lowerThirdFolder,
        startupSettings.atemSettings
    )

    application(exitProcessOnExit = true) {
        var appReady by remember { mutableStateOf(false) }
        // Business logic layer
        val settingsManager = remember { SettingsManager() }
        val statisticsManager = remember { StatisticsManager() }
        var appSettings by remember {
            mutableStateOf(settingsManager.loadSettings().let {
                it.copy(presentationRemoteSettings = it.presentationRemoteSettings.copy(remoteControlEnabled = false))
            })
        }

        // Resolve any unassigned (-1 auto) screen assignments at startup so that
        // DeckLink-only slots are set to None before the UI renders.
        remember {
            val screenDevicesAll = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
            val primaryDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
            val nonPrimaryDevices = screenDevicesAll.filter { it != primaryDevice }
            val deckLinkCount = if (DeckLinkManager.isAvailable()) DeckLinkManager.listDevices().size else 0
            val slotCount = (nonPrimaryDevices.size + deckLinkCount).coerceAtLeast(0)

            val proj = appSettings.projectionSettings
            var changed = false
            val assignments = proj.screenAssignments.toMutableList()
            while (assignments.size < slotCount) {
                val npIdx = assignments.size
                val device = nonPrimaryDevices.getOrNull(npIdx)
                val deviceIdx = if (device != null) screenDevicesAll.indexOf(device) else Constants.KEY_TARGET_NONE
                val bounds = device?.defaultConfiguration?.bounds
                assignments.add(
                    ScreenAssignment(
                        targetDisplay = deviceIdx,
                        targetBoundsX = bounds?.x ?: Int.MIN_VALUE,
                        targetBoundsY = bounds?.y ?: Int.MIN_VALUE,
                        targetBoundsW = bounds?.width ?: 0,
                        targetBoundsH = bounds?.height ?: 0
                    )
                )
                changed = true
            }
            for (idx in assignments.indices) {
                if (assignments[idx].targetDisplay == -1) {
                    val device = nonPrimaryDevices.getOrNull(idx)
                    if (device != null) {
                        val deviceIdx = screenDevicesAll.indexOf(device)
                        val bounds = device.defaultConfiguration.bounds
                        assignments[idx] = assignments[idx].copy(
                            targetDisplay = deviceIdx,
                            targetBoundsX = bounds.x,
                            targetBoundsY = bounds.y,
                            targetBoundsW = bounds.width,
                            targetBoundsH = bounds.height
                        )
                    } else {
                        assignments[idx] = assignments[idx].copy(targetDisplay = Constants.KEY_TARGET_NONE)
                    }
                    changed = true
                }
            }
            if (changed) {
                appSettings = appSettings.copy(
                    projectionSettings = proj.copy(screenAssignments = assignments)
                )
                settingsManager.saveSettings(appSettings)
            }
        }

        val presenterManager = remember { PresenterManager() }
        // Keep the manager's copy of the ATEM settings current — lower-third pre-renders use
        // them to pick the shared render size so playback and ATEM uploads hit one cache entry.
        LaunchedEffect(appSettings.atemSettings) {
            presenterManager.setAtemRenderSettings(appSettings.atemSettings)
        }

        var eulaAccepted by remember { mutableStateOf(appSettings.eulaAcceptedVersion >= CURRENT_EULA_VERSION) }
        var showSetupWizard by remember {
            val bibleReady = appSettings.bibleSettings.primaryBible.isNotEmpty()
            val songsReady = appSettings.songSettings.storageDirectory.isNotEmpty()
            mutableStateOf(!appSettings.setupWizardShown && !(bibleReady && songsReady))
        }

        var currentLanguage by remember {
            val savedLanguageCode = appSettings.language
            val language = Language.entries.find { it.code == savedLanguageCode } ?: Language.ENGLISH
            Locale.setDefault(Locale.forLanguageTag(language.code))
            mutableStateOf(language)
        }

        var scheduleActions by remember { mutableStateOf(ScheduleActions()) }
        val currentScheduleActions by rememberUpdatedState(scheduleActions)

        val mediaViewModel = remember { MediaViewModel() }

        var identifyingScreen by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope { coroutineExceptionHandler }

        var theme by remember {
            val savedTheme = when (appSettings.theme.uppercase()) {
                "LIGHT" -> ThemeMode.LIGHT
                "DARK" -> ThemeMode.DARK
                "WARM" -> ThemeMode.WARM
                "OCEAN" -> ThemeMode.OCEAN
                "ROSE" -> ThemeMode.ROSE
                "MIDNIGHT" -> ThemeMode.MIDNIGHT
                "FOREST" -> ThemeMode.FOREST
                "MOCHA" -> ThemeMode.MOCHA
                else -> ThemeMode.SYSTEM
            }
            mutableStateOf(savedTheme)
        }
        val companionServer = remember { CompanionServer() }
        val qaManager = remember { QAManager() }
        val sttManager = remember { STTManager() }
        val obsManager = remember { OBSWebSocketManager() }
        val companionSatelliteViewModel = remember { CompanionSatelliteViewModel() }
        DisposableEffect(Unit) { onDispose { companionSatelliteViewModel.dispose() } }
        // Auto-connect newly-added connections once; re-keying on id list (not full settings) avoids
        // reconnecting everything whenever an unrelated field on an existing connection is edited.
        val autoConnectedIds = remember { mutableSetOf<String>() }
        LaunchedEffect(appSettings.companionSatelliteConnections.map { it.id }) {
            for (connection in appSettings.companionSatelliteConnections) {
                if (connection.autoConnect && autoConnectedIds.add(connection.id)) {
                    // Companion requires a non-empty DEVICEID — generate + persist one if it was
                    // cleared, same guard as the manual Connect button in settings.
                    val effective = if (connection.deviceId.isBlank()) {
                        val generated = java.util.UUID.randomUUID().toString()
                        appSettings = appSettings.copy(
                            companionSatelliteConnections = appSettings.companionSatelliteConnections.map {
                                if (it.id == connection.id) it.copy(deviceId = generated) else it
                            }
                        )
                        settingsManager.saveSettings(appSettings)
                        connection.copy(deviceId = generated)
                    } else connection
                    companionSatelliteViewModel.connectAll(effective)
                }
            }
        }
        // Reconcile any live settings edit for already-active connections — otherwise unchecking
        // e.g. "Left Sidebar", or editing rows/columns/host/port/etc. on a connection that's
        // already live, would leave that slot's client running with stale registration until the
        // user manually hits Disconnect/Connect again. connectAll() is diff-based (see
        // CompanionSatelliteViewModel), so this is a no-op for connections that aren't changing.
        // Keyed on the full connection list (not hand-picked fields) so this never needs updating
        // when a new registration-affecting setting is added.
        val lastReconciled = remember { mutableMapOf<String, CompanionSatelliteSettings>() }
        LaunchedEffect(appSettings.companionSatelliteConnections) {
            for (connection in appSettings.companionSatelliteConnections) {
                val hasLiveSlot = companionSatelliteViewModel.connectionStates.keys.any { it.connectionId == connection.id }
                // A connection seen before by this effect with different settings than last time
                // was actively edited by the user just now (not merely observed for the first time
                // at startup) — treat that the same as toggling the placement checkbox itself: an
                // explicit action, so it should connect even if autoConnect is off and nothing was
                // live yet. A brand-new/never-before-seen connection still only auto-connects when
                // autoConnect is set, preserving startup's opt-in-only behavior (handled primarily
                // by the auto-connect-once effect above).
                val isLiveEdit = lastReconciled[connection.id]?.let { it != connection } ?: false
                if (hasLiveSlot || connection.autoConnect || isLiveEdit) {
                    companionSatelliteViewModel.connectAll(connection)
                }
                lastReconciled[connection.id] = connection
            }
        }

        val instanceLinkViewModel = remember { InstanceLinkViewModel() }
        DisposableEffect(Unit) { onDispose { instanceLinkViewModel.dispose() } }
        // Captured from the existing onBibleLoaded callback below (BibleViewModel itself stays owned
        // by MainDesktop — only the plain Bible object it already hands out crosses here, same as
        // onBibleLoaded already does for companionServer.updateBible). Used to compute a canonical
        // verse code when broadcasting a live Bible verse, for followers in BibleSyncMode.REFERENCE_ONLY.
        var primaryBibleForInstanceLink by remember { mutableStateOf<Bible?>(null) }
        // Same hoist pattern for the Canvas scene list (SceneViewModel stays owned by MainDesktop;
        // only the plain Scene list crosses here) — used to resolve a mirrored CANVAS live state
        // by scene id.
        var scenesForInstanceLink by remember { mutableStateOf<List<Scene>>(emptyList()) }
        // Auto-connect on launch (and reconnect if the saved connection details change while
        // enabled). Keys are the CONNECTION parameters only — role/bibleSyncMode/mirrorBackgrounds
        // are consumed reactively elsewhere and must not force a spurious reconnect when toggled.
        LaunchedEffect(
            appSettings.instanceLink.enabled,
            appSettings.instanceLink.autoConnect,
            appSettings.instanceLink.primaryHost,
            appSettings.instanceLink.primaryPort,
            appSettings.instanceLink.apiKey,
            appSettings.instanceLink.reconnectDelayMs
        ) {
            val link = appSettings.instanceLink
            if (link.enabled && link.autoConnect && link.primaryHost.isNotBlank() && link.primaryPort > 0) {
                instanceLinkViewModel.connect(
                    link.primaryHost, link.primaryPort, link.apiKey, link.deviceId,
                    link.reconnectDelayMs.toLong()
                )
            } else if (!link.enabled) {
                // Toggling the link off should actually drop the connection, not leave it
                // running until the next app restart.
                instanceLinkViewModel.disconnect()
            }
        }
        // Mirrors the primary's live content locally — the counterpart to onLiveStateChanged below.
        // collectLatest: applying a state does suspend network fetches (pictures, lottie JSON), so
        // a newer state must cancel an in-flight apply instead of queueing behind it — otherwise a
        // slow fetch can finish late and clobber content the operator has already moved past.
        LaunchedEffect(instanceLinkViewModel) {
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
        // Presentations have their own dedicated broadcast (richer than LiveStateDto's mode-only
        // PRESENTATION entry) — fetch and mirror whichever slide the primary is currently showing.
        LaunchedEffect(instanceLinkViewModel) {
            instanceLinkViewModel.remotePresentationSlide.collectLatest { slide ->
                if (slide == null) return@collectLatest
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
        LaunchedEffect(instanceLinkViewModel) {
            instanceLinkViewModel.displayClearedSignal.collect {
                presenterManager.requestClearDisplay()
            }
        }
        // Controller-mode command failures → toast (see InstanceLinkToastHost below).
        val instanceLinkCommandFailures = remember { mutableStateListOf<InstanceLinkCommandFailure>() }
        LaunchedEffect(instanceLinkViewModel) {
            instanceLinkViewModel.commandFailures.collect { failure ->
                instanceLinkCommandFailures.add(failure)
            }
        }
        // The primary's picture folders changed — cached picture files are keyed by folderId+index
        // only, so a replaced image at the same position would otherwise be served stale forever.
        // Clearing the whole cache is cheap: live pictures re-fetch lazily on next display.
        LaunchedEffect(instanceLinkViewModel) {
            instanceLinkViewModel.picturesUpdatedSignal.collect { signal ->
                if (signal == 0) return@collect
                withContext(Dispatchers.IO) {
                    instanceLinkPictureCacheDir.listFiles()?.forEach { it.delete() }
                }
                InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER, "cache_invalidated",
                    mapOf("kind" to "pictures", "trigger" to "pictures_updated")
                )
            }
        }
        // Backgrounds change rarely (initial setup, not per-service) so this fetches once per
        // connection rather than needing a live WS push, same as the Bible file fetch — only when
        // the follower opted in via InstanceLinkSettings.mirrorBackgrounds (off by default, since
        // backgrounds are often venue-specific).
        var mirroredBackgroundSettings by remember { mutableStateOf<BackgroundSettings?>(null) }
        val instanceLinkConnectionStatusForBackgrounds by instanceLinkViewModel.connectionStatus.collectAsState()
        // backgroundsUpdatedSignal re-runs this when the primary announces a background change —
        // the asset cache is cleared first so the per-file exists() gate re-downloads fresh bytes.
        val instanceLinkBackgroundsSignal by instanceLinkViewModel.backgroundsUpdatedSignal.collectAsState()
        LaunchedEffect(instanceLinkConnectionStatusForBackgrounds, appSettings.instanceLink.mirrorBackgrounds, appSettings.instanceLink.role, instanceLinkBackgroundsSignal) {
            // Only in Controlled mode — a Controller keeps its own local backgrounds, same reasoning
            // as the Bible/Songs/Schedule mirror gating in MainDesktop.kt.
            if (instanceLinkConnectionStatusForBackgrounds != InstanceLinkStatus.CONNECTED ||
                !appSettings.instanceLink.mirrorBackgrounds ||
                appSettings.instanceLink.role != InstanceLinkRole.CONTROLLED
            ) {
                mirroredBackgroundSettings = null
                return@LaunchedEffect
            }
            if (instanceLinkBackgroundsSignal > 0) {
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
        // The single override point for rendering paths (PresenterWindows, BrowserSourceVideoRenderer,
        // and MainDesktop's live preview) — everywhere else appSettings is used as-is (editing,
        // persistence, general settings), since those should never show the mirrored backgrounds as
        // if they were this instance's own configuration.
        val effectiveAppSettings = remember(appSettings, mirroredBackgroundSettings) {
            mirroredBackgroundSettings?.let { appSettings.copy(backgroundSettings = it) } ?: appSettings
        }
        // Broadcasts this instance's live content to any connected InstanceLink follower — the
        // counterpart to the remoteLiveState collector above.
        LaunchedEffect(Unit) {
            presenterManager.onLiveStateChanged = { pm, source ->
                val verseCode = if (source == Presenting.BIBLE) {
                    pm.selectedVerse.value.takeIf { it.bookName.isNotEmpty() }?.let { v ->
                        primaryBibleForInstanceLink?.getBookIdByName(v.bookName)?.let { bookId ->
                            primaryBibleForInstanceLink?.getCodeReference(bookId, v.chapter, v.verseNumber)
                        }
                    }
                } else null
                companionServer.updateLiveState(
                    mode = source.name,
                    bibleVerse = pm.selectedVerse.value,
                    lyricSection = pm.lyricSection.value,
                    pictureImagePath = pm.selectedImagePath.value,
                    mediaUrl = pm.currentMediaUrl.value.ifEmpty { null },
                    mediaType = pm.currentMediaType.value.ifEmpty { null },
                    announcementText = pm.announcementText.value.ifEmpty { null },
                    websiteUrl = pm.websiteUrl.value.ifEmpty { null },
                    websiteTitle = pm.webPageTitle.value.ifEmpty { null },
                    sceneId = pm.activeScene.value?.id,
                    sceneName = pm.activeScene.value?.name,
                    questionId = pm.displayedQuestion.value?.id,
                    questionText = pm.displayedQuestion.value?.text,
                    dictionaryWord = pm.displayedDictionaryEntry.value?.word,
                    dictionaryEntry = pm.displayedDictionaryEntry.value,
                    lowerThirdName = pm.currentLowerThirdName.value.ifEmpty { null },
                    verseCode = verseCode,
                    songSectionIndex = if (source == Presenting.LYRICS) pm.songDisplaySectionIndex.value else null,
                    songLineIndex = if (source == Presenting.LYRICS) pm.songDisplayLineIndex.value else null
                )
            }
        }
        remember(qaManager) { companionServer.qaManager = qaManager; true }
        // Auto-connect OBS when settings change (or on first load if enabled)
        LaunchedEffect(
            appSettings.obsSettings.enabled,
            appSettings.obsSettings.host,
            appSettings.obsSettings.port,
            appSettings.obsSettings.password
        ) {
            if (appSettings.obsSettings.enabled) {
                obsManager.connect(
                    appSettings.obsSettings.host,
                    appSettings.obsSettings.port,
                    appSettings.obsSettings.password
                )
            } else {
                obsManager.disconnect()
            }
        }
        // Switch OBS scene when presenting mode changes
        LaunchedEffect(Unit) {
            snapshotFlow { presenterManager.presentingMode.value }
                .collect { mode ->
                    val obs = appSettings.obsSettings
                    if (!obs.enabled) return@collect
                    val sceneName = obs.sceneMappings[mode.name]?.takeIf { it.isNotBlank() }
                        ?: obs.defaultScene.takeIf { it.isNotBlank() }
                        ?: return@collect
                    obsManager.setScene(sceneName)
                }
        }
        // Sync QA settings to server — admin auth reuses the server API key, just like the presentation remote
        LaunchedEffect(appSettings.serverSettings.apiKeyEnabled, appSettings.serverSettings.apiKey, appSettings.qaSettings.rateLimitCooldownSeconds, appSettings.qaSettings.votingEnabled) {
            companionServer.qaAdminPassword = if (appSettings.serverSettings.apiKeyEnabled) appSettings.serverSettings.apiKey else ""
            companionServer.qaCooldownSeconds = appSettings.qaSettings.rateLimitCooldownSeconds
            companionServer.qaVotingEnabled = appSettings.qaSettings.votingEnabled
        }
        val tunnelStatus by companionServer.tunnelManager.status.collectAsState()
        val tunnelUrl by companionServer.tunnelManager.tunnelUrl.collectAsState()
        val prevTunnelWasConnected = remember { mutableStateOf(false) }
        var qaDisplayUrl by remember { mutableStateOf("") }
        var presentationDisplayUrl by remember { mutableStateOf("") }
        LaunchedEffect(tunnelStatus) {
            val isConnected = tunnelStatus is TunnelStatus.Connected
            if (prevTunnelWasConnected.value && !isConnected) {
                companionServer.clearPresentationState()
                qaDisplayUrl = ""
                presentationDisplayUrl = ""
            }
            prevTunnelWasConnected.value = isConnected
        }
        var presentationFrozen by remember { mutableStateOf(false) }
        LaunchedEffect(appSettings.presentationRemoteSettings.remoteControlEnabled, appSettings.serverSettings.apiKeyEnabled, appSettings.serverSettings.apiKey) {
            val activeApiKey = if (appSettings.serverSettings.apiKeyEnabled) appSettings.serverSettings.apiKey else ""
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
        val presentingModeValue = presenterManager.presentingMode.value
        LaunchedEffect(presentingModeValue) {
            companionServer.updatePresentationLiveStatus(presentingModeValue == Presenting.PRESENTATION)
        }
        // ── Browser Source outputs (OBS/vMix overlay) ─────────────────────────────
        // Each output gets its own off-screen renderer (BrowserSourceVideoRenderer) that
        // renders the same BiblePresenter/SongPresenter/AnnouncementsPresenter/PicturePresenter/
        // StageMonitorScreen composables used everywhere else, streamed to CompanionServer as
        // PNG frames — pixel-identical to the native output, no separate styling logic to
        // maintain. PresenterManager itself never leaves this scope; only each renderer's
        // frame flow crosses into CompanionServer.
        LaunchedEffect(appSettings.projectionSettings.browserSourceOutputs) {
            companionServer.updateBrowserSourceOutputs(appSettings.projectionSettings.browserSourceOutputs)
        }
        LaunchedEffect(appSettings.backgroundSettings) {
            companionServer.updateBackgroundSettings(appSettings.backgroundSettings)
        }
        val browserSourceServerUrlState = companionServer.serverUrl.collectAsState()
        appSettings.projectionSettings.browserSourceOutputs.indices.forEach { i ->
            composeKey(i) {
                // rememberUpdatedState, not remember { derivedStateOf { ... } } — appSettings and
                // qaDisplayUrl are plain composable parameters, not Compose State reads, so a
                // keyless derivedStateOf wrapping them would only ever capture their value from
                // this composeKey block's first-ever composition and never update again (no
                // tracked State read inside the calculation to invalidate on). That silently
                // froze every appSettings-driven Browser Source setting — background
                // image/type, fonts, colors, etc. — at whatever it was when the app started,
                // which is why a background image change only took effect after restarting the
                // app. effectiveModeState is unaffected since it genuinely reads presenterManager
                // State objects (.value), which derivedStateOf tracks correctly.
                val appSettingsState = rememberUpdatedState(effectiveAppSettings)
                val screenAssignmentState = rememberUpdatedState(
                    appSettings.projectionSettings.browserSourceOutputs.getOrNull(i) ?: ScreenAssignment()
                )
                val effectiveModeState = remember {
                    derivedStateOf { presenterManager.browserSourceLocks.value[i] ?: presenterManager.presentingMode.value }
                }
                val qaDisplayUrlState = rememberUpdatedState(qaDisplayUrl)
                // Keyed on geometry/fps so a settings change tears the renderer down and builds a
                // fresh one; registerBrowserSourceFrames then closes connected clients, which
                // reconnect and reseed with a full frame at the new size.
                val bsOutput = appSettings.projectionSettings.browserSourceOutputs.getOrNull(i) ?: ScreenAssignment()
                val renderer = remember(i, bsOutput.browserSourceWidth, bsOutput.browserSourceHeight, bsOutput.browserSourceFps) {
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
        // Same backfill mechanism as remoteSelectSongFlow — a remote PROJECT go-live only adds the
        // item to the schedule and flips presentingMode; these flows drive MainDesktop to actually
        // load and push real content (see executeProjectItem below, which deliberately does NOT push
        // picture/slide content itself).
        val remoteSelectPictureFlow =
            remember { kotlinx.coroutines.flow.MutableSharedFlow<ScheduleItem.PictureItem>(extraBufferCapacity = 8) }
        val remoteSelectPresentationFlow =
            remember { kotlinx.coroutines.flow.MutableSharedFlow<ScheduleItem.PresentationItem>(extraBufferCapacity = 8) }
        var dialogDismissSignal by remember { mutableStateOf(0) }
        var showOptionsDialog by remember { mutableStateOf(false) }
        var optionsDialogInitialTab by remember { mutableStateOf(0) }
        // Single entry point so every open site picks its tab explicitly
        val openOptionsDialog: (Int) -> Unit = { tab ->
            optionsDialogInitialTab = tab
            showOptionsDialog = true
        }
        var showStatisticsDialog by remember { mutableStateOf(false) }
        var showInstanceLinkDialog by remember { mutableStateOf(false) }
        var showKeyboardShortcutsDialog by remember { mutableStateOf(false) }
        var showAboutDialog by remember { mutableStateOf(false) }
        var showContactDialog by remember { mutableStateOf(false) }
        var showConverterWindow by remember { mutableStateOf(false) }
        var showLottieGenWindow by remember { mutableStateOf(false) }
        var lottieGenOutputDir by remember { mutableStateOf<java.io.File?>(null) }
        var lottieGenOnFileSaved by remember { mutableStateOf<(() -> Unit)?>(null) }
        var pendingUpdateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
        var pendingUpdateCheckWasManual by remember { mutableStateOf(false) }
        var selectedScheduleItemId by remember { mutableStateOf<String?>(null) }

        // Preload songs and bible at startup, then signal ready
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                companionServer.preloadData(
                    songStorageDir = appSettings.songSettings.storageDirectory,
                    bibleStorageDir = appSettings.bibleSettings.storageDirectory,
                    primaryBibleFileName = appSettings.bibleSettings.primaryBible
                )
                // Seed API key from saved settings before starting, so the first
                // request is already checked against the correct key.
                companionServer.updateApiKey(
                    enabled = appSettings.serverSettings.apiKeyEnabled,
                    key = appSettings.serverSettings.apiKey
                )
                companionServer.updateFileUploadEnabled(appSettings.serverSettings.fileUploadEnabled)
                companionServer.updateAtemConfig(
                    appSettings.atemSettings,
                    appSettings.streamingSettings.lowerThirdFolder
                )
                // Auto-start server if user previously enabled it
                if (appSettings.serverSettings.enabled) {
                    companionServer.start(
                        port = appSettings.serverSettings.port,
                        hostOverride = appSettings.serverSettings.serverHost
                    )
                }
            }
            appReady = true
            // Check for updates in background after startup, respecting the configured interval
            val isFirstEverUpdateCheck = appSettings.lastUpdateCheckTimestamp == 0L
            if (appSettings.updateCheckInterval.isDueSince(appSettings.lastUpdateCheckTimestamp)) {
                val result = UpdateChecker.checkForUpdate(includePrereleases = appSettings.participateInPrereleases)
                appSettings = appSettings.copy(lastUpdateCheckTimestamp = System.currentTimeMillis())
                settingsManager.saveSettings(appSettings)
                if (isFirstEverUpdateCheck) {
                    // Let the user pick their update-check frequency the first time it ever runs.
                    pendingUpdateResult = result
                    pendingUpdateCheckWasManual = true
                } else if (result is UpdateCheckResult.Available) {
                    pendingUpdateResult = result
                    pendingUpdateCheckWasManual = false
                }
            }
        }


        val screens = rememberScreenDevices()
        val savedPlacement = when (appSettings.windowPlacement) {
            "floating" -> WindowPlacement.Floating
            "fullscreen" -> WindowPlacement.Fullscreen
            else -> WindowPlacement.Maximized
        }
        // Use OS primary monitor bounds so maximized/fullscreen stays on one screen
        val primaryBounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice.defaultConfiguration.bounds
        val state = rememberWindowState(
            placement = savedPlacement,
            position = if (savedPlacement == WindowPlacement.Floating && appSettings.windowX >= 0)
                WindowPosition(appSettings.windowX.dp, appSettings.windowY.dp)
            else WindowPosition(primaryBounds.x.dp, primaryBounds.y.dp),
            size = if (savedPlacement == WindowPlacement.Floating)
                DpSize(appSettings.windowWidth.dp, appSettings.windowHeight.dp)
            else DpSize(primaryBounds.width.dp, primaryBounds.height.dp)
        )

        // Splash screen while app is loading
        if (!appReady) {
            SplashWindow(theme = theme)
        }

        if (appReady && eulaAccepted) {
            Window(
                onCloseRequest = {
                    val placementStr = when (state.placement) {
                        WindowPlacement.Floating -> "floating"
                        WindowPlacement.Fullscreen -> "fullscreen"
                        WindowPlacement.Maximized -> "maximized"
                    }
                    val isFloating = state.placement == WindowPlacement.Floating
                    appSettings = appSettings.copy(
                        windowPlacement = placementStr,
                        windowWidth = if (isFloating) state.size.width.value.toInt() else appSettings.windowWidth,
                        windowHeight = if (isFloating) state.size.height.value.toInt() else appSettings.windowHeight,
                        windowX = if (isFloating) state.position.x.value.toInt() else -1,
                        windowY = if (isFloating) state.position.y.value.toInt() else -1
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
                MacMenuBarActivationFix()
                LanguageProvider(language = currentLanguage) {
                    AppThemeWrapper(theme = theme) {
                        CompositionLocalProvider(
                            LocalMediaViewModel provides mediaViewModel,
                            LocalMainWindowState provides state
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {

                                // ── Remote API permission state (inside Window so schedule actions are live) ──
                                // Each entry: Triple(RemoteEvent, allowAction, denyAction)
                                val remoteEventQueue =
                                    remember { mutableStateListOf<Triple<RemoteEvent, () -> Unit, () -> Unit>>() }

                                // Persistent allow/block lists (survive app restarts)
                                val remoteClientManager = remember { RemoteClientManager() }
                                // Session-only sets (cleared on app restart)
                                val sessionAllowedClients =
                                    remember { mutableStateListOf<String>() }
                                val sessionBlockedClients =
                                    remember { mutableStateListOf<String>() }
                                // Activity toasts for already-allowed clients (auto-approved actions)
                                val remoteActivityNotifications =
                                    remember { mutableStateListOf<RemoteActivityNotification>() }

                                // ── Remote add-to-schedule requests ──────────────────────────────────────────
                                LaunchedEffect(Unit) {
                                    companionServer.onAddToSchedule.collect { pending ->
                                        val clientId = pending.clientId
                                        // Permanent block → auto-reject
                                        if (remoteClientManager.isBlocked(clientId)) {
                                            pending.decision.complete(false)
                                            return@collect
                                        }
                                        // Session block → auto-reject
                                        if (clientId.isNotBlank() && sessionBlockedClients.contains(clientId)) {
                                            pending.decision.complete(false)
                                            return@collect
                                        }
                                        // Permanent allow or session allow → auto-approve
                                        if (remoteClientManager.isAllowed(clientId) ||
                                            (clientId.isNotBlank() && sessionAllowedClients.contains(clientId))
                                        ) {
                                            val item = pending.item
                                            when (item) {
                                                is ScheduleItem.SongItem -> {
                                                    currentScheduleActions.addSong(
                                                        item.songNumber,
                                                        item.title,
                                                        item.songbook,
                                                        item.songId
                                                    )
                                                    coroutineScope.launch { remoteSelectSongFlow.emit(item) }
                                                }

                                                is ScheduleItem.BibleVerseItem ->
                                                    currentScheduleActions.addBibleVerse(
                                                        item.bookName,
                                                        item.chapter,
                                                        item.verseNumber,
                                                        item.verseText,
                                                        item.verseRange,
                                                        item.bookId
                                                    )

                                                is ScheduleItem.PresentationItem ->
                                                    currentScheduleActions.addPresentation(
                                                        item.filePath,
                                                        item.fileName,
                                                        item.slideCount,
                                                        item.fileType
                                                    )

                                                is ScheduleItem.PictureItem ->
                                                    currentScheduleActions.addPicture(
                                                        item.folderPath,
                                                        item.folderName,
                                                        item.imageCount
                                                    )

                                                is ScheduleItem.MediaItem ->
                                                    currentScheduleActions.addMedia(
                                                        item.mediaUrl,
                                                        item.mediaTitle,
                                                        item.mediaType
                                                    )

                                                else -> Unit
                                            }
                                            pending.decision.complete(true)
                                            // Show activity toast so operator is aware
                                            val (eTitle, eDetail) = remoteEventLabel(item)
                                            remoteActivityNotifications.add(RemoteActivityNotification(
                                                type = RemoteEventType.ADD_TO_SCHEDULE,
                                                title = eTitle,
                                                detail = eDetail,
                                                clientId = clientId,
                                                clientLabel = remoteClientManager.getLabel(clientId)
                                            ))
                                            return@collect
                                        }
                                        val item = pending.item
                                        val (eventTitle, eventDetail) = remoteEventLabel(item)
                                        val event = RemoteEvent(
                                            type = RemoteEventType.ADD_TO_SCHEDULE,
                                            title = eventTitle,
                                            detail = eventDetail,
                                            clientId = clientId,
                                            clientLabel = remoteClientManager.getLabel(clientId)
                                        )
                                        val allow: () -> Unit = {
                                            when (item) {
                                                is ScheduleItem.SongItem -> {
                                                    currentScheduleActions.addSong(
                                                        item.songNumber,
                                                        item.title,
                                                        item.songbook,
                                                        item.songId
                                                    )
                                                    coroutineScope.launch { remoteSelectSongFlow.emit(item) }
                                                }

                                                is ScheduleItem.BibleVerseItem ->
                                                    currentScheduleActions.addBibleVerse(
                                                        item.bookName,
                                                        item.chapter,
                                                        item.verseNumber,
                                                        item.verseText,
                                                        item.verseRange,
                                                        item.bookId
                                                    )

                                                is ScheduleItem.PresentationItem ->
                                                    currentScheduleActions.addPresentation(
                                                        item.filePath,
                                                        item.fileName,
                                                        item.slideCount,
                                                        item.fileType
                                                    )

                                                is ScheduleItem.PictureItem ->
                                                    currentScheduleActions.addPicture(
                                                        item.folderPath,
                                                        item.folderName,
                                                        item.imageCount
                                                    )

                                                is ScheduleItem.MediaItem ->
                                                    currentScheduleActions.addMedia(
                                                        item.mediaUrl,
                                                        item.mediaTitle,
                                                        item.mediaType
                                                    )

                                                else -> Unit
                                            }
                                            pending.decision.complete(true)
                                        }
                                        val deny: () -> Unit = { pending.decision.complete(false) }
                                        remoteEventQueue.add(Triple(event, allow, deny))
                                    }
                                }

                                // ── Remote remove-from-schedule requests ──────────────────────────────────────
                                LaunchedEffect(Unit) {
                                    companionServer.onRemoveFromSchedule.collect { pending ->
                                        val clientId = pending.clientId
                                        // Permanent block → auto-reject
                                        if (remoteClientManager.isBlocked(clientId)) {
                                            pending.decision.complete(false)
                                            return@collect
                                        }
                                        // Session block → auto-reject
                                        if (clientId.isNotBlank() && sessionBlockedClients.contains(clientId)) {
                                            pending.decision.complete(false)
                                            return@collect
                                        }
                                        // Permanent allow or session allow → auto-approve
                                        if (remoteClientManager.isAllowed(clientId) ||
                                            (clientId.isNotBlank() && sessionAllowedClients.contains(clientId))
                                        ) {
                                            currentScheduleActions.removeById(pending.id)
                                            pending.decision.complete(true)
                                            // Show activity toast so operator is aware
                                            remoteActivityNotifications.add(RemoteActivityNotification(
                                                type = RemoteEventType.REMOVE_FROM_SCHEDULE,
                                                title = pending.label,
                                                clientId = clientId,
                                                clientLabel = remoteClientManager.getLabel(clientId)
                                            ))
                                            return@collect
                                        }
                                        val event = RemoteEvent(
                                            type = RemoteEventType.REMOVE_FROM_SCHEDULE,
                                            title = pending.label,
                                            clientId = clientId,
                                            clientLabel = remoteClientManager.getLabel(clientId)
                                        )
                                        val allow: () -> Unit = {
                                            currentScheduleActions.removeById(pending.id)
                                            pending.decision.complete(true)
                                        }
                                        val deny: () -> Unit = { pending.decision.complete(false) }
                                        remoteEventQueue.add(Triple(event, allow, deny))
                                    }
                                }

                                // ── Remote add-batch-to-schedule requests ─────────────────────────────────────
                                LaunchedEffect(Unit) {
                                    companionServer.onAddBatchToSchedule.collect { pending ->
                                        val clientId = pending.clientId
                                        // Permanent block or session block → auto-reject
                                        if (remoteClientManager.isBlocked(clientId) ||
                                            (clientId.isNotBlank() && sessionBlockedClients.contains(clientId))
                                        ) {
                                            pending.decision.complete(false)
                                            return@collect
                                        }
                                        // Permanent allow or session allow → auto-approve
                                        if (remoteClientManager.isAllowed(clientId) ||
                                            (clientId.isNotBlank() && sessionAllowedClients.contains(clientId))
                                        ) {
                                            for (item in pending.items) {
                                                when (item) {
                                                    is ScheduleItem.SongItem -> {
                                                        currentScheduleActions.addSong(
                                                            item.songNumber,
                                                            item.title,
                                                            item.songbook,
                                                            item.songId
                                                        )
                                                        coroutineScope.launch { remoteSelectSongFlow.emit(item) }
                                                    }

                                                    is ScheduleItem.BibleVerseItem ->
                                                        currentScheduleActions.addBibleVerse(
                                                            item.bookName,
                                                            item.chapter,
                                                            item.verseNumber,
                                                            item.verseText,
                                                            item.verseRange,
                                                            item.bookId
                                                        )

                                                    is ScheduleItem.PresentationItem ->
                                                        currentScheduleActions.addPresentation(
                                                            item.filePath,
                                                            item.fileName,
                                                            item.slideCount,
                                                            item.fileType
                                                        )

                                                    is ScheduleItem.PictureItem ->
                                                        currentScheduleActions.addPicture(
                                                            item.folderPath,
                                                            item.folderName,
                                                            item.imageCount
                                                        )

                                                    is ScheduleItem.MediaItem ->
                                                        currentScheduleActions.addMedia(
                                                            item.mediaUrl,
                                                            item.mediaTitle,
                                                            item.mediaType
                                                        )

                                                    else -> Unit
                                                }
                                            }
                                            pending.decision.complete(true)
                                            // Show activity toast so operator is aware
                                            val batchCount = pending.items.size
                                            val batchTitle = if (batchCount == 1)
                                                remoteEventLabel(pending.items.first()).first
                                            else "$batchCount items"
                                            val batchDetail = pending.items.take(3).joinToString(" · ") { item ->
                                                when (item) {
                                                    is ScheduleItem.BibleVerseItem -> "${item.bookName} ${item.chapter}:${item.verseNumber}"
                                                    is ScheduleItem.SongItem -> "${item.songNumber} – ${item.title}"
                                                    else -> item.displayText.take(30)
                                                }
                                            }.let { if (batchCount > 3) "$it …" else it }
                                            remoteActivityNotifications.add(RemoteActivityNotification(
                                                type = RemoteEventType.ADD_TO_SCHEDULE,
                                                title = batchTitle,
                                                detail = batchDetail,
                                                clientId = clientId,
                                                clientLabel = remoteClientManager.getLabel(clientId)
                                            ))
                                            return@collect
                                        }
                                        val count = pending.items.size
                                        // Build a human-readable summary: first 3 items joined, then "…" if more
                                        val summaryTitle = if (count == 1) {
                                            remoteEventLabel(pending.items.first()).first
                                        } else {
                                            "$count items"
                                        }
                                        val summaryDetail = pending.items.take(3).joinToString(" · ") { item ->
                                            when (item) {
                                                is ScheduleItem.BibleVerseItem ->
                                                    "${item.bookName} ${item.chapter}:${item.verseNumber}"

                                                is ScheduleItem.SongItem ->
                                                    "${item.songNumber} – ${item.title}"

                                                else -> item.displayText.take(30)
                                            }
                                        }.let { if (count > 3) "$it …" else it }
                                        val event = RemoteEvent(
                                            type = RemoteEventType.ADD_TO_SCHEDULE,
                                            title = summaryTitle,
                                            detail = summaryDetail,
                                            clientId = clientId,
                                            clientLabel = remoteClientManager.getLabel(clientId)
                                        )
                                        val allow: () -> Unit = {
                                            for (item in pending.items) {
                                                when (item) {
                                                    is ScheduleItem.SongItem -> {
                                                        currentScheduleActions.addSong(
                                                            item.songNumber,
                                                            item.title,
                                                            item.songbook,
                                                            item.songId
                                                        )
                                                        coroutineScope.launch { remoteSelectSongFlow.emit(item) }
                                                    }

                                                    is ScheduleItem.BibleVerseItem ->
                                                        currentScheduleActions.addBibleVerse(
                                                            item.bookName,
                                                            item.chapter,
                                                            item.verseNumber,
                                                            item.verseText,
                                                            item.verseRange,
                                                            item.bookId
                                                        )

                                                    is ScheduleItem.PresentationItem ->
                                                        currentScheduleActions.addPresentation(
                                                            item.filePath,
                                                            item.fileName,
                                                            item.slideCount,
                                                            item.fileType
                                                        )

                                                    is ScheduleItem.PictureItem ->
                                                        currentScheduleActions.addPicture(
                                                            item.folderPath,
                                                            item.folderName,
                                                            item.imageCount
                                                        )

                                                    is ScheduleItem.MediaItem ->
                                                        currentScheduleActions.addMedia(
                                                            item.mediaUrl,
                                                            item.mediaTitle,
                                                            item.mediaType
                                                        )

                                                    else -> Unit
                                                }
                                            }
                                            pending.decision.complete(true)
                                        }
                                        val deny: () -> Unit = { pending.decision.complete(false) }
                                        remoteEventQueue.add(Triple(event, allow, deny))
                                    }
                                }

                                // ── Remote project requests ──────────────────────────────────────────────────
                                LaunchedEffect(Unit) {
                                    companionServer.onProject.collect { pending ->
                                        val clientId = pending.clientId
                                        // Permanent block or session block → auto-reject
                                        if (remoteClientManager.isBlocked(clientId) ||
                                            (clientId.isNotBlank() && sessionBlockedClients.contains(clientId))
                                        ) {
                                            pending.decision.complete(false)
                                            return@collect
                                        }
                                        // Permanent allow or session allow → auto-approve
                                        if (remoteClientManager.isAllowed(clientId) ||
                                            (clientId.isNotBlank() && sessionAllowedClients.contains(clientId))
                                        ) {
                                            val item = pending.item
                                            executeProjectItem(
                                                item,
                                                currentScheduleActions,
                                                presenterManager,
                                                statisticsManager
                                            )
                                            if (item is ScheduleItem.SongItem) {
                                                coroutineScope.launch { remoteSelectSongFlow.emit(item) }
                                            }
                                            if (item is ScheduleItem.PictureItem) {
                                                coroutineScope.launch { remoteSelectPictureFlow.emit(item) }
                                            }
                                            if (item is ScheduleItem.PresentationItem) {
                                                coroutineScope.launch { remoteSelectPresentationFlow.emit(item) }
                                            }
                                            pending.decision.complete(true)
                                            // Show activity toast so operator is aware
                                            val (pTitle, pDetail) = remoteEventLabel(item)
                                            remoteActivityNotifications.add(RemoteActivityNotification(
                                                type = RemoteEventType.PROJECT,
                                                title = pTitle,
                                                detail = pDetail,
                                                clientId = clientId,
                                                clientLabel = remoteClientManager.getLabel(clientId)
                                            ))
                                            return@collect
                                        }
                                        val item = pending.item
                                        val (eventTitle, eventDetail) = remoteEventLabel(item)
                                        val event = RemoteEvent(
                                            type = RemoteEventType.PROJECT,
                                            title = eventTitle,
                                            detail = eventDetail,
                                            clientId = clientId,
                                            clientLabel = remoteClientManager.getLabel(clientId)
                                        )
                                        val allow: () -> Unit = {
                                            executeProjectItem(
                                                item,
                                                currentScheduleActions,
                                                presenterManager,
                                                statisticsManager
                                            )
                                            // Also drive Songs tab selection for song items
                                            if (item is ScheduleItem.SongItem) {
                                                coroutineScope.launch { remoteSelectSongFlow.emit(item) }
                                            }
                                            if (item is ScheduleItem.PictureItem) {
                                                coroutineScope.launch { remoteSelectPictureFlow.emit(item) }
                                            }
                                            if (item is ScheduleItem.PresentationItem) {
                                                coroutineScope.launch { remoteSelectPresentationFlow.emit(item) }
                                            }
                                            pending.decision.complete(true)
                                        }
                                        val deny: () -> Unit = { pending.decision.complete(false) }
                                        remoteEventQueue.add(Triple(event, allow, deny))
                                    }
                                }

                                // ── Remote song-section navigation ───────────────────────────────────────────
                                // Fires when a mobile client calls POST /api/songs/{n}/select or sends
                                // WS "select_song_section".  No approval required — applied instantly.
                                LaunchedEffect(Unit) {
                                    companionServer.onSelectSongSection.collect { req ->
                                        val sections = presenterManager.allLyricSections.value
                                        val section = sections.getOrNull(req.section) ?: return@collect
                                        presenterManager.setLyricSection(section)
                                        presenterManager.setSongDisplaySectionIndex(req.section)
                                        presenterManager.setSongDisplayLineIndex(if (req.lineIndex >= 0) req.lineIndex else -1)
                                        // Make sure the presenter is showing lyrics
                                        if (presenterManager.presentingMode.value != Presenting.LYRICS) {
                                            presenterManager.setPresentingMode(Presenting.LYRICS)
                                            presenterManager.setShowPresenterWindow(true)
                                        }
                                    }
                                }

                                // ── Remote clear / display-off ────────────────────────────────────────────────
                                // Fires when a mobile client calls POST /api/clear or sends WS "clear".
                                LaunchedEffect(Unit) {
                                    companionServer.onClear.collect {
                                        mediaViewModel.pause()
                                        presenterManager.requestClearDisplay()
                                    }
                                }

                                // ── Companion lower-third sequence (POST /api/lowerthirds/{name}/run) ───────
                                // The sequencer handles the ATEM DSK and timing; these collectors do the
                                // same go-live / off-air the Lower Third tab does.
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
                                        if (presenterManager.presentingMode.value == Presenting.LOWER_THIRD) {
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

                                // ── Remote QA admin requests (add / edit / delete) ───────────────────────────
                                LaunchedEffect(Unit) {
                                    companionServer.onQAAdminRequest.collect { pending ->
                                        val clientId = pending.clientId
                                        if (remoteClientManager.isBlocked(clientId) ||
                                            (clientId.isNotBlank() && sessionBlockedClients.contains(clientId))
                                        ) {
                                            pending.decision.complete(false)
                                            return@collect
                                        }
                                        if (remoteClientManager.isAllowed(clientId) ||
                                            (clientId.isNotBlank() && sessionAllowedClients.contains(clientId))
                                        ) {
                                            pending.decision.complete(true)
                                            remoteActivityNotifications.add(RemoteActivityNotification(
                                                type = qaActionType(pending.action),
                                                title = pending.text.take(80),
                                                clientId = clientId,
                                                clientLabel = remoteClientManager.getLabel(clientId)
                                            ))
                                            return@collect
                                        }
                                        val eventType = qaActionType(pending.action)
                                        val event = RemoteEvent(
                                            type = eventType,
                                            title = pending.text.take(80),
                                            clientId = clientId,
                                            clientLabel = remoteClientManager.getLabel(clientId)
                                        )
                                        val allow: () -> Unit = { pending.decision.complete(true) }
                                        val deny: () -> Unit  = { pending.decision.complete(false) }
                                        remoteEventQueue.add(Triple(event, allow, deny))
                                    }
                                }

                                // ── Presentation remote connection requests ───────────────────────────────────
                                LaunchedEffect(Unit) {
                                    companionServer.onPresentationRemoteConnect.collect { pending ->
                                        val clientId = pending.clientId
                                        if (remoteClientManager.isBlocked(clientId) ||
                                            (clientId.isNotBlank() && sessionBlockedClients.contains(clientId))
                                        ) {
                                            pending.decision.complete(false)
                                            return@collect
                                        }
                                        if (remoteClientManager.isAllowed(clientId) ||
                                            (clientId.isNotBlank() && sessionAllowedClients.contains(clientId))
                                        ) {
                                            pending.decision.complete(true)
                                            remoteActivityNotifications.add(RemoteActivityNotification(
                                                type = RemoteEventType.PRESENTATION_CONNECT,
                                                title = "",
                                                clientId = clientId,
                                                clientLabel = remoteClientManager.getLabel(clientId)
                                            ))
                                            return@collect
                                        }
                                        val event = RemoteEvent(
                                            type = RemoteEventType.PRESENTATION_CONNECT,
                                            title = "",
                                            clientId = clientId,
                                            clientLabel = remoteClientManager.getLabel(clientId)
                                        )
                                        val allow: () -> Unit = { pending.decision.complete(true) }
                                        val deny: () -> Unit  = { pending.decision.complete(false) }
                                        remoteEventQueue.add(Triple(event, allow, deny))
                                    }
                                }

                                // ── Q&A admin connection requests ─────────────────────────────────────────────
                                LaunchedEffect(Unit) {
                                    companionServer.onQaAdminConnect.collect { pending ->
                                        val clientId = pending.clientId
                                        if (remoteClientManager.isBlocked(clientId) ||
                                            (clientId.isNotBlank() && sessionBlockedClients.contains(clientId))
                                        ) {
                                            pending.decision.complete(false)
                                            return@collect
                                        }
                                        if (remoteClientManager.isAllowed(clientId) ||
                                            (clientId.isNotBlank() && sessionAllowedClients.contains(clientId))
                                        ) {
                                            pending.decision.complete(true)
                                            remoteActivityNotifications.add(RemoteActivityNotification(
                                                type = RemoteEventType.QA_ADMIN_CONNECT,
                                                title = "",
                                                clientId = clientId,
                                                clientLabel = remoteClientManager.getLabel(clientId)
                                            ))
                                            return@collect
                                        }
                                        val event = RemoteEvent(
                                            type = RemoteEventType.QA_ADMIN_CONNECT,
                                            title = "",
                                            clientId = clientId,
                                            clientLabel = remoteClientManager.getLabel(clientId)
                                        )
                                        val allow: () -> Unit = { pending.decision.complete(true) }
                                        val deny: () -> Unit  = { pending.decision.complete(false) }
                                        remoteEventQueue.add(Triple(event, allow, deny))
                                    }
                                }

                                // ── Remote Bible hold toggle ──────────────────────────────────────────────────
                                LaunchedEffect(Unit) {
                                    companionServer.onBibleHold.collect { hold ->
                                        presenterManager.setBibleHold(hold)
                                    }
                                }

                                // ── Notify mobile clients when display is cleared ─────────────────────────────
                                LaunchedEffect(Unit) {
                                    snapshotFlow { presenterManager.presentingMode.value }
                                        .collect { mode ->
                                            if (mode == Presenting.NONE) {
                                                companionServer.broadcastDisplayCleared()
                                            }
                                        }
                                }

                                // ── Notify mobile clients when song section changes ──────────────────────────
                                LaunchedEffect(Unit) {
                                    snapshotFlow { presenterManager.songDisplaySectionIndex.value }
                                        .collect { index ->
                                            if (presenterManager.presentingMode.value == Presenting.LYRICS) {
                                                companionServer.broadcastSongSectionSelected(index)
                                            }
                                        }
                                }

                                // ── Instant-action activity toasts ────────────────────────────────────────────
                                // For every no-approval action (present, upload, clear) show a toast so the
                                // operator can see what a remote client just did and optionally block them.
                                LaunchedEffect(Unit) {
                                    companionServer.onInstantAction.collect { action ->
                                        val type = when (action.actionType) {
                                            "present" -> RemoteEventType.PRESENT
                                            "upload"  -> RemoteEventType.UPLOAD
                                            "clear"   -> RemoteEventType.CLEAR
                                            else      -> RemoteEventType.PRESENT
                                        }
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
                                    isInstanceLinkConnected = instanceLinkViewModel.connectionStatus.collectAsState().value != InstanceLinkStatus.DISCONNECTED,
                                    onConverter = { showConverterWindow = true },
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
                                            appSettings = appSettings.copy(lastUpdateCheckTimestamp = System.currentTimeMillis())
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
                                )
                                // Crash recovery warning banner
                                if (CrashReporter.didCrashLastRun && CrashReporter.videoBackgroundsDisabled) {
                                    var showBanner by remember { mutableStateOf(true) }
                                    if (showBanner) {
                                        Box(
                                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Video backgrounds disabled after ${CrashReporter.consecutiveCrashes} consecutive crashes.  [Re-enable]  [Dismiss]",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.onPreviewKeyEvent {
                                                    showBanner = false; true
                                                }
                                            )
                                        }
                                        // Auto-dismiss after 15 seconds
                                        LaunchedEffect(Unit) {
                                            delay(15_000)
                                            showBanner = false
                                        }
                                    }
                                }

                                val instanceLinkIsControllerConnected =
                                    instanceLinkViewModel.connectionStatus.collectAsState().value == InstanceLinkStatus.CONNECTED &&
                                        appSettings.instanceLink.role == InstanceLinkRole.CONTROLLER
                                MainDesktop(
                                    hostWindow = window,
                                    instanceLinkConnectionStatus = instanceLinkViewModel.connectionStatus.collectAsState().value,
                                    instanceLinkNextRetryAtMs = instanceLinkViewModel.nextRetryAtMs.collectAsState().value,
                                    instanceLinkBibleUpdatedSignal = instanceLinkViewModel.bibleUpdatedSignal.collectAsState().value,
                                    instanceLinkSecondaryBibleUpdatedSignal = instanceLinkViewModel.secondaryBibleUpdatedSignal.collectAsState().value,
                                    instanceLinkFollowingHost = appSettings.instanceLink.primaryHost,
                                    connectedInstanceLinkFollowerCount = companionServer.connectedInstanceLinkFollowers.collectAsState().value.size,
                                    onInstanceLinkConnect = {
                                        val link = appSettings.instanceLink
                                        instanceLinkViewModel.connect(
                                            link.primaryHost, link.primaryPort, link.apiKey, link.deviceId,
                                            link.reconnectDelayMs.toLong()
                                        )
                                    },
                                    onInstanceLinkDisconnect = { instanceLinkViewModel.disconnect() },
                                    instanceLinkRemoteSchedule = instanceLinkViewModel.remoteSchedule.collectAsState().value,
                                    instanceLinkRemoteSongCatalog = instanceLinkViewModel.remoteSongCatalog.collectAsState().value,
                                    instanceLinkFetchSongDetail = { number, songbook -> instanceLinkViewModel.fetchSongDetail(number, songbook) },
                                    instanceLinkFetchBibleFile = { instanceLinkViewModel.fetchBibleFile() },
                                    instanceLinkBibleSyncMode = appSettings.instanceLink.bibleSyncMode,
                                    instanceLinkFetchSecondaryBibleFile = { instanceLinkViewModel.fetchSecondaryBibleFile() },
                                    instanceLinkOnSecondaryBibleFilePathChanged = { path -> companionServer.updateSecondaryBibleFilePath(path) },
                                    instanceLinkSendAddToSchedule = if (appSettings.instanceLink.allowPushToSchedule) {
                                        { item -> instanceLinkViewModel.sendAddToSchedule(item) }
                                    } else null,
                                    instanceLinkSendRemoveFromSchedule = if (appSettings.instanceLink.allowPushToSchedule) {
                                        { id -> instanceLinkViewModel.sendRemoveFromSchedule(id) }
                                    } else null,
                                    instanceLinkRole = appSettings.instanceLink.role,
                                    instanceLinkSendProject = if (instanceLinkIsControllerConnected) {
                                        { item -> instanceLinkViewModel.sendProject(item) }
                                    } else null,
                                    instanceLinkSendVerse = if (instanceLinkIsControllerConnected) {
                                        { bookName, chapter, verseNumber, verseText, verseRange ->
                                            instanceLinkViewModel.sendSelectBibleVerse(bookName, chapter, verseNumber, verseText, verseRange)
                                        }
                                    } else null,
                                    instanceLinkSendPicture = if (instanceLinkIsControllerConnected) {
                                        { folderId, index, fileName -> instanceLinkViewModel.sendSelectPicture(folderId, index, fileName) }
                                    } else null,
                                    instanceLinkSendSongSection = if (instanceLinkIsControllerConnected) {
                                        { number, section, lineIndex -> instanceLinkViewModel.sendSelectSongSection(number, section, lineIndex) }
                                    } else null,
                                    instanceLinkSendSlide = if (instanceLinkIsControllerConnected) {
                                        { id, index -> instanceLinkViewModel.sendSelectSlide(id, index) }
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
                                    instanceLinkFetchPictureImageBytes = if (instanceLinkViewModel.connectionStatus.collectAsState().value == InstanceLinkStatus.CONNECTED) {
                                        { folderId, index -> instanceLinkViewModel.fetchPictureImageBytes(folderId, index) }
                                    } else null,
                                    instanceLinkFetchPresentationSlideBytes = if (instanceLinkViewModel.connectionStatus.collectAsState().value == InstanceLinkStatus.CONNECTED) {
                                        { id, index -> instanceLinkViewModel.fetchPresentationSlideBytes(id, index) }
                                    } else null,
                                    instanceLinkMediaStreamUrl = run {
                                        val link = appSettings.instanceLink
                                        if (instanceLinkViewModel.connectionStatus.collectAsState().value == InstanceLinkStatus.CONNECTED) {
                                            ({ itemId: String ->
                                                val keyParam = if (link.apiKey.isNotEmpty()) "?${Constants.QUERY_PARAM_API_KEY}=${link.apiKey}" else ""
                                                "http://${link.primaryHost}:${link.primaryPort}${Constants.ENDPOINT_MEDIA_STREAM}/$itemId$keyParam"
                                            })
                                        } else null
                                    },
                                    onVerseSelected = { verses -> presenterManager.setSelectedVerses(verses) },
                                    onSongItemSelected = { section ->
                                        presenterManager.setLyricSection(section)
                                        // In line mode, sync displayedLyricSection immediately so it updates
                                        // in the same Compose snapshot as songDisplayLineIndex. Without this,
                                        // there is an intermediate recomposition where songDisplayLineIndex=0
                                        // but displayedLyricSection still points to the old verse, causing the
                                        // first line of the old verse to flash briefly on verse boundaries.
                                        val ss = appSettings.songSettings
                                        val inLineMode = ss.fullscreenDisplayMode == Constants.SONG_DISPLAY_MODE_LINE ||
                                            ss.lowerThirdDisplayMode == Constants.SONG_DISPLAY_MODE_LINE ||
                                            ss.lookAheadDisplayMode == Constants.SONG_DISPLAY_MODE_LINE ||
                                            ss.lowerThirdLookAheadDisplayMode == Constants.SONG_DISPLAY_MODE_LINE
                                        if (inLineMode) {
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
                                    onScheduleActionsReady = { scheduleActions = it },
                                    presenting = { mode ->
                                        presenterManager.setPresentingMode(mode)
                                        if (mode != Presenting.NONE) presenterManager.setShowPresenterWindow(true)
                                    },
                                    onScheduleItemSelected = { itemId -> selectedScheduleItemId = itemId },
                                    onShowSettings = { openOptionsDialog(0) },
                                    onShowBackgroundSettings = { openOptionsDialog(3) },
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
                                            filePath = File(appSettings.bibleSettings.storageDirectory, translation).absolutePath
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
                                    onStartTunnel = { companionServer.tunnelManager.start(appSettings.serverSettings.port) },
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
                                        if (outputDir.isNotEmpty() && java.io.File(outputDir).isDirectory) {
                                            lottieGenOutputDir = java.io.File(outputDir)
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
                                    companionSatelliteViewModel = companionSatelliteViewModel
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
                                        // Re-preload in case bible/song directories changed
                                        companionServer.preloadData(
                                            songStorageDir = updated.songSettings.storageDirectory,
                                            bibleStorageDir = updated.bibleSettings.storageDirectory,
                                            primaryBibleFileName = updated.bibleSettings.primaryBible
                                        )
                                        // Keep API key enforcement in sync with saved settings
                                        companionServer.updateApiKey(
                                            enabled = updated.serverSettings.apiKeyEnabled,
                                            key = updated.serverSettings.apiKey
                                        )
                                        companionServer.updateFileUploadEnabled(updated.serverSettings.fileUploadEnabled)
                                        companionServer.updateAtemConfig(
                                            updated.atemSettings,
                                            updated.streamingSettings.lowerThirdFolder
                                        )
                                    },
                                    onThemeChange = { newTheme ->
                                        appSettings = appSettings.copy(theme = newTheme.toString())
                                        theme = newTheme
                                        settingsManager.saveSettings(appSettings)
                                    },
                                    onIdentifyScreen = {
                                        identifyingScreen = true
                                        coroutineScope.launch {
                                            delay(5_000L)
                                            identifyingScreen = false
                                        }
                                    },
                                    onIdentifyBrowserSource = { index ->
                                        presenterManager.identifyBrowserSourceOutput(index)
                                    },
                                    onOpenLottieGen = { outputDir, onSaved ->
                                        if (outputDir.isNotEmpty() && java.io.File(outputDir).isDirectory) {
                                            lottieGenOutputDir = java.io.File(outputDir)
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
                                    onDismiss = { showKeyboardShortcutsDialog = false; dialogDismissSignal++ }
                                )
                                StatisticsDialog(
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
                                    onConnect = { host, port, apiKey, autoConnect, allowPushToSchedule, bibleSyncMode, mirrorBackgrounds, role ->
                                        appSettings = appSettings.copy(
                                            instanceLink = appSettings.instanceLink.copy(
                                                enabled = true,
                                                primaryHost = host,
                                                primaryPort = port,
                                                apiKey = apiKey,
                                                autoConnect = autoConnect,
                                                allowPushToSchedule = allowPushToSchedule,
                                                bibleSyncMode = bibleSyncMode,
                                                mirrorBackgrounds = mirrorBackgrounds,
                                                role = role
                                            )
                                        )
                                        settingsManager.saveSettings(appSettings)
                                        instanceLinkViewModel.connect(
                                            host, port, apiKey, appSettings.instanceLink.deviceId,
                                            appSettings.instanceLink.reconnectDelayMs.toLong()
                                        )
                                    },
                                    onDisconnect = { instanceLinkViewModel.disconnect() },
                                    onDismiss = { showInstanceLinkDialog = false; dialogDismissSignal++ }
                                )
                                AboutDialog(
                                    isVisible = showAboutDialog,
                                    onDismiss = { showAboutDialog = false; dialogDismissSignal++ },
                                    theme = theme
                                )
                                ContactUsDialog(
                                    isVisible = showContactDialog,
                                    onDismiss = { showContactDialog = false; dialogDismissSignal++ }
                                )
                                if (showConverterWindow) {
                                    ConverterWindow(
                                        theme = theme,
                                        onClose = { showConverterWindow = false }
                                    )
                                }
                                if (showLottieGenWindow) {
                                    val screenBounds = presenterScreenBounds()
                                    LottieGenWindow(
                                        theme = theme,
                                        outputDir = lottieGenOutputDir,
                                        onClose = { showLottieGenWindow = false },
                                        onFileSaved = lottieGenOnFileSaved,
                                        canvasWidth = screenBounds.width,
                                        canvasHeight = screenBounds.height
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

                                // ── Remote API event dialog ───────────────────────
                                val currentRemote = remoteEventQueue.firstOrNull()
                                val currentClientId = currentRemote?.first?.clientId ?: ""
                                RemoteEventDialog(
                                    event = currentRemote?.first,
                                    queueSize = remoteEventQueue.size,
                                    isClientKnownAllowed = remoteClientManager.isAllowed(currentClientId),
                                    isClientKnownBlocked = remoteClientManager.isBlocked(currentClientId),
                                    isInstanceLinkFollower = currentClientId.isNotBlank() &&
                                        currentClientId in companionServer.connectedInstanceLinkFollowers.collectAsState().value,
                                    onAllow = {
                                        currentRemote?.second?.invoke()
                                        if (remoteEventQueue.isNotEmpty()) remoteEventQueue.removeAt(0)
                                    },
                                    onAllowForSession = {
                                        // Mark this client as session-allowed, then silently approve
                                        // the current item AND every other queued item from the same client
                                        if (currentClientId.isNotBlank() && !sessionAllowedClients.contains(
                                                currentClientId
                                            )
                                        ) {
                                            sessionAllowedClients.add(currentClientId)
                                        }
                                        val clientToAllow = currentClientId
                                        val toApprove =
                                            remoteEventQueue.filter { it.first.clientId == clientToAllow || clientToAllow.isBlank() }
                                        toApprove.forEach { it.second.invoke() }
                                        remoteEventQueue.removeAll(toApprove)
                                    },
                                    onAllowPermanently = {
                                        // Permanently allow and silently approve all queued items from this client
                                        remoteClientManager.allowPermanently(currentClientId)
                                        val clientToAllow = currentClientId
                                        val toApprove =
                                            remoteEventQueue.filter { it.first.clientId == clientToAllow || clientToAllow.isBlank() }
                                        toApprove.forEach { it.second.invoke() }
                                        remoteEventQueue.removeAll(toApprove)
                                    },
                                    onBlockForSession = {
                                        // Deny all queued items from this client; mark session-blocked
                                        if (currentClientId.isNotBlank() && !sessionBlockedClients.contains(
                                                currentClientId
                                            )
                                        ) {
                                            sessionBlockedClients.add(currentClientId)
                                        }
                                        val clientToBlock = currentClientId
                                        val toRemove =
                                            remoteEventQueue.filter { it.first.clientId == clientToBlock || clientToBlock.isBlank() }
                                        toRemove.forEach { it.third.invoke() }
                                        remoteEventQueue.removeAll(toRemove)
                                    },
                                    onBlockPermanently = {
                                        remoteClientManager.blockPermanently(currentClientId)
                                        // Deny all queued items from this client
                                        val clientToBlock = currentClientId
                                        val toRemove =
                                            remoteEventQueue.filter { it.first.clientId == clientToBlock || clientToBlock.isBlank() }
                                        toRemove.forEach { it.third.invoke() }
                                        remoteEventQueue.removeAll(toRemove)
                                    },
                                    onDeny = {
                                        currentRemote?.third?.invoke()
                                        if (remoteEventQueue.isNotEmpty()) remoteEventQueue.removeAt(0)
                                    }
                                )

                                // ── Activity toast for auto-approved clients ──────────────
                                InstanceLinkToastHost(
                                    failures = instanceLinkCommandFailures,
                                    onDismiss = { failure -> instanceLinkCommandFailures.remove(failure) }
                                )
                                RemoteActivityToastHost(
                                    notifications = remoteActivityNotifications,
                                    connectedInstanceLinkFollowers = companionServer.connectedInstanceLinkFollowers.collectAsState().value,
                                    onDismiss = { n -> remoteActivityNotifications.remove(n) },
                                    onDismissAll = { remoteActivityNotifications.clear() },
                                    onBlockForSession = { n ->
                                        val cid = n.clientId
                                        if (cid.isNotBlank() && !sessionBlockedClients.contains(cid)) {
                                            sessionBlockedClients.add(cid)
                                            // Also remove from session-allowed if present
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

            // Auto-clear presenting mode when media finishes playing
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

        if (appReady && !eulaAccepted) {
            LicenseDialog(
                onAccept = {
                    val updated = appSettings.copy(eulaAcceptedVersion = CURRENT_EULA_VERSION)
                    settingsManager.saveSettings(updated)
                    appSettings = updated
                    eulaAccepted = true
                },
                onDecline = { exitApplication() }
            )
        }
    }
}

/** Where fetched picture bytes are cached so PresenterManager.setSelectedImagePath (which needs a
 *  local path, not bytes) can display them like any other local file. */
private val instanceLinkPictureCacheDir: File by lazy {
    File(System.getProperty("user.home"), ".churchpresenter/instance-link/cache/pictures").apply { mkdirs() }
}

/** Where fetched background image/video bytes are cached, keyed by slot — BackgroundConfig's
 *  image/video fields need a local path, not bytes, same reasoning as [instanceLinkPictureCacheDir]. */
private val instanceLinkBackgroundCacheDir: File by lazy {
    File(System.getProperty("user.home"), ".churchpresenter/instance-link/cache/backgrounds").apply { mkdirs() }
}

/**
 * Downloads the primary's configured background image/video assets (only for slots it actually has
 * set — most churches only use one or two) into a local cache, then returns a [BackgroundSettings]
 * copy with every image/video path rewritten to the cached file. BiblePresenter/SongPresenter then
 * render it exactly like a local background — no changes needed in either presenter. Only called
 * when the follower opted in via InstanceLinkSettings.mirrorBackgrounds; colors/gradients/opacity/
 * type are plain values already carried by [remote] as-is, no transfer needed for those.
 */
private suspend fun downloadMirroredBackgroundSettings(
    remote: BackgroundSettings,
    instanceLinkViewModel: InstanceLinkViewModel
): BackgroundSettings {
    suspend fun cache(slot: String, path: String, isVideo: Boolean): String {
        if (path.isBlank()) return path
        val ext = File(path).extension.ifBlank { if (isVideo) "mp4" else "jpg" }
        val kind = if (isVideo) "video" else "image"
        val cacheFile = File(instanceLinkBackgroundCacheDir, "$slot-$kind.$ext")
        if (!cacheFile.exists()) {
            val bytes = instanceLinkViewModel.fetchBackgroundAsset(slot, isVideo)
            if (bytes == null) {
                InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER, "background_asset_fetch_failed",
                    mapOf("slot" to slot, "isVideo" to isVideo)
                )
                return ""
            }
            // Temp-file + rename — same cancellation-safety reasoning as the picture cache:
            // the surrounding effect can be restarted mid-download.
            val tmp = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(cacheFile)) tmp.delete()
            if (!cacheFile.exists()) return ""
        }
        return cacheFile.absolutePath
    }
    return remote.copy(
        defaultBackgroundImage = cache(Constants.BACKGROUND_SLOT_DEFAULT, remote.defaultBackgroundImage, false),
        defaultBackgroundVideo = cache(Constants.BACKGROUND_SLOT_DEFAULT, remote.defaultBackgroundVideo, true),
        defaultLowerThirdBackgroundImage = cache(Constants.BACKGROUND_SLOT_DEFAULT_LOWER_THIRD, remote.defaultLowerThirdBackgroundImage, false),
        defaultLowerThirdBackgroundVideo = cache(Constants.BACKGROUND_SLOT_DEFAULT_LOWER_THIRD, remote.defaultLowerThirdBackgroundVideo, true),
        bibleBackground = remote.bibleBackground.copy(
            backgroundImage = cache(Constants.BACKGROUND_SLOT_BIBLE, remote.bibleBackground.backgroundImage, false),
            backgroundVideo = cache(Constants.BACKGROUND_SLOT_BIBLE, remote.bibleBackground.backgroundVideo, true)
        ),
        bibleLowerThirdBackground = remote.bibleLowerThirdBackground.copy(
            backgroundImage = cache(Constants.BACKGROUND_SLOT_BIBLE_LOWER_THIRD, remote.bibleLowerThirdBackground.backgroundImage, false),
            backgroundVideo = cache(Constants.BACKGROUND_SLOT_BIBLE_LOWER_THIRD, remote.bibleLowerThirdBackground.backgroundVideo, true)
        ),
        songBackground = remote.songBackground.copy(
            backgroundImage = cache(Constants.BACKGROUND_SLOT_SONG, remote.songBackground.backgroundImage, false),
            backgroundVideo = cache(Constants.BACKGROUND_SLOT_SONG, remote.songBackground.backgroundVideo, true)
        ),
        songLowerThirdBackground = remote.songLowerThirdBackground.copy(
            backgroundImage = cache(Constants.BACKGROUND_SLOT_SONG_LOWER_THIRD, remote.songLowerThirdBackground.backgroundImage, false),
            backgroundVideo = cache(Constants.BACKGROUND_SLOT_SONG_LOWER_THIRD, remote.songLowerThirdBackground.backgroundVideo, true)
        )
    )
}

/**
 * Applies a [LiveStateDto] received from another instance's CompanionServer to this instance's own
 * [PresenterManager], so an InstanceLink follower mirrors the primary's output. Bible verses, song
 * sections, announcements, website content, pictures, lower thirds (fetched by preset name),
 * media (streamed from the primary, no position sync — the DTO carries no transport state),
 * canvas scenes (matched by id against this instance's own local scenes), Q&A questions, and
 * Strong's dictionary entries (carried whole in the DTO) are all mirrored; presentations use
 * their own richer dedicated broadcast instead (see the remotePresentationSlide collector in
 * the caller). STT stays mode-only — there is no caption feed to mirror.
 */
private suspend fun applyRemoteLiveState(
    state: LiveStateDto,
    presenterManager: PresenterManager,
    instanceLinkViewModel: InstanceLinkViewModel,
    bibleSyncMode: BibleSyncMode = BibleSyncMode.FULL_REPLICA,
    localPrimaryBible: Bible? = null,
    /** This instance's own saved scenes — CANVAS mirroring is id-match only (no content endpoint). */
    localScenes: List<Scene> = emptyList(),
    /** Loads + starts media playback locally (MediaViewModel stays owned by its composable). */
    onPlayRemoteMedia: ((url: String, type: String) -> Unit)? = null
) {
    val mode = runCatching { Presenting.valueOf(state.contentType) }.getOrNull()
    if (mode == null) {
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "apply_live_state",
            mapOf("contentType" to state.contentType, "resolved" to false, "reason" to "unknown_content_type")
        )
        return
    }
    when (mode) {
        Presenting.BIBLE -> {
            val codeBook = state.verseCodeBook
            val codeChapter = state.verseCodeChapter
            val codeVerse = state.verseCodeVerse
            if (bibleSyncMode == BibleSyncMode.REFERENCE_ONLY && codeBook != null && codeChapter != null && codeVerse != null) {
                // Reference-only: never touch a downloaded file — resolve the SAME canonical verse in
                // this instance's own independently-configured (possibly different-language) Bible via
                // Bible.getVerseDetailsByCode, so the follower shows its own translation's wording, not
                // the primary's. If this Bible has no verse at that code (versification mismatch, or no
                // local bible configured), there's nothing sensible to show — quietly no-op.
                val result = localPrimaryBible?.getVerseDetailsByCode(codeBook, codeChapter, codeVerse)
                if (result != null) {
                    presenterManager.setSelectedVerses(
                        listOf(
                            SelectedVerse(
                                bibleAbbreviation = localPrimaryBible.getBibleAbbreviation(),
                                bibleName = localPrimaryBible.getBibleTitle(),
                                bookName = result.bookName,
                                chapter = result.displayChapter,
                                verseNumber = result.displayVerse,
                                verseText = result.verseText,
                                verseRange = state.verseRange ?: ""
                            )
                        )
                    )
                    InstanceLinkLogger.log(
                        InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                        mapOf("contentType" to "BIBLE", "resolved" to true, "mode" to "reference_only")
                    )
                } else {
                    InstanceLinkLogger.log(
                        InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                        mapOf(
                            "contentType" to "BIBLE", "resolved" to false, "mode" to "reference_only",
                            "reason" to if (localPrimaryBible == null) "no_local_bible_loaded" else "verse_code_not_found"
                        )
                    )
                }
            } else if (state.bookName != null) {
                // Full replica: the primary's own wording, verbatim.
                // setSelectedVerses (plural), not setSelectedVerse — only the plural setter feeds the
                // selectedVerses -> displayedVerses bridging LaunchedEffect that BiblePresenter actually
                // renders from; the singular setter alone leaves the screen blank despite the mode
                // correctly switching to BIBLE.
                presenterManager.setSelectedVerses(
                    listOf(
                        SelectedVerse(
                            bookName = state.bookName,
                            chapter = state.chapter ?: 0,
                            verseNumber = state.verseNumber ?: 0,
                            verseText = state.verseText ?: "",
                            verseRange = state.verseRange ?: ""
                        )
                    )
                )
                InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                    mapOf("contentType" to "BIBLE", "resolved" to true, "mode" to "full_replica")
                )
            } else {
                InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                    mapOf("contentType" to "BIBLE", "resolved" to false, "reason" to "no_book_name_in_state")
                )
            }
        }
        Presenting.LYRICS -> if (state.songTitle != null) {
            presenterManager.setLyricSection(
                LyricSection(
                    title = state.songTitle,
                    songNumber = state.songNumber ?: 0,
                    type = state.sectionType ?: "",
                    lines = state.lines ?: emptyList()
                )
            )
            presenterManager.setSongDisplaySectionIndex(state.songSectionIndex ?: -1)
            presenterManager.setSongDisplayLineIndex(state.songLineIndex ?: -1)
            InstanceLinkLogger.log(InstanceLinkLogSide.FOLLOWER, "apply_live_state", mapOf("contentType" to "LYRICS", "resolved" to true))
        } else {
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                mapOf("contentType" to "LYRICS", "resolved" to false, "reason" to "no_song_title_in_state")
            )
        }
        Presenting.ANNOUNCEMENTS -> {
            val text = state.announcementText
            if (text != null) {
                presenterManager.setAnnouncementText(text)
                InstanceLinkLogger.log(InstanceLinkLogSide.FOLLOWER, "apply_live_state", mapOf("contentType" to "ANNOUNCEMENTS", "resolved" to true))
            } else {
                InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                    mapOf("contentType" to "ANNOUNCEMENTS", "resolved" to false, "reason" to "no_text_in_state")
                )
            }
        }
        Presenting.WEBSITE -> {
            state.websiteUrl?.let { presenterManager.setWebsiteUrl(it) }
            state.websiteTitle?.let { presenterManager.setWebPageTitle(it) }
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                mapOf("contentType" to "WEBSITE", "resolved" to (state.websiteUrl != null))
            )
        }
        Presenting.PICTURES -> {
            val folderId = state.pictureFolderId
            val index = state.pictureIndex
            if (folderId != null && index != null) {
                val cacheFile = File(instanceLinkPictureCacheDir, "${folderId}_$index.jpg")
                if (!cacheFile.exists()) {
                    val bytes = instanceLinkViewModel.fetchPictureImageBytes(folderId, index)
                    if (bytes != null) {
                        // Temp-file + rename: this apply can be cancelled mid-write by a newer
                        // live state (collectLatest) — a truncated file must never land under the
                        // final name, or the exists() cache gate would trust it forever.
                        val tmp = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
                        tmp.writeBytes(bytes)
                        if (!tmp.renameTo(cacheFile)) tmp.delete()
                        if (!cacheFile.exists()) {
                            InstanceLinkLogger.log(
                                InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                                mapOf("contentType" to "PICTURES", "resolved" to false, "reason" to "cache_write_failed")
                            )
                            return
                        }
                    } else {
                        InstanceLinkLogger.log(
                            InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                            mapOf("contentType" to "PICTURES", "resolved" to false, "reason" to "fetch_failed")
                        )
                        return
                    }
                }
                presenterManager.setSelectedImagePath(cacheFile.absolutePath)
                InstanceLinkLogger.log(InstanceLinkLogSide.FOLLOWER, "apply_live_state", mapOf("contentType" to "PICTURES", "resolved" to true))
            } else {
                InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                    mapOf("contentType" to "PICTURES", "resolved" to false, "reason" to "missing_folder_id_or_index")
                )
            }
        }
        Presenting.LOWER_THIRD -> {
            val name = state.lowerThirdName
            if (name != null) {
                val bytes = instanceLinkViewModel.fetchLowerThirdJson(name)
                if (bytes != null) {
                    presenterManager.setLottieContent(
                        String(bytes, Charsets.UTF_8), pauseAtFrame = false, pauseFrame = -1f,
                        pauseDurationMs = 2000L, presetName = name
                    )
                    InstanceLinkLogger.log(InstanceLinkLogSide.FOLLOWER, "apply_live_state", mapOf("contentType" to "LOWER_THIRD", "resolved" to true))
                } else {
                    InstanceLinkLogger.log(
                        InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                        mapOf("contentType" to "LOWER_THIRD", "resolved" to false, "reason" to "fetch_failed")
                    )
                }
            } else {
                InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                    mapOf("contentType" to "LOWER_THIRD", "resolved" to false, "reason" to "no_name_in_state")
                )
            }
        }
        Presenting.MEDIA -> {
            // No position/transport sync in this pass: LiveStateDto carries which media is live,
            // not where playback is — a follower starts the same media from the top.
            val mediaType = state.mediaType
            val streamUrl = state.mediaId?.let { instanceLinkViewModel.mediaStreamUrl(it) }
            when {
                mediaType == Constants.MEDIA_TYPE_URL && state.mediaUrl != null && onPlayRemoteMedia != null -> {
                    onPlayRemoteMedia(state.mediaUrl, mediaType)
                    presenterManager.setCurrentMedia(state.mediaUrl, mediaType)
                    InstanceLinkLogger.log(
                        InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                        mapOf("contentType" to "MEDIA", "resolved" to true, "source" to "url", "positionSync" to false)
                    )
                }
                streamUrl != null && onPlayRemoteMedia != null -> {
                    val type = mediaType ?: Constants.MEDIA_TYPE_LOCAL
                    onPlayRemoteMedia(streamUrl, type)
                    presenterManager.setCurrentMedia(streamUrl, type)
                    InstanceLinkLogger.log(
                        InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                        mapOf("contentType" to "MEDIA", "resolved" to true, "source" to "stream", "positionSync" to false)
                    )
                }
                else -> InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                    // Media launched outside the primary's schedule has no stream mapping
                    // (LiveStateDto.mediaId comes from its schedule-item → path map).
                    mapOf("contentType" to "MEDIA", "resolved" to false, "reason" to "no_media_id")
                )
            }
        }
        Presenting.CANVAS -> {
            val scene = state.sceneId?.let { id -> localScenes.find { it.id == id } }
            if (scene != null) {
                presenterManager.setActiveScene(scene)
                InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                    mapOf("contentType" to "CANVAS", "resolved" to true)
                )
            } else {
                // Id-match only: scene content isn't fetchable over the link — mirroring works
                // when the same scenes.json exists on both instances. Mode still switches.
                InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                    mapOf(
                        "contentType" to "CANVAS", "resolved" to false,
                        "reason" to "scene_not_found_locally", "sceneName" to state.sceneName
                    )
                )
            }
        }
        Presenting.QA -> {
            val questionId = state.questionId
            val questionText = state.questionText
            if (questionId != null && questionText != null) {
                presenterManager.setDisplayedQuestion(
                    Question(
                        id = questionId,
                        text = questionText,
                        timestamp = System.currentTimeMillis(),
                        status = QuestionStatus.APPROVED
                    )
                )
                InstanceLinkLogger.log(InstanceLinkLogSide.FOLLOWER, "apply_live_state", mapOf("contentType" to "QA", "resolved" to true))
            } else {
                InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                    mapOf("contentType" to "QA", "resolved" to false, "reason" to "no_question_in_state")
                )
            }
        }
        Presenting.DICTIONARY -> {
            val entry = state.dictionaryEntry
            val word = state.dictionaryWord
            when {
                entry != null -> {
                    presenterManager.setDisplayedDictionaryEntry(entry)
                    InstanceLinkLogger.log(InstanceLinkLogSide.FOLLOWER, "apply_live_state", mapOf("contentType" to "DICTIONARY", "resolved" to true))
                }
                word != null -> {
                    // Old primary that doesn't carry the full entry — show what we have.
                    presenterManager.setDisplayedDictionaryEntry(
                        StrongsEntry(number = "", word = word, transliteration = "", pronunciation = "", definition = "")
                    )
                    InstanceLinkLogger.log(
                        InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                        mapOf("contentType" to "DICTIONARY", "resolved" to true, "partial" to true)
                    )
                }
                else -> InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                    mapOf("contentType" to "DICTIONARY", "resolved" to false, "reason" to "no_word_in_state")
                )
            }
        }
        else -> {
            // presentation: mirrored via its own dedicated broadcast (remotePresentationSlide
            // collector); stt: no caption feed exists to mirror. Mode still switches below.
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "apply_live_state",
                mapOf("contentType" to mode.name, "resolved" to false, "reason" to "mode_only_no_feed")
            )
        }
    }
    presenterManager.setPresentingMode(mode)
    presenterManager.setShowPresenterWindow(true)
}

private fun qaActionType(action: String): RemoteEventType = when (action) {
    "edit"    -> RemoteEventType.QA_EDIT
    "delete"  -> RemoteEventType.QA_DELETE
    "approve" -> RemoteEventType.QA_APPROVE
    "deny"    -> RemoteEventType.QA_DENY
    "done"    -> RemoteEventType.QA_DONE
    "display"       -> RemoteEventType.QA_DISPLAY
    "clear-display" -> RemoteEventType.QA_CLEAR_DISPLAY
    else            -> RemoteEventType.QA_ADD
}

/** Returns a (title, detail) pair describing a ScheduleItem for the remote event banner. */
private fun remoteEventLabel(item: ScheduleItem): Pair<String, String> = when (item) {
    is ScheduleItem.SongItem -> "${item.songNumber} - ${item.title}" to item.songbook
    is ScheduleItem.BibleVerseItem -> {
        val ref = if (item.verseRange.isNotEmpty()) "${item.bookName} ${item.chapter}:${item.verseRange}"
        else "${item.bookName} ${item.chapter}:${item.verseNumber}"
        ref to item.verseText.take(60)
    }

    is ScheduleItem.PictureItem -> item.folderName to "${item.imageCount} images"
    is ScheduleItem.PresentationItem -> item.fileName to item.fileType.uppercase()
    is ScheduleItem.MediaItem -> item.mediaTitle to item.mediaType
    is ScheduleItem.LabelItem -> item.text.take(60) to ""
    is ScheduleItem.AnnouncementItem -> item.text.take(60) to ""
    is ScheduleItem.LowerThirdItem -> item.presetLabel to ""
    is ScheduleItem.WebsiteItem -> item.title to item.url
    is ScheduleItem.SceneItem -> item.sceneName to "Scene"
    is ScheduleItem.DictionaryItem -> item.word to item.number
}

/**
 * Executes a project request — adds to schedule and sets presenter state.
 * Fixes the original bug where SongItem projection never selected the song in the Songs tab.
 */
private fun executeProjectItem(
    item: ScheduleItem,
    scheduleActions: ScheduleActions,
    presenterManager: PresenterManager,
    statisticsManager: StatisticsManager? = null
) {
    when (item) {
        is ScheduleItem.SongItem -> {
            // Add to schedule AND select the song so the Songs tab navigates to it
            scheduleActions.addSong(item.songNumber, item.title, item.songbook, item.songId)
            presenterManager.setLyricSection(
                LyricSection(
                    title = item.title,
                    songNumber = item.songNumber,
                    lines = emptyList(),
                    type = Constants.SECTION_TYPE_SONG
                )
            )
            statisticsManager?.recordSongDisplay(songId = item.songId, songNumber = item.songNumber, title = item.title, songbook = item.songbook)
            presenterManager.setPresentingMode(Presenting.LYRICS)
            presenterManager.setShowPresenterWindow(true)
        }

        is ScheduleItem.BibleVerseItem -> {
            scheduleActions.addBibleVerse(
                item.bookName,
                item.chapter,
                item.verseNumber,
                item.verseText,
                item.verseRange,
                item.bookId
            )
            presenterManager.setSelectedVerses(
                listOf(
                    SelectedVerse(
                        bookName = item.bookName,
                        chapter = item.chapter,
                        verseNumber = item.verseNumber,
                        verseText = item.verseText,
                        verseRange = item.verseRange
                    )
                )
            )
            presenterManager.setPresentingMode(Presenting.BIBLE)
            presenterManager.setShowPresenterWindow(true)
        }

        is ScheduleItem.PictureItem -> {
            // Deliberately does NOT call setSelectedImagePath(item.folderPath) — that setter expects
            // a single image FILE path, not a folder, and a folder path can never render. The actual
            // image push happens via remoteSelectPictureFlow in main.kt (MainDesktop loads the folder
            // into PicturesViewModel, whose own reactive effect pushes the current image once loaded).
            scheduleActions.addPicture(item.folderPath, item.folderName, item.imageCount)
            presenterManager.setPresentingMode(Presenting.PICTURES)
            presenterManager.setShowPresenterWindow(true)
        }

        is ScheduleItem.PresentationItem -> {
            scheduleActions.addPresentation(item.filePath, item.fileName, item.slideCount, item.fileType)
            presenterManager.setPresentingMode(Presenting.PRESENTATION)
            presenterManager.setShowPresenterWindow(true)
        }

        is ScheduleItem.MediaItem -> {
            scheduleActions.addMedia(item.mediaUrl, item.mediaTitle, item.mediaType)
            presenterManager.setCurrentMedia(item.mediaUrl, item.mediaType)
            presenterManager.setPresentingMode(Presenting.MEDIA)
            presenterManager.setShowPresenterWindow(true)
        }

        else -> Unit
    }
}

@Composable
private fun SplashWindow(theme: ThemeMode) {
    Window(
        onCloseRequest = {},
        title = stringResource(Res.string.app_name),
        icon = painterResource(Res.drawable.ic_app_icon),
        state = rememberWindowState(
            width = 400.dp,
            height = 300.dp,
            position = WindowPosition(Alignment.Center)
        ),
        undecorated = true,
        resizable = false,
        alwaysOnTop = true
    ) {
        AppThemeWrapper(theme = theme) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(Res.drawable.ic_app_icon),
                        contentDescription = null,
                        modifier = Modifier.size(96.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(Res.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PresenterWindows(
    screens: Array<GraphicsDevice>,
    presenterManager: PresenterManager,
    mediaViewModel: MediaViewModel,
    appSettings: AppSettings,
    identifyingScreen: Boolean,
    serverUrl: String = "",
    qaDisplayUrl: String = "",
    sttManager: STTManager,
) {
    val showPresenterWindow by presenterManager.showPresenterWindow
    val presentingMode by presenterManager.presentingMode
    val screenLocks by presenterManager.screenLocks
    val selectedVerses by presenterManager.selectedVerses
    val displayedVerses by presenterManager.displayedVerses
    val nextVerses by presenterManager.nextVerses
    val bibleTransitionAlpha by presenterManager.bibleTransitionAlpha
    val lyricSection by presenterManager.lyricSection
    val lyricSectionVersion by presenterManager.lyricSectionVersion
    val displayedLyricSection by presenterManager.displayedLyricSection
    val songTransitionAlpha by presenterManager.songTransitionAlpha
    val songDisplayLineIndex by presenterManager.songDisplayLineIndex
    val allLyricSections by presenterManager.allLyricSections
    val songDisplaySectionIndex by presenterManager.songDisplaySectionIndex
    val selectedImagePath by presenterManager.selectedImagePath
    val displayedImagePath by presenterManager.displayedImagePath
    val pictureTransitionAlpha by presenterManager.pictureTransitionAlpha
    val previousDisplayedImagePath by presenterManager.previousDisplayedImagePath
    val pictureSlideOffset by presenterManager.pictureSlideOffset
    val selectedSlide by presenterManager.selectedSlide
    val displayedSlide by presenterManager.displayedSlide
    val slideFrozen by presenterManager.slideFrozen
    val presentationFrame by presenterManager.presentationFrame
    val slideTransitionAlpha by presenterManager.slideTransitionAlpha
    val previousDisplayedSlide by presenterManager.previousDisplayedSlide
    val slideSlideOffset by presenterManager.slideSlideOffset
    val animationType by presenterManager.animationType
    val transitionDuration by presenterManager.transitionDuration
    val announcementText by presenterManager.announcementText
    val displayedAnnouncementText by presenterManager.displayedAnnouncementText
    val announcementTransitionAlpha by presenterManager.announcementTransitionAlpha
    val clearAnnouncementOnFinish = {
        presenterManager.setAnnouncementText("")
        presenterManager.setDisplayedAnnouncementText("")
        presenterManager.requestClearDisplay()
    }
    val lottieJsonContent by presenterManager.lottieJsonContent
    val lottiePauseAtFrame by presenterManager.lottiePauseAtFrame
    val lottiePauseFrame by presenterManager.lottiePauseFrame
    val lottiePauseDurationMs by presenterManager.lottiePauseDurationMs
    val lottieTrigger by presenterManager.lottieTrigger
    val lottieProgress by presenterManager.lottieProgress
    val lottieFrame by presenterManager.lottieFrame
    val mediaTransitionAlpha by presenterManager.mediaTransitionAlpha
    val websiteUrl by presenterManager.websiteUrl
    val activeScene by presenterManager.activeScene
    val displayedQuestion by presenterManager.displayedQuestion
    val qaTransitionAlpha by presenterManager.qaTransitionAlpha
    val showQRCodeOnDisplay by presenterManager.showQRCodeOnDisplay
    val displayedDictionaryEntry by presenterManager.displayedDictionaryEntry
    val presenterNotes by presenterManager.presenterNotes

    val proj = appSettings.projectionSettings

    // Mode-level crossfade duration (shared, per-screen active flag computed inside each window)
    val modeCrossfadeDuration = maxOf(
        if (appSettings.bibleSettings.crossfade) appSettings.bibleSettings.transitionDuration.toInt() else 0,
        if (appSettings.songSettings.crossfade) appSettings.songSettings.transitionDuration.toInt() else 0
    ).coerceAtLeast(100)

    // Fade-out before clearing display
    val clearRequested by presenterManager.clearDisplayRequested
    LaunchedEffect(clearRequested) {
        if (!clearRequested) return@LaunchedEffect
        val mode = presenterManager.presentingMode.value
        // Don't fade the content alpha if any screen is locked to this mode —
        // that screen is still showing the content and the alpha must stay at 1.
        val modeIsLocked = presenterManager.screenLocks.value.values.any { it == mode }
        val shouldFade = !modeIsLocked && when (mode) {
            Presenting.BIBLE -> appSettings.bibleSettings.fadeOut
            Presenting.LYRICS -> appSettings.songSettings.fadeOut
            else -> false
        }
        if (shouldFade) {
            val duration = when (mode) {
                Presenting.BIBLE -> appSettings.bibleSettings.transitionDuration.toInt()
                Presenting.LYRICS -> appSettings.songSettings.transitionDuration.toInt()
                else -> 500
            }.coerceAtLeast(100)
            val anim = Animatable(1f)
            anim.animateTo(0f, tween(durationMillis = duration)) {
                when (mode) {
                    Presenting.BIBLE -> presenterManager.setBibleTransitionAlpha(this.value)
                    Presenting.LYRICS -> presenterManager.setSongTransitionAlpha(this.value)
                    else -> {}
                }
            }
        }
        // Set mode to NONE — alphas stay at 0 until next go-live triggers fade-in
        presenterManager.setPresentingMode(Presenting.NONE)
    }

    // Centralized Bible transition: one animation drives all windows so they stay in sync
    // When hold is active, skip updating displayedVerses so the user can browse freely
    val bibleHold by presenterManager.bibleHold
    LaunchedEffect(selectedVerses, bibleHold) {
        if (bibleHold) return@LaunchedEffect
        val bs = appSettings.bibleSettings
        // All transitions (crossfade, fade in/out) are handled inside BiblePresenter
        presenterManager.setDisplayedVerses(selectedVerses)
        presenterManager.setBibleTransitionAlpha(1f)
    }

    // Centralized Song transition
    LaunchedEffect(lyricSection, lyricSectionVersion) {
        val ss = appSettings.songSettings
        // Skip animation when section content hasn't changed (e.g. line navigation within same verse)
        if (lyricSection == presenterManager.displayedLyricSection.value) {
            presenterManager.setSongTransitionAlpha(1f)
            return@LaunchedEffect
        }
        // Skip fade in line mode — only one line visible, instant swap is cleaner
        val isLineMode = ss.fullscreenDisplayMode == Constants.SONG_DISPLAY_MODE_LINE ||
                ss.lowerThirdDisplayMode == Constants.SONG_DISPLAY_MODE_LINE ||
                ss.lookAheadDisplayMode == Constants.SONG_DISPLAY_MODE_LINE ||
                ss.lowerThirdLookAheadDisplayMode == Constants.SONG_DISPLAY_MODE_LINE
        if (isLineMode) {
            presenterManager.setDisplayedLyricSection(lyricSection)
            presenterManager.setSongTransitionAlpha(1f)
            return@LaunchedEffect
        }
        // All transitions (crossfade, fade in/out) are handled inside SongPresenter
        presenterManager.setDisplayedLyricSection(lyricSection)
        presenterManager.setSongTransitionAlpha(1f)
    }

    // Centralized Picture transition
    LaunchedEffect(selectedImagePath) {
        val current = presenterManager.displayedImagePath.value
        when {
            current == null || animationType == AnimationType.NONE -> {
                presenterManager.setDisplayedImagePath(selectedImagePath)
                presenterManager.setPictureTransitionAlpha(1f)
                presenterManager.setPreviousDisplayedImagePath(null)
            }
            animationType == AnimationType.FADE -> {
                val halfDuration = transitionDuration / 2
                val anim = Animatable(1f)
                anim.animateTo(0f, tween(halfDuration)) {
                    presenterManager.setPictureTransitionAlpha(value)
                }
                presenterManager.setDisplayedImagePath(selectedImagePath)
                anim.animateTo(1f, tween(halfDuration)) {
                    presenterManager.setPictureTransitionAlpha(value)
                }
            }
            animationType == AnimationType.CROSSFADE -> {
                presenterManager.setPreviousDisplayedImagePath(current)
                presenterManager.setDisplayedImagePath(selectedImagePath)
                presenterManager.setPictureTransitionAlpha(0f)
                val anim = Animatable(0f)
                anim.animateTo(1f, tween(transitionDuration)) {
                    presenterManager.setPictureTransitionAlpha(value)
                }
                presenterManager.setPreviousDisplayedImagePath(null)
            }
            animationType == AnimationType.SLIDE_LEFT || animationType == AnimationType.SLIDE_RIGHT -> {
                presenterManager.setPreviousDisplayedImagePath(current)
                presenterManager.setDisplayedImagePath(selectedImagePath)
                presenterManager.setPictureTransitionAlpha(1f)
                presenterManager.setPictureSlideOffset(0f)
                val anim = Animatable(0f)
                anim.animateTo(1f, tween(transitionDuration)) {
                    presenterManager.setPictureSlideOffset(value)
                }
                presenterManager.setPreviousDisplayedImagePath(null)
                presenterManager.setPictureSlideOffset(1f)
            }
        }
    }

    // Animated-presentation frame clock: one evaluation per display frame while a build step
    // animates, published to every output window via presenterManager.presentationFrame.
    LaunchedEffect(Unit) {
        presenterManager.runPresentationClock()
    }

    // Centralized Slide transition
    LaunchedEffect(selectedSlide) {
        val current = presenterManager.displayedSlide.value
        when {
            current == null || animationType == AnimationType.NONE -> {
                presenterManager.setDisplayedSlide(selectedSlide)
                presenterManager.setSlideTransitionAlpha(1f)
                presenterManager.setPreviousDisplayedSlide(null)
            }
            animationType == AnimationType.FADE -> {
                val halfDuration = transitionDuration / 2
                val anim = Animatable(1f)
                anim.animateTo(0f, tween(halfDuration)) {
                    presenterManager.setSlideTransitionAlpha(value)
                }
                presenterManager.setDisplayedSlide(selectedSlide)
                anim.animateTo(1f, tween(halfDuration)) {
                    presenterManager.setSlideTransitionAlpha(value)
                }
            }
            animationType == AnimationType.CROSSFADE -> {
                presenterManager.setPreviousDisplayedSlide(current)
                presenterManager.setDisplayedSlide(selectedSlide)
                presenterManager.setSlideTransitionAlpha(0f)
                val anim = Animatable(0f)
                anim.animateTo(1f, tween(transitionDuration)) {
                    presenterManager.setSlideTransitionAlpha(value)
                }
                presenterManager.setPreviousDisplayedSlide(null)
            }
            animationType == AnimationType.SLIDE_LEFT || animationType == AnimationType.SLIDE_RIGHT -> {
                presenterManager.setPreviousDisplayedSlide(current)
                presenterManager.setDisplayedSlide(selectedSlide)
                presenterManager.setSlideTransitionAlpha(1f)
                presenterManager.setSlideSlideOffset(0f)
                val anim = Animatable(0f)
                anim.animateTo(1f, tween(transitionDuration)) {
                    presenterManager.setSlideSlideOffset(value)
                }
                presenterManager.setPreviousDisplayedSlide(null)
                presenterManager.setSlideSlideOffset(1f)
            }
        }
    }

    // Centralized Announcements transition
    LaunchedEffect(announcementText) {
        val annSettings = appSettings.announcementsSettings
        val isFade = annSettings.animationType == Constants.ANIMATION_FADE
        val isNone = annSettings.animationType == Constants.ANIMATION_NONE
        val wasEmpty = presenterManager.displayedAnnouncementText.value.isEmpty()
        val fadeDuration = 500
        val sliderSum = 30500L // 500 + 30000, matches AnnouncementsTab speed slider
        val displayDuration = (sliderSum - annSettings.animationDuration).coerceAtLeast(500)
        val loopCount = annSettings.loopCount

        if (!isFade && !isNone) {
            // Directional slides — just swap text, animation handled in AnnouncementsPresenter
            presenterManager.setDisplayedAnnouncementText(announcementText)
            presenterManager.setAnnouncementTransitionAlpha(1f)
        } else if (announcementText.isEmpty()) {
            // Cleared by user or loop finished — fade out if fade, instant if none
            if (isFade && !wasEmpty) {
                val anim = Animatable(1f)
                anim.animateTo(0f, tween(fadeDuration)) {
                    presenterManager.setAnnouncementTransitionAlpha(value)
                }
            }
            presenterManager.setDisplayedAnnouncementText("")
            presenterManager.setAnnouncementTransitionAlpha(1f)
        } else {
            // Show text with timed display
            presenterManager.setDisplayedAnnouncementText(announcementText)

            // Fade in (only for fade animation)
            if (isFade) {
                presenterManager.setAnnouncementTransitionAlpha(0f)
                val anim = Animatable(0f)
                anim.animateTo(1f, tween(fadeDuration)) {
                    presenterManager.setAnnouncementTransitionAlpha(value)
                }
            } else {
                presenterManager.setAnnouncementTransitionAlpha(1f)
            }

            if (loopCount > 0) {
                // Finite loops: display for duration × loopCount, then clear
                delay(displayDuration * loopCount)

                // Fade out (only for fade animation)
                if (isFade) {
                    val anim = Animatable(1f)
                    anim.animateTo(0f, tween(fadeDuration)) {
                        presenterManager.setAnnouncementTransitionAlpha(value)
                    }
                }
                // Clear display and exit presenting mode
                presenterManager.setAnnouncementText("")
                presenterManager.setDisplayedAnnouncementText("")
                presenterManager.requestClearDisplay()
            }
            // loopCount == 0: infinite — stay visible until user manually stops
        }
    }

    // Centralized Lottie (lower third) animation — one animation drives all windows
    val lottieComposition by rememberLottieComposition(key = lottieJsonContent) {
        LottieCompositionSpec.JsonString(lottieJsonContent)
    }
    LaunchedEffect(lottieComposition, lottiePauseAtFrame, lottiePauseFrame, lottiePauseDurationMs, lottieTrigger) {
        // The live Compottie GPU-vector renderer (used below whenever pre-rendered frames aren't
        // ready yet) can silently render nothing partway through a clip on some GPU/driver
        // combinations. Pre-rendered raw frames don't share that failure mode (each is a static
        // bitmap, independently rendered ahead of time). So this loop polls
        // presenterManager.lottieFrameCount LIVE on every tick (not just once at effect-start) and
        // switches over to raw-frame playback the instant frames become available — continuing
        // from the same elapsed-time position, so there's no visible jump — instead of committing
        // to whichever path was ready first for the whole clip.
        try {
            val comp = lottieComposition
            val initialFrameCount = presenterManager.lottieFrameCount.value
            val totalDurMs = when {
                comp != null -> ((comp.durationFrames / comp.frameRate) * 1000f).toLong().coerceAtLeast(1L)
                initialFrameCount != null -> (initialFrameCount * 1000L / presenterManager.lottiePrerenderFps.value).coerceAtLeast(1L)
                else -> return@LaunchedEffect
            }
            val hasPause = lottiePauseAtFrame && lottiePauseFrame in 0f..1f
            val pauseAtMs = if (hasPause) (totalDurMs * lottiePauseFrame).toLong() else -1L
            val grandTotalMs = totalDurMs + (if (hasPause) lottiePauseDurationMs else 0L)

            fun progressAt(elapsedMs: Long): Float {
                if (!hasPause) return (elapsedMs.toFloat() / totalDurMs).coerceIn(0f, 1f)
                return when {
                    elapsedMs < pauseAtMs -> (elapsedMs.toFloat() / totalDurMs).coerceIn(0f, lottiePauseFrame)
                    elapsedMs < pauseAtMs + lottiePauseDurationMs -> lottiePauseFrame
                    else -> {
                        val postElapsed = elapsedMs - pauseAtMs - lottiePauseDurationMs
                        val postTotalMs = (totalDurMs - pauseAtMs).coerceAtLeast(1L)
                        (lottiePauseFrame + (postElapsed.toFloat() / postTotalMs) * (1f - lottiePauseFrame)).coerceIn(0f, 1f)
                    }
                }
            }

            // Vsync-driven clock: elapsed time comes from real frame timestamps, so a missed
            // display frame self-corrects on the next one instead of accumulating drift the way
            // a fixed-delay tick would.
            val startNanos = withFrameNanos { it }
            var elapsedMs = 0L
            while (true) {
                val frameCount = presenterManager.lottieFrameCount.value
                val progress = progressAt(elapsedMs)
                if (frameCount != null) {
                    val idx = (progress * (frameCount - 1)).roundToInt().coerceIn(0, frameCount - 1)
                    presenterManager.setLottieCurrentFrameIndex(idx)
                } else {
                    presenterManager.setLottieProgress(progress)
                }
                if (elapsedMs >= grandTotalMs) break
                val nowNanos = withFrameNanos { it }
                elapsedMs = ((nowNanos - startNanos) / 1_000_000).coerceAtMost(grandTotalMs)
            }
            // Snap to the final state (re-check readiness one last time in case it just became ready).
            val finalFrameCount = presenterManager.lottieFrameCount.value
            if (finalFrameCount != null) {
                presenterManager.setLottieCurrentFrameIndex(finalFrameCount - 1)
            } else {
                presenterManager.setLottieProgress(1f)
            }
            presenterManager.requestClearDisplay()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CrashReporter.reportException(e, "Lottie playback LaunchedEffect")
            throw e
        }
    }

    // Shared primary/normal output content — driven by a ScreenAssignment so behavior (crossfade,
    // lower-third layout, per-type visibility, language mode, backgrounds) is identical whether the
    // caller is a real per-screen output window or the developer-only windowed test output below.
    // screenNumber is null when there's no physical screen to label (e.g. the test window).
    val presenterOutputContent: @Composable (screenAssignment: ScreenAssignment, effectiveMode: Presenting, screenNumber: Int?) -> Unit = { screenAssignment, effectiveMode, screenNumber ->
        val primaryRole = screenAssignment.primaryOutputRole
        val showBg = if (screenAssignment.isLowerThird) screenAssignment.showLowerThirdBackground else screenAssignment.showFullscreenBackground
        CompositionLocalProvider(LocalMediaViewModel provides mediaViewModel) {
            if (screenAssignment.displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR) {
                // Stage monitor: dedicated presenter-confidence layout
                StageMonitorScreen(
                    sm = appSettings.stageMonitorSettings,
                    presentingMode = presentingMode,
                    announcementActive = effectiveMode == Presenting.ANNOUNCEMENTS,
                    currentLyricSection = displayedLyricSection,
                    allLyricSections = allLyricSections,
                    songDisplaySectionIndex = songDisplaySectionIndex,
                    displayedVerses = displayedVerses,
                    nextVerses = nextVerses,
                    announcementText = displayedAnnouncementText,
                    displayedImagePath = displayedImagePath,
                    displayedSlide = displayedSlide,
                    presenterNotes = presenterNotes,
                    activeScene = activeScene,
                    displayedQuestion = displayedQuestion,
                    qaSettings = appSettings.qaSettings,
                    displayedDictionaryEntry = displayedDictionaryEntry,
                    dictionarySettings = appSettings.dictionarySettings,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                PresenterScreen(
                    modifier = Modifier.fillMaxSize(),
                    appSettings = appSettings,
                    outputRole = primaryRole,
                    isLowerThird = screenAssignment.isLowerThird,
                    showBackground = showBg
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                                    mediaViewModel.pause()
                                    presenterManager.requestClearDisplay()
                                    true
                                } else false
                            }
                    ) {
                        var prevEffectiveMode by remember { mutableStateOf(effectiveMode) }
                        val screenCrossfadeActive = (appSettings.bibleSettings.crossfade || appSettings.songSettings.crossfade) && effectiveMode != Presenting.NONE && prevEffectiveMode != Presenting.NONE
                        if (effectiveMode != prevEffectiveMode) prevEffectiveMode = effectiveMode
                        Crossfade(targetState = effectiveMode, animationSpec = if (screenCrossfadeActive) tween(modeCrossfadeDuration) else snap()) { mode ->
                            when (mode) {
                                Presenting.BIBLE ->
                                    if (screenAssignment.showBible) {
                                        BiblePresenter(
                                            selectedVerses = displayedVerses,
                                            appSettings = appSettings,
                                            isLowerThird = screenAssignment.isLowerThird,
                                            isLowerThirdVertical = screenAssignment.isLowerThirdVertical,
                                            outputRole = primaryRole,
                                            transitionAlpha = bibleTransitionAlpha,
                                            showBackground = showBg && screenAssignment.showBibleBackground,
                                            crossfadeEnabled = appSettings.bibleSettings.crossfade,
                                            languageMode = screenAssignment.bibleMode
                                        )
                                    }

                                Presenting.LYRICS ->
                                    if (screenAssignment.showSongs) {
                                        SongPresenter(
                                            lyricSection = displayedLyricSection,
                                            appSettings = appSettings,
                                            isLowerThird = screenAssignment.isLowerThird,
                                            isLowerThirdVertical = screenAssignment.isLowerThirdVertical,
                                            outputRole = primaryRole,
                                            transitionAlpha = songTransitionAlpha,
                                            displayLineIndex = songDisplayLineIndex,
                                            lookAheadEnabled = screenAssignment.songLookAhead,
                                            allLyricSections = allLyricSections,
                                            displaySectionIndex = songDisplaySectionIndex,
                                            showBackground = showBg && screenAssignment.showSongsBackground,
                                            crossfadeEnabled = appSettings.songSettings.crossfade,
                                            languageOverride = screenAssignment.songMode
                                        )
                                    }

                                Presenting.PICTURES ->
                                    if (screenAssignment.showPictures)
                                        PicturePresenter(
                                            imagePath = displayedImagePath,
                                            previousImagePath = previousDisplayedImagePath,
                                            transitionAlpha = pictureTransitionAlpha,
                                            slideOffset = pictureSlideOffset,
                                            animationType = animationType
                                        )

                                Presenting.PRESENTATION ->
                                    if (screenAssignment.showPictures)
                                        PresentationPresenter(
                                            frame = presentationFrame,
                                            slide = displayedSlide,
                                            previousSlide = previousDisplayedSlide,
                                            transitionAlpha = slideTransitionAlpha,
                                            slideOffset = slideSlideOffset,
                                            animationType = animationType,
                                            frozen = slideFrozen
                                        )

                                Presenting.MEDIA ->
                                    if (screenAssignment.showMedia) {
                                        if (mediaViewModel.isAudioFile) {
                                            // Audio: playback handled by hidden VideoPlayer in MainDesktop
                                            // Projection shows background only
                                        } else {
                                            MediaPresenter(
                                                modifier = Modifier.fillMaxSize(),
                                                audioDeviceId = appSettings.projectionSettings.audioOutputDeviceId,
                                                transitionAlpha = mediaTransitionAlpha
                                            )
                                        }
                                    }

                                Presenting.LOWER_THIRD ->
                                    if (screenAssignment.showStreaming)
                                        LowerThirdPresenter(
                                            composition = lottieComposition,
                                            progress = { presenterManager.lottieProgress.value },
                                            appSettings = appSettings,
                                            frame = lottieFrame
                                        )

                                Presenting.ANNOUNCEMENTS ->
                                    if (screenAssignment.showAnnouncements)
                                        AnnouncementsPresenter(
                                            text = displayedAnnouncementText,
                                            appSettings = appSettings,
                                            outputRole = primaryRole,
                                            transitionAlpha = announcementTransitionAlpha,
                                            onFinished = clearAnnouncementOnFinish,
                                            showBackground = showBg
                                        )

                                Presenting.WEBSITE ->
                                    if (screenAssignment.showWebsite) WebsitePresenter(
                                        url = websiteUrl,
                                        modifier = Modifier.fillMaxSize(),
                                        onSnapshot = { bitmap -> presenterManager.setWebSnapshot(bitmap) },
                                        onBrowserCreated = { browser -> presenterManager.setLiveBrowser(browser) },
                                        onUrlChanged = { newUrl -> presenterManager.setWebsiteUrl(newUrl) },
                                        onTitleChanged = { title -> presenterManager.setWebPageTitle(title) },
                                        audioDeviceId = appSettings.projectionSettings.audioOutputDeviceId
                                    )

                                Presenting.CANVAS -> ScenePresenter(scene = activeScene)

                                Presenting.QA ->
                                    if (screenAssignment.showQA) {
                                        if (showQRCodeOnDisplay) {
                                            QAQRCodePresenter(
                                                url = "${qaDisplayUrl.ifEmpty { serverUrl }}/qa",
                                                qaSettings = appSettings.qaSettings,
                                                transitionAlpha = qaTransitionAlpha,
                                            )
                                        } else {
                                            QAPresenter(
                                                question = displayedQuestion,
                                                qaSettings = appSettings.qaSettings,
                                                transitionAlpha = qaTransitionAlpha,
                                            )
                                        }
                                    }

                                Presenting.STT ->
                                    if (screenAssignment.showSTT) {
                                        STTPresenter(
                                            segments = sttManager.segments,
                                            inProgressText = sttManager.inProgressText.value,
                                            translationSegments = sttManager.translationSegments,
                                            inProgressTranslation = sttManager.inProgressTranslation.value,
                                            highlightedWords = sttManager.highlightedWords,
                                            sttSettings = appSettings.sttSettings,
                                        )
                                    }
                                Presenting.DICTIONARY ->
                                    if (screenAssignment.showDictionary)
                                        DictionaryPresenter(
                                            dictionarySettings = appSettings.dictionarySettings,
                                            entry = displayedDictionaryEntry,
                                            outputRole = primaryRole,
                                            transitionAlpha = 1f
                                        )
                                Presenting.NONE -> { /* nothing */
                                }
                            }
                        }

                        // Clear live browser ref when leaving WEBSITE mode
                        LaunchedEffect(presentingMode) {
                            if (presentingMode != Presenting.WEBSITE) {
                                presenterManager.setLiveBrowser(null)
                            }
                        }

                        if (screenNumber != null && identifyingScreen) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.75f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(Res.string.screen_number, screenNumber),
                                    color = Color.White,
                                    fontSize = 96.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Identify the OS primary monitor and build list of non-primary screens
    val defaultDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
    val availableScreens = screens.indices.filter { screens[it] != defaultDevice }

    val deckLinkDeviceCount = if (DeckLinkManager.isAvailable()) DeckLinkManager.listDevices().size else 0
    val windowCount = availableScreens.size + deckLinkDeviceCount
    // Dev convenience: on a single-monitor dev machine there's no non-primary monitor or DeckLink
    // device to open a real output window on. Show Output 1 as an ordinary window instead of not
    // rendering at all, driven by the same "Toggle Presenter Displays" button/state.
    val devWindowedFallback = !BuildConfig.IS_RELEASE && windowCount == 0
    for (i in 0 until (windowCount + if (devWindowedFallback) 1 else 0)) {
        if (devWindowedFallback && i >= windowCount) {
            val screenAssignment = proj.getAssignment(0)
            val effectiveMode = screenLocks[0] ?: presentingMode
            val fallbackWindowState = remember { WindowState(width = 960.dp, height = 540.dp) }
            Window(
                visible = showPresenterWindow,
                title = stringResource(Res.string.presenter_view_title, 1),
                icon = painterResource(Res.drawable.ic_app_icon),
                onCloseRequest = { presenterManager.setShowPresenterWindow(false) },
                state = fallbackWindowState,
                undecorated = false,
                resizable = true,
                alwaysOnTop = false,
            ) {
                presenterOutputContent(screenAssignment, effectiveMode, null)
            }
            continue
        }
        val screenAssignment = proj.getAssignment(i)
        val effectiveMode = screenLocks[i] ?: presentingMode

        // DeckLink outputs: render via offscreen Window + pixel capture
        if (screenAssignment.targetType == "decklink") {
            if (showPresenterWindow && screenAssignment.targetDisplay >= 0) {
                val deckLinkRole = screenAssignment.primaryOutputRole
                DeckLinkComposeOutput(
                    deviceIndex = screenAssignment.targetDisplay,
                    outputRole = deckLinkRole,
                    appSettings = appSettings,
                    mediaViewModel = mediaViewModel,
                    isLowerThird = screenAssignment.isLowerThird,
                ) {
                    var prevEffectiveMode by remember { mutableStateOf(effectiveMode) }
                    val screenCrossfadeActive = (appSettings.bibleSettings.crossfade || appSettings.songSettings.crossfade) && effectiveMode != Presenting.NONE && prevEffectiveMode != Presenting.NONE
                    if (effectiveMode != prevEffectiveMode) prevEffectiveMode = effectiveMode
                    Crossfade(
                        targetState = effectiveMode,
                        animationSpec = if (screenCrossfadeActive) tween(modeCrossfadeDuration) else snap()
                    ) { mode ->
                    when (mode) {
                        Presenting.BIBLE ->
                            if (screenAssignment.showBible) {
                                BiblePresenter(
                                    selectedVerses = displayedVerses,
                                    appSettings = appSettings,
                                    isLowerThird = screenAssignment.isLowerThird,
                                    isLowerThirdVertical = screenAssignment.isLowerThirdVertical,
                                    outputRole = deckLinkRole,
                                    transitionAlpha = bibleTransitionAlpha,
                                    crossfadeEnabled = appSettings.bibleSettings.crossfade,
                                    languageMode = screenAssignment.bibleMode
                                )
                            }

                        Presenting.LYRICS ->
                            if (screenAssignment.showSongs) {
                                SongPresenter(
                                    lyricSection = displayedLyricSection,
                                    appSettings = appSettings,
                                    isLowerThird = screenAssignment.isLowerThird,
                                    isLowerThirdVertical = screenAssignment.isLowerThirdVertical,
                                    outputRole = deckLinkRole,
                                    transitionAlpha = songTransitionAlpha,
                                    displayLineIndex = songDisplayLineIndex,
                                    lookAheadEnabled = screenAssignment.songLookAhead,
                                    allLyricSections = allLyricSections,
                                    displaySectionIndex = songDisplaySectionIndex,
                                    crossfadeEnabled = appSettings.songSettings.crossfade,
                                    languageOverride = screenAssignment.songMode
                                )
                            }

                        Presenting.PICTURES ->
                            if (screenAssignment.showPictures)
                                PicturePresenter(
                                    imagePath = displayedImagePath,
                                    previousImagePath = previousDisplayedImagePath,
                                    transitionAlpha = pictureTransitionAlpha,
                                    slideOffset = pictureSlideOffset,
                                    animationType = animationType
                                )

                        Presenting.PRESENTATION ->
                            if (screenAssignment.showPictures)
                                PresentationPresenter(
                                    frame = presentationFrame,
                                    slide = displayedSlide,
                                    previousSlide = previousDisplayedSlide,
                                    transitionAlpha = slideTransitionAlpha,
                                    slideOffset = slideSlideOffset,
                                    animationType = animationType,
                                    frozen = slideFrozen
                                )

                        Presenting.MEDIA ->
                            if (screenAssignment.showMedia) {
                                if (mediaViewModel.isAudioFile) {
                                    // Audio: background only
                                } else {
                                    MediaPresenter(
                                        modifier = Modifier.fillMaxSize(),
                                        audioDeviceId = appSettings.projectionSettings.audioOutputDeviceId,
                                        transitionAlpha = mediaTransitionAlpha
                                    )
                                }
                            }

                        Presenting.LOWER_THIRD ->
                            if (screenAssignment.showStreaming)
                                LowerThirdPresenter(
                                    composition = lottieComposition,
                                    progress = { presenterManager.lottieProgress.value },
                                    appSettings = appSettings,
                                    frame = lottieFrame
                                )

                        Presenting.ANNOUNCEMENTS ->
                            if (screenAssignment.showAnnouncements)
                                AnnouncementsPresenter(
                                    text = displayedAnnouncementText,
                                    appSettings = appSettings,
                                    outputRole = deckLinkRole,
                                    transitionAlpha = announcementTransitionAlpha,
                                    onFinished = clearAnnouncementOnFinish
                                )

                        Presenting.WEBSITE ->
                            if (screenAssignment.showWebsite) WebsitePresenter(
                                url = websiteUrl,
                                modifier = Modifier.fillMaxSize(),
                                onSnapshot = { bitmap -> presenterManager.setWebSnapshot(bitmap) },
                                onUrlChanged = { newUrl -> presenterManager.setWebsiteUrl(newUrl) },
                                onTitleChanged = { title -> presenterManager.setWebPageTitle(title) },
                                audioDeviceId = appSettings.projectionSettings.audioOutputDeviceId,
                                outputRole = Constants.OUTPUT_ROLE_KEY
                            )

                        Presenting.CANVAS -> ScenePresenter(scene = activeScene)

                        Presenting.QA ->
                            if (screenAssignment.showQA) {
                                if (showQRCodeOnDisplay) {
                                    QAQRCodePresenter(
                                        url = "${qaDisplayUrl.ifEmpty { serverUrl }}/qa",
                                        qaSettings = appSettings.qaSettings,
                                        transitionAlpha = qaTransitionAlpha,
                                    )
                                } else {
                                    QAPresenter(
                                        question = displayedQuestion,
                                        qaSettings = appSettings.qaSettings,
                                        transitionAlpha = qaTransitionAlpha,
                                    )
                                }
                            }


                        Presenting.STT ->
                            if (screenAssignment.showSTT) {
                                STTPresenter(
                                    segments = sttManager.segments,
                                    inProgressText = sttManager.inProgressText.value,
                                    translationSegments = sttManager.translationSegments,
                                    inProgressTranslation = sttManager.inProgressTranslation.value,
                                    highlightedWords = sttManager.highlightedWords,
                                    sttSettings = appSettings.sttSettings,
                                )
                            }
                        Presenting.DICTIONARY ->
                            if (screenAssignment.showDictionary)
                                DictionaryPresenter(
                                    dictionarySettings = appSettings.dictionarySettings,
                                    entry = displayedDictionaryEntry,
                                    outputRole = deckLinkRole,
                                    transitionAlpha = 1f
                                )
                        Presenting.NONE -> { /* nothing */ }
                    }
                    }
                }
            }

            // DeckLink key output
            if (showPresenterWindow && screenAssignment.hasKeyOutput && screenAssignment.keyTargetType == "decklink" && screenAssignment.keyTargetDisplay >= 0) {
                DeckLinkComposeOutput(
                    deviceIndex = screenAssignment.keyTargetDisplay,
                    outputRole = Constants.OUTPUT_ROLE_KEY,
                    appSettings = appSettings,
                    mediaViewModel = mediaViewModel,
                    isLowerThird = screenAssignment.isLowerThird,
                ) {
                    var prevEffectiveMode by remember { mutableStateOf(effectiveMode) }
                    val screenCrossfadeActive = (appSettings.bibleSettings.crossfade || appSettings.songSettings.crossfade) && effectiveMode != Presenting.NONE && prevEffectiveMode != Presenting.NONE
                    if (effectiveMode != prevEffectiveMode) prevEffectiveMode = effectiveMode
                    Crossfade(targetState = effectiveMode, animationSpec = if (screenCrossfadeActive) tween(modeCrossfadeDuration) else snap()) { mode ->
                    when (mode) {
                        Presenting.BIBLE ->
                            if (screenAssignment.showBible) {
                                BiblePresenter(
                                    selectedVerses = displayedVerses,
                                    appSettings = appSettings,
                                    isLowerThird = screenAssignment.isLowerThird,
                                    isLowerThirdVertical = screenAssignment.isLowerThirdVertical,
                                    outputRole = Constants.OUTPUT_ROLE_KEY,
                                    transitionAlpha = bibleTransitionAlpha,
                                    crossfadeEnabled = appSettings.bibleSettings.crossfade,
                                    languageMode = screenAssignment.bibleMode
                                )
                            }

                        Presenting.LYRICS ->
                            if (screenAssignment.showSongs) {
                                SongPresenter(
                                    lyricSection = displayedLyricSection,
                                    appSettings = appSettings,
                                    isLowerThird = screenAssignment.isLowerThird,
                                    isLowerThirdVertical = screenAssignment.isLowerThirdVertical,
                                    outputRole = Constants.OUTPUT_ROLE_KEY,
                                    transitionAlpha = songTransitionAlpha,
                                    displayLineIndex = songDisplayLineIndex,
                                    lookAheadEnabled = screenAssignment.songLookAhead,
                                    allLyricSections = allLyricSections,
                                    displaySectionIndex = songDisplaySectionIndex,
                                    crossfadeEnabled = appSettings.songSettings.crossfade,
                                    languageOverride = screenAssignment.songMode
                                )
                            }

                        Presenting.PICTURES ->
                            if (screenAssignment.showPictures)
                                PicturePresenter(
                                    imagePath = displayedImagePath,
                                    previousImagePath = previousDisplayedImagePath,
                                    transitionAlpha = pictureTransitionAlpha,
                                    slideOffset = pictureSlideOffset,
                                    animationType = animationType,
                                    outputRole = Constants.OUTPUT_ROLE_KEY
                                )

                        Presenting.PRESENTATION ->
                            if (screenAssignment.showPictures)
                                PresentationPresenter(
                                    frame = presentationFrame,
                                    slide = displayedSlide,
                                    previousSlide = previousDisplayedSlide,
                                    transitionAlpha = slideTransitionAlpha,
                                    slideOffset = slideSlideOffset,
                                    animationType = animationType,
                                    outputRole = Constants.OUTPUT_ROLE_KEY,
                                    frozen = slideFrozen
                                )

                        Presenting.MEDIA ->
                            if (screenAssignment.showMedia) {
                                if (mediaViewModel.isAudioFile) {
                                    // Audio: background only
                                } else {
                                    MediaPresenter(
                                        modifier = Modifier.fillMaxSize(),
                                        audioDeviceId = appSettings.projectionSettings.audioOutputDeviceId,
                                        transitionAlpha = mediaTransitionAlpha,
                                        outputRole = Constants.OUTPUT_ROLE_KEY
                                    )
                                }
                            }

                        Presenting.LOWER_THIRD ->
                            if (screenAssignment.showStreaming)
                                LowerThirdPresenter(
                                    composition = lottieComposition,
                                    progress = { presenterManager.lottieProgress.value },
                                    appSettings = appSettings,
                                    outputRole = Constants.OUTPUT_ROLE_KEY,
                                    frame = lottieFrame
                                )

                        Presenting.ANNOUNCEMENTS ->
                            if (screenAssignment.showAnnouncements)
                                AnnouncementsPresenter(
                                    text = displayedAnnouncementText,
                                    appSettings = appSettings,
                                    outputRole = Constants.OUTPUT_ROLE_KEY,
                                    transitionAlpha = announcementTransitionAlpha,
                                    onFinished = {
                                        presenterManager.setAnnouncementText("")
                                        presenterManager.setDisplayedAnnouncementText("")
                                    }
                                )

                        Presenting.WEBSITE ->
                            if (screenAssignment.showWebsite) WebsitePresenter(
                                url = websiteUrl,
                                modifier = Modifier.fillMaxSize(),
                                onSnapshot = { bitmap -> presenterManager.setWebSnapshot(bitmap) },
                                onUrlChanged = { newUrl -> presenterManager.setWebsiteUrl(newUrl) },
                                onTitleChanged = { title -> presenterManager.setWebPageTitle(title) },
                                audioDeviceId = appSettings.projectionSettings.audioOutputDeviceId,
                                outputRole = Constants.OUTPUT_ROLE_KEY
                            )

                        Presenting.CANVAS -> ScenePresenter(scene = activeScene)

                        Presenting.QA ->
                            if (screenAssignment.showQA) {
                                if (showQRCodeOnDisplay) {
                                    QAQRCodePresenter(
                                        url = "${qaDisplayUrl.ifEmpty { serverUrl }}/qa",
                                        qaSettings = appSettings.qaSettings,
                                        outputRole = Constants.OUTPUT_ROLE_KEY,
                                        transitionAlpha = qaTransitionAlpha,
                                    )
                                } else {
                                    QAPresenter(
                                        question = displayedQuestion,
                                        qaSettings = appSettings.qaSettings,
                                        outputRole = Constants.OUTPUT_ROLE_KEY,
                                        transitionAlpha = qaTransitionAlpha,
                                    )
                                }
                            }


                        Presenting.STT ->
                            if (screenAssignment.showSTT) {
                                STTPresenter(
                                    segments = sttManager.segments,
                                    inProgressText = sttManager.inProgressText.value,
                                    translationSegments = sttManager.translationSegments,
                                    inProgressTranslation = sttManager.inProgressTranslation.value,
                                    highlightedWords = sttManager.highlightedWords,
                                    sttSettings = appSettings.sttSettings,
                                )
                            }
                        Presenting.DICTIONARY ->
                            if (screenAssignment.showDictionary)
                                DictionaryPresenter(
                                    dictionarySettings = appSettings.dictionarySettings,
                                    entry = displayedDictionaryEntry,
                                    outputRole = Constants.OUTPUT_ROLE_KEY,
                                    transitionAlpha = 1f
                                )
                        Presenting.NONE -> { /* nothing */ }
                    }
                    }
                }
            }

            // Key output on a regular screen when primary is DeckLink
            if (showPresenterWindow && screenAssignment.hasKeyOutput && screenAssignment.keyTargetType == "screen") {
                val keyScreenIndex = findScreenIndexByBounds(
                    screens,
                    screenAssignment.keyTargetBoundsX,
                    screenAssignment.keyTargetBoundsY,
                    screenAssignment.keyTargetBoundsW,
                    screenAssignment.keyTargetBoundsH
                ) ?: screenAssignment.keyTargetDisplay
                if (keyScreenIndex in screens.indices) {
                    val keyWindowState = remember(i, keyScreenIndex) {
                        val b = screens[keyScreenIndex].defaultConfiguration.bounds
                        WindowState(
                            placement = WindowPlacement.Floating,
                            position = WindowPosition(b.x.dp, b.y.dp),
                            width = b.width.dp,
                            height = b.height.dp
                        )
                    }

                    Window(
                        visible = true,
                        title = "Key Output ${i + 1}",
                        icon = painterResource(Res.drawable.ic_app_icon),
                        onCloseRequest = { presenterManager.setShowPresenterWindow(false) },
                        state = keyWindowState,
                        undecorated = true,
                        resizable = false,
                        alwaysOnTop = true,
                    ) {
                        CompositionLocalProvider(LocalMediaViewModel provides mediaViewModel) {
                            PresenterScreen(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                outputRole = Constants.OUTPUT_ROLE_KEY
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    var prevEffectiveMode by remember { mutableStateOf(effectiveMode) }
                                    val screenCrossfadeActive = (appSettings.bibleSettings.crossfade || appSettings.songSettings.crossfade) && effectiveMode != Presenting.NONE && prevEffectiveMode != Presenting.NONE
                                    if (effectiveMode != prevEffectiveMode) prevEffectiveMode = effectiveMode
                                    Crossfade(targetState = effectiveMode, animationSpec = if (screenCrossfadeActive) tween(modeCrossfadeDuration) else snap()) { mode ->
                    when (mode) {
                                        Presenting.BIBLE ->
                                            if (screenAssignment.showBible) {
                                                BiblePresenter(
                                                    selectedVerses = displayedVerses,
                                                    appSettings = appSettings,
                                                    isLowerThird = screenAssignment.isLowerThird,
                                                    isLowerThirdVertical = screenAssignment.isLowerThirdVertical,
                                                    outputRole = Constants.OUTPUT_ROLE_KEY,
                                                    transitionAlpha = bibleTransitionAlpha,
                                                    crossfadeEnabled = appSettings.bibleSettings.crossfade,
                                                    languageMode = screenAssignment.bibleMode
                                                )
                                            }

                                        Presenting.LYRICS ->
                                            if (screenAssignment.showSongs) {
                                                SongPresenter(
                                                    lyricSection = displayedLyricSection,
                                                    appSettings = appSettings,
                                                    isLowerThird = screenAssignment.isLowerThird,
                                                    isLowerThirdVertical = screenAssignment.isLowerThirdVertical,
                                                    outputRole = Constants.OUTPUT_ROLE_KEY,
                                                    transitionAlpha = songTransitionAlpha,
                                                    displayLineIndex = songDisplayLineIndex,
                                                    lookAheadEnabled = screenAssignment.songLookAhead,
                                                    allLyricSections = allLyricSections,
                                                    displaySectionIndex = songDisplaySectionIndex,
                                                    crossfadeEnabled = appSettings.songSettings.crossfade,
                                                    languageOverride = screenAssignment.songMode
                                                )
                                            }

                                        Presenting.PICTURES ->
                                            if (screenAssignment.showPictures)
                                                PicturePresenter(
                                                    imagePath = displayedImagePath,
                                                    previousImagePath = previousDisplayedImagePath,
                                                    transitionAlpha = pictureTransitionAlpha,
                                                    slideOffset = pictureSlideOffset,
                                                    animationType = animationType,
                                                    outputRole = Constants.OUTPUT_ROLE_KEY
                                                )

                                        Presenting.PRESENTATION ->
                                            if (screenAssignment.showPictures)
                                                PresentationPresenter(
                                    frame = presentationFrame,
                                    slide = displayedSlide,
                                    previousSlide = previousDisplayedSlide,
                                    transitionAlpha = slideTransitionAlpha,
                                    slideOffset = slideSlideOffset,
                                    animationType = animationType,
                                    outputRole = Constants.OUTPUT_ROLE_KEY,
                                    frozen = slideFrozen
                                )

                                        Presenting.MEDIA ->
                                            if (screenAssignment.showMedia) {
                                                if (mediaViewModel.isAudioFile) {
                                                    // Audio: background only
                                                } else {
                                                    MediaPresenter(
                                                        modifier = Modifier.fillMaxSize(),
                                                        audioDeviceId = appSettings.projectionSettings.audioOutputDeviceId,
                                                        transitionAlpha = mediaTransitionAlpha,
                                                        outputRole = Constants.OUTPUT_ROLE_KEY
                                                    )
                                                }
                                            }

                                        Presenting.LOWER_THIRD ->
                                            if (screenAssignment.showStreaming)
                                                LowerThirdPresenter(
                                                    composition = lottieComposition,
                                                    progress = { presenterManager.lottieProgress.value },
                                                    appSettings = appSettings,
                                                    outputRole = Constants.OUTPUT_ROLE_KEY,
                                                    frame = lottieFrame
                                                )

                                        Presenting.ANNOUNCEMENTS ->
                                            if (screenAssignment.showAnnouncements)
                                                AnnouncementsPresenter(
                                                    text = displayedAnnouncementText,
                                                    appSettings = appSettings,
                                                    outputRole = Constants.OUTPUT_ROLE_KEY,
                                                    transitionAlpha = announcementTransitionAlpha,
                                                    onFinished = {
                                                        presenterManager.setAnnouncementText("")
                                                        presenterManager.setDisplayedAnnouncementText("")
                                                    }
                                                )

                                        Presenting.WEBSITE ->
                                            if (screenAssignment.showWebsite) WebsitePresenter(
                                                url = websiteUrl,
                                                modifier = Modifier.fillMaxSize(),
                                                onSnapshot = { bitmap -> presenterManager.setWebSnapshot(bitmap) },
                                                onUrlChanged = { newUrl -> presenterManager.setWebsiteUrl(newUrl) },
                                                onTitleChanged = { title -> presenterManager.setWebPageTitle(title) },
                                                audioDeviceId = appSettings.projectionSettings.audioOutputDeviceId,
                                                outputRole = Constants.OUTPUT_ROLE_KEY
                                            )

                                        Presenting.CANVAS -> ScenePresenter(scene = activeScene)

                                        Presenting.QA ->
                                            if (screenAssignment.showQA) {
                                                if (showQRCodeOnDisplay) {
                                                    QAQRCodePresenter(
                                                        url = "${qaDisplayUrl.ifEmpty { serverUrl }}/qa",
                                                        qaSettings = appSettings.qaSettings,
                                                        outputRole = Constants.OUTPUT_ROLE_KEY,
                                                        transitionAlpha = qaTransitionAlpha,
                                                    )
                                                } else {
                                                    QAPresenter(
                                                        question = displayedQuestion,
                                                        qaSettings = appSettings.qaSettings,
                                                        outputRole = Constants.OUTPUT_ROLE_KEY,
                                                        transitionAlpha = qaTransitionAlpha,
                                                    )
                                                }
                                            }


                                        Presenting.STT ->
                                            if (screenAssignment.showSTT) {
                                                STTPresenter(
                                                    segments = sttManager.segments,
                                                    inProgressText = sttManager.inProgressText.value,
                                                    translationSegments = sttManager.translationSegments,
                                                    inProgressTranslation = sttManager.inProgressTranslation.value,
                                                    highlightedWords = sttManager.highlightedWords,
                                                    sttSettings = appSettings.sttSettings,
                                                )
                                            }
                                        Presenting.DICTIONARY ->
                                            if (screenAssignment.showDictionary)
                                                DictionaryPresenter(
                                                    dictionarySettings = appSettings.dictionarySettings,
                                                    entry = displayedDictionaryEntry,
                                                    outputRole = Constants.OUTPUT_ROLE_KEY,
                                                    transitionAlpha = 1f
                                                )
                                        Presenting.NONE -> { /* nothing */ }
                                    }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            continue
        }

        if (screenAssignment.targetDisplay == Constants.KEY_TARGET_NONE) continue

        // Resolve target display
        val targetScreenIndex = findScreenIndexByBounds(
            screens,
            screenAssignment.targetBoundsX,
            screenAssignment.targetBoundsY,
            screenAssignment.targetBoundsW,
            screenAssignment.targetBoundsH
        ) ?: if (screenAssignment.targetDisplay >= 0 && screenAssignment.targetDisplay < screens.size) {
            screenAssignment.targetDisplay
        } else {
            availableScreens.getOrNull(i) ?: continue
        }

        // Skip if the target screen doesn't exist
        if (targetScreenIndex < 0 || targetScreenIndex >= screens.size) continue

        // Per-output background toggle
        val showBg = if (screenAssignment.isLowerThird) screenAssignment.showLowerThirdBackground else screenAssignment.showFullscreenBackground

        // Derive output role from key target configuration
        val primaryRole = screenAssignment.primaryOutputRole

        val windowState = remember(i) {
            val b = screens[targetScreenIndex].defaultConfiguration.bounds
            WindowState(
                placement = WindowPlacement.Floating,
                position = WindowPosition(b.x.dp, b.y.dp),
                width = b.width.dp,
                height = b.height.dp
            )
        }

        LaunchedEffect(targetScreenIndex) {
            val b = screens[targetScreenIndex].defaultConfiguration.bounds
            windowState.position = WindowPosition(b.x.dp, b.y.dp)
            windowState.size = DpSize(b.width.dp, b.height.dp)
        }

        // Primary window (fill or normal)
        val presenterTitle = stringResource(Res.string.presenter_view_title, i + 1)
        Window(
            visible = showPresenterWindow,
            title = presenterTitle,
            icon = painterResource(Res.drawable.ic_app_icon),
            onCloseRequest = { presenterManager.setShowPresenterWindow(false) },
            state = windowState,
            undecorated = true,
            resizable = false,
            alwaysOnTop = true,
        ) {
            presenterOutputContent(screenAssignment, effectiveMode, i + 1)
        }

        // Key output window — spawned when a key target is configured
        if (screenAssignment.hasKeyOutput && screenAssignment.keyTargetType != "decklink") {
            val keyScreenIndex = findScreenIndexByBounds(
                screens,
                screenAssignment.keyTargetBoundsX,
                screenAssignment.keyTargetBoundsY,
                screenAssignment.keyTargetBoundsW,
                screenAssignment.keyTargetBoundsH
            ) ?: screenAssignment.keyTargetDisplay
            if (keyScreenIndex in screens.indices) {
                val keyWindowState = remember(i, keyScreenIndex) {
                    val b = screens[keyScreenIndex].defaultConfiguration.bounds
                    WindowState(
                        placement = WindowPlacement.Floating,
                        position = WindowPosition(b.x.dp, b.y.dp),
                        width = b.width.dp,
                        height = b.height.dp
                    )
                }

                val keyOutputTitle = stringResource(Res.string.key_output_title, i + 1)
                Window(
                    visible = showPresenterWindow,
                    title = keyOutputTitle,
                    icon = painterResource(Res.drawable.ic_app_icon),
                    onCloseRequest = { presenterManager.setShowPresenterWindow(false) },
                    state = keyWindowState,
                    undecorated = true,
                    resizable = false,
                    alwaysOnTop = true,
                ) {
                    CompositionLocalProvider(LocalMediaViewModel provides mediaViewModel) {
                        PresenterScreen(
                            modifier = Modifier.fillMaxSize(),
                            appSettings = appSettings,
                            outputRole = Constants.OUTPUT_ROLE_KEY
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                                            mediaViewModel.pause()
                                            presenterManager.requestClearDisplay()
                                            true
                                        } else false
                                    }
                            ) {
                                var prevEffectiveMode by remember { mutableStateOf(effectiveMode) }
                                val screenCrossfadeActive = (appSettings.bibleSettings.crossfade || appSettings.songSettings.crossfade) && effectiveMode != Presenting.NONE && prevEffectiveMode != Presenting.NONE
                                if (effectiveMode != prevEffectiveMode) prevEffectiveMode = effectiveMode
                                Crossfade(targetState = effectiveMode, animationSpec = if (screenCrossfadeActive) tween(modeCrossfadeDuration) else snap()) { mode ->
                    when (mode) {
                                    Presenting.BIBLE ->
                                        if (screenAssignment.showBible) {
                                            BiblePresenter(
                                                selectedVerses = displayedVerses,
                                                appSettings = appSettings,
                                                isLowerThird = screenAssignment.isLowerThird,
                                                isLowerThirdVertical = screenAssignment.isLowerThirdVertical,
                                                outputRole = Constants.OUTPUT_ROLE_KEY,
                                                transitionAlpha = bibleTransitionAlpha,
                                                crossfadeEnabled = appSettings.bibleSettings.crossfade,
                                                languageMode = screenAssignment.bibleMode
                                            )
                                        }

                                    Presenting.LYRICS ->
                                        if (screenAssignment.showSongs) {
                                            SongPresenter(
                                                lyricSection = displayedLyricSection,
                                                appSettings = appSettings,
                                                isLowerThird = screenAssignment.isLowerThird,
                                                isLowerThirdVertical = screenAssignment.isLowerThirdVertical,
                                                outputRole = Constants.OUTPUT_ROLE_KEY,
                                                transitionAlpha = songTransitionAlpha,
                                                displayLineIndex = songDisplayLineIndex,
                                                lookAheadEnabled = screenAssignment.songLookAhead,
                                                allLyricSections = allLyricSections,
                                                displaySectionIndex = songDisplaySectionIndex,
                                                crossfadeEnabled = appSettings.songSettings.crossfade,
                                                languageOverride = screenAssignment.songMode
                                            )
                                        }

                                    Presenting.PICTURES ->
                                        if (screenAssignment.showPictures)
                                            PicturePresenter(
                                                imagePath = displayedImagePath,
                                                previousImagePath = previousDisplayedImagePath,
                                                transitionAlpha = pictureTransitionAlpha,
                                                slideOffset = pictureSlideOffset,
                                                animationType = animationType,
                                                outputRole = Constants.OUTPUT_ROLE_KEY
                                            )

                                    Presenting.PRESENTATION ->
                                        if (screenAssignment.showPictures)
                                            PresentationPresenter(
                                                frame = presentationFrame,
                                                slide = displayedSlide,
                                                previousSlide = previousDisplayedSlide,
                                                transitionAlpha = slideTransitionAlpha,
                                                slideOffset = slideSlideOffset,
                                                animationType = animationType,
                                                outputRole = Constants.OUTPUT_ROLE_KEY
                                            )

                                    Presenting.MEDIA ->
                                        if (screenAssignment.showMedia) {
                                            if (mediaViewModel.isAudioFile) {
                                                // Audio: background only
                                            } else {
                                                MediaPresenter(
                                                    modifier = Modifier.fillMaxSize(),
                                                    audioDeviceId = appSettings.projectionSettings.audioOutputDeviceId,
                                                    transitionAlpha = mediaTransitionAlpha,
                                                    outputRole = Constants.OUTPUT_ROLE_KEY
                                                )
                                            }
                                        }

                                    Presenting.LOWER_THIRD ->
                                        if (screenAssignment.showStreaming)
                                            LowerThirdPresenter(
                                                composition = lottieComposition,
                                                progress = { presenterManager.lottieProgress.value },
                                                appSettings = appSettings,
                                                outputRole = Constants.OUTPUT_ROLE_KEY,
                                                frame = lottieFrame
                                            )

                                    Presenting.ANNOUNCEMENTS ->
                                        if (screenAssignment.showAnnouncements)
                                            AnnouncementsPresenter(
                                                text = displayedAnnouncementText,
                                                appSettings = appSettings,
                                                outputRole = Constants.OUTPUT_ROLE_KEY,
                                                transitionAlpha = announcementTransitionAlpha,
                                                onFinished = {
                                                    presenterManager.setAnnouncementText("")
                                                    presenterManager.setDisplayedAnnouncementText("")
                                                }
                                            )

                                    Presenting.WEBSITE ->
                                        if (screenAssignment.showWebsite) WebsitePresenter(
                                            url = websiteUrl,
                                            modifier = Modifier.fillMaxSize(),
                                            onSnapshot = { bitmap -> presenterManager.setWebSnapshot(bitmap) },
                                            onUrlChanged = { newUrl -> presenterManager.setWebsiteUrl(newUrl) },
                                            onTitleChanged = { title -> presenterManager.setWebPageTitle(title) },
                                            audioDeviceId = appSettings.projectionSettings.audioOutputDeviceId,
                                            outputRole = Constants.OUTPUT_ROLE_KEY
                                        )

                                    Presenting.CANVAS -> ScenePresenter(scene = activeScene)

                                    Presenting.QA ->
                                        if (screenAssignment.showQA) {
                                            if (showQRCodeOnDisplay) {
                                                QAQRCodePresenter(
                                                    url = "${qaDisplayUrl.ifEmpty { serverUrl }}/qa",
                                                    qaSettings = appSettings.qaSettings,
                                                    transitionAlpha = qaTransitionAlpha,
                                                )
                                            } else {
                                                QAPresenter(
                                                    question = displayedQuestion,
                                                    qaSettings = appSettings.qaSettings,
                                                    transitionAlpha = qaTransitionAlpha,
                                                )
                                            }
                                        }


                                    Presenting.STT ->
                                        if (screenAssignment.showSTT) {
                                            STTPresenter(
                                                segments = sttManager.segments,
                                                inProgressText = sttManager.inProgressText.value,
                                                translationSegments = sttManager.translationSegments,
                                                inProgressTranslation = sttManager.inProgressTranslation.value,
                                                highlightedWords = sttManager.highlightedWords,
                                                sttSettings = appSettings.sttSettings,
                                            )
                                        }
                                    Presenting.DICTIONARY ->
                                        if (screenAssignment.showDictionary)
                                            DictionaryPresenter(
                                                dictionarySettings = appSettings.dictionarySettings,
                                                entry = displayedDictionaryEntry,
                                                outputRole = Constants.OUTPUT_ROLE_KEY,
                                                transitionAlpha = 1f
                                            )
                                    Presenting.NONE -> { /* nothing */
                                    }
                                }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Key output on DeckLink when primary is a regular screen
        if (screenAssignment.targetType != "decklink" && screenAssignment.hasKeyOutput && screenAssignment.keyTargetType == "decklink" && screenAssignment.keyTargetDisplay >= 0) {
            if (showPresenterWindow) {
                DeckLinkComposeOutput(
                    deviceIndex = screenAssignment.keyTargetDisplay,
                    outputRole = Constants.OUTPUT_ROLE_KEY,
                    appSettings = appSettings,
                    mediaViewModel = mediaViewModel,
                    isLowerThird = screenAssignment.isLowerThird,
                ) {
                    var prevEffectiveMode by remember { mutableStateOf(effectiveMode) }
                    val screenCrossfadeActive = (appSettings.bibleSettings.crossfade || appSettings.songSettings.crossfade) && effectiveMode != Presenting.NONE && prevEffectiveMode != Presenting.NONE
                    if (effectiveMode != prevEffectiveMode) prevEffectiveMode = effectiveMode
                    Crossfade(targetState = effectiveMode, animationSpec = if (screenCrossfadeActive) tween(modeCrossfadeDuration) else snap()) { mode ->
                    when (mode) {
                        Presenting.BIBLE ->
                            if (screenAssignment.showBible) {
                                BiblePresenter(
                                    selectedVerses = displayedVerses,
                                    appSettings = appSettings,
                                    isLowerThird = screenAssignment.isLowerThird,
                                    isLowerThirdVertical = screenAssignment.isLowerThirdVertical,
                                    outputRole = Constants.OUTPUT_ROLE_KEY,
                                    transitionAlpha = bibleTransitionAlpha,
                                    crossfadeEnabled = appSettings.bibleSettings.crossfade,
                                    languageMode = screenAssignment.bibleMode
                                )
                            }

                        Presenting.LYRICS ->
                            if (screenAssignment.showSongs) {
                                SongPresenter(
                                    lyricSection = displayedLyricSection,
                                    appSettings = appSettings,
                                    isLowerThird = screenAssignment.isLowerThird,
                                    isLowerThirdVertical = screenAssignment.isLowerThirdVertical,
                                    outputRole = Constants.OUTPUT_ROLE_KEY,
                                    transitionAlpha = songTransitionAlpha,
                                    displayLineIndex = songDisplayLineIndex,
                                    lookAheadEnabled = screenAssignment.songLookAhead,
                                    allLyricSections = allLyricSections,
                                    displaySectionIndex = songDisplaySectionIndex,
                                    crossfadeEnabled = appSettings.songSettings.crossfade,
                                    languageOverride = screenAssignment.songMode
                                )
                            }

                        Presenting.PICTURES ->
                            if (screenAssignment.showPictures)
                                PicturePresenter(
                                    imagePath = displayedImagePath,
                                    previousImagePath = previousDisplayedImagePath,
                                    transitionAlpha = pictureTransitionAlpha,
                                    slideOffset = pictureSlideOffset,
                                    animationType = animationType,
                                    outputRole = Constants.OUTPUT_ROLE_KEY
                                )

                        Presenting.PRESENTATION ->
                            if (screenAssignment.showPictures)
                                PresentationPresenter(
                                    frame = presentationFrame,
                                    slide = displayedSlide,
                                    previousSlide = previousDisplayedSlide,
                                    transitionAlpha = slideTransitionAlpha,
                                    slideOffset = slideSlideOffset,
                                    animationType = animationType,
                                    outputRole = Constants.OUTPUT_ROLE_KEY,
                                    frozen = slideFrozen
                                )

                        Presenting.MEDIA ->
                            if (screenAssignment.showMedia) {
                                if (mediaViewModel.isAudioFile) {
                                    // Audio: background only
                                } else {
                                    MediaPresenter(
                                        modifier = Modifier.fillMaxSize(),
                                        audioDeviceId = appSettings.projectionSettings.audioOutputDeviceId,
                                        transitionAlpha = mediaTransitionAlpha,
                                        outputRole = Constants.OUTPUT_ROLE_KEY
                                    )
                                }
                            }

                        Presenting.LOWER_THIRD ->
                            if (screenAssignment.showStreaming)
                                LowerThirdPresenter(
                                    composition = lottieComposition,
                                    progress = { presenterManager.lottieProgress.value },
                                    appSettings = appSettings,
                                    outputRole = Constants.OUTPUT_ROLE_KEY,
                                    frame = lottieFrame
                                )

                        Presenting.ANNOUNCEMENTS ->
                            if (screenAssignment.showAnnouncements)
                                AnnouncementsPresenter(
                                    text = displayedAnnouncementText,
                                    appSettings = appSettings,
                                    outputRole = Constants.OUTPUT_ROLE_KEY,
                                    transitionAlpha = announcementTransitionAlpha,
                                    onFinished = {
                                        presenterManager.setAnnouncementText("")
                                        presenterManager.setDisplayedAnnouncementText("")
                                    }
                                )

                        Presenting.WEBSITE ->
                            if (screenAssignment.showWebsite) WebsitePresenter(
                                url = websiteUrl,
                                modifier = Modifier.fillMaxSize(),
                                onSnapshot = { bitmap -> presenterManager.setWebSnapshot(bitmap) },
                                onUrlChanged = { newUrl -> presenterManager.setWebsiteUrl(newUrl) },
                                onTitleChanged = { title -> presenterManager.setWebPageTitle(title) },
                                audioDeviceId = appSettings.projectionSettings.audioOutputDeviceId,
                                outputRole = Constants.OUTPUT_ROLE_KEY
                            )

                        Presenting.CANVAS -> ScenePresenter(scene = activeScene)

                        Presenting.QA ->
                            if (screenAssignment.showQA) {
                                if (showQRCodeOnDisplay) {
                                    QAQRCodePresenter(
                                        url = "${qaDisplayUrl.ifEmpty { serverUrl }}/qa",
                                        qaSettings = appSettings.qaSettings,
                                        transitionAlpha = qaTransitionAlpha,
                                    )
                                } else {
                                    QAPresenter(
                                        question = displayedQuestion,
                                        qaSettings = appSettings.qaSettings,
                                        transitionAlpha = qaTransitionAlpha,
                                    )
                                }
                            }


                        Presenting.STT ->
                            if (screenAssignment.showSTT) {
                                STTPresenter(
                                    segments = sttManager.segments,
                                    inProgressText = sttManager.inProgressText.value,
                                    translationSegments = sttManager.translationSegments,
                                    inProgressTranslation = sttManager.inProgressTranslation.value,
                                    highlightedWords = sttManager.highlightedWords,
                                    sttSettings = appSettings.sttSettings,
                                )
                            }
                        Presenting.DICTIONARY ->
                            if (screenAssignment.showDictionary)
                                DictionaryPresenter(
                                    dictionarySettings = appSettings.dictionarySettings,
                                    entry = displayedDictionaryEntry,
                                    outputRole = primaryRole,
                                    transitionAlpha = 1f
                                )
                        Presenting.NONE -> { /* nothing */ }
                    }
                    }
                }
            }
        }
    }
}
