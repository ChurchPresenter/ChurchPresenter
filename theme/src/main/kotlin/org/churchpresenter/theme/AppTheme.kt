package org.churchpresenter.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The active theme as a component draws with it -- its colors under the components' names, its font
 * in the components' type scale ([TypeScale]), the shadows that suit a light or a dark theme, and the
 * corner shapes. It provides nothing: it reads what `ChurchPresenterTheme` above it set.
 */
object AppTheme {
    val colors: ThemeColors
        @Composable @ReadOnlyComposable
        get() = ThemeColors(MaterialTheme.colorScheme, MaterialTheme.semantic)

    val typography: TypeScale
        @Composable @ReadOnlyComposable
        get() = typeScale(MaterialTheme.typography.bodyMedium.fontFamily ?: FontFamily.Default)

    val elevation: Shadows
        @Composable @ReadOnlyComposable
        get() = if (isDarkScheme(MaterialTheme.colorScheme)) DarkShadows else LightShadows

    val shapes: AppShapes
        @Composable @ReadOnlyComposable
        get() = DefaultAppShapes

    val focusRingWidth: Dp = 3.dp
}
