package org.churchpresenter.converter.song

import org.churchpresenter.converter.library.TextUtils

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.StringReader
import java.nio.charset.Charset
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

data class FreeWorshipSection(val label: String, val text: String)

data class FreeWorshipSong(
    val title: String,
    val author: String,
    val copyright: String,
    val verseOrder: List<String>,
    val sections: List<FreeWorshipSection>
)

/**
 * Reads OpenLyrics XML song files, as exported by Free Worship.
 *
 * Two shapes of the same format are handled:
 *  - Standard OpenLyrics, where one `<verse name="v1">` holds a whole verse and `<br/>` separates
 *    its lines.
 *  - Free Worship's export, which writes **one verse element per displayed line** and distinguishes
 *    them with a trailing lowercase letter — `Ca`, `Cb`, `Cc` are the three lines of the chorus,
 *    `V1a`…`V1g` the seven lines of verse 1. Those are folded back into one section per base name.
 *
 * Free Worship writes UTF-16 with a byte-order mark; [readXml] honours whichever BOM is present and
 * falls back to UTF-8.
 */
object FreeWorshipConverter {

    private val subSlideName = Regex("""^([A-Z]+[0-9]*)([a-z])$""")
    private val trailingEmptyParens = Regex("""\s*\(\s*\)\s*$""")

    fun parse(file: File): FreeWorshipSong {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(readXml(file))))

        val root = doc.documentElement
            ?: throw IllegalArgumentException("Not an XML document")
        require(root.tagName.equals("song", ignoreCase = true)) {
            "Not an OpenLyrics song file (root element is <${root.tagName}>)"
        }

        val properties = root.childElement("properties")
        val title = properties?.childElement("titles")?.childElements("title")
            ?.map { it.textContent.trim() }?.firstOrNull { it.isNotEmpty() } ?: ""
        val author = properties?.childElement("authors")?.childElements("author")
            ?.map { it.textContent.trim() }?.filter { it.isNotEmpty() }?.joinToString(", ") ?: ""
        val copyright = properties?.childElement("copyright")?.textContent?.trim() ?: ""
        val verseOrder = properties?.childElement("verseOrder")?.textContent?.trim()
            ?.split(Regex("""\s+"""))?.filter { it.isNotEmpty() } ?: emptyList()

        val verses = root.childElement("lyrics")?.childElements("verse") ?: emptyList()

        // Fold Free Worship's per-line verses back together, keeping first-seen order.
        val grouped = LinkedHashMap<String, MutableList<String>>()
        for (verse in verses) {
            val name = verse.getAttribute("name").trim()
            val base = subSlideName.find(name)?.groupValues?.get(1) ?: name
            val text = verse.childElements("lines").joinToString("\n") { linesText(it) }
            val cleaned = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (cleaned.isEmpty()) continue
            grouped.getOrPut(base) { mutableListOf() }.addAll(cleaned)
        }

        val ordered = if (verseOrder.isEmpty()) grouped.keys.toList() else {
            val seen = LinkedHashSet<String>()
            verseOrder.forEach { name -> if (grouped.containsKey(name)) seen.add(name) }
            seen.addAll(grouped.keys)
            seen.toList()
        }

        val sections = ordered.mapNotNull { name ->
            val lines = grouped[name] ?: return@mapNotNull null
            FreeWorshipSection(sectionLabel(name), lines.joinToString("\n"))
        }

        return FreeWorshipSong(title, author, copyright, verseOrder, sections)
    }

    fun convert(inputFile: File, outputFile: File) {
        outputFile.writeText(buildSongContent(parse(inputFile)), Charsets.UTF_8)
    }

    /** Free Worship names files "TITLE ().xml" when the songbook entry is blank. */
    fun outputNameFor(inputFile: File): String =
        trailingEmptyParens.replace(inputFile.nameWithoutExtension, "").trim() + ".song"

    internal fun buildSongContent(song: FreeWorshipSong): String {
        val sb = StringBuilder()
        sb.appendLine("---")
        if (song.author.isNotBlank()) sb.appendLine("author: ${song.author}")
        if (song.copyright.isNotBlank()) sb.appendLine("copyright: ${song.copyright}")
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine("[Primary]")
        sb.appendLine("title: ${song.title}")
        for (section in song.sections) {
            sb.appendLine()
            sb.appendLine("[${section.label}]")
            sb.appendLine(section.text)
        }
        return sb.toString()
    }

    /** Maps an OpenLyrics verse name (`v1`, `C`, `B2`, `p`) onto a ChurchPresenter section label. */
    internal fun sectionLabel(name: String): String {
        val match = Regex("""^([A-Za-z]+)([0-9]*)$""").find(name) ?: return name
        val (letters, number) = match.destructured
        val base = when (letters.lowercase()) {
            "v" -> "Verse"
            "c" -> "Chorus"
            "b" -> "Bridge"
            "p" -> "Pre-Chorus"
            "e" -> "Ending"
            "i" -> "Intro"
            "o" -> "Outro"
            "t" -> "Tag"
            else -> return name
        }
        return if (number.isEmpty()) base else "$base $number"
    }

    /** Text of one `<lines>` element, turning `<br/>` into newlines and dropping `<comment>`. */
    private fun linesText(element: Element): String {
        val sb = StringBuilder()
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            when {
                node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE ->
                    sb.append(node.textContent)
                node is Element && node.tagName.equals("br", ignoreCase = true) -> sb.append('\n')
                node is Element && node.tagName.equals("comment", ignoreCase = true) -> Unit
                node is Element && node.tagName.equals("chord", ignoreCase = true) -> sb.append(linesText(node))
                node is Element -> sb.append(linesText(node))
            }
        }
        return sb.toString()
    }

    /** Decodes the file using whichever byte-order mark it carries, defaulting to UTF-8. */
    internal fun readXml(file: File): String {
        val bytes = file.readBytes()
        val text = when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                String(bytes, 2, bytes.size - 2, Charset.forName("UTF-16LE"))
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                String(bytes, 2, bytes.size - 2, Charset.forName("UTF-16BE"))
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            else -> String(bytes, Charsets.UTF_8)
        }
        return TextUtils.sanitizeLyricText(text).trim()
    }

    private fun Element.childElement(tag: String): Element? =
        childElements(tag).firstOrNull()

    private fun Element.childElements(tag: String): List<Element> {
        val out = mutableListOf<Element>()
        val children = childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName.substringAfter(':').equals(tag, ignoreCase = true)) {
                out.add(node)
            }
        }
        return out
    }
}
