package org.churchpresenter.converter.library

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

// Split into one small function per step, which is what keeps the readers below within the
// complexity and nesting limits. Splitting the object itself would scatter one file format across
// several files instead.
@Suppress("TooManyFunctions")
object DuplicateFinder {

    /** A line in more than this many songs says nothing about any pair of them. */
    private const val MAX_SONGS_SHARING_A_LINE = 50

    /** Shorter than this and a line is "Oh" or "Amen" rather than something to match on. */
    private const val MIN_COMPARABLE_LINE_LENGTH = 3

    /** How alike two lines must be to count as the same one typed differently. */
    private const val FUZZY_LINE_MATCH = 0.75

    /** Fewer lines than this in common and two songs are not candidates for comparison at all. */
    private const val MIN_SHARED_LINES = 2

    /** The reason a group carries when it was found by comparing lyrics rather than names. */
    private const val SIMILAR_LYRICS = "Similar lyrics"

    /** Two song indices packed into one Long, so a pair can key a map without allocating. */
    private const val PAIR_SHIFT = 32
    private const val PAIR_LOW_MASK = 0xFFFFFFFFL

    fun findDuplicates(
        directory: File,
        threshold: Double = 0.9,
        matchByNumber: Boolean = false,
        matchByTitle: Boolean = true,
    ): List<DuplicateGroup> {
        val songs = scanSongs(directory)
        if (songs.size < 2) return emptyList()

        val matcher = Matcher(songs, threshold)
        if (matchByNumber) matcher.byNumber()
        if (matchByTitle) matcher.byTitle()
        matcher.byContent()
        return matcher.groups()
    }

    /**
     * One run over a library, in three passes that each claim the songs they group.
     *
     * The passes run in order of how sure they are: the same number in two songbooks, then the same
     * title, then lyrics alike enough to be the same song under another name. A song already
     * claimed is not offered to a later pass, so each song is reported in exactly one group.
     */
    private class Matcher(private val songs: List<SongInfo>, private val threshold: Double) {
        /** Normalised line sets, computed once: every pass compares against them. */
        private val allLines = songs.map { normalizeLines(it) }
        private val groups = mutableListOf<DuplicateGroup>()
        private val assigned = mutableSetOf<Int>()

        fun groups(): List<DuplicateGroup> = groups.sortedByDescending { it.songs.size }

        /** Pass 1: the same song number in two different songbooks, if the lyrics agree. */
        fun byNumber() {
            val byNumber = songs.withIndex()
                .mapNotNull { entry -> extractSongNumber(entry.value.file.name)?.let { it to entry } }
                .groupBy({ it.first }, { it.second })

            for (entries in byNumber.values) {
                val folders = entries.map { it.value.file.parentFile.canonicalPath }.distinct()
                if (entries.size < 2 || folders.size < 2) continue
                val first = entries.first().index
                val similar = entries.filter {
                    it.index == first || lineSimilarity(allLines[first], allLines[it.index]) >= threshold
                }
                claim(similar, "Same song number") { lineSimilarity(allLines[first], allLines[it.index]) }
            }
        }

        /** Pass 2: the same title, whether the file or the song itself carries it. */
        fun byTitle() {
            val byTitle = mutableMapOf<String, MutableList<IndexedValue<SongInfo>>>()
            for (entry in songs.withIndex().filter { it.index !in assigned }) {
                val contentTitle = normalizeText(entry.value.title)
                val fileTitle = normalizeText(stripLeadingNumber(entry.value.file.nameWithoutExtension))
                byTitle.getOrPut(contentTitle) { mutableListOf() }.add(entry)
                if (fileTitle != contentTitle) byTitle.getOrPut(fileTitle) { mutableListOf() }.add(entry)
            }

            for (entries in byTitle.values) {
                // A song reaches this twice when its file and its content agree on the title.
                val unique = entries.distinctBy { it.index }.filter { it.index !in assigned }
                if (unique.size < 2) continue
                val first = unique.first().index
                // Half the threshold, and the greater of the two measures: a title match is
                // evidence in itself, and the same song laid out with different line breaks scores
                // badly line by line while scoring well as one run of text.
                val similar = unique.filter { it.index == first || titleMatchScore(first, it.index) >= threshold / 2 }
                claim(similar, "Same title") { titleMatchScore(first, it.index) }
            }
        }

        private fun titleMatchScore(first: Int, other: Int): Double =
            maxOf(lineSimilarity(allLines[first], allLines[other]), textSimilarity(songs[first], songs[other]))

        /** Records [similar] as a group, if it is one, and takes those songs out of later passes. */
        private fun claim(
            similar: List<IndexedValue<SongInfo>>,
            reason: String,
            score: (IndexedValue<SongInfo>) -> Double,
        ) {
            if (similar.size < 2) return
            groups.add(DuplicateGroup(similar.map { it.value }, reason, similar.map(score)))
            similar.forEach { assigned.add(it.index) }
        }

        /** Pass 3: songs whose lyrics are alike, whatever they are called. */
        fun byContent() {
            val remaining = songs.withIndex().filter { it.index !in assigned }
            if (remaining.size < 2) return
            val scored = scoreCandidates(remaining)
            for ((key, sim) in scored.entries.sortedByDescending { it.value }) {
                val (ri, rj) = unpackPair(key)
                group(remaining[ri].index, remaining[rj].index, sim)
            }
        }

        /**
         * Every pair of [remaining] songs sharing at least two lines, scored.
         *
         * The inverted index is what keeps this from comparing every song with every other: a line
         * appearing in more than [MAX_SONGS_SHARING_A_LINE] songs is "Hallelujah" and says nothing
         * about any pair of them, so it is dropped rather than pairing them all.
         */
        private fun scoreCandidates(remaining: List<IndexedValue<SongInfo>>): Map<Long, Double> {
            val byLine = mutableMapOf<String, MutableSet<Int>>()
            for ((position, indexed) in remaining.withIndex()) {
                for (line in allLines[indexed.index]) byLine.getOrPut(line) { mutableSetOf() }.add(position)
            }

            val shared = mutableMapOf<Long, Int>()
            for (positions in byLine.values) {
                if (positions.size < 2 || positions.size > MAX_SONGS_SHARING_A_LINE) continue
                val list = positions.toList()
                for (a in list.indices) {
                    for (b in a + 1 until list.size) {
                        val key = packPair(list[a], list[b])
                        shared[key] = (shared[key] ?: 0) + 1
                    }
                }
            }

            return shared.filterValues { it >= MIN_SHARED_LINES }
                .mapNotNull { (key, _) ->
                    val (ri, rj) = unpackPair(key)
                    val si = remaining[ri].index
                    val sj = remaining[rj].index
                    if (si in assigned || sj in assigned) return@mapNotNull null
                    lineSimilarity(allLines[si], allLines[sj]).takeIf { it >= threshold }?.let { key to it }
                }
                .toMap()
        }

        /** Puts a scored pair into the group one of them is already in, or starts one for both. */
        private fun group(si: Int, sj: Int, sim: Double) {
            if (si in assigned && sj in assigned) return
            val existing = groups.indexOfFirst { g ->
                g.reason == SIMILAR_LYRICS && g.songs.any { it === songs[si] || it === songs[sj] }
            }
            if (existing >= 0) {
                extend(existing, si, sj, sim)
                return
            }
            val first = if (si !in assigned) si else sj
            val second = if (si !in assigned) sj else si
            groups.add(
                DuplicateGroup(
                    listOf(songs[first], songs[second]),
                    SIMILAR_LYRICS,
                    // The first song is the one the rest of the group is scored against.
                    listOf(lineSimilarity(allLines[first], allLines[first]), sim),
                )
            )
            assigned.add(si)
            assigned.add(sj)
        }

        private fun extend(groupIndex: Int, si: Int, sj: Int, sim: Double) {
            val existing = groups[groupIndex]
            val newSongs = existing.songs.toMutableList()
            val newSims = existing.similarities.toMutableList()
            for (index in listOf(si, sj)) {
                if (assigned.add(index)) {
                    newSongs.add(songs[index])
                    newSims.add(sim)
                }
            }
            groups[groupIndex] = DuplicateGroup(newSongs, SIMILAR_LYRICS, newSims)
        }
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
            .filter { it.length >= MIN_COMPARABLE_LINE_LENGTH }
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

        val exactMatches = shorter.intersect(longer)
        val fuzzy = fuzzyMatches(shorter - exactMatches, longer - exactMatches)
        return (exactMatches.size + fuzzy).toDouble() / shorter.size
    }

    /**
     * How many of [unmatchedShort] are the same line as one of [unmatchedLong], typed differently.
     *
     * This is what lets a song with a spelling mistake in one line still match the copy without it;
     * the bigram score has to be high enough that two different lines about the same thing do not.
     */
    private fun fuzzyMatches(unmatchedShort: Set<String>, unmatchedLong: Set<String>): Int {
        if (unmatchedShort.isEmpty() || unmatchedLong.isEmpty()) return 0
        val longBigrams = unmatchedLong.map { bigrams(it) }
        return unmatchedShort.count { line ->
            val bigrams = bigrams(line)
            longBigrams.any { diceFromBigrams(bigrams, it) >= FUZZY_LINE_MATCH }
        }
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
        val reader = SongReader()
        // Everything from the second language on belongs to another rendition of the song.
        readFileWithFallback(file).lines()
            .takeWhile { !it.trim().equals("[Secondary]", ignoreCase = true) }
            .forEach { reader.read(it) }
        return reader.songInfo(file)
    }

    /**
     * Reads one `.song` file: its title, its section names, and the lines under each of them.
     *
     * The frontmatter handling is the part with a history. It used to skip everything until a
     * `---` block closed, which swallowed the ENTIRE file when there was none — and
     * MarkdownToSongConverter omits frontmatter whenever a song has no author, composer or
     * copyright, so its own output read back as a song with no title and no lyrics at all.
     * Duplicate detection then silently degraded to filename matching for exactly those files.
     */
    private class SongReader {
        private var title = ""
        private val lyrics = mutableListOf<String>()
        private val sections = mutableListOf<String>()
        private val verses = mutableMapOf<String, MutableList<String>>()
        private var currentSection: String? = null
        private var inFrontmatter = false
        private var frontmatterDone = false
        private var foundPrimary = false

        fun read(line: String) {
            val trimmed = line.trim()
            if (!frontmatterDone && !readFrontmatter(trimmed)) return
            when {
                trimmed.equals("[Primary]", ignoreCase = true) -> foundPrimary = true
                trimmed.startsWith("title:", ignoreCase = true) && title.isEmpty() ->
                    title = trimmed.substringAfter(":").trim()
                trimmed.startsWith("[") && trimmed.endsWith("]") -> openSection(trimmed)
                // A chord line, which is not sung.
                trimmed.startsWith("{") && trimmed.endsWith("}") -> Unit
                foundPrimary && trimmed.isNotEmpty() && !isStructuralMarker(trimmed) -> addLyric(trimmed)
            }
        }

        /** Whether [trimmed] is for the caller to read; false while inside the frontmatter block. */
        private fun readFrontmatter(trimmed: String): Boolean {
            if (trimmed == "---") {
                inFrontmatter = !inFrontmatter
                if (!inFrontmatter) frontmatterDone = true
                return false
            }
            if (inFrontmatter) return false
            // No frontmatter block in this file, so parsing starts right here.
            frontmatterDone = true
            return true
        }

        private fun openSection(trimmed: String) {
            if (!foundPrimary) return
            val name = trimmed.removeSurrounding("[", "]")
            currentSection = name
            sections.add(name)
            verses[name] = mutableListOf()
        }

        private fun addLyric(trimmed: String) {
            lyrics.add(trimmed)
            currentSection?.let { verses.getValue(it).add(trimmed) }
        }

        fun songInfo(file: File): SongInfo = SongInfo(
            file = file,
            title = title.ifEmpty { file.nameWithoutExtension },
            lyricsText = lyrics.joinToString("\n"),
            sections = sections,
            verses = verses.mapValues { (_, lines) -> lines.joinToString("\n") },
        )
    }

    private val structuralMarkerRegex = Regex(
        """^\{.*\}[.:]?$"""
    )
    private val bareLabelRegex = Regex(
        """^(припев|куплет|хор|вступление|окончание|бридж|кода|""" +
            """chorus|verse|bridge|intro|outro|refrain|coda)\s*\d*\s*[.:]?\s*${'$'}""",
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

    /** The line prefixes that mark structure or metadata rather than something sung. */
    private val NOT_LYRIC_PREFIXES = listOf("[", "{", "---", "title:", "author:", "composer:", "tune:")

    /**
     * Whether [trimmed] carries structure or metadata rather than a lyric.
     *
     * Repairing one of these would rewrite a field the rest of the app matches songs on, so they
     * are left exactly as they are even when they hold the same lookalike characters.
     */
    private fun isNotLyric(trimmed: String): Boolean = NOT_LYRIC_PREFIXES.any { trimmed.startsWith(it) }

    /** Check if a file contains mixed Latin/Cyrillic homoglyphs in Cyrillic lines. */
    fun hasHomoglyphs(file: File): Boolean {
        val content = readFileWithFallback(file)
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (isNotLyric(trimmed)) continue
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
            if (isNotLyric(trimmed)) {
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
        return lo.toLong() shl PAIR_SHIFT or hi.toLong()
    }

    private fun unpackPair(key: Long): Pair<Int, Int> {
        return Pair((key shr PAIR_SHIFT).toInt(), (key and PAIR_LOW_MASK).toInt())
    }

    // =========================================================================
    // File I/O
    // =========================================================================

    internal fun readFileWithFallback(file: File): String =
        runCatching { decodeUtf8OrCyrillic(file.readBytes()) }
            .getOrElse { file.readText(Charset.forName("windows-1251")) }
}
