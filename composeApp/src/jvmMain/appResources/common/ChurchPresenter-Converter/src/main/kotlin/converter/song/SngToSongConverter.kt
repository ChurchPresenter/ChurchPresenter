package converter.song

import converter.library.TextUtils

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
        val content = readFileWithFallback(file)
        val lines = content.lines()

        val headers = mutableMapOf<String, String>()
        var lineIdx = 0

        // Parse headers (lines starting with #)
        while (lineIdx < lines.size) {
            val line = lines[lineIdx].trim()
            if (line.startsWith("#")) {
                val eqIdx = line.indexOf('=')
                if (eqIdx > 0) {
                    val key = line.substring(1, eqIdx).trim()
                    val value = line.substring(eqIdx + 1).trim()
                    headers[key] = value
                }
                lineIdx++
            } else if (line == "---") {
                lineIdx++
                break
            } else {
                lineIdx++
            }
        }

        // Parse sections (separated by ---)
        val sections = mutableListOf<SngSection>()
        var currentType = ""
        var currentName = ""
        val currentLines = mutableListOf<String>()

        while (lineIdx < lines.size) {
            val line = lines[lineIdx]
            if (line.trim() == "---" || line.trim().startsWith("---")) {
                if (currentType.isNotEmpty()) {
                    sections.add(SngSection(currentType, currentName, currentLines.joinToString("\n").trim()))
                }
                currentType = ""
                currentName = ""
                currentLines.clear()
                lineIdx++
                continue
            }

            if (currentType.isEmpty() && line.trim().isNotEmpty()) {
                val parsed = parseSectionLabel(line.trim())
                currentType = parsed.first
                currentName = parsed.second
                lineIdx++
                continue
            }

            currentLines.add(line)
            lineIdx++
        }

        // Add last section
        if (currentType.isNotEmpty()) {
            sections.add(SngSection(currentType, currentName, currentLines.joinToString("\n").trim()))
        }

        val title = headers["Title"] ?: ""
        val author = headers["Author"] ?: ""
        val copyright = headers["(c)"] ?: ""
        val verseOrderStr = headers["VerseOrder"] ?: ""
        val verseOrder = if (verseOrderStr.isNotBlank()) {
            verseOrderStr.split(",").map { it.trim() }
        } else {
            emptyList()
        }

        return SngSong(title, author, copyright, verseOrder, sections, headers)
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

    private fun parseSectionLabel(label: String): Pair<String, String> {
        val lower = label.lowercase().trim()

        // Match patterns like "verse 1", "chorus", "bridge", "pre-chorus", "ending", etc.
        val verseMatch = Regex("""(?i)(verse|vers|strophe|куплет|строфа)\s*(\d+)""").find(lower)
        if (verseMatch != null) {
            return "Verse" to verseMatch.groupValues[2]
        }

        if (lower.startsWith("chorus") || lower.startsWith("refrain") ||
            lower.startsWith("припев") || lower.startsWith("хор")
        ) {
            return "Chorus" to ""
        }

        if (lower.startsWith("bridge") || lower.startsWith("мост")) {
            return "Bridge" to ""
        }

        if (lower.startsWith("pre-chorus") || lower.startsWith("prechorus")) {
            return "Pre-Chorus" to ""
        }

        if (lower.startsWith("ending") || lower.startsWith("outro") ||
            lower.startsWith("окончание") || lower.startsWith("конец")
        ) {
            return "Ending" to ""
        }

        if (lower.startsWith("intro") || lower.startsWith("вступление")) {
            return "Intro" to ""
        }

        // Generic: use the label as-is
        return label.trim() to ""
    }

    private fun readFileWithFallback(file: File): String {
        // Try UTF-8 first, then Windows-1251 (common for Russian SongBeamer files)
        return try {
            val bytes = file.readBytes()
            // Strip BOM if present
            val content = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
                String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            } else {
                String(bytes, Charsets.UTF_8)
            }
            // Verify it decoded properly
            if (content.contains('\uFFFD')) {
                TextUtils.sanitizeLyricText(String(bytes, Charset.forName("windows-1251")))
            } else {
                TextUtils.sanitizeLyricText(content)
            }
        } catch (e: Exception) {
            TextUtils.sanitizeLyricText(file.readText(Charset.forName("windows-1251")))
        }
    }
}
