package org.churchpresenter.app.churchpresenter.dialogs.tabs

import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SettingsManager
import java.awt.Window
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val exportJsonFormat = Json {
    encodeDefaults = true
    prettyPrint = true
}

internal fun activeWindow(): Window? = Window.getWindows().firstOrNull { it.isActive }

internal suspend fun exportSettings(title: String, exportedMsg: String, failedMsg: String) {
    var file = FileChooser.platformInstance.save(
        location = null,
        suggestedName = "churchpresenter-settings.json",
        title = title,
        filters = listOf(FileNameExtensionFilter("JSON (*.json)", "json"))
    ) ?: return
    try {
        val currentSettings = SettingsManager().loadSettings()
        val json = exportJsonFormat.encodeToString(AppSettings.serializer(), currentSettings)
        if (file.extension != "json") {
            file = file.resolveSibling("${file.nameWithoutExtension}.json")
        }
        file.writeText(json)
        JOptionPane.showMessageDialog(activeWindow(), exportedMsg, title, JOptionPane.INFORMATION_MESSAGE)
    } catch (_: Exception) {
        JOptionPane.showMessageDialog(activeWindow(), failedMsg, title, JOptionPane.ERROR_MESSAGE)
    }
}

internal suspend fun importSettings(
    title: String,
    confirmMsg: String,
    failedMsg: String,
    companionServer: CompanionServer?
) {
    val file = FileChooser.platformInstance.chooseSingle(
        path = null,
        filters = listOf(FileNameExtensionFilter("JSON (*.json)", "json")),
        title = title,
        selectDirectory = false
    ) ?: return
    val confirmed = JOptionPane.showConfirmDialog(
        activeWindow(), confirmMsg, title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
    ) == JOptionPane.YES_OPTION
    if (!confirmed) return
    try {
        val settingsManager = SettingsManager()
        // Migrate on import, not just on startup — an export taken from an older build is in an
        // older schema, and decoding it directly would drop every field a migration converts.
        settingsManager.saveSettings(settingsManager.migrateAndDecode(file.readText()))
        restartApp(companionServer)
    } catch (_: Exception) {
        JOptionPane.showMessageDialog(activeWindow(), failedMsg, title, JOptionPane.ERROR_MESSAGE)
    }
}

internal fun resetAllSettings(
    title: String,
    confirmMsg: String,
    clearCacheMsg: String,
    companionServer: CompanionServer?
) {
    SwingUtilities.invokeLater {
        val confirmed = JOptionPane.showConfirmDialog(
            activeWindow(), confirmMsg, title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
        ) == JOptionPane.YES_OPTION
        if (!confirmed) return@invokeLater
        val settingsManager = SettingsManager()
        val clearCache = JOptionPane.showConfirmDialog(
            activeWindow(), clearCacheMsg, title, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE
        )
        if (clearCache == JOptionPane.YES_OPTION) {
            settingsManager.lottiePresetsDir.deleteRecursively()
        }
        settingsManager.saveSettings(AppSettings())
        restartApp(companionServer)
    }
}

internal fun clearRemoteUploads(title: String, confirmMsg: String, clearedMsg: String) {
    SwingUtilities.invokeLater {
        val confirmed = JOptionPane.showConfirmDialog(
            activeWindow(), confirmMsg, title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
        ) == JOptionPane.YES_OPTION
        if (!confirmed) return@invokeLater
        java.io.File(System.getProperty("user.home"), ".churchpresenter/device_uploads").deleteRecursively()
        JOptionPane.showMessageDialog(activeWindow(), clearedMsg, title, JOptionPane.INFORMATION_MESSAGE)
    }
}

/**
 * Relaunches the app so the settings just written are the ones it comes back with.
 *
 * The server is stopped first so in-flight WebSocket sessions (a connected companion app, say)
 * close cleanly instead of hitting a ping timeout when the JVM exits.
 */
private fun restartApp(companionServer: CompanionServer?) {
    try { companionServer?.stop() } catch (_: Exception) {}
    val javaBin = System.getProperty("java.home") + "/bin/java"
    val command = ProcessHandle.current().info().command().orElse(javaBin)
    val args = ProcessHandle.current().info().arguments().orElse(emptyArray())
    try {
        ProcessBuilder(listOf(command) + args.toList()).start()
    } catch (_: Exception) {}
    Runtime.getRuntime().exit(0)
}
