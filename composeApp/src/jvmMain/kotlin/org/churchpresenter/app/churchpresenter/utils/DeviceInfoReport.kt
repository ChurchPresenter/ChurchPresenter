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

    fun generate(settings: AppSettings): String = buildString {
        val now = LocalDateTime.now()
        val appVersion = try { BuildConfig.VERSION_DISPLAY } catch (_: Exception) { "unknown" }
        val buildType = try { if (BuildConfig.IS_RELEASE) "release" else "dev" } catch (_: Exception) { "unknown" }

        appendLine("=== ChurchPresenter Diagnostic Report ===")
        appendLine("Generated: ${now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
        appendLine()

        appendLine("-- App --")
        appendLine("Version: $appVersion ($buildType)")
        appendLine("Install ID: ${CrashReporter.installId()}")
        appendLine("Crashed last run: ${CrashReporter.didCrashLastRun} (consecutive crashes: ${CrashReporter.consecutiveCrashes})")
        appendLine("Video backgrounds disabled (crash guard): ${CrashReporter.videoBackgroundsDisabled}")
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
        val screens = try {
            val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
            ge.screenDevices.mapIndexed { index, device ->
                val mode = device.displayMode
                val primary = device == ge.defaultScreenDevice
                "  ${index + 1}. ${mode.width}x${mode.height} @${mode.refreshRate}Hz${if (primary) " (primary)" else ""}"
            }
        } catch (_: Exception) { emptyList() }
        if (screens.isEmpty()) appendLine("  (unable to enumerate)") else screens.forEach { appendLine(it) }
        appendLine()

        appendLine("-- DeckLink --")
        val deckLinkAvailable = try { DeckLinkManager.isAvailable() } catch (_: Exception) { false }
        appendLine("Driver available: $deckLinkAvailable")
        if (deckLinkAvailable) {
            val devices = try { DeckLinkManager.listDevices() } catch (_: Exception) { emptyList() }
            if (devices.isEmpty()) appendLine("  (no devices detected)")
            else devices.forEach { appendLine("  ${it.index + 1}. ${it.name}") }
        }
        appendLine()

        appendLine("-- Video / Web --")
        val vlcAvailable = try { isVlcAvailable } catch (_: Exception) { false }
        appendLine("VLC: ${if (vlcAvailable) "available" else "unavailable (${vlcUnavailableReason.ifBlank { "unknown reason" }})"}")
        appendLine("Web browser (JCEF): ${if (CefManager.initialized) "initialized" else "not initialized"}${if (CefManager.macOsUnsupported) " (macOS version too old)" else ""}")
        appendLine()

        appendLine("-- Libraries --")
        val fileManager = FileManager()
        val songFolders = try { fileManager.getSongFoldersInDirectory(settings.songSettings.storageDirectory) } catch (_: Exception) { emptyList() }
        val bibleFiles = try { fileManager.getBibleFilesInDirectory(settings.bibleSettings.storageDirectory) } catch (_: Exception) { emptyList() }
        appendLine("Song libraries (songbooks): ${songFolders.size}")
        appendLine("Total songs: ${songFolders.sumOf { it.second }}")
        appendLine("Bibles: ${bibleFiles.size}")
        appendLine()

        appendLine("-- Outputs & Integrations --")
        appendLine("Configured outputs: ${settings.projectionSettings.screenAssignments.size}")
        appendLine("Browser Source outputs: ${settings.projectionSettings.browserSourceOutputs.size}")
        appendLine("ATEM: ${if (settings.atemSettings.host.isNotBlank()) "configured" else "not configured"}")
        appendLine("OBS: ${if (settings.obsSettings.enabled) "enabled" else "disabled"}")
        appendLine("Companion server: ${if (settings.serverSettings.enabled) "enabled" else "disabled"}")
        appendLine("Instance Link: ${if (settings.instanceLink.enabled) "enabled" else "disabled"}")
        appendLine("Analytics reporting: ${if (CrashReporter.isEnabled()) "enabled" else "disabled"}")
    }
}
