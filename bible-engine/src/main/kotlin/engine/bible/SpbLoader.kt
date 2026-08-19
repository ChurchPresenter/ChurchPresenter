package engine.bible

import engine.Config
import java.io.File

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

        val translations = mutableListOf<EngineTranslation>()
        val seenIds = mutableMapOf<String, Int>()

        for (file in spbFiles) {
            try {
                val t = parseFile(file, seenIds) ?: continue
                if (t.byBCV.size < 10) continue
                translations.add(t)
            } catch (e: Exception) {
                System.err.println("Warning: failed to parse ${file.name}: ${e.message}")
            }
        }
        return translations
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
        val translations = mutableListOf<EngineTranslation>()
        for (name in fileNames.distinct()) {
            val file = byPath[name.replace('\\', '/')] ?: byName[name] ?: continue
            try {
                val t = parseFile(file, seenIds) ?: continue
                if (t.byBCV.size >= 10) translations.add(t)
            } catch (e: Exception) {
                System.err.println("Warning: failed to parse ${file.name}: ${e.message}")
            }
        }
        return translations
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
            val abbr = file.useLines(Charsets.UTF_8) { lines ->
                lines.take(20).firstOrNull { it.startsWith("##Abbreviation:") }
                    ?.removePrefix("##Abbreviation:")?.trim()
            }
            if (abbr.isNullOrBlank()) continue

            // Peek at the id before committing to a full parse. Snapshot the counts first so the
            // parse below re-derives the very same id (deriveId mutates seenIds).
            val before = seenIds.toMutableMap()
            val id = deriveId(file.name, abbr, seenIds)
            if (id !in targets) continue
            val parseSeenIds = before
            try {
                val t = parseFile(file, parseSeenIds) ?: continue
                if (t.byBCV.size >= 10) results.add(t)
            } catch (e: Exception) {
                System.err.println("Warning: failed to parse ${file.name}: ${e.message}")
            }
        }
        return results
    }

    private fun parseFile(file: File, seenIds: MutableMap<String, Int>): EngineTranslation? {
        val lang = extractLanguage(file.name)
        var title = ""
        var abbreviation = ""
        val books = mutableListOf<EngineBook>()
        val verses = mutableListOf<EngineVerse>()

        val lines = file.readLines(Charsets.UTF_8)
        var pastSeparator = false

        for (line in lines) {
            if (!pastSeparator) {
                when {
                    line.startsWith("##Title:") ->
                        title = line.removePrefix("##Title:").trim()
                    line.startsWith("##Abbreviation:") ->
                        abbreviation = line.removePrefix("##Abbreviation:").trim()
                    line.startsWith("##") -> Unit
                    line.trimEnd() == "-----" -> pastSeparator = true
                    !line.startsWith(" ") && !line.startsWith("\t") && line.isNotBlank() -> {
                        val parts = line.split("\t")
                        if (parts.size >= 3) {
                            val num = parts[0].trim().toIntOrNull()
                            val chapCount = parts[2].trim().toIntOrNull()
                            if (num != null && chapCount != null && parts[1].isNotBlank()) {
                                books.add(EngineBook(num, parts[1].trim(), chapCount))
                            }
                        }
                    }
                }
            } else {
                if (!line.startsWith("B")) continue
                val parts = line.split("\t", limit = 5)
                if (parts.size < 5) continue
                val code = parts[0]
                val bookNum = parts[1].toIntOrNull() ?: continue
                val chapter = parts[2].toIntOrNull() ?: continue
                val verse = parts[3].toIntOrNull() ?: continue
                val text = parts[4]
                verses.add(EngineVerse(code, bookNum, chapter, verse, text, verse == 0))
            }
        }

        if (abbreviation.isBlank()) return null

        val id = deriveId(file.name, abbreviation, seenIds)

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
            title = title,
            abbreviation = abbreviation,
            language = lang,
            numbering = numberingFor(lang),
            script = detectScript(verses),
            books = books,
            byBCV = byBCV,
            byChapter = byChapterMut,
            byCode = byCode,
        )
    }

    /** Content-derived dominant script: samples the first verses' letters (see [Script]). */
    private fun detectScript(verses: List<EngineVerse>): Script {
        var latin = 0
        var cyrillic = 0
        for (v in verses.asSequence().take(200)) {
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
                        for (line in lines) {
                            if (line.trimEnd() == "-----") return@useLines
                            if (line.startsWith("##") || line.startsWith(" ") ||
                                line.startsWith("\t") || line.isBlank()) continue
                            val parts = line.split("\t")
                            if (parts.size >= 2) {
                                val num = parts[0].trim().toIntOrNull() ?: continue
                                val name = parts[1].trim()
                                if (name.isNotBlank()) seen.add(num to name)
                            }
                        }
                    }
                }
            }
        return seen.toList()
    }

    private fun extractLanguage(filename: String): String =
        filename.substringBefore("_").uppercase()

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
