package lottiegen

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import lottiegen.lottie.LottieBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LottieBuilderTest {

    private fun builder() = LottieBuilder(w = 1920, h = 1080)

    private fun shapes() = buildJsonArray { add(buildJsonObject { put("ty", JsonPrimitive("gr")) }) }

    private fun transform() = LottieBuilder.defaultTransform()

    private fun JsonObject.layer(i: Int) = getValue("layers").jsonArray[i].jsonObject

    @Test
    fun `a bold weight names the Bold face and a light weight the Regular one`() {
        val b = builder()

        assertEquals("Verdana-Bold", b.addFont("Verdana", 700))
        assertEquals("Verdana-Regular", b.addFont("Verdana", 400))
        assertEquals(2, b.fonts.size)
    }

    @Test
    fun `the weight boundary sits at 700`() {
        val b = builder()

        assertEquals("Arial-Regular", b.addFont("Arial", 699))
        assertEquals("Arial-Bold", b.addFont("Arial", 700))
    }

    @Test
    fun `asking for the same face twice registers it once`() {
        val b = builder()
        b.addFont("Verdana", 700)
        b.addFont("Verdana", 800)

        assertEquals(1, b.fonts.size)
    }

    @Test
    fun `a plain shape layer carries no matte or parent keys`() {
        val b = builder()
        b.addShapeLayer("plain", shapes(), transform())

        val layer = b.toJson().layer(0)
        assertFalse(layer.containsKey("td"))
        assertFalse(layer.containsKey("tt"))
        assertFalse(layer.containsKey("parent"))
    }

    @Test
    fun `a shape layer emits matte and parent keys when asked`() {
        val b = builder()
        b.addShapeLayer("matted", shapes(), transform(), td = 1, tt = 2, parent = 3)

        val layer = b.toJson().layer(0)
        assertEquals(1, layer.getValue("td").jsonPrimitive.content.toInt())
        assertEquals(2, layer.getValue("tt").jsonPrimitive.content.toInt())
        assertEquals(3, layer.getValue("parent").jsonPrimitive.content.toInt())
    }

    @Test
    fun `an image layer follows the same optional-key rule`() {
        val b = builder()
        b.addImageAsset("logo", "data:image/png;base64,AAAA", 10, 10)
        b.addImageLayer("plain", "logo", transform())
        b.addImageLayer("matted", "logo", transform(), td = 1, tt = 2, parent = 0)

        assertFalse(b.toJson().layer(0).containsKey("tt"))
        assertEquals(2, b.toJson().layer(1).getValue("tt").jsonPrimitive.content.toInt())
    }

    @Test
    fun `each layer gets its own index`() {
        val b = builder()
        val first = b.addShapeLayer("a", shapes(), transform())
        val second = b.addShapeLayer("b", shapes(), transform())

        assertNotEquals(first, second)
        assertEquals(first, b.toJson().layer(0).getValue("ind").jsonPrimitive.content.toInt())
    }

    @Test
    fun `a document with no fonts omits the font list entirely`() {
        val b = builder()
        b.addShapeLayer("plain", shapes(), transform())

        val json = b.toJson()
        assertFalse(json.containsKey("fonts"))
        assertFalse(json.containsKey("chars"))
    }

    @Test
    fun `a document with a font carries the font list`() {
        val b = builder()
        b.addFont("Verdana", 400)
        b.addShapeLayer("plain", shapes(), transform())

        val list = b.toJson().getValue("fonts").jsonObject.getValue("list").jsonArray
        assertEquals(1, list.size)
        assertEquals("Verdana-Regular", list[0].jsonObject.getValue("fName").jsonPrimitive.content)
    }

    @Test
    fun `the canvas size and frame rate reach the document`() {
        val json = LottieBuilder(w = 640, h = 360, fr = 60).toJson()

        assertEquals(640, json.getValue("w").jsonPrimitive.content.toInt())
        assertEquals(360, json.getValue("h").jsonPrimitive.content.toInt())
        assertEquals(60, json.getValue("fr").jsonPrimitive.content.toInt())
    }

    @Test
    fun `setDuration drives the out point`() {
        val b = builder()
        b.setDuration(inFrames = 10, holdFrames = 20, outFrames = 5)

        assertEquals(35, b.toJson().getValue("op").jsonPrimitive.content.toInt())
    }

    @Test
    fun `an added image asset reaches the document`() {
        val b = builder()
        b.addImageAsset("logo", "data:image/png;base64,AAAA", 64, 32)

        val asset = b.toJson().getValue("assets").jsonArray.single().jsonObject
        assertEquals("logo", asset.getValue("id").jsonPrimitive.content)
        assertEquals(64, asset.getValue("w").jsonPrimitive.content.toInt())
    }

    @Test
    fun `the default transform is identity on every channel`() {
        val tr = LottieBuilder.defaultTransform()

        assertEquals(100, tr.getValue("o").jsonObject.getValue("k").jsonPrimitive.content.toInt())
        assertEquals(0, tr.getValue("r").jsonObject.getValue("k").jsonPrimitive.content.toInt())
        assertEquals(
            listOf(100.0, 100.0, 100.0),
            tr.getValue("s").jsonObject.getValue("k").jsonArray.map { it.jsonPrimitive.content.toDouble() }
        )
    }

    @Test
    fun `a supplied channel replaces only itself`() {
        val tr = LottieBuilder.defaultTransform(opacity = LottieBuilder.staticProp(40))

        assertEquals(40, tr.getValue("o").jsonObject.getValue("k").jsonPrimitive.content.toInt())
        assertEquals(0, tr.getValue("r").jsonObject.getValue("k").jsonPrimitive.content.toInt())
    }

    @Test
    fun `an animated property is marked animated and a static one is not`() {
        val animated = LottieBuilder.animatedProp(buildJsonArray { add(buildJsonObject { }) })

        assertEquals(1, animated.getValue("a").jsonPrimitive.content.toInt())
        assertEquals(0, LottieBuilder.staticProp(5).getValue("a").jsonPrimitive.content.toInt())
    }
}
