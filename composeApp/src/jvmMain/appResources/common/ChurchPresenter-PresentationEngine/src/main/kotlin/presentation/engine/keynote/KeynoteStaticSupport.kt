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

        ZipFile(file).use { zip ->
            for (entry in zip.entries()) {
                if (entry.isDirectory) continue
                val name = entry.name
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
            orderedThumbnailEntries = orderThumbnails(thumbnailEntries, slideIwaOrder),
            notes = resolveNotes(apxlXml, iwaNotes)
        )
    }

    private fun analyzeDirectory(dir: File): Analysis {
        val dataDir = File(dir, "Data")
        val thumbnails = dataDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS && it.name.lowercase().startsWith("st-") }
            ?.map { it.absolutePath }
            ?: emptyList()
        val slideIwaOrder = File(dir, "Index").listFiles()
            ?.map { it.name }
            ?.filter { it.startsWith("Slide-") && it.endsWith(".iwa") }
            ?.mapNotNull { slideIwaId(it) }
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
            orderedThumbnailEntries = orderThumbnails(thumbnails, slideIwaOrder),
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
    private fun scanIwaForNoteText(bytes: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size - 3) {
            if ((bytes[i].toInt() and 0xFF) == 0xB2 && (bytes[i + 1].toInt() and 0xFF) == 0x38) {
                var length = 0
                var shift = 0
                var j = i + 2
                while (j < bytes.size) {
                    val b = bytes[j].toInt() and 0xFF
                    length = length or ((b and 0x7F) shl shift)
                    j++
                    if (b and 0x80 == 0) break
                    shift += 7
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
