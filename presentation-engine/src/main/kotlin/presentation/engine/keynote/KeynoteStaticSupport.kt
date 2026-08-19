package presentation.engine.keynote

import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Static (non-animated) Keynote extraction — everything in-JVM, no external processes.
 *
 * A `.key` file is a zip (or, for package-format documents, a directory). Two static sources
 * exist inside it, in fidelity order:
 *  1. `QuickLook/Preview.pdf` — a full-resolution PDF of every slide, embedded by Keynote in
 *     most documents. Near-lossless static rendering.
 *  2. `Data/st-*.jpg` — per-slide thumbnails (`st-` = slide thumbnail; `mt-` files are media
 *     assets, not slides). Lower resolution, but always per-slide.
 *
 * Slide order for thumbnails: `Index/Slide-<id>.iwa` entries appear in the zip in presentation
 * order; the `st-` files carry a trailing numeric id whose rank matches the sorted iwa ids.
 * (WS5 replaces this heuristic with a real IWA parse.)
 */
internal object KeynoteStaticSupport {

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "tiff", "tif")
    private const val PREVIEW_PDF_ENTRY = "QuickLook/Preview.pdf"

    /** What a `.key` file offers for static rendering, plus speaker notes. */
    data class Analysis(
        val hasPreviewPdf: Boolean,
        /** Thumbnail zip entry names (or absolute paths for package-format documents), slide order. */
        val orderedThumbnailEntries: List<String>,
        val notes: List<String>
    )

    fun analyze(file: File): Analysis {
        return if (file.isDirectory) analyzeDirectory(file) else analyzeZip(file)
    }

    private fun analyzeZip(file: File): Analysis {
        var hasPreviewPdf = false
        val slideIwaOrder = mutableListOf<Long>()
        val thumbnailEntries = mutableListOf<String>()
        var apxlXml: String? = null
        val iwaNotes = mutableMapOf<Long, String>()
        val allEntryNames = mutableSetOf<String>()

        ZipFile(file).use { zip ->
            for (entry in zip.entries()) {
                if (entry.isDirectory) continue
                val name = entry.name
                allEntryNames.add(name)
                val base = name.substringAfterLast("/")
                when {
                    name.equals(PREVIEW_PDF_ENTRY, ignoreCase = true) && entry.size != 0L ->
                        hasPreviewPdf = true

                    base.startsWith("Slide-") && base.endsWith(".iwa") ->
                        slideIwaId(base)?.let { slideIwaOrder.add(it) }

                    base == "Slide.iwa" ->
                        iwaNotes[-1L] = scanIwaForNoteText(zip.getInputStream(entry).readBytes())

                    isThumbnailEntry(name, base) ->
                        thumbnailEntries.add(name)

                    base.equals("index.apxl", ignoreCase = true) && apxlXml == null ->
                        apxlXml = zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
                }
            }
            // Second pass detail: notes for Slide-<id>.iwa entries (skipped above to keep the
            // hot path single-purpose; only read when the deck actually has slide iwa entries).
            if (slideIwaOrder.isNotEmpty()) {
                for (entry in zip.entries()) {
                    val base = entry.name.substringAfterLast("/")
                    if (base.startsWith("Slide-") && base.endsWith(".iwa")) {
                        val id = slideIwaId(base) ?: continue
                        iwaNotes[id] = scanIwaForNoteText(zip.getInputStream(entry).readBytes())
                    }
                }
            }
        }

        return Analysis(
            hasPreviewPdf = hasPreviewPdf,
            orderedThumbnailEntries = resolveThumbnails(
                modern = orderThumbnails(thumbnailEntries, slideIwaOrder),
                apxlXml = apxlXml,
                exists = { it in allEntryNames },
            ),
            notes = resolveNotes(apxlXml, iwaNotes)
        )
    }

    /**
     * Modern thumbnails when there are any, otherwise the ones a legacy document names for itself.
     *
     * Keynote '09 puts per-slide thumbnails in `thumbs/` as `st<n>.jpg` / `st<n>-<m>.jpg`, which
     * matches neither the `Data/` location nor the `st-` prefix the modern rule looks for — so
     * before this, a legacy deck with nine perfectly good thumbnails was reported as having none
     * and failed to open at all, with a message saying there were no thumbnails.
     *
     * Their names cannot be sorted into slide order (a real document runs `st2-1, st2-2, st3, …,
     * st2, st7, st6, st186`), but they do not need to be: the apxl states the slide-to-thumbnail
     * mapping itself, in document order. That is the only reliable source, and the same file is
     * already parsed here for notes.
     */
    private fun resolveThumbnails(
        modern: List<String>,
        apxlXml: String?,
        exists: (String) -> Boolean,
    ): List<String> {
        if (modern.isNotEmpty()) return modern
        val declared = apxlXml?.let { parseApxlThumbnails(it) } ?: return modern
        // Only offer entries the document actually contains; a stale reference must not become a
        // blank slide in the middle of a deck.
        return declared.filter(exists)
    }

    private fun analyzeDirectory(dir: File): Analysis {
        // A package document is a directory, and File.listFiles hands back whatever order the
        // filesystem stores — near-sorted on APFS, arbitrary on ext4. The zip branch can rely on
        // entry order because Keynote writes it deliberately; a folder carries no such signal, so
        // both lists are sorted instead. Without this the slide order — and with it which thumbnail
        // belongs to which slide — differs from one machine to the next, which is exactly what it
        // did: the same document opened with its slides in one order here and another on CI.
        val dataDir = File(dir, "Data")
        val thumbnails = dataDir.listFiles()
            ?.filter {
                it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS &&
                    it.name.lowercase().startsWith("st-")
            }
            ?.map { it.absolutePath }
            ?.sorted()
            ?: emptyList()
        val slideIwaOrder = File(dir, "Index").listFiles()
            ?.map { it.name }
            ?.filter { it.startsWith("Slide-") && it.endsWith(".iwa") }
            ?.mapNotNull { slideIwaId(it) }
            ?.sorted()
            ?: emptyList()
        val apxlXml = File(dir, "index.apxl").takeIf { it.exists() }?.readText()
        val iwaNotes = mutableMapOf<Long, String>()
        File(dir, "Index").listFiles()
            ?.filter { it.name.startsWith("Slide") && it.name.endsWith(".iwa") }
            ?.forEach { f ->
                val id = if (f.name == "Slide.iwa") -1L else slideIwaId(f.name) ?: return@forEach
                iwaNotes[id] = scanIwaForNoteText(f.readBytes())
            }
        val previewPdf = File(dir, PREVIEW_PDF_ENTRY)
        return Analysis(
            hasPreviewPdf = previewPdf.isFile && previewPdf.length() > 0,
            // A package-format legacy document declares the same relative paths; resolve them
            // against the bundle and hand back absolute ones, as this branch does throughout.
            orderedThumbnailEntries = resolveThumbnails(
                modern = orderThumbnails(thumbnails, slideIwaOrder),
                apxlXml = apxlXml,
                exists = { File(dir, it).isFile },
            ).map { if (File(it).isAbsolute) it else File(dir, it).absolutePath },
            notes = resolveNotes(apxlXml, iwaNotes)
        )
    }

    /** Extracts the embedded preview PDF to [dest]. Returns true when a non-empty PDF was written. */
    fun extractPreviewPdf(file: File, dest: File): Boolean {
        try {
            if (file.isDirectory) {
                val src = File(file, PREVIEW_PDF_ENTRY)
                if (!src.isFile || src.length() == 0L) return false
                src.copyTo(dest, overwrite = true)
                return true
            }
            ZipFile(file).use { zip ->
                val entry = zip.entries().asSequence()
                    .firstOrNull { !it.isDirectory && it.name.equals(PREVIEW_PDF_ENTRY, ignoreCase = true) }
                    ?: return false
                zip.getInputStream(entry).use { input ->
                    BufferedOutputStream(FileOutputStream(dest)).use { out -> input.copyTo(out) }
                }
            }
            return dest.length() > 0
        } catch (_: Exception) {
            dest.delete()
            return false
        }
    }

    /** Reads one thumbnail's bytes by the entry name/path recorded in [Analysis.orderedThumbnailEntries]. */
    fun readThumbnailBytes(file: File, entryName: String): ByteArray? {
        return try {
            if (file.isDirectory) {
                File(entryName).takeIf { it.isFile }?.readBytes()
            } else {
                ZipFile(file).use { zip ->
                    zip.getEntry(entryName)?.let { zip.getInputStream(it).readBytes() }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun isThumbnailEntry(name: String, base: String): Boolean {
        if (!base.lowercase().startsWith("st-")) return false
        if (base.substringAfterLast(".", "").lowercase() !in IMAGE_EXTENSIONS) return false
        // Thumbnails live in the Data/ directory of the bundle.
        return name.substringBeforeLast("/", "").endsWith("Data")
    }

    private fun slideIwaId(base: String): Long? =
        base.removePrefix("Slide-").removeSuffix(".iwa").split("-")[0].toLongOrNull()

    /**
     * Maps thumbnails into presentation order: `st-` files sorted by trailing id have the same
     * rank as the sorted `Slide-<id>.iwa` ids; the iwa entries' zip order is presentation order.
     */
    private fun orderThumbnails(thumbnails: List<String>, slideIwaOrder: List<Long>): List<String> {
        if (thumbnails.isEmpty()) return thumbnails
        val sortedByStId = thumbnails.sortedBy { entry ->
            entry.substringAfterLast("/").substringBeforeLast(".").split("-").lastOrNull()?.toLongOrNull()
                ?: Long.MAX_VALUE
        }
        if (slideIwaOrder.isEmpty()) return sortedByStId
        val iwaSorted = slideIwaOrder.sorted()
        val rankToThumbnail = sortedByStId.mapIndexed { rank, entry -> rank to entry }.toMap()
        val main = slideIwaOrder.mapNotNull { id -> rankToThumbnail[iwaSorted.indexOf(id)] }.distinct()
        return main + thumbnails.filter { it !in main }
    }

    private fun resolveNotes(apxlXml: String?, iwaNotes: Map<Long, String>): List<String> {
        apxlXml?.let { xml ->
            val parsed = parseApxlNotes(xml)
            if (parsed.isNotEmpty()) return parsed
        }
        if (iwaNotes.isNotEmpty()) {
            return iwaNotes.entries.sortedBy { it.key }.map { it.value }
        }
        return emptyList()
    }

    /**
     * Per-slide thumbnail paths a legacy document declares for itself, in document order.
     *
     * Shape: `<key:slide><key:thumbnails><key:binary><sf:data sf:path="thumbs/st3.jpg"/>`. The
     * lookup is scoped to the slide's own `thumbnails` child rather than any descendant `data`
     * element, because a slide's page content carries `sf:data` references to its images too —
     * taking the first one found anywhere would hand back a photo from the slide instead of the
     * slide's thumbnail. Master slides use a different element name and so never appear here.
     */
    internal fun parseApxlThumbnails(xml: String): List<String> {
        val result = mutableListOf<String>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
            val slides = doc.getElementsByTagNameNS("*", "slide")
            for (i in 0 until slides.length) {
                val thumbnails = childNamed(slides.item(i), "thumbnails") ?: continue
                val path = firstDataPath(thumbnails) ?: continue
                result.add(path)
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return result
    }

    private fun childNamed(node: Node, localName: String): Node? {
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.localName == localName) return child
        }
        return null
    }

    /** Depth-first search for the first `sf:data` carrying an `sf:path`. */
    private fun firstDataPath(node: Node): String? {
        if (node.localName == "data") {
            val path = node.attributes?.getNamedItemNS("*", "path")?.nodeValue
                ?: node.attributes?.getNamedItem("sf:path")?.nodeValue
            if (!path.isNullOrBlank()) return path
        }
        val children = node.childNodes
        for (i in 0 until children.length) {
            firstDataPath(children.item(i))?.let { return it }
        }
        return null
    }

    /** Legacy (pre-IWA) Keynote documents carry an XML manifest with per-slide notes. */
    private fun parseApxlNotes(xml: String): List<String> {
        val result = mutableListOf<String>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
            val slides = doc.getElementsByTagNameNS("*", "slide")
            for (i in 0 until slides.length) {
                val slide = slides.item(i)
                val notes = StringBuilder()
                val children = slide.childNodes
                for (j in 0 until children.length) {
                    val child = children.item(j)
                    if (child.localName == "notes") extractTextFromNode(child, notes)
                }
                result.add(notes.toString().trim())
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return result
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

    /**
     * Heuristic scan of a Slide iwa payload for the presenter-notes text field
     * (protobuf field tag bytes 0xB2 0x38 followed by a varint length). Replaced by a real
     * IWA parse in WS5; kept as the fallback for undecodable documents.
     */
    /** The presenter-notes field tag, and the varint decoding that follows it. */
    private const val NOTE_TAG_BYTE_0 = 0xB2
    private const val NOTE_TAG_BYTE_1 = 0x38
    private const val NOTE_TAG_BYTES = 3
    private const val BYTE_MASK = 0xFF
    private const val VARINT_PAYLOAD_MASK = 0x7F
    private const val VARINT_CONTINUATION_BIT = 0x80
    private const val VARINT_PAYLOAD_BITS = 7

    private fun scanIwaForNoteText(bytes: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size - NOTE_TAG_BYTES) {
            if ((bytes[i].toInt() and BYTE_MASK) == NOTE_TAG_BYTE_0 &&
                (bytes[i + 1].toInt() and BYTE_MASK) == NOTE_TAG_BYTE_1
            ) {
                var length = 0
                var shift = 0
                var j = i + 2
                while (j < bytes.size) {
                    val b = bytes[j].toInt() and 0xFF
                    length = length or ((b and VARINT_PAYLOAD_MASK) shl shift)
                    j++
                    if (b and VARINT_CONTINUATION_BIT == 0) break
                    shift += VARINT_PAYLOAD_BITS
                }
                if (length in 1..4096 && j + length <= bytes.size) {
                    try {
                        val s = String(bytes, j, length, Charsets.UTF_8)
                        if (sb.isNotEmpty()) sb.append("\n")
                        sb.append(s)
                    } catch (_: Exception) {
                    }
                }
            }
            i++
        }
        return sb.toString().trim()
    }
}
