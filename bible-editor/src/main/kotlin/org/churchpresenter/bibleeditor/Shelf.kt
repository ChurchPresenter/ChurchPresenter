package org.churchpresenter.bibleeditor

import org.churchpresenter.bible.MAX_BIBLE_SCAN_DEPTH
import org.churchpresenter.bible.defaultTranslationAbbreviation
import org.churchpresenter.bible.readTranslationTitle
import java.io.File

/** Every `.spb` under [folder], with its title and abbreviation read from its header. */
internal fun scanShelf(folder: File): List<ShelfBible> {
    if (!folder.isDirectory) return emptyList()
    return folder.walkTopDown().maxDepth(MAX_BIBLE_SCAN_DEPTH)
        .filter { it.isFile && it.extension.equals("spb", ignoreCase = true) }
        .map { file ->
            val title = readTranslationTitle(file)
            ShelfBible(file.relativeTo(folder).path, file, title, defaultTranslationAbbreviation(title, file.name))
        }
        .sortedBy { it.title.lowercase() }
        .toList()
}
