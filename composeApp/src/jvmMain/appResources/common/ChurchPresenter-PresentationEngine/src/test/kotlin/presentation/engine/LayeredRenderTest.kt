package presentation.engine

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import presentation.engine.model.LayerSpec
import presentation.engine.model.RasterLayer
import java.awt.Color
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * WS2 invariant: compositing the layer decomposition reproduces the plain full-slide render,
 * and per-paragraph transparent-run mutation leaves the document XML untouched afterwards.
 */
class LayeredRenderTest {

    @TempDir
    lateinit var tempDir: File

    /**
     * Fixture: [staticRectA] (bottom), [animatedRect], 3-paragraph text box built by paragraph,
     * [staticRectC] (top). Returns the file.
     */
    private fun createLayeredPptx(): File {
        val file = File(tempDir, "layered.pptx")
        XMLSlideShow().use { ppt ->
            val slide = ppt.createSlide()

            val staticRectA = slide.createAutoShape()
            staticRectA.anchor = Rectangle(40, 40, 200, 120)
            staticRectA.fillColor = Color(0x88, 0x22, 0x22)

            val animatedRect = slide.createAutoShape()
            animatedRect.anchor = Rectangle(300, 40, 200, 120)
            animatedRect.fillColor = Color(0x22, 0x88, 0x22)

            val textBox = slide.createTextBox()
            textBox.anchor = Rectangle(40, 220, 460, 180)
            textBox.text = "First line"
            val p2 = textBox.addNewTextParagraph()
            p2.addNewTextRun().setText("Second line")
            val p3 = textBox.addNewTextParagraph()
            p3.addNewTextRun().setText("Third line")

            val staticRectC = slide.createAutoShape()
            staticRectC.anchor = Rectangle(560, 220, 100, 100)
            staticRectC.fillColor = Color(0x22, 0x22, 0x88)

            Fixtures.addTiming(
                slide,
                listOf(
                    Fixtures.TimingTarget(animatedRect.shapeId.toLong()),
                    Fixtures.TimingTarget(textBox.shapeId.toLong(), paragraphs = listOf(0, 1, 2))
                )
            )
            file.outputStream().use { ppt.write(it) }
        }
        return file
    }

    private fun composite(layers: List<RasterLayer>, width: Int, height: Int): BufferedImage {
        val canvas = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = canvas.createGraphics()
        for (layer in layers) {
            g.drawImage(layer.image, layer.offsetXPx, layer.offsetYPx, null)
        }
        g.dispose()
        return canvas
    }

    /** Mean absolute channel difference over opaque-composited pixels; also % of gross outliers. */
    private fun assertPerceptuallyEqual(expected: BufferedImage, actual: BufferedImage) {
        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)
        var sum = 0L
        var outliers = 0
        val total = expected.width * expected.height
        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
                val e = expected.getRGB(x, y)
                val a = actual.getRGB(x, y)
                var pixelDiff = 0
                for (shift in intArrayOf(24, 16, 8, 0)) {
                    pixelDiff += abs(((e shr shift) and 0xFF) - ((a shr shift) and 0xFF))
                }
                sum += pixelDiff
                if (pixelDiff > 128) outliers++
            }
        }
        val meanPerChannel = sum.toDouble() / total / 4
        val outlierFraction = outliers.toDouble() / total
        assertTrue(
            meanPerChannel < 2.0 && outlierFraction < 0.005,
            "Composite deviates from full render: mean/channel=$meanPerChannel outliers=${outlierFraction * 100}%"
        )
    }

    @Test
    fun `planner produces expected layer structure`() {
        val deck = assertIs<LoadResult.Success>(PresentationLoader.load(createLayeredPptx())).deck
        val layers = deck.slides[0].layers

        val kinds = layers.map { it::class.simpleName }
        assertEquals(
            listOf("Background", "Shape", "ParagraphText", "ParagraphText", "ParagraphText", "Background"),
            kinds,
            "unexpected layer decomposition: $layers"
        )
        assertEquals(layers.indices.toList(), layers.map { it.zIndex }, "zIndex must be contiguous bottom-up")
        val band0 = layers[0] as LayerSpec.Background
        assertEquals(listOf(0), band0.shapeIndexes, "bottom band should hold only the first static rect")
    }

    @Test
    fun `composited layers match the full-slide render`() {
        val deck = assertIs<LoadResult.Success>(PresentationLoader.load(createLayeredPptx())).deck
        DeckRasterizer(deck, targetWidthPx = 960).use { rasterizer ->
            val full = rasterizer.renderFinalFrame(0)
            val layers = rasterizer.rasterizeSlideLayers(0)
            val composite = composite(layers, full.width, full.height)
            assertPerceptuallyEqual(full, composite)
        }
    }

    @Test
    fun `paragraph mutation leaves the slide XML untouched`() {
        val file = createLayeredPptx()
        val deck = assertIs<LoadResult.Success>(PresentationLoader.load(file)).deck
        DeckRasterizer(deck, targetWidthPx = 960).use { rasterizer ->
            val before = rasterizer.renderFinalFrame(0)
            rasterizer.rasterizeSlideLayers(0)
            val after = rasterizer.renderFinalFrame(0)
            for (y in 0 until before.height) {
                for (x in 0 until before.width) {
                    assertEquals(
                        before.getRGB(x, y), after.getRGB(x, y),
                        "pixel ($x,$y) changed after layer rasterization — mutation not restored"
                    )
                }
            }
        }
    }

    @Test
    fun `slides without timing stay a single static composite`() {
        val plain = Fixtures.createPptx(tempDir, slides = listOf("Just text" to ""), name = "plain.pptx")
        val deck = assertIs<LoadResult.Success>(PresentationLoader.load(plain)).deck
        assertEquals(1, deck.slides[0].layers.size)
        assertIs<LayerSpec.StaticComposite>(deck.slides[0].layers[0])
        DeckRasterizer(deck, targetWidthPx = 640).use { rasterizer ->
            val layers = rasterizer.rasterizeSlideLayers(0)
            assertEquals(1, layers.size)
            assertEquals(640, layers[0].image.width)
        }
    }
}
