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
import kotlin.test.assertTrue

class ScheduleRowThumbnailsCacheTest {

    private val dir: File = Files.createTempDirectory("cp-schedule-thumbnails-cache").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun pdf(): File = File(dir, "deck.pdf").also { file ->
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            doc.save(file)
        }
    }

    private fun deck(file: File) = ScheduleItem.PresentationItem(
        id = "d", filePath = file.absolutePath, fileName = file.name, slideCount = 1, fileType = file.extension,
    )

    @Test
    fun `an empty slide left in the disk cache is rendered again`() {
        val cacheDir = File(dir, "cache")
        val deck = deck(pdf())
        assertNotNull(runBlocking { ScheduleRowThumbnails(deckCacheDir = { cacheDir }).thumbnail(deck) })
        val cached = cacheDir.listFiles { f -> f.extension == "jpg" }.orEmpty().single()
        cached.writeBytes(ByteArray(0))

        assertNotNull(runBlocking { ScheduleRowThumbnails(deckCacheDir = { cacheDir }).thumbnail(deck) })
        assertTrue(cached.length() > 0, "the slide is written again")
    }

    @Test
    fun `a slide that cannot be saved to disk is still shown, and nothing half-written is left`() {
        val blocker = File(dir, "not-a-folder").also { it.writeText("a file where the cache folder should be") }
        val cacheDir = File(blocker, "cache")

        assertNotNull(runBlocking { ScheduleRowThumbnails(deckCacheDir = { cacheDir }).thumbnail(deck(pdf())) })
        assertTrue(!cacheDir.exists())
    }

    @Test
    fun `a folder row passes over a sub-folder named like a picture`() {
        val folder = File(dir, "gallery").also { it.mkdirs() }
        File(folder, "a.png").mkdirs()
        ImageIO.write(BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "png", File(folder, "b.png"))
        val item = ScheduleItem.PictureItem(
            id = "p", folderPath = folder.absolutePath, folderName = "gallery", imageCount = 1,
        )

        val thumb = assertNotNull(runBlocking { ScheduleRowThumbnails(deckCacheDir = { dir }).thumbnail(item) })
        assertEquals(true, thumb.width > thumb.height)
    }
}
