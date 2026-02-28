package org.churchpresenter.app.churchpresenter.dialogs.filechooser

import org.churchpresenter.app.churchpresenter.utils.Constants
import java.nio.file.Path
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
     * @return The path of the selected file, or null if the user canceled the dialog
     */
    suspend fun save(location: Path?, suggestedName: String, filters: List<FileNameExtensionFilter>, title: String): Path? {
        val initialLocation = location ?: Path(System.getProperty(Constants.SystemProperties.USER_HOME))
        return withContext(Dispatchers.IO) { saveImpl(initialLocation, suggestedName, filters, title) }
    }

    protected abstract suspend fun saveImpl(location: Path, suggestedName: String, filters: List<FileNameExtensionFilter>, title: String): Path?

    private suspend fun choose(
        path: Path?,
        filters: List<FileNameExtensionFilter>,
        title: String,
        selectDirectory: Boolean,
        multiple: Boolean
    ): List<Path>? {
        val initialPath = path ?: Path(System.getProperty(Constants.SystemProperties.USER_HOME))
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
            val osName = System.getProperty(Constants.SystemProperties.OS_NAME).lowercase()
            if ("nix" in osName || "nux" in osName) {
                XdgFileChooser
            } else {
                SwingFileChooser
            }
        }
    }
}