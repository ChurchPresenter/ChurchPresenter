package org.churchpresenter.sharedui.testing

import org.churchpresenter.sharedui.filechooser.FileChooser
import java.nio.file.Path
import javax.swing.filechooser.FileNameExtensionFilter

/** A [FileChooser] that answers [answer] to every open, and records what it was asked. */
class FakeFileChooser(private val answer: Path?) : FileChooser() {
    var callCount = 0
        private set
    var lastPath: Path? = null
        private set
    var lastTitle: String? = null
        private set
    var lastFilters: List<FileNameExtensionFilter>? = null
        private set

    override suspend fun chooseImpl(
        path: Path,
        filters: List<FileNameExtensionFilter>,
        title: String,
        selectDirectory: Boolean,
        multiple: Boolean,
    ): List<Path>? {
        callCount++
        lastPath = path
        lastTitle = title
        lastFilters = filters
        return answer?.let { listOf(it) }
    }

    override suspend fun saveImpl(
        location: Path,
        suggestedName: String,
        filters: List<FileNameExtensionFilter>,
        title: String,
    ): Path? = error("FakeFileChooser does not save")
}
