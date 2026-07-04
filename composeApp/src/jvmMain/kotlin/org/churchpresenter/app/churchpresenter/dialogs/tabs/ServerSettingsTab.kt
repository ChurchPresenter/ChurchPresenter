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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.app.churchpresenter.composables.SettingsTextField
import androidx.compose.material3.Switch
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
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
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
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.RemoteClientManager
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.isLottieFile
import org.jetbrains.compose.resources.stringResource
import java.util.UUID

@Composable
fun ServerSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    companionServer: CompanionServer,
    remoteClientManager: RemoteClientManager
) {
    val isRunning by companionServer.isRunning.collectAsState()
    val serverUrl by companionServer.serverUrl.collectAsState()
    val copyText: (String) -> Unit = { text ->
        java.awt.Toolkit.getDefaultToolkit().systemClipboard
            .setContents(java.awt.datatransfer.StringSelection(text), null)
    }

    var portText by remember(settings.serverSettings.port) {
        mutableStateOf(settings.serverSettings.port.toString())
    }
    var hostText by remember(settings.serverSettings.serverHost) {
        mutableStateOf(settings.serverSettings.serverHost)
    }
    var apiKeyText by remember(settings.serverSettings.apiKey) {
        mutableStateOf(settings.serverSettings.apiKey)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Card 1: Server ────────────────────────────────────────────────
            SettingsSection(title = stringResource(Res.string.companion_server)) {
                Text(
                    text = stringResource(Res.string.server_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                // ── Enable toggle + status in one row ─────────────────────────
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
                    Switch(
                        checked = isRunning,
                        onCheckedChange = { enable ->
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
                        }
                    )
                    Text(
                        text = if (isRunning) stringResource(Res.string.server_running)
                               else stringResource(Res.string.server_stopped),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isRunning) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()

                // ── Port + note/Restart in one row ────────────────────────────
                SettingRow(label = stringResource(Res.string.server_port)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsTextField(
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
                            modifier = Modifier.width(100.dp),
                            singleLine = true,
                            enabled = !isRunning,
                            placeholder = { Text(stringResource(Res.string.server_port_hint)) }
                        )
                        if (isRunning) {
                            Button(
                                shape = RoundedCornerShape(6.dp),
                                onClick = {
                                    val port = portText.toIntOrNull() ?: Constants.SERVER_DEFAULT_PORT
                                    companionServer.stop()
                                    companionServer.start(port, hostText.trim())
                                },
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

                // ── Host Override ─────────────────────────────────────────────
                SettingRow(label = stringResource(Res.string.server_host_label)) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SettingsTextField(
                            value = hostText,
                            onValueChange = { v ->
                                hostText = v
                                onSettingsChange { s ->
                                    s.copy(serverSettings = s.serverSettings.copy(serverHost = v.trim()))
                                }
                            },
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

                // ── Server URL + Copy + QR in one row (shown when running) ───
                if (isRunning && serverUrl.isNotBlank()) {
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
                            Button(
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
                            apiKey = if (settings.serverSettings.apiKeyEnabled && apiKeyText.isNotBlank()) apiKeyText else null,
                            onDismiss = { showConnectionQrDialog = false }
                        )
                    }
                } // end if (isRunning && serverUrl.isNotBlank())

                HorizontalDivider()

                // ── API Key protection toggle ─────────────────────────────────
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
                Text(
                    text = stringResource(Res.string.browser_source_note_in_server_settings),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ── API Key field + Generate + Copy all in one row ────────────
                if (settings.serverSettings.apiKeyEnabled) {
                    SettingRow(label = stringResource(Res.string.api_key_label)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SettingsTextField(
                                value = apiKeyText,
                                onValueChange = { v ->
                                    apiKeyText = v
                                    onSettingsChange { s ->
                                        s.copy(serverSettings = s.serverSettings.copy(apiKey = v))
                                    }
                                },
                                modifier = Modifier.width(350.dp),
                                singleLine = true,
                                placeholder = {
                                    Text(
                                        stringResource(Res.string.api_key_hint),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            )
                            Button(
                                shape = RoundedCornerShape(6.dp),
                                onClick = {
                                    val newKey = UUID.randomUUID().toString().replace("-", "")
                                    apiKeyText = newKey
                                    onSettingsChange { s ->
                                        s.copy(serverSettings = s.serverSettings.copy(apiKey = newKey))
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Text(stringResource(Res.string.generate_api_key), style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                shape = RoundedCornerShape(6.dp),
                                onClick = { copyText(apiKeyText) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Text(stringResource(Res.string.copy_api_key), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // ── Allow File Upload toggle ──────────────────────────────────
                SettingRow(label = stringResource(Res.string.allow_file_upload)) {
                    Switch(
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
            }

            // ── Card 2: Remote Clients ────────────────────────────────────────
            SettingsSection(title = stringResource(Res.string.remote_clients_title)) {
                Text(
                    text = stringResource(Res.string.remote_clients_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(4.dp))

                // ── Allowed clients list ──────────────────────────────────────
                Text(
                    text = stringResource(Res.string.allowed_clients),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(Res.string.allowed_clients_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val allowedClients = remoteClientManager.allowedClients.toList().sorted()
                if (allowedClients.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.no_allowed_clients),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    allowedClients.forEach { clientId ->
                        key(clientId) {
                            ClientRow(
                                clientId = clientId,
                                label = remoteClientManager.getLabel(clientId),
                                onSetLabel = { remoteClientManager.setLabel(clientId, it) },
                                statusColor = MaterialTheme.colorScheme.primary,
                                statusLabel = stringResource(Res.string.allowed_clients),
                                onRemove = { remoteClientManager.removeAllowed(clientId) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // ── Blocked clients list ──────────────────────────────────────
                Text(
                    text = stringResource(Res.string.blocked_clients),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(Res.string.blocked_clients_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val blockedClients = remoteClientManager.blockedClients.toList().sorted()
                if (blockedClients.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.no_blocked_clients),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    blockedClients.forEach { clientId ->
                        key(clientId) {
                            ClientRow(
                                clientId = clientId,
                                label = remoteClientManager.getLabel(clientId),
                                onSetLabel = { remoteClientManager.setLabel(clientId, it) },
                                statusColor = MaterialTheme.colorScheme.error,
                                statusLabel = stringResource(Res.string.blocked_clients),
                                onRemove = { remoteClientManager.removeBlocked(clientId) }
                            )
                        }
                    }
                }
            }

            // ── Card: Lower Third Triggers (Bitfocus Companion) ───────────────
            run {
                val lowerThirdFolder = settings.streamingSettings.lowerThirdFolder
                val lowerThirds = remember(lowerThirdFolder, isRunning) {
                    java.io.File(lowerThirdFolder)
                        .takeIf { lowerThirdFolder.isNotEmpty() && it.isDirectory }
                        ?.listFiles { f -> f.extension.lowercase() == "json" && isLottieFile(f) }
                        ?.sortedBy { it.nameWithoutExtension.lowercase() }
                        ?.toList()
                        ?: emptyList()
                }
                val atemConfigured = settings.atemSettings.host.isNotBlank()

                fun apiQuery(extra: String = ""): String {
                    val params = buildList {
                        if (extra.isNotEmpty()) add(extra)
                        if (settings.serverSettings.apiKeyEnabled && settings.serverSettings.apiKey.isNotBlank())
                            add("apiKey=" + java.net.URLEncoder.encode(settings.serverSettings.apiKey, "UTF-8"))
                    }
                    return if (params.isEmpty()) "" else "?" + params.joinToString("&")
                }

                // Default key target (1-based) for the "+ key" URLs, matching the configured key
                // type. DSK ignores M/E and uses the DSK number; both carry an explicit keytype so
                // the copied URL behaves as shown regardless of later setting changes.
                val useDsk = settings.atemSettings.useDownstreamKey
                val keyTypeParam = if (useDsk) "keytype=dsk" else "keytype=usk"
                val keyTarget = if (useDsk)
                    "keytype=dsk&key=${settings.atemSettings.dskIndex + 1}"
                else
                    "keytype=usk&me=${settings.atemSettings.keyMixEffect + 1}&key=${settings.atemSettings.keyIndex + 1}"

                fun triggerUrl(name: String, withKey: Boolean): String {
                    val encoded = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
                    val params = buildList {
                        if (!withKey) add("key=0")   // run defaults to keying; key=0 skips it
                        if (settings.serverSettings.apiKeyEnabled && settings.serverSettings.apiKey.isNotBlank())
                            add("apiKey=" + java.net.URLEncoder.encode(settings.serverSettings.apiKey, "UTF-8"))
                    }
                    val query = if (params.isEmpty()) "" else "?" + params.joinToString("&")
                    return "$serverUrl/api/lowerthirds/$encoded/run$query"
                }

                fun stillUrl(name: String, withKey: Boolean): String {
                    val encoded = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
                    return "$serverUrl/api/atem/still/$encoded${apiQuery(if (withKey) keyTarget else "")}"
                }

                fun clipUrl(name: String, withKey: Boolean): String {
                    val encoded = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
                    return "$serverUrl/api/atem/clip/$encoded${apiQuery(if (withKey) keyTarget else "")}"
                }

                fun keyOnUrl()  = "$serverUrl/api/atem/key/on${apiQuery(keyTypeParam)}"
                fun keyOffUrl() = "$serverUrl/api/atem/key/off${apiQuery(keyTypeParam)}"

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
                            val name = file.nameWithoutExtension
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
                                    Button(
                                        shape = RoundedCornerShape(6.dp),
                                        onClick = { copyText(triggerUrl(name, withKey = true)) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    ) { Text(stringResource(Res.string.companion_lt_copy_key), style = MaterialTheme.typography.labelSmall) }
                                    Button(
                                        shape = RoundedCornerShape(6.dp),
                                        onClick = { copyText(triggerUrl(name, withKey = false)) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    ) { Text(stringResource(Res.string.companion_lt_copy_nokey), style = MaterialTheme.typography.labelSmall) }
                                    if (atemConfigured) {
                                        Button(
                                            shape = RoundedCornerShape(6.dp),
                                            onClick = { copyText(stillUrl(name, withKey = true)) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        ) { Text(stringResource(Res.string.companion_atem_still_key), style = MaterialTheme.typography.labelSmall) }
                                        Button(
                                            shape = RoundedCornerShape(6.dp),
                                            onClick = { copyText(stillUrl(name, withKey = false)) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        ) { Text(stringResource(Res.string.companion_atem_still_only), style = MaterialTheme.typography.labelSmall) }
                                        Button(
                                            shape = RoundedCornerShape(6.dp),
                                            onClick = { copyText(clipUrl(name, withKey = true)) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        ) { Text(stringResource(Res.string.companion_atem_clip_key), style = MaterialTheme.typography.labelSmall) }
                                        Button(
                                            shape = RoundedCornerShape(6.dp),
                                            onClick = { copyText(clipUrl(name, withKey = false)) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        ) { Text(stringResource(Res.string.companion_atem_clip_only), style = MaterialTheme.typography.labelSmall) }
                                    }
                                }
                            }
                        }
                    }

                    if (isRunning && serverUrl.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(Modifier.height(4.dp))

                        // Key controls — only when ATEM is configured
                        if (atemConfigured) {
                            Text(
                                text = stringResource(Res.string.companion_atem_key_section),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(Res.string.companion_atem_key_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    shape = RoundedCornerShape(6.dp),
                                    onClick = { copyText(keyOnUrl()) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) { Text(stringResource(Res.string.companion_atem_key_on), style = MaterialTheme.typography.labelSmall) }
                                Button(
                                    shape = RoundedCornerShape(6.dp),
                                    onClick = { copyText(keyOffUrl()) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) { Text(stringResource(Res.string.companion_atem_key_off), style = MaterialTheme.typography.labelSmall) }
                            }
                            Spacer(Modifier.height(4.dp))
                        }

                        // Take-down actions — available whenever the server is running.
                        // "Hide Lower Third" clears only a lower third; "Clear Display" clears any
                        // output (Bible, song, lower third, …) via POST /api/clear.
                        Text(
                            text = stringResource(Res.string.companion_lt_takedown_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                shape = RoundedCornerShape(6.dp),
                                onClick = { copyText("$serverUrl/api/lowerthirds/hide${apiQuery()}") },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) { Text(stringResource(Res.string.companion_lt_copy_hide), style = MaterialTheme.typography.labelSmall) }
                            Button(
                                shape = RoundedCornerShape(6.dp),
                                onClick = { copyText("$serverUrl/api/clear${apiQuery()}") },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) { Text(stringResource(Res.string.tooltip_clear_display), style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientRow(
    clientId: String,
    label: String,
    onSetLabel: (String) -> Unit,
    statusColor: Color,
    statusLabel: String,
    onRemove: () -> Unit
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
            }
            Spacer(Modifier.width(8.dp))
            // Edit (pencil) button
            IconButton(
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
            Button(
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
                IconButton(
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
                IconButton(
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
    // Parse host and port from serverUrl (e.g. "http://192.168.1.50:8765")
    val (parsedHost, parsedPort) = remember(serverUrl) {
        try {
            val u = java.net.URI.create(serverUrl).toURL()
            val host = u.host ?: serverUrl
            val port = if (u.port != -1) u.port.toString() else ""
            host to port
        } catch (_: Exception) {
            serverUrl to ""
        }
    }

    val qrContent = buildString {
        append("churchpresenter://connect?host=$parsedHost")
        if (parsedPort.isNotBlank()) append("&port=$parsedPort")
        if (!apiKey.isNullOrBlank()) append("&apikey=$apiKey")
    }
    val qrBitmap = remember(qrContent) {
        try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1
            )
            val matrix = QRCodeWriter().encode(qrContent, BarcodeFormat.QR_CODE, 512, 512, hints)
            val w = matrix.width
            val h = matrix.height
            val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            val black = 0xFF000000.toInt()
            val white = 0xFFFFFFFF.toInt()
            for (y in 0 until h) {
                for (x in 0 until w) {
                    img.setRGB(x, y, if (matrix.get(x, y)) black else white)
                }
            }
            SkiaImage.makeFromEncoded(
                ByteArrayOutputStream().also { ImageIO.write(img, "PNG", it) }.toByteArray()
            ).toComposeImageBitmap()
        } catch (_: Exception) { null }
    }

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
        AppThemeWrapper(theme = if (isDark) ThemeMode.DARK else ThemeMode.LIGHT) {
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
                    Button(shape = RoundedCornerShape(6.dp), onClick = onDismiss) {
                        Text(stringResource(Res.string.close), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
