package org.churchpresenter.slides.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.hymn_quick_screen
import org.churchpresenter.strings.generated.resources.hymn_quick_window
import org.jetbrains.compose.resources.stringResource
import java.awt.GraphicsEnvironment

@Composable
internal fun PresentationTabScope.PresentationScreenChoice(
    onSelected: () -> Unit = {},
    anchor: (@Composable (open: () -> Unit) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val screens = remember(expanded) {
        if (GraphicsEnvironment.isHeadless()) emptyList() else
            GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.mapIndexed { index, device ->
                val bounds = device.defaultConfiguration.bounds
                "${index + 1} · ${bounds.width}×${bounds.height}"
            }
    }
    val window = stringResource(Res.string.hymn_quick_window)
    val selected = appSettings.projectionSettings.quickPlayDisplay
    Box {
        if (anchor != null) anchor { expanded = true } else {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.padding(vertical = 2.dp)) {
                Text("${stringResource(Res.string.hymn_quick_screen)} · ${screens.getOrNull(selected ?: -1) ?: window}")
            }
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            (listOf(window) + screens).forEachIndexed { index, label ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    expanded = false
                    onSettingsChange {
                        it.copy(projectionSettings = it.projectionSettings.copy(
                            windowedProjection = index == 0,
                            quickPlayDisplay = (index - 1).takeIf { value -> value >= 0 },
                        ))
                    }
                    onSelected()
                })
            }
        }
    }
}
