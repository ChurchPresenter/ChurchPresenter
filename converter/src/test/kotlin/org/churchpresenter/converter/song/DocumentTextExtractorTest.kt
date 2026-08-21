package org.churchpresenter.converter.song

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.nio.file.Files
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
            val layout = show.slideMasters.first().getLayout(org.apache.poi.xslf.usermodel.SlideLayout.TITLE_ONLY)
            slideText.forEach { text ->
                val slide = show.createSlide(layout)
                slide.placeholders.firstOrNull()?.text = text
            }
            file.outputStream().use { show.write(it) }
        }
        return file
    }

    // ── Supported formats ─────────────────────────────────────────────────────

    @Test
    fun `the three document formats are supported and others are not`() {
        assertTrue(DocumentTextExtractor.isSupported(File("a.pdf")))
        assertTrue(DocumentTextExtractor.isSupported(File("a.docx")))
        assertTrue(DocumentTextExtractor.isSupported(File("a.pptx")))
        assertTrue(!DocumentTextExtractor.isSupported(File("a.txt")))
        assertTrue(!DocumentTextExtractor.isSupported(File("a.doc")), "the legacy binary format is not read")
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
    fun `a document that cannot be read yields the error and no songs`() {
        val file = File(temp, "broken.pdf").apply { writeBytes(byteArrayOf(9, 9, 9)) }
        val (message, songs) = MarkdownToSongConverter.preview(file)
        assertTrue(songs.isEmpty())
        assertTrue(message.isNotBlank(), "the failure reason takes the place of the preview text")
    }
}
