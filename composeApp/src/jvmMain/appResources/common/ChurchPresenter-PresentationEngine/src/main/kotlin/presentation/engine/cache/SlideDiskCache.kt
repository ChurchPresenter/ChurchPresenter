package presentation.engine.cache

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import presentation.engine.DeckRasterizer
import presentation.engine.model.DeckFormat
import presentation.engine.model.Fidelity
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Disk cache of rendered final-frame slides, shared by the Presentation tab and the companion
 * server (one render, both consumers).
 *
 * Layout per entry (`<baseDir>/<id>/`):
 *  - `slide_%04d.jpg` — final-frame JPEG per slide
 *  - `note_%04d.txt`  — speaker notes, only when non-blank
 *  - `manifest.json`  — written last; its presence marks the entry valid (a crashed render
 *    leaves no manifest and is re-rendered on next open)
 *
 * Schema v1 entries (bare `mtime.txt`, no manifest) are treated as invalid and re-rendered once.
 */
class SlideDiskCache(
    private val baseDir: File = File(System.getProperty("user.home"), ".churchpresenter/slides")
) {

    companion object {
        const val SCHEMA_VERSION = 2

        /** Stable id per source path — same derivation the companion API has always used. */
        fun idFor(absolutePath: String): String = absolutePath.hashCode().toUInt().toString(16)
    }

    @Serializable
    data class Manifest(
        val schemaVersion: Int,
        val sourcePath: String,
        val sourceMtime: Long,
        val format: DeckFormat,
        val slideCount: Int,
        val renderWidthPx: Int,
        val slides: List<SlideEntry>
    )

    @Serializable
    data class SlideEntry(val fidelity: Fidelity, val hasTimeline: Boolean)

    data class CachedSlides(
        val manifest: Manifest,
        val slideFiles: List<File>,
        val notes: List<String>
    )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun dirFor(file: File): File = File(baseDir, idFor(file.absolutePath))

    /**
     * Returns the cached slides when the entry is valid for the file's current mtime and render
     * width, else null. Pass a null [renderWidthPx] to accept any width (consumers like the
     * companion API that serve whatever resolution was rendered).
     */
    fun lookup(file: File, renderWidthPx: Int?): CachedSlides? {
        val dir = dirFor(file)
        val manifestFile = File(dir, "manifest.json")
        if (!manifestFile.isFile) return null
        val manifest = try {
            json.decodeFromString(Manifest.serializer(), manifestFile.readText())
        } catch (_: Exception) {
            return null
        }
        if (manifest.schemaVersion != SCHEMA_VERSION) return null
        if (manifest.sourceMtime != file.lastModified()) return null
        if (renderWidthPx != null && manifest.renderWidthPx != renderWidthPx) return null
        val slideFiles = (0 until manifest.slideCount).map { File(dir, slideName(it)) }
        if (slideFiles.any { !it.isFile }) return null
        val notes = (0 until manifest.slideCount).map { i ->
            File(dir, noteName(i)).takeIf { it.exists() }?.readText() ?: ""
        }
        return CachedSlides(manifest, slideFiles, notes)
    }

    fun invalidate(file: File) {
        dirFor(file).deleteRecursively()
    }

    /**
     * Deletes cache entries whose source path is not in [keepPaths]. Session-scoped entries
     * (`remote_*`, used for instance-link mirrored slides) are always deleted — they are
     * re-downloaded on demand.
     */
    fun cleanupOrphaned(keepPaths: Collection<String>) {
        if (!baseDir.exists()) return
        val keepIds = keepPaths.map { idFor(it) }.toSet()
        baseDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory && dir.name !in keepIds) dir.deleteRecursively()
        }
    }

    fun beginWrite(file: File, format: DeckFormat, renderWidthPx: Int): Writer =
        Writer(dirFor(file), file, format, renderWidthPx)

    private fun slideName(index: Int) = "slide_%04d.jpg".format(index)
    private fun noteName(index: Int) = "note_%04d.txt".format(index)

    /**
     * Incremental cache writer: slides are written as they render so a consumer can stream them,
     * and the manifest lands last as the commit marker. [abort] (or a crash) leaves an invalid,
     * self-healing entry.
     */
    inner class Writer internal constructor(
        val dir: File,
        private val sourceFile: File,
        private val format: DeckFormat,
        private val renderWidthPx: Int
    ) {
        private val entries = mutableListOf<SlideEntry>()

        init {
            dir.deleteRecursively()
            dir.mkdirs()
        }

        /** Writes one slide; returns the JPEG file. Alpha is flattened onto white for JPEG. */
        fun putSlide(
            index: Int,
            image: BufferedImage,
            note: String,
            fidelity: Fidelity,
            hasTimeline: Boolean
        ): File {
            val slideFile = File(dir, slideName(index))
            ImageIO.write(DeckRasterizer.flattenToRgb(image), "jpg", slideFile)
            if (note.isNotBlank()) File(dir, noteName(index)).writeText(note)
            entries.add(SlideEntry(fidelity, hasTimeline))
            return slideFile
        }

        fun commit() {
            val manifest = Manifest(
                schemaVersion = SCHEMA_VERSION,
                sourcePath = sourceFile.absolutePath,
                sourceMtime = sourceFile.lastModified(),
                format = format,
                slideCount = entries.size,
                renderWidthPx = renderWidthPx,
                slides = entries.toList()
            )
            File(dir, "manifest.json").writeText(json.encodeToString(Manifest.serializer(), manifest))
        }

        fun abort() {
            dir.deleteRecursively()
        }
    }
}
