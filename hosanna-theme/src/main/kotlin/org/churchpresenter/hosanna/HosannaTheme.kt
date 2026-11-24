package org.churchpresenter.hosanna

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalHosannaColors = staticCompositionLocalOf { HosannaLightColors }
val LocalHosannaTypography = staticCompositionLocalOf { hosannaTypography(FontFamily.Default) }
val LocalHosannaElevation = staticCompositionLocalOf { HosannaLightElevation }
val LocalHosannaShapes = staticCompositionLocalOf { HosannaDefaultShapes }

@Composable
fun HosannaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val typography = hosannaTypography(rememberInterFamily())
    CompositionLocalProvider(
        LocalHosannaColors provides if (darkTheme) HosannaDarkColors else HosannaLightColors,
        LocalHosannaTypography provides typography,
        LocalHosannaElevation provides if (darkTheme) HosannaDarkElevation else HosannaLightElevation,
        LocalHosannaShapes provides HosannaDefaultShapes,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) HosannaDarkColorScheme else HosannaLightColorScheme,
            typography = typography.toMaterial(),
            shapes = HosannaDefaultShapes.toMaterial(),
            content = content,
        )
    }
}

object HosannaTheme {
    val colors: HosannaColors
        @Composable @ReadOnlyComposable
        get() = LocalHosannaColors.current

    val typography: HosannaTypography
        @Composable @ReadOnlyComposable
        get() = LocalHosannaTypography.current

    val elevation: HosannaElevation
        @Composable @ReadOnlyComposable
        get() = LocalHosannaElevation.current

    val shapes: HosannaShapes
        @Composable @ReadOnlyComposable
        get() = LocalHosannaShapes.current

    val focusRingWidth: Dp = 3.dp
}
