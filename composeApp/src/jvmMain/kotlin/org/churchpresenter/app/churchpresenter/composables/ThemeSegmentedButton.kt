package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.tooltip_theme_dark
import org.churchpresenter.resources.generated.resources.tooltip_theme_light
import org.churchpresenter.resources.generated.resources.tooltip_theme_system
import org.churchpresenter.theme.ThemeMode
import org.jetbrains.compose.resources.stringResource

@Composable
fun ThemeSegmentedButton(
    selectedTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeItems = listOf(
        SegmentedButtonItem(ThemeMode.LIGHT, "☀", stringResource(Res.string.tooltip_theme_light)),
        SegmentedButtonItem(ThemeMode.DARK, "🌙", stringResource(Res.string.tooltip_theme_dark)),
        SegmentedButtonItem(ThemeMode.SYSTEM, "⚙", stringResource(Res.string.tooltip_theme_system))
    )

    SegmentedButton(
        items = themeItems,
        selectedValue = selectedTheme,
        onValueChange = onThemeChange,
        modifier = modifier
    )
}

