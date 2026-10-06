package org.churchpresenter.settingspages

import androidx.compose.runtime.Composable
import org.churchpresenter.theme.AppThemeWrapper
import org.churchpresenter.theme.ThemeMode

typealias WindowRoot = @Composable (theme: ThemeMode, content: @Composable () -> Unit) -> Unit

internal val DefaultWindowRoot: WindowRoot = { theme, content -> AppThemeWrapper(theme = theme) { content() } }
