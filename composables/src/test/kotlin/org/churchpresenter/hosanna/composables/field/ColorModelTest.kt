package org.churchpresenter.hosanna.composables.field

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColorModelTest {

    private fun assertNear(expected: Float, actual: Float) =
        assertTrue(abs(expected - actual) < 0.01f, "$expected ≈ $actual")

    @Test
    fun `hex, short hex, alpha hex and rgb all parse`() {
        assertEquals(Color(0xFF1F5BB0), parseColor("#1f5bb0"))
        assertEquals(Color(0xFF1F5BB0), parseColor(" 1F5BB0 "))
        assertEquals(Color(0xFFFF0000), parseColor("#f00"))
        assertEquals(Color(0x801F5BB0), parseColor("#1F5BB080"))
        assertEquals(Color(0xFFF0C27A), parseColor("rgb(240, 194, 122)"))
        assertNear(0.5f, parseColor("rgba(240,194,122,0.5)")!!.alpha)
    }

    @Test
    fun `malformed values do not parse`() {
        listOf("#12", "zzzzzz", "#1F5BB", "rgb(300,0,0)", "rgb(1,2)", "rgba(1,2,3,2)", "rgb(a,b,c)", "").forEach {
            assertNull(parseColor(it), it)
        }
    }

    @Test
    fun `values read as uppercase hex and whole percents`() {
        assertEquals("#1F5BB0", hexText(Color(0xFF1F5BB0)))
        assertEquals("#1F5BB0", hexText(Color(0x331F5BB0)))
        assertEquals(70, opacityPercent(Color(0xB31B1F24)))
        assertEquals("70%", opacityText(Color(0xB31B1F24)))
        assertNull(opacityText(Color(0xFF1B1F24)))
        assertNear(0.5f, withOpacityPercent(Color.Red, 50).alpha)
        assertEquals(1f, withOpacityPercent(Color.Red, 150).alpha)
    }

    @Test
    fun `colors round-trip through hue, saturation and value`() {
        val samples = listOf(Color(0xFF1F5BB0), Color(0xFFC43030), Color(0xFF14855A), Color(0xFF808080), Color.Black)
        samples.forEach { color ->
            assertEquals(hexText(color), hexText(color.toHsv().toColor()), hexText(color))
        }
        assertEquals(Color(1f, 0f, 0f), hueColor(0f))
        assertEquals(Color(0f, 1f, 0f), hueColor(120f))
        assertEquals(Color(0f, 0f, 1f), hueColor(240f))
        assertEquals(7, HueStops.size)
    }

    @Test
    fun `the area maps left to right as saturation and top to bottom as value`() {
        val start = Hsv(200f, 0.5f, 0.5f)
        val picked = areaHsv(start, Offset(30f, 25f), width = 100f, height = 100f)
        assertNear(0.3f, picked.saturation)
        assertNear(0.75f, picked.value)
        assertEquals(200f, picked.hue)
        val outside = areaHsv(start, Offset(-10f, 200f), width = 100f, height = 100f)
        assertEquals(0f, outside.saturation)
        assertEquals(0f, outside.value)
    }

    @Test
    fun `arrow keys nudge by one percent, ten with shift`() {
        val hsv = Hsv(0f, 0.5f, 0.5f)
        assertNear(0.51f, nudgeHsv(hsv, Key.DirectionRight, large = false)!!.saturation)
        assertNear(0.4f, nudgeHsv(hsv, Key.DirectionLeft, large = true)!!.saturation)
        assertNear(0.6f, nudgeHsv(hsv, Key.DirectionUp, large = true)!!.value)
        assertNear(0.49f, nudgeHsv(hsv, Key.DirectionDown, large = false)!!.value)
        assertNull(nudgeHsv(hsv, Key.A, large = false))
    }

    @Test
    fun `typed colors keep or drop opacity as the field allows`() {
        val current = Color(0x801B1F24)
        assertEquals(1f, typedColor("#C43030", current, allowOpacity = false)!!.alpha)
        assertEquals(current.alpha, typedColor("#C43030", current, allowOpacity = true)!!.alpha)
        assertEquals(Color(0x40C43030), typedColor("#C4303040", current, allowOpacity = true))
        assertNull(typedColor("nope", current, allowOpacity = true))
    }

    @Test
    fun `None, blank and the field's text`() {
        assertTrue(isNoneText("", "None"))
        assertTrue(isNoneText(" none ", "None"))
        assertFalse(isNoneText("#000", "None"))
        assertEquals("None", fieldText(null, "None"))
        assertEquals("#1F5BB0", fieldText(Color(0xFF1F5BB0), "None"))
        assertTrue(sameRgb(Color(0x801F5BB0), Color(0xFF1F5BB0)))
        assertFalse(sameRgb(Color(0xFF1F5BB0), null))
    }
}
