package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.data.AppSettings

@Composable
fun ProjectionSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Number of Projection Windows
        SectionHeader("Number of Projection Windows")
        Spacer(modifier = Modifier.height(4.dp))
        SettingRow("Windows (1-3)") {
            NumberSettingsTextField(
                initialText = settings.projectionSettings.numberOfWindows,
                onValueChange = { value ->
                    onSettingsChange { s ->
                        s.copy(projectionSettings = s.projectionSettings.copy(numberOfWindows = value))
                    }
                },
                range = 1..3
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Window Position Settings
        SectionHeader("Window Position")
        Spacer(modifier = Modifier.height(8.dp))

        // Visual representation box with position fields
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top position
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Top",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    NumberSettingsTextField(
                        initialText = settings.projectionSettings.windowTop,
                        onValueChange = { value ->
                            onSettingsChange { s ->
                                s.copy(projectionSettings = s.projectionSettings.copy(windowTop = value))
                            }
                        },
                        range = 0..10000
                    )
                }

                // Middle row - Left and Right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left position
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Left",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        NumberSettingsTextField(
                            initialText = settings.projectionSettings.windowLeft,
                            onValueChange = { value ->
                                onSettingsChange { s ->
                                    s.copy(projectionSettings = s.projectionSettings.copy(windowLeft = value))
                                }
                            },
                            range = 0..10000
                        )
                    }

                    // Center indicator
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Screen",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // Right position
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Right",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        NumberSettingsTextField(
                            initialText = settings.projectionSettings.windowRight,
                            onValueChange = { value ->
                                onSettingsChange { s ->
                                    s.copy(projectionSettings = s.projectionSettings.copy(windowRight = value))
                                }
                            },
                            range = 0..10000
                        )
                    }
                }

                // Bottom position
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Bottom",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    NumberSettingsTextField(
                        initialText = settings.projectionSettings.windowBottom,
                        onValueChange = { value ->
                            onSettingsChange { s ->
                                s.copy(projectionSettings = s.projectionSettings.copy(windowBottom = value))
                            }
                        },
                        range = 0..10000
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Help text
        Text(
            text = "Position values represent pixel offsets from screen edges. Use these to adjust projection window placement on secondary displays.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SettingRow(
    label: String,
    width: Dp = 120.dp,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(width)
        )
        content()
    }
}