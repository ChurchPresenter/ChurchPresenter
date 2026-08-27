package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.runtime.Composable
import org.churchpresenter.theme.AppThemeWrapper
import org.churchpresenter.theme.ThemeMode

/**
 * What every one of the app's Compose windows is wrapped in: the theme, plus the app-wide
 * composition locals that have to be installed once per window.
 *
 * Each Compose window is its own composition, so a `CompositionLocalProvider` in one window does
 * not reach another — anything window-wide has to be installed at all fifteen roots or at none.
 * Calling `AppThemeWrapper` directly is what those roots used to do, and adding a provider then
 * meant editing fifteen call sites and remembering to edit the sixteenth. This exists so that is
 * one edit in one place.
 *
 * `AppThemeWrapper` itself stays in `:theme` and stays about theming: that module owns the colour
 * schemes and the type scale, and a clipboard has no business in it.
 *
 * **New windows should call this, not `AppThemeWrapper`.**
 */
@Composable
fun AppWindowRoot(theme: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    AppThemeWrapper(theme = theme) {
        ProvideSafeClipboard(content)
    }
}
