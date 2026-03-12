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
import org.churchpresenter.app.churchpresenter.data.SettingsManager
import org.churchpresenter.app.churchpresenter.dialogs.AboutDialog
import org.churchpresenter.app.churchpresenter.dialogs.KeyboardShortcutsDialog
import org.churchpresenter.app.churchpresenter.dialogs.LicenseDialog
import org.churchpresenter.app.churchpresenter.dialogs.OptionsDialog
import org.churchpresenter.app.churchpresenter.presenter.AnnouncementsPresenter
import org.churchpresenter.app.churchpresenter.presenter.CefManager
import org.churchpresenter.app.churchpresenter.presenter.WebsitePresenter
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.app.churchpresenter.presenter.LowerThirdPresenter
import org.churchpresenter.app.churchpresenter.presenter.MediaPresenter
import org.churchpresenter.app.churchpresenter.presenter.PicturePresenter
import org.churchpresenter.app.churchpresenter.presenter.SlidePresenter
import org.churchpresenter.app.churchpresenter.presenter.Presenting
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
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import org.jetbrains.compose.resources.stringResource
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.util.Locale


fun main() {
    // Install crash reporting before anything else
    CrashReporter.initialize()

    // Pre-warm JavaFX on a background thread before UI starts
    preWarmJavaFX()

    // Initialize JCEF (Chromium) for embedded web browsing
    CefManager.init()

    application {
        var appReady by remember { mutableStateOf(false) }

        // Business logic layer
        val settingsManager = remember { SettingsManager() }
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

        // Set custom VLC path from saved settings before first VLC access
        remember { vlcCustomPath = appSettings.projectionSettings.vlcPath }
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
        val coroutineScope = rememberCoroutineScope()

        var theme by remember {
            val savedTheme = when (appSettings.theme.uppercase()) {
                "LIGHT" -> ThemeMode.LIGHT
                "DARK" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            mutableStateOf(savedTheme)
        }
        val companionServer = remember { CompanionServer() }
        var showOptionsDialog by remember { mutableStateOf(false) }
        var showKeyboardShortcutsDialog by remember { mutableStateOf(false) }
        var showAboutDialog by remember { mutableStateOf(false) }
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
                // Auto-start server if user previously enabled it
                if (appSettings.serverSettings.enabled) {
                    companionServer.start(appSettings.serverSettings.port)
                }
            }
            appReady = true
        }

        // ── Remote add-to-schedule requests (REST POST /api/schedule/add or WS add_to_schedule) ──
        LaunchedEffect(Unit) {
            companionServer.onAddToSchedule.collect { req ->
                when (val item = req.item) {
                    is ScheduleItem.SongItem ->
                        currentScheduleActions.addSong(item.songNumber, item.title, item.songbook)
                    is ScheduleItem.BibleVerseItem ->
                        currentScheduleActions.addBibleVerse(item.bookName, item.chapter, item.verseNumber, item.verseText)
                    is ScheduleItem.PresentationItem ->
                        currentScheduleActions.addPresentation(item.filePath, item.fileName, item.slideCount, item.fileType)
                    is ScheduleItem.PictureItem ->
                        currentScheduleActions.addPicture(item.folderPath, item.folderName, item.imageCount)
                    is ScheduleItem.MediaItem ->
                        currentScheduleActions.addMedia(item.mediaUrl, item.mediaTitle, item.mediaType)
                    else -> Unit
                }
            }
        }

        // ── Remote project requests (REST POST /api/project or WS project) ──────────────────────
        LaunchedEffect(Unit) {
            companionServer.onProject.collect { req ->
                when (val item = req.item) {
                    is ScheduleItem.SongItem -> {
                        currentScheduleActions.addSong(item.songNumber, item.title, item.songbook)
                        presenterManager.setPresentingMode(Presenting.LYRICS)
                        presenterManager.setShowPresenterWindow(true)
                    }
                    is ScheduleItem.BibleVerseItem -> {
                        presenterManager.setSelectedVerses(listOf(
                            org.churchpresenter.app.churchpresenter.models.SelectedVerse(
                                bookName = item.bookName,
                                chapter = item.chapter,
                                verseNumber = item.verseNumber,
                                verseText = item.verseText
                            )
                        ))
                        presenterManager.setPresentingMode(Presenting.BIBLE)
                        presenterManager.setShowPresenterWindow(true)
                    }
                    is ScheduleItem.PictureItem -> {
                        presenterManager.setSelectedImagePath(item.folderPath)
                        presenterManager.setPresentingMode(Presenting.PICTURES)
                        presenterManager.setShowPresenterWindow(true)
                    }
                    is ScheduleItem.PresentationItem -> {
                        presenterManager.setPresentingMode(Presenting.PRESENTATION)
                        presenterManager.setShowPresenterWindow(true)
                    }
                    is ScheduleItem.MediaItem -> {
                        presenterManager.setPresentingMode(Presenting.MEDIA)
                        presenterManager.setShowPresenterWindow(true)
                    }
                    else -> Unit
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
                    exitApplication()
                },
                title = stringResource(Res.string.app_name),
                icon = painterResource(Res.drawable.ic_app_icon),
                state = state
            ) {
                LanguageProvider(language = currentLanguage) {
                    AppThemeWrapper(theme = theme) {
                        CompositionLocalProvider(LocalMediaViewModel provides mediaViewModel) {
                            NavigationTopBar(
                                onAbout = { showAboutDialog = true },
                                onHelp = { java.awt.Desktop.getDesktop().browse(java.net.URI("https://github.com/ChurchPresenter/ChurchPresenter/")) },
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
                                onSettings = { showOptionsDialog = true },
                                onExit = { exitApplication() },
                                onAddToSchedule = {},
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
                            MainDesktop(
                                onVerseSelected = { verses -> presenterManager.setSelectedVerses(verses) },
                                onSongItemSelected = { presenterManager.setLyricSection(it) },
                                appSettings = appSettings,
                                presenterManager = presenterManager,
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
                                onBibleLoaded = { bible, translation -> companionServer.updateBible(bible, translation) },
                                onScheduleChanged = { items -> companionServer.updateSchedule(items) },
                                onPresentationSlidesLoaded = { id, fileName, fileType, slides ->
                                    companionServer.updatePresentation(id, fileName, fileType, slides)
                                },
                                onPicturesLoaded = { folderId, folderName, folderPath, imageFiles ->
                                    companionServer.updatePictures(folderId, folderName, folderPath, imageFiles)
                                },
                                selectPictureImageFlow = kotlinx.coroutines.flow.flow {
                                    companionServer.onSelectPicture.collect { req ->
                                        emit(req.folderId to req.index)
                                    }
                                },
                                serverUrl = companionServer.serverUrl.collectAsState().value
                            )
                            OptionsDialog(
                                isVisible = showOptionsDialog,
                                theme = theme,
                                settingsManager = settingsManager,
                                companionServer = companionServer,
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
                            AboutDialog(
                                isVisible = showAboutDialog,
                                onDismiss = { showAboutDialog = false }
                            )
                        }
                    }
                }
            }

            PresenterWindows(
                screens = screens,
                presenterManager = presenterManager,
                mediaViewModel = mediaViewModel,
                appSettings = appSettings,
                identifyingScreen = identifyingScreen,
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
    val selectedImagePath by presenterManager.selectedImagePath
    val displayedImagePath by presenterManager.displayedImagePath
    val pictureTransitionAlpha by presenterManager.pictureTransitionAlpha
    val selectedSlide by presenterManager.selectedSlide
    val displayedSlide by presenterManager.displayedSlide
    val slideTransitionAlpha by presenterManager.slideTransitionAlpha
    val animationType by presenterManager.animationType
    val transitionDuration by presenterManager.transitionDuration
    val announcementText by presenterManager.announcementText
    val displayedAnnouncementText by presenterManager.displayedAnnouncementText
    val announcementTransitionAlpha by presenterManager.announcementTransitionAlpha
    val lottieJsonContent by presenterManager.lottieJsonContent
    val lottiePauseAtFrame by presenterManager.lottiePauseAtFrame
    val lottiePauseFrame by presenterManager.lottiePauseFrame
    val lottiePauseDurationMs by presenterManager.lottiePauseDurationMs
    val lottieTrigger by presenterManager.lottieTrigger
    val lottieProgress by presenterManager.lottieProgress
    val mediaTransitionAlpha by presenterManager.mediaTransitionAlpha
    val websiteUrl by presenterManager.websiteUrl

    val proj = appSettings.projectionSettings

    // Centralized Bible transition: one animation drives all windows so they stay in sync
    // When hold is active, skip updating displayedVerses so the user can browse freely
    val bibleHold by presenterManager.bibleHold
    LaunchedEffect(selectedVerses, bibleHold) {
        if (bibleHold) return@LaunchedEffect
        val bs = appSettings.bibleSettings
        if (presenterManager.displayedVerses.value.isEmpty() || (!bs.fadeIn && !bs.fadeOut)) {
            presenterManager.setDisplayedVerses(selectedVerses)
            presenterManager.setBibleTransitionAlpha(1f)
        } else {
            val duration = bs.transitionDuration.toInt()
            val anim = androidx.compose.animation.core.Animatable(1f)
            // Fade out (or instant)
            if (bs.fadeOut) {
                anim.animateTo(0f, androidx.compose.animation.core.tween(duration / 2)) {
                    presenterManager.setBibleTransitionAlpha(value)
                }
            } else {
                presenterManager.setBibleTransitionAlpha(0f)
            }
            // Swap content at alpha=0
            presenterManager.setDisplayedVerses(selectedVerses)
            // Fade in (or instant)
            if (bs.fadeIn) {
                anim.snapTo(0f)
                anim.animateTo(1f, androidx.compose.animation.core.tween(duration / 2)) {
                    presenterManager.setBibleTransitionAlpha(value)
                }
            } else {
                presenterManager.setBibleTransitionAlpha(1f)
            }
        }
    }

    // Centralized Song transition
    LaunchedEffect(lyricSection, lyricSectionVersion) {
        val ss = appSettings.songSettings
        if (presenterManager.displayedLyricSection.value.lines.isEmpty() || (!ss.fadeIn && !ss.fadeOut)) {
            presenterManager.setDisplayedLyricSection(lyricSection)
            presenterManager.setSongTransitionAlpha(1f)
        } else {
            val duration = ss.transitionDuration.toInt()
            val anim = androidx.compose.animation.core.Animatable(1f)
            if (ss.fadeOut) {
                anim.animateTo(0f, androidx.compose.animation.core.tween(duration / 2)) {
                    presenterManager.setSongTransitionAlpha(value)
                }
            } else {
                presenterManager.setSongTransitionAlpha(0f)
            }
            presenterManager.setDisplayedLyricSection(lyricSection)
            if (ss.fadeIn) {
                anim.snapTo(0f)
                anim.animateTo(1f, androidx.compose.animation.core.tween(duration / 2)) {
                    presenterManager.setSongTransitionAlpha(value)
                }
            } else {
                presenterManager.setSongTransitionAlpha(1f)
            }
        }
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
            val anim = androidx.compose.animation.core.Animatable(1f)
            anim.animateTo(0f, androidx.compose.animation.core.tween(halfDuration)) {
                presenterManager.setPictureTransitionAlpha(value)
            }
            presenterManager.setDisplayedImagePath(selectedImagePath)
            anim.animateTo(1f, androidx.compose.animation.core.tween(halfDuration)) {
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
            val anim = androidx.compose.animation.core.Animatable(1f)
            anim.animateTo(0f, androidx.compose.animation.core.tween(halfDuration)) {
                presenterManager.setSlideTransitionAlpha(value)
            }
            presenterManager.setDisplayedSlide(selectedSlide)
            anim.animateTo(1f, androidx.compose.animation.core.tween(halfDuration)) {
                presenterManager.setSlideTransitionAlpha(value)
            }
        }
    }

    // Centralized Announcements transition
    LaunchedEffect(announcementText) {
        if (presenterManager.displayedAnnouncementText.value.isEmpty() ||
            appSettings.announcementsSettings.animationType == Constants.ANIMATION_NONE
        ) {
            presenterManager.setDisplayedAnnouncementText(announcementText)
            presenterManager.setAnnouncementTransitionAlpha(1f)
        } else {
            val duration = appSettings.announcementsSettings.animationDuration.coerceAtLeast(50)
            val halfDuration = duration / 2
            val anim = androidx.compose.animation.core.Animatable(1f)
            anim.animateTo(0f, androidx.compose.animation.core.tween(halfDuration)) {
                presenterManager.setAnnouncementTransitionAlpha(value)
            }
            presenterManager.setDisplayedAnnouncementText(announcementText)
            anim.animateTo(1f, androidx.compose.animation.core.tween(halfDuration)) {
                presenterManager.setAnnouncementTransitionAlpha(value)
            }
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
            val anim = androidx.compose.animation.core.Animatable(0f)
            anim.animateTo(
                targetValue = lottiePauseFrame,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = toPauseDur, easing = androidx.compose.animation.core.LinearEasing)
            ) { presenterManager.setLottieProgress(value) }
            if (lottiePauseDurationMs > 0) {
                delay(lottiePauseDurationMs)
                val remainDur = (totalDurMs * (1f - lottiePauseFrame)).toInt().coerceAtLeast(1)
                anim.animateTo(
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = remainDur, easing = androidx.compose.animation.core.LinearEasing)
                ) { presenterManager.setLottieProgress(value) }
            }
        } else {
            val anim = androidx.compose.animation.core.Animatable(0f)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = totalDurMs.toInt(), easing = androidx.compose.animation.core.LinearEasing)
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

        // DeckLink outputs are handled separately — no Compose window needed
        if (screenAssignment.targetType == "decklink") {
            // DeckLink rendering is managed by DeckLinkPresenter (launched externally)
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
                PresenterScreen(modifier = Modifier.fillMaxSize(), appSettings = appSettings, outputRole = primaryRole, isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                                    mediaViewModel.pause()
                                    presenterManager.setPresentingMode(Presenting.NONE)
                                    true
                                } else false
                            }
                    ) {
                        when (presentingMode) {
                            Presenting.BIBLE ->
                                if (screenAssignment.showBible) {
                                    BiblePresenter(
                                        selectedVerses = displayedVerses,
                                        appSettings = appSettings,
                                        isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD,
                                        outputRole = primaryRole,
                                        transitionAlpha = bibleTransitionAlpha
                                    )
                                }
                            Presenting.LYRICS ->
                                if (screenAssignment.showSongs) {
                                    SongPresenter(
                                        lyricSection = displayedLyricSection,
                                        appSettings = appSettings,
                                        isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD,
                                        outputRole = primaryRole,
                                        transitionAlpha = songTransitionAlpha
                                    )
                                }
                            Presenting.PICTURES ->
                                if (screenAssignment.showPictures)
                                    PicturePresenter(imagePath = displayedImagePath, transitionAlpha = pictureTransitionAlpha)
                            Presenting.PRESENTATION ->
                                if (screenAssignment.showPictures)
                                    SlidePresenter(slide = displayedSlide, transitionAlpha = slideTransitionAlpha)
                            Presenting.MEDIA ->
                                if (screenAssignment.showMedia) {
                                    if (mediaViewModel.isAudioFile) {
                                        // Audio: playback handled by hidden VideoPlayer in MainDesktop
                                        // Projection shows background only
                                    } else {
                                        MediaPresenter(modifier = Modifier.fillMaxSize(), audioDeviceId = appSettings.projectionSettings.audioOutputDeviceId, transitionAlpha = mediaTransitionAlpha)
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
                                        transitionAlpha = announcementTransitionAlpha
                                    )
                            Presenting.WEBSITE ->
                                WebsitePresenter(
                                    url = websiteUrl,
                                    modifier = Modifier.fillMaxSize(),
                                    onSnapshot = { bitmap -> presenterManager.setWebSnapshot(bitmap) },
                                    onBrowserCreated = { browser -> presenterManager.setLiveBrowser(browser) },
                                    onUrlChanged = { newUrl -> presenterManager.setWebsiteUrl(newUrl) },
                                    onTitleChanged = { title -> presenterManager.setWebPageTitle(title) },
                                    audioDeviceId = appSettings.projectionSettings.audioOutputDeviceId
                                )
                            Presenting.NONE -> { /* nothing */ }
                        }

                        // Clear live browser ref when leaving WEBSITE mode
                        androidx.compose.runtime.LaunchedEffect(presentingMode) {
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
                        PresenterScreen(modifier = Modifier.fillMaxSize(), appSettings = appSettings, outputRole = Constants.OUTPUT_ROLE_KEY) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                                            mediaViewModel.pause()
                                            presenterManager.setPresentingMode(Presenting.NONE)
                                            true
                                        } else false
                                    }
                            ) {
                                when (presentingMode) {
                                    Presenting.BIBLE ->
                                        if (screenAssignment.showBible) {
                                            BiblePresenter(
                                                selectedVerses = displayedVerses,
                                                appSettings = appSettings,
                                                isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD,
                                                outputRole = Constants.OUTPUT_ROLE_KEY,
                                                transitionAlpha = bibleTransitionAlpha
                                            )
                                        }
                                    Presenting.LYRICS ->
                                        if (screenAssignment.showSongs) {
                                            SongPresenter(
                                                lyricSection = displayedLyricSection,
                                                appSettings = appSettings,
                                                isLowerThird = screenAssignment.displayMode == Constants.DISPLAY_MODE_LOWER_THIRD,
                                                outputRole = Constants.OUTPUT_ROLE_KEY,
                                                transitionAlpha = songTransitionAlpha
                                            )
                                        }
                                    Presenting.PICTURES ->
                                        if (screenAssignment.showPictures)
                                            PicturePresenter(imagePath = displayedImagePath, transitionAlpha = pictureTransitionAlpha)
                                    Presenting.PRESENTATION ->
                                        if (screenAssignment.showPictures)
                                            SlidePresenter(slide = displayedSlide, transitionAlpha = slideTransitionAlpha)
                                    Presenting.MEDIA ->
                                        if (screenAssignment.showMedia) {
                                            if (mediaViewModel.isAudioFile) {
                                                // Audio: background only
                                            } else {
                                                MediaPresenter(modifier = Modifier.fillMaxSize(), audioDeviceId = appSettings.projectionSettings.audioOutputDeviceId, transitionAlpha = mediaTransitionAlpha)
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
                                                outputRole = Constants.OUTPUT_ROLE_KEY,
                                                transitionAlpha = announcementTransitionAlpha
                                            )
                                    Presenting.WEBSITE ->
                                        WebsitePresenter(
                                    url = websiteUrl,
                                    modifier = Modifier.fillMaxSize(),
                                    onSnapshot = { bitmap -> presenterManager.setWebSnapshot(bitmap) },
                                    onUrlChanged = { newUrl -> presenterManager.setWebsiteUrl(newUrl) },
                                    onTitleChanged = { title -> presenterManager.setWebPageTitle(title) },
                                    audioDeviceId = appSettings.projectionSettings.audioOutputDeviceId
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
}
