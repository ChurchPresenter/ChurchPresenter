package org.churchpresenter.app.churchpresenter.dialogs.filechooser

import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter


object SwingFileChooser : FileChooser() {

    /** Hidden owner frame that provides the app icon to file chooser dialogs. */
    private val ownerFrame: JFrame by lazy {
        JFrame().apply {
            isUndecorated = true
            setSize(0, 0)
            setLocationRelativeTo(null)
            try {
                // Try to load icon from app resources directory
                val resDir = System.getProperty("compose.application.resources.dir")
                val iconFile = if (resDir != null) {
                    listOf("icon-32.png", "icon-48.png", "icon.png")
                        .map { java.io.File(resDir, it) }
                        .firstOrNull { it.exists() }
                } else null
                if (iconFile != null) {
                    iconImage = javax.imageio.ImageIO.read(iconFile)
                }
            } catch (_: Exception) {}
        }
    }

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
            ownerFrame.isVisible = true
            val returnCode = chooser.showOpenDialog(ownerFrame)
            ownerFrame.isVisible = false
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
            ownerFrame.isVisible = true
            val returnCode = chooser.showSaveDialog(ownerFrame)
            ownerFrame.isVisible = false
            result = if (returnCode == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile.toPath()
            } else {
                null
            }
        }
        return result
    }
}