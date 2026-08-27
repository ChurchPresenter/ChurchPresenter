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
 *  - the operator's own name for the module, when they have set one, wins over everything below:
 *    naming it is the point of the rename, and a module whose header the church disagrees with is
 *    exactly the case the override exists for;
 *  - a `##Title:` header names the translation, whether the value is separated by a tab (what the
 *    converter writes) or a space (what plenty of hand-made modules have);
 *  - **no** `##Title:` line, or a file that cannot be read at all, falls back to the file's stem;
 *  - a `##Title:` line with nothing after it stays blank, which is what the full loader does too —
 *    an empty title is a module that says its name is nothing, not a module that never said;
 *  - a title buried past the header block is not looked for. These callers scan whole folders, and
 *    reading through a directory of multi-megabyte modules would stall the picker doing it.
 */
fun readTranslationTitle(file: File, override: String? = null): String {
    if (!override.isNullOrBlank()) return override
    return Bible.readTranslationSummary(file.absolutePath, maxLines = Bible.TITLE_SCAN_LINE_LIMIT)?.title
        ?: file.nameWithoutExtension
}

/**
 * The abbreviation a module would be labelled with if nobody renamed it.
 *
 * The same derivation the loader applies, exposed so the rename fields can show it: the operator is
 * being asked to overrule a value they have never seen written down anywhere, since it is computed
 * from the title rather than stored, and a blank box has to say what it is leaving in place.
 */
fun defaultTranslationAbbreviation(title: String, fileName: String): String =
    extractBibleAbbreviation(title, fileName)

/**
 * Each file's own title (relative to [directory]), keyed by file name and not yet qualified.
 *
 * This is the half that costs a header read per file, and it is the half that does not change when
 * the operator renames a module — so a caller that keeps a folder listing around holds *this* and
 * runs [displayNamesFor] over it on every rename, rather than walking the folder again for a name
 * it already has.
 *
 * Reads one header per file, so keep this off the composition thread — the Bible tab reaches it
 * through `produceState`, the settings and dictionary view models from their own coroutines.
 */
fun bibleTitles(directory: String, files: List<String>): Map<String, String> {
    if (directory.isEmpty()) return emptyMap()
    return files.associateWith { readTranslationTitle(File(directory, it)) }
}

/**
 * Names every file in [files] (relative to [directory]) for a picker, keyed by file name.
 *
 * [overrides] is the operator's own name per file name, from the Bible settings; a file with no
 * entry is named by its header as before.
 */
fun bibleDisplayNames(
    directory: String,
    files: List<String>,
    overrides: Map<String, String> = emptyMap(),
): Map<String, String> = displayNamesFor(bibleTitles(directory, files), overrides)

/**
 * Applies [overrides] over already-read [titles] and qualifies whatever repeats.
 *
 * A renamed module goes through the uniqueness pass like any other: renaming two modules to one
 * name has to be told apart in the picker exactly as two matching headers do, and typing the same
 * name twice is a good deal easier than two modules happening to agree on their own.
 */
fun displayNamesFor(
    titles: Map<String, String>,
    overrides: Map<String, String> = emptyMap(),
): Map<String, String> = uniqueDisplayNames(
    titles.mapValues { (fileName, title) -> overrides[fileName]?.takeIf { it.isNotBlank() } ?: title },
)

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
