package org.churchpresenter.app.churchpresenter.dialogs.filechooser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Dialog
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Window
import java.io.File

/**
 * The JDK's native file dialog, owned by [owner] -- for a window that is not the main one.
 *
 * FileKit's macOS panel is app-modal with no parent: shown from the Calendar window it can be left
 * off screen while it still blocks every window of the app. On Windows it is parented to the main
 * window, which the Calendar window sits in front of. AWT's dialog is modal to its owner and is
 * ordered in front of it, wherever that window is -- a frame or, like the Calendar window, a dialog.
 */
internal object OwnedFileDialog {

    suspend fun save(owner: Window, title: String, suggestedName: String, folder: File? = null): File? =
        show(owner, title, FileDialog.SAVE) { dialog ->
            dialog.file = suggestedName
            if (folder != null) dialog.directory = folder.path
        }

    suspend fun open(owner: Window, title: String, extensions: Set<String>): File? =
        show(owner, title, FileDialog.LOAD) { dialog ->
            dialog.setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in extensions }
        }

    private suspend fun show(owner: Window, title: String, mode: Int, configure: (FileDialog) -> Unit): File? =
        withContext(Dispatchers.Main) {
            val dialog = when (owner) {
                is Frame -> FileDialog(owner, title, mode)
                is Dialog -> FileDialog(owner, title, mode)
                else -> FileDialog(null as Frame?, title, mode)
            }
            configure(dialog)
            dialog.isVisible = true
            val name = dialog.file
            val folder = dialog.directory
            dialog.dispose()
            if (name == null || folder == null) null else File(folder, name)
        }
}
