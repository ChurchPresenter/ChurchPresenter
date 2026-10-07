package org.churchpresenter.presentationengine.keynote

import org.w3c.dom.Document
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The XML manifest (`index.apxl`) of a legacy, pre-IWA Keynote document: per-slide thumbnails and
 * per-slide notes. Malformed XML yields nothing rather than an exception.
 */
internal object ApxlManifest {

    /**
     * Per-slide thumbnail paths a legacy document declares for itself, in document order.
     *
     * Shape: `<key:slide><key:thumbnails><key:binary><sf:data sf:path="thumbs/st3.jpg"/>`. The
     * lookup is scoped to the slide's own `thumbnails` child rather than any descendant `data`
     * element, because a slide's page content carries `sf:data` references to its images too —
     * taking the first one found anywhere would hand back a photo from the slide instead of the
     * slide's thumbnail. Master slides use a different element name and so never appear here.
     */
    fun parseThumbnails(xml: String): List<String> = try {
        slides(parse(xml)).mapNotNull { slide -> childNamed(slide, "thumbnails")?.let { firstDataPath(it) } }
    } catch (_: Exception) {
        emptyList()
    }

    /** Legacy (pre-IWA) Keynote documents carry an XML manifest with per-slide notes. */
    fun parseNotes(xml: String): List<String> = try {
        slides(parse(xml)).map { notesOf(it) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun parse(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
    }

    private fun slides(doc: Document): List<Node> {
        val slides = doc.getElementsByTagNameNS("*", "slide")
        return (0 until slides.length).map { slides.item(it) }
    }

    private fun childNamed(node: Node, localName: String): Node? {
        val children = node.childNodes
        return (0 until children.length).map { children.item(it) }.firstOrNull { it.localName == localName }
    }

    /** Depth-first search for the first `sf:data` carrying an `sf:path`. */
    private fun firstDataPath(node: Node): String? {
        if (node.localName == "data") {
            val path = node.attributes?.getNamedItemNS("*", "path")?.nodeValue
                ?: node.attributes?.getNamedItem("sf:path")?.nodeValue
            if (!path.isNullOrBlank()) return path
        }
        val children = node.childNodes
        return (0 until children.length).asSequence().mapNotNull { firstDataPath(children.item(it)) }.firstOrNull()
    }

    /** The text of every `notes` child of [slide], words joined by single spaces. */
    private fun notesOf(slide: Node): String {
        val notes = StringBuilder()
        val children = slide.childNodes
        for (j in 0 until children.length) {
            val child = children.item(j)
            if (child.localName == "notes") extractTextFromNode(child, notes)
        }
        return notes.toString().trim()
    }

    private fun extractTextFromNode(node: Node, sb: StringBuilder) {
        if (node.nodeType == Node.TEXT_NODE) {
            val text = node.nodeValue?.trim()
            if (!text.isNullOrBlank()) {
                if (sb.isNotEmpty()) sb.append(" ")
                sb.append(text)
            }
        }
        val children = node.childNodes
        for (i in 0 until children.length) extractTextFromNode(children.item(i), sb)
    }
}
