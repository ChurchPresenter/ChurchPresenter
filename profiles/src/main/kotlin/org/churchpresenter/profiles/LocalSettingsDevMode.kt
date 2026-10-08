package org.churchpresenter.profiles

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the settings dialog is open in dev mode (AGENT.md, "Dev mode only"): the System tab then
 * shows its Dev mode only card. The Options dialog provides it; off everywhere else.
 */
val LocalSettingsDevMode = staticCompositionLocalOf { false }
