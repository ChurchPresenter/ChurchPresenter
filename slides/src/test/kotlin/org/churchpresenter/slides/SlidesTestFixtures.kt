package org.churchpresenter.slides

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.churchpresenter.slides.viewmodel.PresentationViewModel
import java.io.File

/** A real PDF of [pages] text slides — what the loader rasterises like any deck a church hands over. */
fun pdfDeck(dir: File, pages: Int = 3, name: String = "deck.pdf"): File {
    val file = File(dir, name)
    PDDocument().use { doc ->
        repeat(pages) { index ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA_BOLD, PDF_FONT_SIZE)
                stream.newLineAtOffset(PDF_TEXT_X, PDF_TEXT_Y)
                stream.showText("Slide ${index + 1}")
                stream.endText()
            }
        }
        doc.save(file)
    }
    return file
}

/** Waits for [vm] to finish loading whatever it was given, on the deck's own signal. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.awaitDeck(vm: PresentationViewModel) {
    waitUntil("the deck to finish loading", DECK_TIMEOUT_MS) { !vm.isLoading && vm.slideFiles.isNotEmpty() }
    waitForIdle()
}

private const val PDF_FONT_SIZE = 36f
private const val PDF_TEXT_X = 72f
private const val PDF_TEXT_Y = 500f
private const val DECK_TIMEOUT_MS = 10_000L
