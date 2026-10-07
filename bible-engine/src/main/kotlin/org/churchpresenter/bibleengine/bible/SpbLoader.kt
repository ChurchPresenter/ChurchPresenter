package org.churchpresenter.bibleengine.bible

import org.churchpresenter.bibleengine.Config
import java.io.File
import java.io.IOException

/** Fewer verses than this is not a bible. */
private const val MIN_VERSES = 10

/** How far into a file the `##Abbreviation:` header is looked for before a full parse. */
private const val HEADER_PEEK_LINES = 20

/** A book row is `number \t name \t chapters`. */
private const val BOOK_ROW_FIELDS = 3

/** A verse row is `code \t book \t chapter \t verse \t text`. */
private const val VERSE_ROW_FIELDS = 5

/** How many verses the script sniff samples. */
private const val SCRIPT_SAMPLE_VERSES = 200

private const val SEPARATOR = "-----"

object SpbLoader {

    private val LXX_LANGUAGES = setOf(
        "RUS", "UKR", "BEL", "SRP", "SCR", "BUL", "MKD", "ROM", "RUM", "MOL",
        "KAT", "GEO", "GRE", "GRC", "ELL", "AMH", "ETH", "COP", "SYR", "ARC",
    )

    fun numberingFor(language: String): String =
        if (language.uppercase() in LXX_LANGUAGES) "lxx" else "hebrew"

    fun loadAll(): List<EngineTranslation> {
        val root = File(Config.bibleRoot)
        if (!root.exists()) {
            System.err.println("Bible root not found: ${Config.bibleRoot}")
            return emptyList()
        }
        val spbFiles = root.walk()
            .filter { it.isFile && it.name.endsWith(".spb") }
            .toList()
            .sortedBy { it.name }

        val seenIds = mutableMapOf<String, Int>()
        return spbFiles.mapNotNull { file -> parseUsable(file, seenIds) }
    }

    /**
     * Loads only the named SPB files (ChurchPresenter's primary + secondary bibles), in the given
     * order. Falls back to [loadAll] when the list is empty.
     *
     * Each name is either a path relative to the bible root (`ENG/King James/kjv.spb` — what CP
     * stores now that it scans subfolders) or a bare file name (what it stored before, and what
     * still identifies a file sitting at the root). Relative paths are matched FIRST: a collection
     * can hold two files of the same name in different folders, and resolving those by name alone
     * silently picks whichever the walk happened to reach last.
     */
    fun loadSelected(fileNames: List<String>): List<EngineTranslation> {
        if (fileNames.isEmpty()) return loadAll()
        val root = File(Config.bibleRoot)
        if (!root.exists()) {
            System.err.println("Bible root not found: ${Config.bibleRoot}")
            return emptyList()
        }
        val spbFiles = root.walk().filter { it.isFile && it.name.endsWith(".spb") }.toList()
        val byPath = spbFiles.associateBy { it.toRelativeString(root).replace('\\', '/') }
        val byName = spbFiles.associateBy { it.name }
        val seenIds = mutableMapOf<String, Int>()
        return fileNames.distinct().mapNotNull { name ->
            (byPath[name.replace('\\', '/')] ?: byName[name])?.let { file -> parseUsable(file, seenIds) }
        }
    }

    fun loadDefaults(): List<EngineTranslation> {
        // Empty allow-list means "load everything available" (matches DetectionEngine's index
        // semantics); otherwise loadDefaults would return nothing and the engine would have no data.
        if (Config.defaultTranslations.isEmpty()) return loadAll()

        val root = File(Config.bibleRoot)
        if (!root.exists()) return emptyList()

        val spbFiles = root.walk()
            .filter { it.isFile && it.name.endsWith(".spb") }
            .toList()
            .sortedBy { it.name }

        val targets = Config.defaultTranslations.toSet()
        val seenIds = mutableMapOf<String, Int>()
        val results = mutableListOf<EngineTranslation>()

        for (file in spbFiles) {
            val abbr = headerAbbreviation(file)
            if (abbr.isNullOrBlank()) continue

            // Peek at the id before committing to a full parse. Snapshot the counts first so the
            // parse below re-derives the very same id (deriveId mutates seenIds).
            val before = seenIds.toMutableMap()
            val id = deriveId(file.name, abbr, seenIds)
            if (id in targets) parseUsable(file, before)?.let(results::add)
        }
        return results
    }

    /** The `##Abbreviation:` header from the top of [file], or null when it has none there. */
    private fun headerAbbreviation(file: File): String? = file.useLines(Charsets.UTF_8) { lines ->
        lines.take(HEADER_PEEK_LINES).firstOrNull { it.startsWith("##Abbreviation:") }
            ?.removePrefix("##Abbreviation:")?.trim()
    }

    /** [file] parsed, when it is a bible with enough verses to be one; a read failure is warned and skipped. */
    private fun parseUsable(file: File, seenIds: MutableMap<String, Int>): EngineTranslation? =
        try {
            SpbParse.parseFile(file, seenIds)?.takeIf { it.byBCV.size >= MIN_VERSES }
        } catch (e: IOException) {
            System.err.println("Warning: failed to parse ${file.name}: ${e.message}")
            null
        }

    // Fast header-only scan — reads each SPB file only until the "-----" separator.
    // Returns (bookNum, bookName) pairs from every SPB file found, deduplicated.
    fun scanAllBookManifests(): List<Pair<Int, String>> {
        val root = File(Config.bibleRoot)
        if (!root.exists()) return emptyList()
        val seen = mutableSetOf<Pair<Int, String>>()
        root.walk()
            .filter { it.isFile && it.name.endsWith(".spb") }
            .forEach { file ->
                runCatching {
                    file.useLines(Charsets.UTF_8) { lines ->
                        lines.takeWhile { it.trimEnd() != SEPARATOR }
                            .mapNotNull(SpbParse::manifestEntry)
                            .forEach { seen.add(it) }
                    }
                }
            }
        return seen.toList()
    }

    /**
     * The translation id every consumer keys on: `<LANG>_<sanitizedAbbreviation>`, with `_2`/`_3`
     * suffixes when two files claim the same abbreviation. [seenIds] carries the per-scan occurrence
     * counts and is mutated. Shared with the version corpus so its ids line up with
     * [Config.loadedBibles] and the detection-log rows.
     */
    internal fun deriveId(fileName: String, abbreviation: String, seenIds: MutableMap<String, Int>): String {
        val sanitized = abbreviation.replace(Regex("[^A-Za-z0-9]"), "")
        val baseId = "${extractLanguage(fileName)}_$sanitized"
        val count = seenIds.getOrDefault(baseId, 0)
        seenIds[baseId] = count + 1
        return if (count == 0) baseId else "${baseId}_${count + 1}"
    }
}

private fun extractLanguage(filename: String): String =
    filename.substringBefore("_").uppercase()

/** The `##` headers and the book manifest above an SPB file's separator, as they are read. */
private class SpbHeader {
    var title = ""
    var abbreviation = ""
    val books = mutableListOf<EngineBook>()

    /** Takes in one header line; true once it is the separator, after which verse rows follow. */
    fun read(line: String): Boolean {
        when {
            line.startsWith("##Title:") ->
                title = line.removePrefix("##Title:").trim()
            line.startsWith("##Abbreviation:") ->
                abbreviation = line.removePrefix("##Abbreviation:").trim()
            line.startsWith("##") -> Unit
            line.trimEnd() == SEPARATOR -> return true
            !line.startsWith(" ") && !line.startsWith("\t") && line.isNotBlank() ->
                SpbParse.bookRow(line)?.let(books::add)
        }
        return false
    }
}

/** Reading one SPB file into an [EngineTranslation]. */
private object SpbParse {

    fun parseFile(file: File, seenIds: MutableMap<String, Int>): EngineTranslation? {
        val header = SpbHeader()
        val verses = mutableListOf<EngineVerse>()
        var pastSeparator = false
        for (line in file.readLines(Charsets.UTF_8)) {
            if (!pastSeparator) pastSeparator = header.read(line)
            else verseRow(line)?.let(verses::add)
        }
        return if (header.abbreviation.isBlank()) null else translationOf(file, header, verses, seenIds)
    }

    /** A manifest row's book: `number \t name \t chapters`, or null when it is not one. */
    fun bookRow(line: String): EngineBook? {
        val parts = line.split("\t")
        if (parts.size < BOOK_ROW_FIELDS) return null
        val num = parts[0].trim().toIntOrNull()
        val chapCount = parts[2].trim().toIntOrNull()
        return if (num != null && chapCount != null && parts[1].isNotBlank()) {
            EngineBook(num, parts[1].trim(), chapCount)
        } else {
            null
        }
    }

    /** A verse row: `code \t book \t chapter \t verse \t text`. Verse 0 is a section header. */
    private fun verseRow(line: String): EngineVerse? {
        if (!line.startsWith("B")) return null
        val parts = line.split("\t", limit = VERSE_ROW_FIELDS)
        if (parts.size < VERSE_ROW_FIELDS) return null
        val bookNum = parts[1].toIntOrNull()
        val chapter = parts[2].toIntOrNull()
        val verse = parts[3].toIntOrNull()
        return if (bookNum == null || chapter == null || verse == null) {
            null
        } else {
            EngineVerse(parts[0], bookNum, chapter, verse, parts.last(), verse == 0)
        }
    }

    /** One header-scan line's (bookNum, bookName), or null when the line is not a book row. */
    fun manifestEntry(line: String): Pair<Int, String>? {
        val indented = line.startsWith(" ") || line.startsWith("\t")
        if (line.startsWith("##") || indented || line.isBlank()) return null
        val parts = line.split("\t")
        if (parts.size < 2) return null
        val num = parts[0].trim().toIntOrNull()
        val name = parts[1].trim()
        return if (num == null || name.isBlank()) null else num to name
    }

    private fun translationOf(
        file: File,
        header: SpbHeader,
        verses: List<EngineVerse>,
        seenIds: MutableMap<String, Int>,
    ): EngineTranslation {
        val lang = extractLanguage(file.name)
        val id = SpbLoader.deriveId(file.name, header.abbreviation, seenIds)

        val byBCV = HashMap<Triple<Int, Int, Int>, EngineVerse>(verses.size * 2)
        val byChapterMut = HashMap<Pair<Int, Int>, MutableList<EngineVerse>>()
        val byCode = HashMap<String, EngineVerse>(verses.size * 2)

        for (v in verses) {
            byBCV[Triple(v.bookNum, v.chapter, v.verse)] = v
            byChapterMut.getOrPut(Pair(v.bookNum, v.chapter)) { mutableListOf() }.add(v)
            byCode[v.code] = v
        }

        return EngineTranslation(
            id = id,
            title = header.title,
            abbreviation = header.abbreviation,
            language = lang,
            numbering = SpbLoader.numberingFor(lang),
            script = detectScript(verses),
            books = header.books,
            byBCV = byBCV,
            byChapter = byChapterMut,
            byCode = byCode,
        )
    }

    /** Content-derived dominant script: samples the first verses' letters (see [Script]). */
    private fun detectScript(verses: List<EngineVerse>): Script {
        var latin = 0
        var cyrillic = 0
        for (v in verses.asSequence().take(SCRIPT_SAMPLE_VERSES)) {
            for (ch in v.text) {
                when {
                    ch in 'a'..'z' || ch in 'A'..'Z' -> latin++
                    ch in 'Ѐ'..'ӿ' -> cyrillic++
                }
            }
        }
        return when {
            cyrillic > latin -> Script.CYRILLIC
            latin > 0 -> Script.LATIN
            else -> Script.OTHER
        }
    }
}
