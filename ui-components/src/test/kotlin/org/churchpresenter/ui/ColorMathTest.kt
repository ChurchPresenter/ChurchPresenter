package org.churchpresenter.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The colour picker's own arithmetic: hex in, HSV round trips, hex out.
 *
 * This is where a wrong answer is invisible until it reaches a projector. `cpTryParseHex` is what
 * the hex field commits, so it has to reject anything it cannot read rather than guess — a silent
 * fallback to white would give the operator a colour they never typed. The HSV pair backs the
 * saturation square and the hue bar, and the two have to agree, or dragging one moves the other.
 */
class ColorMathTest {

    /**
     * [tolerance] is generous on purpose: a `Color` holds 8-bit channels, so a hue round trip is
     * quantised — 200f comes back as 200.625f and 359f as 359.06f. Asserting exact equality would
     * be asserting that colours are stored as floats, which they are not.
     */
    private fun assertClose(expected: Float, actual: Float, what: String, tolerance: Float = 0.01f) {
        assertTrue(abs(expected - actual) < tolerance, "$what: expected ~$expected but was $actual")
    }

    // ── Parsing ─────────────────────────────────────────────────────────────────

    @Test
    fun `a six-digit hex reads as opaque rgb`() {
        val c = cpTryParseHex("#FF0000")
        assertEquals(Color(255, 0, 0), c)
    }

    @Test
    fun `the leading hash is optional`() {
        assertEquals(cpTryParseHex("#00FF00"), cpTryParseHex("00FF00"))
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        assertEquals(cpTryParseHex("0000FF"), cpTryParseHex("  0000FF  "))
    }

    @Test
    fun `an eight-digit hex carries alpha first`() {
        val c = cpTryParseHex("#80FF0000")
        assertEquals(Color(255, 0, 0, 128), c, "AARRGGBB — the alpha pair leads")
    }

    @Test
    fun `lower case digits parse`() {
        assertEquals(cpTryParseHex("#ABCDEF"), cpTryParseHex("#abcdef"))
    }

    @Test
    fun `a length that is neither six nor eight is rejected`() {
        listOf("", "F", "FFF", "FFFFF", "FFFFFFF", "FFFFFFFFF").forEach {
            assertNull(cpTryParseHex(it), "\"$it\" is not a colour and must not be guessed at")
        }
    }

    @Test
    fun `non-hex characters are rejected rather than guessed`() {
        listOf("GGGGGG", "#ZZZZZZ", "12 456", "#00 000").forEach {
            assertNull(cpTryParseHex(it), "\"$it\" must not parse")
        }
    }

    @Test
    fun `a signed pair parses rather than being rejected -- known quirk`() {
        // "-1".toInt(16) is -1 and Color clamps it, so "#-10000" reads as red. Harmless (the field
        // only ever receives what the picker writes) but pinned so it is a decision, not a surprise.
        assertEquals(Color(255, 0, 0), cpTryParseHex("#-10000"))
    }

    // ── HSV ─────────────────────────────────────────────────────────────────────

    @Test
    fun `pure red is hue zero, fully saturated, full value`() {
        val (h, s, v) = cpColorToHsv(Color(255, 0, 0))
        assertClose(0f, h, "hue")
        assertClose(1f, s, "saturation")
        assertClose(1f, v, "value")
    }

    @Test
    fun `the primaries land on their own thirds of the wheel`() {
        assertClose(120f, cpColorToHsv(Color(0, 255, 0)).first, "green hue")
        assertClose(240f, cpColorToHsv(Color(0, 0, 255)).first, "blue hue")
    }

    @Test
    fun `grey has no saturation and black has no value`() {
        assertClose(0f, cpColorToHsv(Color(128, 128, 128)).second, "grey saturation")
        assertClose(0f, cpColorToHsv(Color.Black).third, "black value")
    }

    @Test
    fun `white is unsaturated at full value`() {
        val (_, s, v) = cpColorToHsv(Color.White)
        assertClose(0f, s, "saturation")
        assertClose(1f, v, "value")
    }

    @Test
    fun `every sixth of the wheel converts back to itself`() {
        listOf(0f, 60f, 120f, 180f, 240f, 300f, 359f).forEach { hue ->
            val (h, s, v) = cpColorToHsv(cpHsvToColor(hue, 1f, 1f))
            assertClose(hue, h, "hue $hue survives the round trip", tolerance = 1f)
            assertClose(1f, s, "saturation at hue $hue")
            assertClose(1f, v, "value at hue $hue")
        }
    }

    @Test
    fun `a mid saturation and value round trips`() {
        val (h, s, v) = cpColorToHsv(cpHsvToColor(200f, 0.5f, 0.5f))
        assertClose(200f, h, "hue", tolerance = 1f)
        assertClose(0.5f, s, "saturation")
        assertClose(0.5f, v, "value")
    }

    @Test
    fun `zero saturation is grey whatever the hue`() {
        val a = cpHsvToColor(0f, 0f, 0.5f)
        val b = cpHsvToColor(300f, 0f, 0.5f)
        assertEquals(a, b, "with no saturation the hue cannot matter")
    }

    @Test
    fun `zero value is black whatever the hue and saturation`() {
        assertEquals(Color.Black, cpHsvToColor(123f, 1f, 0f).copy(alpha = 1f))
    }
}
