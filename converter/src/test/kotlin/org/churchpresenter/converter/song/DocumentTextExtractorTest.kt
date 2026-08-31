package org.churchpresenter.converter.song

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hslf.usermodel.HSLFTextBox
import org.apache.poi.xslf.usermodel.SlideLayout
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.awt.Rectangle
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pulling song text out of the documents people actually have: PDFs, Word files and slide decks.
 *
 * The fixtures are real documents built with the same libraries that read them back, rather than
 * checked-in binaries — so the tests stay readable, and a POI or PDFBox upgrade that changes the
 * round trip fails here instead of in a user's import.
 */
class DocumentTextExtractorTest {

    private val temp: File = Files.createTempDirectory("converter-extract-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun pdf(vararg lines: String): File {
        val file = File(temp, "doc.pdf")
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(50f, 700f)
                lines.forEach { line ->
                    stream.showText(line)
                    stream.newLineAtOffset(0f, -16f)
                }
                stream.endText()
            }
            doc.save(file)
        }
        return file
    }

    private fun docx(vararg paragraphs: String): File {
        val file = File(temp, "doc.docx")
        XWPFDocument().use { doc ->
            paragraphs.forEach { text ->
                doc.createParagraph().createRun().setText(text)
            }
            file.outputStream().use { doc.write(it) }
        }
        return file
    }

    private fun pptx(vararg slideText: String): File {
        val file = File(temp, "deck.pptx")
        XMLSlideShow().use { show ->
            val layout = show.slideMasters.first().getLayout(SlideLayout.TITLE_ONLY)
            slideText.forEach { text ->
                val slide = show.createSlide(layout)
                slide.placeholders.firstOrNull()?.text = text
            }
            file.outputStream().use { show.write(it) }
        }
        return file
    }

    /** A legacy binary deck, one text box per slide. */
    private fun ppt(vararg slideText: String): File {
        val file = File(temp, "deck.ppt")
        HSLFSlideShow().use { show ->
            slideText.forEach { text ->
                val box = HSLFTextBox().apply {
                    this.text = text
                    anchor = Rectangle(50, 50, 500, 100)
                }
                show.createSlide().addShape(box)
            }
            file.outputStream().use { show.write(it) }
        }
        return file
    }

    /**
     * A `.key` whose IWA body is unreadable but which carries the `QuickLook/Preview.pdf` Keynote
     * embeds in every document it saves.
     *
     * This is the fallback tier, and it is what the converter can build here without hand-writing
     * an IWA object graph — the native path is covered in `:presentation-engine`'s KeynoteTextTest.
     */
    private fun keynote(vararg pageText: String): File {
        val preview = File(temp, "preview.pdf")
        PDDocument().use { doc ->
            pageText.forEach { text ->
                val page = PDPage()
                doc.addPage(page)
                PDPageContentStream(doc, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 24f)
                    stream.newLineAtOffset(50f, 700f)
                    stream.showText(text)
                    stream.endText()
                }
            }
            doc.save(preview)
        }
        val file = File(temp, "deck.key")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("Index/Slide-1.iwa"))
            zip.write(ByteArray(4))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("QuickLook/Preview.pdf"))
            zip.write(preview.readBytes())
            zip.closeEntry()
        }
        return file
    }

    // ── Supported formats ─────────────────────────────────────────────────────

    @Test
    fun `the five document formats are supported and others are not`() {
        assertTrue(DocumentTextExtractor.isSupported(File("a.pdf")))
        assertTrue(DocumentTextExtractor.isSupported(File("a.docx")))
        assertTrue(DocumentTextExtractor.isSupported(File("a.pptx")))
        assertTrue(DocumentTextExtractor.isSupported(File("a.ppt")), "the legacy deck format is read")
        assertTrue(DocumentTextExtractor.isSupported(File("a.key")), "Keynote is read")
        assertTrue(!DocumentTextExtractor.isSupported(File("a.txt")))
        assertTrue(!DocumentTextExtractor.isSupported(File("a.doc")), "the legacy Word format is not read")
    }

    @Test
    fun `the extension check ignores case`() {
        assertTrue(DocumentTextExtractor.isSupported(File("SONG.PDF")))
    }

    // ── Extraction ────────────────────────────────────────────────────────────

    @Test
    fun `text is extracted from a PDF`() {
        val result = DocumentTextExtractor.extract(pdf("Amazing grace how sweet the sound"))
        assertTrue(result.success, result.errorMessage)
        assertTrue(result.text.contains("Amazing grace"), "got: '${result.text}'")
    }

    @Test
    fun `text is extracted from a Word document, one paragraph per line`() {
        val result = DocumentTextExtractor.extract(docx("Verse 1", "Amazing grace how sweet"))
        assertTrue(result.success, result.errorMessage)
        assertTrue(result.text.contains("Verse 1"))
        assertTrue(result.text.contains("Amazing grace how sweet"))
    }

    @Test
    fun `text is extracted from every slide of a deck`() {
        val result = DocumentTextExtractor.extract(pptx("First slide text", "Second slide text"))
        assertTrue(result.success, result.errorMessage)
        assertTrue(result.text.contains("First slide text"))
        assertTrue(result.text.contains("Second slide text"), "later slides are not dropped")
    }

    @Test
    fun `text is extracted from every slide of a legacy deck`() {
        val result = DocumentTextExtractor.extract(ppt("First slide text", "Second slide text"))
        assertTrue(result.success, result.errorMessage)
        assertTrue(result.text.contains("First slide text"))
        assertTrue(result.text.contains("Second slide text"), "later slides are not dropped")
    }

    @Test
    fun `text is extracted from every slide of a Keynote deck`() {
        val result = DocumentTextExtractor.extract(keynote("Amazing grace", "How sweet the sound"))
        assertTrue(result.success, result.errorMessage)
        assertTrue(result.text.contains("Amazing grace"))
        assertTrue(result.text.contains("How sweet the sound"), "later slides are not dropped")
    }

    @Test
    fun `a Keynote deck's slides are separated so they parse as their own sections`() {
        // A blank line between slides is what MarkdownToSongConverter splits verses on.
        val result = DocumentTextExtractor.extract(keynote("Verse one line", "Verse two line"))
        assertTrue(result.text.contains("\n\n"), "got: '${result.text}'")
    }

    @Test
    fun `a Keynote deck saved as a package directory reads the same as a zip`() {
        // Keynote writes both forms, and the older documents that need the preview fallback are the
        // ones most likely to be bundles.
        val bundle = File(temp, "bundle.key").apply { mkdirs() }
        File(bundle, "QuickLook").mkdirs()
        val zipped = keynote("Bundled all the same")
        java.util.zip.ZipFile(zipped).use { zip ->
            val entry = zip.getEntry("QuickLook/Preview.pdf")
            File(bundle, "QuickLook/Preview.pdf").writeBytes(zip.getInputStream(entry).readBytes())
        }
        val result = DocumentTextExtractor.extract(bundle)
        assertTrue(result.success, result.errorMessage)
        assertTrue(result.text.contains("Bundled all the same"), "got: '${result.text}'")
    }

    @Test
    fun `a Keynote deck that yields no text says so rather than failing`() {
        val file = File(temp, "empty.key")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("Index/Slide-1.iwa"))
            zip.write(ByteArray(4))
            zip.closeEntry()
        }
        val result = DocumentTextExtractor.extract(file)
        assertTrue(result.success, "reading succeeded")
        assertEquals("", result.text)
        assertTrue(result.errorMessage!!.contains("No text"), "got: ${result.errorMessage}")
    }

    @Test
    fun `an unsupported extension is refused by name, without being opened`() {
        val file = File(temp, "notes.txt").apply { writeText("some text") }
        val result = DocumentTextExtractor.extract(file)
        assertTrue(!result.success)
        assertTrue(result.errorMessage!!.contains("Unsupported"), "got: ${result.errorMessage}")
    }

    @Test
    fun `a corrupt document reports a failure rather than throwing`() {
        // Users do drag in half-downloaded files; the importer has to survive it.
        val file = File(temp, "broken.docx").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        val result = DocumentTextExtractor.extract(file)
        assertTrue(!result.success)
        assertTrue(!result.errorMessage.isNullOrBlank(), "the user is told something went wrong")
    }

    @Test
    fun `an empty document succeeds but says there was no text`() {
        // Distinct from a failure: the file was read fine, it just has nothing in it.
        val result = DocumentTextExtractor.extract(docx())
        assertTrue(result.success, "reading succeeded")
        assertEquals("", result.text)
        assertTrue(result.errorMessage!!.contains("No text"), "got: ${result.errorMessage}")
    }

    @Test
    fun `an extracted document flows straight into the song parser`() {
        // The path a real import takes: preview() extracts, then parses.
        val file = docx("# Amazing Grace", "Verse 1", "Amazing grace how sweet")
        val (text, songs) = MarkdownToSongConverter.preview(file)
        assertTrue(text.contains("Amazing grace"), "the raw text is returned for the preview pane")
        assertEquals("Amazing Grace", songs.single().title)
    }

    @Test
    fun `a Keynote deck flows straight into the song parser`() {
        // The path a real import takes for a .key: extract, then parse into songs.
        val (text, songs) = MarkdownToSongConverter.preview(keynote("# Amazing Grace", "Amazing grace how sweet"))
        assertTrue(text.contains("Amazing grace how sweet"), "the raw text is returned for the preview pane")
        assertEquals("Amazing Grace", songs.single().title)
    }

    @Test
    fun `a document that cannot be read yields the error and no songs`() {
        val file = File(temp, "broken.pdf").apply { writeBytes(byteArrayOf(9, 9, 9)) }
        val (message, songs) = MarkdownToSongConverter.preview(file)
        assertTrue(songs.isEmpty())
        assertTrue(message.isNotBlank(), "the failure reason takes the place of the preview text")
    }
}
