package org.churchpresenter.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HosannaTokensTest {

    private fun topLeftRadius(shape: Shape): Float {
        val outline = shape.createOutline(Size(200f, 100f), LayoutDirection.Ltr, Density(1f))
        return (outline as Outline.Rounded).roundRect.topLeftCornerRadius.x
    }

    @Test
    fun `the shape scale matches the radius scale`() {
        val s = HosannaDefaultShapes
        assertEquals(
            listOf(4f, 6f, 8f, 12f, 16f),
            listOf(s.xs, s.s, s.m, s.l, s.xl).map(::topLeftRadius),
        )
        assertEquals(CircleShape, s.full)
    }

    @Test
    fun `the type scale matches the spec`() {
        val t = hosannaTypography(FontFamily.Monospace)
        val expected = listOf(
            Triple(t.display, 32f to 40f, FontWeight.Light),
            Triple(t.headline, 20f to 28f, FontWeight.SemiBold),
            Triple(t.title, 16f to 22f, FontWeight.SemiBold),
            Triple(t.subtitle, 14f to 20f, FontWeight.SemiBold),
            Triple(t.body, 13.5f to 20f, FontWeight.Normal),
            Triple(t.bodyStrong, 13.5f to 20f, FontWeight.SemiBold),
            Triple(t.label, 13f to 18f, FontWeight.SemiBold),
            Triple(t.caption, 12f to 16f, FontWeight.Normal),
            Triple(t.captionStrong, 12f to 16f, FontWeight.SemiBold),
            Triple(t.overline, 10.5f to 14f, FontWeight.Bold),
        )
        expected.forEach { (style, sizes, weight) ->
            assertEquals(sizes.first.sp, style.fontSize)
            assertEquals(sizes.second.sp, style.lineHeight)
            assertEquals(weight, style.fontWeight)
            assertEquals(FontFamily.Monospace, style.fontFamily)
        }
        assertEquals((-0.01).em, t.display.letterSpacing)
        assertEquals(0.06.em, t.overline.letterSpacing)
        assertEquals(0.em, t.body.letterSpacing)
    }

    @Test
    fun `tabular and overline helpers`() {
        assertEquals("tnum", hosannaTypography(FontFamily.Default).body.tabular().fontFeatureSettings)
        assertEquals("SONG BOOK", "Song book".asOverline())
        assertEquals(11.dp, HosannaRadius.nested(HosannaRadius.M, 3.dp))
        assertEquals(3.dp, HosannaTheme.focusRingWidth)
    }

    @Test
    fun `elevation levels follow material`() {
        assertEquals(listOf(0, 1, 3, 6, 8, 12).map { it.dp }, HosannaLevel.entries.map { it.dp })
        listOf(HosannaLightShadows, HosannaDarkShadows).forEach { e ->
            assertTrue(e.getValue(HosannaLevel.Level0).isEmpty())
            HosannaLevel.entries.drop(1).forEach { assertEquals(2, e.getValue(it).size) }
        }
    }

    @Test
    fun `dark shadows are stronger than light ones`() {
        HosannaLevel.entries.drop(1).forEach { level ->
            val light = HosannaLightShadows.getValue(level).sumOf { it.alpha.toDouble() }
            val dark = HosannaDarkShadows.getValue(level).sumOf { it.alpha.toDouble() }
            assertTrue(dark > light, "$level")
        }
    }
}
