package org.churchpresenter.app.churchpresenter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
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
import androidx.compose.animation.core.animate
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import org.churchpresenter.app.churchpresenter.composables.DeckLinkManager
import org.churchpresenter.app.churchpresenter.utils.findScreenIndexByBounds
import org.churchpresenter.app.churchpresenter.utils.rememberScreenDevices
import androidx.compose.ui.window.rememberWindowState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.app_name
import churchpresenter.composeapp.generated.resources.ic_app_icon
import churchpresenter.composeapp.generated.resources.loading
import churchpresenter.composeapp.generated.resources.presenter_view_title
import churchpresenter.composeapp.generated.resources.key_output_title
import churchpresenter.composeapp.generated.resources.screen_number
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.ScreenAssignment
import org.churchpresenter.app.churchpresenter.data.Language
import org.churchpresenter.app.churchpresenter.data.RemoteClientManager
import org.churchpresenter.app.churchpresenter.data.SettingsManager
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.app.churchpresenter.dialogs.AboutDialog
import org.churchpresenter.app.churchpresenter.dialogs.KeyboardShortcutsDialog
import org.churchpresenter.app.churchpresenter.dialogs.LicenseDialog
import org.churchpresenter.app.churchpresenter.dialogs.RemoteActivityNotification
import org.churchpresenter.app.churchpresenter.dialogs.RemoteActivityToastHost
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEvent
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEventDialog
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEventType
import org.churchpresenter.app.churchpresenter.dialogs.OptionsDialog
import org.churchpresenter.app.churchpresenter.presenter.DeckLinkComposeOutput
import org.churchpresenter.app.churchpresenter.presenter.AnnouncementsPresenter
import org.churchpresenter.app.churchpresenter.presenter.QAPresenter
import org.churchpresenter.app.churchpresenter.presenter.QAQRCodePresenter
import org.churchpresenter.app.churchpresenter.presenter.CefManager
import org.churchpresenter.app.churchpresenter.presenter.WebsitePresenter
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.app.churchpresenter.presenter.LowerThirdPresenter
import org.churchpresenter.app.churchpresenter.presenter.MediaPresenter
import org.churchpresenter.app.churchpresenter.presenter.PicturePresenter
import org.churchpresenter.app.churchpresenter.presenter.SlidePresenter
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.presenter.ScenePresenter
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.ui.theme.LanguageProvider
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.composables.preWarmJavaFX
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import org.churchpresenter.app.churchpresenter.composables.vlcCustomPath
import org.churchpresenter.app.churchpresenter.data.Bible
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.app.churchpresenter.server.TunnelStatus
import org.churchpresenter.app.churchpresenter.viewmodel.QAManager
import org.churchpresenter.app.churchpresenter.server.AddToScheduleRequest
import org.churchpresenter.app.churchpresenter.server.PendingRemoteRequest
import org.churchpresenter.app.churchpresenter.server.ProjectRequest
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.AnalyticsReporter
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import org.churchpresenter.app.churchpresenter.utils.UpdateChecker
import org.churchpresenter.app.churchpresenter.utils.UpdateInfo
import org.churchpresenter.app.churchpresenter.dialogs.StatisticsDialog
import org.churchpresenter.app.churchpresenter.dialogs.UpdateAvailableDialog
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.CoroutineExceptionHandler
import org.churchpresenter.app.churchpresenter.models.SelectedVerse


private var singleInstanceSocket: java.net.ServerSocket? = null

/**
 * Attempt to bind a local port to enforce single-instance.
 * Returns true if this is the first instance, false if another is already running.
 */
private fun acquireSingleInstanceLock(): Boolean {
    return try {
        // Bind to a fixed localhost port — if it's already taken, another instance is running
        singleInstanceSocket = java.net.ServerSocket(Constants.SINGLE_INSTANCE_PORT, 1, java.net.InetAddress.getLoopbackAddress())
        true
    } catch (_: Exception) {
        false
    }
}

fun main() {
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
    CrashReporter.initialize()
    AnalyticsReporter.initialize()
    AnalyticsReporter.logAppOpen()

    // Catch exceptions thrown inside coroutines / Compose lambdas —
    // these never reach Thread.setDefaultUncaughtExceptionHandler on their own.
    val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        CrashReporter.reportException(throwable, context = "CoroutineExceptionHandler")
    }

    // Pre-warm JavaFX on a background thread before UI starts
    preWarmJavaFX()

    // Initialize JCEF (Chromium) for embedded web browsing
    CefManager.init()

    // Set custom VLC path from saved settings before any composable checks isVlcAvailable
    vlcCustomPath = SettingsManager().loadSettings().projectionSettings.vlcPath

    application(exitProcessOnExit = true) {
        var appReady by remember { mutableStateOf(false) }
        // Business logic layer
        val settingsManager = remember { SettingsManager() }
        val statisticsManager = remember { StatisticsManager() }
        var appSettings by remember { mutableStateOf(settingsManager.loadSettings()) }

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

        var licenseAccepted by remember { mutableStateOf(appSettings.licenseAccepted) }

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
                else -> ThemeMode.SYSTEM
            }
            mutableStateOf(savedTheme)
        }
        val companionServer = remember { CompanionServer() }
        val qaManager = remember { QAManager() }
        remember(qaManager) { companionServer.qaManager = qaManager; true }
        // Sync QA settings to server
        LaunchedEffect(appSettings.qaSettings.adminPassword, appSettings.qaSettings.rateLimitCooldownSeconds) {
            companionServer.qaAdminPassword = appSettings.qaSettings.adminPassword
            companionServer.qaCooldownSeconds = appSettings.qaSettings.rateLimitCooldownSeconds
        }
        val tunnelStatus by companionServer.tunnelManager.status.collectAsState()
        val tunnelUrl by companionServer.tunnelManager.tunnelUrl.collectAsState()
        var qaDisplayUrl by remember { mutableStateOf("") }
        val remoteSelectSongFlow =
            remember { kotlinx.coroutines.flow.MutableSharedFlow<ScheduleItem.SongItem>(extraBufferCapacity = 8) }
        var showOptionsDialog by remember { mutableStateOf(false) }
        var showStatisticsDialog by remember { mutableStateOf(false) }
        var showKeyboardShortcutsDialog by remember { mutableStateOf(false) }
        var showAboutDialog by remember { mutableStateOf(false) }
        var pendingUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
        var selectedScheduleItemId by remember { mutableStateOf<String?>(null) }

        // Preload songs and bible at startup, then signal ready
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                companionServer.preloadData(
                    songStorageDir = appSettings.songSettings.storageDirectory,
                    bibleStorageDir = appSettings.bibleSettings.storageDirectory,
                    primaryBibleFileName = appSettings.bibleSettings.primaryBible
                )
                companionServer.updateLowerThirdFolder(appSettings.streamingSettings.lowerThirdFolder)
                // Seed API key from saved settings before starting, so the first
                // request is already checked against the correct key.
                companionServer.updateApiKey(
                    enabled = appSettings.serverSettings.apiKeyEnabled,
                    key = appSettings.serverSettings.apiKey
                )
                companionServer.updateFileUploadEnabled(appSettings.serverSettings.fileUploadEnabled)
                // Auto-start server if user previously enabled it
                if (appSettings.serverSettings.enabled) {
                    companionServer.start(
                        port = appSettings.serverSettings.port,
                        hostOverride = appSettings.serverSettings.serverHost
                    )
                }
            }
            appReady = true
            // Check for updates in background after startup
            val updateInfo = UpdateChecker.checkForUpdate()
            if (updateInfo != null) {
                pendingUpdateInfo = updateInfo
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
            SplashWindow()
        }

        if (appReady && licenseAccepted) {
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
                    companionServer.tunnelManager.shutdown()
                    exitApplication()
                },
                title = stringResource(Res.string.app_name),
                icon = painterResource(Res.drawable.ic_app_icon),
                state = state
            ) {
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
                                                        item.verseRange
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
                                                        item.verseRange
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
                                                            item.verseRange
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
                                                            item.verseRange
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
                                    onAbout = { showAboutDialog = true },
                                    onStatistics = { showStatisticsDialog = true },
                                    onHelp = {
                                        Desktop.getDesktop()
                                            .browse(URI("https://github.com/ChurchPresenter/ChurchPresenter/"))
                                    },
                                    onCheckForUpdates = {
                                        coroutineScope.launch {
                                            val info = UpdateChecker.checkForUpdate()
                                            if (info != null) {
                                                pendingUpdateInfo = info
                                            } else {
                                                Desktop.getDesktop()
                                                    .browse(URI("https://github.com/ChurchPresenter/ChurchPresenter/releases/latest"))
                                            }
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
                                    onSettings = {
                                        showOptionsDialog = true
                                    },
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

                                MainDesktop(
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
                                    presenterManager = presenterManager,
                                    statisticsManager = statisticsManager,
                                    onScheduleActionsReady = { scheduleActions = it },
                                    presenting = { mode ->
                                        presenterManager.setPresentingMode(mode)
                                        if (mode != Presenting.NONE) presenterManager.setShowPresenterWindow(true)
                                    },
                                    onScheduleItemSelected = { itemId -> selectedScheduleItemId = itemId },
                                    onShowSettings = { showOptionsDialog = true },
                                    onSettingsChange = { updateFn ->
                                        appSettings = updateFn(appSettings)
                                        settingsManager.saveSettings(appSettings)
                                    },
                                    theme = theme,
                                    onSongsLoaded = { songs -> companionServer.updateSongs(songs) },
                                    onBibleLoaded = { bible, translation ->
                                        companionServer.updateBible(
                                            bible,
                                            translation
                                        )
                                    },
                                    onScheduleChanged = { items -> companionServer.updateSchedule(items) },
                                    onPresentationSlidesLoaded = { id, filePath, fileName, fileType, slides ->
                                        companionServer.updatePresentation(id, filePath, fileName, fileType, slides)
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
                                )
                                OptionsDialog(
                                    isVisible = showOptionsDialog,
                                    theme = theme,
                                    settingsManager = settingsManager,
                                    companionServer = companionServer,
                                    remoteClientManager = remoteClientManager,
                                    presenterManager = presenterManager,
                                    onDismiss = { showOptionsDialog = false },
                                    onSave = { updated ->
                                        appSettings = updated
                                        settingsManager.saveSettings(updated)
                                        // Re-preload in case bible/song directories changed
                                        companionServer.preloadData(
                                            songStorageDir = updated.songSettings.storageDirectory,
                                            bibleStorageDir = updated.bibleSettings.storageDirectory,
                                            primaryBibleFileName = updated.bibleSettings.primaryBible
                                        )
                                        companionServer.updateLowerThirdFolder(updated.streamingSettings.lowerThirdFolder)
                                        // Keep API key enforcement in sync with saved settings
                                        companionServer.updateApiKey(
                                            enabled = updated.serverSettings.apiKeyEnabled,
                                            key = updated.serverSettings.apiKey
                                        )
                                        companionServer.updateFileUploadEnabled(updated.serverSettings.fileUploadEnabled)
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
                                    }
                                )
                                KeyboardShortcutsDialog(
                                    isVisible = showKeyboardShortcutsDialog,
                                    onDismiss = { showKeyboardShortcutsDialog = false }
                                )
                                StatisticsDialog(
                                    isVisible = showStatisticsDialog,
                                    theme = theme,
                                    statisticsManager = statisticsManager,
                                    onDismiss = { showStatisticsDialog = false }
                                )
                                AboutDialog(
                                    isVisible = showAboutDialog,
                                    onDismiss = { showAboutDialog = false },
                                    theme = theme
                                )
                                UpdateAvailableDialog(
                                    updateInfo = pendingUpdateInfo,
                                    onDismiss = { pendingUpdateInfo = null }
                                )

                                // ── Remote API event dialog ───────────────────────
                                val currentRemote = remoteEventQueue.firstOrNull()
                                val currentClientId = currentRemote?.first?.clientId ?: ""
                                RemoteEventDialog(
                                    event = currentRemote?.first,
                                    queueSize = remoteEventQueue.size,
                                    isClientKnownAllowed = remoteClientManager.isAllowed(currentClientId),
                                    isClientKnownBlocked = remoteClientManager.isBlocked(currentClientId),
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
                                RemoteActivityToastHost(
                                    notifications = remoteActivityNotifications,
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
                appSettings = appSettings,
                identifyingScreen = identifyingScreen,
                serverUrl = companionServer.serverUrl.collectAsState().value,
                qaDisplayUrl = qaDisplayUrl,
            )
        } else if (appReady) {
            LicenseDialog(
                onAccept = {
                    val updated = appSettings.copy(licenseAccepted = true)
                    settingsManager.saveSettings(updated)
                    appSettings = updated
                    licenseAccepted = true
                },
                onDecline = { exitApplication() }
            )
        }
    }
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
            statisticsManager?.recordSongDisplay(item.songNumber, item.title, item.songbook)
            presenterManager.setPresentingMode(Presenting.LYRICS)
            presenterManager.setShowPresenterWindow(true)
        }

        is ScheduleItem.BibleVerseItem -> {
            scheduleActions.addBibleVerse(
                item.bookName,
                item.chapter,
                item.verseNumber,
                item.verseText,
                item.verseRange
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
            scheduleActions.addPicture(item.folderPath, item.folderName, item.imageCount)
            presenterManager.setSelectedImagePath(item.folderPath)
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
            presenterManager.setPresentingMode(Presenting.MEDIA)
            presenterManager.setShowPresenterWindow(true)
        }

        else -> Unit
    }
}

@Composable
private fun SplashWindow() {
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
        AppThemeWrapper(theme = ThemeMode.SYSTEM) {
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
) {
    val showPresenterWindow by presenterManager.showPresenterWindow
    val presentingMode by presenterManager.presentingMode
    val selectedVerses by presenterManager.selectedVerses
    val displayedVerses by presenterManager.displayedVerses
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
    val nextImagePath by presenterManager.nextImagePath
    val pictureTransitionAlpha by presenterManager.pictureTransitionAlpha
    val selectedSlide by presenterManager.selectedSlide
    val displayedSlide by presenterManager.displayedSlide
    val nextSlide by presenterManager.nextSlide
    val slideTransitionAlpha by presenterManager.slideTransitionAlpha
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
    val mediaTransitionAlpha by presenterManager.mediaTransitionAlpha
    val websiteUrl by presenterManager.websiteUrl
    val activeScene by presenterManager.activeScene
    val displayedQuestion by presenterManager.displayedQuestion
    val qaTransitionAlpha by presenterManager.qaTransitionAlpha
    val showQRCodeOnDisplay by presenterManager.showQRCodeOnDisplay
    val timerRemainingSeconds by presenterManager.timerRemainingSeconds
    val timerRunning by presenterManager.timerRunning
    val presenterNotes by presenterManager.presenterNotes

    val proj = appSettings.projectionSettings

    // Mode-level crossfade (between Bible ↔ Song etc.) — only between content modes, not to/from NONE
    var previousPresentingMode by remember { mutableStateOf(presentingMode) }
    val modeCrossfadeActive = (appSettings.bibleSettings.crossfade || appSettings.songSettings.crossfade)
            && presentingMode != Presenting.NONE && previousPresentingMode != Presenting.NONE
    if (presentingMode != previousPresentingMode) previousPresentingMode = presentingMode
    val modeCrossfadeDuration = maxOf(
        if (appSettings.bibleSettings.crossfade) appSettings.bibleSettings.transitionDuration.toInt() else 0,
        if (appSettings.songSettings.crossfade) appSettings.songSettings.transitionDuration.toInt() else 0
    ).coerceAtLeast(100)

    // Fade-out before clearing display
    val clearRequested by presenterManager.clearDisplayRequested
    LaunchedEffect(clearRequested) {
        if (!clearRequested) return@LaunchedEffect
        val mode = presenterManager.presentingMode.value
        val shouldFade = when (mode) {
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
        if (presenterManager.displayedImagePath.value == null ||
            animationType == AnimationType.NONE
        ) {
            presenterManager.setDisplayedImagePath(selectedImagePath)
            presenterManager.setPictureTransitionAlpha(1f)
        } else {
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
    }

    // Centralized Slide transition
    LaunchedEffect(selectedSlide) {
        if (presenterManager.displayedSlide.value == null ||
            animationType == AnimationType.NONE
        ) {
            presenterManager.setDisplayedSlide(selectedSlide)
            presenterManager.setSlideTransitionAlpha(1f)
        } else {
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
        val comp = lottieComposition ?: return@LaunchedEffect
        val totalDurMs = ((comp.durationFrames / comp.frameRate) * 1000f).toLong().coerceAtLeast(1L)
        val hasPause = lottiePauseAtFrame && lottiePauseFrame in 0f..1f

        presenterManager.setLottieProgress(0f)

        if (hasPause) {
            val toPauseDur = (totalDurMs * lottiePauseFrame).toInt().coerceAtLeast(1)
            val anim = Animatable(0f)
            anim.animateTo(
                targetValue = lottiePauseFrame,
                animationSpec = tween(
                    durationMillis = toPauseDur,
                    easing = LinearEasing
                )
            ) { presenterManager.setLottieProgress(value) }
            if (lottiePauseDurationMs > 0) {
                delay(lottiePauseDurationMs)
                val remainDur = (totalDurMs * (1f - lottiePauseFrame)).toInt().coerceAtLeast(1)
                anim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = remainDur,
                        easing = LinearEasing
                    )
                ) { presenterManager.setLottieProgress(value) }
            }
        } else {
            val anim = Animatable(0f)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = totalDurMs.toInt(),
                    easing = LinearEasing
                )
            ) { presenterManager.setLottieProgress(value) }
        }
    }

    // Identify the OS primary monitor and build list of non-primary screens
    val defaultDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
    val availableScreens = screens.indices.filter { screens[it] != defaultDevice }

    // Create windows for non-primary screens + DeckLink device slots
    val deckLinkDeviceCount = if (DeckLinkManager.isAvailable()) DeckLinkManager.listDevices().size else 0
    val windowCount = availableScreens.size + deckLinkDeviceCount

    for (i in 0 until windowCount) {
        val screenAssignment = proj.getAssignment(i)

        // DeckLink outputs: render via offscreen Window + pixel capture
        if (screenAssignment.targetType == "decklink") {
            if (showPresenterWindow && screenAssignment.targetDisplay >= 0) {
                val deckLinkRole = screenAssignment.primaryOutputRole
                DeckLinkComposeOutput(
                    deviceIndex = screenAssignment.targetDisplay,
                    outputRole = deckLinkRole,
                    appSettings = appSettings,
                    mediaViewModel = mediaViewModel,
                    isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD,
                ) {
                    Crossfade(
                        targetState = presentingMode,
                        animationSpec = if (modeCrossfadeActive) tween(modeCrossfadeDuration) else snap()
                    ) { mode ->
                    when (mode) {
                        Presenting.BIBLE ->
                            if (screenAssignment.showBible) {
                                BiblePresenter(
                                    selectedVerses = displayedVerses,
                                    appSettings = appSettings,
                                    isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD,
                                    outputRole = deckLinkRole,
                                    transitionAlpha = bibleTransitionAlpha,
                                    crossfadeEnabled = appSettings.bibleSettings.crossfade
                                )
                            }

                        Presenting.LYRICS ->
                            if (screenAssignment.showSongs) {
                                SongPresenter(
                                    lyricSection = displayedLyricSection,
                                    appSettings = appSettings,
                                    isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD,
                                    outputRole = deckLinkRole,
                                    transitionAlpha = songTransitionAlpha,
                                    displayLineIndex = songDisplayLineIndex,
                                    lookAheadEnabled = screenAssignment.songLookAhead,
                                    allLyricSections = allLyricSections,
                                    displaySectionIndex = songDisplaySectionIndex,
                                    crossfadeEnabled = appSettings.songSettings.crossfade
                                )
                            }

                        Presenting.PICTURES ->
                            if (screenAssignment.showPictures)
                                PicturePresenter(
                                    imagePath = displayedImagePath,
                                    transitionAlpha = pictureTransitionAlpha
                                )

                        Presenting.PRESENTATION ->
                            if (screenAssignment.showPictures)
                                SlidePresenter(slide = displayedSlide, transitionAlpha = slideTransitionAlpha)

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
                                    jsonContent = lottieJsonContent,
                                    progress = lottieProgress,
                                    appSettings = appSettings
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
                    isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD,
                ) {
                    Crossfade(targetState = presentingMode, animationSpec = if (modeCrossfadeActive) tween(modeCrossfadeDuration) else snap()) { mode ->
                    when (mode) {
                        Presenting.BIBLE ->
                            if (screenAssignment.showBible) {
                                BiblePresenter(
                                    selectedVerses = displayedVerses,
                                    appSettings = appSettings,
                                    isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD,
                                    outputRole = Constants.OUTPUT_ROLE_KEY,
                                    transitionAlpha = bibleTransitionAlpha,
                                    crossfadeEnabled = appSettings.bibleSettings.crossfade
                                )
                            }

                        Presenting.LYRICS ->
                            if (screenAssignment.showSongs) {
                                SongPresenter(
                                    lyricSection = displayedLyricSection,
                                    appSettings = appSettings,
                                    isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD,
                                    outputRole = Constants.OUTPUT_ROLE_KEY,
                                    transitionAlpha = songTransitionAlpha,
                                    displayLineIndex = songDisplayLineIndex,
                                    lookAheadEnabled = screenAssignment.songLookAhead,
                                    allLyricSections = allLyricSections,
                                    displaySectionIndex = songDisplaySectionIndex,
                                    crossfadeEnabled = appSettings.songSettings.crossfade
                                )
                            }

                        Presenting.PICTURES ->
                            if (screenAssignment.showPictures)
                                PicturePresenter(
                                    imagePath = displayedImagePath,
                                    transitionAlpha = pictureTransitionAlpha,
                                    outputRole = Constants.OUTPUT_ROLE_KEY
                                )

                        Presenting.PRESENTATION ->
                            if (screenAssignment.showPictures)
                                SlidePresenter(slide = displayedSlide, transitionAlpha = slideTransitionAlpha, outputRole = Constants.OUTPUT_ROLE_KEY)

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
                                    jsonContent = lottieJsonContent,
                                    progress = lottieProgress,
                                    appSettings = appSettings,
                                    outputRole = Constants.OUTPUT_ROLE_KEY
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
                                    Crossfade(targetState = presentingMode, animationSpec = if (modeCrossfadeActive) tween(modeCrossfadeDuration) else snap()) { mode ->
                    when (mode) {
                                        Presenting.BIBLE ->
                                            if (screenAssignment.showBible) {
                                                BiblePresenter(
                                                    selectedVerses = displayedVerses,
                                                    appSettings = appSettings,
                                                    isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD,
                                                    outputRole = Constants.OUTPUT_ROLE_KEY,
                                                    transitionAlpha = bibleTransitionAlpha,
                                                    crossfadeEnabled = appSettings.bibleSettings.crossfade
                                                )
                                            }

                                        Presenting.LYRICS ->
                                            if (screenAssignment.showSongs) {
                                                SongPresenter(
                                                    lyricSection = displayedLyricSection,
                                                    appSettings = appSettings,
                                                    isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD,
                                                    outputRole = Constants.OUTPUT_ROLE_KEY,
                                                    transitionAlpha = songTransitionAlpha,
                                                    displayLineIndex = songDisplayLineIndex,
                                                    lookAheadEnabled = screenAssignment.songLookAhead,
                                                    allLyricSections = allLyricSections,
                                                    displaySectionIndex = songDisplaySectionIndex,
                                                    crossfadeEnabled = appSettings.songSettings.crossfade
                                                )
                                            }

                                        Presenting.PICTURES ->
                                            if (screenAssignment.showPictures)
                                                PicturePresenter(
                                                    imagePath = displayedImagePath,
                                                    transitionAlpha = pictureTransitionAlpha,
                                                    outputRole = Constants.OUTPUT_ROLE_KEY
                                                )

                                        Presenting.PRESENTATION ->
                                            if (screenAssignment.showPictures)
                                                SlidePresenter(slide = displayedSlide, transitionAlpha = slideTransitionAlpha, outputRole = Constants.OUTPUT_ROLE_KEY)

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
                                                    jsonContent = lottieJsonContent,
                                                    progress = lottieProgress,
                                                    appSettings = appSettings,
                                                    outputRole = Constants.OUTPUT_ROLE_KEY
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

        // Skip "None" — user disabled this output
        if (screenAssignment.targetDisplay == Constants.KEY_TARGET_NONE) continue

        // Resolve target display: try bounds first (reliable), fall back to index, then auto-assign
        val targetScreenIndex = findScreenIndexByBounds(
            screens,
            screenAssignment.targetBoundsX,
            screenAssignment.targetBoundsY,
            screenAssignment.targetBoundsW,
            screenAssignment.targetBoundsH
        ) ?: if (screenAssignment.targetDisplay >= 0 && screenAssignment.targetDisplay < screens.size) {
            screenAssignment.targetDisplay
        } else {
            // Auto-assign to next available non-primary screen
            availableScreens.getOrNull(i) ?: continue
        }

        // Skip if the target screen doesn't exist
        if (targetScreenIndex < 0 || targetScreenIndex >= screens.size) continue

        // Per-output background toggle
        val showBg = if (screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD) screenAssignment.showLowerThirdBackground else screenAssignment.showFullscreenBackground

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

        // Reposition window when target display changes
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
            CompositionLocalProvider(LocalMediaViewModel provides mediaViewModel) {
                if (screenAssignment.displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR) {
                    // Stage monitor: dedicated presenter-confidence layout
                    StageMonitorScreen(
                        sm = appSettings.stageMonitorSettings,
                        presentingMode = presentingMode,
                        currentLyricSection = displayedLyricSection,
                        allLyricSections = allLyricSections,
                        songDisplaySectionIndex = songDisplaySectionIndex,
                        displayedVerses = displayedVerses,
                        timerRemainingSeconds = timerRemainingSeconds,
                        timerRunning = timerRunning,
                        displayedImagePath = displayedImagePath,
                        nextImagePath = nextImagePath,
                        displayedSlide = displayedSlide,
                        nextSlide = nextSlide,
                        announcementText = displayedAnnouncementText,
                        presenterNotes = presenterNotes,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                PresenterScreen(
                    modifier = Modifier.fillMaxSize(),
                    appSettings = appSettings,
                    outputRole = primaryRole,
                    isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD,
                    showBackground = if (screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD) screenAssignment.showLowerThirdBackground else screenAssignment.showFullscreenBackground
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
                        Crossfade(targetState = presentingMode, animationSpec = if (modeCrossfadeActive) tween(modeCrossfadeDuration) else snap()) { mode ->
                    when (mode) {
                            Presenting.BIBLE ->
                                if (screenAssignment.showBible) {
                                    BiblePresenter(
                                        selectedVerses = displayedVerses,
                                        appSettings = appSettings,
                                        isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD,
                                        outputRole = primaryRole,
                                        transitionAlpha = bibleTransitionAlpha,
                                        showBackground = showBg,
                                        crossfadeEnabled = appSettings.bibleSettings.crossfade
                                    )
                                }

                            Presenting.LYRICS ->
                                if (screenAssignment.showSongs) {
                                    SongPresenter(
                                        lyricSection = displayedLyricSection,
                                        appSettings = appSettings,
                                        isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD,
                                        outputRole = primaryRole,
                                        transitionAlpha = songTransitionAlpha,
                                        displayLineIndex = songDisplayLineIndex,
                                        lookAheadEnabled = screenAssignment.songLookAhead,
                                        allLyricSections = allLyricSections,
                                        displaySectionIndex = songDisplaySectionIndex,
                                        showBackground = showBg,
                                        crossfadeEnabled = appSettings.songSettings.crossfade
                                    )
                                }

                            Presenting.PICTURES ->
                                if (screenAssignment.showPictures)
                                    PicturePresenter(
                                        imagePath = displayedImagePath,
                                        transitionAlpha = pictureTransitionAlpha
                                    )

                            Presenting.PRESENTATION ->
                                if (screenAssignment.showPictures)
                                    SlidePresenter(slide = displayedSlide, transitionAlpha = slideTransitionAlpha)

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
                                        jsonContent = lottieJsonContent,
                                        progress = lottieProgress,
                                        appSettings = appSettings
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

                        if (identifyingScreen) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.75f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(Res.string.screen_number, i + 1),
                                    color = Color.White,
                                    fontSize = 96.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    }
                } // end else (non-stage-monitor)
            }
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
                                Crossfade(targetState = presentingMode, animationSpec = if (modeCrossfadeActive) tween(modeCrossfadeDuration) else snap()) { mode ->
                    when (mode) {
                                    Presenting.BIBLE ->
                                        if (screenAssignment.showBible) {
                                            BiblePresenter(
                                                selectedVerses = displayedVerses,
                                                appSettings = appSettings,
                                                isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD,
                                                outputRole = Constants.OUTPUT_ROLE_KEY,
                                                transitionAlpha = bibleTransitionAlpha,
                                                crossfadeEnabled = appSettings.bibleSettings.crossfade
                                            )
                                        }

                                    Presenting.LYRICS ->
                                        if (screenAssignment.showSongs) {
                                            SongPresenter(
                                                lyricSection = displayedLyricSection,
                                                appSettings = appSettings,
                                                isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD,
                                                outputRole = Constants.OUTPUT_ROLE_KEY,
                                                transitionAlpha = songTransitionAlpha,
                                                displayLineIndex = songDisplayLineIndex,
                                                lookAheadEnabled = screenAssignment.songLookAhead,
                                                allLyricSections = allLyricSections,
                                                displaySectionIndex = songDisplaySectionIndex,
                                                crossfadeEnabled = appSettings.songSettings.crossfade
                                            )
                                        }

                                    Presenting.PICTURES ->
                                        if (screenAssignment.showPictures)
                                            PicturePresenter(
                                                imagePath = displayedImagePath,
                                                transitionAlpha = pictureTransitionAlpha,
                                                outputRole = Constants.OUTPUT_ROLE_KEY
                                            )

                                    Presenting.PRESENTATION ->
                                        if (screenAssignment.showPictures)
                                            SlidePresenter(
                                                slide = displayedSlide,
                                                transitionAlpha = slideTransitionAlpha,
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
                                                jsonContent = lottieJsonContent,
                                                progress = lottieProgress,
                                                appSettings = appSettings,
                                                outputRole = Constants.OUTPUT_ROLE_KEY
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
                    isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD,
                ) {
                    Crossfade(targetState = presentingMode, animationSpec = if (modeCrossfadeActive) tween(modeCrossfadeDuration) else snap()) { mode ->
                    when (mode) {
                        Presenting.BIBLE ->
                            if (screenAssignment.showBible) {
                                BiblePresenter(
                                    selectedVerses = displayedVerses,
                                    appSettings = appSettings,
                                    isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD,
                                    outputRole = Constants.OUTPUT_ROLE_KEY,
                                    transitionAlpha = bibleTransitionAlpha,
                                    crossfadeEnabled = appSettings.bibleSettings.crossfade
                                )
                            }

                        Presenting.LYRICS ->
                            if (screenAssignment.showSongs) {
                                SongPresenter(
                                    lyricSection = displayedLyricSection,
                                    appSettings = appSettings,
                                    isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD,
                                    outputRole = Constants.OUTPUT_ROLE_KEY,
                                    transitionAlpha = songTransitionAlpha,
                                    displayLineIndex = songDisplayLineIndex,
                                    lookAheadEnabled = screenAssignment.songLookAhead,
                                    allLyricSections = allLyricSections,
                                    displaySectionIndex = songDisplaySectionIndex,
                                    crossfadeEnabled = appSettings.songSettings.crossfade
                                )
                            }

                        Presenting.PICTURES ->
                            if (screenAssignment.showPictures)
                                PicturePresenter(
                                    imagePath = displayedImagePath,
                                    transitionAlpha = pictureTransitionAlpha,
                                    outputRole = Constants.OUTPUT_ROLE_KEY
                                )

                        Presenting.PRESENTATION ->
                            if (screenAssignment.showPictures)
                                SlidePresenter(slide = displayedSlide, transitionAlpha = slideTransitionAlpha, outputRole = Constants.OUTPUT_ROLE_KEY)

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
                                    jsonContent = lottieJsonContent,
                                    progress = lottieProgress,
                                    appSettings = appSettings,
                                    outputRole = Constants.OUTPUT_ROLE_KEY
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

                        Presenting.NONE -> { /* nothing */ }
                    }
                    }
                }
            }
        }
    }
}
