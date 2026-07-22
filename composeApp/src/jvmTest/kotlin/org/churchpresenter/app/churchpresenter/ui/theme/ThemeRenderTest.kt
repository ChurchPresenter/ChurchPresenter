package org.churchpresenter.app.churchpresenter.ui.theme

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The theme actually reaching the app, rather than the colour tables behind it.
 *
 * [ThemeTest] reads the nine schemes as data. This runs `ChurchPresenterTheme` in a composition and
 * asks what a component inside it would see: the right scheme for the chosen mode, the app's own
 * shapes and typography, and a scrollbar style that is legible against the surface it is drawn on.
 *
 * The mapping from mode to scheme is a ten-branch `when` written once and never seen again — two
 * branches pointing at the same scheme, or a dark scheme wired to a light mode, compiles perfectly
 * and is only visible when somebody selects that theme mid-service. The wrappers around it are just
 * as easy to get wrong quietly: `AppThemeWrapper` has to supply a `ThemeManager` (without one, a
 * `ThemeSwitcher` inside it edits a throwaway) and `LanguageProvider` has to actually provide, not
 * shadow, the language.
 *
 * SYSTEM is deliberately asserted loosely — it follows the host OS, so a test that pinned it to
 * light would pass on one machine and fail on the next.
 */
@OptIn(ExperimentalTestApi::class)
class ThemeRenderTest {

    /** One of the private schemes in `Theme.kt`, by name. */
    private fun scheme(name: String): ColorScheme =
        Class.forName("org.churchpresenter.app.churchpresenter.ui.theme.ThemeKt")
            .getDeclaredField(name)
            .apply { isAccessible = true }
            .get(null) as ColorScheme

    /** The scheme each mode has to resolve to. SYSTEM is excluded — it depends on the host. */
    private val expectedScheme = mapOf(
        ThemeMode.LIGHT to "LightColorScheme",
        ThemeMode.DARK to "DarkColorScheme",
        ThemeMode.WARM to "WarmColorScheme",
        ThemeMode.OCEAN to "OceanColorScheme",
        ThemeMode.ROSE to "RoseColorScheme",
        ThemeMode.MIDNIGHT to "MidnightColorScheme",
        ThemeMode.FOREST to "ForestColorScheme",
        ThemeMode.MOCHA to "MochaColorScheme",
        ThemeMode.STUDIO to "StudioColorScheme",
    )

    /** Renders [content] inside the app theme and hands back what it captured. */
    private fun <T> underTheme(mode: ThemeMode, capture: @Composable () -> T): T {
        var captured: T? = null
        runComposeUiTest {
            setContent { ChurchPresenterTheme(themeMode = mode) { captured = capture() } }
        }
        @Suppress("UNCHECKED_CAST")
        return captured as T
    }

    // ── Which scheme each mode gets ─────────────────────────────────────────────

    @Test
    fun `every mode resolves to its own scheme`() {
        expectedScheme.forEach { (mode, name) ->
            val applied = underTheme(mode) { MaterialTheme.colorScheme }

            assertSame(scheme(name), applied, "$mode is not painted with $name")
        }
    }

    @Test
    fun `no two modes are painted the same`() {
        val applied = expectedScheme.keys.associateWith { underTheme(it) { MaterialTheme.colorScheme.primary } }

        assertEquals(
            expectedScheme.size,
            applied.values.toSet().size,
            "two themes that look identical make one of them pointless: $applied",
        )
    }

    @Test
    fun `the system mode follows the host rather than inventing a scheme`() {
        val applied = underTheme(ThemeMode.SYSTEM) { MaterialTheme.colorScheme }

        assertTrue(
            applied === scheme("LightColorScheme") || applied === scheme("DarkColorScheme"),
            "System has to resolve to the plain light or dark scheme, not to an accent theme",
        )
    }

    @Test
    fun `a light mode really is light and a dark mode really is dark`() {
        // The cheap sanity check the ten-branch when cannot make for itself.
        fun luminance(mode: ThemeMode) = underTheme(mode) { MaterialTheme.colorScheme.background }.luminance()

        listOf(ThemeMode.LIGHT, ThemeMode.WARM, ThemeMode.OCEAN, ThemeMode.ROSE).forEach {
            assertTrue(luminance(it) > 0.5f, "$it is wired to a dark scheme")
        }
        listOf(ThemeMode.DARK, ThemeMode.MIDNIGHT, ThemeMode.FOREST, ThemeMode.MOCHA, ThemeMode.STUDIO).forEach {
            assertTrue(luminance(it) < 0.5f, "$it is wired to a light scheme")
        }
    }

    // ── What else the theme carries ─────────────────────────────────────────────

    @Test
    fun `the app's own corner radii are applied`() {
        // Material's defaults are rounder; these are the values the whole app is drawn with.
        val shapes = underTheme(ThemeMode.LIGHT) { MaterialTheme.shapes }

        assertEquals(RoundedCornerShape(4.dp), shapes.extraSmall)
        assertEquals(RoundedCornerShape(6.dp), shapes.small)
        assertEquals(RoundedCornerShape(8.dp), shapes.medium)
        assertEquals(RoundedCornerShape(10.dp), shapes.large)
        assertEquals(RoundedCornerShape(12.dp), shapes.extraLarge)
    }

    @Test
    fun `the app's own typography is applied`() {
        val typography = underTheme(ThemeMode.LIGHT) { MaterialTheme.typography }

        assertEquals(AppTypography, typography, "falling back to Material's defaults changes every size in the app")
    }

    @Test
    fun `the scrollbar is styled against the theme it sits in`() {
        val light = underTheme(ThemeMode.LIGHT) { LocalScrollbarStyle.current }
        val dark = underTheme(ThemeMode.DARK) { LocalScrollbarStyle.current }

        assertEquals(5.dp, light.thickness)
        assertEquals(16.dp, light.minimalHeight)
        assertNotEquals(
            light.unhoverColor,
            dark.unhoverColor,
            "a scrollbar coloured for one theme disappears into the surface of the other",
        )
    }

    @Test
    fun `a hovered scrollbar is more visible than an idle one`() {
        val style = underTheme(ThemeMode.DARK) { LocalScrollbarStyle.current }

        assertTrue(
            style.hoverColor.alpha > style.unhoverColor.alpha,
            "hovering has to make the bar clearer, not dimmer",
        )
    }

    @Test
    fun `content inside the theme is composed`() = runComposeUiTest {
        // The theme wraps the entire app; a content lambda that is not called is a blank window.
        setContent { ChurchPresenterTheme(themeMode = ThemeMode.OCEAN) { Text("the whole app") } }

        onNodeWithText("the whole app").assertExists()
    }

    // ── The wrappers around it ──────────────────────────────────────────────────

    @Test
    fun `the app wrapper applies the theme it is given`() {
        var applied: ColorScheme? = null
        runComposeUiTest {
            setContent { AppThemeWrapper(theme = ThemeMode.FOREST) { applied = MaterialTheme.colorScheme } }
        }

        assertSame(scheme("ForestColorScheme"), applied)
    }

    @Test
    fun `the app wrapper supplies a theme manager to what it wraps`() {
        // Without one, a ThemeSwitcher inside would edit a manager nothing else can see.
        var manager: ThemeManager? = null
        runComposeUiTest {
            setContent { AppThemeWrapper { manager = LocalThemeManager.current } }
        }

        assertTrue(manager != null)
    }

    @Test
    fun `the app wrapper defaults to following the system`() {
        var applied: ColorScheme? = null
        runComposeUiTest {
            setContent { AppThemeWrapper { applied = MaterialTheme.colorScheme } }
        }

        assertTrue(
            applied === scheme("LightColorScheme") || applied === scheme("DarkColorScheme"),
            "a wrapper with no theme named must not pick an accent theme of its own",
        )
    }

    @Test
    fun `the language provider hands its language down`() {
        var seen: Language? = null
        runComposeUiTest {
            setContent { LanguageProvider(Language.RUSSIAN) { seen = LocalLanguage.current } }
        }

        assertEquals(Language.RUSSIAN, seen)
    }

    @Test
    fun `the language provider can be nested for one part of the screen`() {
        // A presenter window can run in a different language from the operator's own UI.
        var outer: Language? = null
        var inner: Language? = null
        runComposeUiTest {
            setContent {
                LanguageProvider(Language.ENGLISH) {
                    outer = LocalLanguage.current
                    LanguageProvider(Language.RUSSIAN) { inner = LocalLanguage.current }
                }
            }
        }

        assertEquals(Language.ENGLISH, outer)
        assertEquals(Language.RUSSIAN, inner, "the inner scope must win inside itself")
    }

    @Test
    fun `english is what is read when nothing has provided a language`() {
        var seen: Language? = null
        runComposeUiTest {
            setContent { seen = LocalLanguage.current }
        }

        assertEquals(Language.ENGLISH, seen, "a missing provider must not leave the UI blank or throw")
    }
}
