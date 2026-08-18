package converter.song

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xslf.usermodel.XMLSlideShow
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

    private fun extractDocx(file: File): String {
        file.inputStream().use { stream ->
            XWPFDocument(stream).use { document ->
                val sb = StringBuilder()
                for (paragraph in document.paragraphs) {
                    val text = paragraph.text.trim()
                    if (text.isNotEmpty()) {
                        sb.appendLine(text)
                    } else {
                        sb.appendLine()
                    }
                }
                return sb.toString().trim()
            }
        }
    }

    private fun extractPptx(file: File): String {
        file.inputStream().use { stream ->
            XMLSlideShow(stream).use { pptx ->
                val sb = StringBuilder()
                for ((idx, slide) in pptx.slides.withIndex()) {
                    if (idx > 0) sb.appendLine()
                    for (shape in slide.shapes) {
                        if (shape is XSLFTextShape) {
                            val text = shape.text.trim()
                            if (text.isNotEmpty()) {
                                sb.appendLine(text)
                            }
                        }
                    }
                }
                return sb.toString().trim()
            }
        }
    }
}
