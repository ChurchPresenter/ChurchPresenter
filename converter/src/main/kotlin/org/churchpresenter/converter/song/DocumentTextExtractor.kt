package converter.song

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File

data class ExtractionResult(
    val success: Boolean,
    val text: String,
    val errorMessage: String? = null
)

object DocumentTextExtractor {

    private val supportedExtensions = setOf("pdf", "docx", "pptx")

    fun isSupported(file: File): Boolean {
        return file.extension.lowercase() in supportedExtensions
    }

    // POI and PDFBox raise unchecked exceptions of their own for a file that is damaged, the
    // wrong format, or password-protected, and the panel's job is to say so rather than to fail.
    // Narrowing this would mean naming every type two libraries might raise.
    @Suppress("TooGenericExceptionCaught")
    fun extract(file: File): ExtractionResult {
        return try {
            val text = when (file.extension.lowercase()) {
                "pdf" -> extractPdf(file)
                "docx" -> extractDocx(file)
                "pptx" -> extractPptx(file)
                else -> return ExtractionResult(false, "", "Unsupported file format: .${file.extension}")
            }
            if (text.isBlank()) {
                ExtractionResult(true, "", "No text content found in document")
            } else {
                ExtractionResult(true, text)
            }
        } catch (e: Exception) {
            ExtractionResult(false, "", "Failed to extract text: ${e.message}")
        }
    }

    private fun extractPdf(file: File): String {
        PDDocument.load(file).use { document ->
            val stripper = PDFTextStripper()
            return stripper.getText(document).trim()
        }
    }

    private fun extractDocx(file: File): String =
        file.inputStream().use { stream ->
            XWPFDocument(stream).use { document ->
                document.paragraphs.joinToString("\n") { it.text.trim() }.trim()
            }
        }

    private fun extractPptx(file: File): String =
        file.inputStream().use { stream ->
            XMLSlideShow(stream).use { pptx ->
                pptx.slides.joinToString("\n\n") { slide -> slideText(slide) }.trim()
            }
        }

    /** Every text shape on one slide, in the order the deck lays them out. */
    private fun slideText(slide: XSLFSlide): String =
        slide.shapes.filterIsInstance<XSLFTextShape>()
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
}
