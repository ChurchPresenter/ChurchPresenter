package org.churchpresenter.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ElevationTest {

    private val palettes = ThemeMode.entries.filter { it != ThemeMode.SYSTEM }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private val all: Map<ThemeMode, ElevationPalette> by lazy {
        val found = mutableMapOf<ThemeMode, ElevationPalette>()
        runComposeUiTest {
            setContent {
                palettes.forEach { mode ->
                    MaterialTheme(colorScheme = colorSchemeFor(mode)) { found[mode] = elevationPalette() }
                }
            }
        }
        found
    }

    private fun paletteFor(mode: ThemeMode): ElevationPalette = all.getValue(mode)

    @Test
    fun `every theme's palette is dark or light as its scheme is`() {
        palettes.forEach { mode ->
            val palette = paletteFor(mode)
            assertEquals(isDarkScheme(colorSchemeFor(mode)), palette.isDark, "$mode")
        }
    }

    @Test
    fun `the danger label clears 4_5 to 1 on its key in every theme`() {
        palettes.forEach { mode ->
            val danger = paletteFor(mode).danger
            val top = contrast(danger.ink, danger.top)
            val bottom = contrast(danger.ink, danger.bottom)
            assertTrue(top >= 4.5, "$mode top: $top")
            assertTrue(bottom >= 4.5, "$mode bottom: $bottom")
        }
    }

    @Test
    fun `dark keys carry a light hairline and light keys a dark one`() {
        val dark = paletteFor(ThemeMode.DARK)
        val light = paletteFor(ThemeMode.LIGHT)
        assertTrue(dark.keyEdge.alpha > 0f && dark.keyEdge.luminance() > 0.5f)
        assertTrue(light.keyEdge.alpha > 0f && light.keyEdge.luminance() < 0.5f)
        assertEquals(Color.White, light.key.top)
    }

    @Test
    fun `a dark key stands lighter than the lightest container it sits on`() {
        val scheme = colorSchemeFor(ThemeMode.DARK)
        val key = paletteFor(ThemeMode.DARK).key
        assertTrue(key.bottom.luminance() > scheme.surfaceContainerHighest.luminance())
    }

    @Test
    fun `a tinted fill keeps its color at the bottom and lifts the top`() {
        val palette = paletteFor(ThemeMode.DARK)
        val fill = palette.tinted(Color.Red, Color.White)
        assertEquals(Color.Red, fill.bottom)
        assertEquals(Color.White, fill.ink)
        assertTrue(fill.top.luminance() > fill.bottom.luminance())
        assertFalse(paletteFor(ThemeMode.LIGHT).tinted(Color.Red, Color.White).top == Color.Red)
    }

    @Test
    fun `a sunken well is darker along its top edge than below it`() = runComposeUiTest {
        setContent {
            MaterialTheme(colorScheme = colorSchemeFor(ThemeMode.DARK)) {
                Box(Modifier.size(40.dp).testTag("well").sunken(RoundedCornerShape(0.dp), elevationPalette()))
            }
        }
        val pixels = onNodeWithTag("well").captureToImage().toPixelMap()
        assertTrue(pixels[20, 2].luminance() < pixels[20, 30].luminance())
    }

    @Test
    fun `a well takes the fill and rim it is given`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(
                    Modifier.size(40.dp).testTag("well")
                        .sunken(RoundedCornerShape(0.dp), elevationPalette(), fill = Color.Blue, rim = Color.Red)
                )
            }
        }
        val pixels = onNodeWithTag("well").captureToImage().toPixelMap()
        val rim = pixels[20, 0]
        assertTrue(rim.red > 0.8f && rim.blue < 0.2f, "the rim is the one asked for: $rim")
        val body = pixels[20, 34]
        assertTrue(body.blue > 0.8f && body.red < 0.2f, "the well is the fill asked for: $body")
    }

    @Test
    fun `raised, pressed, hovered and disabled surfaces all draw`() = runComposeUiTest {
        setContent {
            MaterialTheme(colorScheme = colorSchemeFor(ThemeMode.LIGHT)) {
                val palette = elevationPalette()
                val shape = RoundedCornerShape(0.dp)
                Column {
                    Box(Modifier.size(30.dp).testTag("raised").raised(shape, palette.accent, palette))
                    Box(Modifier.size(30.dp).testTag("pressed").raised(shape, palette.accent, palette, pressed = true))
                    Box(Modifier.size(30.dp).testTag("hovered").raised(shape, palette.accent, palette, hovered = true))
                    Box(Modifier.size(30.dp).testTag("flat").flatDisabled(shape, palette))
                }
            }
        }
        val raised = onNodeWithTag("raised").captureToImage().toPixelMap()
        val pressed = onNodeWithTag("pressed").captureToImage().toPixelMap()
        assertTrue(
            pressed[15, 1].luminance() < raised[15, 1].luminance(),
            "a pressed key is shaded along its top where a raised one is lit",
        )
        onNodeWithTag("hovered").captureToImage()
        val flat = onNodeWithTag("flat").captureToImage().toPixelMap()
        assertEquals(0.4f, flat[15, 15].alpha, 0.02f, "a disabled control is drawn at 40%")
    }
}
