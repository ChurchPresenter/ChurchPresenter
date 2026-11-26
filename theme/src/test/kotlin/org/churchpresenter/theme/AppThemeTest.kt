@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppThemeTest {

    private fun <T> underTheme(mode: ThemeMode, capture: @Composable () -> T): T {
        var captured: T? = null
        runComposeUiTest {
            setContent { ChurchPresenterTheme(themeMode = mode) { captured = capture() } }
        }
        @Suppress("UNCHECKED_CAST")
        return captured as T
    }

    @Test
    fun `the components' colors are the active theme's Material roles`() {
        listOf(ThemeMode.LIGHT, ThemeMode.PLUM).forEach { mode ->
            val s = colorSchemeFor(mode)
            val c = themeColorsFor(mode)
            assertEquals(
                listOf(
                    s.primary, s.onPrimary, s.primaryContainer, s.onPrimaryContainer, s.secondary, s.onSecondary,
                    s.secondaryContainer, s.onSecondaryContainer, s.tertiary, s.onTertiary, s.tertiaryContainer,
                    s.onTertiaryContainer, s.error, s.onError, s.errorContainer, s.onErrorContainer, s.background,
                    s.onBackground, s.surface, s.onSurface, s.surfaceVariant, s.onSurfaceVariant,
                    s.surfaceContainerLowest, s.surfaceContainerLow, s.surfaceContainer, s.surfaceContainerHigh,
                    s.surfaceContainerHighest, s.outline, s.outlineVariant, s.inverseSurface, s.inverseOnSurface,
                    s.scrim,
                ),
                listOf(
                    c.primary, c.onPrimary, c.primaryContainer, c.onPrimaryContainer, c.secondary, c.onSecondary,
                    c.secondaryContainer, c.onSecondaryContainer, c.tertiary, c.onTertiary, c.tertiaryContainer,
                    c.onTertiaryContainer, c.error, c.onError, c.errorContainer, c.onErrorContainer, c.background,
                    c.onBackground, c.surface, c.onSurface, c.surfaceVariant, c.onSurfaceVariant,
                    c.surfaceContainerLowest, c.surfaceContainerLow, c.surfaceContainer, c.surfaceContainerHigh,
                    c.surfaceContainerHighest, c.outline, c.outlineVariant, c.inverseSurface, c.inverseOnSurface,
                    c.scrim,
                ),
                "$mode",
            )
        }
    }

    @Test
    fun `the components' named colors are the active theme's semantic roles`() {
        ThemeMode.entries.filter { it != ThemeMode.SYSTEM && it != ThemeMode.CUSTOM }.forEach { mode ->
            val s = semanticColorsFor(mode, colorSchemeFor(mode))
            val c = themeColorsFor(mode)
            assertEquals(
                listOf(
                    s.success, s.onSuccess, s.successContainer, s.onSuccessContainer, s.live, s.onLive, s.favorite,
                    s.onSurfaceFaint, s.contentSongs, s.contentBible, s.contentPresentation, s.contentPictures,
                    s.contentMedia, s.contentLowerThird, s.contentAnnouncement, s.chordVerse, s.chordChorus,
                    s.chordBridge, s.chordIntro, s.chordVoice, s.chordOther,
                ),
                listOf(
                    c.success, c.onSuccess, c.successContainer, c.onSuccessContainer, c.live, c.onLive, c.star,
                    c.onSurfaceFaint, c.song, c.bible, c.presentation, c.pictures, c.media, c.lowerThird,
                    c.announcement, c.verse, c.chorus, c.bridge, c.intro, c.voice, c.otherSection,
                ),
                "$mode",
            )
        }
    }

    @Test
    fun `the output's black and white do not follow the theme`() {
        val light = themeColorsFor(ThemeMode.LIGHT)
        val dark = themeColorsFor(ThemeMode.DARK)
        assertEquals(light.screen, dark.screen)
        assertEquals(Color.White, light.onScreen)
        assertTrue(light.screen.red < 0.1f)
    }

    @Test
    fun `system and custom take the light or dark theme's set by brightness`() {
        val dark = colorSchemeFor(ThemeMode.DARK)
        val light = colorSchemeFor(ThemeMode.LIGHT)
        assertEquals(semanticColorsFor(ThemeMode.DARK, dark), semanticColorsFor(ThemeMode.SYSTEM, dark))
        assertEquals(semanticColorsFor(ThemeMode.LIGHT, light), semanticColorsFor(ThemeMode.CUSTOM, light))
    }

    @Test
    fun `under a theme, AppTheme reads its colors, font, shadows and shapes`() {
        val read = underTheme(ThemeMode.DARK) {
            listOf(
                AppTheme.colors.primary == MaterialTheme.colorScheme.primary,
                AppTheme.typography.body.fontFamily == MaterialTheme.typography.bodyMedium.fontFamily,
                AppTheme.elevation === DarkShadows,
                AppTheme.shapes === DefaultAppShapes,
            )
        }
        assertEquals(listOf(true, true, true, true), read)
        assertSame(LightShadows, underTheme(ThemeMode.LIGHT) { AppTheme.elevation })
        assertEquals(3.dp, AppTheme.focusRingWidth)
    }

    @Test
    fun `the type scale is set in the font it is given`() {
        val scale = typeScale(FontFamily.Monospace)
        listOf(
            scale.display, scale.headline, scale.title, scale.subtitle, scale.body, scale.bodyStrong, scale.label,
            scale.caption, scale.captionStrong, scale.overline,
        ).forEach { assertEquals(FontFamily.Monospace, it.fontFamily) }
        assertEquals("tnum", scale.body.tabular().fontFeatureSettings)
        assertEquals("HELLO", "hello".asOverline())
    }

    @Test
    fun `radii nest and the shapes use them`() {
        assertEquals(AppRadius.M + 4.dp, AppRadius.nested(AppRadius.M, 4.dp))
        assertEquals(6, listOf(
            DefaultAppShapes.xs, DefaultAppShapes.s, DefaultAppShapes.m,
            DefaultAppShapes.l, DefaultAppShapes.xl, DefaultAppShapes.full,
        ).toSet().size)
        assertTrue(StateAlpha.HOVER < StateAlpha.DISABLED_CONTENT)
    }

    @Test
    fun `every elevation draws, in both themes`() {
        runComposeUiTest {
            setContent {
                ElevationLevel.entries.forEach { level ->
                    listOf(LightShadows, DarkShadows).forEach { shadows ->
                        Box(Modifier.size(20.dp).elevationShadow(level, RectangleShape, shadows))
                    }
                }
            }
            waitForIdle()
        }
        assertEquals(ElevationLevel.entries.toSet(), LightShadows.layers.keys)
        assertEquals(ElevationLevel.entries.toSet(), DarkShadows.layers.keys)
    }
}
