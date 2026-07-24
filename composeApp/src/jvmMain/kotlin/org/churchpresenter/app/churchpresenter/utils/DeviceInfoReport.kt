package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.app.churchpresenter.BuildConfig
import org.churchpresenter.app.churchpresenter.composables.DeckLinkManager
import org.churchpresenter.app.churchpresenter.composables.isVlcAvailable
import org.churchpresenter.app.churchpresenter.composables.vlcUnavailableReason
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.presenter.CefManager
import org.churchpresenter.app.churchpresenter.viewmodel.FileManager
import java.awt.GraphicsEnvironment
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Builds a plain-text snapshot of the machine/app configuration for bug reports — OS, display
 * layout, VLC/DeckLink/JCEF availability, and a redacted summary of output/integration settings
 * (booleans and counts only — no hostnames, ports, or API keys, so it's safe to paste into a
 * public GitHub issue).
 */
object DeviceInfoReport {

    internal data class DeviceFacts(
        val appVersion: String,
        val buildType: String,
        val installId: String,
        val didCrashLastRun: Boolean,
        val consecutiveCrashes: Int,
        val videoBackgroundsDisabled: Boolean,
        val screens: List<String>,
        val deckLinkAvailable: Boolean,
        val deckLinkDevices: List<String>,
        val vlcAvailable: Boolean,
        val vlcReason: String,
        val jcefInitialized: Boolean,
        val jcefMacUnsupported: Boolean,
        val songFolderCount: Int,
        val totalSongs: Int,
        val bibleCount: Int,
        val analyticsEnabled: Boolean,
    )

    internal fun screenLine(index: Int, width: Int, height: Int, refreshRate: Int, primary: Boolean): String =
        "  ${index + 1}. ${width}x${height} @${refreshRate}Hz${if (primary) " (primary)" else ""}"

    internal fun deviceLine(index: Int, name: String): String = "  ${index + 1}. $name"

    fun generate(settings: AppSettings): String = render(settings, gather(settings), LocalDateTime.now())

    internal fun gather(settings: AppSettings): DeviceFacts {
        val appVersion = try { BuildConfig.VERSION_DISPLAY } catch (_: Exception) { "unknown" }
        val buildType = try { if (BuildConfig.IS_RELEASE) "release" else "dev" } catch (_: Exception) { "unknown" }

        val screens = try {
            val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
            ge.screenDevices.mapIndexed { index, device ->
                val mode = device.displayMode
                screenLine(index, mode.width, mode.height, mode.refreshRate, device == ge.defaultScreenDevice)
            }
        } catch (_: Exception) { emptyList() }

        val deckLinkAvailable = try { DeckLinkManager.isAvailable() } catch (_: Exception) { false }
        val deckLinkDevices = if (deckLinkAvailable) {
            try { DeckLinkManager.listDevices().map { deviceLine(it.index, it.name) } } catch (_: Exception) { emptyList() }
        } else emptyList()

        val vlcAvailable = try { isVlcAvailable } catch (_: Exception) { false }

        val fileManager = FileManager()
        val songFolders = try { fileManager.getSongFoldersInDirectory(settings.songSettings.storageDirectory) } catch (_: Exception) { emptyList() }
        val bibleFiles = try { fileManager.getBibleFilesInDirectory(settings.bibleSettings.storageDirectory) } catch (_: Exception) { emptyList() }

        return DeviceFacts(
            appVersion = appVersion,
            buildType = buildType,
            installId = CrashReporter.installId(),
            didCrashLastRun = CrashReporter.didCrashLastRun,
            consecutiveCrashes = CrashReporter.consecutiveCrashes,
            videoBackgroundsDisabled = CrashReporter.videoBackgroundsDisabled,
            screens = screens,
            deckLinkAvailable = deckLinkAvailable,
            deckLinkDevices = deckLinkDevices,
            vlcAvailable = vlcAvailable,
            vlcReason = vlcUnavailableReason,
            jcefInitialized = CefManager.initialized,
            jcefMacUnsupported = CefManager.macOsUnsupported,
            songFolderCount = songFolders.size,
            totalSongs = songFolders.sumOf { it.second },
            bibleCount = bibleFiles.size,
            analyticsEnabled = CrashReporter.isEnabled(),
        )
    }

    internal fun render(settings: AppSettings, facts: DeviceFacts, now: LocalDateTime): String = buildString {
        appendLine("=== ChurchPresenter Diagnostic Report ===")
        appendLine("Generated: ${now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
        appendLine()

        appendLine("-- App --")
        appendLine("Version: ${facts.appVersion} (${facts.buildType})")
        appendLine("Install ID: ${facts.installId}")
        appendLine("Crashed last run: ${facts.didCrashLastRun} (consecutive crashes: ${facts.consecutiveCrashes})")
        appendLine("Video backgrounds disabled (crash guard): ${facts.videoBackgroundsDisabled}")
        appendLine()

        appendLine("-- System --")
        appendLine("OS: ${System.getProperty("os.name", "unknown")} ${System.getProperty("os.version", "")} (${System.getProperty("os.arch", "unknown")})")
        appendLine("Java: ${System.getProperty("java.version", "unknown")} (${System.getProperty("java.vendor", "unknown")})")
        val runtime = Runtime.getRuntime()
        appendLine("CPU cores: ${runtime.availableProcessors()}")
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMb = runtime.maxMemory() / (1024 * 1024)
        appendLine("Memory: ${usedMb}MB used / ${maxMb}MB max heap")
        appendLine()

        appendLine("-- Displays --")
        if (facts.screens.isEmpty()) appendLine("  (unable to enumerate)") else facts.screens.forEach { appendLine(it) }
        appendLine()

        appendLine("-- DeckLink --")
        appendLine("Driver available: ${facts.deckLinkAvailable}")
        if (facts.deckLinkAvailable) {
            if (facts.deckLinkDevices.isEmpty()) appendLine("  (no devices detected)")
            else facts.deckLinkDevices.forEach { appendLine(it) }
        }
        appendLine()

        appendLine("-- Video / Web --")
        appendLine("VLC: ${if (facts.vlcAvailable) "available" else "unavailable (${facts.vlcReason.ifBlank { "unknown reason" }})"}")
        appendLine("Web browser (JCEF): ${if (facts.jcefInitialized) "initialized" else "not initialized"}${if (facts.jcefMacUnsupported) " (macOS version too old)" else ""}")
        appendLine()

        appendLine("-- Libraries --")
        appendLine("Song libraries (songbooks): ${facts.songFolderCount}")
        appendLine("Total songs: ${facts.totalSongs}")
        appendLine("Bibles: ${facts.bibleCount}")
        appendLine()

        appendLine("-- Outputs & Integrations --")
        appendLine("Configured outputs: ${settings.projectionSettings.screenAssignments.size}")
        appendLine("Browser Source outputs: ${settings.projectionSettings.browserSourceOutputs.size}")
        appendLine("ATEM: ${if (settings.atemSettings.host.isNotBlank()) "configured" else "not configured"}")
        appendLine("OBS: ${if (settings.obsSettings.enabled) "enabled" else "disabled"}")
        appendLine("Companion server: ${if (settings.serverSettings.enabled) "enabled" else "disabled"}")
        appendLine("Instance Link: ${if (settings.instanceLink.enabled) "enabled" else "disabled"}")
        appendLine("Analytics reporting: ${if (facts.analyticsEnabled) "enabled" else "disabled"}")
    }
}
