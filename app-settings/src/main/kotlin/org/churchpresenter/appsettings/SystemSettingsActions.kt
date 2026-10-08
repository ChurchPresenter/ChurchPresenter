package org.churchpresenter.appsettings

import kotlinx.serialization.json.Json
import org.churchpresenter.sharedui.filechooser.FileChooser
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CalendarSyncSettings
import org.churchpresenter.settings.SettingsManager
import java.awt.Window
import java.nio.file.Path
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

/** How a settings action tells the operator something: an information or an error message. */
internal enum class SettingsMessageKind { INFO, ERROR }

/** How a settings action asks a yes/no question: as a warning, or as a plain question. */
internal enum class SettingsQuestionKind { WARNING, QUESTION }

/**
 * The steps of the settings actions that only a person at a real screen can take -- the file
 * pickers, the dialogs, the Swing thread and the relaunch. Everything else those actions decide
 * stays in them, against this, so a test can answer for the person. [SwingSettingsActionUi] is the
 * app's; every action takes it as a defaulted last parameter.
 */
internal interface SettingsActionUi {
    /** The path to save to, offering [suggestedName]; null when the operator cancelled. */
    suspend fun chooseSavePath(suggestedName: String, title: String): Path?

    /** The JSON file to open; null when the operator cancelled. */
    suspend fun chooseOpenPath(title: String): Path?

    fun showMessage(message: String, title: String, kind: SettingsMessageKind)

    /** True when the operator answered yes. */
    fun confirm(message: String, title: String, kind: SettingsQuestionKind): Boolean

    /** The index of the option picked from [options] (the first is the default); anything else when closed. */
    fun pickOption(question: String, title: String, options: Array<String>): Int

    /** Runs [block] on the UI thread, later. */
    fun onUiThread(block: () -> Unit)

    /** Stops [companionServer], relaunches the app and exits this one. */
    fun restart(companionServer: CompanionServer?)
}

/** The real pickers, JOptionPane, the Swing event thread and a relaunch of this JVM. */
internal object SwingSettingsActionUi : SettingsActionUi {
    override suspend fun chooseSavePath(suggestedName: String, title: String): Path? =
        FileChooser.platformInstance.save(
            location = null,
            suggestedName = suggestedName,
            title = title,
            filters = listOf(FileNameExtensionFilter("JSON (*.json)", "json"))
        )

    override suspend fun chooseOpenPath(title: String): Path? =
        FileChooser.platformInstance.chooseSingle(
            path = null,
            filters = listOf(FileNameExtensionFilter("JSON (*.json)", "json")),
            title = title,
            selectDirectory = false
        )

    override fun showMessage(message: String, title: String, kind: SettingsMessageKind) {
        val type = when (kind) {
            SettingsMessageKind.INFO -> JOptionPane.INFORMATION_MESSAGE
            SettingsMessageKind.ERROR -> JOptionPane.ERROR_MESSAGE
        }
        JOptionPane.showMessageDialog(activeWindow(), message, title, type)
    }

    override fun confirm(message: String, title: String, kind: SettingsQuestionKind): Boolean {
        val type = when (kind) {
            SettingsQuestionKind.WARNING -> JOptionPane.WARNING_MESSAGE
            SettingsQuestionKind.QUESTION -> JOptionPane.QUESTION_MESSAGE
        }
        return JOptionPane.showConfirmDialog(
            activeWindow(), message, title, JOptionPane.YES_NO_OPTION, type
        ) == JOptionPane.YES_OPTION
    }

    override fun pickOption(question: String, title: String, options: Array<String>): Int =
        JOptionPane.showOptionDialog(
            activeWindow(), question, title, JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE, null, options, options[0],
        )

    override fun onUiThread(block: () -> Unit) = SwingUtilities.invokeLater(block)

    /**
     * Relaunches the app so the settings just written are the ones it comes back with.
     *
     * The server is stopped first so in-flight WebSocket sessions (a connected companion app, say)
     * close cleanly instead of hitting a ping timeout when the JVM exits.
     */
    override fun restart(companionServer: CompanionServer?) {
        try { companionServer?.stop() } catch (_: Exception) {}
        val javaBin = System.getProperty("java.home") + "/bin/java"
        val command = ProcessHandle.current().info().command().orElse(javaBin)
        val args = ProcessHandle.current().info().arguments().orElse(emptyArray())
        try {
            ProcessBuilder(listOf(command) + args.toList()).start()
        } catch (_: Exception) {}
        Runtime.getRuntime().exit(0)
    }
}

/**
 * Saves the settings to a file the user picks. [withoutSecrets] leaves out every password, sign-in
 * and API key ([withoutSecrets]), for a file that is safe to share -- with support, on an issue.
 */
internal suspend fun exportSettings(
    title: String,
    exportedMsg: String,
    failedMsg: String,
    withoutSecrets: Boolean = false,
    ui: SettingsActionUi = SwingSettingsActionUi,
) {
    var file = ui.chooseSavePath(
        suggestedName = if (withoutSecrets) SAFE_EXPORT_NAME else EXPORT_NAME,
        title = title,
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
        ui.showMessage(exportedMsg, title, SettingsMessageKind.INFO)
    } catch (_: Exception) {
        ui.showMessage(failedMsg, title, SettingsMessageKind.ERROR)
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
    ui: SettingsActionUi = SwingSettingsActionUi,
) {
    val file = ui.chooseOpenPath(title) ?: return
    val confirmed = ui.confirm(confirmMsg, title, SettingsQuestionKind.WARNING)
    if (!confirmed) return
    try {
        val settingsManager = SettingsManager()
        // Migrate on import, not just on startup — an export taken from an older build is in an
        // older schema, and decoding it directly would drop every field a migration converts.
        // Keep this machine's own relay pairing: an older export may still carry another machine's key
        // and tokens, and adopting them would make two desktops answer as one.
        val imported = settingsManager.migrateAndDecode(file.readText())
        // A file with passwords of its own asks whose to keep; one without keeps this computer's
        val useFileSecrets = if (imported.hasSecrets) askUseFileSecrets(title, secrets, ui) ?: return else false
        settingsManager.saveSettings(importedSettings(imported, settingsManager.loadSettings(), useFileSecrets))
        ui.restart(companionServer)
    } catch (_: Exception) {
        ui.showMessage(failedMsg, title, SettingsMessageKind.ERROR)
    }
}

internal fun resetAllSettings(
    title: String,
    confirmMsg: String,
    clearCacheMsg: String,
    companionServer: CompanionServer?,
    ui: SettingsActionUi = SwingSettingsActionUi,
) {
    ui.onUiThread {
        val confirmed = ui.confirm(confirmMsg, title, SettingsQuestionKind.WARNING)
        if (!confirmed) return@onUiThread
        val settingsManager = SettingsManager()
        val clearCache = ui.confirm(clearCacheMsg, title, SettingsQuestionKind.QUESTION)
        if (clearCache) {
            settingsManager.lottiePresetsDir.deleteRecursively()
        }
        settingsManager.saveSettings(AppSettings())
        ui.restart(companionServer)
    }
}

/** The folders a device can write into, all cleared together by [clearRemoteUploads]. */
private val REMOTE_UPLOAD_DIRS = listOf("device_uploads", "device_presentations", "device_media")

internal fun clearRemoteUploads(
    title: String,
    confirmMsg: String,
    clearedMsg: String,
    ui: SettingsActionUi = SwingSettingsActionUi,
) {
    ui.onUiThread {
        val confirmed = ui.confirm(confirmMsg, title, SettingsQuestionKind.WARNING)
        if (!confirmed) return@onUiThread
        // All three trees, not just the pictures one: the button offers to delete "all remotely
        // uploaded files", and decks and media pushed from a phone are written beside them and
        // were never cleaned by anything.
        val home = java.io.File(System.getProperty("user.home"))
        REMOTE_UPLOAD_DIRS.forEach { java.io.File(home, ".churchpresenter/$it").deleteRecursively() }
        ui.showMessage(clearedMsg, title, SettingsMessageKind.INFO)
    }
}

/** True to take the file's passwords, false to keep this computer's, null when the import is called off. */
private fun askUseFileSecrets(title: String, choice: SecretsChoice, ui: SettingsActionUi): Boolean? {
    val options = arrayOf(choice.keep, choice.useFile, choice.cancel)
    val picked = ui.pickOption(choice.question, title, options)
    return when (picked) {
        0 -> false
        1 -> true
        else -> null
    }
}
