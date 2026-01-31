package org.churchpresenter.app.churchpresenter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

@Composable
fun AppThemeWrapper(
    content: @Composable () -> Unit
) {
    val themeManager = remember { ThemeManager() }
    ProvideThemeManager(themeManager = themeManager) {
        val currentTheme by themeManager.themeMode
        val isDarkTheme = when (currentTheme) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }

        ChurchPresenterTheme(darkTheme = isDarkTheme) {
            content()
        }
    }
}
