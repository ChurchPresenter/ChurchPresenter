package org.churchpresenter.app.churchpresenter.utils

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File

/**
 * Reading files out of a drag, split from the drop target so it can be tested against a stand-in
 * [Transferable] rather than a real drag from the desktop.
 *
 * The two halves answer different questions at different moments, and conflating them is what stops
 * a drop working at all:
 *
 *  * [carriesFileList] is asked **during** the drag, to decide whether the target engages. It is a
 *    flavor check and nothing more.
 *  * [fileListOrNull] is asked **at the drop**, when the data may finally be pulled across.
 *
 * `getTransferData` is refused mid-drag on macOS, so using it for the first question makes the
 * predicate answer no and the target never engages.
 *
 * Measured on macOS, a drop from Finder arrives carrying two dozen `text/uri-list` flavors plus
 * `application/x-java-file-list`; the last of those is the one that yields [File]s.
 */
internal fun Transferable.carriesFileList(): Boolean =
    isDataFlavorSupported(DataFlavor.javaFileListFlavor)

/**
 * The files this drag carries, or null when it carries none.
 *
 * Null rather than an empty list because the caller's question is "is there anything to add?", and
 * an empty drop and a drop of something that is not a file both answer it the same way.
 */
@Suppress("UNCHECKED_CAST")
internal fun Transferable.fileListOrNull(): List<File>? =
    takeIf { it.carriesFileList() }
        ?.getTransferData(DataFlavor.javaFileListFlavor)
        ?.let { it as? List<File> }
        ?.takeIf { it.isNotEmpty() }
