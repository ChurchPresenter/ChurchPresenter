package org.churchpresenter.app.churchpresenter.utils

import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The Schedule rows' thumbnails (#1050): which rows get one, where it comes from, and what is kept. */
class ScheduleRowThumbnailsTest {

    private val dir: File = Files.createTempDirectory("cp-schedule-thumbnails").toFile()
    private val cacheDir = File(dir, "cache")

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun thumbnails(videoFrame: (File) -> File? = { null }) =
        ScheduleRowThumbnails(deckCacheDir = { cacheDir }, videoFrame = videoFrame)

    private fun png(name: String, width: Int, height: Int, folder: File = dir): File =
        File(folder, name).also { file ->
            folder.mkdirs()
            ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", file)
        }

    private fun pdf(): File = File(dir, "deck.pdf").also { file ->
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            doc.save(file)
        }
    }

    private fun picture(folder: File, imagePath: String = "") = ScheduleItem.PictureItem(
        id = "p", folderPath = folder.absolutePath, folderName = folder.name, imageCount = 1, imagePath = imagePath,
    )

    private fun media(path: String, type: String = "local") =
        ScheduleItem.MediaItem(id = "m", mediaUrl = path, mediaTitle = "clip", mediaType = type)

    private fun deck(file: File) = ScheduleItem.PresentationItem(
        id = "d", filePath = file.absolutePath, fileName = file.name, slideCount = 1, fileType = file.extension,
    )

    @Test
    fun `a single picture row shows that picture, scaled down to a thumbnail`() {
        val image = png("photo.png", 800, 400)
        val thumb = assertNotNull(runBlocking { thumbnails().thumbnail(picture(dir, image.absolutePath)) })
        assertEquals(192, thumb.width)
        assertEquals(96, thumb.height)
    }

    @Test
    fun `a folder row shows its first picture by name, skipping what is not a picture`() {
        val folder = File(dir, "gallery")
        png("b.png", 20, 40, folder)
        png("A.png", 40, 20, folder)
        File(folder, "0-notes.txt").writeText("not a picture")
        val thumb = assertNotNull(runBlocking { thumbnails().thumbnail(picture(folder)) })
        assertTrue(thumb.width > thumb.height, "A.png, the wide one, comes first by name")
    }

    @Test
    fun `a folder with no pictures, or one that is gone, has no thumbnail`() {
        val empty = File(dir, "empty").also { it.mkdirs() }
        assertNull(runBlocking { thumbnails().thumbnail(picture(empty)) })
        assertNull(runBlocking { thumbnails().thumbnail(picture(File(dir, "missing"))) })
    }

    @Test
    fun `a deck row shows its first slide, and keeps it on disk`() {
        val provider = thumbnails()
        assertNotNull(runBlocking { provider.thumbnail(deck(pdf())) })
        assertEquals(1, cacheDir.listFiles { f -> f.extension == "jpg" }?.size, "the slide is saved for next time")
        assertNotNull(runBlocking { thumbnails().thumbnail(deck(File(dir, "deck.pdf"))) }, "and read back from disk")
    }

    @Test
    fun `a deck that cannot be opened has no thumbnail`() {
        val broken = File(dir, "broken.pptx").also { it.writeText("not a deck") }
        assertNull(runBlocking { thumbnails().thumbnail(deck(broken)) })
        assertTrue(cacheDir.listFiles().isNullOrEmpty(), "nothing is cached for it")
    }

    @Test
    fun `a video row shows its first frame, whatever its type is called`() {
        val clip = File(dir, "loop.mp4").also { it.writeText("video") }
        val frame = png("frame.png", 64, 36)
        val asked = mutableListOf<File>()
        val provider = thumbnails { video -> asked += video; frame }
        assertNotNull(runBlocking { provider.thumbnail(media(clip.absolutePath, type = "video")) })
        assertEquals(listOf(clip), asked)
    }

    @Test
    fun `audio, a stream, a missing clip and a clip ffmpeg cannot read have no thumbnail`() {
        val song = File(dir, "song.mp3").also { it.writeText("audio") }
        val clip = File(dir, "clip.mov").also { it.writeText("video") }
        val frame = png("frame.png", 64, 36)
        assertNull(runBlocking { thumbnails { frame }.thumbnail(media(song.absolutePath, type = "audio")) })
        assertNull(runBlocking { thumbnails { frame }.thumbnail(media("https://example.org/live.m3u8", type = "url")) })
        assertNull(runBlocking { thumbnails { frame }.thumbnail(media(File(dir, "gone.mp4").absolutePath)) })
        assertNull(runBlocking { thumbnails { null }.thumbnail(media(clip.absolutePath)) })
    }

    @Test
    fun `rows the output draws itself have no thumbnail`() {
        val provider = thumbnails()
        listOf(
            ScheduleItem.SongItem(id = "s", songNumber = 1, title = "Song", songbook = "Book"),
            ScheduleItem.WebsiteItem(id = "w", url = "https://example.org"),
            ScheduleItem.AnnouncementItem(id = "a", text = "Notice"),
        ).forEach { item -> assertNull(runBlocking { provider.thumbnail(item) }, "$item") }
    }

    @Test
    fun `a thumbnail is decoded once, and again when its file changes`() {
        val image = png("photo.png", 200, 100)
        val provider = thumbnails()
        val item = picture(dir, image.absolutePath)
        val first = runBlocking { provider.thumbnail(item) }
        assertSame(first, runBlocking { provider.thumbnail(item) }, "kept in memory")

        png("photo.png", 300, 100)
        image.setLastModified(image.lastModified() + 5_000)
        assertNotSame(first, runBlocking { provider.thumbnail(item) }, "a replaced file is read again")
    }

    @Test
    fun `the memory holds only so many thumbnails`() {
        val provider = ScheduleRowThumbnails(deckCacheDir = { cacheDir }, videoFrame = { null }, capacity = 2)
        val items = (1..3).map { n -> picture(dir, png("p$n.png", 10, 10).absolutePath) }
        val first = runBlocking { provider.thumbnail(items[0]) }
        runBlocking { items.drop(1).forEach { provider.thumbnail(it) } }
        assertNotSame(first, runBlocking { provider.thumbnail(items[0]) }, "the oldest was let go")
    }
}
