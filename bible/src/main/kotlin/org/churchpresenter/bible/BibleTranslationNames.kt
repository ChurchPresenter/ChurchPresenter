package org.churchpresenter.bible

import java.io.File

/**
 * Naming installed `.spb` translations for the user.
 *
 * Four places used to hand-roll the same `##Title:` scan — the Bible settings picker, the dictionary's
 * bible list, the canvas source panel and the Bible tab's stack reorder dropdown — each with its own
 * copy of the fallback rules and its own idea of how many lines to read. They all go through
 * [readTranslationTitle] now, which reads the header through [Bible.readTranslationSummary]: one
 * parser for the format, in the same file as the format's other reader.
 *
 * The rules these callers all want, and which the single reader keeps:
 *  - a `##Title:` header names the translation, whether the value is separated by a tab (what the
 *    converter writes) or a space (what plenty of hand-made modules have);
 *  - **no** `##Title:` line, or a file that cannot be read at all, falls back to the file's stem;
 *  - a `##Title:` line with nothing after it stays blank, which is what the full loader does too —
 *    an empty title is a module that says its name is nothing, not a module that never said;
 *  - a title buried past the header block is not looked for. These callers scan whole folders, and
 *    reading through a directory of multi-megabyte modules would stall the picker doing it.
 */
fun readTranslationTitle(file: File): String =
    Bible.readTranslationSummary(file.absolutePath, maxLines = Bible.TITLE_SCAN_LINE_LIMIT)?.title
        ?: file.nameWithoutExtension

/**
 * Names every file in [files] (relative to [directory]) for a picker, keyed by file name.
 *
 * Reads one header per file, so keep this off the composition thread — the Bible tab reaches it
 * through `produceState`, the settings and dictionary view models from their own coroutines.
 */
fun bibleDisplayNames(directory: String, files: List<String>): Map<String, String> {
    if (directory.isEmpty()) return emptyMap()
    return uniqueDisplayNames(files.associateWith { readTranslationTitle(File(directory, it)) })
}

/**
 * Qualifies repeated titles so no two files end up sharing a display name.
 *
 * A picker reverse-maps the chosen name back to its file, so these have to be unique — and two files
 * genuinely can carry the same `##Title:`: a collection nests one folder per translation and commonly
 * holds several editions of one version. The folder is what distinguishes them to the user as well,
 * so that is what a repeat is qualified with; two files sharing both a title and a folder can only be
 * told apart by their file names.
 */
internal fun uniqueDisplayNames(titles: Map<String, String>): Map<String, String> {
    val duplicated = titles.values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    return titles.mapValues { (path, title) ->
        if (title !in duplicated) return@mapValues title
        val folder = path.substringBeforeLast('/', "")
        if (folder.isEmpty()) "$title  ($path)" else "$title  ($folder)"
    }
}
