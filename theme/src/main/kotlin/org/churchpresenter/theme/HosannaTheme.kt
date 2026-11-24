package org.churchpresenter.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object HosannaTheme {
    val typography: HosannaTypography
        @Composable @ReadOnlyComposable
        get() = hosannaTypography(MaterialTheme.typography.bodyMedium.fontFamily ?: FontFamily.Default)

    val elevation: HosannaElevation
        @Composable @ReadOnlyComposable
        get() = HosannaElevation(
            layers = if (isDarkScheme(MaterialTheme.colorScheme)) HosannaDarkShadows else HosannaLightShadows,
            edge = MaterialTheme.semantic.elevationEdge,
        )

    val shapes: HosannaShapes = HosannaDefaultShapes

    val focusRingWidth: Dp = 3.dp
}
