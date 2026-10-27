package org.churchpresenter.serverui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.server.DevicePermission
import org.churchpresenter.server.DevicePermissions
import org.churchpresenter.sharedui.composables.TooltipIconButton
import org.churchpresenter.sharedui.utils.AppWindowRoot
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.allowed_clients_description
import org.churchpresenter.strings.generated.resources.blocked_clients_description
import org.churchpresenter.strings.generated.resources.client_label_cancel
import org.churchpresenter.strings.generated.resources.client_label_edit_tooltip
import org.churchpresenter.strings.generated.resources.client_label_placeholder
import org.churchpresenter.strings.generated.resources.close
import org.churchpresenter.strings.generated.resources.connection_qr_title
import org.churchpresenter.strings.generated.resources.instance_link_follower_badge
import org.churchpresenter.strings.generated.resources.instance_link_followers_connected_count
import org.churchpresenter.strings.generated.resources.no_allowed_clients
import org.churchpresenter.strings.generated.resources.no_blocked_clients
import org.churchpresenter.strings.generated.resources.remote_client_permissions
import org.churchpresenter.strings.generated.resources.remote_clients_description
import org.churchpresenter.strings.generated.resources.remote_clients_tab_allowed
import org.churchpresenter.strings.generated.resources.remote_clients_tab_blocked
import org.churchpresenter.strings.generated.resources.remote_clients_title
import org.churchpresenter.strings.generated.resources.remote_clients_unnamed
import org.churchpresenter.strings.generated.resources.remove
import org.churchpresenter.strings.generated.resources.save
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.components.SettingsTextField
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.sharedui.utils.rememberCenteredDialogState

@Composable
internal fun RemoteClientsCard(companionServer: CompanionServer, remoteClientManager: RemoteClientManager) {
    val followers by companionServer.connectedInstanceLinkFollowers.collectAsState()
    var showBlocked by remember { mutableStateOf(false) }
    val allowed = remoteClientManager.allowedClients.toList().sorted()
    val blocked = remoteClientManager.blockedClients.toList().sorted()
    ServerSettingsCard(
        icon = Icons.Outlined.PhoneIphone,
        tint = MaterialTheme.semantic.success,
        title = stringResource(Res.string.remote_clients_title),
        description = stringResource(Res.string.remote_clients_description),
    ) {
        if (followers.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.instance_link_followers_connected_count, followers.size),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp),
            )
        }
        Row(modifier = Modifier.padding(horizontal = 12.dp).padding(top = 4.dp)) {
            CardTab(
                icon = Icons.Outlined.Check,
                iconTint = MaterialTheme.semantic.success,
                label = stringResource(Res.string.remote_clients_tab_allowed),
                count = allowed.size,
                selected = !showBlocked,
                onSelect = { showBlocked = false },
            )
            CardTab(
                icon = Icons.Outlined.Block,
                iconTint = MaterialTheme.colorScheme.error,
                label = stringResource(Res.string.remote_clients_tab_blocked),
                count = blocked.size,
                selected = showBlocked,
                onSelect = { showBlocked = true },
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        CardBlock {
            if (showBlocked) {
                ClientList(
                    description = stringResource(Res.string.blocked_clients_description),
                    emptyText = stringResource(Res.string.no_blocked_clients),
                    clients = blocked,
                    blocked = true,
                    remoteClientManager = remoteClientManager,
                    followers = followers,
                    onRemove = remoteClientManager::removeBlocked,
                )
            } else {
                ClientList(
                    description = stringResource(Res.string.allowed_clients_description),
                    emptyText = stringResource(Res.string.no_allowed_clients),
                    clients = allowed,
                    blocked = false,
                    remoteClientManager = remoteClientManager,
                    followers = followers,
                    onRemove = remoteClientManager::removeAllowed,
                )
            }
        }
    }
}

@Composable
private fun ClientList(
    description: String,
    emptyText: String,
    clients: List<String>,
    blocked: Boolean,
    remoteClientManager: RemoteClientManager,
    followers: Set<String>,
    onRemove: (String) -> Unit,
) {
    DimText(description, small = true)
    if (clients.isEmpty()) {
        EmptyNote(emptyText)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            clients.forEach { clientId ->
                key(clientId) {
                    ClientRow(
                        clientId = clientId,
                        label = remoteClientManager.getLabel(clientId),
                        onSetLabel = { remoteClientManager.setLabel(clientId, it) },
                        blocked = blocked,
                        onRemove = { onRemove(clientId) },
                        isInstanceLinkFollower = clientId in followers,
                        // A blocked device may do nothing, so only an allowed one has permissions.
                        permissions = if (blocked) null else remoteClientManager.permissionsFor(clientId),
                        onPermissionChange = { permission, allowed ->
                            remoteClientManager.setPermission(clientId, permission, allowed)
                        },
                    )
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
    blocked: Boolean,
    onRemove: () -> Unit,
    isInstanceLinkFollower: Boolean = false,
    permissions: DevicePermissions? = null,
    onPermissionChange: (DevicePermission, Boolean) -> Unit = { _, _ -> },
) {
    var showPermissions by remember { mutableStateOf(false) }
    val shape = AppShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        ClientRowLine(
            clientId = clientId,
            label = label,
            onSetLabel = onSetLabel,
            blocked = blocked,
            onRemove = onRemove,
            isInstanceLinkFollower = isInstanceLinkFollower,
            permissions = permissions,
            showPermissions = showPermissions,
            onTogglePermissions = { showPermissions = !showPermissions },
        )
        if (permissions != null && showPermissions) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ClientPermissionsPanel(permissions, onPermissionChange)
        }
    }
}

/** The device's own line: who it is, and its buttons -- or its name being edited. */
@Composable
private fun ClientRowLine(
    clientId: String,
    label: String,
    onSetLabel: (String) -> Unit,
    blocked: Boolean,
    onRemove: () -> Unit,
    isInstanceLinkFollower: Boolean,
    permissions: DevicePermissions?,
    showPermissions: Boolean,
    onTogglePermissions: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var editText by remember(label) { mutableStateOf(label) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        IconChip(
            Icons.Outlined.PhoneIphone,
            if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.semantic.success,
            size = 30.dp,
            iconSize = 15.dp,
        )
        if (editing) {
            SettingsTextField(
                value = editText,
                onValueChange = { editText = it },
                modifier = Modifier.weight(1f),
                fillWidth = true,
                singleLine = true,
                placeholder = {
                    Text(
                        stringResource(Res.string.client_label_placeholder),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
            RowIconButton(Icons.Filled.Check, stringResource(Res.string.save), MaterialTheme.colorScheme.primary) {
                onSetLabel(editText)
                editing = false
            }
            RowIconButton(Icons.Filled.Close, stringResource(Res.string.client_label_cancel)) {
                editing = false
                editText = label
            }
            // The pencil stays a toggle: pressed again, it closes the editor without saving.
            RowIconButton(Icons.Outlined.Edit, stringResource(Res.string.client_label_edit_tooltip)) {
                editing = false
                editText = label
            }
        } else {
            ClientIdentity(clientId, label, isInstanceLinkFollower, modifier = Modifier.weight(1f))
            if (permissions != null) {
                PermissionsSummary(permissions)
                val tint = if (showPermissions) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                RowIconButton(Icons.Outlined.Tune, stringResource(Res.string.remote_client_permissions), tint) {
                    onTogglePermissions()
                }
            }
            RowIconButton(Icons.Outlined.Edit, stringResource(Res.string.client_label_edit_tooltip)) {
                editing = true
                editText = label
            }
            RemoveButton(stringResource(Res.string.remove), onClick = onRemove)
        }
    }
}

/** A small flat icon button inside a row, named for a screen reader and shown as a tooltip. */
@Composable
private fun RowIconButton(
    icon: ImageVector,
    name: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    TooltipIconButton(
        painter = rememberVectorPainter(icon),
        text = name,
        onClick = onClick,
        iconSize = 16.dp,
        buttonSize = 30.dp,
        iconTint = tint,
    )
}

@Composable
internal fun ConnectionQrDialog(serverUrl: String, apiKey: String?, onDismiss: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val mainWindowState = LocalMainWindowState.current
    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberCenteredDialogState(mainWindowState, 400.dp, 500.dp),
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
fun ConnectionQrDialogContent(serverUrl: String, apiKey: String?, onDismiss: () -> Unit) {
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

/** Who a connected client is: its label (or that it has none), its device id, and whether it is a follower. */
@Composable
private fun ClientIdentity(
    clientId: String,
    label: String,
    isInstanceLinkFollower: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = label.ifBlank { stringResource(Res.string.remote_clients_unnamed) },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (label.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isInstanceLinkFollower) {
                Pill(
                    stringResource(Res.string.instance_link_follower_badge),
                    MaterialTheme.colorScheme.primary,
                    round = false,
                )
            }
        }
        Text(
            text = clientId,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
