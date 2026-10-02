package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import org.churchpresenter.diagnostics.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import org.churchpresenter.app.churchpresenter.utils.AppWindowIcons
import org.churchpresenter.sharedui.utils.addGuardedShutdownHook
import org.churchpresenter.app.churchpresenter.utils.deleteLeftoverUpdateInstallers
import org.churchpresenter.sharedui.utils.DevFlags
import org.churchpresenter.app.churchpresenter.utils.GpuInfo
import org.churchpresenter.app.churchpresenter.utils.LottieFonts
import org.churchpresenter.sharedui.utils.SystemFonts
import org.churchpresenter.presentationengine.fonts.SlideFontRegistry
import churchpresenter.composeapp.generated.resources.Res
import kotlinx.coroutines.runBlocking
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.withBundledBible
import org.churchpresenter.app.churchpresenter.data.asDurationRow
import org.churchpresenter.settings.SettingsManager
import org.churchpresenter.web.presenter.CefManager
import org.churchpresenter.app.churchpresenter.ui.theme.themeCustomizationFrom
import org.churchpresenter.theme.LocalThemeCustomization
import org.churchpresenter.theme.ThemeCustomization
import org.churchpresenter.sharedui.utils.FfmpegBinary
import org.churchpresenter.media.composables.vlcCustomPath
import org.churchpresenter.app.churchpresenter.server.LottieRenderCache
import org.churchpresenter.app.churchpresenter.server.CalendarSyncService
import org.churchpresenter.settings.calendarFolder
import org.churchpresenter.settings.utils.AppDataDir
import org.churchpresenter.settings.utils.Constants

import org.churchpresenter.app.churchpresenter.utils.AutoStartManager
import org.churchpresenter.diagnostics.BuildIdentity
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.app.churchpresenter.utils.LiveMapReporter
import org.churchpresenter.sharedui.utils.UsageEvents
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineExceptionHandler
import org.churchpresenter.app.churchpresenter.composables.CameraDeviceCatalog
import org.churchpresenter.app.churchpresenter.composables.ResourceCensus
import org.jetbrains.compose.resources.MissingResourceException

private const val MILLIS_PER_MINUTE = 60_000L
internal const val OPTIONS_TAB_BACKGROUND = 2
internal const val UPDATE_CHECK_DELAY_MS = 5_000L
internal const val STORY_PROMPT_DELAY_MS = 8_000L

internal const val CURRENT_EULA_VERSION = 1

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
    } catch (e: IOException) {
        // Writing the Bible or the settings.
        CrashReporter.reportException(e, "Bundling default KJV Bible")
    } catch (e: MissingResourceException) {
        // A build without the bundled module.
        CrashReporter.reportException(e, "Bundling default KJV Bible")
    }
}

fun main() {
    // Before anything else: skiko latches this on its first SkiaLayer, so a later set is ignored.
    preferredRenderApi(System.getProperty("os.name", ""), DevFlags.renderApiOverride)?.let {
        System.setProperty("skiko.renderApi", it)
    }
    if (!acquireSingleInstanceLock()) {
        Log.info("Startup", "ChurchPresenter is already running.")
        javax.swing.JOptionPane.showMessageDialog(
            null,
            "ChurchPresenter is already running.",
            "ChurchPresenter",
            javax.swing.JOptionPane.WARNING_MESSAGE
        )
        System.exit(0)
        return
    }

    // ImageIO caches its output stream in a temp file by default, so every slide JPEG the
    // presentation cache writes depended on java.io.tmpdir being writable — and where it was not,
    // ImageIO.write failed with the useless "Can't create an ImageOutputStream!" rather than
    // anything naming a temp directory. That was 75 reports across four churches. The images this
    // app writes are single slides and thumbnails, small enough to stage in memory, so the temp
    // file buys nothing and costs a dependency on a directory the app does not control.
    javax.imageio.ImageIO.setUseCache(false)

    // Before the first window: every window gets the app icon's pixel frames as it opens.
    AppWindowIcons.install()

    val startupSettings = SettingsManager().loadSettings()
    CrashReporter.initialize(
        startupSettings.analyticsReportingEnabled,
        // BuildConfig is generated into :composeApp, so :diagnostics is told what this build is
        // rather than reading it.
        BuildIdentity(
            versionDisplay = BuildConfig.VERSION_DISPLAY,
            appVersion = BuildConfig.APP_VERSION,
            isRelease = BuildConfig.IS_RELEASE,
            buildType = BuildConfig.BUILD_TYPE,
            buildChannel = BuildConfig.BUILD_CHANNEL,
        ),
    )
    CrashReporter.breadcrumb("Application started", category = "lifecycle")
    // The installer a previous update ran is still in the temp directory: it could not be deleted
    // while it was running. The single-instance guard above means no download is in flight.
    deleteLeftoverUpdateInstallers()
    // Which renderer was live is the first thing a GPU driver crash needs and the one thing the
    // report never carried. "default" means the platform's own choice, which is not the same fact
    // as any named API — a report from a machine on Direct3D-by-default and one pinned to it are
    // different evidence. Set here rather than with the availability tags below: a render fault
    // can arrive before those run.
    CrashReporter.setTag("render.api", System.getProperty("skiko.renderApi") ?: "default")
    // Which renderer ran is only half the fact: whether a GPU driver fault is an NVIDIA, AMD or
    // Intel problem is the axis such a group has to be split by, and no report carried it at all.
    // Empty off Windows and on any machine the call fails, so an absent tag means "not known"
    // rather than a guess. Beside render.api for the same reason: a render fault can arrive early.
    CrashReporter.setConfigTags(GpuInfo.crashTags())

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

    // One camera enumeration at startup, so a capture that fails before any picker has been opened
    // can still report what the machine had. A fifth of camera reports arrived carrying
    // `camera.enumerator=not_run` — nothing had looked, because only a picker enumerates and the
    // presenter restores a scene without one.
    //
    // Its own daemon thread, like the font warm-up above: this shells out to ffmpeg, and on Windows
    // to PowerShell as well, so it must not be on the path that opens the window.
    Thread {
        runCatching { runBlocking { CameraDeviceCatalog.refresh() } }
    }.apply { isDaemon = true }.start()

    val sessionStartedAt = System.currentTimeMillis()
    addGuardedShutdownHook("session") {
        UsageEvents.recordSessionMinutes(((System.currentTimeMillis() - sessionStartedAt) / MILLIS_PER_MINUTE).toInt())
        // Shutdown is when the high-water marks are final. Reports only when they are higher than a
        // scene can account for — see ResourceCensus, and why counts rather than CPU or heap.
        ResourceCensus.reportIfLeaky()
    }

    val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        CrashReporter.reportException(throwable, context = "CoroutineExceptionHandler")
    }

    CefManager.init()
    CrashReporter.setTag("jcef.available", CefManager.initialized.toString())
    if (CefManager.macOsUnsupported) CrashReporter.setTag("jcef.macos_unsupported", "true")
    if (CefManager.windowsUnsupported) CrashReporter.setTag("jcef.windows_unsupported", "true")

    io.github.vinceglb.filekit.FileKit.init(appId = "ChurchPresenter")

    Thread { AutoStartManager.syncRegistration() }.apply { isDaemon = true }.start()

    // Warm-up only, and the whole of it is optional: what it saves is the first font picker
    // enumerating the machine's fonts on the UI thread. It is wrapped because everything in it goes
    // through AWT's font stack, which raises an Error rather than an Exception when the platform's
    // native font manager will not load — reported as a fatal crash from this thread even though
    // the app itself carried on. See LiveMapReporter.detectScreenCount for the same lesson.
    Thread {
        runCatching {
            LottieFonts.bundledFontResources().forEach { resource ->
                LottieFonts::class.java.getResourceAsStream(resource)?.let {
                    SlideFontRegistry.registerFontStream(it)
                }
            }
            SlideFontRegistry.initialize()
            SystemFonts.families()
        }
    }.apply { isDaemon = true }.start()

    vlcCustomPath = startupSettings.projectionSettings.vlcPath
    FfmpegBinary.customPath = startupSettings.projectionSettings.ffmpegPath

    LottieRenderCache.ensureForFolder(
        startupSettings.streamingSettings.lowerThirdFolder,
        startupSettings.atemSettings
    )

    application(exitProcessOnExit = true) {
        // Held above every window so each one -- the main window, Settings, every dialog -- is drawn
        // with the same accent, font and text size, and a change reaches all of them at once.
        var themeCustomization by remember { mutableStateOf(themeCustomizationFrom(startupSettings)) }
        CompositionLocalProvider(LocalThemeCustomization provides themeCustomization) {
            ChurchPresenterApp(coroutineExceptionHandler, onThemeCustomizationChange = { themeCustomization = it })
        }
    }
}


/**
 * The whole desktop UI: windows, presenter outputs, the server wiring and every dialog.
 *
 * Split out of [main] so that function is only the pre-UI startup — settings, crash reporting,
 * fonts, VLC and the single-instance lock — and this is the Compose tree.
 */
@Composable
private fun ApplicationScope.ChurchPresenterApp(
    coroutineExceptionHandler: CoroutineExceptionHandler,
    onThemeCustomizationChange: (ThemeCustomization) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope { coroutineExceptionHandler }
    val application = this
    val root = remember { AppRootState(application, coroutineScope) }
    with(root) {
        // The desktop's end of calendar sync with phones. Made here, beside the settings it writes
        // back to, so the startup round and the Settings card talk to the same object.
        val calendarSync = remember(appSettings.calendarStorageDirectory, appSettings.songSettings.storageDirectory) {
            CalendarSyncService(
                folder = appSettings.calendarFolder(),
                songFolder = appSettings.songSettings.storageDirectory.takeIf { it.isNotBlank() }?.let(::File),
                settings = { appSettings.calendarSync },
                saveSettings = { sync ->
                    appSettings = appSettings.copy(calendarSync = sync)
                    settingsManager.saveSettings(appSettings)
                },
                typicalSeconds = { song -> liveDurationLog.median(song.asDurationRow()) },
            )
        }
        DisposableEffect(Unit) {
            onDispose {
                instanceLinkViewModel.dispose()
                companionSatelliteViewModel.dispose()
            }
        }
        SettingsDrivenEffects()
        InstanceLinkFollowerWiring()
        MirroredBackgroundsWiring()
        val effectiveAppSettings = rememberEffectiveAppSettings()
        val tunnelStatus by companionServer.tunnelManager.status.collectAsState()
        val tunnelUrl by companionServer.tunnelManager.tunnelUrl.collectAsState()
        CompanionServerWiring(tunnelStatus)
        VirtualOutputs(effectiveAppSettings)
        StartupEffect()
        AppWindows(effectiveAppSettings, calendarSync, tunnelStatus, tunnelUrl, onThemeCustomizationChange)
    }
}
