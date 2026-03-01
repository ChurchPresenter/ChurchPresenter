package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.utils.Constants
import java.awt.Window
import java.nio.file.Path
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.copyTo
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Handles all file system operations for the application.
 * Separated from UI to follow clean architecture principles.
 */
class FileManager {

    /**
     * Get list of song files (*.sps) from a directory
     */
    fun getSongFilesInDirectory(directory: Path): List<Path> {
        if (!directory.exists() || !directory.isDirectory()) return emptyList()
        return directory.listDirectoryEntries("*.${Constants.EXTENSION_SPS}").sortedBy { it.name }
    }

    /**
     * Get list of Bible files (*.spb) from a directory
     */
    fun getBibleFilesInDirectory(directory: Path): List<Path> {
        if (!directory.exists() || !directory.isDirectory()) return emptyList()
        return directory.listDirectoryEntries("*.${Constants.EXTENSION_SPB}").sortedBy { it.name }
    }

    /**
     * Show directory chooser dialog and return selected directory path
     */
    suspend fun chooseDirectory(currentDirectory: Path? = null): Path? {
        return FileChooser.platformInstance.chooseSingle(
            path = currentDirectory,
            filters = emptyList(),
            title = "",
            selectDirectory = true
        )
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
    fun importFiles(sourceFiles: List<Path>, targetDirectory: Path): List<String> {
        val errors = mutableListOf<String>()

        if (!targetDirectory.exists() || !targetDirectory.isDirectory()) {
            errors.add("Target directory does not exist: $targetDirectory")
            return errors
        }

        sourceFiles.forEach { sourceFile ->
            val targetFile = targetDirectory.resolve(sourceFile.name)
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
    fun deleteFile(file: Path?): String? {
        if (file == null) return "File does not exist"
        return try {
            if (file.deleteIfExists()) {
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
