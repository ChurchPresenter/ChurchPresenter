package converter

import java.io.File
import java.nio.charset.Charset

data class SongInfo(
    val file: File,
    val title: String,
    val lyricsText: String,
    /** Section headers found in [Primary], e.g. ["Verse 1", "Chorus", "Verse 2"] */
    val sections: List<String> = emptyList(),
    /** Verse/section name → lyrics text */
    val verses: Map<String, String> = emptyMap()
)

data class DuplicateGroup(
    val songs: List<SongInfo>,
    val reason: String,
    /** Pairwise similarity of each song to the first song in the group (1.0 for the first). */
    val similarities: List<Double> = emptyList()
)

object DuplicateFinder {

    fun findDuplicates(directory: File, threshold: Double = 0.9, matchByNumber: Boolean = false, matchByTitle: Boolean = true): List<DuplicateGroup> {
        val songs = scanSongs(directory)
        if (songs.size < 2) return emptyList()

        val groups = mutableListOf<DuplicateGroup>()
        val assigned = mutableSetOf<Int>()

        // Pre-compute normalized line sets for all songs (once)
        val allLines = songs.map { normalizeLines(it) }

        // Pass 1 (optional): same song number across different parent folders
        if (matchByNumber) {
            val byNumberOnly = songs.withIndex()
                .filter { extractSongNumber(it.value.file.name) != null }
                .groupBy { extractSongNumber(it.value.file.name)!! }
            for ((_, entries) in byNumberOnly) {
                val distinctFolders = entries.map { it.value.file.parentFile.canonicalPath }.distinct()
                if (entries.size > 1 && distinctFolders.size > 1) {
                    val firstIdx = entries.first().index
                    val similar = entries.filter { e ->
                        e.index == firstIdx || lineSimilarity(allLines[firstIdx], allLines[e.index]) >= threshold
                    }
                    if (similar.size > 1) {
                        val sims = similar.map { e -> lineSimilarity(allLines[firstIdx], allLines[e.index]) }
                        groups.add(DuplicateGroup(similar.map { it.value }, "Same song number", sims))
                        similar.forEach { assigned.add(it.index) }
                    }
                }
            }
        }

        // Pass 2 (optional): exact title matches (by file content title AND by filename after stripping numbers)
        if (matchByTitle) {
            val remainingAfterNumbers = songs.withIndex().filter { it.index !in assigned }
            // Group by normalized content title OR by normalized filename (after stripping leading numbers)
            val titleGroups = mutableMapOf<String, MutableList<IndexedValue<SongInfo>>>()
            for (entry in remainingAfterNumbers) {
                val contentTitle = normalizeText(entry.value.title)
                val fileTitle = normalizeText(stripLeadingNumber(entry.value.file.nameWithoutExtension))
                titleGroups.getOrPut(contentTitle) { mutableListOf() }.add(entry)
                if (fileTitle != contentTitle) {
                    titleGroups.getOrPut(fileTitle) { mutableListOf() }.add(entry)
                }
            }
            for ((_, entries) in titleGroups) {
                // Deduplicate entries (a song may appear via both content title and filename)
                val unique = entries.distinctBy { it.index }.filter { it.index !in assigned }
                if (unique.size > 1) {
                    val firstIdx = unique.first().index
                    // Include songs whose content is similar to the first.
                    // Use max of line-level and text-level similarity to handle
                    // different line breaks of the same song.
                    val similar = unique.filter { e ->
                        if (e.index == firstIdx) return@filter true
                        val lineSim = lineSimilarity(allLines[firstIdx], allLines[e.index])
                        val textSim = textSimilarity(songs[firstIdx], songs[e.index])
                        maxOf(lineSim, textSim) >= threshold * 0.5
                    }
                    if (similar.size > 1) {
                        val sims = similar.map { e ->
                            val lineSim = lineSimilarity(allLines[firstIdx], allLines[e.index])
                            val textSim = textSimilarity(songs[firstIdx], songs[e.index])
                            maxOf(lineSim, textSim)
                        }
                        groups.add(DuplicateGroup(similar.map { it.value }, "Same title", sims))
                        similar.forEach { assigned.add(it.index) }
                    }
                }
            }
        }

        // Pass 3: line similarity via inverted index (fast candidate finding)
        val remaining = songs.withIndex().filter { it.index !in assigned }
        if (remaining.size >= 2) {
            // Build inverted index: normalized line → set of indices into 'remaining'
            val invertedIndex = mutableMapOf<String, MutableSet<Int>>()
            for ((ri, indexed) in remaining.withIndex()) {
                for (line in allLines[indexed.index]) {
                    invertedIndex.getOrPut(line) { mutableSetOf() }.add(ri)
                }
            }

            // Find candidate pairs: songs sharing >= 2 lines
            val candidatePairs = mutableMapOf<Long, Int>() // packed pair key → shared line count
            for ((_, songIndices) in invertedIndex) {
                if (songIndices.size < 2 || songIndices.size > 50) continue // skip very common lines
                val list = songIndices.toList()
                for (a in list.indices) {
                    for (b in a + 1 until list.size) {
                        val key = packPair(list[a], list[b])
                        candidatePairs[key] = (candidatePairs[key] ?: 0) + 1
                    }
                }
            }

            // Score candidates that share >= 2 lines
            val scoredPairs = mutableMapOf<Long, Double>() // pair key → similarity
            for ((key, sharedCount) in candidatePairs) {
                if (sharedCount < 2) continue
                val (ri, rj) = unpackPair(key)
                val si = remaining[ri].index
                val sj = remaining[rj].index
                if (si in assigned || sj in assigned) continue
                val sim = lineSimilarity(allLines[si], allLines[sj])
                if (sim >= threshold) {
                    scoredPairs[key] = sim
                }
            }

            // Group scored pairs
            for ((key, sim) in scoredPairs.entries.sortedByDescending { it.value }) {
                val (ri, rj) = unpackPair(key)
                val si = remaining[ri].index
                val sj = remaining[rj].index
                if (si in assigned && sj in assigned) continue

                // Find or create group
                val existingGroup = groups.indexOfFirst { g ->
                    g.reason == "Similar lyrics" && g.songs.any { it === songs[si] || it === songs[sj] }
                }
                if (existingGroup >= 0) {
                    val g = groups[existingGroup]
                    val newSongs = mutableListOf<SongInfo>()
                    val newSims = mutableListOf<Double>()
                    newSongs.addAll(g.songs)
                    newSims.addAll(g.similarities)
                    if (si !in assigned) {
                        newSongs.add(songs[si]); newSims.add(sim); assigned.add(si)
                    }
                    if (sj !in assigned) {
                        newSongs.add(songs[sj]); newSims.add(sim); assigned.add(sj)
                    }
                    groups[existingGroup] = DuplicateGroup(newSongs, "Similar lyrics", newSims)
                } else if (si !in assigned || sj !in assigned) {
                    val firstIdx = if (si !in assigned) si else sj
                    val secondIdx = if (si !in assigned) sj else si
                    val firstSim = lineSimilarity(allLines[firstIdx], allLines[firstIdx])
                    groups.add(DuplicateGroup(
                        listOf(songs[firstIdx], songs[secondIdx]),
                        "Similar lyrics",
                        listOf(firstSim, sim)
                    ))
                    assigned.add(si)
                    assigned.add(sj)
                }
            }
        }

        return groups.sortedByDescending { it.songs.size }
    }

    fun scanSongs(directory: File): List<SongInfo> {
        return directory.walkTopDown()
            .filter { it.isFile && it.extension.equals("song", ignoreCase = true) }
            .mapNotNull { file ->
                try {
                    parseSong(file)
                } catch (_: Exception) {
                    null
                }
            }
            .toList()
    }

    /**
     * Given duplicate groups and a "keep" folder, returns the list of files
     * that should be deleted (duplicates NOT inside the keep folder).
     * If a group has no song in the keep folder, nothing from that group is deleted.
     */
    fun resolveDeletes(groups: List<DuplicateGroup>, keepFolder: File): List<File> {
        val keepPath = keepFolder.canonicalPath
        return groups.flatMap { group ->
            val kept = group.songs.filter { it.file.canonicalPath.startsWith(keepPath) }
            if (kept.isEmpty()) {
                emptyList()
            } else {
                val outsiders = group.songs.filter { !it.file.canonicalPath.startsWith(keepPath) }.map { it.file }
                val extraInsiders = kept.drop(1).map { it.file }
                outsiders + extraInsiders
            }
        }
    }

    // =========================================================================
    // Line-level similarity
    // =========================================================================

    /** Extract unique normalized lyric lines from a song (no structural markers). */
    private fun normalizeLines(song: SongInfo): Set<String> {
        return song.lyricsText.lines()
            .map { normalizeText(it) }
            .filter { it.length >= 3 } // skip very short lines
            .toSet()
    }

    /** Text-level similarity (ignoring line breaks). Joins all lyrics into a single
     *  normalized string and compares via bigram dice. Useful when the same song
     *  is formatted with different line breaks. */
    private fun textSimilarity(songA: SongInfo, songB: SongInfo): Double {
        val a = normalizeText(songA.lyricsText.replace('\n', ' '))
        val b = normalizeText(songB.lyricsText.replace('\n', ' '))
        return diceFromBigrams(bigrams(a), bigrams(b))
    }

    /**
     * Line-level similarity between two songs.
     * Score = matched lines (exact + fuzzy) / lines in shorter song.
     * Handles missing verses and spelling errors.
     */
    private fun lineSimilarity(linesA: Set<String>, linesB: Set<String>): Double {
        if (linesA.isEmpty() && linesB.isEmpty()) return 1.0
        if (linesA.isEmpty() || linesB.isEmpty()) return 0.0

        val shorter = if (linesA.size <= linesB.size) linesA else linesB
        val longer = if (linesA.size <= linesB.size) linesB else linesA

        // Count exact matches
        val exactMatches = shorter.intersect(longer)
        var matched = exactMatches.size

        // Fuzzy match remaining lines (handles spelling errors)
        if (matched < shorter.size) {
            val unmatchedShort = shorter - exactMatches
            val unmatchedLong = longer - exactMatches
            if (unmatchedLong.isNotEmpty()) {
                // Pre-compute bigrams for unmatched longer lines
                val longBigrams = unmatchedLong.map { Triple(it, it, bigrams(it)) }
                for (sLine in unmatchedShort) {
                    val sBi = bigrams(sLine)
                    var bestSim = 0.0
                    for ((_, lNorm, lBi) in longBigrams) {
                        val sim = diceFromBigrams(sBi, lBi)
                        if (sim > bestSim) bestSim = sim
                    }
                    if (bestSim >= 0.75) matched++
                }
            }
        }

        return matched.toDouble() / shorter.size
    }

    // =========================================================================
    // Parsing
    // =========================================================================

    /** Extract leading number from filenames like "0407 - Title.song" */
    private fun extractSongNumber(filename: String): String? {
        val match = Regex("""^(\d+)\s*-\s""").find(filename)
        return match?.groupValues?.get(1)
    }

    /** Strip leading number prefix from filename, e.g. "0407 - Title" → "Title" */
    private fun stripLeadingNumber(name: String): String {
        return name.replace(Regex("""^\d+\s*-\s*"""), "")
    }

    private fun parseSong(file: File): SongInfo {
        val content = readFileWithFallback(file)
        val lines = content.lines()

        var title = ""
        val lyricsLines = mutableListOf<String>()
        val sections = mutableListOf<String>()
        val verses = mutableMapOf<String, MutableList<String>>()
        var currentSection: String? = null
        var inFrontmatter = false
        var frontmatterDone = false
        var foundPrimary = false

        for (line in lines) {
            val trimmed = line.trim()
            if (!frontmatterDone) {
                if (trimmed == "---") {
                    inFrontmatter = !inFrontmatter
                    if (!inFrontmatter) frontmatterDone = true
                    continue
                }
                if (inFrontmatter) continue
                // No frontmatter block in this file, so parsing starts right here. This used to
                // `continue` unconditionally, which swallowed the ENTIRE file when it had no
                // `---` block — and MarkdownToSongConverter omits frontmatter whenever a song has
                // no author/composer/copyright, so its own output read back as a song with no
                // title (filename fallback) and no lyrics at all. Duplicate detection then
                // silently degraded to filename matching for exactly those files.
                frontmatterDone = true
            }
            if (trimmed.equals("[Primary]", ignoreCase = true)) {
                foundPrimary = true
                continue
            }
            if (trimmed.startsWith("title:", ignoreCase = true) && title.isEmpty()) {
                title = trimmed.substringAfter(":").trim()
                continue
            }
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                if (trimmed.equals("[Secondary]", ignoreCase = true)) break
                if (foundPrimary) {
                    currentSection = trimmed.removeSurrounding("[", "]")
                    sections.add(currentSection)
                    verses[currentSection] = mutableListOf()
                }
                continue
            }
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) continue
            if (foundPrimary && trimmed.isNotEmpty() && !isStructuralMarker(trimmed)) {
                lyricsLines.add(trimmed)
                if (currentSection != null) {
                    verses[currentSection]!!.add(trimmed)
                }
            }
        }

        if (title.isEmpty()) title = file.nameWithoutExtension
        val verseTexts = verses.mapValues { (_, vLines) -> vLines.joinToString("\n") }
        return SongInfo(file, title, lyricsLines.joinToString("\n"), sections, verseTexts)
    }

    private val structuralMarkerRegex = Regex(
        """^\{.*\}[.:]?$"""
    )
    private val bareLabelRegex = Regex(
        """^(припев|куплет|хор|вступление|окончание|бридж|кода|chorus|verse|bridge|intro|outro|refrain|coda)\s*\d*\s*[.:]?\s*$""",
        RegexOption.IGNORE_CASE
    )

    private fun isStructuralMarker(line: String): Boolean {
        return structuralMarkerRegex.matches(line) || bareLabelRegex.matches(line)
    }

    // =========================================================================
    // Homoglyph detection & fixing
    // =========================================================================

    /** Map Latin lookalike characters to Cyrillic equivalents (lowercase, for normalization). */
    private val homoglyphMap = mapOf(
        'a' to 'а', 'c' to 'с', 'e' to 'е', 'o' to 'о', 'p' to 'р',
        'x' to 'х', 'y' to 'у', 'b' to 'в', 'h' to 'н', 'k' to 'к',
        'm' to 'м', 't' to 'т'
    )

    /** Full-case map for fixing actual file content (both lower and upper). */
    private val homoglyphFixMap = mapOf(
        'a' to 'а', 'A' to 'А', 'c' to 'с', 'C' to 'С',
        'e' to 'е', 'E' to 'Е', 'o' to 'о', 'O' to 'О',
        'p' to 'р', 'P' to 'Р', 'x' to 'х', 'X' to 'Х',
        'y' to 'у', 'B' to 'В', 'H' to 'Н', 'K' to 'К',
        'M' to 'М', 'T' to 'Т'
    )

    /** Check if a line is primarily Cyrillic (more Cyrillic letters than Latin). */
    private fun isCyrillicLine(line: String): Boolean {
        val cyrillic = line.count { it in '\u0400'..'\u04FF' }
        val latin = line.count { it in 'A'..'Z' || it in 'a'..'z' }
        return cyrillic > 0 && cyrillic > latin
    }

    /** Check if a file contains mixed Latin/Cyrillic homoglyphs in Cyrillic lines. */
    fun hasHomoglyphs(file: File): Boolean {
        val content = readFileWithFallback(file)
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[") || trimmed.startsWith("{") || trimmed.startsWith("---") ||
                trimmed.startsWith("title:") || trimmed.startsWith("author:") ||
                trimmed.startsWith("composer:") || trimmed.startsWith("tune:")) continue
            if (isCyrillicLine(trimmed) && trimmed.any { it in homoglyphFixMap }) return true
        }
        return false
    }

    /** Fix homoglyphs in a file: replace Latin lookalikes with Cyrillic in lines that are
     *  primarily Cyrillic. English lines are left untouched.
     *  Returns the number of characters replaced, or 0 if no changes. */
    fun fixHomoglyphs(file: File): Int {
        val content = readFileWithFallback(file)
        val lines = content.lines()
        var totalFixed = 0
        val fixedLines = lines.map { line ->
            val trimmed = line.trim()
            // Skip structural/metadata lines
            if (trimmed.startsWith("[") || trimmed.startsWith("{") || trimmed.startsWith("---") ||
                trimmed.startsWith("title:") || trimmed.startsWith("author:") ||
                trimmed.startsWith("composer:") || trimmed.startsWith("tune:")) {
                line
            } else if (!isCyrillicLine(trimmed)) {
                // Not a Cyrillic line — leave it alone
                line
            } else {
                val sb = StringBuilder(line.length)
                for (ch in line) {
                    val replacement = homoglyphFixMap[ch]
                    if (replacement != null) {
                        sb.append(replacement)
                        totalFixed++
                    } else {
                        sb.append(ch)
                    }
                }
                sb.toString()
            }
        }
        if (totalFixed > 0) {
            file.writeText(fixedLines.joinToString("\n"), Charsets.UTF_8)
        }
        return totalFixed
    }

    /** Scan directory for files with homoglyphs. */
    fun findHomoglyphFiles(directory: File): List<File> {
        return directory.walkTopDown()
            .filter { it.isFile && it.extension.equals("song", ignoreCase = true) }
            .filter { hasHomoglyphs(it) }
            .toList()
    }

    private fun normalizeText(text: String): String {
        val lower = text.lowercase()
        val sb = StringBuilder(lower.length)
        for (ch in lower) {
            sb.append(homoglyphMap[ch] ?: ch)
        }
        return sb.toString()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun bigrams(text: String): Map<String, Int> {
        if (text.length < 2) return emptyMap()
        val map = mutableMapOf<String, Int>()
        for (i in 0 until text.length - 1) {
            val bg = text.substring(i, i + 2)
            map[bg] = (map[bg] ?: 0) + 1
        }
        return map
    }

    private fun diceFromBigrams(a: Map<String, Int>, b: Map<String, Int>): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.keys.sumOf { minOf(a[it] ?: 0, b[it] ?: 0) }
        return (2.0 * intersection) / (a.values.sum() + b.values.sum())
    }

    /** Bigram-based similarity — public for use in UI. */
    internal fun similarity(a: String, b: String): Double {
        val na = normalizeText(a)
        val nb = normalizeText(b)
        return diceFromBigrams(bigrams(na), bigrams(nb))
    }

    // =========================================================================
    // Pair packing for inverted index
    // =========================================================================

    private fun packPair(a: Int, b: Int): Long {
        val lo = minOf(a, b)
        val hi = maxOf(a, b)
        return lo.toLong() shl 32 or hi.toLong()
    }

    private fun unpackPair(key: Long): Pair<Int, Int> {
        return Pair((key shr 32).toInt(), (key and 0xFFFFFFFFL).toInt())
    }

    // =========================================================================
    // File I/O
    // =========================================================================

    internal fun readFileWithFallback(file: File): String {
        return try {
            val bytes = file.readBytes()
            val content = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
                String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            } else {
                String(bytes, Charsets.UTF_8)
            }
            if (content.contains('\uFFFD')) String(bytes, Charset.forName("windows-1251")) else content
        } catch (_: Exception) {
            file.readText(Charset.forName("windows-1251"))
        }
    }
}
