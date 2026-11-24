package org.churchpresenter.hosanna

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HosannaThemeTest {

    private class Seen {
        var scheme: ColorScheme? = null
        var colors: HosannaColors? = null
        var shapes: HosannaShapes? = null
        var materialShapes: Shapes? = null
        var elevation: HosannaElevation? = null
        var typography: HosannaTypography? = null
    }

    private fun under(dark: Boolean): Seen {
        val seen = Seen()
        runComposeUiTest {
            setContent {
                HosannaTheme(darkTheme = dark) {
                    seen.scheme = MaterialTheme.colorScheme
                    seen.materialShapes = MaterialTheme.shapes
                    seen.colors = HosannaTheme.colors
                    seen.shapes = HosannaTheme.shapes
                    seen.elevation = HosannaTheme.elevation
                    seen.typography = HosannaTheme.typography
                    Column {
                        HosannaLevel.entries.forEach { level ->
                            Box(
                                Modifier.size(24.dp)
                                    .hosannaElevation(level, HosannaTheme.shapes.l, HosannaTheme.elevation),
                            )
                        }
                    }
                }
            }
            waitForIdle()
        }
        return seen
    }

    @Test
    fun `light provides the light tokens`() {
        val seen = under(dark = false)
        assertEquals(HosannaLightColorScheme.primary, seen.scheme?.primary)
        assertEquals(HosannaLightColors, seen.colors)
        assertEquals(HosannaLightElevation, seen.elevation)
        assertEquals(HosannaDefaultShapes, seen.shapes)
        assertEquals(HosannaDefaultShapes.toMaterial(), seen.materialShapes)
    }

    @Test
    fun `dark provides the dark tokens`() {
        val seen = under(dark = true)
        assertEquals(HosannaDarkColorScheme.primary, seen.scheme?.primary)
        assertEquals(HosannaDarkColors, seen.colors)
        assertEquals(HosannaDarkElevation, seen.elevation)
    }

    @Test
    fun `text is set in the bundled Inter`() {
        val family = under(dark = false).typography?.body?.fontFamily
        assertNotEquals(FontFamily.Default, family)
        assertNotEquals(null, family)
    }

    @Test
    fun `outside the theme the tokens fall back to light`() {
        val seen = Seen()
        runComposeUiTest {
            setContent {
                seen.colors = LocalHosannaColors.current
                seen.shapes = LocalHosannaShapes.current
                seen.elevation = LocalHosannaElevation.current
                seen.typography = LocalHosannaTypography.current
            }
        }
        assertEquals(HosannaLightColors, seen.colors)
        assertEquals(HosannaDefaultShapes, seen.shapes)
        assertEquals(HosannaLightElevation, seen.elevation)
        assertEquals(FontFamily.Default, seen.typography?.body?.fontFamily)
    }

    @Test
    fun `switching to dark while shown updates every token`() {
        var dark by mutableStateOf(false)
        var tick by mutableStateOf(0)
        val seen = Seen()
        runComposeUiTest {
            setContent {
                tick.let { HosannaTheme(darkTheme = dark) { seen.colors = HosannaTheme.colors } }
            }
            waitForIdle()
            assertEquals(HosannaLightColors, seen.colors)
            tick++
            waitForIdle()
            assertEquals(HosannaLightColors, seen.colors)
            dark = true
            waitForIdle()
            assertEquals(HosannaDarkColors, seen.colors)
        }
    }

    @Test
    fun `Inter sets text differently from the system font`() {
        var inter = 0
        var system = 0
        runComposeUiTest {
            setContent {
                HosannaTheme(darkTheme = false) {
                    val measurer = rememberTextMeasurer()
                    val sample = "Amazing Grace 0123456789"
                    val body = HosannaTheme.typography.body
                    inter = measurer.measure(sample, body).size.width
                    system = measurer.measure(sample, body.copy(fontFamily = FontFamily.Monospace)).size.width
                }
            }
            waitForIdle()
        }
        assertTrue(inter > 0)
        assertNotEquals(system, inter)
    }

    @Test
    fun `level 2 draws an edge and level 0 does not`() {
        val size = 40
        fun edgePixel(level: HosannaLevel): Color {
            var pixel = Color.Unspecified
            runComposeUiTest {
                setContent {
                    HosannaTheme(darkTheme = false) {
                        Box(
                            Modifier.size(size.dp).background(Color.White).testTag("box")
                                .hosannaElevation(level, RectangleShape, HosannaTheme.elevation),
                        )
                    }
                }
                val map = onNodeWithTag("box").captureToImage().toPixelMap()
                pixel = map[map.width / 2, 0]
            }
            return pixel
        }
        assertEquals(Color.White, edgePixel(HosannaLevel.Level0))
        assertNotEquals(Color.White, edgePixel(HosannaLevel.Level2))
    }

    @Test
    fun `the default theme follows the system`() {
        var colors: HosannaColors? = null
        runComposeUiTest {
            setContent { HosannaTheme { colors = HosannaTheme.colors } }
        }
        assertNotEquals(null, colors)
    }
}
