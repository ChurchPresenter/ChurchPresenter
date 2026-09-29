package org.churchpresenter.app.churchpresenter.utils

import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Calendar Manager's preset preview: a deck's first few slides at thumbnail width. */
class SlideThumbnailsTest {

    private val dir: File = Files.createTempDirectory("cp-slide-thumbnails").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun pdf(pages: Int): File = File(dir, "deck.pdf").also { file ->
        PDDocument().use { doc ->
            repeat(pages) { doc.addPage(PDPage()) }
            doc.save(file)
        }
    }

    @Test
    fun `the first slides are rendered, no more than asked for, at thumbnail width`() {
        val thumbs = runBlocking { slideThumbnails(pdf(pages = 3).absolutePath, max = 2) }

        assertEquals(2, thumbs.size)
        assertTrue(thumbs.all { it.width == 320 }, "widths were ${thumbs.map { it.width }}")
    }

    @Test
    fun `a deck shorter than the limit gives every slide it has`() {
        assertEquals(1, runBlocking { slideThumbnails(pdf(pages = 1).absolutePath, max = 4) }.size)
    }

    @Test
    fun `a file that cannot be opened previews as nothing rather than failing`() {
        assertEquals(emptyList(), runBlocking { slideThumbnails(File(dir, "missing.pdf").absolutePath, max = 3) })
    }
}
