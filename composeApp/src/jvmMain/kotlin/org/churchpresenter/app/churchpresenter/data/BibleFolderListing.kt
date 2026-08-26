package org.churchpresenter.app.churchpresenter.data

import org.churchpresenter.bible.bibleTitles
import org.churchpresenter.bible.displayNamesFor
import org.churchpresenter.app.churchpresenter.viewmodel.FileManager

/** A Bible folder's installed modules, and the name each one is shown under in a picker. */
data class BibleFolderListing(
    val files: List<String>,
    /**
     * Each module's own header title, un-qualified and with no rename applied.
     *
     * Kept rather than only the finished names because a rename must not cost another walk of the
     * folder: [namesWith] recomputes from these in memory, on every keystroke if need be, while the
     * expensive half — one header read per module — happens once per folder.
     */
    val titles: Map<String, String>,
) {
    /** Picker names with no rename applied: every module under the title its own header gives. */
    val displayNames: Map<String, String> = displayNamesFor(titles)

    /** Picker names with the operator's renames ([overrides], keyed by file name) applied over them. */
    fun namesWith(overrides: Map<String, String>): Map<String, String> =
        if (overrides.isEmpty()) displayNames else displayNamesFor(titles, overrides)

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
 * [bibleTitles] — so there is one implementation of each rule, not a second copy here. Renames are
 * deliberately *not* applied here: they cost no IO, so they are applied by the caller through
 * [BibleFolderListing.namesWith] instead of pinning this scan to the settings that hold them.
 */
fun readBibleFolderListing(directory: String): BibleFolderListing {
    if (directory.isEmpty()) return BibleFolderListing.EMPTY
    val files = FileManager().getBibleFilesInDirectory(directory)
    return BibleFolderListing(files, bibleTitles(directory, files))
}
