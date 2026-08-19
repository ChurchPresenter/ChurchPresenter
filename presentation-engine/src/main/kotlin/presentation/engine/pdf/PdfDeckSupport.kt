package presentation.engine.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import presentation.engine.model.DeckLoadError
import java.io.File

/** Metadata read from a PDF without rendering anything. */
internal data class PdfMetadata(
    val pageCount: Int,
    val pageWidthPt: Double,
    val pageHeightPt: Double
)

internal sealed interface PdfMetadataResult {
    data class Success(val metadata: PdfMetadata) : PdfMetadataResult
    data class Failure(val error: DeckLoadError, val detail: String?) : PdfMetadataResult
}

internal object PdfDeckSupport {

    fun readMetadata(file: File): PdfMetadataResult {
        return try {
            PDDocument.load(file).use { document ->
                if (document.numberOfPages == 0) {
                    return PdfMetadataResult.Failure(DeckLoadError.EMPTY_DOCUMENT, null)
                }
                val mediaBox = document.getPage(0).mediaBox
                PdfMetadataResult.Success(
                    PdfMetadata(
                        pageCount = document.numberOfPages,
                        pageWidthPt = mediaBox.width.toDouble(),
                        pageHeightPt = mediaBox.height.toDouble()
                    )
                )
            }
        } catch (e: InvalidPasswordException) {
            PdfMetadataResult.Failure(DeckLoadError.PASSWORD_PROTECTED, e.message)
        } catch (e: Exception) {
            PdfMetadataResult.Failure(DeckLoadError.PARSE_FAILED, e.message)
        }
    }
}
