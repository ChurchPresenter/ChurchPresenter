package org.churchpresenter.app.churchpresenter.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
fun AppThemeWrapper(
    theme: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val themeManager = remember { ThemeManager() }
    ProvideThemeManager(themeManager = themeManager) {
        ChurchPresenterTheme(themeMode = theme) {
            content()
        }
    }
}
