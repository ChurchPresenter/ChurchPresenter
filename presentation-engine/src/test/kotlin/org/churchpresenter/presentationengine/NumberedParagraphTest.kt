package org.churchpresenter.presentationengine

import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextBox
import org.apache.poi.xslf.usermodel.XSLFTextParagraph
import org.churchpresenter.presentationengine.fonts.SlideFontRegistry
import org.churchpresenter.presentationengine.model.LayerSpec
import org.churchpresenter.presentationengine.pptx.bulletStyleOf
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextCharacterProperties
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextParagraphProperties
import org.openxmlformats.schemas.drawingml.x2006.main.STTextAutonumberScheme
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape
import java.awt.Color
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Numbered paragraphs, and what a paragraph's layer may carry of the paragraphs around it.
 *
 * Every deck here is one text box of three lines with a wide hanging indent, so a number sits well
 * to the left of the words it leads and the two can be told apart by where their ink is.
 */
class NumberedParagraphTest {

    private val temp: File = Files.createTempDirectory("numbered-paragraph-test").toFile()
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

    /** What a test sets on the deck: each paragraph's properties, each run's, and the box itself. */
    private class Setup(
        val paragraph: (Int, CTTextParagraphProperties) -> Unit = { _, _ -> },
        val run: (Int, CTTextCharacterProperties) -> Unit = { _, _ -> },
        val box: (XSLFTextBox) -> Unit = {},
        val builtByParagraph: Boolean = false,
    )

    private fun deck(setup: Setup): File {
        val file = File(temp, "deck-${decks++}.pptx")
        XMLSlideShow().use { ppt ->
            ppt.pageSize = Dimension(720, 405)
            val slide = ppt.createSlide()
            val box = slide.createTextBox()
            box.anchor = Rectangle(40, 40, 640, 300)
            box.clearText()
            LINES.forEachIndexed { index, words ->
                val paragraph = box.addNewTextParagraph()
                paragraph.leftMargin = HANGING_PT
                paragraph.indent = -HANGING_PT
                val run = paragraph.addNewTextRun()
                run.setText(words)
                run.fontSize = 40.0
                run.fontFamily = "Carlito"
                run.isBold = true
                setup.run(index, run.getRPr(true))
                setup.paragraph(index, paragraph.xmlObject.pPr)
            }
            setup.box(box)
            if (setup.builtByParagraph) {
                Fixtures.addTiming(slide, listOf(Fixtures.TimingTarget(box.shapeId.toLong(), LINES.indices.toList())))
            }
            file.outputStream().use { ppt.write(it) }
        }
        return file
    }

    private fun numbered(startAt: Int? = null) = { _: Int, pPr: CTTextParagraphProperties ->
        val number = pPr.addNewBuAutoNum()
        number.type = STTextAutonumberScheme.ARABIC_PERIOD
        if (startAt != null) number.startAt = startAt
    }

    private fun frame(file: File): BufferedImage {
        val deck = (PresentationLoader.load(file) as LoadResult.Success).deck
        return DeckRasterizer(deck, targetWidthPx = 720).use { it.renderFinalFrame(0) }
    }

    /** Each paragraph's layer, drawn onto a slide-sized transparent image so positions compare. */
    private fun paragraphLayers(file: File): List<BufferedImage> {
        val deck = (PresentationLoader.load(file) as LoadResult.Success).deck
        return DeckRasterizer(deck, targetWidthPx = 720).use { rasterizer ->
            rasterizer.rasterizeSlideLayers(0).filter { it.spec is LayerSpec.ParagraphText }.map { layer ->
                BufferedImage(720, 405, BufferedImage.TYPE_INT_ARGB).also { slide ->
                    val graphics = slide.createGraphics()
                    graphics.drawImage(layer.image, layer.offsetXPx, layer.offsetYPx, null)
                    graphics.dispose()
                }
            }
        }
    }

    /** The bounds of everything drawn: not the white slide of a frame, not the nothing of a layer. */
    private fun ink(image: BufferedImage): Rectangle? {
        val drawn = (0 until image.height).flatMap { y -> (0 until image.width).map { x -> x to y } }
            .filter { (x, y) -> isInk(Color(image.getRGB(x, y), true)) }
        val (x0, y0) = drawn.firstOrNull() ?: return null
        return Rectangle(x0, y0, 0, 0).apply { drawn.forEach { (x, y) -> add(x, y) } }
    }

    private fun isInk(pixel: Color): Boolean =
        pixel.alpha > FAINT && minOf(pixel.red, pixel.green, pixel.blue) < WHITE

    private fun differ(a: BufferedImage, b: BufferedImage): Boolean =
        !a.getRGB(0, 0, a.width, a.height, null, 0, a.width)
            .contentEquals(b.getRGB(0, 0, b.width, b.height, null, 0, b.width))

    private val plainTextLeft: Int by lazy { assertNotNull(ink(frame(deck(Setup())))).x }

    @Test
    fun `a paragraph numbered with nothing but its scheme draws its number`() {
        val drawn = assertNotNull(ink(frame(deck(Setup(paragraph = numbered())))))

        assertTrue(
            drawn.x < plainTextLeft - HANGING_PT / 2,
            "the number hangs left of the words, which start at $plainTextLeft: ink starts at ${drawn.x}",
        )
    }

    @Test
    fun `a list counts on from the number it is told to start at`() {
        val fromOne = frame(deck(Setup(paragraph = numbered())))
        val fromFour = frame(deck(Setup(paragraph = numbered(startAt = 4))))

        assertTrue(differ(fromOne, fromFour), "1. 2. 3. and 4. 5. 6. are different pictures")
    }

    @Test
    fun `numbering set on the box's list style reaches its paragraphs`() {
        val inherited = Setup(box = { box ->
            (box.xmlObject as CTShape).txBody.addNewLstStyle().addNewLvl1PPr().addNewBuAutoNum().type =
                STTextAutonumberScheme.ARABIC_PERIOD
        })

        assertTrue(assertNotNull(ink(frame(deck(inherited)))).x < plainTextLeft - HANGING_PT / 2)
    }

    @Test
    fun `a paragraph that says it has no bullet is not numbered by its list style`() {
        val overridden = Setup(
            paragraph = { _, pPr -> pPr.addNewBuNone() },
            box = { box ->
                (box.xmlObject as CTShape).txBody.addNewLstStyle().addNewLvl1PPr().addNewBuAutoNum().type =
                    STTextAutonumberScheme.ARABIC_PERIOD
            },
        )

        assertEquals(plainTextLeft, assertNotNull(ink(frame(deck(overridden)))).x)
    }

    @Test
    fun `a number carries the glow of the text it leads`() {
        val bare = frame(deck(Setup(paragraph = numbered())))
        val glowing = frame(deck(Setup(paragraph = numbered(), run = { _, rPr -> rPr.glow(4.0, Color.RED) })))
        val numberLeft = assertNotNull(ink(bare)).x

        assertTrue(
            assertNotNull(ink(glowing)).x < numberLeft - 1,
            "the glow reaches left of the number itself, which starts at $numberLeft",
        )
    }

    @Test
    fun `a paragraph's layer holds its own number and no other paragraph's`() {
        val layers = paragraphLayers(deck(Setup(paragraph = numbered(), builtByParagraph = true)))
        assertEquals(LINES.size, layers.size)
        val drawn = layers.map { assertNotNull(ink(it), "every layer draws its line") }

        drawn.forEach { assertTrue(it.x < plainTextLeft - HANGING_PT / 2, "its number is on it: $it") }
        assertOneLineEach(drawn)
    }

    @Test
    fun `a paragraph's layer holds none of the other paragraphs' glow, shadow, outline or highlight`() {
        val decorated = Setup(
            paragraph = numbered(),
            run = { index, rPr ->
                when (index) {
                    0 -> rPr.glow(radiusPt = 4.0, rgb = Color.RED)
                    1 -> {
                        rPr.shadow(blurPt = 2.0, distancePt = 3.0, angleDeg = 45, rgb = Color.RED)
                        rPr.addNewHighlight().addNewSrgbClr().setVal(byteArrayOf(0, 0x7F, 0))
                    }
                    else -> rPr.outline(widthPt = 2.0, rgb = Color.RED)
                }
            },
            builtByParagraph = true,
        )
        val file = deck(decorated)
        val drawn = paragraphLayers(file).map { assertNotNull(ink(it), "every layer draws its line") }

        assertOneLineEach(drawn)
        assertFalse(differ(frame(file), frame(deck(decorated.let { Setup(it.paragraph, it.run) }))),
            "and the settled slide is the one a deck with no builds draws")
    }

    /** Each of [drawn] is one line tall, below the one before: a leak makes a layer two or three tall. */
    private fun assertOneLineEach(drawn: List<Rectangle>) {
        val whole = drawn.reduce { all, next -> all.union(next) }
        drawn.forEach { assertTrue(it.height < whole.height / 2, "one line of three, not $it of $whole") }
        drawn.zipWithNext { above, below -> assertTrue(above.y < below.y && above.maxY < below.maxY) }
    }

    @Test
    fun `a numbered paragraph's style reads and writes the paragraph's own bullet properties`() {
        XMLSlideShow().use { ppt ->
            val box = ppt.createSlide().createTextBox()
            val paragraph: XSLFTextParagraph = box.addNewTextParagraph()
            paragraph.addNewTextRun().setText("One")
            assertNull(bulletStyleOf(paragraph), "no bullet until it has one")

            numbered(startAt = 3)(0, paragraph.xmlObject.addNewPPr())
            val style = assertNotNull(bulletStyleOf(paragraph))
            assertNotNull(style.autoNumberingScheme)
            assertEquals(3, style.autoNumberingStartAt)
            assertNull(style.bulletCharacter)
            assertNull(style.bulletFont)
            assertNull(style.bulletFontSize)
            assertNull(style.bulletFontColor)

            style.setBulletFontColor(Color.RED)
            assertNotNull(style.bulletFontColor)
            style.setBulletFontColor(assertNotNull(style.bulletFontColor))
            assertNotNull(paragraph.bulletFontColor)

            paragraph.bulletCharacter = "-"
            assertEquals("-", bulletStyleOf(paragraph)?.bulletCharacter, "a bullet character is POI's own style")
        }
    }

    @Test
    fun `a paragraph from a binary deck has only the bullet POI gives it`() {
        HSLFSlideShow().use { ppt ->
            val paragraph = ppt.createSlide().createTextBox().textParagraphs.first()
            assertEquals(paragraph.bulletStyle == null, bulletStyleOf(paragraph) == null)
        }
    }

    private companion object {
        val LINES = listOf("Alpha", "Beta", "Gamma")
        const val HANGING_PT = 80.0
        const val WHITE = 235
        const val FAINT = 24
    }
}
