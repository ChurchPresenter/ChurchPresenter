package org.churchpresenter.lottiegen.ui

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Derived from the palette so the Material surfaces (dialogs, dropdown menus, the colour picker)
// sit in the same colours as the hand-built panel chrome. Without this the two disagree in exactly
// the way the light theme used to: hand-drawn panels in one palette, menus and dialogs in another.
private fun colorSchemeFor(palette: LottieGenPalette, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        primaryContainer = palette.logoChipBg,
        onPrimaryContainer = palette.logoIcon,
        secondary = palette.fillEnd,
        onSecondary = palette.onAccent,
        secondaryContainer = palette.logoChipBg,
        onSecondaryContainer = palette.logoIcon,
        tertiary = palette.tick,
        onTertiary = palette.onAccent,
        error = if (dark) Color(0xFFF2555A) else Color(0xFFB3261E),
        onError = Color.White,
        errorContainer = if (dark) Color(0xFF7A1F23) else Color(0xFFF9DEDC),
        onErrorContainer = if (dark) Color.White else Color(0xFF410E0B),
        background = palette.appBg,
        onBackground = palette.primaryText,
        surface = palette.cardBg,
        onSurface = palette.primaryText,
        surfaceVariant = palette.fieldBg,
        onSurfaceVariant = palette.labelText,
        surfaceContainer = palette.panelBg,
        surfaceContainerHigh = palette.fieldBg,
        surfaceContainerHighest = palette.subtleBg,
        outline = palette.borderHover,
        outlineVariant = palette.cardBorder,
        inverseSurface = palette.primaryText,
        inverseOnSurface = palette.appBg,
    )
}

private val LottieGenTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
)

/**
 * The tool's own theme, for the standalone window: Material surfaces, typography and the palette.
 *
 * [dark] picks the palette; standalone stays dark, which is what the generator has always looked
 * like. Embedded in a host app it is [ProvideLottieGenPalette] that runs instead — the host owns
 * the MaterialTheme there and only the palette has to be supplied.
 */
@Composable
fun LottieGenTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    MaterialTheme(
        colorScheme = colorSchemeFor(palette, dark),
        typography = LottieGenTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(6.dp),
            medium = RoundedCornerShape(8.dp),
            large = RoundedCornerShape(10.dp),
            extraLarge = RoundedCornerShape(12.dp)
        )
    ) {
        ProvideLottieGenPalette(palette, content)
    }
}

/**
 * Puts [palette] — and the scrollbar styling that goes with it — in scope for the panel chrome,
 * leaving the ambient MaterialTheme alone.
 *
 * This is the embedded path: ChurchPresenter has already applied its own theme around the
 * generator, so replacing it would be wrong, but the hand-drawn chrome still has to be told which
 * way it is being rendered.
 */
@Composable
fun ProvideLottieGenPalette(palette: LottieGenPalette, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLottieGenPalette provides palette,
        LocalScrollbarStyle provides ScrollbarStyle(
            minimalHeight = 16.dp,
            thickness = 6.dp,
            shape = RoundedCornerShape(4.dp),
            hoverDurationMillis = 150,
            unhoverColor = palette.scrollThumb,
            hoverColor = palette.scrollThumbHover
        )
    ) {
        content()
    }
}
