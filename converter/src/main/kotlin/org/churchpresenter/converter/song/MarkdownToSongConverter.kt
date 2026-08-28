package org.churchpresenter.converter.song

import java.io.File

data class ParsedSong(
    val title: String,
    val author: String = "",
    val copyright: String = "",
    val composer: String = "",
    val sections: List<SongSection> = emptyList(),
    val ccli: String = ""
)

data class SongSection(
    val label: String,
    val lines: List<String>
)

data class DocConversionResult(
    val songsCreated: Int,
    val outputFiles: List<File>,
    val errors: List<String>
)

// Split into one small function per step, which is what keeps the readers below within the
// complexity and nesting limits. Splitting the object itself would scatter one file format across
// several files instead.
@Suppress("TooManyFunctions")
object MarkdownToSongConverter {

    /** Longer than this and a line is a lyric, not the song's title. */
    private const val MAX_TITLE_LENGTH = 120

    /** Fewer lines than this either side of a rule and the rule is decoration, not a boundary. */
    private const val MIN_LINES_PER_SONG = 2

    /** A line that is nothing but "1." — how an unlabelled document opens its next verse. */
    private val VERSE_MARKER = Regex("""^\d+\.\s*${'$'}""")

    /** A markdown sub-heading, and the bold markers a label written as one carries. */
    private val SUB_HEADING = Regex("""^#{2,4}\s+(.+)""")

    /** A level-one or level-two heading, which is what a document titles a song with. */
    private val HEADING = Regex("""^#{1,2}\s+(.+)""")
    private val BOLD = Regex("""^\*\*(.+)\*\*${'$'}""")

    // `(?iu)`, not `(?i)`: a bare inline `(?i)` is ASCII-only case folding, so `Куплет`/`Припев`
    // as anyone actually writes them did not match while lower-case `куплет` did — a Russian
    // document then imported as one section holding every lyric AND every label line. The
    // metadata patterns below escape this because Kotlin's RegexOption.IGNORE_CASE implies
    // UNICODE_CASE; this one takes no options, so the flag has to be in the pattern.
    private val sectionLabelRegex = Regex(
        """(?iu)^(?:#{1,4}\s+)?(?:\*\*)?""" +
        """(verse|vers|strophe|куплет|строфа|chorus|refrain|припев|хор|bridge|мост|""" +
        """pre-chorus|prechorus|ending|outro|окончание|конец|intro|вступление|coda|tag)""" +
        """(?:\s+(\d+))?(?:\*\*)?[:\s]*$"""
    )

    private val authorRegex = Regex(
        """(?i)^\s*(?:author|by|автор|слова|words|text|lyrics by)[:\s]+(.+)""",
        RegexOption.IGNORE_CASE
    )
    private val copyrightRegex = Regex(
        """(?i)^\s*(?:copyright|©|\(c\)|\(С\))[:\s]*(.+)""",
        RegexOption.IGNORE_CASE
    )
    private val composerRegex = Regex(
        """(?i)^\s*(?:composer|music|музыка|мелодия|music by)[:\s]+(.+)""",
        RegexOption.IGNORE_CASE
    )

    fun parseMarkdown(markdown: String, sourceFileName: String): List<ParsedSong> {
        val cleaned = markdown.trim()
        if (cleaned.isBlank()) return emptyList()

        // Try to split into multiple songs
        val songBlocks = splitIntoSongs(cleaned)

        return songBlocks.mapIndexed { idx, block ->
            val fallbackTitle = if (songBlocks.size == 1) {
                sourceFileName.substringBeforeLast('.')
            } else {
                "${sourceFileName.substringBeforeLast('.')} - ${idx + 1}"
            }
            parseSingleSong(block, fallbackTitle)
        }.filter { it.sections.isNotEmpty() || it.title.isNotBlank() }
    }

    fun buildSongContent(song: ParsedSong): String {
        val sb = StringBuilder()

        // Frontmatter
        val hasMeta = song.author.isNotBlank() || song.copyright.isNotBlank() ||
            song.composer.isNotBlank() || song.ccli.isNotBlank()
        if (hasMeta) {
            sb.appendLine("---")
            if (song.author.isNotBlank()) sb.appendLine("author: ${song.author}")
            if (song.composer.isNotBlank()) sb.appendLine("composer: ${song.composer}")
            if (song.copyright.isNotBlank()) sb.appendLine("copyright: ${song.copyright}")
            if (song.ccli.isNotBlank()) sb.appendLine("ccli: ${song.ccli}")
            sb.appendLine("---")
            sb.appendLine()
        }

        sb.appendLine("[Primary]")
        sb.appendLine("title: ${song.title}")

        for (section in song.sections) {
            sb.appendLine()
            sb.appendLine("[${section.label}]")
            for (line in section.lines) {
                sb.appendLine(line)
            }
        }

        return sb.toString()
    }

    // One song that cannot be written -- a name the filesystem refuses, a full disk -- is reported
    // and the rest of the document still converts. Which exception says so is the filesystem's
    // business, not this loop's.
    @Suppress("TooGenericExceptionCaught")
    fun convert(markdownText: String, sourceFileName: String, outputDir: File): DocConversionResult {
        val songs = parseMarkdown(markdownText, sourceFileName)
        if (songs.isEmpty()) {
            return DocConversionResult(0, emptyList(), listOf("No songs found in document"))
        }

        outputDir.mkdirs()
        val outputFiles = mutableListOf<File>()
        val errors = mutableListOf<String>()

        for ((idx, song) in songs.withIndex()) {
            try {
                val fileName = if (songs.size == 1) {
                    sanitizeName(song.title.ifBlank { sourceFileName.substringBeforeLast('.') }) + ".song"
                } else {
                    val num = (idx + 1).toString().padStart(4, '0')
                    "$num - ${sanitizeName(song.title.ifBlank { "Song ${idx + 1}" })}.song"
                }

                val outFile = File(outputDir, fileName)
                outFile.writeText(buildSongContent(song), Charsets.UTF_8)
                outputFiles.add(outFile)
            } catch (e: Exception) {
                errors.add("Error writing song ${idx + 1}: ${e.message}")
            }
        }

        return DocConversionResult(outputFiles.size, outputFiles, errors)
    }

    fun preview(inputFile: File): Pair<String, List<ParsedSong>> {
        val result = DocumentTextExtractor.extract(inputFile)
        if (!result.success) {
            return (result.errorMessage ?: "Unknown error") to emptyList()
        }
        val songs = parseMarkdown(result.text, inputFile.name)
        return result.text to songs
    }

    // ── Song splitting ──────────────────────────────────────────────────────

    private fun splitIntoSongs(markdown: String): List<String> {
        val lines = markdown.lines()

        // Strategy 1: Split on level-1 headings (# Title)
        val h1Indices = lines.indices.filter { lines[it].matches(Regex("""^#\s+.+""")) }
        if (h1Indices.size > 1) return splitAtIndices(lines, h1Indices)

        // Strategy 2: Split on horizontal rules (---) that separate substantial blocks. A rule
        // with nothing substantial either side of it is decoration, not a boundary.
        //
        // There is no strategy 3: a document of PPTX slide markers is one song whose slides are
        // its sections, which is what the single-song path below already produces.
        return splitAtRules(lines) ?: listOf(markdown)
    }

    /** [lines] split at its horizontal rules, or null when they do not separate whole songs. */
    private fun splitAtRules(lines: List<String>): List<String>? {
        val hrIndices = lines.indices.filter {
            lines[it].matches(Regex("""^-{3,}\s*${'$'}""")) || lines[it].matches(Regex("""^\*{3,}\s*${'$'}"""))
        }
        if (hrIndices.isEmpty()) return null
        val substantialBlocks = splitAtSeparators(lines, hrIndices).filter { block ->
            block.lines().count { it.isNotBlank() } >= MIN_LINES_PER_SONG
        }
        return substantialBlocks.takeIf { it.size > 1 }
    }

    private fun splitAtIndices(lines: List<String>, indices: List<Int>): List<String> {
        val blocks = mutableListOf<String>()
        for (i in indices.indices) {
            val start = indices[i]
            val end = if (i + 1 < indices.size) indices[i + 1] else lines.size
            val block = lines.subList(start, end).joinToString("\n").trim()
            if (block.isNotBlank()) blocks.add(block)
        }
        // Include any content before the first heading as part of the first song
        if (indices.first() > 0) {
            val preamble = lines.subList(0, indices.first()).joinToString("\n").trim()
            if (preamble.isNotBlank() && blocks.isNotEmpty()) {
                blocks[0] = preamble + "\n\n" + blocks[0]
            }
        }
        return blocks
    }

    private fun splitAtSeparators(lines: List<String>, separatorIndices: List<Int>): List<String> {
        val blocks = mutableListOf<String>()
        var start = 0
        for (sepIdx in separatorIndices) {
            val block = lines.subList(start, sepIdx).joinToString("\n").trim()
            if (block.isNotBlank()) blocks.add(block)
            start = sepIdx + 1
        }
        if (start < lines.size) {
            val block = lines.subList(start, lines.size).joinToString("\n").trim()
            if (block.isNotBlank()) blocks.add(block)
        }
        return blocks
    }

    // ── Single song parsing ─────────────────────────────────────────────────

    private fun parseSingleSong(block: String, fallbackTitle: String): ParsedSong {
        val lines = block.lines()
        var title = ""
        var author = ""
        var copyright = ""
        var composer = ""
        val metaLineIndices = mutableSetOf<Int>()

        // Extract metadata
        for ((i, line) in lines.withIndex()) {
            authorRegex.find(line)?.let { author = it.groupValues[1].trim(); metaLineIndices.add(i) }
            copyrightRegex.find(line)?.let { copyright = it.groupValues[1].trim(); metaLineIndices.add(i) }
            composerRegex.find(line)?.let { composer = it.groupValues[1].trim(); metaLineIndices.add(i) }
        }

        // Extract title from first heading or first non-empty line
        val titled = titleOf(lines, metaLineIndices)
        if (titled != null) {
            title = titled.second
            metaLineIndices.add(titled.first)
        }

        if (title.isBlank()) title = fallbackTitle

        // Parse sections from remaining lines
        val contentLines = lines.filterIndexed { i, _ -> i !in metaLineIndices }
        val sections = parseSections(contentLines)

        return ParsedSong(title, author, copyright, composer, sections)
    }

    /**
     * Which line of [lines] is the song's title, and what it says.
     *
     * A heading is taken outright. Failing that the first line that could be one is used — which
     * rules out a section label, and anything too long to be a title — and if nothing qualifies the
     * caller falls back to the file's own name.
     */
    private fun titleOf(lines: List<String>, metaLineIndices: Set<Int>): Pair<Int, String>? {
        for ((index, line) in lines.withIndex()) {
            if (index in metaLineIndices) continue
            val trimmed = line.trim().replace(BOLD, "$1")
            val heading = HEADING.find(line.trim())?.groupValues?.get(1)?.trim()?.replace(BOLD, "$1")
            when {
                heading != null -> return index to heading
                trimmed.isBlank() -> Unit
                sectionLabelRegex.matches(trimmed) || trimmed.length >= MAX_TITLE_LENGTH -> Unit
                else -> return index to trimmed
            }
        }
        return null
    }

    private fun parseSections(lines: List<String>): List<SongSection> {
        val sections = mutableListOf<SongSection>()
        var currentLabel: String? = null
        // Whether [currentLabel] came from a real section marker ("Chorus", "Куплет 1", a
        // sub-heading) rather than being auto-assigned to lyrics that arrived unlabelled.
        //
        // This distinction is what makes the paragraph splits below reachable at all. They used to
        // be guarded on `currentLabel == null`, but the fall-through at the bottom assigns
        // "Verse N" to the first lyric line, so the label was never null again and a document with
        // no section markers imported as ONE section holding the entire song — which the app then
        // shows as a single slide. Only an *explicit* label should suppress paragraph splitting.
        var labelIsExplicit = false
        var currentLines = mutableListOf<String>()
        var verseCounter = 1

        /** Closes the section in progress, if it holds anything. */
        fun flush() {
            val label = currentLabel
            if (label != null && currentLines.any { it.isNotBlank() }) {
                sections.add(SongSection(label, currentLines.dropLastWhile { it.isBlank() }))
                if (!labelIsExplicit) verseCounter++
            }
        }

        /** Ends the section in progress and opens one called [label], or an unnamed one. */
        fun open(label: String?) {
            flush()
            currentLabel = label
            labelIsExplicit = label != null
            currentLines = mutableListOf()
        }

        for (line in lines) {
            val trimmed = line.trim()
            val label = labelOf(trimmed)
            when {
                label != null -> open(label)
                // A bare "1." / "2." marker starts the next verse of an unlabelled document, and
                // so does a blank line: each paragraph is a verse. Under an explicit label a blank
                // line is part of that section instead.
                !labelIsExplicit && trimmed.matches(VERSE_MARKER) -> open(null)
                !labelIsExplicit && trimmed.isBlank() && currentLines.any { it.isNotBlank() } -> open(null)
                trimmed.isNotBlank() -> {
                    if (currentLabel == null) {
                        currentLabel = "Verse $verseCounter"
                        labelIsExplicit = false
                    }
                    currentLines.add(stripMarkdown(trimmed))
                }
                // A blank line inside a section is kept, so its slides break where they were typed.
                currentLabel != null -> currentLines.add("")
            }
        }

        flush()

        // Post-process: detect repeated sections as Chorus
        return detectChorus(sections)
    }

    /**
     * The section [trimmed] names, or null when it is not a section marker.
     *
     * A marker is either the label on its own ("Chorus", "Куплет 1") or one written as a
     * sub-heading (`## Chorus`, `### **Verse 2**`), which is how a document exported from Word
     * usually carries it.
     */
    private fun labelOf(trimmed: String): String? {
        val direct = sectionLabelRegex.find(trimmed)
        if (direct != null) return formatLabel(direct.groupValues[1], direct.groupValues[2])

        val heading = SUB_HEADING.find(trimmed)?.groupValues?.get(1)?.trim()?.replace(BOLD, "$1")
        val inHeading = heading?.let { sectionLabelRegex.find(it) } ?: return null
        return formatLabel(inHeading.groupValues[1], inHeading.groupValues[2])
    }

    private fun detectChorus(sections: List<SongSection>): List<SongSection> {
        if (sections.size < 2) return sections

        // Check if any unlabeled verse appears more than once (= likely chorus)
        val verseTexts = mutableMapOf<String, MutableList<Int>>()
        for ((i, section) in sections.withIndex()) {
            if (section.label.startsWith("Verse")) {
                val normalized = section.lines.joinToString("\n").trim().lowercase()
                verseTexts.getOrPut(normalized) { mutableListOf() }.add(i)
            }
        }

        val repeated = verseTexts.filter { it.value.size > 1 }
        if (repeated.isEmpty()) return sections

        // Relabel repeated sections as Chorus, keep only first occurrence
        val result = mutableListOf<SongSection>()
        val seenChorus = mutableSetOf<String>()
        var verseNum = 1

        for (section in sections) {
            val normalized = section.lines.joinToString("\n").trim().lowercase()
            if (section.label.startsWith("Verse") && normalized in repeated) {
                if (seenChorus.add(normalized)) {
                    result.add(SongSection("Chorus", section.lines))
                }
                // Skip duplicate chorus occurrences
            } else if (section.label.startsWith("Verse")) {
                result.add(SongSection("Verse ${verseNum++}", section.lines))
            } else {
                result.add(section)
            }
        }

        return result
    }

    private fun formatLabel(type: String, number: String): String {
        val normalized = when (type.lowercase()) {
            "verse", "vers", "strophe", "куплет", "строфа" -> "Verse"
            "chorus", "refrain", "припев", "хор" -> "Chorus"
            "bridge", "мост" -> "Bridge"
            "pre-chorus", "prechorus" -> "Pre-Chorus"
            "ending", "outro", "окончание", "конец" -> "Ending"
            "intro", "вступление" -> "Intro"
            "coda" -> "Coda"
            "tag" -> "Tag"
            else -> type.replaceFirstChar { it.uppercaseChar() }
        }
        return if (number.isNotBlank()) "$normalized $number" else normalized
    }

    private fun stripMarkdown(text: String): String {
        return text
            .replace(Regex("""\*\*(.+?)\*\*"""), "$1")   // bold
            .replace(Regex("""\*(.+?)\*"""), "$1")         // italic
            .replace(Regex("""__(.+?)__"""), "$1")          // bold alt
            .replace(Regex("""_(.+?)_"""), "$1")            // italic alt
            .replace(Regex("""~~(.+?)~~"""), "$1")          // strikethrough
            .replace(Regex("""^>\s?"""), "")                // blockquote
            .trim()
    }

    private fun sanitizeName(name: String): String {
        return name
            .replace(Regex("""[/\\:*?"<>|]"""), " ")
            .replace(Regex("""[\x00-\x1F\x7F]"""), "")
            .replace(Regex("""[^\p{Print}\p{L}\p{M}\p{N}\p{P}\p{Z}]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
