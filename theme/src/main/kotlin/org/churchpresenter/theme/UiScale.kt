package org.churchpresenter.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** The density [ProvideUiScale] last put in place, so a second call in the same window is a no-op. */
private val LocalUiScaledDensity = staticCompositionLocalOf<Density?> { null }

/**
 * Draws [content] at the user's interface size and UI text size, both applied to [LocalDensity]:
 * [uiScale] multiplies the density, so every `dp` and `sp` grows — the whole layout, as Ctrl + does
 * in a browser — and [fontScale] multiplies the font scale on top, so text alone can grow further.
 * Both multiply what the display already asks for, so a machine scaled by its OS stays scaled.
 *
 * Each window starts from the display's own density, so every window of the app's UI needs this once:
 * [ChurchPresenterTheme] calls it, and a dialog that opens its own window without going through the
 * theme wraps its body in it. Output windows must not — the congregation's screen does not follow the
 * operator's interface size.
 *
 * Calling it again inside a window that already has it changes nothing: the density in effect is the
 * one it put there, which is how a theme nested inside another does not scale the window twice.
 */
@Composable
fun ProvideUiScale(
    fontScale: Float = LocalThemeCustomization.current.fontScale,
    uiScale: Float = LocalThemeCustomization.current.uiScale,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    if ((fontScale == 1f && uiScale == 1f) || density === LocalUiScaledDensity.current) {
        content()
        return
    }
    val scaled = remember(density, fontScale, uiScale) {
        Density(density.density * uiScale, density.fontScale * fontScale)
    }
    CompositionLocalProvider(
        LocalDensity provides scaled,
        LocalUiScaledDensity provides scaled,
        content = content,
    )
}

/** How close two scales must be to count as the same step; they are stored as floats. */
private const val SCALE_EPSILON = 0.001f

/**
 * The largest interface size a screen of [screenWidthDp] x [screenHeightDp] can take while still
 * leaving the window [layoutWidthDp] x [layoutHeightDp] of room — what the main window's layout needs.
 * Past it, panels collapse and controls stop fitting: 3x on a 2560x1600 laptop is an 853x533 window.
 * Never below 1, so the default size is always reachable; a screen that cannot
 * be measured (none, or headless) takes every step.
 */
fun maxUiScale(screenWidthDp: Float, screenHeightDp: Float, layoutWidthDp: Float, layoutHeightDp: Float): Float {
    if (screenWidthDp <= 0f || screenHeightDp <= 0f) return UI_SCALES.last()
    return minOf(screenWidthDp / layoutWidthDp, screenHeightDp / layoutHeightDp).coerceAtLeast(1f)
}

/** The steps of [UI_SCALES] a screen whose largest size is [maxScale] can take. */
fun uiScaleSteps(maxScale: Float): List<Float> = UI_SCALES.filter { it <= maxScale + SCALE_EPSILON }

/**
 * The next interface size up from [current] — or down when not [zoomIn] — among the steps
 * [maxScale] allows. A size between steps moves to the neighbouring one, and the ends stay put.
 */
fun steppedUiScale(current: Float, zoomIn: Boolean, maxScale: Float): Float {
    val steps = uiScaleSteps(maxScale)
    return if (zoomIn) {
        steps.firstOrNull { it > current + SCALE_EPSILON } ?: steps.last()
    } else {
        steps.lastOrNull { it < current - SCALE_EPSILON } ?: steps.first()
    }
}
