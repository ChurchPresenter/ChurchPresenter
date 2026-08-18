package converter.song

import converter.library.TextUtils

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.StringReader
import java.nio.charset.Charset
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import org.xml.sax.SAXException

/**
 * The XML plumbing every song-format reader here needs.
 *
 * Song exports are written by desktop apps rather than by tooling, so they arrive in whatever
 * encoding the host machine used — [readXmlText] decodes by byte-order mark instead of trusting the
 * declaration, which is how a UTF-16 export stops looking like NUL-separated gibberish. The element
 * helpers ignore namespaces and case for the same reason: the same product writes `<Contents>` in
 * one version and `<contents>` in the next.
 */

private const val UTF16_LE_MARK = 0xFF
private const val UTF16_BE_MARK = 0xFE
private const val UTF8_MARK_1 = 0xEF
private const val UTF8_MARK_2 = 0xBB
private const val UTF8_MARK_3 = 0xBF

/** Decodes a file by whichever byte-order mark it carries, defaulting to UTF-8. */
internal fun readXmlText(file: File): String = decodeXmlText(file.readBytes())

internal fun decodeXmlText(bytes: ByteArray): String {
    val text = when {
        startsWith(bytes, UTF16_LE_MARK, UTF16_BE_MARK) ->
            String(bytes, 2, bytes.size - 2, Charset.forName("UTF-16LE"))
        startsWith(bytes, UTF16_BE_MARK, UTF16_LE_MARK) ->
            String(bytes, 2, bytes.size - 2, Charset.forName("UTF-16BE"))
        startsWith(bytes, UTF8_MARK_1, UTF8_MARK_2, UTF8_MARK_3) ->
            String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        else -> String(bytes, Charsets.UTF_8)
    }
    return TextUtils.sanitizeLyricText(text).trim()
}

private fun startsWith(bytes: ByteArray, vararg mark: Int): Boolean =
    bytes.size >= mark.size && mark.withIndex().all { (index, value) -> bytes[index] == value.toByte() }

/**
 * Parses [text] and returns its root element, without resolving any external DTD.
 *
 * A document the parser rejects is retried through [repairXml] rather than given up on, because the
 * apps these files come from do not reliably escape their own text. If the repaired document fails
 * too, the *original* failure is what is thrown — that one describes the real file.
 */
internal fun parseXmlRoot(text: String): Element {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
    }
    val document = try {
        factory.newDocumentBuilder().parse(InputSource(StringReader(text)))
    } catch (invalid: SAXException) {
        runCatching { factory.newDocumentBuilder().parse(InputSource(StringReader(repairXml(text)))) }
            .getOrElse { throw invalid }
    }
    return document.documentElement ?: throw IllegalArgumentException("Not an XML document")
}

/** The root element of [file], required to be named [expectedRoot]. */
internal fun xmlRootOf(file: File, expectedRoot: String): Element {
    val root = parseXmlRoot(readXmlText(file))
    require(root.tagName.substringAfter(':').equals(expectedRoot, ignoreCase = true)) {
        "Expected a <$expectedRoot> document, found <${root.tagName}>"
    }
    return root
}

/** Direct children named [tag], namespace prefix and case ignored. */
internal fun Element.childElements(tag: String): List<Element> {
    val found = mutableListOf<Element>()
    val children = childNodes
    for (index in 0 until children.length) {
        val node = children.item(index)
        if (node is Element && node.tagName.substringAfter(':').equals(tag, ignoreCase = true)) {
            found.add(node)
        }
    }
    return found
}

/** Every descendant named [tag], however deeply nested. */
internal fun Element.descendants(tag: String): List<Element> {
    val found = mutableListOf<Element>()
    val children = childNodes
    for (index in 0 until children.length) {
        val node = children.item(index)
        if (node is Element) {
            if (node.tagName.substringAfter(':').equals(tag, ignoreCase = true)) found.add(node)
            found.addAll(node.descendants(tag))
        }
    }
    return found
}

internal fun Element.childElement(tag: String): Element? = childElements(tag).firstOrNull()

/** Trimmed text of the first child named [tag], or "" when there is none. */
internal fun Element.childText(tag: String): String = childElement(tag)?.textContent?.trim() ?: ""

/** Text of this element with `<br/>` turned into newlines, which several formats rely on. */
internal fun Element.textWithBreaks(): String {
    val text = StringBuilder()
    val children = childNodes
    for (index in 0 until children.length) {
        val node = children.item(index)
        when {
            node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE ->
                text.append(node.textContent)
            node is Element && node.tagName.substringAfter(':').equals("br", ignoreCase = true) ->
                text.append('\n')
            node is Element -> text.append(node.textWithBreaks())
        }
    }
    return text.toString()
}
