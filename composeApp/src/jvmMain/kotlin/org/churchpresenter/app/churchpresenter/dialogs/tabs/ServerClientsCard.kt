package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import org.churchpresenter.theme.AppShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.SettingsTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.churchpresenter.app.churchpresenter.utils.AppWindowRoot
import org.churchpresenter.theme.ThemeMode
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import org.churchpresenter.sharedui.utils.centeredOnMainWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.allowed_clients
import org.churchpresenter.strings.generated.resources.allowed_clients_description
import org.churchpresenter.strings.generated.resources.blocked_clients
import org.churchpresenter.strings.generated.resources.blocked_clients_description
import org.churchpresenter.strings.generated.resources.client_label_cancel
import org.churchpresenter.strings.generated.resources.client_label_edit_tooltip
import org.churchpresenter.strings.generated.resources.client_label_placeholder
import org.churchpresenter.strings.generated.resources.save
import org.churchpresenter.strings.generated.resources.close
import org.churchpresenter.strings.generated.resources.connection_qr_title
import org.churchpresenter.strings.generated.resources.no_allowed_clients
import org.churchpresenter.strings.generated.resources.no_blocked_clients
import org.churchpresenter.strings.generated.resources.instance_link_follower_badge
import org.churchpresenter.strings.generated.resources.instance_link_followers_connected_count
import org.churchpresenter.strings.generated.resources.remote_clients_description
import org.churchpresenter.strings.generated.resources.remote_clients_title
import org.churchpresenter.strings.generated.resources.remove
import org.churchpresenter.sharedui.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.data.RemoteClientManager
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RemoteClientsCard(companionServer: CompanionServer, remoteClientManager: RemoteClientManager) {
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
                AppShape(4.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        // ── Top row: identity + action buttons ───────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ClientIdentity(
                clientId, label, statusColor, statusLabel, isInstanceLinkFollower,
                modifier = Modifier.weight(1f),
            )
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
                shape = AppShape(6.dp),
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
                        contentDescription = stringResource(Res.string.save),
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
internal fun ConnectionQrDialog(serverUrl: String, apiKey: String?, onDismiss: () -> Unit) {
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
                    .background(MaterialTheme.colorScheme.surfaceVariant, AppShape(4.dp))
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
            RaisedButton(shape = AppShape(6.dp), onClick = onDismiss) {
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

/** Who a connected client is: its label if it has one, its device id, its status, and whether it is a follower. */
@Composable
private fun ClientIdentity(
    clientId: String,
    label: String,
    statusColor: Color,
    statusLabel: String,
    isInstanceLinkFollower: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
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
}
