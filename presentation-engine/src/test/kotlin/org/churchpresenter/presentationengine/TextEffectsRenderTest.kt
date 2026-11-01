package org.churchpresenter.presentationengine

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.churchpresenter.presentationengine.fonts.SlideFontRegistry
import org.churchpresenter.presentationengine.pptx.TextEffects
import org.churchpresenter.presentationengine.pptx.TextEffectsKey
import org.churchpresenter.presentationengine.pptx.TextGlow
import org.churchpresenter.presentationengine.pptx.boxBlur
import org.churchpresenter.presentationengine.pptx.drawWithEffects
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Rectangle
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.text.AttributedString
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextEffectsRenderTest {

    private val temp: File = Files.createTempDirectory("text-effects-test").toFile()
    private var decks = 0

    @BeforeTest
    fun registerFont() {
        SlideFontRegistry::class.java.getResourceAsStream("/slide-fonts/Carlito-Bold.ttf")
            ?.let { SlideFontRegistry.registerFontStream(it) }
    }

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun render(file: File, width: Int = 720): BufferedImage {
        val deck = (PresentationLoader.load(file) as LoadResult.Success).deck
        return DeckRasterizer(deck, targetWidthPx = width).use { it.renderFinalFrame(0) }
    }

    private fun render(line: EffectLine, width: Int = 720): BufferedImage =
        render(effectLinesPptx(File(temp, "deck-${decks++}.pptx"), listOf(line)), width)

    /** The bounds of the white text, by its nearly white pixels; nothing else on the slide is. */
    private fun textBounds(image: BufferedImage): Rectangle {
        val white = (0 until image.height).flatMap { y -> (0 until image.width).map { x -> x to y } }
            .filter { (x, y) -> Color(image.getRGB(x, y)).let { minOf(it.red, it.green, it.blue) > WHITE } }
        check(white.isNotEmpty()) { "no text was drawn" }
        val (x0, y0) = white.first()
        return Rectangle(x0, y0, 0, 0).apply { white.forEach { (x, y) -> add(x, y) } }
    }

    /** The pixels [a] and [b] draw visibly differently. */
    private fun changes(a: BufferedImage, b: BufferedImage): List<Pair<Int, Int>> = buildList {
        for (y in 0 until a.height) for (x in 0 until a.width) {
            val p = Color(a.getRGB(x, y))
            val q = Color(b.getRGB(x, y))
            val diff = maxOf(abs(p.red - q.red), abs(p.green - q.green), abs(p.blue - q.blue))
            if (diff > VISIBLE) add(x to y)
        }
    }

    /** How far past [bounds] the farthest of [points] lies, in pixels. */
    private fun reach(points: List<Pair<Int, Int>>, bounds: Rectangle): Int = points.maxOf { (x, y) ->
        val dx = max(max(bounds.x - x, x - (bounds.x + bounds.width)), 0)
        val dy = max(max(bounds.y - y, y - (bounds.y + bounds.height)), 0)
        max(dx, dy)
    }

    private fun glowReach(width: Int): Int {
        val plain = render(EffectLine("Glowing"), width)
        val glowing = render(EffectLine("Glowing") { it.glow(radiusPt = 10.0, rgb = Color.BLACK) }, width)
        return reach(changes(plain, glowing), textBounds(plain))
    }

    @Test
    fun `a glow darkens the background around the text as far as its radius`() {
        val plain = render(EffectLine("Glowing"))
        val glowing = render(EffectLine("Glowing") { it.glow(radiusPt = 10.0, rgb = Color.BLACK) })
        val changed = changes(plain, glowing)

        assertTrue(changed.isNotEmpty(), "the glow drew nothing")
        assertTrue(
            changed.all { (x, y) -> Color(glowing.getRGB(x, y)).let { it.red + it.green + it.blue } <
                Color(plain.getRGB(x, y)).let { it.red + it.green + it.blue } },
            "a black glow only darkens",
        )
        assertEquals(textBounds(plain), textBounds(glowing), "the text itself is still drawn white over it")
        val reach = reach(changed, textBounds(plain))
        assertTrue(reach in 5..14, "a 10pt glow at 1px per point reaches about 10px, not $reach")
    }

    @Test
    fun `a glow keeps its size in points when the slide is drawn larger`() {
        val small = glowReach(720)
        val large = glowReach(1440)

        assertTrue(large >= small * 1.6 && large <= small * 2.4, "twice the pixels, twice the reach: $small → $large")
    }

    @Test
    fun `an outline is drawn on the glyph edges, no wider than itself`() {
        val plain = render(EffectLine("Outlined"))
        val outlined = render(EffectLine("Outlined") { it.outline(widthPt = 2.0, rgb = Color.BLACK) })
        val changed = changes(plain, outlined)

        assertTrue(changed.any { (x, y) -> Color(outlined.getRGB(x, y)).let { it.red + it.green + it.blue } < DARK })
        assertTrue(reach(changed, textBounds(plain)) <= 3, "half of 2pt past the edge, and a pixel of antialiasing")
    }

    @Test
    fun `a shadow falls in its direction and nowhere else`() {
        val plain = render(EffectLine("Shadowed"))
        val shadowed = render(
            EffectLine("Shadowed") { it.shadow(blurPt = 0.0, distancePt = 6.0, angleDeg = 0, rgb = Color.BLACK) },
        )
        val bounds = textBounds(plain)
        val changed = changes(plain, shadowed)

        assertTrue(changed.any { (x, _) -> x > bounds.x + bounds.width + 3 }, "it reaches past the right edge")
        assertTrue(changed.none { (x, _) -> x < bounds.x - 1 }, "nothing to the left")
        assertTrue(
            changed.none { (_, y) -> y < bounds.y - 1 || y > bounds.y + bounds.height + 1 },
            "nor above or below",
        )
    }

    @Test
    fun `only the run that carries an effect gets it`() {
        fun deck(glowSecond: Boolean): File {
            val file = File(temp, "runs-${decks++}.pptx")
            XMLSlideShow().use { ppt ->
                ppt.pageSize = Dimension(720, 405)
                val box = ppt.createSlide().createTextBox()
                box.anchor = Rectangle(40, 20, 640, 80)
                box.clearText()
                val paragraph = box.addNewTextParagraph()
                listOf("Plain words ", "Glow").forEachIndexed { index, text ->
                    val run = paragraph.addNewTextRun()
                    run.setText(text)
                    run.fontSize = 48.0
                    run.fontFamily = "Carlito"
                    run.isBold = true
                    run.setFontColor(Color.WHITE)
                    if (glowSecond && index == 1) run.getRPr(true).glow(radiusPt = 10.0, rgb = Color.BLACK)
                }
                file.outputStream().use { ppt.write(it) }
            }
            return file
        }
        val plain = render(deck(glowSecond = false))
        val changed = changes(plain, render(deck(glowSecond = true)))
        val bounds = textBounds(plain)

        assertTrue(changed.isNotEmpty())
        assertTrue(
            changed.minOf { it.first } > bounds.x + bounds.width / 3,
            "the first words stay as they were; only the glowing word's surroundings change",
        )
    }

    @Test
    fun `a line without effects draws as it did`() {
        val text = AttributedString("Words").apply {
            addAttribute(TextAttribute.FONT, Font("Carlito", Font.BOLD, 40))
        }
        val (plain, through) = listOf(false, true).map { effects ->
            BufferedImage(300, 80, BufferedImage.TYPE_INT_ARGB).also { image ->
                val graphics = image.createGraphics()
                val layout = TextLayout(text.iterator, graphics.fontRenderContext)
                if (effects) drawWithEffects(graphics, layout, text, 10f, 50f) else layout.draw(graphics, 10f, 50f)
                graphics.dispose()
            }
        }

        assertContentEquals(plain.getRGB(0, 0, 300, 80, null, 0, 300), through.getRGB(0, 0, 300, 80, null, 0, 300))
    }

    private fun glowingLine(glow: TextGlow): Pair<AttributedString, TextEffects> {
        val effects = TextEffects(glow = glow)
        return AttributedString("Words").apply {
            addAttribute(TextAttribute.FONT, Font("Carlito", Font.BOLD, 40))
            addAttribute(TextEffectsKey, effects)
        } to effects
    }

    @Test
    fun `a transparent glow draws nothing`() {
        val (text, _) = glowingLine(TextGlow(Color(0, 0, 0, 0), radiusPt = 10.0))
        val image = BufferedImage(300, 80, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.color = Color.WHITE
        drawWithEffects(graphics, TextLayout(text.iterator, graphics.fontRenderContext), text, 10f, 50f)
        graphics.dispose()

        val pixels = image.getRGB(0, 0, 300, 80, null, 0, 300)
        assertTrue(pixels.none { it ushr 24 != 0 && (it and 0xFFFFFF) == 0 && it ushr 24 < 0xFF }, "no part-black halo")
    }

    @Test
    fun `a glow is skipped rather than drawn on a collapsed or enormous scale`() {
        val (text, _) = glowingLine(TextGlow(Color.BLACK, radiusPt = 10.0))
        listOf(0.0, 2_000.0).forEach { scale ->
            val image = BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            graphics.scale(scale, scale)
            drawWithEffects(graphics, TextLayout(text.iterator, graphics.fontRenderContext), text, 0f, 30f)
            graphics.dispose()
        }
    }

    @Test
    fun `a box blur spreads a point evenly and keeps edges in bounds`() {
        val values = IntArray(9 * 9).also { it[4 * 9 + 4] = 81 * 9 }
        boxBlur(values, 9, 9, radius = 1)

        val spread = (3..5).flatMap { y -> (3..5).map { x -> values[y * 9 + x] } }
        assertTrue(spread.all { it == 81 }, "a 3×3 box shares the point equally: $spread")
        assertEquals(81 * 9, values.sum())

        val edge = IntArray(3).also { it[0] = 90 }
        boxBlur(edge, 3, 1, radius = 1)
        assertContentEquals(intArrayOf(10, 10, 0), edge, "outside the grid counts as nothing, across and down")
    }

    @Test
    fun `a box blur of no radius changes nothing`() {
        val values = intArrayOf(1, 2, 3, 4)
        boxBlur(values, 2, 2, radius = 0)
        assertContentEquals(intArrayOf(1, 2, 3, 4), values)
    }

    private companion object {
        const val WHITE = 245
        const val VISIBLE = 8
        const val DARK = 120
    }
}
