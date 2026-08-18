package lottiegen.spec

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import lottiegen.lottie.LottieGenerator
import lottiegen.model.LottieGenConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Interpreter coverage for designer-baked [ImageElement]s: embedded asset + ty=2 layer,
 * FIT/STRETCH/COVER scale math, the td/tt rounded-corner matte, alphaFactor, and the
 * silent skip for an element with no image.
 */
class ImageElementTest {

    /** 1×1 transparent PNG. */
    private val dataUri = "data:image/png;base64," +
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="

    private fun spec(element: ImageElement) = StyleSpec(elements = listOf(element))

    /** naturalW = 2 × naturalH, rendered into a square Em box — makes the modes distinct. */
    private fun image(
        scaleMode: ImageScaleMode = ImageScaleMode.FIT,
        corner: CornerSpec = CornerSpec.None,
        alphaFactor: Double = 1.0,
        dataUri: String = this.dataUri
    ) = ImageElement(
        id = "img",
        name = "Art",
        dataUri = dataUri,
        naturalW = 100,
        naturalH = 50,
        size = SizeSpec.Em(2.0, 2.0),
        scaleMode = scaleMode,
        corner = corner,
        alphaFactor = alphaFactor
    )

    private fun generate(element: ImageElement): JsonObject =
        LottieGenerator.generate(LottieGenConfig(), SpecStyleGenerator(spec(element)))

    private fun JsonObject.layers() = this["layers"]!!.jsonArray.map { it.jsonObject }

    private fun JsonObject.imageLayer(): JsonObject =
        layers().single { it["ty"]!!.jsonPrimitive.int == 2 }

    /** Static (a=0) scale [sx, sy] of a layer. */
    private fun JsonObject.staticScale(): Pair<Double, Double> {
        val s = this["ks"]!!.jsonObject["s"]!!.jsonObject
        assertEquals(0, s["a"]!!.jsonPrimitive.int, "scale expected static")
        val k = s["k"]!!.jsonArray
        return (k[0] as JsonPrimitive).double to (k[1] as JsonPrimitive).double
    }

    @Test
    fun embedsAssetAndReferencesItFromTheImageLayer() {
        val json = generate(image())
        val asset = json["assets"]!!.jsonArray.map { it.jsonObject }
            .single { it["id"]!!.jsonPrimitive.content == "img_img" }
        assertEquals(dataUri, asset["p"]!!.jsonPrimitive.content)
        assertEquals(100, asset["w"]!!.jsonPrimitive.int)
        assertEquals(50, asset["h"]!!.jsonPrimitive.int)
        assertEquals("img_img", json.imageLayer()["refId"]!!.jsonPrimitive.content)
    }

    @Test
    fun scaleModesMapTheNaturalDimsOntoTheBox() {
        val fit = generate(image(ImageScaleMode.FIT)).imageLayer().staticScale()
        val stretch = generate(image(ImageScaleMode.STRETCH)).imageLayer().staticScale()
        val cover = generate(image(ImageScaleMode.COVER)).imageLayer().staticScale()

        // STRETCH: per-axis; the box is square and the image is 2:1, so sy = 2·sx.
        assertEquals(stretch.first * 2, stretch.second, 1e-6)
        // FIT: uniform at the smaller axis factor (the width axis here).
        assertEquals(stretch.first, fit.first, 1e-6)
        assertEquals(fit.first, fit.second, 1e-6)
        // COVER: uniform at the larger axis factor (the height axis here).
        assertEquals(stretch.second, cover.first, 1e-6)
        assertEquals(cover.first, cover.second, 1e-6)
    }

    @Test
    fun roundedCornersEmitMattePair() {
        val layers = generate(image(corner = CornerSpec.Em(0.3))).layers()
        val maskIndex = layers.indexOfFirst { it["td"]?.jsonPrimitive?.int == 1 }
        assertTrue(maskIndex >= 0, "expected a td=1 matte layer")
        assertEquals(1, layers[maskIndex + 1]["tt"]?.jsonPrimitive?.int, "image must be matted")
        assertEquals(2, layers[maskIndex + 1]["ty"]!!.jsonPrimitive.int)
    }

    @Test
    fun coverAlwaysMattesAndPlainFitDoesNot() {
        val cover = generate(image(ImageScaleMode.COVER)).layers()
        assertTrue(cover.any { it["td"]?.jsonPrimitive?.int == 1 }, "COVER must clip via matte")

        val fit = generate(image(ImageScaleMode.FIT)).layers()
        assertTrue(fit.none { it["td"]?.jsonPrimitive?.int == 1 }, "FIT with no corners must not matte")
        assertNull(fit.single { it["ty"]!!.jsonPrimitive.int == 2 }["tt"], "unmatted image must carry no tt")
    }

    @Test
    fun alphaFactorSetsRestOpacity() {
        val opacity = generate(image(alphaFactor = 0.5)).imageLayer()["ks"]!!
            .jsonObject["o"]!!.jsonObject
        assertEquals(0, opacity["a"]!!.jsonPrimitive.int)
        assertEquals(50.0, opacity["k"]!!.jsonPrimitive.double, 1e-6)
    }

    @Test
    fun blankImageRendersNothingAndNeverFails() {
        val json = generate(image(dataUri = ""))
        assertTrue(json.layers().none { it["ty"]!!.jsonPrimitive.int == 2 }, "no image layer expected")
        assertTrue(
            json["assets"]!!.jsonArray.isEmpty(),
            "no asset expected for a blank image element"
        )
    }
}
