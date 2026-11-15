package org.churchpresenter.presentationengine

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WordDocumentPresentationTest {
    @Test
    fun `Word documents are accepted by the presentation loader`() {
        assertTrue(PresentationLoader.SUPPORTED_EXTENSIONS.containsAll(setOf("doc", "docx")))
    }

    @Test
    fun `converted Word pages render through the PDF pipeline and retain the original document path`() {
        val root = Files.createTempDirectory("word-presentation-test").toFile()
        val source = root.resolve("sermon.docx").apply { writeText("source document") }
        val cachedPdf = WordDocumentPdf.cacheFile(source)
        try {
            cachedPdf.parentFile.mkdirs()
            PDDocument().use { pdf ->
                pdf.addPage(PDPage())
                pdf.addPage(PDPage())
                pdf.save(cachedPdf)
            }
            val result = assertIs<LoadResult.Success>(PresentationLoader.load(source))
            assertEquals(source, result.deck.sourceFile)
            assertEquals(2, result.deck.slideCount)
            DeckRasterizer(result.deck, targetWidthPx = 640).use { renderer ->
                assertEquals(640, renderer.renderFinalFrame(0).width)
            }
        } finally {
            cachedPdf.parentFile.deleteRecursively()
            root.deleteRecursively()
        }
    }
}
