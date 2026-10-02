package org.churchpresenter.app.churchpresenter.dialogs.tabs

import kotlinx.serialization.json.Json
import org.churchpresenter.sharedui.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CalendarSyncSettings
import org.churchpresenter.settings.SettingsManager
import java.awt.Window
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.churchpresenter.settings.hasSecrets
import org.churchpresenter.settings.importedSettings
import org.churchpresenter.settings.withoutSecrets

private val exportJsonFormat = Json {
    encodeDefaults = true
    prettyPrint = true
}

private const val EXPORT_NAME = "churchpresenter-settings.json"
private const val SAFE_EXPORT_NAME = "churchpresenter-settings-no-passwords.json"

internal fun activeWindow(): Window? = Window.getWindows().firstOrNull { it.isActive }

/**
 * Saves the settings to a file the user picks. [withoutSecrets] leaves out every password, sign-in
 * and API key ([withoutSecrets]), for a file that is safe to share -- with support, on an issue.
 */
internal suspend fun exportSettings(
    title: String,
    exportedMsg: String,
    failedMsg: String,
    withoutSecrets: Boolean = false,
) {
    var file = FileChooser.platformInstance.save(
        location = null,
        suggestedName = if (withoutSecrets) SAFE_EXPORT_NAME else EXPORT_NAME,
        title = title,
        filters = listOf(FileNameExtensionFilter("JSON (*.json)", "json"))
    ) ?: return
    try {
        // The calendar relay's key and tokens are this church's credentials, not preferences: an export
        // gets emailed and shared, so they never go into one.
        val loaded = SettingsManager().loadSettings()
        val currentSettings = if (withoutSecrets) {
            loaded.withoutSecrets()
        } else {
            loaded.copy(calendarSync = CalendarSyncSettings())
        }
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

/** What the import asks when the file carries passwords of its own: the question and its two answers. */
internal class SecretsChoice(val question: String, val keep: String, val useFile: String, val cancel: String)

internal suspend fun importSettings(
    title: String,
    confirmMsg: String,
    failedMsg: String,
    companionServer: CompanionServer?,
    secrets: SecretsChoice,
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
        // Keep this machine's own relay pairing: an older export may still carry another machine's key
        // and tokens, and adopting them would make two desktops answer as one.
        val imported = settingsManager.migrateAndDecode(file.readText())
        // A file with passwords of its own asks whose to keep; one without keeps this computer's
        val useFileSecrets = if (imported.hasSecrets) askUseFileSecrets(title, secrets) ?: return else false
        settingsManager.saveSettings(importedSettings(imported, settingsManager.loadSettings(), useFileSecrets))
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

/** The folders a device can write into, all cleared together by [clearRemoteUploads]. */
private val REMOTE_UPLOAD_DIRS = listOf("device_uploads", "device_presentations", "device_media")

internal fun clearRemoteUploads(title: String, confirmMsg: String, clearedMsg: String) {
    SwingUtilities.invokeLater {
        val confirmed = JOptionPane.showConfirmDialog(
            activeWindow(), confirmMsg, title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
        ) == JOptionPane.YES_OPTION
        if (!confirmed) return@invokeLater
        // All three trees, not just the pictures one: the button offers to delete "all remotely
        // uploaded files", and decks and media pushed from a phone are written beside them and
        // were never cleaned by anything.
        val home = java.io.File(System.getProperty("user.home"))
        REMOTE_UPLOAD_DIRS.forEach { java.io.File(home, ".churchpresenter/$it").deleteRecursively() }
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

/** True to take the file's passwords, false to keep this computer's, null when the import is called off. */
private fun askUseFileSecrets(title: String, choice: SecretsChoice): Boolean? {
    val options = arrayOf(choice.keep, choice.useFile, choice.cancel)
    val picked = JOptionPane.showOptionDialog(
        activeWindow(), choice.question, title, JOptionPane.YES_NO_CANCEL_OPTION,
        JOptionPane.QUESTION_MESSAGE, null, options, options[0],
    )
    return when (picked) {
        0 -> false
        1 -> true
        else -> null
    }
}
