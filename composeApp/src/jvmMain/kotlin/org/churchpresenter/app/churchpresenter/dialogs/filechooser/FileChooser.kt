package org.churchpresenter.app.churchpresenter.dialogs.filechooser

import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.diagnostics.CrashReporter
import java.nio.file.Path
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.path.exists

abstract class FileChooser {

    /**
     * Opens a file chooser dialog allowing the user to select a single file or directory.
     *
     * @param path The initial directory to open in the file chooser. If null, the user's home directory will be used.
     * @param filters the filters to apply when showing files in the dialog. If empty, no filtering will be applied. Ignored if [selectDirectory] is true.
     * @param title The title of the file chooser dialog.
     * @param selectDirectory If true, the dialog will allow selecting directories instead of files.
     * @return The path of the selected file or directory, or null if the user canceled the dialog.
     */
    suspend fun chooseSingle(
        path: Path?,
        filters: List<FileNameExtensionFilter>,
        title: String,
        selectDirectory: Boolean
    ): Path? {
        // firstOrNull, not single: a dialog is asked for one path but the answer comes from a
        // platform (the XDG portal's uri list, a native picker) that is free to hand back none or
        // several, and neither is worth crashing an Open over.
        return choose(path, filters, title, selectDirectory, multiple = false)?.firstOrNull()
    }

    /**
     * Opens a file chooser dialog allowing the user to select multiple files or directories.
     *
     * @param path The initial directory to open in the file chooser. If null, the user's home directory will be used as the initial path.
     * @param filters the filters to apply when showing files in the dialog. If empty, no filtering will be applied. Ignored if [selectDirectory] is true.
     * @param title The title of the file chooser dialog.
     * @param selectDirectory If true, the dialog will allow selecting directories instead of files.
     * @return A list of paths of the selected files or directories, or null if the user canceled the dialog.
     */
    suspend fun chooseMultiple(
        path: Path?,
        filters: List<FileNameExtensionFilter>,
        title: String,
        selectDirectory: Boolean
    ): List<Path>? {
        return choose(path, filters, title, selectDirectory, multiple = true)
    }

    /**
     * Opens a file chooser dialog allowing the user to select a location and name for saving a file.
     * @param location The initial directory to open in the file chooser. If null, the user's home directory will be used as the initial path.
     * @param suggestedName A suggested file name to pre-fill in the dialog.
     * @param filters the filters to apply when showing files in the dialog. If empty, no filtering will be applied.
     * @param title The title of the file chooser dialog.
     * @return The path of the selected file, or null if the user canceled the dialog.
     * [suggestedName] is written in full, with the extension; it is [baseName] that reaches the
     * platform dialog. When filters are provided, the returned path is guaranteed to carry exactly
     * one of the filter extensions — appended if the dialog dropped it, collapsed if it added a
     * second.
     */
    suspend fun save(location: Path?, suggestedName: String, filters: List<FileNameExtensionFilter>, title: String): Path? {
        val initialLocation = location ?: Path(System.getProperty(Constants.SystemProperties.USER_HOME))
        val extensions = filters.flatMap { it.extensions.toList() }
        val offered = baseName(suggestedName, extensions)
        val result = withContext(Dispatchers.IO) { saveImpl(initialLocation, offered, filters, title) } ?: return null
        val name = normalizeSavedName(result.fileName.toString(), extensions)
        return if (name == result.fileName.toString()) result else result.resolveSibling(name)
    }

    /**
     * [suggestedName] with a trailing filter extension taken off, which is what the dialogs are
     * offered.
     *
     * Every caller writes the name it wants in full ("schedule.cps"), and every native saver adds
     * the extension back itself — FileKit by an unconditional concat, the macOS panel and the
     * Windows dialog from the type they are told to save as — so a name handed over whole comes
     * back as `schedule.cps.cps`. Stripping it here means the split is done once, for every
     * platform, rather than in whichever backend last remembered to.
     */
    internal fun baseName(suggestedName: String, extensions: List<String>): String {
        val matched = extensions.firstOrNull { suggestedName.endsWith(".$it", ignoreCase = true) }
        return matched?.let { suggestedName.dropLast(it.length + 1) } ?: suggestedName
    }

    /**
     * [name] carrying exactly one of [extensions]: a repeat collapsed, a missing one added.
     *
     * [baseName] removes the cause on the way in; this removes the symptom on the way out, because
     * what a native dialog does to a name is not ours to predict and a doubled extension has
     * already shipped. Only a repeat of the *same* filter extension is collapsed, so a deliberate
     * `service.tar.gz` keeps both halves.
     */
    internal fun normalizeSavedName(name: String, extensions: List<String>): String {
        if (extensions.isEmpty()) return name
        var out = name
        val doubled = extensions.firstOrNull { out.endsWith(".$it.$it", ignoreCase = true) }
        if (doubled != null) {
            while (out.endsWith(".$doubled.$doubled", ignoreCase = true)) out = out.dropLast(doubled.length + 1)
        }
        return if (extensions.any { out.endsWith(".$it", ignoreCase = true) }) out else "$out.${extensions.first()}"
    }

    protected abstract suspend fun saveImpl(location: Path, suggestedName: String, filters: List<FileNameExtensionFilter>, title: String): Path?

    private suspend fun choose(
        path: Path?,
        filters: List<FileNameExtensionFilter>,
        title: String,
        selectDirectory: Boolean,
        multiple: Boolean
    ): List<Path>? {
        val initialPath = path?.takeIf { it.exists() } ?: Path(System.getProperty(Constants.SystemProperties.USER_HOME))
        // An empty selection is a cancel, not a selection of nothing — callers read null that way.
        return withContext(Dispatchers.IO) {
            chooseImpl(initialPath, filters, title, selectDirectory, multiple)?.takeIf { it.isNotEmpty() }
        }
    }

    /**
     * Latched after the first failure of this chooser's platform dialog, so a machine whose
     * dialogs are broken doesn't retry — and re-report — on every open.
     */
    @Volatile
    internal var nativeDialogsBroken = false

    /**
     * Runs [attempt] (the platform dialog) and falls back to [fallback] (the Swing dialog) when the
     * platform one is unavailable.
     *
     * Every platform dialog is somebody else's process or library — a native picker, the XDG
     * desktop portal over D-Bus — and none of them failing is a reason for the app to die: without
     * this, a session with no portal takes the whole app down the first time an operator opens a
     * folder. A [CancellationException] is the operator dismissing the dialog, not a fault, so it
     * is re-thrown untouched and must not latch the platform path off. [context] labels the crash
     * report the fault path files.
     */
    internal suspend fun <T> withNativeDialog(
        context: String,
        attempt: suspend () -> T,
        fallback: suspend () -> T
    ): T {
        if (nativeDialogsBroken) return fallback()
        return try {
            attempt()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            CrashReporter.reportException(t, context = context)
            nativeDialogsBroken = true
            fallback()
        }
    }

    protected abstract suspend fun chooseImpl(
        path: Path,
        filters: List<FileNameExtensionFilter>,
        title: String,
        selectDirectory: Boolean,
        multiple: Boolean
    ): List<Path>?

    companion object {
        val platformInstance: FileChooser by lazy {
            platformFor(System.getProperty(Constants.SystemProperties.OS_NAME))
        }

        /**
         * Which chooser [osName] calls for: Linux talks to the XDG desktop portal, everything else
         * gets a native dialog through FileKit.
         *
         * Picking wrong is not a graceful failure — the XDG chooser on a machine with no session
         * bus cannot open a dialog at all, so every Open and Save in the app stops working.
         */
        internal fun platformFor(osName: String): FileChooser {
            val name = osName.lowercase()
            return if ("nix" in name || "nux" in name) XdgFileChooser else FileKitFileChooser
        }
    }
}
