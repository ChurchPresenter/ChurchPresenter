package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.companion_satellite_add_connection
import churchpresenter.composeapp.generated.resources.companion_satellite_autoconnect
import churchpresenter.composeapp.generated.resources.companion_satellite_bitmap_size
import churchpresenter.composeapp.generated.resources.companion_satellite_columns
import churchpresenter.composeapp.generated.resources.companion_satellite_companion_config_note
import churchpresenter.composeapp.generated.resources.companion_satellite_connect
import churchpresenter.composeapp.generated.resources.companion_satellite_connection_name
import churchpresenter.composeapp.generated.resources.companion_satellite_description
import churchpresenter.composeapp.generated.resources.companion_satellite_device_id
import churchpresenter.composeapp.generated.resources.companion_satellite_device_id_hint
import churchpresenter.composeapp.generated.resources.companion_satellite_disconnect
import churchpresenter.composeapp.generated.resources.companion_satellite_host
import churchpresenter.composeapp.generated.resources.companion_satellite_host_hint
import churchpresenter.composeapp.generated.resources.companion_satellite_left_sidebar_device_id
import churchpresenter.composeapp.generated.resources.companion_satellite_max_button_size
import churchpresenter.composeapp.generated.resources.companion_satellite_max_button_size_hint
import churchpresenter.composeapp.generated.resources.companion_satellite_product_name
import churchpresenter.composeapp.generated.resources.companion_satellite_right_sidebar_device_id
import churchpresenter.composeapp.generated.resources.companion_satellite_reconnect_delay
import churchpresenter.composeapp.generated.resources.companion_satellite_remove_connection
import churchpresenter.composeapp.generated.resources.companion_satellite_rows
import churchpresenter.composeapp.generated.resources.companion_satellite_settings
import churchpresenter.composeapp.generated.resources.companion_satellite_show_in
import churchpresenter.composeapp.generated.resources.companion_satellite_show_in_left_sidebar
import churchpresenter.composeapp.generated.resources.companion_satellite_show_in_right_sidebar
import churchpresenter.composeapp.generated.resources.companion_satellite_show_in_tab
import churchpresenter.composeapp.generated.resources.companion_satellite_status_connecting
import churchpresenter.composeapp.generated.resources.companion_satellite_status_disconnected
import churchpresenter.composeapp.generated.resources.companion_satellite_status_error
import companionsatellite.CompanionConnectionStatus
import org.churchpresenter.app.churchpresenter.composables.SettingRow
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.composables.SettingsTextField
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.CompanionSatelliteSettings
import org.churchpresenter.app.churchpresenter.models.CompanionConnectionUiState
import org.churchpresenter.app.churchpresenter.models.CompanionSurfacePlacement
import org.churchpresenter.app.churchpresenter.models.CompanionSurfaceSlot
import org.churchpresenter.app.churchpresenter.viewmodel.CompanionSatelliteViewModel
import org.jetbrains.compose.resources.stringResource

@Composable
fun CompanionSatelliteSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    viewModel: CompanionSatelliteViewModel? = null
) {
    val connections = settings.companionSatelliteConnections

    fun updateConnection(id: String, block: CompanionSatelliteSettings.() -> CompanionSatelliteSettings) {
        onSettingsChange { s ->
            s.copy(companionSatelliteConnections = s.companionSatelliteConnections.map {
                if (it.id == id) it.block() else it
            })
        }
    }

    fun removeConnection(id: String) {
        onSettingsChange { s ->
            s.copy(companionSatelliteConnections = s.companionSatelliteConnections.filter { it.id != id })
        }
        viewModel?.removeConnection(id)
    }

    fun addConnection() {
        onSettingsChange { s ->
            s.copy(companionSatelliteConnections = s.companionSatelliteConnections + CompanionSatelliteSettings())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            connections.forEach { connection ->
                CompanionConnectionCard(
                    connection = connection,
                    viewModel = viewModel,
                    canRemove = connections.size > 1,
                    onUpdate = { block -> updateConnection(connection.id, block) },
                    onRemove = { removeConnection(connection.id) }
                )
            }

            TextButton(onClick = { addConnection() }) {
                Text(stringResource(Res.string.companion_satellite_add_connection))
            }
        }
    }
}

/** First enabled placement in Tab -> Left -> Right order — only used to pick which status to
 * display; unrelated to (and must not be confused with) the device-id derivation rule, which
 * always anchors the bare deviceId to TAB regardless of which placements are enabled. */
private fun primaryPlacement(connection: CompanionSatelliteSettings): CompanionSurfacePlacement? =
    CompanionSurfacePlacement.entries.firstOrNull { connection.isEnabled(it) }

@Composable
private fun CompanionConnectionCard(
    connection: CompanionSatelliteSettings,
    viewModel: CompanionSatelliteViewModel?,
    canRemove: Boolean,
    onUpdate: (CompanionSatelliteSettings.() -> CompanionSatelliteSettings) -> Unit,
    onRemove: () -> Unit
) {
    var nameText by remember(connection.id, connection.name) { mutableStateOf(connection.name) }
    var hostText by remember(connection.id, connection.host) { mutableStateOf(connection.host) }
    var portText by remember(connection.id, connection.port) { mutableStateOf(connection.port.toString()) }
    var deviceIdText by remember(connection.id, connection.deviceId) { mutableStateOf(connection.deviceId) }
    var leftSidebarDeviceIdText by remember(connection.id, connection.leftSidebarDeviceId) { mutableStateOf(connection.leftSidebarDeviceId) }
    var rightSidebarDeviceIdText by remember(connection.id, connection.rightSidebarDeviceId) { mutableStateOf(connection.rightSidebarDeviceId) }
    var productNameText by remember(connection.id, connection.productName) { mutableStateOf(connection.productName) }
    var reconnectDelayText by remember(connection.id, connection.reconnectDelayMs) { mutableStateOf(connection.reconnectDelayMs.toString()) }
    var tabRowsText by remember(connection.id, connection.tabRows) { mutableStateOf(connection.tabRows.toString()) }
    var tabColumnsText by remember(connection.id, connection.tabColumns) { mutableStateOf(connection.tabColumns.toString()) }
    var tabBitmapSizeText by remember(connection.id, connection.tabBitmapSize) { mutableStateOf(connection.tabBitmapSize.toString()) }
    var leftRowsText by remember(connection.id, connection.leftSidebarRows) { mutableStateOf(connection.leftSidebarRows.toString()) }
    var leftColumnsText by remember(connection.id, connection.leftSidebarColumns) { mutableStateOf(connection.leftSidebarColumns.toString()) }
    var leftBitmapSizeText by remember(connection.id, connection.leftSidebarBitmapSize) { mutableStateOf(connection.leftSidebarBitmapSize.toString()) }
    var rightRowsText by remember(connection.id, connection.rightSidebarRows) { mutableStateOf(connection.rightSidebarRows.toString()) }
    var rightColumnsText by remember(connection.id, connection.rightSidebarColumns) { mutableStateOf(connection.rightSidebarColumns.toString()) }
    var rightBitmapSizeText by remember(connection.id, connection.rightSidebarBitmapSize) { mutableStateOf(connection.rightSidebarBitmapSize.toString()) }
    var tabMaxButtonSizeText by remember(connection.id, connection.tabMaxButtonSizeDp) { mutableStateOf(connection.tabMaxButtonSizeDp.toString()) }
    var leftMaxButtonSizeText by remember(connection.id, connection.leftSidebarMaxButtonSizeDp) { mutableStateOf(connection.leftSidebarMaxButtonSizeDp.toString()) }
    var rightMaxButtonSizeText by remember(connection.id, connection.rightSidebarMaxButtonSizeDp) { mutableStateOf(connection.rightSidebarMaxButtonSizeDp.toString()) }

    SettingsSection(
        title = connection.name.ifBlank { stringResource(Res.string.companion_satellite_settings) },
        modifier = Modifier.fillMaxWidth().widthIn(max = 1150.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Left column — connection details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.companion_satellite_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(12.dp))

                if (viewModel != null) {
                    val primary = primaryPlacement(connection)
                    val state = primary?.let { viewModel.connectionStates[CompanionSurfaceSlot(connection.id, it)] }
                        ?: CompanionConnectionUiState(CompanionSurfaceSlot(connection.id, CompanionSurfacePlacement.TAB))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (state.status == CompanionConnectionStatus.CONNECTED || state.status == CompanionConnectionStatus.CONNECTING) {
                            OutlinedButton(onClick = { viewModel.disconnectAll(connection) }) {
                                Text(stringResource(Res.string.companion_satellite_disconnect))
                            }
                        } else {
                            Button(
                                onClick = {
                                    // Companion requires a non-empty DEVICEID ("Missing DEVICEID"
                                    // otherwise) — generate one on the fly if the field was cleared,
                                    // same as a brand-new connection already gets by default.
                                    val effective = if (connection.deviceId.isBlank()) {
                                        val generated = java.util.UUID.randomUUID().toString()
                                        deviceIdText = generated
                                        onUpdate { copy(deviceId = generated) }
                                        connection.copy(deviceId = generated)
                                    } else connection
                                    viewModel.connectAll(effective)
                                },
                                enabled = connection.host.isNotBlank() && primary != null
                            ) {
                                Text(stringResource(Res.string.companion_satellite_connect))
                            }
                        }

                        // Only surface a status label when something needs attention — the
                        // Connect/Disconnect button itself already reflects the connected state.
                        if (state.status != CompanionConnectionStatus.CONNECTED) {
                            val (statusText, statusColor) = when (state.status) {
                                CompanionConnectionStatus.CONNECTING ->
                                    stringResource(Res.string.companion_satellite_status_connecting) to Color(0xFFFFC107)
                                CompanionConnectionStatus.ERROR ->
                                    stringResource(Res.string.companion_satellite_status_error, state.errorMessage) to MaterialTheme.colorScheme.error
                                else ->
                                    stringResource(Res.string.companion_satellite_status_disconnected) to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            }
                            Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                SettingRow(label = stringResource(Res.string.companion_satellite_connection_name)) {
                    SettingsTextField(
                        value = nameText,
                        onValueChange = {
                            nameText = it
                            onUpdate { copy(name = it) }
                        },
                        singleLine = true,
                        modifier = Modifier.widthIn(max = 250.dp)
                    )
                }

                SettingRow(label = stringResource(Res.string.companion_satellite_host)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.widthIn(max = 350.dp)
                    ) {
                        SettingsTextField(
                            value = hostText,
                            onValueChange = {
                                hostText = it
                                onUpdate { copy(host = it) }
                            },
                            placeholder = { Text(stringResource(Res.string.companion_satellite_host_hint)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        SettingsTextField(
                            value = portText,
                            onValueChange = { v ->
                                portText = v
                                v.toIntOrNull()?.let { onUpdate { copy(port = it) } }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(68.dp)
                        )
                    }
                }

                val deviceIdHint = stringResource(Res.string.companion_satellite_device_id_hint)

                SettingRow(label = stringResource(Res.string.companion_satellite_device_id)) {
                    SettingsTextField(
                        value = deviceIdText,
                        onValueChange = {
                            deviceIdText = it
                            onUpdate { copy(deviceId = it) }
                        },
                        placeholder = { Text(deviceIdHint) },
                        singleLine = true,
                        modifier = Modifier.widthIn(max = 350.dp)
                    )
                }

                SettingRow(label = stringResource(Res.string.companion_satellite_left_sidebar_device_id)) {
                    SettingsTextField(
                        value = leftSidebarDeviceIdText,
                        onValueChange = {
                            leftSidebarDeviceIdText = it
                            onUpdate { copy(leftSidebarDeviceId = it) }
                        },
                        placeholder = { Text(deviceIdHint) },
                        singleLine = true,
                        modifier = Modifier.widthIn(max = 350.dp)
                    )
                }

                SettingRow(label = stringResource(Res.string.companion_satellite_right_sidebar_device_id)) {
                    SettingsTextField(
                        value = rightSidebarDeviceIdText,
                        onValueChange = {
                            rightSidebarDeviceIdText = it
                            onUpdate { copy(rightSidebarDeviceId = it) }
                        },
                        placeholder = { Text(deviceIdHint) },
                        singleLine = true,
                        modifier = Modifier.widthIn(max = 350.dp)
                    )
                }

                SettingRow(label = stringResource(Res.string.companion_satellite_product_name)) {
                    SettingsTextField(
                        value = productNameText,
                        onValueChange = {
                            productNameText = it
                            onUpdate { copy(productName = it) }
                        },
                        singleLine = true,
                        modifier = Modifier.widthIn(max = 250.dp)
                    )
                }

                SettingRow(label = stringResource(Res.string.companion_satellite_reconnect_delay)) {
                    SettingsTextField(
                        value = reconnectDelayText,
                        onValueChange = { v ->
                            reconnectDelayText = v
                            v.toIntOrNull()?.let { onUpdate { copy(reconnectDelayMs = it.coerceAtLeast(500)) } }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(120.dp)
                    )
                }
            }

            // Right column — where this connection's grid appears, and each placement's own grid
            // shape (each enabled placement is its own device registration). Which page a placement
            // starts on and which sub-rectangle of a larger page it shows are configured in
            // Companion itself, not here — see the note below.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.companion_satellite_show_in),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(Res.string.companion_satellite_companion_config_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(4.dp))

                CompanionPlacementBlock(
                    label = stringResource(Res.string.companion_satellite_show_in_tab),
                    checked = connection.showInTab,
                    onCheckedChange = { onUpdate { copy(showInTab = it) } },
                    rowsText = tabRowsText,
                    onRowsChange = { v -> tabRowsText = v; v.toIntOrNull()?.let { onUpdate { copy(tabRows = it.coerceAtLeast(1)) } } },
                    columnsText = tabColumnsText,
                    onColumnsChange = { v -> tabColumnsText = v; v.toIntOrNull()?.let { onUpdate { copy(tabColumns = it.coerceAtLeast(1)) } } },
                    bitmapSizeText = tabBitmapSizeText,
                    onBitmapSizeChange = { v -> tabBitmapSizeText = v; v.toIntOrNull()?.let { onUpdate { copy(tabBitmapSize = it.coerceAtLeast(1)) } } },
                    maxButtonSizeText = tabMaxButtonSizeText,
                    onMaxButtonSizeChange = { v -> tabMaxButtonSizeText = v; v.toIntOrNull()?.let { onUpdate { copy(tabMaxButtonSizeDp = it.coerceAtLeast(0)) } } }
                )

                CompanionPlacementBlock(
                    label = stringResource(Res.string.companion_satellite_show_in_left_sidebar),
                    checked = connection.showInLeftSidebar,
                    onCheckedChange = { onUpdate { copy(showInLeftSidebar = it) } },
                    rowsText = leftRowsText,
                    onRowsChange = { v -> leftRowsText = v; v.toIntOrNull()?.let { onUpdate { copy(leftSidebarRows = it.coerceAtLeast(1)) } } },
                    columnsText = leftColumnsText,
                    onColumnsChange = { v -> leftColumnsText = v; v.toIntOrNull()?.let { onUpdate { copy(leftSidebarColumns = it.coerceAtLeast(1)) } } },
                    bitmapSizeText = leftBitmapSizeText,
                    onBitmapSizeChange = { v -> leftBitmapSizeText = v; v.toIntOrNull()?.let { onUpdate { copy(leftSidebarBitmapSize = it.coerceAtLeast(1)) } } },
                    maxButtonSizeText = leftMaxButtonSizeText,
                    onMaxButtonSizeChange = { v -> leftMaxButtonSizeText = v; v.toIntOrNull()?.let { onUpdate { copy(leftSidebarMaxButtonSizeDp = it.coerceAtLeast(0)) } } }
                )

                CompanionPlacementBlock(
                    label = stringResource(Res.string.companion_satellite_show_in_right_sidebar),
                    checked = connection.showInRightSidebar,
                    onCheckedChange = { onUpdate { copy(showInRightSidebar = it) } },
                    rowsText = rightRowsText,
                    onRowsChange = { v -> rightRowsText = v; v.toIntOrNull()?.let { onUpdate { copy(rightSidebarRows = it.coerceAtLeast(1)) } } },
                    columnsText = rightColumnsText,
                    onColumnsChange = { v -> rightColumnsText = v; v.toIntOrNull()?.let { onUpdate { copy(rightSidebarColumns = it.coerceAtLeast(1)) } } },
                    bitmapSizeText = rightBitmapSizeText,
                    onBitmapSizeChange = { v -> rightBitmapSizeText = v; v.toIntOrNull()?.let { onUpdate { copy(rightSidebarBitmapSize = it.coerceAtLeast(1)) } } },
                    maxButtonSizeText = rightMaxButtonSizeText,
                    onMaxButtonSizeChange = { v -> rightMaxButtonSizeText = v; v.toIntOrNull()?.let { onUpdate { copy(rightSidebarMaxButtonSizeDp = it.coerceAtLeast(0)) } } }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Switch(
                checked = connection.autoConnect,
                onCheckedChange = { onUpdate { copy(autoConnect = it) } }
            )
            Text(
                stringResource(Res.string.companion_satellite_autoconnect),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (canRemove) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRemove) {
                Text(stringResource(Res.string.companion_satellite_remove_connection), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** One placement's checkbox + (when checked) its own grid shape — each enabled placement is a
 * separate Companion device registration, so its grid size is independent too. Which page it
 * starts on and which sub-rectangle of a larger page it shows are deliberately not configured
 * here — Companion's own per-surface settings already cover that reliably (see the note shown
 * above these blocks in [CompanionConnectionCard]). */
@Composable
private fun CompanionPlacementBlock(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    rowsText: String,
    onRowsChange: (String) -> Unit,
    columnsText: String,
    onColumnsChange: (String) -> Unit,
    bitmapSizeText: String,
    onBitmapSizeChange: (String) -> Unit,
    maxButtonSizeText: String,
    onMaxButtonSizeChange: (String) -> Unit
) {
    // A single Row (not a Column with fields wrapping below) so the checkbox, label and every
    // field for this placement stay on one line; horizontalScroll is the safety net if the
    // window is ever too narrow to fit all of them, rather than wrapping to a second line.
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(100.dp))
        if (checked) {
            SettingsTextField(
                value = rowsText,
                onValueChange = onRowsChange,
                label = stringResource(Res.string.companion_satellite_rows),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(80.dp)
            )
            SettingsTextField(
                value = columnsText,
                onValueChange = onColumnsChange,
                label = stringResource(Res.string.companion_satellite_columns),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(80.dp)
            )
            SettingsTextField(
                value = bitmapSizeText,
                onValueChange = onBitmapSizeChange,
                label = stringResource(Res.string.companion_satellite_bitmap_size),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(110.dp)
            )
            SettingsTextField(
                value = maxButtonSizeText,
                onValueChange = onMaxButtonSizeChange,
                label = stringResource(Res.string.companion_satellite_max_button_size),
                placeholder = { Text(stringResource(Res.string.companion_satellite_max_button_size_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(130.dp)
            )
        }
    }
}
