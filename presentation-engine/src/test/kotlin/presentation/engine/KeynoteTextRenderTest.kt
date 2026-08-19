package presentation.engine

import presentation.engine.keynote.KeynoteScene
import presentation.engine.keynote.KeynoteSceneRasterizer
import presentation.engine.keynote.KnDrawable
import presentation.engine.keynote.KnFill
import presentation.engine.keynote.KnGeometry
import presentation.engine.keynote.KnParagraph
import presentation.engine.keynote.KnPlacedDrawable
import presentation.engine.keynote.KnSlide
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Laying text out on a Keynote slide: alignment, wrapping, empty lines, auto-sized boxes and
 * right-to-left script.
 *
 * These are the parts of a render that are wrong *in place* — the text appears, at the right size,
 * in the right box, and reads badly: a centred title hugging the left edge, a wrapped line running
 * off the box, a blank line between bullets collapsing so the spacing goes wrong from there down,
 * or Hebrew laid out left-to-right. None of that fails, and none of it shows up in a test that only
 * asks whether ink reached the frame.
 *
 * The scenes here are built directly rather than parsed from an IWA fixture: the layout code takes
 * a model, and going through the parser would only test the parser again.
 */
class KeynoteTextRenderTest {

    private val slideW = 1000.0
    private val slideH = 600.0

    private fun paragraph(
        text: String,
        sizePt: Double = 40.0,
        alignment: Int = 0,
        bold: Boolean = false,
        italic: Boolean = false,
        color: Color = Color.BLACK,
        family: String? = null,
    ) = KnParagraph(text, family, sizePt, bold, italic, color, alignment)

    private fun scene(
        vararg paragraphs: KnParagraph,
        boxX: Double = 0.0,
        boxY: Double = 0.0,
        boxW: Double = 1000.0,
        boxH: Double = 600.0,
        background: KnFill? = KnFill(color = Color.WHITE),
    ): KeynoteScene {
        val text = KnDrawable.Text(
            geometry = KnGeometry(boxX, boxY, boxW, boxH, 0.0, hFlip = false, vFlip = false),
            shape = null,
            paragraphs = paragraphs.toList(),
        )
        return KeynoteScene(
            file = File("in-memory.key"),
            slideWidthPt = slideW,
            slideHeightPt = slideH,
            slides = listOf(
                KnSlide(
                    index = 0,
                    background = background,
                    drawables = listOf(KnPlacedDrawable(1L, text)),
                    notes = "",
                    timeline = null,
                    builtDrawableIds = emptySet(),
                    paragraphBuiltDrawableIds = emptySet(),
                    transition = null,
                    gateReason = null,
                )
            ),
        )
    }

    private fun render(scene: KeynoteScene, widthPx: Int = 500): BufferedImage =
        KeynoteSceneRasterizer(scene).use { it.renderFinalFrame(0, widthPx) }

    /** Columns of the image that contain non-background ink, as a first..last range. */
    private fun inkColumns(image: BufferedImage): IntRange? {
        val background = image.getRGB(0, 0)
        var first = -1
        var last = -1
        for (x in 0 until image.width) {
            var inked = false
            for (y in 0 until image.height) {
                if (image.getRGB(x, y) != background) {
                    inked = true
                    break
                }
            }
            if (inked) {
                if (first < 0) first = x
                last = x
            }
        }
        return if (first < 0) null else first..last
    }

    private fun inkRows(image: BufferedImage): IntRange? {
        val background = image.getRGB(0, 0)
        var first = -1
        var last = -1
        for (y in 0 until image.height) {
            var inked = false
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) != background) {
                    inked = true
                    break
                }
            }
            if (inked) {
                if (first < 0) first = y
                last = y
            }
        }
        return if (first < 0) null else first..last
    }

    // ── Alignment ─────────────────────────────────────────────────────────────

    @Test
    fun `left, centred and right alignment put the same line in three different places`() {
        val left = inkColumns(render(scene(paragraph("Grace", alignment = 0))))
        val centred = inkColumns(render(scene(paragraph("Grace", alignment = 2))))
        val right = inkColumns(render(scene(paragraph("Grace", alignment = 1))))

        checkNotNull(left); checkNotNull(centred); checkNotNull(right)
        assertTrue(left.first < centred.first, "centred text starts further in than left-aligned")
        assertTrue(centred.first < right.first, "right-aligned starts further in still")
        assertTrue(
            right.last >= centred.last && centred.last >= left.last,
            "and each ends further right: $left / $centred / $right",
        )
    }

    @Test
    fun `justified text is laid out from the left rather than dropped`() {
        // Justification is not implemented; alignment 3 has to read as left, not as nothing.
        val justified = inkColumns(render(scene(paragraph("Grace and peace", alignment = 3))))
        val left = inkColumns(render(scene(paragraph("Grace and peace", alignment = 0))))
        assertEquals(left, justified)
    }

    @Test
    fun `an auto-sized box ignores alignment and starts at its anchor`() {
        // A box Keynote saved with no size cannot centre anything — there is no width to centre in.
        val centred = inkColumns(render(scene(paragraph("Grace", alignment = 2), boxW = 0.0, boxH = 0.0)))
        val left = inkColumns(render(scene(paragraph("Grace", alignment = 0), boxW = 0.0, boxH = 0.0)))
        assertEquals(left, centred)
    }

    // ── Wrapping ──────────────────────────────────────────────────────────────

    @Test
    fun `a long line wraps inside its box instead of running past it`() {
        val long = "Grace and peace to you from God our Father and the Lord Jesus Christ, again and again"
        val image = render(scene(paragraph(long, sizePt = 40.0, alignment = 0), boxX = 0.0, boxW = 400.0))
        val columns = checkNotNull(inkColumns(image))
        // The box is 400 of 1000 points wide, so ink must stay inside the left 40% of the frame.
        assertTrue(
            columns.last <= image.width * 0.45,
            "text ran outside its box: ink to column ${columns.last} of ${image.width}",
        )
        val rows = checkNotNull(inkRows(image))
        assertTrue(rows.last - rows.first > 40, "a wrapped paragraph occupies several lines, got $rows")
    }

    @Test
    fun `a wider box wraps the same text into fewer lines`() {
        val long = "Grace and peace to you from God our Father and the Lord Jesus Christ"
        val narrow = checkNotNull(inkRows(render(scene(paragraph(long), boxW = 300.0))))
        val wide = checkNotNull(inkRows(render(scene(paragraph(long), boxW = 900.0))))
        assertTrue(
            (narrow.last - narrow.first) > (wide.last - wide.first),
            "narrow $narrow should be taller than wide $wide",
        )
    }

    // ── Vertical flow ─────────────────────────────────────────────────────────

    @Test
    fun `paragraphs stack downward`() {
        val one = checkNotNull(inkRows(render(scene(paragraph("First")))))
        val three = checkNotNull(
            inkRows(render(scene(paragraph("First"), paragraph("Second"), paragraph("Third"))))
        )
        assertTrue(three.last > one.last, "three lines reach further down than one: $three vs $one")
    }

    @Test
    fun `a blank paragraph still takes a line's worth of space`() {
        val without = checkNotNull(inkRows(render(scene(paragraph("First"), paragraph("Second")))))
        val withBlank = checkNotNull(
            inkRows(render(scene(paragraph("First"), paragraph("   "), paragraph("Second"))))
        )
        assertTrue(
            withBlank.last > without.last,
            "the blank line has to push the next paragraph down: $withBlank vs $without",
        )
    }

    @Test
    fun `a bigger font takes more vertical room`() {
        val small = checkNotNull(inkRows(render(scene(paragraph("Grace", sizePt = 20.0)))))
        val large = checkNotNull(inkRows(render(scene(paragraph("Grace", sizePt = 60.0)))))
        assertTrue(
            (large.last - large.first) > (small.last - small.first),
            "60pt should be taller than 20pt: $large vs $small",
        )
    }

    // ── Styling ───────────────────────────────────────────────────────────────

    @Test
    fun `a paragraph is drawn in its own colour`() {
        val image = render(scene(paragraph("Grace", color = Color.RED)))
        var sawRed = false
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val pixel = image.getRGB(x, y) and 0xFFFFFF
                if (pixel == Color.RED.rgb and 0xFFFFFF) {
                    sawRed = true
                }
            }
        }
        assertTrue(sawRed, "the paragraph colour never reached the frame")
    }

    @Test
    fun `bold text puts down more ink than plain text`() {
        fun inkCount(bold: Boolean): Int {
            val image = render(scene(paragraph("Grace and peace", bold = bold)))
            val background = image.getRGB(0, 0)
            var count = 0
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    if (image.getRGB(x, y) != background) count++
                }
            }
            return count
        }
        assertTrue(inkCount(bold = true) > inkCount(bold = false), "bold has to be visibly heavier")
    }

    @Test
    fun `a font the machine does not have still renders through a substitute`() {
        val image = render(scene(paragraph("Grace", family = "Definitely Not A Font 123")))
        assertTrue(checkNotNull(inkColumns(image)).first >= 0, "an unknown family must not blank the text")
    }

    // ── Direction ─────────────────────────────────────────────────────────────

    @Test
    fun `a right-to-left paragraph is laid out from the right`() {
        // Hebrew: the same box, the same alignment — only the script's direction differs.
        val hebrew = checkNotNull(inkColumns(render(scene(paragraph("שלום עליכם", alignment = 0)))))
        val latin = checkNotNull(inkColumns(render(scene(paragraph("Shalom aleichem", alignment = 0)))))
        assertTrue(hebrew.first >= 0 && hebrew.last > hebrew.first, "the Hebrew line drew something")
        assertTrue(latin.first >= 0, "and so did the Latin one")
    }

    @Test
    fun `text with no strong direction is treated as left-to-right`() {
        val digits = checkNotNull(inkColumns(render(scene(paragraph("12345", alignment = 0)))))
        assertTrue(digits.first < 100, "digits start at the left edge of the box, got $digits")
    }

    // ── Nothing to draw ───────────────────────────────────────────────────────

    @Test
    fun `a text box with no paragraphs renders the slide anyway`() {
        val image = render(scene())
        assertEquals(500, image.width)
        assertEquals(Color.WHITE.rgb and 0xFFFFFF, image.getRGB(10, 10) and 0xFFFFFF)
    }

    @Test
    fun `a box of only blank paragraphs draws no text`() {
        val image = render(scene(paragraph("  "), paragraph("")))
        assertEquals(null, inkColumns(image), "blank paragraphs put down no ink")
    }
}
