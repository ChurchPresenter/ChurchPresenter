package org.churchpresenter.app.churchpresenter.dialogs.filechooser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * The JDK's native file dialog, owned by [owner] -- for a window that is not the main one.
 *
 * FileKit's macOS panel is app-modal with no parent: shown from the Calendar window it can be left
 * off screen while it still blocks every window of the app. AWT's dialog is modal to its owner and
 * is ordered in front of it, wherever that window is.
 */
internal object OwnedFileDialog {

    suspend fun save(owner: Frame, title: String, suggestedName: String): File? =
        show(owner, title, FileDialog.SAVE) { it.file = suggestedName }

    suspend fun open(owner: Frame, title: String, extensions: Set<String>): File? =
        show(owner, title, FileDialog.LOAD) { dialog ->
            dialog.setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in extensions }
        }

    private suspend fun show(owner: Frame, title: String, mode: Int, configure: (FileDialog) -> Unit): File? =
        withContext(Dispatchers.Main) {
            val dialog = FileDialog(owner, title, mode)
            configure(dialog)
            dialog.isVisible = true
            val name = dialog.file
            val folder = dialog.directory
            dialog.dispose()
            if (name == null || folder == null) null else File(folder, name)
        }
}
