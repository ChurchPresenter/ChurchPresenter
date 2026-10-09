package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.sharedui.guide.guideTarget
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.server.updateApiKey
import org.churchpresenter.server.updateFileUploadEnabled
import org.churchpresenter.server.updateMaxMediaUploadMb
import androidx.compose.material3.minimumInteractiveComponentSize
import org.churchpresenter.theme.components.toggleRow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import org.churchpresenter.theme.AppShape
import androidx.compose.foundation.verticalScroll
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.SettingsTextField
import org.churchpresenter.theme.components.RaisedSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.api_key_hint
import org.churchpresenter.strings.generated.resources.api_key_label
import org.churchpresenter.strings.generated.resources.api_key_protection
import org.churchpresenter.strings.generated.resources.browser_source_note_in_server_settings
import org.churchpresenter.strings.generated.resources.allow_file_upload
import org.churchpresenter.strings.generated.resources.allow_file_upload_description
import org.churchpresenter.strings.generated.resources.max_media_upload_label
import org.churchpresenter.strings.generated.resources.max_media_upload_description
import org.churchpresenter.strings.generated.resources.companion_server
import org.churchpresenter.strings.generated.resources.copy_api_key
import org.churchpresenter.strings.generated.resources.show_qr_code
import org.churchpresenter.strings.generated.resources.enable_server
import org.churchpresenter.strings.generated.resources.generate_api_key
import org.churchpresenter.strings.generated.resources.server_description
import org.churchpresenter.strings.generated.resources.server_port
import org.churchpresenter.strings.generated.resources.server_port_hint
import org.churchpresenter.strings.generated.resources.server_port_note
import org.churchpresenter.strings.generated.resources.server_restart
import org.churchpresenter.strings.generated.resources.server_running
import org.churchpresenter.strings.generated.resources.server_stopped
import org.churchpresenter.strings.generated.resources.server_host_hint
import org.churchpresenter.strings.generated.resources.server_host_label
import org.churchpresenter.strings.generated.resources.server_host_note
import org.churchpresenter.strings.generated.resources.server_url_label
import org.churchpresenter.sharedui.composables.SettingRow
import org.churchpresenter.sharedui.composables.SettingSwitchRow
import org.churchpresenter.sharedui.composables.SettingsScrollbar
import org.churchpresenter.sharedui.composables.SettingsScrollbarGutter
import org.churchpresenter.sharedui.composables.SettingsSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.RemoteClientManager
import org.churchpresenter.server.CalendarSyncService
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource
import java.util.UUID
import org.churchpresenter.sharedui.utils.SystemClipboard

@Composable
fun ServerSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    companionServer: CompanionServer,
    remoteClientManager: RemoteClientManager,
    calendarSync: CalendarSyncService? = null,
) {
    val isRunning by companionServer.isRunning.collectAsState()
    val serverUrl by companionServer.serverUrl.collectAsState()
    val copyText: (String) -> Unit = { text ->
        SystemClipboard.copy(text)
    }

    LaunchedEffect(settings.serverSettings.apiKeyEnabled, settings.serverSettings.apiKey) {
        companionServer.updateApiKey(
            enabled = settings.serverSettings.apiKeyEnabled,
            key = settings.serverSettings.apiKey
        )
    }

    LaunchedEffect(settings.serverSettings.fileUploadEnabled) {
        companionServer.updateFileUploadEnabled(settings.serverSettings.fileUploadEnabled)
    }

    LaunchedEffect(settings.serverSettings.maxMediaUploadMb) {
        companionServer.updateMaxMediaUploadMb(settings.serverSettings.maxMediaUploadMb)
    }

    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(end = SettingsScrollbarGutter),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Card 1: Server ────────────────────────────────────────────────
            ServerCard(
                settings = settings,
                onSettingsChange = onSettingsChange,
                companionServer = companionServer,
                isRunning = isRunning,
                serverUrl = serverUrl,
                copyText = copyText,
            )

            // ── Card 2: Remote Clients ────────────────────────────────────────
            RemoteClientsCard(companionServer = companionServer, remoteClientManager = remoteClientManager)

            // ── Card: Calendar on phones ───────────────────────────────────────
            if (calendarSync != null) {
                CalendarSyncCard(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    sync = calendarSync,
                    labelFor = remoteClientManager::getLabel,
                )
            }

            // ── Card: Lower Third Triggers (Bitfocus Companion) ───────────────
            CompanionTriggersCard(
                settings = settings,
                isRunning = isRunning,
                serverUrl = serverUrl,
                copyText = copyText,
            )
        }
        SettingsScrollbar(scrollState)
    }
}

@Composable
private fun ServerCard(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    companionServer: CompanionServer,
    isRunning: Boolean,
    serverUrl: String,
    copyText: (String) -> Unit,
) {
    var portText by remember(settings.serverSettings.port) {
        mutableStateOf(settings.serverSettings.port.toString())
    }
    var hostText by remember(settings.serverSettings.serverHost) {
        mutableStateOf(settings.serverSettings.serverHost)
    }
    var apiKeyText by remember(settings.serverSettings.apiKey) {
        mutableStateOf(settings.serverSettings.apiKey)
    }

    SettingsSection(title = stringResource(Res.string.companion_server)) {
        Text(
            text = stringResource(Res.string.server_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        // ── Enable toggle + status in one row ─────────────────────────
        ServerEnableRow(
            isRunning = isRunning,
            onEnable = { enable ->
                val port = portText.toIntOrNull() ?: Constants.SERVER_DEFAULT_PORT
                if (enable) {
                    companionServer.start(port, hostText.trim())
                    onSettingsChange { s ->
                        s.copy(serverSettings = s.serverSettings.copy(enabled = true, port = port))
                    }
                } else {
                    companionServer.stop()
                    onSettingsChange { s ->
                        s.copy(serverSettings = s.serverSettings.copy(enabled = false))
                    }
                }
            },
        )

        HorizontalDivider()

        // ── Port + note/Restart in one row ────────────────────────────
        ServerPortRow(
            portText = portText,
            isRunning = isRunning,
            onPortText = { v ->
                portText = v
                v.toIntOrNull()?.let { port ->
                    onSettingsChange { s ->
                        s.copy(serverSettings = s.serverSettings.copy(port = port))
                    }
                }
            },
            onRestart = {
                val port = portText.toIntOrNull() ?: Constants.SERVER_DEFAULT_PORT
                companionServer.stop()
                companionServer.start(port, hostText.trim())
            },
        )

        // ── Host Override ─────────────────────────────────────────────
        ServerHostRow(
            hostText = hostText,
            isRunning = isRunning,
            onHostText = { v ->
                hostText = v
                onSettingsChange { s ->
                    s.copy(serverSettings = s.serverSettings.copy(serverHost = v.trim()))
                }
            },
        )

        // ── Server URL + Copy + QR in one row (shown when running) ───
        if (isRunning && serverUrl.isNotBlank()) {
            ServerUrlRow(
                serverUrl = serverUrl,
                apiKey = if (settings.serverSettings.apiKeyEnabled && apiKeyText.isNotBlank()) apiKeyText else null,
            )
        }

        HorizontalDivider()

        ApiKeySection(
            settings = settings,
            onSettingsChange = onSettingsChange,
            apiKeyText = apiKeyText,
            onApiKeyText = { apiKeyText = it },
            copyText = copyText,
        )

        FileUploadSection(settings = settings, onSettingsChange = onSettingsChange)
    }
}

@Composable
private fun ServerEnableRow(isRunning: Boolean, onEnable: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val interaction = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier.guideTarget(GuideTargets.SERVER_ENABLE).toggleRow(isRunning, onEnable, interaction),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(Res.string.enable_server),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            RaisedSwitch(
                checked = isRunning,
                onCheckedChange = null,
                interactionSource = interaction,
                modifier = Modifier.minimumInteractiveComponentSize(),
            )
        }
        Text(
            text = if (isRunning) stringResource(Res.string.server_running)
                   else stringResource(Res.string.server_stopped),
            style = MaterialTheme.typography.labelSmall,
            color = if (isRunning) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ServerPortRow(
    portText: String,
    isRunning: Boolean,
    onPortText: (String) -> Unit,
    onRestart: () -> Unit,
) {
    SettingRow(label = Res.string.server_port) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsTextField(
                value = portText,
                onValueChange = { v ->
                    if (v.length <= 5 && v.all(Char::isDigit)) onPortText(v)
                },
                modifier = Modifier.width(100.dp),
                singleLine = true,
                enabled = !isRunning,
                placeholder = { Text(stringResource(Res.string.server_port_hint)) }
            )
            if (isRunning) {
                RaisedButton(
                    shape = AppShape(6.dp),
                    onClick = onRestart,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(stringResource(Res.string.server_restart), style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Text(
                    text = stringResource(Res.string.server_port_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ServerHostRow(hostText: String, isRunning: Boolean, onHostText: (String) -> Unit) {
    SettingRow(label = Res.string.server_host_label) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SettingsTextField(
                value = hostText,
                onValueChange = onHostText,
                modifier = Modifier.width(280.dp),
                singleLine = true,
                enabled = !isRunning,
                placeholder = {
                    Text(
                        stringResource(Res.string.server_host_hint),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            )
            Text(
                text = stringResource(Res.string.server_host_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ServerUrlRow(serverUrl: String, apiKey: String?) {
    var showConnectionQrDialog by remember { mutableStateOf(false) }
    SettingRow(label = Res.string.server_url_label) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SettingsTextField(
                value = serverUrl,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.widthIn(max = 280.dp),
            )
            RaisedButton(
                shape = AppShape(6.dp),
                onClick = { showConnectionQrDialog = true },
                modifier = Modifier.guideTarget(GuideTargets.SERVER_QR),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Text(stringResource(Res.string.show_qr_code), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    if (showConnectionQrDialog) {
        ConnectionQrDialog(
            serverUrl = serverUrl,
            apiKey = apiKey,
            onDismiss = { showConnectionQrDialog = false }
        )
    }
}

@Composable
private fun ApiKeySection(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    apiKeyText: String,
    onApiKeyText: (String) -> Unit,
    copyText: (String) -> Unit,
) {
    // ── API Key protection toggle ─────────────────────────────────
    SettingSwitchRow(
        label = Res.string.api_key_protection,
        checked = settings.serverSettings.apiKeyEnabled,
        onCheckedChange = { enabled ->
            onSettingsChange { s ->
                s.copy(serverSettings = s.serverSettings.copy(apiKeyEnabled = enabled))
            }
        }
    )
    Text(
        text = stringResource(Res.string.browser_source_note_in_server_settings),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // ── API Key field + Generate + Copy all in one row ────────────
    if (settings.serverSettings.apiKeyEnabled) {
        val setApiKey: (String) -> Unit = { key ->
            onApiKeyText(key)
            onSettingsChange { s ->
                s.copy(serverSettings = s.serverSettings.copy(apiKey = key))
            }
        }
        SettingRow(label = Res.string.api_key_label) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SettingsTextField(
                    value = apiKeyText,
                    onValueChange = setApiKey,
                    modifier = Modifier.width(350.dp),
                    singleLine = true,
                    placeholder = {
                        Text(
                            stringResource(Res.string.api_key_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                )
                CopyUrlButton(
                    text = stringResource(Res.string.generate_api_key),
                    tone = ButtonTone.PRIMARY,
                    onClick = { setApiKey(UUID.randomUUID().toString().replace("-", "")) },
                )
                CopyUrlButton(
                    text = stringResource(Res.string.copy_api_key),
                    tone = ButtonTone.SECONDARY,
                    onClick = { copyText(apiKeyText) },
                )
            }
        }
    }
}

@Composable
private fun FileUploadSection(settings: AppSettings, onSettingsChange: ((AppSettings) -> AppSettings) -> Unit) {
    // ── Allow File Upload toggle ──────────────────────────────────
    SettingSwitchRow(
        label = Res.string.allow_file_upload,
        checked = settings.serverSettings.fileUploadEnabled,
        onCheckedChange = { enabled ->
            onSettingsChange { s ->
                s.copy(serverSettings = s.serverSettings.copy(fileUploadEnabled = enabled))
            }
        }
    )
    Text(
        text = stringResource(Res.string.allow_file_upload_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // ── Max media upload size (only relevant when uploads are enabled) ──
    if (settings.serverSettings.fileUploadEnabled) {
        var maxMbText by remember(settings.serverSettings.maxMediaUploadMb) {
            mutableStateOf(settings.serverSettings.maxMediaUploadMb.toString())
        }
        SettingRow(label = Res.string.max_media_upload_label) {
            SettingsTextField(
                value = maxMbText,
                onValueChange = { v ->
                    if (v.length <= 5 && v.all(Char::isDigit)) {
                        maxMbText = v
                        v.toIntOrNull()?.takeIf { it > 0 }?.let { mb ->
                            onSettingsChange { s ->
                                s.copy(serverSettings = s.serverSettings.copy(maxMediaUploadMb = mb))
                            }
                        }
                    }
                },
                modifier = Modifier.width(100.dp),
                singleLine = true,
                placeholder = { Text(Constants.DEFAULT_MAX_MEDIA_UPLOAD_MB.toString()) }
            )
        }
        Text(
            text = stringResource(Res.string.max_media_upload_description, Constants.DEFAULT_MAX_MEDIA_UPLOAD_MB),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal enum class ButtonTone { PRIMARY, SECONDARY, ERROR_CONTAINER, ERROR }

/** The small labelled button every copy-a-URL action on this tab is drawn as. */
@Composable
internal fun CopyUrlButton(
    text: String,
    tone: ButtonTone,
    onClick: () -> Unit,
    horizontalPadding: Dp = 12.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = when (tone) {
        ButtonTone.PRIMARY -> ButtonDefaults.buttonColors(
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer
        )
        ButtonTone.SECONDARY -> ButtonDefaults.buttonColors(
            containerColor = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer
        )
        ButtonTone.ERROR_CONTAINER -> ButtonDefaults.buttonColors(
            containerColor = scheme.errorContainer,
            contentColor = scheme.onErrorContainer
        )
        ButtonTone.ERROR -> ButtonDefaults.buttonColors(
            containerColor = scheme.error,
            contentColor = scheme.onError
        )
    }
    RaisedButton(
        shape = AppShape(6.dp),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 6.dp),
        colors = colors
    ) { Text(text, style = MaterialTheme.typography.labelSmall) }
}
