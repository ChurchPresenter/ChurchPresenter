package org.churchpresenter.lottiegen.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** The native file choosers the generator and the editor open; each answers null when cancelled. */
internal interface LottieGenPickers {
    fun openFile(description: String, vararg extensions: String): File?
    fun openDirectory(): File?
    fun saveFile(title: String, suggestedName: String): File?
}

/** The real choosers: Swing's for opening, AWT's for saving. */
internal object SwingPickers : LottieGenPickers {
    override fun openFile(description: String, vararg extensions: String): File? {
        val chooser = JFileChooser()
        chooser.fileFilter = FileNameExtensionFilter(description, *extensions)
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    override fun openDirectory(): File? {
        val chooser = JFileChooser()
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    override fun saveFile(title: String, suggestedName: String): File? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
        dialog.file = suggestedName
        dialog.isVisible = true
        val dir = dialog.directory
        val fileName = dialog.file
        return if (dir != null && fileName != null) File(dir, fileName) else null
    }
}

/** The choosers in use; a test provides its own, since none of the real ones can open headless. */
internal val LocalLottieGenPickers = staticCompositionLocalOf<LottieGenPickers> { SwingPickers }
