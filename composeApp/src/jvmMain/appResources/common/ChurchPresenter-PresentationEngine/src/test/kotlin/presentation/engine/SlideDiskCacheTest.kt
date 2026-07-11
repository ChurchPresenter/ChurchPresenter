package presentation.engine

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import presentation.engine.cache.SlideDiskCache
import presentation.engine.model.DeckFormat
import presentation.engine.model.Fidelity
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlideDiskCacheTest {

    @TempDir
    lateinit var tempDir: File

    private fun image(color: Color): BufferedImage =
        BufferedImage(64, 36, BufferedImage.TYPE_INT_ARGB).also {
            val g = it.createGraphics()
            g.color = color
            g.fillRect(0, 0, 64, 36)
            g.dispose()
        }

    private fun newCache() = SlideDiskCache(File(tempDir, "cache"))

    private fun sourceFile() = File(tempDir, "deck.pptx").apply { if (!exists()) writeText("stub") }

    @Test
    fun `write commit lookup round-trips slides and notes`() {
        val cache = newCache()
        val source = sourceFile()
        val writer = cache.beginWrite(source, DeckFormat.PPTX, renderWidthPx = 1920)
        writer.putSlide(0, image(Color.RED), "note zero", Fidelity.NATIVE, hasTimeline = false)
        writer.putSlide(1, image(Color.BLUE), "", Fidelity.NATIVE, hasTimeline = false)
        writer.commit()

        val cached = assertNotNull(cache.lookup(source, renderWidthPx = 1920))
        assertEquals(2, cached.slideFiles.size)
        assertTrue(cached.slideFiles.all { it.isFile })
        assertEquals(listOf("note zero", ""), cached.notes)
        assertEquals(DeckFormat.PPTX, cached.manifest.format)
    }

    @Test
    fun `uncommitted write is invisible`() {
        val cache = newCache()
        val source = sourceFile()
        cache.beginWrite(source, DeckFormat.PPTX, 1920)
            .putSlide(0, image(Color.RED), "", Fidelity.NATIVE, false)
        assertNull(cache.lookup(source, 1920))
    }

    @Test
    fun `source mtime change invalidates`() {
        val cache = newCache()
        val source = sourceFile()
        val writer = cache.beginWrite(source, DeckFormat.PPTX, 1920)
        writer.putSlide(0, image(Color.RED), "", Fidelity.NATIVE, false)
        writer.commit()
        source.setLastModified(source.lastModified() + 5_000)
        assertNull(cache.lookup(source, 1920))
    }

    @Test
    fun `render width change invalidates`() {
        val cache = newCache()
        val source = sourceFile()
        val writer = cache.beginWrite(source, DeckFormat.PPTX, 1920)
        writer.putSlide(0, image(Color.RED), "", Fidelity.NATIVE, false)
        writer.commit()
        assertNull(cache.lookup(source, 1280))
    }

    @Test
    fun `legacy v1 entry (mtime txt, no manifest) is invalid`() {
        val cache = newCache()
        val source = sourceFile()
        val dir = cache.dirFor(source).apply { mkdirs() }
        File(dir, "slide_0000.jpg").writeBytes(Fixtures.jpegBytes(64, 36, Color.RED))
        File(dir, "mtime.txt").writeText(source.lastModified().toString())
        assertNull(cache.lookup(source, 1920))
    }

    @Test
    fun `cleanupOrphaned keeps only listed sources`() {
        val cache = newCache()
        val keep = sourceFile()
        val drop = File(tempDir, "old.pptx").apply { writeText("stub") }
        for (f in listOf(keep, drop)) {
            val writer = cache.beginWrite(f, DeckFormat.PPTX, 1920)
            writer.putSlide(0, image(Color.RED), "", Fidelity.NATIVE, false)
            writer.commit()
        }
        cache.cleanupOrphaned(listOf(keep.absolutePath))
        assertNotNull(cache.lookup(keep, 1920))
        assertNull(cache.lookup(drop, 1920))
    }
}
