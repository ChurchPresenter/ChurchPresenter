package org.churchpresenter.calendar.model

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.awt.Font
import java.awt.Shape
import java.awt.font.FontRenderContext
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.geom.PathIterator
import java.awt.geom.Point2D
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
private const val QUAD_TO_CUBIC = 2f / 3f
private const val SEGMENT_COORDS = 6
private const val FONT_UNITS = 1000f

/**
 * The run of show as a one-page-per-however-many-rows PDF, for the band and the booth.
 *
 * Deliberately plain: a heading, then one line per row with its clock time, its title and its
 * planned length, with section names as rules between them. It is a sheet somebody holds, not a
 * reproduction of the screen.
 *
 * [font] supplies the embedded TrueType face — see [org.churchpresenter.calendar.CalendarHost.pdfFont].
 * When it returns null the built-in Helvetica stands in. Either way, a run of text the face cannot
 * encode -- Tamil, say, which OpenSans has no glyphs for -- is drawn as glyph outlines from a system
 * font instead; see [SheetPage.text].
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

    /**
     * One line of text at the cursor.
     *
     * Runs [font] can encode are set as real text. The rest are shaped by AWT, which falls back to
     * an installed font that has the script and reorders it correctly (PDFBox does neither: Tamil's
     * pre-base vowel signs would land after their consonant), and filled as outlines. A run no
     * installed font can display is marked with `?` rather than failing the export.
     */
    private fun text(value: String, x: Float, font: PDFont, size: Float) {
        var pen = x
        for ((run, encodable) in value.runsBy { font.canEncode(it) }) {
            pen += when {
                encodable -> showText(run, pen, font, size)
                else -> showOutline(run, pen, font, size)
                    ?: showText(run.placeholders(), pen, font, size)
            }
        }
    }

    private fun showText(value: String, x: Float, font: PDFont, size: Float): Float {
        stream.beginText()
        stream.setFont(font, size)
        stream.newLineAtOffset(x, y)
        stream.showText(value)
        stream.endText()
        return font.getStringWidth(value) / FONT_UNITS * size
    }

    private fun showOutline(value: String, x: Float, font: PDFont, size: Float): Float? {
        val style = if (font === faces.bold) Font.BOLD else Font.PLAIN
        val awtFont = Font(Font.SANS_SERIF, style, 1).deriveFont(size)
        if (awtFont.canDisplayUpTo(value) != -1) return null
        val layout = TextLayout(value, awtFont, FontRenderContext(null, true, true))
        fillOutline(layout.getOutline(null), x, y)
        return layout.advance
    }

    private fun fillOutline(shape: Shape, x: Float, baseline: Float) {
        // Flipped into PDF space, where y runs up from the page's foot.
        val path = shape.getPathIterator(AffineTransform(1f, 0f, 0f, -1f, x, baseline))
        val coords = FloatArray(SEGMENT_COORDS)
        var last = Point2D.Float()
        var drawn = false
        while (!path.isDone) {
            val type = path.currentSegment(coords)
            val points = coords.toList().chunked(2) { Point2D.Float(it[0], it[1]) }
            when (type) {
                PathIterator.SEG_MOVETO -> stream.moveTo(points[0].x, points[0].y)
                PathIterator.SEG_LINETO -> stream.lineTo(points[0].x, points[0].y)
                PathIterator.SEG_QUADTO -> stream.curveTo(
                    last.x + QUAD_TO_CUBIC * (points[0].x - last.x), last.y + QUAD_TO_CUBIC * (points[0].y - last.y),
                    points[1].x + QUAD_TO_CUBIC * (points[0].x - points[1].x),
                    points[1].y + QUAD_TO_CUBIC * (points[0].y - points[1].y),
                    points[1].x, points[1].y,
                )
                PathIterator.SEG_CUBICTO -> stream.curveTo(
                    points[0].x, points[0].y, points[1].x, points[1].y, points[2].x, points[2].y,
                )
                PathIterator.SEG_CLOSE -> stream.closePath()
            }
            last = when (type) {
                PathIterator.SEG_QUADTO -> points[1]
                PathIterator.SEG_CUBICTO -> points[2]
                PathIterator.SEG_CLOSE -> last
                else -> points[0]
            }
            drawn = true
            path.next()
        }
        if (!drawn) return
        if (path.windingRule == PathIterator.WIND_EVEN_ODD) stream.fillEvenOdd() else stream.fill()
    }

    private fun rule(at: Float) {
        stream.setLineWidth(RULE_WIDTH)
        stream.moveTo(MARGIN, at)
        stream.lineTo(right, at)
        stream.stroke()
    }
}

private fun PDFont.canEncode(glyph: String): Boolean = runCatching { encode(glyph) }.isSuccess

/**
 * [this] split into runs of code points that do or do not satisfy [encodable]. A combining mark or
 * a joiner stays with the run before it, so a cluster is never split between text and outline.
 */
private fun String.runsBy(encodable: (String) -> Boolean): List<Pair<String, Boolean>> {
    val runs = mutableListOf<Pair<StringBuilder, Boolean>>()
    var i = 0
    while (i < length) {
        val codePoint = codePointAt(i)
        val glyph = String(Character.toChars(codePoint))
        val last = runs.lastOrNull()
        val fits = if (last != null && codePoint.attachesToPrevious()) last.second else encodable(glyph)
        if (last != null && last.second == fits) last.first.append(glyph) else runs += StringBuilder(glyph) to fits
        i += Character.charCount(codePoint)
    }
    return runs.map { (run, fits) -> run.toString() to fits }
}

private fun Int.attachesToPrevious(): Boolean = when (Character.getType(this).toByte()) {
    Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK, Character.FORMAT -> true
    else -> false
}

private fun String.placeholders(): String = "?".repeat(codePointCount(0, length))
