package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.utils.Constants
import java.io.File
import javax.swing.JOptionPane
import java.awt.Window
import java.nio.file.Path
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * Handles all file system operations for the application.
 * Separated from UI to follow clean architecture principles.
 */
class FileManager {

    /**
     * Get list of song files (*.sps) from a directory
     */
    fun getSongFilesInDirectory(directory: String): List<String> {
        if (directory.isEmpty()) return emptyList()

        val dir = File(directory)
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        return dir.listFiles { file ->
            file.extension.lowercase() == Constants.EXTENSION_SPS
        }?.map { it.name }?.sorted() ?: emptyList()
    }

    /**
     * Get list of songbook folders (subdirectories containing .song files) from a directory
     */
    fun getSongFoldersInDirectory(directory: String): List<Pair<String, Int>> {
        if (directory.isEmpty()) return emptyList()

        val rootDir = File(directory)
        if (!rootDir.exists() || !rootDir.isDirectory) return emptyList()

        val results = mutableListOf<Pair<String, Int>>()

        // Count .song files in the root directory itself
        val rootSongCount = rootDir.listFiles { file ->
            file.extension.lowercase() == Constants.EXTENSION_SONG
        }?.size ?: 0
        if (rootSongCount > 0) {
            results.add(Pair("/", rootSongCount))
        }

        collectSongFolders(rootDir, rootDir, results)
        return results.sortedBy { it.first }
    }

    private fun collectSongFolders(currentDir: File, rootDir: File, results: MutableList<Pair<String, Int>>) {
        val subdirs = currentDir.listFiles { file -> file.isDirectory } ?: emptyArray()
        for (subdir in subdirs) {
            val songCount = subdir.listFiles { file ->
                file.extension.lowercase() == Constants.EXTENSION_SONG
            }?.size ?: 0
            if (songCount > 0) {
                val relativePath = subdir.toRelativeString(rootDir).replace('\\', '/')
                results.add(Pair(relativePath, songCount))
            }
            collectSongFolders(subdir, rootDir, results)
        }
    }

    /**
     * Get list of Bible files (*.spb) from a directory
     */
    fun getBibleFilesInDirectory(directory: String): List<String> {
        if (directory.isEmpty()) return emptyList()

        val dir = File(directory)
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        return dir.listFiles { file ->
            file.extension.lowercase() == Constants.EXTENSION_SPB
        }?.map { it.name }?.sorted() ?: emptyList()
    }

    /**
     * Show directory chooser dialog and return selected directory path
     */
    suspend fun chooseDirectory(currentDirectory: String = ""): String? {
        return FileChooser.platformInstance.chooseSingle(
            path = Path(currentDirectory),
            filters = emptyList(),
            title = "",
            selectDirectory = true
        )?.absolutePathString()
    }

    /**
     * Show file chooser dialog for song files and return selected files
     */
    suspend fun chooseSongFiles(): List<Path>? {
        return FileChooser.platformInstance.chooseMultiple(
            path = null,
            filters = listOf(
                FileNameExtensionFilter(
                    "Song Files (*.${Constants.EXTENSION_SPS})", Constants.EXTENSION_SPS
                )
            ),
            title = "",
            selectDirectory = false
        )
    }

    /**
     * Show file chooser dialog for Bible files and return selected file
     */
    suspend fun chooseBibleFile(): Path? {
        return FileChooser.platformInstance.chooseSingle(
            path = null,
            filters = listOf(
                FileNameExtensionFilter(
                    "Bible Files (*.${Constants.EXTENSION_SPB})", Constants.EXTENSION_SPB
                )
            ),
            title = "",
            selectDirectory = false
        )
    }

    /**
     * Import (copy) files to target directory
     * Returns list of error messages if any
     */
    fun importFiles(sourceFiles: List<Path>, targetDirectory: String): List<String> {
        val errors = mutableListOf<String>()
        val targetDir = Path(targetDirectory)

        if (!targetDir.exists() || !targetDir.isDirectory()) {
            errors.add("Target directory does not exist: $targetDirectory")
            return errors
        }

        sourceFiles.forEach { sourceFile ->
            val targetFile = targetDir.resolve(sourceFile.name)
            try {
                sourceFile.copyTo(targetFile, overwrite = true)
            } catch (e: Exception) {
                errors.add("Error copying ${sourceFile.name}: ${e.message}")
            }
        }

        return errors
    }

    /**
     * Delete a file from directory
     * Returns error message if failed, null if successful
     */
    fun deleteFile(directory: String, fileName: String): String? {
        val fileToDelete = File(directory, fileName)
        val dirCanonical = File(directory).canonicalPath
        if (!fileToDelete.canonicalPath.startsWith(dirCanonical + File.separator) &&
            fileToDelete.canonicalPath != dirCanonical) {
            return "Invalid file path"
        }
        return try {
            if (fileToDelete.delete()) {
                null // Success
            } else {
                "Failed to delete file"
            }
        } catch (e: Exception) {
            "Error deleting file: ${e.message}"
        }
    }

    /**
     * Show confirmation dialog
     * Returns true if user confirmed
     */
    fun showConfirmDialog(
        message: String,
        title: String = "Confirm",
        parentWindow: Window? = null
    ): Boolean {
        val result = JOptionPane.showConfirmDialog(
            parentWindow,
            message,
            title,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )
        return result == JOptionPane.YES_OPTION
    }

    /**
     * Show warning dialog
     */
    fun showWarning(
        message: String,
        title: String = "Warning",
        parentWindow: Window? = null
    ) {
        JOptionPane.showMessageDialog(
            parentWindow,
            message,
            title,
            JOptionPane.WARNING_MESSAGE
        )
    }

    /**
     * Show error dialog
     */
    fun showError(
        message: String,
        title: String = "Error",
        parentWindow: Window? = null
    ) {
        JOptionPane.showMessageDialog(
            parentWindow,
            message,
            title,
            JOptionPane.ERROR_MESSAGE
        )
    }
}
