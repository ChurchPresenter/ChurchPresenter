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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.api_key_hint
import churchpresenter.composeapp.generated.resources.api_key_label
import churchpresenter.composeapp.generated.resources.api_key_protection
import churchpresenter.composeapp.generated.resources.companion_server
import churchpresenter.composeapp.generated.resources.copy_url
import churchpresenter.composeapp.generated.resources.enable_server
import churchpresenter.composeapp.generated.resources.server_description
import churchpresenter.composeapp.generated.resources.server_endpoints
import churchpresenter.composeapp.generated.resources.server_port
import churchpresenter.composeapp.generated.resources.server_port_hint
import churchpresenter.composeapp.generated.resources.server_port_note
import churchpresenter.composeapp.generated.resources.server_restart
import churchpresenter.composeapp.generated.resources.server_running
import churchpresenter.composeapp.generated.resources.server_stopped
import churchpresenter.composeapp.generated.resources.server_url_label
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.jetbrains.compose.resources.stringResource

@Composable
fun ServerSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    companionServer: CompanionServer
) {
    val isRunning by companionServer.isRunning.collectAsState()
    val serverUrl by companionServer.serverUrl.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    // Local port/key state so edits don't immediately restart anything
    var portText by remember(settings.serverSettings.port) {
        mutableStateOf(settings.serverSettings.port.toString())
    }
    var apiKeyText by remember(settings.serverSettings.apiKey) {
        mutableStateOf(settings.serverSettings.apiKey)
    }

    // Keep API key in server in sync with settings (no restart needed)
    LaunchedEffect(settings.serverSettings.apiKeyEnabled, settings.serverSettings.apiKey) {
        companionServer.updateApiKey(
            enabled = settings.serverSettings.apiKeyEnabled,
            key = settings.serverSettings.apiKey
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(5.dp)
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 15.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Text(
            text = stringResource(Res.string.companion_server),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(Res.string.server_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        // ── Enable toggle ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(Res.string.enable_server),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isRunning) stringResource(Res.string.server_running)
                           else stringResource(Res.string.server_stopped),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isRunning) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isRunning,
                onCheckedChange = { enable ->
                    val port = portText.toIntOrNull() ?: Constants.SERVER_DEFAULT_PORT
                    if (enable) {
                        companionServer.start(port)
                        onSettingsChange { s ->
                            s.copy(serverSettings = s.serverSettings.copy(enabled = true, port = port))
                        }
                    } else {
                        companionServer.stop()
                        onSettingsChange { s ->
                            s.copy(serverSettings = s.serverSettings.copy(enabled = false))
                        }
                    }
                }
            )
        }

        HorizontalDivider()

        // ── Port ──────────────────────────────────────────────────────────────
        SettingRow(label = stringResource(Res.string.server_port)) {
            Column {
                OutlinedTextField(
                    value = portText,
                    onValueChange = { v ->
                        if (v.length <= 5 && v.all(Char::isDigit)) {
                            portText = v
                            v.toIntOrNull()?.let { port ->
                                onSettingsChange { s ->
                                    s.copy(serverSettings = s.serverSettings.copy(port = port))
                                }
                            }
                        }
                    },
                    modifier = Modifier.width(120.dp),
                    singleLine = true,
                    enabled = !isRunning,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    placeholder = { Text(stringResource(Res.string.server_port_hint)) }
                )
                if (!isRunning) {
                    Text(
                        text = stringResource(Res.string.server_port_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── Server URL (shown when running) ───────────────────────────────────
        if (isRunning && serverUrl.isNotBlank()) {
            SettingRow(label = stringResource(Res.string.server_url_label)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = serverUrl,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Button(
                        onClick = { clipboardManager.setText(AnnotatedString(serverUrl)) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text(stringResource(Res.string.copy_url), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Restart button
            Button(
                onClick = {
                    val port = portText.toIntOrNull() ?: Constants.SERVER_DEFAULT_PORT
                    companionServer.stop()
                    companionServer.start(port)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(stringResource(Res.string.server_restart), style = MaterialTheme.typography.labelLarge)
            }
        }

        HorizontalDivider()

        // ── API Key ───────────────────────────────────────────────────────────
        SettingRow(label = stringResource(Res.string.api_key_protection)) {
            Switch(
                checked = settings.serverSettings.apiKeyEnabled,
                onCheckedChange = { enabled ->
                    onSettingsChange { s ->
                        s.copy(serverSettings = s.serverSettings.copy(apiKeyEnabled = enabled))
                    }
                }
            )
        }

        if (settings.serverSettings.apiKeyEnabled) {
            SettingRow(label = stringResource(Res.string.api_key_label)) {
                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { v ->
                        apiKeyText = v
                        onSettingsChange { s ->
                            s.copy(serverSettings = s.serverSettings.copy(apiKey = v))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    placeholder = { Text(stringResource(Res.string.api_key_hint)) }
                )
            }
        }

        HorizontalDivider()

        // ── Endpoints info ────────────────────────────────────────────────────
        Text(
            text = stringResource(Res.string.server_endpoints),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        listOf(
            "GET ${Constants.ENDPOINT_INFO}     — server info",
            "GET ${Constants.ENDPOINT_SONGS}    — song catalog",
            "GET ${Constants.ENDPOINT_BIBLE}    — bible catalog",
            "GET ${Constants.ENDPOINT_SCHEDULE} — current schedule",
            "WS  ${Constants.ENDPOINT_WS}       — real-time updates"
        ).forEach { endpoint ->
            Text(
                text = endpoint,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
    }
}

@Composable
private fun SettingRow(
    label: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(160.dp)
        )
        content()
    }
}

