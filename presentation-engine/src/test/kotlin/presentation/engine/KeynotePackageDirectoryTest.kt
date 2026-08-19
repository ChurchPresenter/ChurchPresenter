package presentation.engine

import presentation.engine.keynote.KeynoteStaticSupport
import presentation.engine.model.DeckFormat
import presentation.engine.model.DeckLoadError
import presentation.engine.model.Fidelity
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Keynote's **package** form: a `.key` that is a directory on disk rather than a zip.
 *
 * Keynote writes one or the other depending on the version and on "Copy audio and video into
 * document", and the two are read by completely separate code paths — the zip one walks entries,
 * this one walks files. A document that opens fine as a zip therefore says nothing about the same
 * document as a folder, which is what these tests are for: the same thumbnails, the same
 * slide-order rule, the same notes and the same preview-PDF preference, reached the other way.
 *
 * Paths coming out of this branch are **absolute**, unlike the zip branch's entry names — the
 * rasterizer opens them directly.
 */
class KeynotePackageDirectoryTest {

    private val temp: File = Files.createTempDirectory("keynote-package-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    /** A `.key` directory bundle: `Index/Slide-<id>.iwa` per slide, `Data/st-<n>.jpg` thumbnails. */
    private fun bundle(
        name: String = "package.key",
        slideIds: List<Long> = listOf(1L, 2L),
        thumbnails: List<String> = listOf("st-1.jpg", "st-2.jpg"),
        notes: Map<Long, String> = emptyMap(),
        previewPdf: ByteArray? = null,
    ): File {
        val dir = File(temp, name).apply { mkdirs() }
        File(dir, "Index").mkdirs()
        File(dir, "Data").mkdirs()
        for (id in slideIds) {
            File(dir, "Index/Slide-$id.iwa").writeBytes(iwaWithNote(notes[id]))
        }
        for (thumb in thumbnails) {
            File(dir, "Data/$thumb").writeBytes(jpeg(Color.GRAY))
        }
        if (previewPdf != null) {
            File(dir, "QuickLook").mkdirs()
            File(dir, "QuickLook/Preview.pdf").writeBytes(previewPdf)
        }
        return dir
    }

    /**
     * Slide payload bytes carrying the presenter-notes field: tag `0xB2 0x38`, a varint length,
     * then UTF-8 — the shape the notes scanner looks for.
     */
    private fun iwaWithNote(text: String?): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 0, 0))
        if (text != null) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            out.write(0xB2)
            out.write(0x38)
            var length = bytes.size
            while (true) {
                val b = length and 0x7F
                length = length ushr 7
                out.write(if (length > 0) b or 0x80 else b)
                if (length == 0) break
            }
            out.write(bytes)
        }
        out.write(byteArrayOf(0, 0, 0, 0))
        return out.toByteArray()
    }

    private fun jpeg(color: Color): ByteArray {
        val image = BufferedImage(64, 36, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { paint = color; fillRect(0, 0, 64, 36); dispose() }
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", out)
        return out.toByteArray()
    }

    // ── Static analysis of the folder ─────────────────────────────────────────

    @Test
    fun `thumbnails in a package bundle are found and handed back as absolute paths`() {
        val dir = bundle()
        val analysis = KeynoteStaticSupport.analyze(dir)

        assertEquals(2, analysis.orderedThumbnailEntries.size)
        assertTrue(
            analysis.orderedThumbnailEntries.all { File(it).isAbsolute && File(it).isFile },
            "the rasterizer opens these directly, so they have to be real absolute paths: " +
                "${analysis.orderedThumbnailEntries}",
        )
    }

    @Test
    fun `thumbnails follow the slide order the Index declares`() {
        // st-9 belongs to the first slide and st-3 to the second: name order is not slide order.
        val dir = bundle(slideIds = listOf(9L, 3L), thumbnails = listOf("st-3.jpg", "st-9.jpg"))
        val entries = KeynoteStaticSupport.analyze(dir).orderedThumbnailEntries

        assertContentEquals(
            listOf("st-9.jpg", "st-3.jpg"),
            entries.map { File(it).name },
            "slide order comes from Index/Slide-<id>.iwa, not from the thumbnail file names",
        )
    }

    @Test
    fun `notes are scanned out of each slide's payload`() {
        val dir = bundle(notes = mapOf(1L to "opening prayer", 2L to "read slowly"))
        val notes = KeynoteStaticSupport.analyze(dir).notes

        assertTrue(notes.any { it.contains("opening prayer") }, "got $notes")
        assertTrue(notes.any { it.contains("read slowly") }, "got $notes")
    }

    @Test
    fun `a slide with no notes contributes an empty entry rather than shifting the rest`() {
        val dir = bundle(slideIds = listOf(1L, 2L), notes = mapOf(2L to "only the second"))
        val notes = KeynoteStaticSupport.analyze(dir).notes

        assertEquals(2, notes.size, "one entry per slide, in slide order: $notes")
        assertEquals("", notes[0])
        assertTrue(notes[1].contains("only the second"))
    }

    @Test
    fun `a package with no thumbnails and no preview reports nothing to show`() {
        val dir = bundle(slideIds = emptyList(), thumbnails = emptyList())
        val analysis = KeynoteStaticSupport.analyze(dir)

        assertTrue(analysis.orderedThumbnailEntries.isEmpty())
        assertTrue(analysis.notes.isEmpty())
        assertTrue(!analysis.hasPreviewPdf)
    }

    @Test
    fun `an empty preview file does not count as a preview`() {
        val dir = bundle(previewPdf = ByteArray(0))
        assertTrue(!KeynoteStaticSupport.analyze(dir).hasPreviewPdf, "a zero-byte Preview.pdf is not one")
    }

    @Test
    fun `non-image files in Data are not mistaken for thumbnails`() {
        val dir = bundle()
        File(dir, "Data/st-notes.txt").writeText("not a thumbnail")
        File(dir, "Data/image.jpg").writeBytes(jpeg(Color.RED))

        val entries = KeynoteStaticSupport.analyze(dir).orderedThumbnailEntries
        assertEquals(2, entries.size, "only st-*.jpg images count, got ${entries.map { File(it).name }}")
    }

    // ── End to end through the loader ─────────────────────────────────────────

    @Test
    fun `a package bundle loads as a static deck, one slide per thumbnail`() {
        val dir = bundle(notes = mapOf(1L to "first slide notes"))
        val result = assertIs<LoadResult.Success>(PresentationLoader.load(dir))

        assertEquals(DeckFormat.KEYNOTE, result.deck.format)
        assertEquals(2, result.deck.slideCount)
        assertTrue(result.deck.slides.all { it.fidelity == Fidelity.STATIC_FALLBACK })
        assertTrue(result.deck.slides[0].notes.contains("first slide notes"))
        assertEquals("", result.deck.slides[1].notes)
    }

    @Test
    fun `a package bundle's embedded preview PDF decides the slide count and page size`() {
        // The preview PDF is preferred over thumbnails: it is vector, and it states its geometry.
        val pdfBytes = Fixtures.createPdf(temp, pages = 3, name = "preview-source.pdf").readBytes()
        val dir = bundle(name = "with-preview.key", previewPdf = pdfBytes)

        val result = assertIs<LoadResult.Success>(PresentationLoader.load(dir))
        assertEquals(3, result.deck.slideCount, "three PDF pages, three slides")
        assertEquals(720.0, result.deck.slideWidthPt, 1.0)
        assertEquals(405.0, result.deck.slideHeightPt, 1.0)
    }

    @Test
    fun `an unreadable preview PDF falls back to the thumbnails and says so`() {
        val dir = bundle(name = "broken-preview.key", previewPdf = "not a pdf at all".toByteArray())

        val result = assertIs<LoadResult.Success>(PresentationLoader.load(dir))
        assertEquals(2, result.deck.slideCount, "the two thumbnails carry the deck")
        assertTrue(
            result.deck.warnings.any { it.contains("preview", ignoreCase = true) },
            "the degrade has to be recorded, got ${result.deck.warnings}",
        )
    }

    @Test
    fun `a package bundle with nothing in it fails as an empty document rather than throwing`() {
        val dir = bundle(name = "empty.key", slideIds = emptyList(), thumbnails = emptyList())

        val result = assertIs<LoadResult.Failure>(PresentationLoader.load(dir))
        assertEquals(DeckLoadError.EMPTY_DOCUMENT, result.error)
    }
}
