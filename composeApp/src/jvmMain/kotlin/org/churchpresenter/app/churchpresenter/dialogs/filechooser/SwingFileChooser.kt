package org.churchpresenter.app.churchpresenter.dialogs.filechooser

import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter


object SwingFileChooser : FileChooser() {

    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun chooseImpl(
        path: Path,
        filters: List<FileNameExtensionFilter>,
        title: String,
        selectDirectory: Boolean,
        multiple: Boolean
    ): List<Path>? {
        var result: List<Path>? = null
        SwingUtilities.invokeAndWait {
            val chooser = JFileChooser(path.toFile()).apply {
                dialogTitle = title
                fileSelectionMode = if (selectDirectory) JFileChooser.DIRECTORIES_ONLY else JFileChooser.FILES_ONLY
                isMultiSelectionEnabled = multiple
                filters.forEach { addChoosableFileFilter(it) }
            }
            val returnCode = chooser.showOpenDialog(null)
            result = if (returnCode == JFileChooser.APPROVE_OPTION) {
                if (multiple) chooser.selectedFiles.map { it.toPath() } else listOf(chooser.selectedFile.toPath())
            } else {
                null
            }
        }
        return result
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun saveImpl(
        location: Path,
        suggestedName: String,
        filters: List<FileNameExtensionFilter>,
        title: String
    ): Path? {
        var result: Path? = null
        SwingUtilities.invokeAndWait {
            val chooser = JFileChooser(location.toFile()).apply {
                dialogTitle = title
                selectedFile = location.resolve(suggestedName).toFile()
                filters.forEach { addChoosableFileFilter(it) }
            }
            val returnCode = chooser.showSaveDialog(null)
            result = if (returnCode == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile.toPath()
            } else {
                null
            }
        }
        return result
    }
}