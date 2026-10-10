package org.churchpresenter.companionsurface

import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.MutableState
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import org.churchpresenter.companionsatellite.CompanionSatelliteClient
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.sharedui.composables.LabeledCheckbox
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.material3.Text
import org.churchpresenter.theme.components.GhostButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.companion_satellite_add_connection
import org.churchpresenter.strings.generated.resources.companion_satellite_autoconnect
import org.churchpresenter.strings.generated.resources.companion_satellite_bitmap_size
import org.churchpresenter.strings.generated.resources.companion_satellite_columns
import org.churchpresenter.strings.generated.resources.companion_satellite_companion_config_note
import org.churchpresenter.strings.generated.resources.companion_satellite_connect
import org.churchpresenter.strings.generated.resources.companion_satellite_connection_name
import org.churchpresenter.strings.generated.resources.companion_satellite_description
import org.churchpresenter.strings.generated.resources.companion_satellite_device_id
import org.churchpresenter.strings.generated.resources.companion_satellite_device_id_hint
import org.churchpresenter.strings.generated.resources.companion_satellite_disconnect
import org.churchpresenter.strings.generated.resources.companion_satellite_host
import org.churchpresenter.strings.generated.resources.companion_satellite_host_hint
import org.churchpresenter.strings.generated.resources.companion_satellite_left_sidebar_device_id
import org.churchpresenter.strings.generated.resources.companion_satellite_max_button_size
import org.churchpresenter.strings.generated.resources.companion_satellite_max_button_size_hint
import org.churchpresenter.strings.generated.resources.companion_satellite_product_name
import org.churchpresenter.strings.generated.resources.companion_satellite_right_sidebar_device_id
import org.churchpresenter.strings.generated.resources.companion_satellite_reconnect_delay
import org.churchpresenter.strings.generated.resources.companion_satellite_remove_connection
import org.churchpresenter.strings.generated.resources.companion_satellite_rows
import org.churchpresenter.strings.generated.resources.companion_satellite_settings
import org.churchpresenter.strings.generated.resources.companion_satellite_show_in
import org.churchpresenter.strings.generated.resources.companion_satellite_show_in_left_sidebar
import org.churchpresenter.strings.generated.resources.companion_satellite_show_in_right_sidebar
import org.churchpresenter.strings.generated.resources.companion_satellite_show_in_tab
import org.churchpresenter.strings.generated.resources.companion_satellite_status_connecting
import org.churchpresenter.strings.generated.resources.companion_satellite_status_disconnected
import org.churchpresenter.strings.generated.resources.atem_status_error
import org.churchpresenter.companionsatellite.CompanionConnectionStatus
import org.churchpresenter.sharedui.composables.SettingRow
import org.churchpresenter.sharedui.composables.SettingsScrollbar
import org.churchpresenter.sharedui.composables.SettingsScrollbarGutter
import org.churchpresenter.sharedui.composables.SettingsSection
import org.churchpresenter.theme.components.SettingsTextField
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CompanionSatelliteSettings
import org.churchpresenter.core.models.companion.CompanionSurfacePlacement
import org.churchpresenter.core.models.companion.CompanionSurfaceSlot
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.sharedui.composables.LabeledSwitch
import org.churchpresenter.theme.semantic

private const val MIN_RECONNECT_DELAY_MS = 500

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

    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(end = SettingsScrollbarGutter),
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

            GhostButton(onClick = { addConnection() }) {
                Text(stringResource(Res.string.companion_satellite_add_connection))
            }
        }
        SettingsScrollbar(scrollState)
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
                    CompanionConnectRow(connection, viewModel, onUpdate)
                    Spacer(Modifier.height(12.dp))
                }

                CompanionConnectionFields(connection, onUpdate)
            }

            // Right column — where this connection's grid appears, and each placement's own grid
            // shape (each enabled placement is its own device registration). Which page a placement
            // starts on and which sub-rectangle of a larger page it shows are configured in
            // Companion itself, not here — see the note below.
            Column(modifier = Modifier.weight(1f)) {
                CompanionPlacements(connection, onUpdate)
            }
        }

        Spacer(Modifier.height(8.dp))

        LabeledSwitch(
            checked = connection.autoConnect,
            onCheckedChange = { onUpdate { copy(autoConnect = it) } },
            label = stringResource(Res.string.companion_satellite_autoconnect),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            spacing = 12.dp,
        )

        if (canRemove) {
            Spacer(Modifier.height(8.dp))
            GhostButton(onClick = onRemove) {
                Text(
                    stringResource(Res.string.companion_satellite_remove_connection),
                    color = MaterialTheme.colorScheme.error
                )
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
        LabeledCheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            label = label,
            modifier = Modifier.width(156.dp),
            spacing = 8.dp,
        )
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

/** A field's text, following [value] whenever it changes from outside -- and per connection [key]. */
@Composable
private fun rememberFieldText(key: String, value: Any): MutableState<String> =
    remember(key, value) { mutableStateOf(value.toString()) }

/** One placement's grid: rows, columns, the button bitmap size, and the largest a button is drawn. */
private data class PlacementShape(val rows: Int, val columns: Int, val bitmapSize: Int, val maxButtonSizeDp: Int)

/**
 * A placement's checkbox and grid fields over [shape]. Each field keeps its own text and reports a
 * whole new shape when it parses: sizes are at least 1, the button cap at least 0 (no cap).
 */
@Composable
private fun CompanionPlacement(
    connectionId: String,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: PlacementShape,
    onShapeChange: (PlacementShape) -> Unit,
) {
    var rowsText by rememberFieldText(connectionId, shape.rows)
    var columnsText by rememberFieldText(connectionId, shape.columns)
    var bitmapSizeText by rememberFieldText(connectionId, shape.bitmapSize)
    var maxButtonSizeText by rememberFieldText(connectionId, shape.maxButtonSizeDp)
    CompanionPlacementBlock(
        label = label,
        checked = checked,
        onCheckedChange = onCheckedChange,
        rowsText = rowsText,
        onRowsChange = { v ->
            rowsText = v
            v.toIntOrNull()?.let { onShapeChange(shape.copy(rows = it.coerceAtLeast(1))) }
        },
        columnsText = columnsText,
        onColumnsChange = { v ->
            columnsText = v
            v.toIntOrNull()?.let { onShapeChange(shape.copy(columns = it.coerceAtLeast(1))) }
        },
        bitmapSizeText = bitmapSizeText,
        onBitmapSizeChange = { v ->
            bitmapSizeText = v
            v.toIntOrNull()?.let { onShapeChange(shape.copy(bitmapSize = it.coerceAtLeast(1))) }
        },
        maxButtonSizeText = maxButtonSizeText,
        onMaxButtonSizeChange = { v ->
            maxButtonSizeText = v
            v.toIntOrNull()?.let { onShapeChange(shape.copy(maxButtonSizeDp = it.coerceAtLeast(0))) }
        }
    )
}

/** Connect or disconnect every placement of [connection], and say what needs attention. */
@Composable
private fun CompanionConnectRow(
    connection: CompanionSatelliteSettings,
    viewModel: CompanionSatelliteViewModel,
    onUpdate: (CompanionSatelliteSettings.() -> CompanionSatelliteSettings) -> Unit,
) {
    val primary = primaryPlacement(connection)
    val state = primary?.let { viewModel.connectionStates[CompanionSurfaceSlot(connection.id, it)] }
        ?: CompanionConnectionUiState(
            CompanionSurfaceSlot(connection.id, CompanionSurfacePlacement.TAB)
        )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (
            state.status == CompanionConnectionStatus.CONNECTED ||
            state.status == CompanionConnectionStatus.CONNECTING
        ) {
            KeyButton(onClick = { viewModel.disconnectAll(connection) }) {
                Text(stringResource(Res.string.companion_satellite_disconnect))
            }
        } else {
            RaisedButton(
                onClick = {
                    // Companion requires a non-empty DEVICEID ("Missing DEVICEID"
                    // otherwise) — generate one on the fly if the field was cleared,
                    // same as a brand-new connection already gets by default.
                    val effective = if (connection.deviceId.isBlank()) {
                        val generated = java.util.UUID.randomUUID().toString()
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
                    stringResource(Res.string.companion_satellite_status_connecting) to
                        MaterialTheme.semantic.warning
                CompanionConnectionStatus.ERROR ->
                    stringResource(Res.string.atem_status_error, state.errorMessage) to
                        MaterialTheme.colorScheme.error
                else ->
                    stringResource(Res.string.companion_satellite_status_disconnected) to
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            }
            Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
        }
    }
}

/** The connection's name, address, device ids, product name and reconnect delay. */
@Composable
private fun CompanionConnectionFields(
    connection: CompanionSatelliteSettings,
    onUpdate: (CompanionSatelliteSettings.() -> CompanionSatelliteSettings) -> Unit,
) {
    var hostText by rememberFieldText(connection.id, connection.host)
    var portText by rememberFieldText(connection.id, connection.port)
    var portFocused by remember { mutableStateOf(false) }

    // Saved on Enter or on leaving the field, never per keystroke: each save reconnects, and typing
    // 70000 used to save 7000 on the way -- a port nobody asked for. Anything unusable reverts.
    fun commitPort() {
        val port = portText.trim().toIntOrNull()?.takeIf { it in CompanionSatelliteClient.VALID_PORTS }
        if (port == null) portText = connection.port.toString()
        else if (port != connection.port) onUpdate { copy(port = port) }
    }
    var reconnectDelayText by rememberFieldText(connection.id, connection.reconnectDelayMs)

    CompanionTextRow(
        Res.string.companion_satellite_connection_name,
        connection.id, connection.name, null, 250.dp,
    ) { value -> onUpdate { copy(name = value) } }

    SettingRow(label = Res.string.companion_satellite_host) {
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
                onValueChange = { portText = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { commitPort() }),
                modifier = Modifier.width(68.dp).onFocusChanged { state ->
                    if (portFocused && !state.isFocused) commitPort()
                    portFocused = state.isFocused
                }
            )
        }
    }

    val deviceIdHint = stringResource(Res.string.companion_satellite_device_id_hint)

    CompanionTextRow(
        Res.string.companion_satellite_device_id,
        connection.id, connection.deviceId, deviceIdHint, 350.dp,
    ) { value -> onUpdate { copy(deviceId = value) } }

    CompanionTextRow(
        Res.string.companion_satellite_left_sidebar_device_id,
        connection.id, connection.leftSidebarDeviceId, deviceIdHint, 350.dp,
    ) { value -> onUpdate { copy(leftSidebarDeviceId = value) } }

    CompanionTextRow(
        Res.string.companion_satellite_right_sidebar_device_id,
        connection.id, connection.rightSidebarDeviceId, deviceIdHint, 350.dp,
    ) { value -> onUpdate { copy(rightSidebarDeviceId = value) } }

    CompanionTextRow(
        Res.string.companion_satellite_product_name,
        connection.id, connection.productName, null, 250.dp,
    ) { value -> onUpdate { copy(productName = value) } }

    SettingRow(label = Res.string.companion_satellite_reconnect_delay) {
        SettingsTextField(
            value = reconnectDelayText,
            onValueChange = { v ->
                reconnectDelayText = v
                v.toIntOrNull()?.let {
                    onUpdate { copy(reconnectDelayMs = it.coerceAtLeast(MIN_RECONNECT_DELAY_MS)) }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(120.dp)
        )
    }
}

/** Where this connection's grid appears, and each placement's own grid shape. */
@Composable
private fun CompanionPlacements(
    connection: CompanionSatelliteSettings,
    onUpdate: (CompanionSatelliteSettings.() -> CompanionSatelliteSettings) -> Unit,
) {
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

    CompanionPlacement(
        connection.id,
        stringResource(Res.string.companion_satellite_show_in_tab),
        connection.showInTab,
        { onUpdate { copy(showInTab = it) } },
        PlacementShape(connection.tabRows, connection.tabColumns, connection.tabBitmapSize,
            connection.tabMaxButtonSizeDp),
    ) { shape ->
        onUpdate {
            copy(tabRows = shape.rows, tabColumns = shape.columns, tabBitmapSize = shape.bitmapSize,
                tabMaxButtonSizeDp = shape.maxButtonSizeDp)
        }
    }

    CompanionPlacement(
        connection.id,
        stringResource(Res.string.companion_satellite_show_in_left_sidebar),
        connection.showInLeftSidebar,
        { onUpdate { copy(showInLeftSidebar = it) } },
        PlacementShape(connection.leftSidebarRows, connection.leftSidebarColumns,
            connection.leftSidebarBitmapSize, connection.leftSidebarMaxButtonSizeDp),
    ) { shape ->
        onUpdate {
            copy(leftSidebarRows = shape.rows, leftSidebarColumns = shape.columns,
                leftSidebarBitmapSize = shape.bitmapSize, leftSidebarMaxButtonSizeDp = shape.maxButtonSizeDp)
        }
    }

    CompanionPlacement(
        connection.id,
        stringResource(Res.string.companion_satellite_show_in_right_sidebar),
        connection.showInRightSidebar,
        { onUpdate { copy(showInRightSidebar = it) } },
        PlacementShape(connection.rightSidebarRows, connection.rightSidebarColumns,
            connection.rightSidebarBitmapSize, connection.rightSidebarMaxButtonSizeDp),
    ) { shape ->
        onUpdate {
            copy(rightSidebarRows = shape.rows, rightSidebarColumns = shape.columns,
                rightSidebarBitmapSize = shape.bitmapSize,
                rightSidebarMaxButtonSizeDp = shape.maxButtonSizeDp)
        }
    }
}

/** One labelled text setting of a connection, saved on every keystroke. */
@Composable
private fun CompanionTextRow(
    label: StringResource,
    connectionId: String,
    value: String,
    placeholder: String?,
    maxWidth: Dp,
    onChange: (String) -> Unit,
) {
    var text by rememberFieldText(connectionId, value)
    SettingRow(label = label) {
        SettingsTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(it)
            },
            placeholder = placeholder?.let { hint -> { Text(hint) } },
            singleLine = true,
            modifier = Modifier.widthIn(max = maxWidth)
        )
    }
}
