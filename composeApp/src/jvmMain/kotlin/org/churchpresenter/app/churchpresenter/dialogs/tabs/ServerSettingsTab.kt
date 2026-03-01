package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.server_api_key
import churchpresenter.composeapp.generated.resources.server_api_key_enabled
import churchpresenter.composeapp.generated.resources.server_api_key_hint
import churchpresenter.composeapp.generated.resources.server_copy_key
import churchpresenter.composeapp.generated.resources.server_enabled
import churchpresenter.composeapp.generated.resources.server_generate_key
import churchpresenter.composeapp.generated.resources.server_info
import churchpresenter.composeapp.generated.resources.server_key_copied
import churchpresenter.composeapp.generated.resources.server_port
import churchpresenter.composeapp.generated.resources.server_restart
import churchpresenter.composeapp.generated.resources.server_restart_note
import churchpresenter.composeapp.generated.resources.server_settings
import churchpresenter.composeapp.generated.resources.server_status_running
import churchpresenter.composeapp.generated.resources.server_status_stopped
import churchpresenter.composeapp.generated.resources.server_url_label
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.jetbrains.compose.resources.stringResource
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.UUID

/**
 * Settings tab for the Ktor companion server.
 * Full API documentation: see COMPANION_API.md in the project root.
 */
@Composable
fun ServerSettingsTab(
    settings: AppSettings,
    companionServer: CompanionServer,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    val serverSettings = settings.serverSettings
    val isRunning by companionServer.isRunning.collectAsState()
    val serverUrl by companionServer.serverUrl.collectAsState()
    var keyCopied by remember { mutableStateOf(false) }
    var portText by remember(serverSettings.port) { mutableStateOf(serverSettings.port.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(Res.string.server_settings),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        HorizontalDivider()

        // ── Enable / Status ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(Res.string.server_enabled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = serverSettings.enabled,
                    onCheckedChange = { enabled ->
                        onSettingsChange { s ->
                            s.copy(serverSettings = s.serverSettings.copy(enabled = enabled))
                        }
                        if (enabled) companionServer.start(serverSettings.port) else companionServer.stop()
                    }
                )
            }

            // Status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val statusColor = if (isRunning) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                androidx.compose.foundation.Canvas(modifier = Modifier.width(10.dp).height(10.dp)) {
                    drawCircle(color = statusColor)
                }
                Text(
                    text = if (isRunning) stringResource(Res.string.server_status_running)
                           else stringResource(Res.string.server_status_stopped),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isRunning) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }

            // Server URL (read-only)
            if (isRunning && serverUrl.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.server_url_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(min = 60.dp)
                    )
                    Text(
                        text = serverUrl,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Port
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(Res.string.server_port),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.widthIn(min = 60.dp)
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { value ->
                        portText = value
                        val port = value.toIntOrNull()
                        if (port != null && port in 1024..65535) {
                            onSettingsChange { s ->
                                s.copy(serverSettings = s.serverSettings.copy(port = port))
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                // Restart button
                Button(
                    onClick = {
                        val port = portText.toIntOrNull()
                        if (port != null && port in 1024..65535) {
                            onSettingsChange { s ->
                                s.copy(serverSettings = s.serverSettings.copy(port = port))
                            }
                            companionServer.stop()
                            if (serverSettings.enabled) companionServer.start(port)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(
                        text = stringResource(Res.string.server_restart),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Text(
                text = stringResource(Res.string.server_restart_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── API Key ───────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(Res.string.server_api_key_enabled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Checkbox(
                    checked = serverSettings.apiKeyEnabled,
                    onCheckedChange = { enabled ->
                        onSettingsChange { s ->
                            s.copy(serverSettings = s.serverSettings.copy(apiKeyEnabled = enabled))
                        }
                    }
                )
            }

            if (serverSettings.apiKeyEnabled) {
                // API Key field + buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = serverSettings.apiKey,
                        onValueChange = { key ->
                            onSettingsChange { s ->
                                s.copy(serverSettings = s.serverSettings.copy(apiKey = key))
                            }
                            keyCopied = false
                        },
                        placeholder = {
                            Text(
                                text = stringResource(Res.string.server_api_key_hint),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(Res.string.server_api_key)) },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    // Generate button
                    Button(
                        onClick = {
                            val newKey = UUID.randomUUID().toString().replace("-", "")
                            onSettingsChange { s ->
                                s.copy(serverSettings = s.serverSettings.copy(apiKey = newKey))
                            }
                            keyCopied = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text(
                            text = stringResource(Res.string.server_generate_key),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    // Copy button
                    Button(
                        onClick = {
                            if (serverSettings.apiKey.isNotEmpty()) {
                                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                clipboard.setContents(StringSelection(serverSettings.apiKey), null)
                                keyCopied = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text(
                            text = if (keyCopied) stringResource(Res.string.server_key_copied)
                                   else stringResource(Res.string.server_copy_key),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Info note
                Text(
                    text = stringResource(Res.string.server_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

