package org.churchpresenter.presentationengine

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.churchpresenter.presentationengine.keynote.KeynoteDeckParser
import org.churchpresenter.presentationengine.keynote.KeynoteStaticSupport
import org.churchpresenter.presentationengine.keynote.KnDrawable
import org.churchpresenter.presentationengine.keynote.KnSlide
import java.io.File

/**
 * The words on a Keynote deck's slides, for callers that want its text rather than its pixels.
 *
 * The rest of this module's public surface hands back a [org.churchpresenter.presentationengine.model.Deck]
 * of layers and a rasterizer — everything needed to *show* a deck and nothing that reads it. The
 * converter needs the opposite: the lyrics, so it can write them out as songs. The IWA parsing that
 * answers that already exists here in full, so this exposes it instead of it being written twice.
 *
 * Speaker notes are deliberately left out: they are the presenter's, not the audience's, and a song
 * imported with them folded in gains verses nobody sang.
 */
object KeynoteText {

    /**
     * The text of every slide in [file], one entry per slide and in slide order.
     *
     * [file] is a `.key`, in either of the two forms Keynote writes — a zip, or a package
     * directory. Modern documents are read from their IWA archives; one that cannot be parsed that
     * way (password-protected, or an iWork '09 document whose body is `index.apxl`) falls back to
     * the `QuickLook/Preview.pdf` Keynote embeds, which carries the same words as real PDF text.
     *
     * Never throws: a damaged, empty or unreadable file comes back as an empty list, matching
     * [PresentationLoader.load]'s contract.
     */
    fun slideTexts(file: File): List<String> {
        val native = nativeSlideTexts(file)
        return if (native.any { it.isNotBlank() }) native else previewPdfSlideTexts(file)
    }

    // ── The modern path: the IWA object graph ────────────────────────────────

    // The parser reaches into an archive written by another program, and the contract above is that
    // an unreadable deck is an empty result rather than a thrown exception. Naming the types a
    // hand-rolled protobuf reader can raise on malformed bytes would be naming most of them.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun nativeSlideTexts(file: File): List<String> =
        try {
            KeynoteDeckParser.parse(file)?.slides?.map { slideText(it) } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

    /**
     * One slide's text boxes, read top-to-bottom and then left-to-right.
     *
     * The parser hands drawables back in z-order, which is the order they were *created* — a title
     * typed after the verse under it comes second. Sorting by position is what makes a slide read
     * the way it looks.
     */
    private fun slideText(slide: KnSlide): String =
        textBoxes(slide.drawables.map { it.drawable }, offsetX = 0.0, offsetY = 0.0)
            .sortedWith(compareBy({ it.y }, { it.x }))
            .map { it.text }
            .filter { it.isNotBlank() }
            .joinToString("\n")

    /** A text box and where it sits on the slide, in absolute slide points. */
    private data class PlacedText(val x: Double, val y: Double, val text: String)

    /**
     * Every text box in [drawables], with group children resolved to absolute positions.
     *
     * A group's children are stored relative to its origin, so a grouped verse sorts against the
     * rest of the slide only once the parent's offset is added.
     */
    private fun textBoxes(drawables: List<KnDrawable>, offsetX: Double, offsetY: Double): List<PlacedText> =
        drawables.flatMap { drawable ->
            val geometry = drawable.geometry
            when (drawable) {
                is KnDrawable.Text ->
                    listOf(PlacedText(offsetX + geometry.x, offsetY + geometry.y, paragraphText(drawable)))
                is KnDrawable.Group ->
                    textBoxes(
                        drawable.children.map { it.drawable },
                        offsetX + geometry.x,
                        offsetY + geometry.y
                    )
                else -> emptyList()
            }
        }

    /** One text box's paragraphs, one per line. */
    private fun paragraphText(text: KnDrawable.Text): String =
        text.paragraphs.joinToString("\n") { it.text.trim() }.trim()

    // ── The fallback: the preview PDF Keynote embeds ─────────────────────────

    /**
     * The embedded `QuickLook/Preview.pdf`'s text, one entry per page.
     *
     * Keynote writes this preview into every document it saves, and its pages are the slides, so a
     * deck this module cannot parse natively still yields its words.
     */
    private fun previewPdfSlideTexts(file: File): List<String> {
        val temp = createTempPdf() ?: return emptyList()
        return try {
            if (KeynoteStaticSupport.extractPreviewPdf(file, temp)) pdfPageTexts(temp) else emptyList()
        } finally {
            temp.delete()
        }
    }

    // A temp file the JVM refuses to create (a full or read-only temp dir) is the same outcome as a
    // deck with no preview in it: no text, and no exception out of a function documented not to
    // throw one.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun createTempPdf(): File? =
        try {
            File.createTempFile("keynote-preview", ".pdf")
        } catch (_: Exception) {
            null
        }

    // PDFBox raises unchecked exceptions of its own for a truncated or encrypted document, and a
    // preview that will not open is a deck with no readable text rather than a failure to report.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun pdfPageTexts(pdf: File): List<String> =
        try {
            PDDocument.load(pdf).use { document ->
                (1..document.numberOfPages).map { page -> pageText(document, page) }
            }
        } catch (_: Exception) {
            emptyList()
        }

    /** One page's text. A stripper is per-page: it holds the range it was configured with. */
    private fun pageText(document: PDDocument, page: Int): String =
        PDFTextStripper().apply {
            startPage = page
            endPage = page
        }.getText(document).trim()
}
