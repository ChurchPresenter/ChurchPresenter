package org.churchpresenter.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The UI text size and font reaching a composition, and reaching it exactly once.
 *
 * The size is applied as a font scale on the window's density. A window of the app's UI can pick it
 * up twice — through `ChurchPresenterTheme` and again through a theme nested inside it — and 1.3x
 * applied twice is 1.69x, which no one chose. Output windows must not pick it up at all; they never
 * go through either call, which is a property of their call sites rather than of this code.
 */
@OptIn(ExperimentalTestApi::class)
class UiFontScaleTest {

    private val base = Density(density = 2f, fontScale = 1f)

    @Test
    fun `the scale multiplies the window's own font scale`() = runComposeUiTest {
        var seen = 0f
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1.1f)) {
                ProvideUiFontScale(1.3f) { seen = LocalDensity.current.fontScale }
            }
        }
        waitForIdle()
        assertEquals(1.1f * 1.3f, seen, 0.001f)
    }

    @Test
    fun `a second call in the same window does not scale again`() = runComposeUiTest {
        var seen = 0f
        setContent {
            CompositionLocalProvider(LocalDensity provides base) {
                ProvideUiFontScale(1.3f) {
                    ProvideUiFontScale(1.3f) { seen = LocalDensity.current.fontScale }
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
                ProvideUiFontScale(1.3f) {
                    CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                        ProvideUiFontScale(1.15f) { seen = LocalDensity.current.fontScale }
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
                ProvideUiFontScale(1f) { same = LocalDensity.current === base }
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
                ProvideUiFontScale { seen = LocalDensity.current.fontScale }
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
}
