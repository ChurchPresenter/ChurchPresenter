package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import org.churchpresenter.app.churchpresenter.utils.AppWindowRoot
import org.churchpresenter.theme.ThemeMode
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.jetbrains.skia.Image as SkiaImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.allowed_clients
import churchpresenter.composeapp.generated.resources.companion_lt_copy_key
import churchpresenter.composeapp.generated.resources.companion_lt_copy_hide
import churchpresenter.composeapp.generated.resources.companion_lt_takedown_desc
import churchpresenter.composeapp.generated.resources.tooltip_clear_display
import churchpresenter.composeapp.generated.resources.companion_lt_copy_nokey
import churchpresenter.composeapp.generated.resources.companion_lt_none
import churchpresenter.composeapp.generated.resources.companion_lt_server_off
import churchpresenter.composeapp.generated.resources.companion_atem_clip_key
import churchpresenter.composeapp.generated.resources.companion_atem_clip_key_note
import churchpresenter.composeapp.generated.resources.companion_atem_clip_only
import churchpresenter.composeapp.generated.resources.companion_atem_key_desc
import churchpresenter.composeapp.generated.resources.companion_atem_key_off
import churchpresenter.composeapp.generated.resources.companion_atem_key_on
import churchpresenter.composeapp.generated.resources.companion_atem_key_section
import churchpresenter.composeapp.generated.resources.companion_atem_still_key
import churchpresenter.composeapp.generated.resources.companion_atem_still_only
import churchpresenter.composeapp.generated.resources.companion_atem_upload_note
import churchpresenter.composeapp.generated.resources.companion_lt_triggers
import churchpresenter.composeapp.generated.resources.companion_lt_triggers_desc
import churchpresenter.composeapp.generated.resources.allowed_clients_description
import churchpresenter.composeapp.generated.resources.api_key_hint
import churchpresenter.composeapp.generated.resources.api_key_label
import churchpresenter.composeapp.generated.resources.api_key_protection
import churchpresenter.composeapp.generated.resources.browser_source_note_in_server_settings
import churchpresenter.composeapp.generated.resources.allow_file_upload
import churchpresenter.composeapp.generated.resources.allow_file_upload_description
import churchpresenter.composeapp.generated.resources.max_media_upload_label
import churchpresenter.composeapp.generated.resources.max_media_upload_description
import churchpresenter.composeapp.generated.resources.blocked_clients
import churchpresenter.composeapp.generated.resources.blocked_clients_description
import churchpresenter.composeapp.generated.resources.client_label_cancel
import churchpresenter.composeapp.generated.resources.client_label_edit_tooltip
import churchpresenter.composeapp.generated.resources.client_label_placeholder
import churchpresenter.composeapp.generated.resources.client_label_save
import churchpresenter.composeapp.generated.resources.companion_server
import churchpresenter.composeapp.generated.resources.close
import churchpresenter.composeapp.generated.resources.copy_api_key
import churchpresenter.composeapp.generated.resources.show_qr_code
import churchpresenter.composeapp.generated.resources.connection_qr_title
import churchpresenter.composeapp.generated.resources.enable_server
import churchpresenter.composeapp.generated.resources.generate_api_key
import churchpresenter.composeapp.generated.resources.no_allowed_clients
import churchpresenter.composeapp.generated.resources.no_blocked_clients
import churchpresenter.composeapp.generated.resources.instance_link_follower_badge
import churchpresenter.composeapp.generated.resources.instance_link_followers_connected_count
import churchpresenter.composeapp.generated.resources.remote_clients_description
import churchpresenter.composeapp.generated.resources.remote_clients_title
import churchpresenter.composeapp.generated.resources.remove
import churchpresenter.composeapp.generated.resources.server_description
import churchpresenter.composeapp.generated.resources.server_port
import churchpresenter.composeapp.generated.resources.server_port_hint
import churchpresenter.composeapp.generated.resources.server_port_note
import churchpresenter.composeapp.generated.resources.server_restart
import churchpresenter.composeapp.generated.resources.server_running
import churchpresenter.composeapp.generated.resources.server_stopped
import churchpresenter.composeapp.generated.resources.server_host_hint
import churchpresenter.composeapp.generated.resources.server_host_label
import churchpresenter.composeapp.generated.resources.server_host_note
import churchpresenter.composeapp.generated.resources.server_url_label
import org.churchpresenter.app.churchpresenter.composables.SettingRow
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbar
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbarGutter
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.settings.ServerSettings
import org.churchpresenter.settings.AtemSettings
import java.net.URLEncoder
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.RemoteClientManager
import org.churchpresenter.app.churchpresenter.server.CalendarSyncService
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.isLottieFile
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import org.churchpresenter.app.churchpresenter.utils.SystemClipboard

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
        Text(
            text = stringResource(Res.string.enable_server),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        RaisedSwitch(checked = isRunning, onCheckedChange = onEnable)
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
    SettingRow(label = stringResource(Res.string.server_port)) {
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
                    shape = RoundedCornerShape(6.dp),
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
    SettingRow(label = stringResource(Res.string.server_host_label)) {
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
                        style = MaterialTheme.typography.bodySmall
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
    SettingRow(label = stringResource(Res.string.server_url_label)) {
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
                shape = RoundedCornerShape(6.dp),
                onClick = { showConnectionQrDialog = true },
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
    SettingRow(label = stringResource(Res.string.api_key_protection)) {
        RaisedSwitch(
            checked = settings.serverSettings.apiKeyEnabled,
            onCheckedChange = { enabled ->
                onSettingsChange { s ->
                    s.copy(serverSettings = s.serverSettings.copy(apiKeyEnabled = enabled))
                }
            }
        )
    }
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
        SettingRow(label = stringResource(Res.string.api_key_label)) {
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
    SettingRow(label = stringResource(Res.string.allow_file_upload)) {
        RaisedSwitch(
            checked = settings.serverSettings.fileUploadEnabled,
            onCheckedChange = { enabled ->
                onSettingsChange { s ->
                    s.copy(serverSettings = s.serverSettings.copy(fileUploadEnabled = enabled))
                }
            }
        )
    }
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
        SettingRow(label = stringResource(Res.string.max_media_upload_label)) {
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

@Composable
private fun RemoteClientsCard(companionServer: CompanionServer, remoteClientManager: RemoteClientManager) {
    SettingsSection(title = stringResource(Res.string.remote_clients_title)) {
        Text(
            text = stringResource(Res.string.remote_clients_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(4.dp))

        val connectedInstanceLinkFollowers by companionServer.connectedInstanceLinkFollowers
            .collectAsState()
        if (connectedInstanceLinkFollowers.isNotEmpty()) {
            Text(
                text = stringResource(
                    Res.string.instance_link_followers_connected_count,
                    connectedInstanceLinkFollowers.size
                ),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
        }

        // ── Allowed clients list ──────────────────────────────────────
        ClientList(
            title = stringResource(Res.string.allowed_clients),
            description = stringResource(Res.string.allowed_clients_description),
            emptyText = stringResource(Res.string.no_allowed_clients),
            clients = remoteClientManager.allowedClients.toList().sorted(),
            statusColor = MaterialTheme.colorScheme.primary,
            remoteClientManager = remoteClientManager,
            followers = connectedInstanceLinkFollowers,
            onRemove = remoteClientManager::removeAllowed,
        )

        Spacer(Modifier.height(4.dp))

        // ── Blocked clients list ──────────────────────────────────────
        ClientList(
            title = stringResource(Res.string.blocked_clients),
            description = stringResource(Res.string.blocked_clients_description),
            emptyText = stringResource(Res.string.no_blocked_clients),
            clients = remoteClientManager.blockedClients.toList().sorted(),
            statusColor = MaterialTheme.colorScheme.error,
            remoteClientManager = remoteClientManager,
            followers = connectedInstanceLinkFollowers,
            onRemove = remoteClientManager::removeBlocked,
        )
    }
}

@Composable
private fun ClientList(
    title: String,
    description: String,
    emptyText: String,
    clients: List<String>,
    statusColor: Color,
    remoteClientManager: RemoteClientManager,
    followers: Set<String>,
    onRemove: (String) -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = statusColor
    )
    Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (clients.isEmpty()) {
        Text(
            text = emptyText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(vertical = 4.dp)
        )
    } else {
        clients.forEach { clientId ->
            key(clientId) {
                ClientRow(
                    clientId = clientId,
                    label = remoteClientManager.getLabel(clientId),
                    onSetLabel = { remoteClientManager.setLabel(clientId, it) },
                    statusColor = statusColor,
                    statusLabel = title,
                    onRemove = { onRemove(clientId) },
                    isInstanceLinkFollower = clientId in followers
                )
            }
        }
    }
}

/** The URLs one lower third can be copied as: the trigger, and the ATEM still and clip uploads. */
private class TriggerUrls(
    private val serverUrl: String,
    private val keyTarget: String,
    private val apiKey: String,
) {
    fun trigger(name: String, withKey: Boolean): String = lowerThirdTriggerUrl(serverUrl, name, withKey, apiKey)
    fun still(name: String, withKey: Boolean): String =
        atemMediaUrl(serverUrl, "still", name, if (withKey) keyTarget else "", apiKey)
    fun clip(name: String, withKey: Boolean): String =
        atemMediaUrl(serverUrl, "clip", name, if (withKey) keyTarget else "", apiKey)
}

@Composable
private fun CompanionTriggersCard(
    settings: AppSettings,
    isRunning: Boolean,
    serverUrl: String,
    copyText: (String) -> Unit,
) {
    val lowerThirdFolder = settings.streamingSettings.lowerThirdFolder
    // `isLottieFile` reads each JSON in full, so this is the folder's whole weight in
    // bytes — off the composition thread. The card is a list of trigger URLs, so a
    // frame of it empty says nothing misleading.
    val lowerThirds by produceState(emptyList<java.io.File>(), lowerThirdFolder, isRunning) {
        value = withContext(Dispatchers.IO) {
            java.io.File(lowerThirdFolder)
                .takeIf { lowerThirdFolder.isNotEmpty() && it.isDirectory }
                ?.listFiles { f -> f.extension.lowercase() == "json" && isLottieFile(f) }
                ?.sortedBy { it.nameWithoutExtension.lowercase() }
                ?.toList()
                ?: emptyList()
        }
    }
    val atemConfigured = settings.atemSettings.host.isNotBlank()

    // Default key target (1-based) for the "+ key" URLs, matching the configured key
    // type. DSK ignores M/E and uses the DSK number; both carry an explicit keytype so
    // the copied URL behaves as shown regardless of later setting changes.
    val keyTypeParam = atemKeyTypeParam(settings.atemSettings)
    val apiKeyOrBlank = effectiveApiKey(settings.serverSettings)
    val urls = TriggerUrls(serverUrl, atemKeyTarget(settings.atemSettings), apiKeyOrBlank)

    SettingsSection(title = stringResource(Res.string.companion_lt_triggers)) {
        Text(
            text = stringResource(Res.string.companion_lt_triggers_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(Res.string.companion_atem_upload_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
        )
        if (atemConfigured) {
            Text(
                text = stringResource(Res.string.companion_atem_clip_key_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when {
            !isRunning || serverUrl.isBlank() -> Text(
                text = stringResource(Res.string.companion_lt_server_off),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            lowerThirds.isEmpty() -> Text(
                text = stringResource(Res.string.companion_lt_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            else -> lowerThirds.forEach { file ->
                LowerThirdTriggerRow(
                    name = file.nameWithoutExtension,
                    urls = urls,
                    atemConfigured = atemConfigured,
                    copyText = copyText,
                )
            }
        }

        if (isRunning && serverUrl.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(Modifier.height(4.dp))

            // Key controls — only when ATEM is configured
            if (atemConfigured) {
                AtemKeySection(
                    serverUrl = serverUrl,
                    keyTypeParam = keyTypeParam,
                    apiKey = apiKeyOrBlank,
                    copyText = copyText,
                )
            }

            TakedownSection(serverUrl = serverUrl, apiKey = apiKeyOrBlank, copyText = copyText)
        }
    }
}

@Composable
private fun LowerThirdTriggerRow(
    name: String,
    urls: TriggerUrls,
    atemConfigured: Boolean,
    copyText: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CopyUrlButton(
                text = stringResource(Res.string.companion_lt_copy_key),
                tone = ButtonTone.PRIMARY,
                horizontalPadding = 10.dp,
                onClick = { copyText(urls.trigger(name, withKey = true)) },
            )
            CopyUrlButton(
                text = stringResource(Res.string.companion_lt_copy_nokey),
                tone = ButtonTone.SECONDARY,
                horizontalPadding = 10.dp,
                onClick = { copyText(urls.trigger(name, withKey = false)) },
            )
            if (atemConfigured) {
                CopyUrlButton(
                    text = stringResource(Res.string.companion_atem_still_key),
                    tone = ButtonTone.PRIMARY,
                    horizontalPadding = 10.dp,
                    onClick = { copyText(urls.still(name, withKey = true)) },
                )
                CopyUrlButton(
                    text = stringResource(Res.string.companion_atem_still_only),
                    tone = ButtonTone.SECONDARY,
                    horizontalPadding = 10.dp,
                    onClick = { copyText(urls.still(name, withKey = false)) },
                )
                CopyUrlButton(
                    text = stringResource(Res.string.companion_atem_clip_key),
                    tone = ButtonTone.PRIMARY,
                    horizontalPadding = 10.dp,
                    onClick = { copyText(urls.clip(name, withKey = true)) },
                )
                CopyUrlButton(
                    text = stringResource(Res.string.companion_atem_clip_only),
                    tone = ButtonTone.SECONDARY,
                    horizontalPadding = 10.dp,
                    onClick = { copyText(urls.clip(name, withKey = false)) },
                )
            }
        }
    }
}

@Composable
private fun AtemKeySection(serverUrl: String, keyTypeParam: String, apiKey: String, copyText: (String) -> Unit) {
    Text(
        text = stringResource(Res.string.companion_atem_key_section),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        text = stringResource(Res.string.companion_atem_key_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CopyUrlButton(
            text = stringResource(Res.string.companion_atem_key_on),
            tone = ButtonTone.PRIMARY,
            onClick = { copyText(atemKeyUrl(serverUrl, on = true, keyTypeParam = keyTypeParam, apiKey = apiKey)) },
        )
        CopyUrlButton(
            text = stringResource(Res.string.companion_atem_key_off),
            tone = ButtonTone.SECONDARY,
            onClick = { copyText(atemKeyUrl(serverUrl, on = false, keyTypeParam = keyTypeParam, apiKey = apiKey)) },
        )
    }
    Spacer(Modifier.height(4.dp))
}

/**
 * Take-down actions — available whenever the server is running. "Hide Lower Third" clears only a
 * lower third; "Clear Display" clears any output (Bible, song, lower third, …) via POST /api/clear.
 */
@Composable
private fun TakedownSection(serverUrl: String, apiKey: String, copyText: (String) -> Unit) {
    Text(
        text = stringResource(Res.string.companion_lt_takedown_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CopyUrlButton(
            text = stringResource(Res.string.companion_lt_copy_hide),
            tone = ButtonTone.ERROR_CONTAINER,
            onClick = { copyText(lowerThirdHideUrl(serverUrl, apiKey)) },
        )
        CopyUrlButton(
            text = stringResource(Res.string.tooltip_clear_display),
            tone = ButtonTone.ERROR,
            onClick = { copyText(clearDisplayUrl(serverUrl, apiKey)) },
        )
    }
}

private enum class ButtonTone { PRIMARY, SECONDARY, ERROR_CONTAINER, ERROR }

/** The small labelled button every copy-a-URL action on this tab is drawn as. */
@Composable
private fun CopyUrlButton(
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
        shape = RoundedCornerShape(6.dp),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 6.dp),
        colors = colors
    ) { Text(text, style = MaterialTheme.typography.labelSmall) }
}

@Composable
private fun ClientRow(
    clientId: String,
    label: String,
    onSetLabel: (String) -> Unit,
    statusColor: Color,
    statusLabel: String,
    onRemove: () -> Unit,
    isInstanceLinkFollower: Boolean = false
) {
    var editing by remember { mutableStateOf(false) }
    var editText by remember(label) { mutableStateOf(label) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        // ── Top row: identity + action buttons ───────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Friendly label (if set)
                if (label.isNotBlank()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
                // Raw device ID
                Text(
                    text = clientId,
                    style = if (label.isNotBlank()) MaterialTheme.typography.labelSmall
                            else MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (label.isNotBlank()) 0.6f else 1f
                    )
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
                if (isInstanceLinkFollower) {
                    Text(
                        text = stringResource(Res.string.instance_link_follower_badge),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            // Edit (pencil) button
            KeyIconButton(
                onClick = { editing = !editing; editText = label },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(Res.string.client_label_edit_tooltip),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(4.dp))
            RaisedButton(
                shape = RoundedCornerShape(6.dp),
                onClick = onRemove,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text(stringResource(Res.string.remove), style = MaterialTheme.typography.labelSmall)
            }
        }

        // ── Inline label editor (shown when editing) ──────────────────────────
        if (editing) {
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SettingsTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = {
                        Text(
                            stringResource(Res.string.client_label_placeholder),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                )
                // Confirm
                KeyIconButton(
                    onClick = {
                        onSetLabel(editText)
                        editing = false
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = stringResource(Res.string.client_label_save),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                // Cancel
                KeyIconButton(
                    onClick = { editing = false; editText = label },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(Res.string.client_label_cancel),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


@Composable
private fun ConnectionQrDialog(serverUrl: String, apiKey: String?, onDismiss: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val mainWindowState = LocalMainWindowState.current
    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, 400.dp, 500.dp),
            width = 400.dp,
            height = 500.dp
        ),
        title = stringResource(Res.string.connection_qr_title),
        resizable = false
    ) {
        AppWindowRoot(theme = if (isDark) ThemeMode.DARK else ThemeMode.LIGHT) {
            ConnectionQrDialogContent(serverUrl = serverUrl, apiKey = apiKey, onDismiss = onDismiss)
        }
    }
}

/**
 * What the connection dialog draws: the QR itself, the deep link it encodes, and a Close button.
 *
 * Separate from [ConnectionQrDialog] because that one is a [DialogWindow] — a real AWT window, which
 * throws `HeadlessException` under a headless JVM, so nothing inside it could be rendered by a test
 * or photographed for the screenshot set. The same split as `BibleCatalogBrowserDialogContent` and
 * `LocalLibraryDialogContent`.
 */
@Composable
internal fun ConnectionQrDialogContent(serverUrl: String, apiKey: String?, onDismiss: () -> Unit) {
    // Parse host and port from serverUrl (e.g. "http://192.168.1.50:8765")
    val (parsedHost, parsedPort) = remember(serverUrl) { parseServerUrlHostPort(serverUrl) }

    val qrContent = connectionQrContent(parsedHost, parsedPort, apiKey)
    val qrBitmap = remember(qrContent) { connectionQrBitmap(qrContent) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(300.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = qrContent,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            RaisedButton(shape = RoundedCornerShape(6.dp), onClick = onDismiss) {
                Text(stringResource(Res.string.close), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ── URL building ────────────────────────────────────────────────────────────────
// Pulled out of the composables above so the trigger URLs an operator copies into a Stream Deck can
// be tested directly. They were local functions closing over the settings, reachable only by
// clicking a Copy button — and that writes to the system clipboard, which throws HeadlessException
// under the test JVM, so none of this logic could be exercised at all. The composables call these
// and nothing else builds URLs.

/** The API key to append to companion URLs, or blank when protection is off or no key is set. */
internal fun effectiveApiKey(server: ServerSettings): String =
    if (server.apiKeyEnabled && server.apiKey.isNotBlank()) server.apiKey else ""

/**
 * The `?a=b&c=d` tail shared by the ATEM URLs: [extra] first if present, then the API key if there
 * is one. Returns an empty string when there is nothing to add, so callers can append it blindly.
 */
internal fun apiQueryString(extra: String = "", apiKey: String = ""): String {
    val params = buildList {
        if (extra.isNotEmpty()) add(extra)
        if (apiKey.isNotEmpty()) add("apiKey=" + URLEncoder.encode(apiKey, "UTF-8"))
    }
    return if (params.isEmpty()) "" else "?" + params.joinToString("&")
}

/** A lower-third name as it appears in a URL path — spaces as `%20` rather than `+`. */
internal fun encodeUrlPathSegment(name: String): String =
    URLEncoder.encode(name, "UTF-8").replace("+", "%20")

/**
 * The URL that plays lower third [name]. Running defaults to keying, so the *unkeyed* variant is the
 * one that carries `key=0`.
 */
internal fun lowerThirdTriggerUrl(
    serverUrl: String,
    name: String,
    withKey: Boolean,
    apiKey: String = "",
): String {
    val params = buildList {
        if (!withKey) add("key=0")
        if (apiKey.isNotEmpty()) add("apiKey=" + URLEncoder.encode(apiKey, "UTF-8"))
    }
    val query = if (params.isEmpty()) "" else "?" + params.joinToString("&")
    return "$serverUrl/api/lowerthirds/${encodeUrlPathSegment(name)}/run$query"
}

/** The URL that uploads [name] to the ATEM as a [kind] ("still" or "clip"). */
internal fun atemMediaUrl(
    serverUrl: String,
    kind: String,
    name: String,
    keyTarget: String,
    apiKey: String = "",
): String = "$serverUrl/api/atem/$kind/${encodeUrlPathSegment(name)}" + apiQueryString(keyTarget, apiKey)

/** `keytype=dsk` or `keytype=usk`, per the configured key type. */
internal fun atemKeyTypeParam(atem: AtemSettings): String =
    if (atem.useDownstreamKey) "keytype=dsk" else "keytype=usk"

/**
 * The default key target for the "+ key" URLs, 1-based to match the switcher's own numbering. A DSK
 * ignores the M/E and names only the downstream key; an upstream key names both.
 */
internal fun atemKeyTarget(atem: AtemSettings): String =
    if (atem.useDownstreamKey) {
        "keytype=dsk&key=${atem.dskIndex + 1}"
    } else {
        "keytype=usk&me=${atem.keyMixEffect + 1}&key=${atem.keyIndex + 1}"
    }

/**
 * Encodes [content] as a QR bitmap, or null when it cannot be encoded.
 *
 * Null rather than an exception is the point: this is called from inside the connection dialog's
 * composition, so a throw would take the dialog down instead of merely leaving it without a code —
 * and ZXing does throw on content it cannot represent, an empty string included.
 *
 * Error correction is left at M and the quiet-zone margin at 1: a phone camera a metre from a laptop
 * screen has a clean, well-lit target, and a wider margin would shrink the modules inside a fixed
 * 512px square for no gain.
 */
internal fun connectionQrBitmap(content: String, sizePx: Int = 512): ImageBitmap? = try {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val img = BufferedImage(matrix.width, matrix.height, BufferedImage.TYPE_INT_ARGB)
    val black = 0xFF000000.toInt()
    val white = 0xFFFFFFFF.toInt()
    for (y in 0 until matrix.height) {
        for (x in 0 until matrix.width) {
            img.setRGB(x, y, if (matrix.get(x, y)) black else white)
        }
    }
    SkiaImage.makeFromEncoded(
        ByteArrayOutputStream().also { ImageIO.write(img, "PNG", it) }.toByteArray()
    ).toComposeImageBitmap()
} catch (_: Exception) {
    null
}

/** The `churchpresenter://connect` deep link the connection QR encodes. */
internal fun connectionQrContent(host: String, port: String, apiKey: String?): String = buildString {
    append("churchpresenter://connect?host=$host")
    if (port.isNotBlank()) append("&port=$port")
    if (!apiKey.isNullOrBlank()) append("&apikey=$apiKey")
}

/**
 * Splits a server URL into the host and port the connection QR encodes.
 *
 * Anything unparseable falls back to using the whole string as the host and no port, so a hand-typed
 * host override still produces a scannable code rather than throwing inside the dialog.
 */
internal fun parseServerUrlHostPort(serverUrl: String): Pair<String, String> = try {
    val parsed = java.net.URI.create(serverUrl).toURL()
    val host = parsed.host ?: serverUrl
    val port = if (parsed.port != -1) parsed.port.toString() else ""
    host to port
} catch (_: Exception) {
    serverUrl to ""
}

/** The URL that turns the configured ATEM key on ([on]) or off. */
internal fun atemKeyUrl(serverUrl: String, on: Boolean, keyTypeParam: String, apiKey: String = ""): String =
    "$serverUrl/api/atem/key/${if (on) "on" else "off"}" + apiQueryString(keyTypeParam, apiKey)

/** The URL that takes the current lower third down, leaving other output alone. */
internal fun lowerThirdHideUrl(serverUrl: String, apiKey: String = ""): String =
    "$serverUrl/api/lowerthirds/hide" + apiQueryString(apiKey = apiKey)

/** The URL that clears every output — Bible, song, lower third and the rest. */
internal fun clearDisplayUrl(serverUrl: String, apiKey: String = ""): String =
    "$serverUrl/api/clear" + apiQueryString(apiKey = apiKey)
