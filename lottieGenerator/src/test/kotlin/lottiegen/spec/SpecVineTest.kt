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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Interpreter coverage for the vine primitives: curved paths, trim draw-on, pivot
 * anchors, staggered scale — generated through the real pipeline from the bundled
 * demo_vine.json.
 */
class SpecVineTest {

    private fun vineJson(align: String): JsonObject {
        val generator = SpecStyleGenerator.fromResource("/styles/demo_vine.json")
        return LottieGenerator.generate(LottieGenConfig(align = align), generator)
    }

    private fun JsonObject.layers() = this["layers"]!!.jsonArray.map { it.jsonObject }

    private fun JsonObject.groupItems(): List<JsonObject> =
        this["shapes"]!!.jsonArray[0].jsonObject["it"]!!.jsonArray.map { it.jsonObject }

    private fun List<JsonObject>.ofType(type: String): List<JsonObject> =
        filter { it["ty"]?.jsonPrimitive?.content == type }

    @Test
    fun vineLayerHasCurvedPathAndAnimatedTrim() {
        val vine = vineJson("left").layers().first { it["nm"]!!.jsonPrimitive.content == "Vine" }
        val items = vine.groupItems()

        // Curved path: at least one non-zero tangent component.
        val path = items.ofType("sh").single()
        val k = path["ks"]!!.jsonObject["k"]!!.jsonObject
        val outTangents = k["o"]!!.jsonArray.map { it.jsonArray.map { c -> (c as JsonPrimitive).double } }
        assertTrue(outTangents.flatten().any { it != 0.0 }, "expected bezier tangents on the vine path")

        // Trim: present, end animated 0 -> 100 across the in phase.
        val trim = items.ofType("tm").single()
        val end = trim["e"]!!.jsonObject
        assertEquals(1, end["a"]!!.jsonPrimitive.int, "trim end must be animated")
        val endKfs = end["k"]!!.jsonArray.map { it.jsonObject }
        val firstValue = endKfs.first()["s"]!!.jsonArray[0].jsonPrimitive.double
        val restValue = endKfs.first { it["t"]!!.jsonPrimitive.int == 240 }["s"]!!.jsonArray[0].jsonPrimitive.double
        assertEquals(0.0, firstValue, 1e-6)
        assertEquals(100.0, restValue, 1e-6)

        // Stroke painted with the accent role.
        assertTrue(items.ofType("st").isNotEmpty(), "vine must be stroke-painted")
    }

    @Test
    fun leavesArePivotedAndStaggered() {
        val layers = vineJson("left").layers()
        val leaves = layers.filter { it["nm"]!!.jsonPrimitive.content.startsWith("Leaf") }
        assertEquals(3, leaves.size)

        for (leaf in leaves) {
            val transform = leaf["ks"]!!.jsonObject
            // Pivot: non-zero anchor.
            val anchor = transform["a"]!!.jsonObject["k"]!!.jsonArray
            val anchorX = (anchor[0] as JsonPrimitive).double
            assertTrue(anchorX != 0.0, "leaf must be pivoted at its stem")
            // Scale animated.
            assertEquals(1, transform["s"]!!.jsonObject["a"]!!.jsonPrimitive.int, "leaf scale must be animated")
        }

        // Staggering: each leaf's scale departs from zero at a different keyframe time.
        val departTimes = leaves.map { leaf ->
            val kfs = leaf["ks"]!!.jsonObject["s"]!!.jsonObject["k"]!!.jsonArray.map { it.jsonObject }
            kfs.first { (it["s"]!!.jsonArray[0] as JsonPrimitive).double == 0.0 && it["t"]!!.jsonPrimitive.int > 0 }["t"]!!.jsonPrimitive.int
        }
        assertEquals(departTimes.distinct().size, departTimes.size, "leaves must be staggered")
    }

    @Test
    fun rightAlignmentMirrorsCurveHandedness() {
        fun vinePathGeometry(align: String): Pair<List<Double>, List<Double>> {
            val vine = vineJson(align).layers().first { it["nm"]!!.jsonPrimitive.content == "Vine" }
            val k = vine.groupItems().ofType("sh").single()["ks"]!!.jsonObject["k"]!!.jsonObject
            val vertexXs = k["v"]!!.jsonArray.map { (it.jsonArray[0] as JsonPrimitive).double }
            val tangentXs = k["o"]!!.jsonArray.map { (it.jsonArray[0] as JsonPrimitive).double }
            return vertexXs to tangentXs
        }

        val (leftVerts, leftTangents) = vinePathGeometry("left")
        val (rightVerts, rightTangents) = vinePathGeometry("right")
        leftVerts.zip(rightVerts).forEach { (l, r) -> assertEquals(l, -r, 1e-6, "vertex x must mirror") }
        leftTangents.zip(rightTangents).forEach { (l, r) -> assertEquals(l, -r, 1e-6, "tangent x must mirror") }
    }

    @Test
    fun demoVineDecodesAsTemplate() {
        val stream = assertNotNull(javaClass.getResourceAsStream("/styles/demo_vine.json"))
        val spec = SpecJson.decode(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        assertEquals(7, spec.elements.size)
        assertTrue(spec.elements.any { it is PathElement })
    }
}
