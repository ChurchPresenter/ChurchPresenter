package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode

@Composable
fun ThemeSegmentedButton(
    selectedTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeItems = listOf(
        SegmentedButtonItem(ThemeMode.LIGHT, "☀"),
        SegmentedButtonItem(ThemeMode.DARK, "🌙"),
        SegmentedButtonItem(ThemeMode.SYSTEM, "⚙")
    )

    SegmentedButton(
        items = themeItems,
        selectedValue = selectedTheme,
        onValueChange = onThemeChange,
        modifier = modifier
    )
}

