package org.churchpresenter.canvas

import java.nio.file.Path
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Picking one file off disk — an image or a video for a scene source.
 *
 * The app's `FileChooser` is four classes and a thousand lines: three platform implementations, a
 * save path, a multi-select path and the name normalisation around them. The canvas uses exactly one
 * of its methods, twice, so this is that method and no more. `:composeApp` adapts its real chooser
 * to this in `FileChooserCanvasFilePicker`.
 *
 * Defaults to picking nothing, which is what a cancelled dialog returns — so a preview or a test
 * composes without a file system behind it and the button is simply inert.
 */
fun interface CanvasFilePicker {

    /**
     * Opens a picker at [path] (or the home directory when null) and returns the chosen file, or
     * null when the operator cancels.
     */
    suspend fun chooseSingle(path: Path?, filters: List<FileNameExtensionFilter>, title: String): Path?

    companion object {
        /** Picks nothing. */
        val None: CanvasFilePicker = CanvasFilePicker { _, _, _ -> null }
    }
}
