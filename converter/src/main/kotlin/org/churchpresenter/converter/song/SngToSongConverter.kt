package org.churchpresenter.converter.song

import org.churchpresenter.converter.library.TextUtils
import org.churchpresenter.converter.library.decodeUtf8OrCyrillic

import java.io.File
import java.nio.charset.Charset

data class SngSong(
    val title: String,
    val author: String,
    val copyright: String,
    val verseOrder: List<String>,
    val sections: List<SngSection>,
    val headers: Map<String, String>
)

data class SngSection(
    val type: String,
    val name: String,
    val text: String
)

object SngToSongConverter {

    fun parse(file: File): SngSong {
        val lines = readFileWithFallback(file).lines()
        val headerEnd = lines.indexOfFirst { it.trim() == "---" }
        // Without the `---` that ends the header block there is no section boundary to find, so
        // the file yields its metadata and no lyrics.
        val headers = readHeaders(if (headerEnd < 0) lines else lines.subList(0, headerEnd))
        val sections = readSections(if (headerEnd < 0) emptyList() else lines.subList(headerEnd + 1, lines.size))

        val verseOrder = headers["VerseOrder"].orEmpty()
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return SngSong(
            title = headers["Title"].orEmpty(),
            author = headers["Author"].orEmpty(),
            copyright = headers["(c)"].orEmpty(),
            verseOrder = verseOrder,
            sections = sections,
            headers = headers,
        )
    }

    /** The `#Key=Value` block a `.sng` opens with. Anything else in it is ignored. */
    private fun readHeaders(lines: List<String>): Map<String, String> =
        lines.map { it.trim() }
            .filter { it.startsWith("#") && it.indexOf('=') > 0 }
            .associate { line ->
                val equals = line.indexOf('=')
                line.substring(1, equals).trim() to line.substring(equals + 1).trim()
            }

    /**
     * The sections after the header block, separated by `---`.
     *
     * The first non-empty line of a section names it; everything under that is sung. A section with
     * no name is one the file left empty, and is dropped rather than written blank.
     */
    private fun readSections(lines: List<String>): List<SngSection> {
        val sections = mutableListOf<SngSection>()
        var label: Pair<String, String>? = null
        val body = mutableListOf<String>()

        fun flush() {
            label?.let { (type, name) -> sections.add(SngSection(type, name, body.joinToString("\n").trim())) }
            label = null
            body.clear()
        }

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("---") -> flush()
                label == null && trimmed.isNotEmpty() -> label = parseSectionLabel(trimmed)
                else -> body.add(line)
            }
        }
        flush()
        return sections
    }

    fun convert(sngFile: File, outputFile: File) {
        val song = parse(sngFile)
        val songContent = buildSongContent(song)
        outputFile.writeText(songContent, Charsets.UTF_8)
    }

    fun convertBatch(sngFiles: List<File>, outputDir: File): List<Pair<File, File>> {
        outputDir.mkdirs()
        return sngFiles.map { sngFile ->
            val outputFile = File(outputDir, sngFile.nameWithoutExtension + ".song")
            convert(sngFile, outputFile)
            sngFile to outputFile
        }
    }

    private fun buildSongContent(song: SngSong): String {
        val sb = StringBuilder()

        // Frontmatter
        sb.appendLine("---")
        if (song.author.isNotBlank()) {
            sb.appendLine("author: ${song.author}")
        }
        if (song.copyright.isNotBlank()) {
            sb.appendLine("copyright: ${song.copyright}")
        }
        sb.appendLine("---")
        sb.appendLine()

        // Primary section
        sb.appendLine("[Primary]")
        sb.appendLine("title: ${song.title}")

        // Write each unique section once (no chorus repetition).
        // Use verse order to determine ordering, but deduplicate.
        val sectionsToWrite = if (song.verseOrder.isNotEmpty()) {
            val seen = mutableSetOf<String>()
            song.verseOrder.mapNotNull { orderLabel ->
                val section = song.sections.find { matchesOrder(it, orderLabel) }
                if (section != null) {
                    val key = "${section.type}|${section.name}".lowercase()
                    if (seen.add(key)) section else null
                } else null
            }
        } else {
            song.sections
        }

        for (section in sectionsToWrite) {
            sb.appendLine()
            sb.appendLine("[${formatSectionName(section.type, section.name)}]")
            sb.appendLine(section.text)
        }

        return sb.toString()
    }

    private fun matchesOrder(section: SngSection, orderLabel: String): Boolean {
        val normalized = orderLabel.lowercase().trim()
        val sectionKey = "${section.type} ${section.name}".lowercase().trim()
        val sectionTypeOnly = section.type.lowercase().trim()

        return sectionKey == normalized ||
                sectionTypeOnly == normalized ||
                section.name.lowercase().trim() == normalized
    }

    private fun formatSectionName(type: String, name: String): String {
        val formattedType = type.replaceFirstChar { it.uppercaseChar() }
        return if (name.isNotBlank() && name != type) {
            "$formattedType $name"
        } else {
            formattedType
        }
    }

    /**
     * The section names SongBeamer writes, in both languages, longest prefix first.
     *
     * `pre-chorus` has to be tried before `chorus` would be reached, which is why this is an
     * ordered list rather than a map: the first prefix that matches names the section.
     */
    private val SECTION_PREFIXES = listOf(
        listOf("pre-chorus", "prechorus") to "Pre-Chorus",
        listOf("chorus", "refrain", "припев", "хор") to "Chorus",
        listOf("bridge", "мост") to "Bridge",
        listOf("ending", "outro", "окончание", "конец") to "Ending",
        listOf("intro", "вступление") to "Intro",
    )

    private val VERSE_LABEL = Regex("""(?i)(verse|vers|strophe|куплет|строфа)\s*(\d+)""")

    private fun parseSectionLabel(label: String): Pair<String, String> {
        val lower = label.lowercase().trim()
        val verseMatch = VERSE_LABEL.find(lower)
        if (verseMatch != null) return "Verse" to verseMatch.groupValues[2]

        val named = SECTION_PREFIXES.firstOrNull { (prefixes, _) -> prefixes.any { lower.startsWith(it) } }
        // A name this vocabulary does not cover is kept as it stands rather than guessed at.
        return (named?.second ?: label.trim()) to ""
    }

    private fun readFileWithFallback(file: File): String =
        runCatching { TextUtils.sanitizeLyricText(decodeUtf8OrCyrillic(file.readBytes())) }
            .getOrElse { TextUtils.sanitizeLyricText(file.readText(Charset.forName("windows-1251"))) }
}
