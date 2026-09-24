package org.churchpresenter.settings

import kotlinx.serialization.Serializable

/**
 * The Custom theme's optional colour overrides, each as `#RRGGBB`.
 *
 * Empty means "automatic": the theme derives that role from the accent, as it does for everything
 * when none of these is set. They sit beside `AppSettings.customThemeAccent` rather than in it
 * because the accent is always present and these are each independently optional.
 */
@Serializable
data class CustomThemeColors(
    /** Window, panels, cards and fields — its hue and saturation; the base still sets how dark. */
    val background: String = "",
    /** Toggles, secondary buttons and chips. */
    val secondary: String = "",
    /** Body text and labels. Nudged lighter or darker if it would not read on the backgrounds. */
    val text: String = "",
    /** Connected, running, succeeded. */
    val success: String = "",
    /** Connecting, degraded, needs attention. */
    val warning: String = "",
    /** Failed, destructive. */
    val error: String = "",
    /** The fill of a selected row in a list. */
    val selection: String = "",
)
