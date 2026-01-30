package org.churchpresenter.app.churchpresenter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.ui.theme.rememberThemeManager

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
                text = when (currentTheme) {
                    ThemeMode.LIGHT -> "☀"
                    ThemeMode.DARK -> "🌙"
                    ThemeMode.SYSTEM -> "⚙"
                },
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = when (mode) {
                                    ThemeMode.LIGHT -> "☀"
                                    ThemeMode.DARK -> "🌙"
                                    ThemeMode.SYSTEM -> "⚙"
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when (mode) {
                                    ThemeMode.LIGHT -> "Light Theme"
                                    ThemeMode.DARK -> "Dark Theme"
                                    ThemeMode.SYSTEM -> "System Theme"
                                },
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
