package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.utils.Constants
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import java.awt.Window

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
    fun chooseDirectory(currentDirectory: String = "", parentWindow: Window? = null): String? {
        val dirChooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            if (currentDirectory.isNotEmpty()) {
                this.currentDirectory = File(currentDirectory)
            }
        }
        val result = dirChooser.showOpenDialog(parentWindow)
        return if (result == JFileChooser.APPROVE_OPTION) {
            dirChooser.selectedFile.absolutePath
        } else {
            null
        }
    }

    /**
     * Show file chooser dialog for song files and return selected files
     */
    fun chooseSongFiles(parentWindow: Window? = null): List<File>? {
        val fileChooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            isMultiSelectionEnabled = true
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                "Song Files (*.${Constants.EXTENSION_SPS})", Constants.EXTENSION_SPS
            )
        }
        val result = fileChooser.showOpenDialog(parentWindow)
        return if (result == JFileChooser.APPROVE_OPTION) {
            fileChooser.selectedFiles.toList()
        } else {
            null
        }
    }

    /**
     * Show file chooser dialog for Bible files and return selected file
     */
    fun chooseBibleFile(parentWindow: Window? = null): File? {
        val fileChooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            isMultiSelectionEnabled = false
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                "Bible Files (*.${Constants.EXTENSION_SPB})", Constants.EXTENSION_SPB
            )
        }
        val result = fileChooser.showOpenDialog(parentWindow)
        return if (result == JFileChooser.APPROVE_OPTION) {
            fileChooser.selectedFile
        } else {
            null
        }
    }

    /**
     * Import (copy) files to target directory
     * Returns list of error messages if any
     */
    fun importFiles(sourceFiles: List<File>, targetDirectory: String): List<String> {
        val errors = mutableListOf<String>()
        val targetDir = File(targetDirectory)

        if (!targetDir.exists() || !targetDir.isDirectory) {
            errors.add("Target directory does not exist: $targetDirectory")
            return errors
        }

        sourceFiles.forEach { sourceFile ->
            val targetFile = File(targetDir, sourceFile.name)
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
