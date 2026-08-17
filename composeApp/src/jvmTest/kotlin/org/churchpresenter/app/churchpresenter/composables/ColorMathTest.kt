package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The colour conversions behind ColorPickerDialog — hex parse/format and the HSV round-trip that the
 * hue/saturation wheel relies on. Colour maths is quietly easy to get wrong (channel order, the wrap
 * at hue 360, an off-by-one in the 0–255 scaling), and a wrong result silently sends the wrong colour
 * to a background or lower third, so the branches and the round-trips are pinned here.
 */
class ColorMathTest {

    // ── cpColorToHex ───────────────────────────────────────────────────────────

    @Test fun `primaries and extremes format to their hex`() {
        assertEquals("#FF0000", cpColorToHex(Color(255, 0, 0)))
        assertEquals("#00FF00", cpColorToHex(Color(0, 255, 0)))
        assertEquals("#0000FF", cpColorToHex(Color(0, 0, 255)))
        assertEquals("#FFFFFF", cpColorToHex(Color(255, 255, 255)))
        assertEquals("#000000", cpColorToHex(Color(0, 0, 0)))
        assertEquals("#123456", cpColorToHex(Color(0x12, 0x34, 0x56)))
    }

    // ── cpTryParseHex ──────────────────────────────────────────────────────────

    @Test fun `a six-digit hex parses, with or without the hash and in any case`() {
        assertEquals("#FF0000", cpColorToHex(assertNotNull(cpTryParseHex("#FF0000"))))
        assertEquals("#112233", cpColorToHex(assertNotNull(cpTryParseHex("112233"))), "the leading # is optional")
        assertEquals("#FF0000", cpColorToHex(assertNotNull(cpTryParseHex("#ff0000"))), "lower case is accepted")
    }

    @Test fun `an eight-digit hex parses as AARRGGBB and the alpha is dropped from the hex form`() =
        assertEquals("#112233", cpColorToHex(assertNotNull(cpTryParseHex("#FF112233"))))

    @Test fun `malformed hex yields null rather than throwing`() {
        assertNull(cpTryParseHex("xyz"))
        assertNull(cpTryParseHex("#12345"), "five digits is neither RGB nor ARGB")
        assertNull(cpTryParseHex(""))
        assertNull(cpTryParseHex("#GG0000"), "non-hex digits")
    }

    @Test fun `format then parse is a round trip for opaque colours`() {
        for (hex in listOf("#FF0000", "#00FF00", "#0000FF", "#123456", "#ABCDEF", "#000000", "#FFFFFF")) {
            assertEquals(hex, cpColorToHex(assertNotNull(cpTryParseHex(hex))), "round trip of $hex")
        }
    }

    // ── HSV ────────────────────────────────────────────────────────────────────

    private fun assertApprox(expected: Float, actual: Float, tol: Float, what: String) =
        assertTrue(abs(expected - actual) <= tol, "$what: expected ~$expected, was $actual")

    @Test fun `converting a colour to HSV reads the expected hue, saturation and value`() {
        val (hr, sr, vr) = cpColorToHsv(Color(255, 0, 0))
        assertApprox(0f, hr, 0.5f, "red hue"); assertApprox(
            1f,
            sr,
            0.01f,
            "red sat",
        ); assertApprox(1f, vr, 0.01f, "red val")

        val (hg, _, _) = cpColorToHsv(Color(0, 255, 0))
        assertApprox(120f, hg, 0.5f, "green hue")

        val (hb, _, _) = cpColorToHsv(Color(0, 0, 255))
        assertApprox(240f, hb, 0.5f, "blue hue")
    }

    @Test fun `a grey has zero saturation and a value equal to its brightness`() {
        val (h, s, v) = cpColorToHsv(Color(128, 128, 128))
        assertApprox(0f, h, 0.5f, "grey hue is undefined, reported 0")
        assertApprox(0f, s, 0.01f, "grey saturation")
        assertApprox(128f / 255f, v, 0.01f, "grey value")
    }

    @Test fun `black is value zero and white is full value with no saturation`() {
        assertApprox(0f, cpColorToHsv(Color(0, 0, 0)).third, 0.01f, "black value")
        val white = cpColorToHsv(Color(255, 255, 255))
        assertApprox(1f, white.third, 0.01f, "white value")
        assertApprox(0f, white.second, 0.01f, "white saturation")
    }

    @Test fun `HSV to colour produces the pure primaries at full saturation and value`() {
        assertEquals("#FF0000", cpColorToHex(cpHsvToColor(0f, 1f, 1f)))
        assertEquals("#00FF00", cpColorToHex(cpHsvToColor(120f, 1f, 1f)))
        assertEquals("#0000FF", cpColorToHex(cpHsvToColor(240f, 1f, 1f)))
    }

    @Test fun `colour to HSV and back reproduces the colour`() {
        for (c in listOf(Color(200, 50, 90), Color(30, 180, 240), Color(255, 200, 0))) {
            val (h, s, v) = cpColorToHsv(c)
            val back = cpHsvToColor(h, s, v)
            assertApprox(c.red, back.red, 0.01f, "red channel")
            assertApprox(c.green, back.green, 0.01f, "green channel")
            assertApprox(c.blue, back.blue, 0.01f, "blue channel")
        }
    }
}
