package org.churchpresenter.app.churchpresenter.data

import org.churchpresenter.bible.Bible
import org.churchpresenter.bible.bibleDisplayNames
import org.churchpresenter.bible.readTranslationTitle
import org.churchpresenter.app.churchpresenter.viewmodel.FileManager

/** A Bible folder's installed modules, and the name each one is shown under in a picker. */
data class BibleFolderListing(
    val files: List<String>,
    val displayNames: Map<String, String>,
) {
    /** [fileName]'s title, falling back to the file name itself for a module not in this listing. */
    fun nameOf(fileName: String): String = displayNames[fileName] ?: fileName

    companion object {
        val EMPTY = BibleFolderListing(emptyList(), emptyMap())
    }
}

/**
 * Lists [directory]'s modules and reads the title out of each.
 *
 * **Blocking — call this from `Dispatchers.IO`, never from composition.** Listing is a bounded
 * recursive walk and naming opens a header out of every `.spb`, which on a folder of full-size
 * modules is the difference between a dialog that paints and one that hangs; the settings dialog
 * used to do both inline in its composition, on every open.
 *
 * Both halves come from the existing readers — [FileManager.getBibleFilesInDirectory] and
 * [bibleDisplayNames] — so there is one implementation of each rule, not a second copy here.
 */
fun readBibleFolderListing(directory: String): BibleFolderListing {
    if (directory.isEmpty()) return BibleFolderListing.EMPTY
    val files = FileManager().getBibleFilesInDirectory(directory)
    return BibleFolderListing(files, bibleDisplayNames(directory, files))
}
