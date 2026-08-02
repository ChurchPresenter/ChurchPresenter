package converter

import java.io.File

data class ParsedSong(
    val title: String,
    val author: String = "",
    val copyright: String = "",
    val composer: String = "",
    val sections: List<SongSection> = emptyList()
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

object MarkdownToSongConverter {

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
        val hasMeta = song.author.isNotBlank() || song.copyright.isNotBlank() || song.composer.isNotBlank()
        if (hasMeta) {
            sb.appendLine("---")
            if (song.author.isNotBlank()) sb.appendLine("author: ${song.author}")
            if (song.composer.isNotBlank()) sb.appendLine("composer: ${song.composer}")
            if (song.copyright.isNotBlank()) sb.appendLine("copyright: ${song.copyright}")
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
        if (h1Indices.size > 1) {
            return splitAtIndices(lines, h1Indices)
        }

        // Strategy 2: Split on horizontal rules (---) that separate substantial blocks
        val hrIndices = lines.indices.filter {
            lines[it].matches(Regex("""^-{3,}\s*$""")) || lines[it].matches(Regex("""^\*{3,}\s*$"""))
        }
        if (hrIndices.isNotEmpty()) {
            val blocks = splitAtSeparators(lines, hrIndices)
            // Only treat as multi-song if blocks have enough content
            val substantialBlocks = blocks.filter { block ->
                block.lines().count { it.isNotBlank() } >= 2
            }
            if (substantialBlocks.size > 1) {
                return substantialBlocks
            }
        }

        // Strategy 3: PPTX slide markers
        val slideIndices = lines.indices.filter {
            lines[it].trim().matches(Regex("""^<!--\s*slide\s*-->$""", RegexOption.IGNORE_CASE))
        }
        if (slideIndices.size > 1) {
            // Group slides into songs (each slide = a section, but they might belong to the same song)
            // For now, treat the whole document as one song — slides become sections
            return listOf(markdown)
        }

        // Single song
        return listOf(markdown)
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
        for ((i, line) in lines.withIndex()) {
            if (i in metaLineIndices) continue
            val headingMatch = Regex("""^#{1,2}\s+(.+)""").find(line.trim())
            if (headingMatch != null) {
                title = headingMatch.groupValues[1].trim()
                    .replace(Regex("""^\*\*(.+)\*\*$"""), "$1") // strip bold
                metaLineIndices.add(i)
                break
            }
            if (line.trim().isNotBlank() && title.isBlank()) {
                // Check if this line looks like a title (short, not a section label)
                val trimmed = line.trim()
                    .replace(Regex("""^\*\*(.+)\*\*$"""), "$1") // strip bold
                if (sectionLabelRegex.matches(trimmed).not() && trimmed.length < 120) {
                    title = trimmed
                    metaLineIndices.add(i)
                    break
                }
            }
        }

        if (title.isBlank()) title = fallbackTitle

        // Parse sections from remaining lines
        val contentLines = lines.filterIndexed { i, _ -> i !in metaLineIndices }
        val sections = parseSections(contentLines)

        return ParsedSong(title, author, copyright, composer, sections)
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

        for (line in lines) {
            val trimmed = line.trim()

            // Check for section label
            val labelMatch = sectionLabelRegex.find(trimmed)
            if (labelMatch != null) {
                flush()
                currentLabel = formatLabel(labelMatch.groupValues[1], labelMatch.groupValues[2])
                labelIsExplicit = true
                currentLines = mutableListOf()
                continue
            }

            // A bare "1." / "2." marker starts the next verse of an unlabelled document.
            if (!labelIsExplicit && trimmed.matches(Regex("""^\d+\.\s*$"""))) {
                flush()
                currentLabel = null
                currentLines = mutableListOf()
                continue
            }

            // Check for sub-headings that might be section labels (## or ### or bold)
            val subHeadingMatch = Regex("""^#{2,4}\s+(.+)""").find(trimmed)
            if (subHeadingMatch != null) {
                val headingText = subHeadingMatch.groupValues[1].trim()
                    .replace(Regex("""^\*\*(.+)\*\*$"""), "$1")
                val innerLabelMatch = sectionLabelRegex.find(headingText)
                if (innerLabelMatch != null) {
                    flush()
                    currentLabel = formatLabel(innerLabelMatch.groupValues[1], innerLabelMatch.groupValues[2])
                    labelIsExplicit = true
                    currentLines = mutableListOf()
                    continue
                }
            }

            // In an unlabelled document a blank line ends the paragraph, and each paragraph is a
            // verse. Under an explicit label a blank line is kept as part of that section instead.
            if (trimmed.isBlank() && !labelIsExplicit && currentLines.any { it.isNotBlank() }) {
                flush()
                currentLabel = null
                currentLines = mutableListOf()
                continue
            }

            if (trimmed.isNotBlank()) {
                if (currentLabel == null) {
                    currentLabel = "Verse $verseCounter"
                    labelIsExplicit = false
                }
                // Strip markdown formatting from lyrics
                currentLines.add(stripMarkdown(trimmed))
            } else if (currentLabel != null) {
                currentLines.add("") // preserve blank lines within sections
            }
        }

        flush()

        // Post-process: detect repeated sections as Chorus
        return detectChorus(sections)
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
