package org.churchpresenter.app.churchpresenter.dialogs.filechooser

import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter


object SwingFileChooser : FileChooser() {

    override suspend fun chooseImpl(
        path: Path,
        filters: List<FileNameExtensionFilter>,
        title: String,
        selectDirectory: Boolean,
        multiple: Boolean
    ): List<Path>? {
        val chooser = JFileChooser(path.toFile()).apply {
            dialogTitle = title
            fileSelectionMode = if (selectDirectory) JFileChooser.DIRECTORIES_ONLY else JFileChooser.FILES_ONLY
            isMultiSelectionEnabled = multiple
            filters.forEach { addChoosableFileFilter(it) }
        }

        val result = chooser.showOpenDialog(null)
        return if (result == JFileChooser.APPROVE_OPTION) {
            if (multiple) chooser.selectedFiles.map { it.toPath() } else listOf(chooser.selectedFile.toPath())
        } else {
            null
        }
    }

    override suspend fun saveImpl(
        location: Path,
        suggestedName: String,
        filters: List<FileNameExtensionFilter>,
        title: String
    ): Path? {
        val chooser = JFileChooser(location.toFile()).apply {
            dialogTitle = title
            selectedFile = location.resolve(suggestedName).toFile()
            filters.forEach { addChoosableFileFilter(it) }
        }

        val result = chooser.showSaveDialog(null)
        return if (result == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.toPath()
        } else {
            null
        }
    }
}