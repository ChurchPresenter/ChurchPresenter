package org.churchpresenter.lottiegen.band

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.lottiegen.band.BandJson.intField
import org.churchpresenter.lottiegen.band.BandJson.name
import org.churchpresenter.lottiegen.band.BandJson.stringField
import org.churchpresenter.lottiegen.band.BandJson.type
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BibleLottieGeneratorTest {

    private fun generate(cfg: BibleLottieGenConfig = BibleLottieGenConfig()): JsonObject =
        BibleLottieGenerator.generate(cfg)

    private fun picture(): BandImage {
        val image = BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB)
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        return BandImage("data:image/png;base64," + Base64.getEncoder().encodeToString(bytes), 40, 30, "pic.png")
    }

    @Test
    fun `a template carries the canvas, the five markers and the metadata the player reads`() {
        val cfg = BibleLottieGenConfig(canvasW = 1280, canvasH = 240)
        val doc = generate(cfg)
        assertEquals(1280, doc.intField("w"))
        assertEquals(240, doc.intField("h"))
        val timeline = BandTimeline.from(cfg)
        assertEquals(timeline.totalFrames, doc.intField("op"))
        assertEquals(setOf("bg_in", "text_in", "hold", "text_out", "bg_out"), BandJson.markers(doc).keys)
        assertEquals(timeline.holdStart to timeline.holdFrames, BandJson.markers(doc).getValue("hold"))
        val meta = BandJson.meta(doc)
        assertEquals("bible-band", meta.stringField("kind"))
        assertEquals("FADE", meta.stringField("textAnimation"))
        assertEquals("FOLLOW_SETTINGS", meta.stringField("textAlign"))
        assertEquals(cfg.tickerPxPerSecond, meta.intField("tickerPxPerSecond"))
    }

    @Test
    fun `the slot boxes are declared in the metadata, not left to the text documents`() {
        val cfg = BibleLottieGenConfig(layout = SlotLayout.SIDE_BY_SIDE)
        val slots = computeSlots(cfg)
        val declared = BandJson.meta(generate(cfg))["slots"]!!.let { it as JsonObject }
        fun box(name: String) = declared[name]!!.jsonArray.map { it.jsonPrimitive.content.toDouble() }
        assertEquals(listOf(slots.text1.x, slots.text1.y, slots.text1.w, slots.text1.h), box("Text1"))
        val reference2 = slots.reference2!!
        assertEquals(listOf(reference2.x, reference2.y, reference2.w, reference2.h), box("Reference2"))
        assertNotNull(declared["Text2"])
        // The document's box starts an ascent below the slot: the sample's baseline, not the slot.
        val textDoc = BandJson.textDocument(BandJson.layer(generate(cfg), "Text1"))
        assertTrue(textDoc["ps"]!!.jsonArray[1].jsonPrimitive.content.toDouble() > slots.text1.y)
    }

    @Test
    fun `text goes in before the band so it paints on top, with a hidden shadow twin under each slot`() {
        val names = BandJson.layerNames(generate(BibleLottieGenConfig(layout = SlotLayout.SINGLE)))
        val textLayers = names.filter { !it.startsWith("Band") }
        assertEquals(listOf("Text1", "Text1Shadow", "Reference1", "Reference1Shadow"), textLayers)
        val firstBand = names.indexOfFirst { it.startsWith("Band") }
        assertTrue(names.indexOf("Text1") < firstBand, "first in the array is topmost")
        val shadow = BandJson.layer(generate(), "Text1Shadow")
        assertEquals(true, shadow["hd"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals(5, shadow.type, "a real text layer, so the player can fill it in")
    }

    @Test
    fun `two-language layouts add the second pair of slots`() {
        val names = BandJson.layerNames(generate(BibleLottieGenConfig(layout = SlotLayout.STACKED)))
        assertTrue("Text2" in names && "Reference2" in names)
        assertTrue("Text2" !in BandJson.layerNames(generate(BibleLottieGenConfig(layout = SlotLayout.SINGLE))))
    }

    @Test
    fun `a wipe and a ticker cut each text layer with a matte written just before it`() {
        for (anim in listOf(TextAnimation.WIPE, TextAnimation.TICKER)) {
            val doc = generate(BibleLottieGenConfig(textAnimation = anim))
            val names = BandJson.layerNames(doc)
            val matteIndex = names.indexOf("Text1Matte")
            assertEquals(names.indexOf("Text1") - 1, matteIndex, "$anim: matte directly above the text")
            assertEquals(1, BandJson.layer(doc, "Text1Matte").intField("td"))
            assertEquals(1, BandJson.layer(doc, "Text1").intField("tt"))
            assertEquals(anim.name, BandJson.meta(doc).stringField("textAnimation"))
        }
        assertNull(BandJson.layer(generate(), "Text1").intField("tt"), "a fade needs no matte")
    }

    @Test
    fun `alignment pins are written as the player reads them, a ticker always left`() {
        val doc = generate(BibleLottieGenConfig(textAlign = BandTextAlign.RIGHT, referenceAlign = BandTextAlign.LEFT))
        assertEquals(1, BandJson.textDocument(BandJson.layer(doc, "Text1")).intField("j"))
        assertEquals(0, BandJson.textDocument(BandJson.layer(doc, "Reference1")).intField("j"))
        assertEquals("RIGHT", BandJson.meta(doc).stringField("textAlign"))
        val ticker =
            generate(BibleLottieGenConfig(textAnimation = TextAnimation.TICKER, textAlign = BandTextAlign.CENTER))
        assertEquals(0, BandJson.textDocument(BandJson.layer(ticker, "Text1")).intField("j"))
    }

    @Test
    fun `every style and entrance generates a band with the same slots`() {
        for (style in BandStyle.entries) {
            for (entrance in BandEntrance.entries) {
                val doc = generate(BibleLottieGenConfig(bandStyle = style, entrance = entrance))
                val names = BandJson.layerNames(doc)
                assertTrue("Text1" in names && "Reference1" in names, "$style/$entrance keeps the slots")
                assertTrue(names.any { it.startsWith("Band") }, "$style/$entrance draws a band")
            }
        }
    }

    @Test
    fun `wipes and the swipe cut the band with a matte every piece refers to by index`() {
        for (entrance in listOf(BandEntrance.WIPE_LEFT, BandEntrance.WIPE_RIGHT, BandEntrance.SWIPE)) {
            val doc = generate(BibleLottieGenConfig(entrance = entrance, bandStyle = BandStyle.HORIZONTAL_BANDS))
            val layers = BandJson.layers(doc)
            val matte = layers.first { it.name == "BandMatte" }
            assertEquals(1, matte.intField("td"))
            val pieces = layers.filter {
                it.name.startsWith("Band") && it.name != "BandMatte" && !it.name.contains("SwipeBar")
            }
            assertTrue(pieces.isNotEmpty())
            pieces.forEach { assertEquals(matte.intField("ind"), it.intField("tp"), "${it.name} is cut by the matte") }
        }
        val swipe = BandJson.layerNames(generate(BibleLottieGenConfig(entrance = BandEntrance.SWIPE)))
        val topmostBand = swipe.first { it.startsWith("Band") }
        assertEquals("BandSwipeBar", topmostBand, "the bar leads, so it is painted over the fill")
        val fade = BandJson.layers(generate(BibleLottieGenConfig(entrance = BandEntrance.FADE)))
        assertNull(fade.firstOrNull { it.name == "BandMatte" })
    }

    @Test
    fun `gradients paint the fill with a gradient and a colour style with a plain fill`() {
        val gradient = BandJson.layers(generate(BibleLottieGenConfig(bandStyle = BandStyle.GRADIENT_TRIO))).last()
        assertTrue("gf" in BandJson.shapeTypes(gradient))
        val solid = BandJson.layers(generate(BibleLottieGenConfig(bandStyle = BandStyle.SOLID_BAR))).last()
        assertEquals(listOf("rc", "fl", "tr"), BandJson.shapeTypes(solid))
    }

    @Test
    fun `a border adds a stroke piece above the fill`() {
        val bordered = generate(BibleLottieGenConfig(borderThickness = 4))
        val layers = BandJson.layers(bordered).filter { it.name.startsWith("Band") }
        assertTrue("st" in BandJson.shapeTypes(layers[layers.size - 2]), "the stroke sits just above the fill")
        val glass = BandJson.layers(generate(BibleLottieGenConfig(bandStyle = BandStyle.GLASS_PANEL)))
        assertTrue("st" in BandJson.shapeTypes(glass[glass.size - 2]), "glass always has one")
    }

    @Test
    fun `a picture in a role becomes an image under a matte of that role's shape`() {
        val cfg =
            BibleLottieGenConfig(bandStyle = BandStyle.ANGLED_BLADE, images = mapOf(BandColorRole.SECOND to picture()))
        val doc = generate(cfg)
        val layers = BandJson.layers(doc).filter { it.name.startsWith("Band") }
        val image = layers.first { it.type == 2 }
        val matte = layers.first { it.name == image.name.removeSuffix("Image") + "Matte" }
        assertEquals(1, matte.intField("td"))
        assertEquals(matte.intField("ind"), image.intField("tp"))
        assertEquals("band_second", image.stringField("refId"))
        assertEquals(1, BandJson.assets(doc).size)
        assertTrue(BandJson.markers(doc).isNotEmpty())
    }

    @Test
    fun `a background picture keeps the background colour above it as a tint`() {
        val cfg = BibleLottieGenConfig(bgAlpha = 40, images = mapOf(BandColorRole.BACKGROUND to picture()))
        val layers = BandJson.layers(generate(cfg)).filter { it.name.startsWith("Band") }
        assertEquals(2, layers.last().type, "the picture is the bottom-most layer")
        val tint = BandJson.shapeTypes(layers[layers.size - 3])
        assertEquals(listOf("rc", "fl", "tr"), tint, "the tint rectangle sits above it")
        assertTrue(cfg.hasBackgroundImage)
        assertTrue(!BibleLottieGenConfig().hasBackgroundImage)
        assertTrue(!BandImage("data:", 0, 0, "x").isUsable)
    }

    @Test
    fun `a picture that is not usable is drawn as the colour instead`() {
        val cfg = BibleLottieGenConfig(images = mapOf(BandColorRole.BACKGROUND to BandImage("data:", 0, 0, "bad")))
        assertTrue(BandJson.layers(generate(cfg)).none { it.type == 2 })
    }

    @Test
    fun `a zero canvas is refused`() {
        assertFailsWith<IllegalArgumentException> { generate(BibleLottieGenConfig(canvasW = 0)) }
    }

    @Test
    fun `the style enum says which colours it draws from`() {
        assertTrue(BandStyle.SOLID_BAR.let { !it.usesSecond && !it.usesTertiary })
        assertTrue(BandStyle.GRADIENT_BAR.usesSecond)
        assertTrue(BandStyle.WAVE_DECK.usesTertiary)
        assertTrue(TextAnimation.TICKER.isRuntimeDriven && !TextAnimation.FADE.isRuntimeDriven)
    }
}
