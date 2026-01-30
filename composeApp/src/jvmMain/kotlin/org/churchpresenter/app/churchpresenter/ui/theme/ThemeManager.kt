package org.churchpresenter.app.churchpresenter.ui.theme

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
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
