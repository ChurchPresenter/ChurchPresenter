package org.churchpresenter.canvas

import org.churchpresenter.bible.MAX_BIBLE_SCAN_DEPTH
import java.io.File

/**
 * Every `.spb` file under [directory], as a path relative to it with `/` separators, sorted -- empty
 * when it is blank or not a folder. Searched [MAX_BIBLE_SCAN_DEPTH] deep, so a symlink cycle or a
 * stray deep tree cannot hang the Bible source's translation list.
 */
internal fun bibleFilesInDirectory(directory: String): List<String> {
    if (directory.isEmpty()) return emptyList()
    val dir = File(directory)
    if (!dir.isDirectory) return emptyList()
    return dir.walkTopDown().maxDepth(MAX_BIBLE_SCAN_DEPTH)
        .filter { it.isFile && it.extension.equals(SPB_EXTENSION, ignoreCase = true) }
        .map { it.toRelativeString(dir).replace('\\', '/') }
        .sorted()
        .toList()
}

private const val SPB_EXTENSION = "spb"
