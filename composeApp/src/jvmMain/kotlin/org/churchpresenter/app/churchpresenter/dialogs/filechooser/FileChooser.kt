package org.churchpresenter.app.churchpresenter.dialogs.filechooser

import org.churchpresenter.app.churchpresenter.utils.Constants
import java.nio.file.Path
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
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
        return choose(path, filters, title, selectDirectory, multiple = false)?.single()
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
     * When filters are provided, the returned path is guaranteed to carry one of the
     * filter extensions (the first one is appended if the user omitted it).
     */
    suspend fun save(location: Path?, suggestedName: String, filters: List<FileNameExtensionFilter>, title: String): Path? {
        val initialLocation = location ?: Path(System.getProperty(Constants.SystemProperties.USER_HOME))
        val result = withContext(Dispatchers.IO) { saveImpl(initialLocation, suggestedName, filters, title) } ?: return null
        val extensions = filters.flatMap { it.extensions.toList() }
        val name = result.fileName.toString()
        return if (extensions.isEmpty() || extensions.any { name.endsWith(".$it", ignoreCase = true) }) {
            result
        } else {
            result.resolveSibling("$name.${extensions.first()}")
        }
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
        return withContext(Dispatchers.IO) { chooseImpl(initialPath, filters, title, selectDirectory, multiple) }
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