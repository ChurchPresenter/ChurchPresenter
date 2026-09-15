package org.churchpresenter.calendar.model

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.ByteArrayInputStream
import java.io.File

private const val TITLE_SIZE = 16f
private const val SUB_SIZE = 9f
private const val SECTION_SIZE = 9f
private const val ROW_SIZE = 10f
private const val META_SIZE = 8f

private const val MARGIN = 48f
private const val ROW_HEIGHT = 18f
private const val SECTION_GAP = 10f
private const val TIME_COLUMN = 46f
private const val DURATION_COLUMN = 52f

/**
 * The run of show as a one-page-per-however-many-rows PDF, for the band and the booth.
 *
 * Deliberately plain: a heading, then one line per row with its clock time, its title and its
 * planned length, with section names as rules between them. It is a sheet somebody holds, not a
 * reproduction of the screen.
 *
 * [font] supplies the embedded TrueType face — see [org.churchpresenter.calendar.CalendarHost.pdfFont].
 * When it returns null the built-in Helvetica stands in, and any character that face cannot encode
 * is replaced rather than thrown on: a Cyrillic song library would otherwise fail the whole export
 * on its first row.
 */
fun exportRunOfShowPdf(
    service: PlannedService,
    target: File,
    dateLabel: String,
    font: (bold: Boolean) -> ByteArray?,
) {
    PDDocument().use { document ->
        val regular = loadFont(document, font(false), PDType1Font.HELVETICA)
        val bold = loadFont(document, font(true), PDType1Font.HELVETICA_BOLD)
        val embedded = regular !is PDType1Font

        var page = newPage(document)
        var stream = PDPageContentStream(document, page)
        var y = page.mediaBox.height - MARGIN

        try {
            y = drawHeading(stream, service, dateLabel, bold, regular, embedded, y)
            val clocks = runClocks(service)

            service.items.forEach { item ->
                if (y < MARGIN + ROW_HEIGHT) {
                    stream.close()
                    page = newPage(document)
                    stream = PDPageContentStream(document, page)
                    y = page.mediaBox.height - MARGIN
                }
                y = if (item is ScheduleItem.LabelItem) {
                    drawSection(stream, item, bold, embedded, y)
                } else {
                    drawRow(stream, item, clocks[item.id], service.plannedSeconds[item.id], regular, embedded, y)
                }
            }
        } finally {
            stream.close()
        }
        document.save(target)
    }
}

private fun newPage(document: PDDocument): PDPage = PDPage(PDRectangle.A4).also { document.addPage(it) }

private fun loadFont(document: PDDocument, bytes: ByteArray?, fallback: PDFont): PDFont =
    bytes?.let { runCatching { PDType0Font.load(document, ByteArrayInputStream(it), true) }.getOrNull() } ?: fallback

private fun drawHeading(
    stream: PDPageContentStream,
    service: PlannedService,
    dateLabel: String,
    bold: PDFont,
    regular: PDFont,
    embedded: Boolean,
    top: Float,
): Float {
    var y = top
    stream.text(service.name, MARGIN, y, bold, TITLE_SIZE, embedded)
    y -= TITLE_SIZE + 4f

    val total = service.plannedTotalSeconds()
    val meta = buildString {
        append(dateLabel)
        append(" · ")
        append(service.startTime)
        if (total > 0) {
            append(" · ")
            append(formatDuration(total))
        }
    }
    stream.text(meta, MARGIN, y, regular, SUB_SIZE, embedded)
    y -= SUB_SIZE + 10f

    stream.rule(MARGIN, y, PDRectangle.A4.width - MARGIN)
    return y - 14f
}

private fun drawSection(
    stream: PDPageContentStream,
    item: ScheduleItem.LabelItem,
    bold: PDFont,
    embedded: Boolean,
    top: Float,
): Float {
    val y = top - SECTION_GAP
    stream.text(item.text.uppercase(), MARGIN, y, bold, SECTION_SIZE, embedded)
    stream.rule(MARGIN, y - 4f, PDRectangle.A4.width - MARGIN)
    return y - ROW_HEIGHT
}

private fun drawRow(
    stream: PDPageContentStream,
    item: ScheduleItem,
    clock: RowClock?,
    plannedSeconds: Int?,
    regular: PDFont,
    embedded: Boolean,
    top: Float,
): Float {
    val right = PDRectangle.A4.width - MARGIN
    stream.text(clock?.time.orEmpty(), MARGIN, top, regular, META_SIZE, embedded)
    stream.text(item.displayText, MARGIN + TIME_COLUMN, top, regular, ROW_SIZE, embedded)
    if (plannedSeconds != null) {
        stream.text(formatDuration(plannedSeconds), right - DURATION_COLUMN, top, regular, META_SIZE, embedded)
    }
    return top - ROW_HEIGHT
}

/** One line of text, with whatever the font cannot encode replaced rather than thrown on. */
private fun PDPageContentStream.text(
    value: String,
    x: Float,
    y: Float,
    font: PDFont,
    size: Float,
    embedded: Boolean,
) {
    if (value.isEmpty()) return
    beginText()
    setFont(font, size)
    newLineAtOffset(x, y)
    showText(if (embedded) value else value.toWinAnsiSafe())
    endText()
}

private fun PDPageContentStream.rule(fromX: Float, y: Float, toX: Float) {
    setLineWidth(0.5f)
    moveTo(fromX, y)
    lineTo(toX, y)
    stroke()
}

/**
 * [value] with every character the built-in Helvetica cannot encode replaced by `?`.
 *
 * Only reached when no TrueType font was supplied. PDFBox throws `IllegalArgumentException` from
 * `showText` on the first unencodable character, which would abandon the export part-written — so
 * the substitution happens here, where it costs a legible placeholder instead of a failure.
 */
private fun String.toWinAnsiSafe(): String =
    map { if (it.code in WIN_ANSI_RANGE || it in WIN_ANSI_EXTRA) it else '?' }.joinToString("")

private val WIN_ANSI_RANGE = 0x20..0xFF
private val WIN_ANSI_EXTRA = charArrayOf('‘', '’', '“', '”', '–', '—', '•')
