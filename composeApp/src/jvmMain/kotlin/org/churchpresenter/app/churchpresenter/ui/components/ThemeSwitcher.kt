package org.churchpresenter.app.churchpresenter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.dark_theme
import churchpresenter.composeapp.generated.resources.forest_theme
import churchpresenter.composeapp.generated.resources.light_theme
import churchpresenter.composeapp.generated.resources.midnight_theme
import churchpresenter.composeapp.generated.resources.mocha_theme
import churchpresenter.composeapp.generated.resources.ocean_theme
import churchpresenter.composeapp.generated.resources.rose_theme
import churchpresenter.composeapp.generated.resources.system_theme
import churchpresenter.composeapp.generated.resources.warm_theme
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.ui.theme.rememberThemeManager
import org.jetbrains.compose.resources.stringResource

@Composable
fun ThemeSwitcher(
    modifier: Modifier = Modifier
) {
    val themeManager = rememberThemeManager()
    val currentTheme by themeManager.themeMode
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Button(
            onClick = { showMenu = !showMenu },
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(2.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Text(
                text = themeIcon(currentTheme),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    onClick = {
                        themeManager.setThemeMode(mode)
                        showMenu = false
                    },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = themeIcon(mode),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = stringResource(when (mode) {
                                    ThemeMode.LIGHT -> Res.string.light_theme
                                    ThemeMode.DARK -> Res.string.dark_theme
                                    ThemeMode.SYSTEM -> Res.string.system_theme
                                    ThemeMode.WARM -> Res.string.warm_theme
                                    ThemeMode.OCEAN -> Res.string.ocean_theme
                                    ThemeMode.ROSE -> Res.string.rose_theme
                                    ThemeMode.MIDNIGHT -> Res.string.midnight_theme
                                    ThemeMode.FOREST -> Res.string.forest_theme
                                    ThemeMode.MOCHA -> Res.string.mocha_theme
                                }),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    modifier = Modifier.background(
                        if (currentTheme == mode)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    }
}

private fun themeIcon(mode: ThemeMode): String = when (mode) {
    ThemeMode.LIGHT -> "☀"
    ThemeMode.DARK -> "🌙"
    ThemeMode.SYSTEM -> "⚙"
    ThemeMode.WARM -> "🌅"
    ThemeMode.OCEAN -> "🌊"
    ThemeMode.ROSE -> "🌸"
    ThemeMode.MIDNIGHT -> "🌃"
    ThemeMode.FOREST -> "🌲"
    ThemeMode.MOCHA -> "☕"
}

