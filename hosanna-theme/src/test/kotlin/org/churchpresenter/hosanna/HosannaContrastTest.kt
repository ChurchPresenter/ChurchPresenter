package org.churchpresenter.hosanna

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

class HosannaContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun pairs(c: ColorScheme, x: HosannaColors) = listOf(
        Triple("text", c.onSurface to c.surface, 4.5),
        Triple("secondary text", c.onSurfaceVariant to c.surface, 4.5),
        Triple("hint text", x.onSurfaceFaint to c.surface, 3.0),
        Triple("text in a field", c.onSurface to c.surfaceContainerHighest, 4.5),
        Triple("filled button", c.onPrimary to c.primary, 4.5),
        Triple("tonal button", c.onPrimaryContainer to c.primaryContainer, 4.5),
        Triple("primary on surface", c.primary to c.surface, 3.0),
        Triple("link", x.link to c.surface, 4.5),
        Triple("neutral button", c.onSecondaryContainer to c.secondaryContainer, 4.5),
        Triple("error text", c.error to c.surface, 4.5),
        Triple("denied tag", c.onErrorContainer to c.errorContainer, 4.5),
        Triple("approved tag", x.onSuccessContainer to x.successContainer, 4.5),
        Triple("warning tag", c.onTertiaryContainer to c.tertiaryContainer, 4.5),
        Triple("tooltip", c.inverseOnSurface to c.inverseSurface, 4.5),
        Triple("live badge", x.live to c.surface, 3.0),
    )

    private fun assertReadable(name: String, scheme: ColorScheme, extended: HosannaColors) {
        pairs(scheme, extended).forEach { (label, colors, need) ->
            val ratio = contrast(colors.first, colors.second)
            assertTrue(ratio >= need, "$name $label is $ratio:1, needs $need:1")
        }
    }

    @Test
    fun `light pairs are readable`() = assertReadable("light", HosannaLightColorScheme, HosannaLightColors)

    @Test
    fun `dark pairs are readable`() = assertReadable("dark", HosannaDarkColorScheme, HosannaDarkColors)

    @Test
    fun `dark surfaces get lighter as they rise`() {
        val c = HosannaDarkColorScheme
        assertTrue(c.surface.luminance() < c.surfaceContainer.luminance())
        assertTrue(c.surfaceContainer.luminance() < c.surfaceContainerHigh.luminance())
    }

    private fun roles(x: HosannaColors) = listOf(
        x.success, x.onSuccess, x.successContainer, x.onSuccessContainer, x.live, x.onLive,
        x.onSurfaceFaint, x.link, x.song, x.bible, x.presentation, x.pictures, x.media, x.lowerThird,
        x.announcement, x.verse, x.chorus, x.bridge, x.intro, x.voice, x.otherSection,
    )

    @Test
    fun `every extended role has its own dark value`() {
        roles(HosannaLightColors).zip(roles(HosannaDarkColors)).forEachIndexed { i, (light, dark) ->
            assertTrue(light != dark, "role $i is the same in light and dark")
        }
        assertTrue(HosannaLightColors.star == HosannaDarkColors.star)
    }

    @Test
    fun `content and section colors read on a panel`() {
        listOf(
            HosannaLightColors to HosannaLightColorScheme.surface,
            HosannaDarkColors to HosannaDarkColorScheme.surface,
        ).forEach { (x, panel) ->
            listOf(
                x.song, x.bible, x.presentation, x.pictures, x.media, x.lowerThird, x.announcement,
                x.verse, x.chorus, x.bridge, x.intro, x.voice, x.otherSection,
            ).forEach { assertTrue(contrast(it, panel) >= 3.0, "$it on $panel") }
        }
    }

    @Test
    fun `elevation is never tonal`() {
        assertTrue(HosannaLightColorScheme.surfaceTint == HosannaLightColorScheme.surface)
        assertTrue(HosannaDarkColorScheme.surfaceTint == HosannaDarkColorScheme.surface)
    }
}
