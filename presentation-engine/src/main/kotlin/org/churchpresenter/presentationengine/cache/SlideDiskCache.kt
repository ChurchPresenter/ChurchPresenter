package org.churchpresenter.presentationengine.cache

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.churchpresenter.presentationengine.DeckRasterizer
import org.churchpresenter.presentationengine.model.DeckFormat
import org.churchpresenter.presentationengine.model.Fidelity
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

/**
 * Thrown when a [SlideDiskCache.Writer] is asked to keep writing an entry that another writer (or
 * an invalidate) has since taken over. The render that raised it must stop and not commit; the
 * writer that superseded it owns the files now.
 */
class SlideCacheSupersededException(dir: File) :
    IOException("Slide cache entry ${dir.absolutePath} was taken over by another writer")

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
        /** Cache directory names are the hash in hex — short, and filesystem-safe. */
        private const val HEX_RADIX = 16

        const val SCHEMA_VERSION = 2

        /** Stable id per source path — same derivation the companion API has always used. */
        fun idFor(absolutePath: String): String = absolutePath.hashCode().toUInt().toString(HEX_RADIX)

        /**
         * The writer that currently owns each entry directory, keyed by its absolute path.
         *
         * An entry dir is keyed on the source path alone, so several renders can target the same
         * one: the tab and the companion server render the same deck, and re-selecting a file
         * starts a new render while the cancelled one is still in its loop. Every deletion path
         * ([Writer.init], [Writer.abort], [invalidate], [cleanupOrphaned]) used to wipe the dir
         * unconditionally, so whichever render lost the race carried on writing into a directory
         * that no longer existed — `ImageIO.write` then failed with "Can't create an
         * ImageOutputStream!" from the middle of the deck onwards.
         *
         * Ownership is process-wide because every [SlideDiskCache] instance shares one base dir.
         */
        private val activeWriters = ConcurrentHashMap<String, Writer>()

        /** Why a write could not happen, for the exception message. */
        private fun diagnose(dir: File): String =
            "dir exists=${dir.isDirectory}, writable=${dir.canWrite()}, usableBytes=${dir.usableSpace}"
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

    /**
     * Deletes the entry for [file]. An in-flight render of that file is disowned rather than left
     * writing into a deleted directory: its next `putSlide` throws [SlideCacheSupersededException].
     */
    fun invalidate(file: File) {
        val dir = dirFor(file)
        activeWriters.remove(dir.absolutePath)
        dir.deleteRecursively()
    }

    /**
     * Deletes cache entries whose source path is not in [keepPaths]. Session-scoped entries
     * (`remote_*`, used for instance-link mirrored slides) are always deleted — they are
     * re-downloaded on demand. An entry a render is currently writing is left alone; it is
     * cleaned up by the next call once that render has finished.
     */
    fun cleanupOrphaned(keepPaths: Collection<String>) {
        if (!baseDir.exists()) return
        val keepIds = keepPaths.map { idFor(it) }.toSet()
        baseDir.listFiles()?.forEach { dir ->
            val busy = activeWriters.containsKey(dir.absolutePath)
            if (dir.isDirectory && dir.name !in keepIds && !busy) dir.deleteRecursively()
        }
    }

    /** True while some render owns [file]'s entry — a second render should wait rather than start. */
    fun isWriting(file: File): Boolean = activeWriters.containsKey(dirFor(file).absolutePath)

    fun beginWrite(file: File, format: DeckFormat, renderWidthPx: Int): Writer =
        Writer(dirFor(file), file, format, renderWidthPx)

    private fun slideName(index: Int) = "slide_%04d.jpg".format(index)
    private fun noteName(index: Int) = "note_%04d.txt".format(index)

    /**
     * Incremental cache writer: slides are written as they render so a consumer can stream them,
     * and the manifest lands last as the commit marker. [abort] (or a crash) leaves an invalid,
     * self-healing entry.
     *
     * Creating a writer takes ownership of the entry directory (see [activeWriters]): a writer
     * that has been superseded stops at its next [putSlide] or [commit] with a
     * [SlideCacheSupersededException], and its [abort] no longer deletes the files that now
     * belong to the newer writer.
     */
    inner class Writer internal constructor(
        val dir: File,
        private val sourceFile: File,
        private val format: DeckFormat,
        private val renderWidthPx: Int
    ) {
        private val entries = mutableListOf<SlideEntry>()
        private val key = dir.absolutePath

        init {
            activeWriters[key] = this
            try {
                dir.deleteRecursively()
                ensureDir()
            } catch (e: Throwable) {
                activeWriters.remove(key, this)
                throw e
            }
        }

        private fun isCurrent(): Boolean = activeWriters[key] === this

        /** Creates the entry directory, failing loudly (and once) instead of per slide. */
        private fun ensureDir() {
            if (dir.isDirectory) return
            if (!dir.mkdirs() && !dir.isDirectory) {
                throw IOException("Cannot create slide cache directory $key (${diagnose(dir)})")
            }
        }

        /** Writes one slide; returns the JPEG file. Alpha is flattened onto white for JPEG. */
        fun putSlide(
            index: Int,
            image: BufferedImage,
            note: String,
            fidelity: Fidelity,
            hasTimeline: Boolean
        ): File {
            if (!isCurrent()) throw SlideCacheSupersededException(dir)
            ensureDir()
            val slideFile = File(dir, slideName(index))
            val written = try {
                ImageIO.write(DeckRasterizer.flattenToRgb(image), "jpg", slideFile)
            } catch (e: IOException) {
                // The bare ImageIO message ("Can't create an ImageOutputStream!") names neither the
                // file nor why it could not be opened; say both.
                throw IOException("Failed to write slide $index to $slideFile (${diagnose(dir)})", e)
            }
            if (!written) throw IOException("No JPEG writer accepted slide $index for $slideFile")
            if (note.isNotBlank()) File(dir, noteName(index)).writeText(note)
            entries.add(SlideEntry(fidelity, hasTimeline))
            return slideFile
        }

        fun commit() {
            if (!activeWriters.remove(key, this)) throw SlideCacheSupersededException(dir)
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

        /** Discards this writer's entry — unless another writer owns it now, which leaves it alone. */
        fun abort() {
            if (activeWriters.remove(key, this)) dir.deleteRecursively()
        }
    }
}
