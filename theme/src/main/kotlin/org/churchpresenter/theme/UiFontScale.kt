package org.churchpresenter.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** The density [ProvideUiFontScale] last put in place, so a second call in the same window is a no-op. */
private val LocalUiScaledDensity = staticCompositionLocalOf<Density?> { null }

/**
 * Draws [content] with the user's UI text size applied, as a font scale on [LocalDensity] — so every
 * `sp` grows with it, including the many sites that set a size of their own.
 *
 * Each window starts from the display's own density, so every window of the app's UI needs this once:
 * [ChurchPresenterTheme] calls it, and a dialog that opens its own window without going through the
 * theme wraps its body in it. Output windows must not — the congregation's screen does not follow the
 * operator's text size.
 *
 * Calling it again inside a window that already has it changes nothing: the density in effect is the
 * one it put there, which is how a theme nested inside another does not scale the text twice.
 */
@Composable
fun ProvideUiFontScale(
    scale: Float = LocalThemeCustomization.current.fontScale,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    if (scale == 1f || density === LocalUiScaledDensity.current) {
        content()
        return
    }
    val scaled = remember(density, scale) { Density(density.density, density.fontScale * scale) }
    CompositionLocalProvider(
        LocalDensity provides scaled,
        LocalUiScaledDensity provides scaled,
        content = content,
    )
}
