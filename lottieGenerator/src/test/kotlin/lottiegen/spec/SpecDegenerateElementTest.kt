package lottiegen.spec

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import lottiegen.lottie.LottieGenerator
import lottiegen.model.LottieGenConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpecDegenerateElementTest {

    private val png = "data:image/png;base64," +
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="

    private fun generate(element: ElementSpec, cfg: LottieGenConfig = LottieGenConfig()): JsonObject =
        LottieGenerator.generate(cfg, SpecStyleGenerator(StyleSpec(elements = listOf(element))))

    private fun JsonObject.layers() = getValue("layers").jsonArray.map { it.jsonObject }
    private fun JsonObject.imageLayers() = layers().filter { it["ty"]?.jsonPrimitive?.int == 2 }
    private fun JsonObject.assets() = getValue("assets").jsonArray

    @Test
    fun `a logo element with no logo configured draws nothing`() {
        val json = generate(LogoElement(id = "logo"), LottieGenConfig(logoEnabled = true))

        assertTrue(json.imageLayers().isEmpty())
        assertTrue(json.assets().isEmpty())
    }

    @Test
    fun `a logo with zero dimensions draws nothing`() {
        val cfg = LottieGenConfig(logoEnabled = true, logoData = png, logoW = 0, logoH = 0)

        assertTrue(generate(LogoElement(id = "logo"), cfg).imageLayers().isEmpty())
    }

    @Test
    fun `a logo with a negative dimension draws nothing`() {
        val cfg = LottieGenConfig(logoEnabled = true, logoData = png, logoW = 10, logoH = -1)

        assertTrue(generate(LogoElement(id = "logo"), cfg).imageLayers().isEmpty())
    }

    @Test
    fun `a fully configured logo does draw`() {
        val cfg = LottieGenConfig(logoEnabled = true, logoData = png, logoW = 64, logoH = 64)
        val json = generate(LogoElement(id = "logo"), cfg)

        assertEquals(1, json.imageLayers().size)
        assertEquals(1, json.assets().size)
    }

    private fun image(dataUri: String = png, w: Int = 100, h: Int = 50) = ImageElement(
        id = "img", name = "Art", dataUri = dataUri,
        naturalW = w, naturalH = h, size = SizeSpec.Em(2.0, 2.0)
    )

    @Test
    fun `an image element with no data draws nothing`() {
        assertTrue(generate(image(dataUri = "")).imageLayers().isEmpty())
    }

    @Test
    fun `an image with a zero natural dimension draws nothing`() {
        assertTrue(generate(image(w = 0)).imageLayers().isEmpty())
        assertTrue(generate(image(h = 0)).imageLayers().isEmpty())
    }

    @Test
    fun `an image with a zero resolved box draws nothing`() {
        val flat = ImageElement(
            id = "img", name = "Art", dataUri = png,
            naturalW = 100, naturalH = 50, size = SizeSpec.Em(0.0, 0.0)
        )

        assertTrue(generate(flat).imageLayers().isEmpty())
    }

    @Test
    fun `a fully configured image does draw`() {
        assertEquals(1, generate(image()).imageLayers().size)
    }

    @Test
    fun `a polygon with no vertices does not throw`() {
        val json = generate(PolygonElement(id = "poly", verticesEm = emptyList()))

        assertTrue(json.layers().isNotEmpty(), "the document should still assemble")
    }

    @Test
    fun `a polygon with vertices produces a shape layer`() {
        val poly = PolygonElement(
            id = "poly",
            verticesEm = listOf(listOf(0.0, 0.0), listOf(2.0, 0.0), listOf(1.0, 1.5))
        )

        assertTrue(generate(poly).layers().any { it["ty"]?.jsonPrimitive?.int == 4 })
    }

    @Test
    fun `a single-copy repeat still renders`() {
        val poly = PolygonElement(
            id = "poly",
            verticesEm = listOf(listOf(0.0, 0.0), listOf(1.0, 0.0), listOf(1.0, 1.0)),
            repeat = RepeatSpec(copies = 1)
        )

        assertTrue(generate(poly).layers().any { it["ty"]?.jsonPrimitive?.int == 4 })
    }

    @Test
    fun `a repeat with several copies emits a repeater`() {
        val poly = PolygonElement(
            id = "poly",
            verticesEm = listOf(listOf(0.0, 0.0), listOf(1.0, 0.0), listOf(1.0, 1.0)),
            repeat = RepeatSpec(copies = 4, offsetXEm = 1.5)
        )
        val json = generate(poly)

        val hasRepeater = json.layers().any { layer ->
            layer["shapes"]?.jsonArray?.any { it.jsonObject.toString().contains("\"rp\"") } == true
        }
        assertTrue(hasRepeater, "copies > 1 should produce a repeater item")
    }

    @Test
    fun `a negative repeat offset still renders`() {
        val poly = PolygonElement(
            id = "poly",
            verticesEm = listOf(listOf(0.0, 0.0), listOf(1.0, 0.0), listOf(1.0, 1.0)),
            repeat = RepeatSpec(copies = 3, offsetXEm = -1.5, fadeOut = true)
        )

        assertTrue(generate(poly).layers().any { it["ty"]?.jsonPrimitive?.int == 4 })
    }
}
