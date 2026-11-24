package org.churchpresenter.hosanna

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HosannaRulesTest {

    private val styles = hosannaTypography(FontFamily.Default).let {
        listOf(
            it.display, it.headline, it.title, it.subtitle, it.body,
            it.bodyStrong, it.label, it.caption, it.captionStrong, it.overline,
        )
    }

    @Test
    fun `state layers get stronger the more the user does`() {
        assertTrue(HosannaStateAlpha.HOVER < HosannaStateAlpha.PRESSED)
        assertTrue(HosannaStateAlpha.PRESSED < HosannaStateAlpha.DRAGGED)
        assertEquals(HosannaStateAlpha.FOCUS, HosannaStateAlpha.PRESSED)
        assertTrue(HosannaStateAlpha.DISABLED_CONTAINER < HosannaStateAlpha.DISABLED_CONTENT)
        assertEquals(0.32f, HosannaStateAlpha.SCRIM)
        assertTrue(HosannaStateAlpha.CONTENT_TYPE_CONTAINER < HosannaStateAlpha.CONTENT_TYPE_EDGE)
        assertTrue(HosannaStateAlpha.DRAGGED < HosannaStateAlpha.SECONDARY_FILL)
        assertTrue(HosannaStateAlpha.SECONDARY_FILL < HosannaStateAlpha.DISABLED_CONTENT)
        assertTrue(HosannaStateAlpha.DISABLED_CONTENT < HosannaStateAlpha.MUTED_CONTENT)
        assertTrue(HosannaStateAlpha.MUTED_CONTENT < 1f)
    }

    @Test
    fun `only the four bundled weights are used`() {
        val bundled = setOf(FontWeight.Light, FontWeight.Normal, FontWeight.SemiBold, FontWeight.Bold)
        styles.forEach { assertTrue(it.fontWeight in bundled, "${it.fontWeight}") }
        assertEquals(listOf(styles.first()), styles.filter { it.fontWeight == FontWeight.Light })
    }

    @Test
    fun `nothing people read is under 12sp`() {
        styles.dropLast(1).forEach { assertTrue(it.fontSize.value >= 12f, "${it.fontSize}") }
        assertEquals(10.5.sp, styles.last().fontSize)
    }

    @Test
    fun `every line is taller than its text`() {
        styles.forEach { assertTrue(it.lineHeight.value > it.fontSize.value, "$it") }
    }

    @Test
    fun `shadows deepen with every level`() {
        listOf(HosannaLightElevation, HosannaDarkElevation).forEach { e ->
            HosannaLevel.entries.drop(1).zipWithNext().forEach { (lower, higher) ->
                val a = e.layers.getValue(lower).last()
                val b = e.layers.getValue(higher).last()
                assertTrue(b.offsetY > a.offsetY && b.blur > a.blur && b.alpha > a.alpha, "$lower → $higher")
            }
        }
    }

    @Test
    fun `the edge is a faint line of the opposite brightness`() {
        val light = HosannaLightElevation.edge
        val dark = HosannaDarkElevation.edge
        assertEquals(Color(0x14, 0x19, 0x23).copy(alpha = light.alpha), light)
        assertEquals(Color.White.copy(alpha = dark.alpha), dark)
        assertTrue(light.alpha in 0.11f..0.13f && dark.alpha in 0.10f..0.12f)
    }

    @Test
    fun `the output screen looks the same in both themes`() {
        assertEquals(HosannaLightColors.screen, HosannaDarkColors.screen)
        assertEquals(HosannaLightColors.onScreen, HosannaDarkColors.onScreen)
        assertTrue(HosannaLightColors.screen.luminance() < HosannaLightColors.onScreen.luminance())
    }
}
