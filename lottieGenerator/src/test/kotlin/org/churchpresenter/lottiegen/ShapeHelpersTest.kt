package org.churchpresenter.lottiegen

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.lottiegen.lottie.makeAnimatedRect
import org.churchpresenter.lottiegen.lottie.makeAnimatedStroke
import org.churchpresenter.lottiegen.lottie.makeEllipse
import org.churchpresenter.lottiegen.lottie.makeGroup
import org.churchpresenter.lottiegen.lottie.makeRect
import org.churchpresenter.lottiegen.lottie.makeStroke
import org.churchpresenter.lottiegen.lottie.makeTrimPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShapeHelpersTest {

    private fun kfs(): JsonArray = buildJsonArray { add(buildJsonObject { put("t", JsonPrimitive(0)) }) }

    private fun JsonObject.pos(): List<Double> =
        getValue("p").jsonObject.getValue("k").jsonArray.map { it.jsonPrimitive.content.toDouble() }

    @Test
    fun `a rect with no position sits at the origin`() {
        assertEquals(listOf(0.0, 0.0), makeRect(w = 10.0, h = 4.0).pos())
    }

    @Test
    fun `a rect honours an explicit position`() {
        assertEquals(listOf(3.0, -7.0), makeRect(w = 10.0, h = 4.0, position = listOf(3.0, -7.0)).pos())
    }

    @Test
    fun `an animated rect defaults and honours position the same way`() {
        assertEquals(listOf(0.0, 0.0), makeAnimatedRect(kfs()).pos())
        assertEquals(listOf(5.0, 6.0), makeAnimatedRect(kfs(), position = listOf(5.0, 6.0)).pos())
    }

    @Test
    fun `an ellipse defaults and honours position the same way`() {
        assertEquals(listOf(0.0, 0.0), makeEllipse(w = 8.0, h = 8.0).pos())
        assertEquals(listOf(1.0, 2.0), makeEllipse(w = 8.0, h = 8.0, position = listOf(1.0, 2.0)).pos())
    }

    @Test
    fun `a trim path with no keyframes is static on both ends`() {
        val trim = makeTrimPath(start = 10.0, end = 90.0)

        assertEquals(0, trim.getValue("s").jsonObject.getValue("a").jsonPrimitive.content.toInt())
        assertEquals(0, trim.getValue("e").jsonObject.getValue("a").jsonPrimitive.content.toInt())
        assertEquals(10.0, trim.getValue("s").jsonObject.getValue("k").jsonPrimitive.content.toDouble())
        assertEquals(90.0, trim.getValue("e").jsonObject.getValue("k").jsonPrimitive.content.toDouble())
    }

    @Test
    fun `keyframed trim ends are marked animated`() {
        val trim = makeTrimPath(startKFs = kfs(), endKFs = kfs())

        assertEquals(1, trim.getValue("s").jsonObject.getValue("a").jsonPrimitive.content.toInt())
        assertEquals(1, trim.getValue("e").jsonObject.getValue("a").jsonPrimitive.content.toInt())
    }

    @Test
    fun `a trim path can animate one end and pin the other`() {
        val trim = makeTrimPath(end = 42.0, startKFs = kfs())

        assertEquals(1, trim.getValue("s").jsonObject.getValue("a").jsonPrimitive.content.toInt())
        assertEquals(0, trim.getValue("e").jsonObject.getValue("a").jsonPrimitive.content.toInt())
        assertEquals(42.0, trim.getValue("e").jsonObject.getValue("k").jsonPrimitive.content.toDouble())
    }

    @Test
    fun `a zero-width stroke is dropped rather than emitted`() {
        assertNull(makeStroke(color = listOf(1.0, 0.0, 0.0), width = 0.0))
        assertNull(makeStroke(color = listOf(1.0, 0.0, 0.0), width = -2.0))
    }

    @Test
    fun `a solid stroke carries no dash array`() {
        val stroke = assertNotNull(makeStroke(color = listOf(1.0, 0.0, 0.0), width = 3.0))

        assertFalse(stroke.containsKey("d"))
        assertEquals(3.0, stroke.getValue("w").jsonObject.getValue("k").jsonPrimitive.content.toDouble())
    }

    @Test
    fun `a dashed stroke gains a dash array`() {
        val stroke = assertNotNull(makeStroke(color = listOf(1.0, 0.0, 0.0), width = 3.0, dashPx = 6.0))

        assertTrue(stroke.containsKey("d"))
    }

    @Test
    fun `an animated stroke follows the same dash rule`() {
        assertFalse(makeAnimatedStroke(listOf(1.0, 1.0, 1.0), kfs()).containsKey("d"))
        assertTrue(makeAnimatedStroke(listOf(1.0, 1.0, 1.0), kfs(), dashPx = 4.0).containsKey("d"))
    }

    private fun JsonObject.groupTransform(): JsonObject =
        getValue("it").jsonArray.map { it.jsonObject }.single { it["ty"]?.jsonPrimitive?.content == "tr" }

    @Test
    fun `a group with no transform gets an identity transform`() {
        val tr = makeGroup(listOf(makeRect(4.0, 4.0))).groupTransform()

        assertEquals(listOf(0.0, 0.0), tr.getValue("p").jsonObject.getValue("k").jsonArray
            .map { it.jsonPrimitive.content.toDouble() })
        assertEquals(listOf(100.0, 100.0), tr.getValue("s").jsonObject.getValue("k").jsonArray
            .map { it.jsonPrimitive.content.toDouble() })
        assertEquals(100, tr.getValue("o").jsonObject.getValue("k").jsonPrimitive.content.toInt())
        assertEquals(0, tr.getValue("r").jsonObject.getValue("k").jsonPrimitive.content.toInt())
    }

    @Test
    fun `a supplied transform overrides every channel it provides`() {
        val supplied = buildJsonObject {
            put("p", buildJsonObject { put("a", JsonPrimitive(0)); put("k", buildJsonArray {
                add(JsonPrimitive(9.0)); add(JsonPrimitive(9.0)) }) })
            put("o", buildJsonObject { put("a", JsonPrimitive(0)); put("k", JsonPrimitive(25)) })
        }
        val tr = makeGroup(listOf(makeRect(4.0, 4.0)), transform = supplied).groupTransform()

        assertEquals(listOf(9.0, 9.0), tr.getValue("p").jsonObject.getValue("k").jsonArray
            .map { it.jsonPrimitive.content.toDouble() })
        assertEquals(25, tr.getValue("o").jsonObject.getValue("k").jsonPrimitive.content.toInt())
        assertEquals(listOf(100.0, 100.0), tr.getValue("s").jsonObject.getValue("k").jsonArray
            .map { it.jsonPrimitive.content.toDouble() })
    }

    @Test
    fun `group items are kept ahead of the transform item`() {
        val group = makeGroup(listOf(makeRect(1.0, 1.0), makeEllipse(2.0, 2.0)))
        val types = group.getValue("it").jsonArray.map { it.jsonObject.getValue("ty").jsonPrimitive.content }

        assertEquals(listOf("rc", "el", "tr"), types)
    }
}
