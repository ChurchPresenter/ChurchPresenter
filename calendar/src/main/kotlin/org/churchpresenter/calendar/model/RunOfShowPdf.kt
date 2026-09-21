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

private const val TITLE_GAP = 4f
private const val META_GAP = 10f
private const val HEADING_GAP = 14f
private const val SECTION_RULE_GAP = 4f
private const val RULE_WIDTH = 0.5f

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
 *
 * [use24Hour] is the calendar's clock format, so the sheet reads the way the window does.
 */
fun exportRunOfShowPdf(
    service: PlannedService,
    target: File,
    dateLabel: String,
    font: (bold: Boolean) -> ByteArray?,
    use24Hour: Boolean = true,
) {
    PDDocument().use { document ->
        val faces = Faces(
            regular = loadFont(document, font(false)) { PDType1Font.HELVETICA },
            bold = loadFont(document, font(true)) { PDType1Font.HELVETICA_BOLD },
        )
        drawSheet(document, faces, service, headingMeta(service, dateLabel, use24Hour), use24Hour)
        document.save(target)
    }
}

private fun drawSheet(
    document: PDDocument,
    faces: Faces,
    service: PlannedService,
    meta: String,
    use24Hour: Boolean,
) {
    val clocks = runClocks(service).mapValues { (_, clock) -> clockText(clock.time, use24Hour) }
    var page = SheetPage(document, faces)
    try {
        page.heading(service.name, meta)
        for (item in service.items) {
            if (!page.hasRoomForRow) {
                page.close()
                page = SheetPage(document, faces)
            }
            when (item) {
                is ScheduleItem.LabelItem -> page.section(item)
                else -> page.row(item, clocks[item.id].orEmpty(), service.plannedSeconds[item.id])
            }
        }
    } finally {
        page.close()
    }
}

/**
 * The embedded face made from [bytes], or [fallback]'s built-in one.
 *
 * [fallback] is a lambda because merely *naming* `PDType1Font.HELVETICA` runs PDFBox's static
 * initializer, which builds its font mapper and scans every font installed on the machine — seconds
 * on a large Windows font folder, and a failure on any machine with a font it cannot parse. An
 * export that embeds its own face never needs the built-in one, so it must never pay for it.
 */
private fun loadFont(document: PDDocument, bytes: ByteArray?, fallback: () -> PDFont): PDFont =
    bytes?.let { runCatching { PDType0Font.load(document, ByteArrayInputStream(it), true) }.getOrNull() }
        ?: fallback()

/** The line under the title: the date, the start time and, once anything is estimated, the planned length. */
private fun headingMeta(service: PlannedService, dateLabel: String, use24Hour: Boolean): String = buildString {
    append(dateLabel)
    append(" · ")
    append(clockText(service.startTime, use24Hour))
    val total = service.plannedTotalSeconds()
    if (total > 0) {
        append(" · ")
        append(formatDuration(total))
    }
}

/** The two faces the sheet is set in, and whether they are the embedded ones or the built-in fallback. */
private class Faces(val regular: PDFont, val bold: PDFont) {
    val embedded: Boolean = regular !is PDType1Font
}

/** One A4 page of the sheet, with the cursor that walks down it. */
private class SheetPage(document: PDDocument, private val faces: Faces) : AutoCloseable {
    private val page = PDPage(PDRectangle.A4).also { document.addPage(it) }
    private val stream = PDPageContentStream(document, page)
    private val right = PDRectangle.A4.width - MARGIN
    private var y = page.mediaBox.height - MARGIN

    val hasRoomForRow: Boolean get() = y >= MARGIN + ROW_HEIGHT

    fun heading(title: String, meta: String) {
        text(title, MARGIN, faces.bold, TITLE_SIZE)
        y -= TITLE_SIZE + TITLE_GAP
        text(meta, MARGIN, faces.regular, SUB_SIZE)
        y -= SUB_SIZE + META_GAP
        rule(y)
        y -= HEADING_GAP
    }

    fun section(item: ScheduleItem.LabelItem) {
        y -= SECTION_GAP
        text(item.text.uppercase(), MARGIN, faces.bold, SECTION_SIZE)
        rule(y - SECTION_RULE_GAP)
        y -= ROW_HEIGHT
    }

    fun row(item: ScheduleItem, clock: String, plannedSeconds: Int?) {
        text(clock, MARGIN, faces.regular, META_SIZE)
        text(item.displayText, MARGIN + TIME_COLUMN, faces.regular, ROW_SIZE)
        if (plannedSeconds != null) {
            text(formatDuration(plannedSeconds), right - DURATION_COLUMN, faces.regular, META_SIZE)
        }
        y -= ROW_HEIGHT
    }

    override fun close() = stream.close()

    /** One line of text at the cursor, with whatever the font cannot encode replaced rather than thrown on. */
    private fun text(value: String, x: Float, font: PDFont, size: Float) {
        if (value.isEmpty()) return
        stream.beginText()
        stream.setFont(font, size)
        stream.newLineAtOffset(x, y)
        stream.showText(if (faces.embedded) value else value.toWinAnsiSafe())
        stream.endText()
    }

    private fun rule(at: Float) {
        stream.setLineWidth(RULE_WIDTH)
        stream.moveTo(MARGIN, at)
        stream.lineTo(right, at)
        stream.stroke()
    }
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
