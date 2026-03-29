package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.rememberDialogState
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
import churchpresenter.composeapp.generated.resources.allowed_clients_description
import churchpresenter.composeapp.generated.resources.api_key_hint
import churchpresenter.composeapp.generated.resources.api_key_label
import churchpresenter.composeapp.generated.resources.api_key_protection
import churchpresenter.composeapp.generated.resources.blocked_clients
import churchpresenter.composeapp.generated.resources.blocked_clients_description
import churchpresenter.composeapp.generated.resources.client_label_cancel
import churchpresenter.composeapp.generated.resources.client_label_edit_tooltip
import churchpresenter.composeapp.generated.resources.client_label_placeholder
import churchpresenter.composeapp.generated.resources.client_label_save
import churchpresenter.composeapp.generated.resources.companion_server
import churchpresenter.composeapp.generated.resources.api_key_qr_title
import churchpresenter.composeapp.generated.resources.copy_api_key
import churchpresenter.composeapp.generated.resources.show_qr_code
import churchpresenter.composeapp.generated.resources.copy_url
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
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.RemoteClientManager
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.app.churchpresenter.utils.Constants
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
    val clipboardManager = LocalClipboardManager.current

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(5.dp)
    ) {
        // ── Settings column ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                // ── Header ────────────────────────────────────────────────────
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
                            modifier = Modifier.width(100.dp),
                            singleLine = true,
                            enabled = !isRunning,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            placeholder = { Text(stringResource(Res.string.server_port_hint)) }
                        )
                        if (isRunning) {
                            Button(
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
                        OutlinedTextField(
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
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
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

                // ── Server URL + Copy in one row (shown when running) ─────────
                if (isRunning && serverUrl.isNotBlank()) {
                    SettingRow(label = stringResource(Res.string.server_url_label)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = serverUrl,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            Button(
                                onClick = { clipboardManager.setText(AnnotatedString(serverUrl)) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Text(stringResource(Res.string.copy_url), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

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

                // ── API Key field + Generate + Copy all in one row ────────────
                if (settings.serverSettings.apiKeyEnabled) {
                    SettingRow(label = stringResource(Res.string.api_key_label)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = apiKeyText,
                                onValueChange = { v ->
                                    apiKeyText = v
                                    onSettingsChange { s ->
                                        s.copy(serverSettings = s.serverSettings.copy(apiKey = v))
                                    }
                                },
                                modifier = Modifier.width(350.dp),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                placeholder = {
                                    Text(
                                        stringResource(Res.string.api_key_hint),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            )
                            Button(
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
                                onClick = { clipboardManager.setText(AnnotatedString(apiKeyText)) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Text(stringResource(Res.string.copy_api_key), style = MaterialTheme.typography.labelSmall)
                            }
                            var showQrDialog by remember { mutableStateOf(false) }
                            Button(
                                onClick = { showQrDialog = true },
                                enabled = apiKeyText.isNotBlank(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            ) {
                                Text(stringResource(Res.string.show_qr_code), style = MaterialTheme.typography.labelSmall)
                            }
                            if (showQrDialog) {
                                ApiKeyQrDialog(apiKey = apiKeyText, onDismiss = { showQrDialog = false })
                            }
                        }
                    }
                }

                HorizontalDivider()

                // ── Remote Clients ────────────────────────────────────────────
                Text(
                    text = stringResource(Res.string.remote_clients_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
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
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
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
private fun SettingRow(
    label: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        content()
    }
}

@Composable
private fun ApiKeyQrDialog(apiKey: String, onDismiss: () -> Unit) {
    val qrBitmap = remember(apiKey) {
        try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1
            )
            val matrix = QRCodeWriter().encode(apiKey, BarcodeFormat.QR_CODE, 512, 512, hints)
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
    Dialog(
        onCloseRequest = onDismiss,
        state = rememberDialogState(width = 380.dp, height = 440.dp),
        title = stringResource(Res.string.api_key_qr_title),
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(300.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Text(
                text = apiKey,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface
            )
            Button(onClick = onDismiss) {
                Text("Close", style = MaterialTheme.typography.labelSmall)
            }
        }
        }
        }
    }
}
