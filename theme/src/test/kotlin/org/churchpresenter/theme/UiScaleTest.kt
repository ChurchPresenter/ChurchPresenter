package org.churchpresenter.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The interface size, UI text size and font reaching a composition, and reaching it exactly once.
 *
 * The interface size multiplies the window's density and the text size its font scale. A window of
 * the app's UI can pick them up twice — through `ChurchPresenterTheme` and again through a theme
 * nested inside it — and 1.3x applied twice is 1.69x, which no one chose. Output windows must not pick
 * them up at all; they never go through either call, which is a property of their call sites rather
 * than of this code.
 */
@OptIn(ExperimentalTestApi::class)
class UiScaleTest {

    private val base = Density(density = 2f, fontScale = 1f)

    @Test
    fun `the scale multiplies the window's own font scale`() = runComposeUiTest {
        var seen = 0f
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1.1f)) {
                ProvideUiScale(1.3f) { seen = LocalDensity.current.fontScale }
            }
        }
        waitForIdle()
        assertEquals(1.1f * 1.3f, seen, 0.001f)
    }

    @Test
    fun `the interface size multiplies the window's own density, and text grows on top`() = runComposeUiTest {
        var seen: Density? = null
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1.1f)) {
                ProvideUiScale(fontScale = 1.15f, uiScale = 1.5f) { seen = LocalDensity.current }
            }
        }
        waitForIdle()
        assertEquals(2f * 1.5f, seen!!.density, 0.001f)
        assertEquals(1.1f * 1.15f, seen!!.fontScale, 0.001f)
    }

    @Test
    fun `the interface size alone leaves the font scale as it was`() = runComposeUiTest {
        var seen: Density? = null
        setContent {
            CompositionLocalProvider(LocalDensity provides base) {
                ProvideUiScale(fontScale = 1f, uiScale = 2f) { seen = LocalDensity.current }
            }
        }
        waitForIdle()
        assertEquals(4f, seen!!.density, 0.001f)
        assertEquals(1f, seen!!.fontScale, 0.001f)
    }

    @Test
    fun `a theme nested inside another grows the interface once`() = runComposeUiTest {
        var seen = 0f
        setContent {
            CompositionLocalProvider(
                LocalDensity provides base,
                LocalThemeCustomization provides ThemeCustomization(uiScale = 1.25f),
            ) {
                ChurchPresenterTheme(ThemeMode.DARK) {
                    ChurchPresenterTheme(ThemeMode.LIGHT) { seen = LocalDensity.current.density }
                }
            }
        }
        waitForIdle()
        assertEquals(2.5f, seen, 0.001f)
    }

    @Test
    fun `a second call in the same window does not scale again`() = runComposeUiTest {
        var seen = 0f
        setContent {
            CompositionLocalProvider(LocalDensity provides base) {
                ProvideUiScale(1.3f) {
                    ProvideUiScale(1.3f) { seen = LocalDensity.current.fontScale }
                }
            }
        }
        waitForIdle()
        assertEquals(1.3f, seen, 0.001f)
    }

    @Test
    fun `a fresh density - a new window - is scaled again`() = runComposeUiTest {
        var seen = 0f
        setContent {
            CompositionLocalProvider(LocalDensity provides base) {
                ProvideUiScale(1.3f) {
                    CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                        ProvideUiScale(1.15f) { seen = LocalDensity.current.fontScale }
                    }
                }
            }
        }
        waitForIdle()
        assertEquals(1.15f, seen, 0.001f)
    }

    @Test
    fun `the default size leaves the density alone`() = runComposeUiTest {
        var same = false
        setContent {
            CompositionLocalProvider(LocalDensity provides base) {
                ProvideUiScale(1f) { same = LocalDensity.current === base }
            }
        }
        waitForIdle()
        assertEquals(true, same)
    }

    @Test
    fun `the default scale is the one the app supplied`() = runComposeUiTest {
        var seen = 0f
        setContent {
            CompositionLocalProvider(
                LocalDensity provides base,
                LocalThemeCustomization provides ThemeCustomization(fontScale = 0.9f),
            ) {
                ProvideUiScale { seen = LocalDensity.current.fontScale }
            }
        }
        waitForIdle()
        assertEquals(0.9f, seen, 0.001f)
    }

    @Test
    fun `a theme nested inside another scales the text once`() = runComposeUiTest {
        var seen = 0f
        setContent {
            CompositionLocalProvider(
                LocalDensity provides base,
                LocalThemeCustomization provides ThemeCustomization(fontScale = 1.3f),
            ) {
                ChurchPresenterTheme(ThemeMode.DARK) {
                    ChurchPresenterTheme(ThemeMode.LIGHT) { seen = LocalDensity.current.fontScale }
                }
            }
        }
        waitForIdle()
        assertEquals(1.3f, seen, 0.001f)
    }

    @Test
    fun `the theme applies the UI font to its typography`() = runComposeUiTest {
        var family: FontFamily? = null
        setContent {
            ChurchPresenterTheme(ThemeMode.DARK, ThemeCustomization(fontFamily = FontFamily.Serif)) {
                family = MaterialTheme.typography.bodyMedium.fontFamily
            }
        }
        waitForIdle()
        assertEquals(FontFamily.Serif, family)
    }

    @Test
    fun `picked status colours reach the composition only under the custom theme`() = runComposeUiTest {
        val picked = Color.hsl(280f, 0.7f, 0.5f)
        val custom = ThemeCustomization(success = picked, warning = picked)
        var underCustom: Color? = null
        var underPreset: Color? = null
        setContent {
            ChurchPresenterTheme(ThemeMode.CUSTOM, custom) { underCustom = MaterialTheme.semantic.success }
            ChurchPresenterTheme(ThemeMode.DARK, custom) { underPreset = MaterialTheme.semantic.success }
        }
        waitForIdle()
        assertEquals(picked, underCustom)
        assertEquals(semanticColorsFor(colorSchemeFor(ThemeMode.DARK)).success, underPreset)
    }

    @Test
    fun `the largest size leaves the layout its room on the smaller side`() {
        assertEquals(2f, maxUiScale(2560f, 1600f, 1280f, 720f), 0.001f, "2560 / 1280 limits it")
        assertEquals(1.5f, maxUiScale(1920f, 1080f, 1280f, 720f), 0.001f)
        assertEquals(1.25f, maxUiScale(1600f, 900f, 1280f, 720f), 0.001f)
    }

    @Test
    fun `a screen smaller than the layout still offers the default size`() {
        assertEquals(1366f / 1280f, maxUiScale(1366f, 768f, 1280f, 720f), 0.001f)
        assertEquals(1f, maxUiScale(1024f, 600f, 1280f, 720f), 0.001f)
    }

    @Test
    fun `a screen that cannot be measured takes every step`() {
        assertEquals(UI_SCALES.last(), maxUiScale(0f, 0f, 1280f, 720f))
    }

    @Test
    fun `the steps stop at the largest the screen allows`() {
        assertEquals(listOf(0.8f, 0.9f, 1f, 1.1f, 1.25f, 1.5f), uiScaleSteps(1.5f))
        assertEquals(listOf(0.8f, 0.9f, 1f), uiScaleSteps(1.07f))
        assertEquals(UI_SCALES, uiScaleSteps(2f))
    }

    @Test
    fun `zooming moves one step at a time and stops at either end`() {
        assertEquals(1.1f, steppedUiScale(1f, zoomIn = true, maxScale = 2f))
        assertEquals(0.9f, steppedUiScale(1f, zoomIn = false, maxScale = 2f))
        assertEquals(2f, steppedUiScale(2f, zoomIn = true, maxScale = 2f), "the top stays put")
        assertEquals(0.8f, steppedUiScale(0.8f, zoomIn = false, maxScale = 2f), "the bottom stays put")
    }

    @Test
    fun `a size between steps, or above what the screen allows, moves to a neighbouring step`() {
        assertEquals(1.25f, steppedUiScale(1.2f, zoomIn = true, maxScale = 2f))
        assertEquals(1.1f, steppedUiScale(1.2f, zoomIn = false, maxScale = 2f))
        assertEquals(1.25f, steppedUiScale(2f, zoomIn = false, maxScale = 1.25f), "saved on a bigger screen")
        assertEquals(1.25f, steppedUiScale(2f, zoomIn = true, maxScale = 1.25f))
    }
}
