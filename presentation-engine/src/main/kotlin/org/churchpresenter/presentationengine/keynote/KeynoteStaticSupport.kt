package org.churchpresenter.presentationengine.keynote

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

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
 * (WS5 replaces this heuristic with a real IWA parse.) The ordering rules are [KeynoteThumbnails].
 */
internal object KeynoteStaticSupport {

    internal const val PREVIEW_PDF_ENTRY = "QuickLook/Preview.pdf"

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
        val scan = KeynoteZipScan()
        ZipFile(file).use { zip ->
            zip.entries().asSequence().filterNot { it.isDirectory }.forEach { scan.accept(zip, it) }
            // Second pass detail: notes for Slide-<id>.iwa entries (skipped above to keep the
            // hot path single-purpose; only read when the deck actually has slide iwa entries).
            if (scan.slideIwaOrder.isNotEmpty()) scan.iwaNotes.putAll(slideIwaNotes(zip))
        }

        return Analysis(
            hasPreviewPdf = scan.hasPreviewPdf,
            orderedThumbnailEntries = KeynoteThumbnails.resolve(
                modern = KeynoteThumbnails.order(scan.thumbnailEntries, scan.slideIwaOrder),
                apxlXml = scan.apxlXml,
                exists = { it in scan.allEntryNames },
            ),
            notes = resolveNotes(scan.apxlXml, scan.iwaNotes)
        )
    }

    /** The notes of every `Slide-<id>.iwa` entry in [zip], by id. */
    private fun slideIwaNotes(zip: ZipFile): Map<Long, String> =
        zip.entries().asSequence()
            .mapNotNull { entry ->
                val base = entry.name.substringAfterLast("/")
                if (base.startsWith("Slide-") && base.endsWith(".iwa")) slideIwaId(base)?.let { it to entry } else null
            }
            .associate { (id, entry) -> id to IwaNoteScanner.scan(zip.getInputStream(entry).readBytes()) }

    private fun analyzeDirectory(dir: File): Analysis {
        // A package document is a directory, and File.listFiles hands back whatever order the
        // filesystem stores — near-sorted on APFS, arbitrary on ext4. The zip branch can rely on
        // entry order because Keynote writes it deliberately; a folder carries no such signal, so
        // both lists are sorted instead. Without this the slide order — and with it which thumbnail
        // belongs to which slide — differs from one machine to the next, which is exactly what it
        // did: the same document opened with its slides in one order here and another on CI.
        val thumbnails = File(dir, "Data").listFiles()
            ?.filter { KeynoteThumbnails.isPackageThumbnail(it) }
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
                iwaNotes[id] = IwaNoteScanner.scan(f.readBytes())
            }
        val previewPdf = File(dir, PREVIEW_PDF_ENTRY)
        return Analysis(
            hasPreviewPdf = previewPdf.isFile && previewPdf.length() > 0,
            // A package-format legacy document declares the same relative paths; resolve them
            // against the bundle and hand back absolute ones, as this branch does throughout.
            orderedThumbnailEntries = KeynoteThumbnails.resolve(
                modern = KeynoteThumbnails.order(thumbnails, slideIwaOrder),
                apxlXml = apxlXml,
                exists = { File(dir, it).isFile },
            ).map { if (File(it).isAbsolute) it else File(dir, it).absolutePath },
            notes = resolveNotes(apxlXml, iwaNotes)
        )
    }

    /** Extracts the embedded preview PDF to [dest]. Returns true when a non-empty PDF was written. */
    fun extractPreviewPdf(file: File, dest: File): Boolean = try {
        if (file.isDirectory) copyPreviewFromDirectory(file, dest) else copyPreviewFromZip(file, dest)
    } catch (_: Exception) {
        dest.delete()
        false
    }

    private fun copyPreviewFromDirectory(dir: File, dest: File): Boolean {
        val src = File(dir, PREVIEW_PDF_ENTRY)
        if (!src.isFile || src.length() == 0L) return false
        src.copyTo(dest, overwrite = true)
        return true
    }

    private fun copyPreviewFromZip(file: File, dest: File): Boolean {
        val copied = ZipFile(file).use { zip ->
            val entry = zip.entries().asSequence()
                .firstOrNull { !it.isDirectory && it.name.equals(PREVIEW_PDF_ENTRY, ignoreCase = true) }
            entry?.let { zip.copyEntryTo(it, dest) } != null
        }
        return copied && dest.length() > 0
    }

    /** Reads one thumbnail's bytes by the entry name/path recorded in [Analysis.orderedThumbnailEntries]. */
    fun readThumbnailBytes(file: File, entryName: String): ByteArray? = try {
        if (file.isDirectory) File(entryName).takeIf { it.isFile }?.readBytes() else readZipEntry(file, entryName)
    } catch (_: Exception) {
        null
    }

    internal fun slideIwaId(base: String): Long? =
        base.removePrefix("Slide-").removeSuffix(".iwa").split("-")[0].toLongOrNull()

    private fun resolveNotes(apxlXml: String?, iwaNotes: Map<Long, String>): List<String> {
        val parsed = apxlXml?.let { ApxlManifest.parseNotes(it) }.orEmpty()
        return when {
            parsed.isNotEmpty() -> parsed
            else -> iwaNotes.entries.sortedBy { it.key }.map { it.value }
        }
    }
}

/** One entry's bytes, or null when [file] has no entry of that name. */
private fun readZipEntry(file: File, entryName: String): ByteArray? =
    ZipFile(file).use { zip -> zip.getEntry(entryName)?.let { zip.getInputStream(it).readBytes() } }

private fun ZipFile.copyEntryTo(entry: ZipEntry, dest: File) {
    getInputStream(entry).use { input ->
        BufferedOutputStream(FileOutputStream(dest)).use { out -> input.copyTo(out) }
    }
}
