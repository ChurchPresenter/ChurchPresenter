package org.churchpresenter.app.churchpresenter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.SystemTheme

@Composable
fun AppThemeWrapper(
    theme: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {

    val themeManager = remember { ThemeManager() }
    ProvideThemeManager(themeManager = themeManager) {
        val isDarkTheme = when (theme) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }

        ChurchPresenterTheme(darkTheme = isDarkTheme) {
            content()
        }
    }
}
