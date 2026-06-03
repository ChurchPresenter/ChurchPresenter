package org.churchpresenter.app.churchpresenter.ui.theme

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
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

// Typography definition
val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    )
)

// Light theme colors — neutral grey palette
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4A5568),           // muted slate-grey
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1E7),  // light grey-blue container
    onPrimaryContainer = Color(0xFF1A202C),
    secondary = Color(0xFF607D8B),         // blue-grey accent
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFD8DC),
    onSecondaryContainer = Color(0xFF263238),
    tertiary = Color(0xFF78909C),          // lighter blue-grey
    onTertiary = Color.White,
    error = Color(0xFFB00020),
    onError = Color.White,
    errorContainer = Color(0xFFFDE7E7),
    onErrorContainer = Color(0xFF790000),
    background = Color(0xFFF4F4F5),        // very light grey, no blue tint
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFEFEFF1),           // neutral light grey surface
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFE2E2E5),    // slightly darker grey, no purple
    onSurfaceVariant = Color(0xFF44444A),
    outline = Color(0xFF8A8A94),
    outlineVariant = Color(0xFFCCCCD0),
    // Custom colors for buttons
    inverseSurface = Color(0xFF4CAF50),    // Success button background
    inverseOnSurface = Color.White,        // Success button text
)

// Warm light theme — cream/amber tones
private val WarmColorScheme = lightColorScheme(
    primary = Color(0xFF7C5C3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDD9BC),
    onPrimaryContainer = Color(0xFF2E1A05),
    secondary = Color(0xFF8D6E4A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8D0B5),
    onSecondaryContainer = Color(0xFF2E1A05),
    tertiary = Color(0xFFA07850),
    onTertiary = Color.White,
    error = Color(0xFFB00020),
    onError = Color.White,
    errorContainer = Color(0xFFFDE7E7),
    onErrorContainer = Color(0xFF790000),
    background = Color(0xFFFAF3E8),
    onBackground = Color(0xFF1E150A),
    surface = Color(0xFFF5ECD8),
    onSurface = Color(0xFF1E150A),
    surfaceVariant = Color(0xFFEBDFC8),
    onSurfaceVariant = Color(0xFF4A3820),
    outline = Color(0xFF9C8060),
    outlineVariant = Color(0xFFD9C8A8),
    inverseSurface = Color(0xFF4CAF50),
    inverseOnSurface = Color.White,
)

// Ocean light theme — soft blue-tinted surfaces
private val OceanColorScheme = lightColorScheme(
    primary = Color(0xFF2A6B8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBFDDED),
    onPrimaryContainer = Color(0xFF001F2E),
    secondary = Color(0xFF4A7E96),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCAE3EF),
    onSecondaryContainer = Color(0xFF001F2E),
    tertiary = Color(0xFF3A8FA8),
    onTertiary = Color.White,
    error = Color(0xFFB00020),
    onError = Color.White,
    errorContainer = Color(0xFFFDE7E7),
    onErrorContainer = Color(0xFF790000),
    background = Color(0xFFEDF5FA),
    onBackground = Color(0xFF0A1A22),
    surface = Color(0xFFE0EEF6),
    onSurface = Color(0xFF0A1A22),
    surfaceVariant = Color(0xFFCCE2EE),
    onSurfaceVariant = Color(0xFF1E3D50),
    outline = Color(0xFF5A8FAA),
    outlineVariant = Color(0xFFAAD0E0),
    inverseSurface = Color(0xFF4CAF50),
    inverseOnSurface = Color.White,
)

// Rose light theme — warm pinkish-grey palette
private val RoseColorScheme = lightColorScheme(
    primary = Color(0xFF8E4A5A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDD0D6),
    onPrimaryContainer = Color(0xFF2E0A12),
    secondary = Color(0xFF9E6070),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFD5DA),
    onSecondaryContainer = Color(0xFF2E0A12),
    tertiary = Color(0xFFAA7080),
    onTertiary = Color.White,
    error = Color(0xFFB00020),
    onError = Color.White,
    errorContainer = Color(0xFFFDE7E7),
    onErrorContainer = Color(0xFF790000),
    background = Color(0xFFFAF0F2),
    onBackground = Color(0xFF1E0A10),
    surface = Color(0xFFF5E6E9),
    onSurface = Color(0xFF1E0A10),
    surfaceVariant = Color(0xFFEDD5DA),
    onSurfaceVariant = Color(0xFF4A2030),
    outline = Color(0xFFA06070),
    outlineVariant = Color(0xFFDDB8C0),
    inverseSurface = Color(0xFF4CAF50),
    inverseOnSurface = Color.White,
)

// Dark theme colors
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF004881),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFF4DB6AC),
    onSecondary = Color(0xFF003D36),
    secondaryContainer = Color(0xFF005B4F),
    onSecondaryContainer = Color(0xFF70F0DD),
    tertiary = Color(0xFFE1BEE7),
    onTertiary = Color(0xFF4A148C),
    error = Color(0xFFF44336),
    onError = Color.White,
    errorContainer = Color(0xFFD32F2F),
    onErrorContainer = Color.White,
    background = Color(0xFF10131A),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    // Custom colors for buttons
    inverseSurface = Color(0xFF66BB6A), // Success button background (lighter for dark theme)
    inverseOnSurface = Color.Black, // Success button text (black on light green)
)

// Midnight dark theme — deep navy blue
private val MidnightColorScheme = darkColorScheme(
    primary = Color(0xFF82AAFF),
    onPrimary = Color(0xFF001A45),
    primaryContainer = Color(0xFF003180),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFF7EB8D4),
    onSecondary = Color(0xFF003547),
    secondaryContainer = Color(0xFF004D66),
    onSecondaryContainer = Color(0xFFBDE9FF),
    tertiary = Color(0xFFA8C8E8),
    onTertiary = Color(0xFF0A2540),
    error = Color(0xFFF44336),
    onError = Color.White,
    errorContainer = Color(0xFFD32F2F),
    onErrorContainer = Color.White,
    background = Color(0xFF080E1A),
    onBackground = Color(0xFFDDE4F0),
    surface = Color(0xFF0E1520),
    onSurface = Color(0xFFDDE4F0),
    surfaceVariant = Color(0xFF1E2A3A),
    onSurfaceVariant = Color(0xFFB0BDD0),
    outline = Color(0xFF5A7090),
    outlineVariant = Color(0xFF1E2A3A),
    inverseSurface = Color(0xFF66BB6A),
    inverseOnSurface = Color.Black,
)

// Forest dark theme — dark green-tinted surfaces
private val ForestColorScheme = darkColorScheme(
    primary = Color(0xFF80C8A0),
    onPrimary = Color(0xFF003820),
    primaryContainer = Color(0xFF005030),
    onPrimaryContainer = Color(0xFFB0E8C8),
    secondary = Color(0xFF60A880),
    onSecondary = Color(0xFF003018),
    secondaryContainer = Color(0xFF004020),
    onSecondaryContainer = Color(0xFF90D8A8),
    tertiary = Color(0xFF90C8A0),
    onTertiary = Color(0xFF003818),
    error = Color(0xFFF44336),
    onError = Color.White,
    errorContainer = Color(0xFFD32F2F),
    onErrorContainer = Color.White,
    background = Color(0xFF080E0A),
    onBackground = Color(0xFFD0E8D8),
    surface = Color(0xFF0E1810),
    onSurface = Color(0xFFD0E8D8),
    surfaceVariant = Color(0xFF1A2A1E),
    onSurfaceVariant = Color(0xFFA8C8B0),
    outline = Color(0xFF486858),
    outlineVariant = Color(0xFF1A2A1E),
    inverseSurface = Color(0xFF66BB6A),
    inverseOnSurface = Color.Black,
)

// Mocha dark theme — warm dark brown (Catppuccin-style)
private val MochaColorScheme = darkColorScheme(
    primary = Color(0xFFCBA6F7),
    onPrimary = Color(0xFF1E0040),
    primaryContainer = Color(0xFF301050),
    onPrimaryContainer = Color(0xFFEDD8FF),
    secondary = Color(0xFFF5C2E7),
    onSecondary = Color(0xFF3A0030),
    secondaryContainer = Color(0xFF4A1040),
    onSecondaryContainer = Color(0xFFFFD6F0),
    tertiary = Color(0xFF89DCEB),
    onTertiary = Color(0xFF003040),
    error = Color(0xFFF38BA8),
    onError = Color(0xFF300010),
    errorContainer = Color(0xFF4A0020),
    onErrorContainer = Color(0xFFFFB3C6),
    background = Color(0xFF1E1928),
    onBackground = Color(0xFFCDD6F4),
    surface = Color(0xFF181622),
    onSurface = Color(0xFFCDD6F4),
    surfaceVariant = Color(0xFF302840),
    onSurfaceVariant = Color(0xFFBAC2E8),
    outline = Color(0xFF6C7086),
    outlineVariant = Color(0xFF302840),
    inverseSurface = Color(0xFF66BB6A),
    inverseOnSurface = Color.Black,
)

@Composable
fun ChurchPresenterTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val colorScheme = when (themeMode) {
        ThemeMode.LIGHT -> LightColorScheme
        ThemeMode.DARK -> DarkColorScheme
        ThemeMode.SYSTEM -> if (systemDark) DarkColorScheme else LightColorScheme
        ThemeMode.WARM -> WarmColorScheme
        ThemeMode.OCEAN -> OceanColorScheme
        ThemeMode.ROSE -> RoseColorScheme
        ThemeMode.MIDNIGHT -> MidnightColorScheme
        ThemeMode.FOREST -> ForestColorScheme
        ThemeMode.MOCHA -> MochaColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography
    ) {
        CompositionLocalProvider(
            LocalScrollbarStyle provides ScrollbarStyle(
                minimalHeight = 16.dp,
                thickness = 8.dp,
                shape = RoundedCornerShape(4.dp),
                hoverDurationMillis = 300,
                unhoverColor = colorScheme.onSurface.copy(alpha = 0.3f),
                hoverColor = colorScheme.onSurface.copy(alpha = 0.5f)
            )
        ) {
            content()
        }
    }
}

