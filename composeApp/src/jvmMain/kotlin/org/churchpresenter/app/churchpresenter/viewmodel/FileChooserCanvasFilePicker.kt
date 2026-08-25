package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.canvas.CanvasFilePicker
import java.nio.file.Path
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * The app's real file chooser, as the canvas tab's [CanvasFilePicker].
 *
 * `:canvas-tab` cannot see [FileChooser] — it is four classes of platform-specific dialog code in
 * `:composeApp` — and needs exactly one of its methods, so this is the adapter between them. Without
 * it the tab falls back to `CanvasFilePicker.None` and both Browse buttons are silently inert.
 *
 * `selectDirectory` is fixed false: the canvas picks a file, never a folder.
 */
class FileChooserCanvasFilePicker(
    private val chooser: FileChooser = FileChooser.platformInstance,
) : CanvasFilePicker {

    override suspend fun chooseSingle(
        path: Path?,
        filters: List<FileNameExtensionFilter>,
        title: String,
    ): Path? = chooser.chooseSingle(path, filters, title, selectDirectory = false)
}
