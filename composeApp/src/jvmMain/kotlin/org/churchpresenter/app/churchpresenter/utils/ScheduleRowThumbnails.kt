package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.media.utils.VideoFirstFrame
import org.churchpresenter.presentationengine.DeckRasterizer
import org.churchpresenter.presentationengine.LoadResult
import org.churchpresenter.presentationengine.PresentationLoader
import org.churchpresenter.schedule.ScheduleThumbnails
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.slides.utils.PictureDecoder
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.imageio.ImageIO

/**
 * The Schedule rows' thumbnails (#1050): a picture row's image (a folder's first), a deck's first
 * slide and a local video's first frame. Every other row has none and keeps its type chip.
 *
 * Held in memory by the source file's path, modification time and length, so scrolling and
 * reordering never decode twice and a file replaced under the same name is read again. A deck's
 * first slide is also kept on disk under [deckCacheDir], since rasterizing a large PPTX can take
 * longer than the row is worth waiting on; a video's frame already is, by [VideoFirstFrame].
 *
 * [renders] bounds how many are made at once, so opening a long schedule does not start a deck
 * parse or an ffmpeg run for every row together.
 */
internal class ScheduleRowThumbnails(
    private val deckCacheDir: () -> File = {
        File(System.getProperty("user.home"), ".churchpresenter/cache/schedule-thumbnails")
    },
    private val videoFrame: (File) -> File? = { VideoFirstFrame.extract(it) },
    renders: Int = CONCURRENT_RENDERS,
    private val capacity: Int = MEMORY_CAPACITY,
) : ScheduleThumbnails {

    private val gate = Semaphore(renders)
    private val memory = object : LinkedHashMap<String, ImageBitmap>(capacity, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>) = size > capacity
    }

    override suspend fun thumbnail(item: ScheduleItem): ImageBitmap? {
        val source = sourceOf(item) ?: return null
        val key = cacheKey(source.file)
        synchronized(memory) { memory[key] }?.let { return it }
        val bitmap = gate.withPermit {
            when (source.kind) {
                SourceKind.PICTURE -> picture(source.file)
                SourceKind.DECK -> deckFirstSlide(source.file, key)
                SourceKind.VIDEO -> videoFrame(source.file)?.let(::picture)
            }
        } ?: return null
        synchronized(memory) { memory[key] = bitmap }
        return bitmap
    }

    private fun picture(file: File): ImageBitmap? =
        PictureDecoder.decodeScaledOrNull(file, THUMB_WIDTH_PX, THUMB_HEIGHT_PX)?.toComposeImageBitmap()

    /** The deck's first slide, from the disk cache when it has been rendered before. */
    private fun deckFirstSlide(file: File, key: String): ImageBitmap? {
        val cached = File(deckCacheDir(), sha1(key) + ".jpg")
        val fromDisk = if (cached.isFile && cached.length() > 0) picture(cached) else null
        return fromDisk ?: renderFirstSlide(file)?.let { slide ->
            try {
                cached.parentFile.mkdirs()
                ImageIO.write(DeckRasterizer.flattenToRgb(slide), "jpg", cached)
            } catch (_: IOException) {
                cached.delete()
            }
            slide.toComposeImageBitmap()
        }
    }

    /** The deck's first slide with every build complete, or null when it cannot be opened. */
    private fun renderFirstSlide(file: File): BufferedImage? {
        val deck = (PresentationLoader.load(file) as? LoadResult.Success)?.deck?.takeIf { it.slideCount > 0 }
        return deck?.let {
            runCatching { DeckRasterizer(it, DECK_RENDER_WIDTH_PX).use { r -> r.renderFinalFrame(0) } }.getOrNull()
        }
    }

    private enum class SourceKind { PICTURE, DECK, VIDEO }

    private class Source(val file: File, val kind: SourceKind)

    private companion object {
        const val THUMB_WIDTH_PX = 192
        const val THUMB_HEIGHT_PX = 108
        const val DECK_RENDER_WIDTH_PX = 320
        const val CONCURRENT_RENDERS = 2
        const val MEMORY_CAPACITY = 64
        const val LOAD_FACTOR = 0.75f

        fun sourceOf(item: ScheduleItem): Source? = when (item) {
            is ScheduleItem.PictureItem -> pictureFile(item)?.let { Source(it, SourceKind.PICTURE) }
            is ScheduleItem.PresentationItem -> existing(item.filePath)?.let { Source(it, SourceKind.DECK) }
            // By the file, not `mediaType`: the Media tab writes "local", but a dropped file, a remote
            // add or an import may carry another word for the same clip.
            is ScheduleItem.MediaItem -> existing(item.mediaUrl)
                ?.takeIf { it.extension.lowercase() in Constants.VIDEO_EXTENSIONS }
                ?.let { Source(it, SourceKind.VIDEO) }
            else -> null
        }

        /** The one picture the row is for, or the folder's first by name. */
        fun pictureFile(item: ScheduleItem.PictureItem): File? =
            existing(item.imagePath) ?: File(item.folderPath).listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in Constants.PICTURE_EXTENSIONS }
                ?.minByOrNull { it.name.lowercase() }

        fun existing(path: String): File? = path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }

        fun cacheKey(file: File): String = "${file.absolutePath}:${file.lastModified()}:${file.length()}"

        fun sha1(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
