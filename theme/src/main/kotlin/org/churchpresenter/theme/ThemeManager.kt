package org.churchpresenter.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
    WARM,
    OCEAN,
    ROSE,
    MIDNIGHT,
    FOREST,
    MOCHA,
    STUDIO
}

class ThemeManager {
    private var _themeMode = mutableStateOf(ThemeMode.SYSTEM)
    val themeMode: State<ThemeMode> = _themeMode

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }
}

// Global theme manager instance
val LocalThemeManager = compositionLocalOf { ThemeManager() }

@Composable
fun ProvideThemeManager(
    themeManager: ThemeManager = remember { ThemeManager() },
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalThemeManager provides themeManager) {
        content()
    }
}

@Composable
fun rememberThemeManager(): ThemeManager {
    return LocalThemeManager.current
}

/**
 * The [ThemeMode] a saved settings string means, falling back to [ThemeMode.SYSTEM] when it names
 * nothing recognisable — a settings file from a newer version, or hand-edited.
 *
 * Matched against [ThemeMode.entries] rather than a hand-written `when`, so it cannot fall behind the
 * enum. It already had: the `when` this replaces listed nine of the ten modes and omitted
 * [ThemeMode.STUDIO], which is offered in the top bar, the theme switcher and the setup wizard — so
 * choosing Studio and restarting silently landed the user back on System, with no error anywhere.
 *
 * The stored form is [ThemeMode.toString], i.e. the enum name; [saved] is upper-cased first because
 * older settings files hold lower-case values.
 */
fun themeFromSettings(saved: String): ThemeMode =
    ThemeMode.entries.find { it.name == saved.uppercase() } ?: ThemeMode.SYSTEM
