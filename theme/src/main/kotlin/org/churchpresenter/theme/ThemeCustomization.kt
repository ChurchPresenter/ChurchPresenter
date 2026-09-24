package org.churchpresenter.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import kotlin.math.abs

/** The accent [ThemeMode.CUSTOM] paints with until the user has picked one of their own. */
val DefaultCustomAccent = Color(0xFF3F7FBF)

/** The UI text sizes offered, as multipliers of the platform's own font scale. */
val UI_FONT_SCALES = listOf(0.9f, 1f, 1.15f, 1.3f)

/**
 * What the user has chosen on top of a theme: the accent, base and optional per-role colours
 * [ThemeMode.CUSTOM] is generated from, and the font family and text size every theme is drawn with.
 *
 * Every colour but [accent] is optional; null means "derive it", which is what the theme does for
 * all of them when only an accent is picked.
 *
 * Supplied by the app through [LocalThemeCustomization] rather than read here — this module reads no
 * settings, so the colours arrive as values and the palette stays a pure function of them.
 */
@Immutable
data class ThemeCustomization(
    val accent: Color = DefaultCustomAccent,
    val dark: Boolean = true,
    /** Its hue and saturation tint every surface; the base still decides how light each one is. */
    val background: Color? = null,
    val secondary: Color? = null,
    /** Body text. Moved lighter or darker, keeping its hue, until it reads on every surface. */
    val text: Color? = null,
    val success: Color? = null,
    val warning: Color? = null,
    val error: Color? = null,
    /** The fill of a selected row. */
    val selection: Color? = null,
    val fontFamily: FontFamily? = null,
    val fontScale: Float = 1f,
)

val LocalThemeCustomization = compositionLocalOf { ThemeCustomization() }

/**
 * The lightness of every role on one base. Only hue and saturation come from the user's colours, so
 * these alone decide that each layer clears the one beneath it by the margins ThemeSurfaceRampTest
 * holds every theme to.
 */
private interface Tones {
    val background: Float
    val surface: Float
    val container: Float
    val containerHigh: Float
    val page: Float
    val outlineVariant: Float
    val outline: Float
    val onSurfaceVariant: Float
    val onSurface: Float
    val accent: Float
    val accentContainer: Float
    val onContainer: Float
}

private object DarkTones : Tones {
    override val background = 0.07f
    override val surface = 0.10f
    override val container = 0.15f
    override val containerHigh = 0.21f
    override val page = 0.28f
    override val outlineVariant = 0.32f
    override val outline = 0.58f
    override val onSurfaceVariant = 0.78f
    override val onSurface = 0.90f
    override val accent = 0.70f
    override val accentContainer = 0.24f
    override val onContainer = 0.90f
}

private object LightTones : Tones {
    override val background = 0.965f
    override val surface = 0.94f
    override val container = 0.98f
    override val containerHigh = 0.90f
    override val page = 0.83f
    override val outlineVariant = 0.72f
    override val outline = 0.55f
    override val onSurfaceVariant = 0.30f
    override val onSurface = 0.10f
    override val accent = 0.38f
    override val accentContainer = 0.87f
    override val onContainer = 0.12f
}

private const val LIGHT_LABEL_L = 0.97f
private const val DARK_LABEL_L = 0.14f
private const val SURFACE_TINT_SATURATION = 0.12f
private const val MAX_BACKGROUND_SATURATION = 0.45f
private const val TEXT_TINT_SATURATION = 0.06f
private const val MIN_ACCENT_SATURATION = 0.35f
private const val MAX_ACCENT_SATURATION = 0.80f
private const val CONTAINER_SATURATION_FACTOR = 0.6f
private const val SECONDARY_HUE_SHIFT = 35f
private const val TERTIARY_HUE_SHIFT = -60f
private const val SECONDARY_SATURATION_FACTOR = 0.6f
private const val MIN_TEXT_CONTRAST = 4.5
private const val LUMINANCE_OFFSET = 0.05
private const val LIGHTNESS_STEP = 0.02f
private const val FULL_CIRCLE = 360f
private const val HUE_SEXTANT = 60f
private const val HUE_GREEN_OFFSET = 2f
private const val HUE_BLUE_OFFSET = 4f
private const val SEXTANTS = 6f

/**
 * The full palette generated from [custom] — its accent, base and whichever per-role colours are set.
 *
 * Only the colours' hue and saturation are kept for the accent, secondary and surfaces; every
 * lightness is fixed per role, so a very pale or very dark pick still yields a primary that reads on
 * its base and surfaces that step apart. Colours the user picks for a *fill* — selection and error —
 * are used exactly, with black or white text chosen to read on them. Error falls back to the Light or
 * Dark preset's, and the tooltip pair (`inverseSurface`) always comes from the preset.
 */
fun customColorScheme(custom: ThemeCustomization): ColorScheme {
    val tones = if (custom.dark) DarkTones else LightTones
    val preset = colorSchemeFor(if (custom.dark) ThemeMode.DARK else ThemeMode.LIGHT)
    val accent = Hsl.of(custom.accent)
    val hue = accent.hue
    val saturation = accent.saturation.coerceIn(MIN_ACCENT_SATURATION, MAX_ACCENT_SATURATION)
    val containerSaturation = saturation * CONTAINER_SATURATION_FACTOR

    val (tintHue, tintSaturation) = surfaceTint(custom.background, hue)
    fun tint(lightness: Float) = Color.hsl(tintHue, tintSaturation, lightness)
    val surfaces = listOf(tones.background, tones.surface, tones.container, tones.containerHigh, tones.page)
        .map(::tint)
    val onSurface = custom.text?.let { readableOn(it, surfaces, lighten = custom.dark) }
        ?: Color.hsl(hue, TEXT_TINT_SATURATION, tones.onSurface)
    val textHue = custom.text?.let(Hsl::of)?.hue ?: hue
    val onSurfaceVariant = Color.hsl(textHue, TEXT_TINT_SATURATION, tones.onSurfaceVariant)

    fun role(roleHue: Float, roleSaturation: Float): RoleColors {
        val main = Color.hsl(roleHue, roleSaturation, tones.accent)
        return RoleColors(
            main = main,
            onMain = tintedOn(main, roleHue, roleSaturation),
            container = Color.hsl(roleHue, roleSaturation, tones.accentContainer),
            onContainer = Color.hsl(roleHue, roleSaturation, tones.onContainer),
        )
    }
    val primary = role(hue, saturation)
    val secondary = custom.secondary?.let(Hsl::of)
        ?.let { role(it.hue, it.saturation.coerceIn(MIN_ACCENT_SATURATION, MAX_ACCENT_SATURATION)) }
        ?: role(wrapHue(hue + SECONDARY_HUE_SHIFT), saturation * SECONDARY_SATURATION_FACTOR)
    val tertiary = role(wrapHue(hue + TERTIARY_HUE_SHIFT), saturation * SECONDARY_SATURATION_FACTOR)
    val primaryContainer = custom.selection ?: Color.hsl(hue, containerSaturation, tones.accentContainer)
    val onPrimaryContainer = custom.selection?.let(::onColorFor)
        ?: Color.hsl(hue, containerSaturation, tones.onContainer)
    val error = custom.error?.let { statusColors(it, custom.dark) }

    val base = if (custom.dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary.main,
        onPrimary = primary.onMain,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary.main,
        onSecondary = secondary.onMain,
        secondaryContainer = secondary.container,
        onSecondaryContainer = secondary.onContainer,
        tertiary = tertiary.main,
        onTertiary = tertiary.onMain,
        tertiaryContainer = tertiary.container,
        onTertiaryContainer = tertiary.onContainer,
        error = error?.main ?: preset.error,
        onError = error?.onMain ?: preset.onError,
        errorContainer = error?.container ?: preset.errorContainer,
        onErrorContainer = error?.onContainer ?: preset.onErrorContainer,
        background = tint(tones.background),
        onBackground = onSurface,
        surface = tint(tones.surface),
        onSurface = onSurface,
        surfaceVariant = tint(tones.page),
        onSurfaceVariant = onSurfaceVariant,
        surfaceContainer = tint(tones.container),
        surfaceContainerHigh = tint(tones.containerHigh),
        outline = tint(tones.outline),
        outlineVariant = tint(tones.outlineVariant),
        inverseSurface = preset.inverseSurface,
        inverseOnSurface = preset.inverseOnSurface,
    )
}

/**
 * [base] with the Custom theme's status colours swapped in — success and warning, which Material 3
 * has no role for. The chosen colour is used exactly; its text and container are derived from it.
 */
internal fun customSemanticColors(base: SemanticColors, custom: ThemeCustomization): SemanticColors {
    var colors = base
    custom.success?.let { picked ->
        val status = statusColors(picked, custom.dark)
        colors = colors.copy(
            success = status.main,
            onSuccess = status.onMain,
            successContainer = status.container,
            onSuccessContainer = status.onContainer,
        )
    }
    custom.warning?.let { picked ->
        val status = statusColors(picked, custom.dark)
        colors = colors.copy(
            warning = status.main,
            onWarning = status.onMain,
            warningContainer = status.container,
            onWarningContainer = status.onContainer,
        )
    }
    return colors
}

/** The hue and saturation every surface is tinted with: the picked background's, or a whisper of the accent. */
private fun surfaceTint(background: Color?, accentHue: Float): Pair<Float, Float> =
    background?.let(Hsl::of)?.let { it.hue to it.saturation.coerceAtMost(MAX_BACKGROUND_SATURATION) }
        ?: (accentHue to SURFACE_TINT_SATURATION)

private class RoleColors(val main: Color, val onMain: Color, val container: Color, val onContainer: Color)

/** A picked status colour, its legible text, and a quiet container of the same hue with its text. */
private fun statusColors(picked: Color, dark: Boolean): RoleColors {
    val tones = if (dark) DarkTones else LightTones
    val (hue, saturation) = Hsl.of(picked)
    val containerSaturation = saturation * CONTAINER_SATURATION_FACTOR
    return RoleColors(
        main = picked,
        onMain = onColorFor(picked),
        container = Color.hsl(hue, containerSaturation, tones.accentContainer),
        onContainer = Color.hsl(hue, containerSaturation, tones.onContainer),
    )
}

/**
 * A near-white or near-black of [hue], whichever reads better on [fill]. A fixed light label is not
 * enough: on a light base a yellow or cyan button is bright even at the accent's fixed lightness.
 */
private fun tintedOn(fill: Color, hue: Float, saturation: Float): Color {
    val light = Color.hsl(hue, saturation, LIGHT_LABEL_L)
    val dark = Color.hsl(hue, saturation, DARK_LABEL_L)
    return if (contrast(light, fill) >= contrast(dark, fill)) light else dark
}

/** Black or white, whichever reads better on [fill]. One of the two always clears 4.5:1. */
private fun onColorFor(fill: Color): Color =
    if (contrast(Color.White, fill) >= contrast(Color.Black, fill)) Color.White else Color.Black

/**
 * [picked], moved lighter (or darker) in steps while keeping its hue, until it clears body-text
 * contrast against every one of [backgrounds].
 */
private fun readableOn(picked: Color, backgrounds: List<Color>, lighten: Boolean): Color {
    val (hue, saturation, start) = Hsl.of(picked)
    var lightness = start
    var candidate = picked
    while (backgrounds.minOf { contrast(candidate, it) } < MIN_TEXT_CONTRAST && lightness in 0f..1f) {
        lightness += if (lighten) LIGHTNESS_STEP else -LIGHTNESS_STEP
        candidate = Color.hsl(hue, saturation, lightness.coerceIn(0f, 1f))
    }
    return candidate
}

private fun contrast(a: Color, b: Color): Double {
    val la = a.luminance() + LUMINANCE_OFFSET
    val lb = b.luminance() + LUMINANCE_OFFSET
    return maxOf(la, lb).toDouble() / minOf(la, lb).toDouble()
}

private fun wrapHue(hue: Float): Float = ((hue % FULL_CIRCLE) + FULL_CIRCLE) % FULL_CIRCLE

/** A colour's HSL hue (0..360), saturation and lightness (0..1). */
internal data class Hsl(val hue: Float, val saturation: Float, val lightness: Float) {
    companion object {
        fun of(color: Color): Hsl {
            val r = color.red
            val g = color.green
            val b = color.blue
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val delta = max - min
            val lightness = (max + min) / 2f
            if (delta == 0f) return Hsl(0f, 0f, lightness)
            val saturation = delta / (1f - abs(2f * lightness - 1f))
            val sextant = when (max) {
                r -> ((g - b) / delta) % SEXTANTS
                g -> (b - r) / delta + HUE_GREEN_OFFSET
                else -> (r - g) / delta + HUE_BLUE_OFFSET
            }
            return Hsl(wrapHue(sextant * HUE_SEXTANT), saturation.coerceIn(0f, 1f), lightness)
        }
    }
}

/** The status colours the Custom theme paints with for [custom] — for showing what "automatic" gives. */
fun customSemanticColorsFor(custom: ThemeCustomization): SemanticColors =
    customSemanticColors(semanticColorsFor(customColorScheme(custom)), custom)
