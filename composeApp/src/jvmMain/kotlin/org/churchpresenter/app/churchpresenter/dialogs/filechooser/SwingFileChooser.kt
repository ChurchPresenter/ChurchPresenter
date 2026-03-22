package org.churchpresenter.app.churchpresenter.dialogs.filechooser

import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO
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
                iconImage = loadAppIcon()
            } catch (_: Exception) {}
        }
    }

    /** Loads the app icon from appResources (packaged) or the source tree (IDE run). */
    private fun loadAppIcon(): java.awt.Image? {
        // 1. Packaged app: compose.application.resources.dir is set
        System.getProperty("compose.application.resources.dir")?.let { resDir ->
            listOf("icon-32.png", "icon-48.png", "icon.png")
                .map { File(resDir, it) }
                .firstOrNull { it.exists() }
                ?.let { return ImageIO.read(it) }
        }
        // 2. IDE / development run: walk up from working directory to find appResources
        var dir = File(System.getProperty("user.dir"))
        repeat(6) {
            val candidate = File(dir, "composeApp/src/jvmMain/appResources/common/icon-32.png")
            if (candidate.exists()) return ImageIO.read(candidate)
            dir = dir.parentFile ?: return@repeat
        }
        return null
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
