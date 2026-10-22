package org.churchpresenter.serverui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.churchpresenter.server.CalendarSyncService
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.server.requestRestart
import org.churchpresenter.server.requestStart
import org.churchpresenter.server.requestStop
import org.churchpresenter.server.updateApiKey
import org.churchpresenter.server.updateFileUploadEnabled
import org.churchpresenter.server.updateMaxMediaUploadMb
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.serverPortOrNull
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.composables.SettingsScrollbar
import org.churchpresenter.sharedui.composables.SettingsScrollbarGutter
import org.churchpresenter.sharedui.composables.TooltipIconButton
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.guideTarget
import org.churchpresenter.sharedui.utils.SystemClipboard
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.allow_file_upload
import org.churchpresenter.strings.generated.resources.allow_file_upload_description
import org.churchpresenter.strings.generated.resources.api_key_hint
import org.churchpresenter.strings.generated.resources.api_key_label
import org.churchpresenter.strings.generated.resources.api_key_protection
import org.churchpresenter.strings.generated.resources.browser_source_note_in_server_settings
import org.churchpresenter.strings.generated.resources.companion_server
import org.churchpresenter.strings.generated.resources.copy_api_key
import org.churchpresenter.strings.generated.resources.enable_server
import org.churchpresenter.strings.generated.resources.generate_api_key
import org.churchpresenter.strings.generated.resources.max_media_upload_description
import org.churchpresenter.strings.generated.resources.max_media_upload_label
import org.churchpresenter.strings.generated.resources.max_media_upload_minutes
import org.churchpresenter.strings.generated.resources.max_media_upload_under_minute
import org.churchpresenter.strings.generated.resources.server_description
import org.churchpresenter.strings.generated.resources.server_host_hint
import org.churchpresenter.strings.generated.resources.server_host_label
import org.churchpresenter.strings.generated.resources.server_host_note
import org.churchpresenter.strings.generated.resources.server_port
import org.churchpresenter.strings.generated.resources.server_port_hint
import org.churchpresenter.strings.generated.resources.server_port_note
import org.churchpresenter.strings.generated.resources.server_restart
import org.churchpresenter.strings.generated.resources.server_running
import org.churchpresenter.strings.generated.resources.server_stopped
import org.churchpresenter.strings.generated.resources.server_url_copy
import org.churchpresenter.strings.generated.resources.server_url_label
import org.churchpresenter.strings.generated.resources.show_qr_code
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.components.RaisedSwitch
import org.churchpresenter.theme.components.SettingsTextField
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.semantic
import org.churchpresenter.theme.sunken
import org.jetbrains.compose.resources.stringResource
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun ServerSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    companionServer: CompanionServer,
    remoteClientManager: RemoteClientManager,
    calendarSync: CalendarSyncService? = null,
    /** The relay calendar sync reaches when settings name none; the app passes its built-in one. */
    builtInRelayUrl: String = "",
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
            verticalArrangement = Arrangement.spacedBy(CARD_GAP)
        ) {
            val server: @Composable () -> Unit = {
                ServerCard(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    companionServer = companionServer,
                    isRunning = isRunning,
                    serverUrl = serverUrl,
                    copyText = copyText,
                )
            }
            val clients: @Composable () -> Unit = {
                RemoteClientsCard(companionServer = companionServer, remoteClientManager = remoteClientManager)
            }
            val calendar: (@Composable () -> Unit)? = calendarSync?.let { sync ->
                {
                    CalendarSyncCard(
                        settings = settings,
                        onSettingsChange = onSettingsChange,
                        sync = sync,
                        labelFor = remoteClientManager::getLabel,
                        builtInRelayUrl = builtInRelayUrl,
                    )
                }
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth >= TWO_COLUMNS_FROM) {
                    // Wide enough for two cards a row: the server and its clients on the left, the
                    // calendar on the right -- or the clients there, when there is no calendar.
                    Row(horizontalArrangement = Arrangement.spacedBy(CARD_GAP)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
                            server()
                            if (calendar != null) clients()
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
                            if (calendar != null) calendar() else clients()
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
                        server()
                        clients()
                        calendar?.invoke()
                    }
                }
            }

            // The triggers run the full width: a row holds a name and up to three pairs of buttons.
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
    val onEnable: (Boolean) -> Unit = { enable ->
        val port = serverPortOrNull(portText) ?: Constants.SERVER_DEFAULT_PORT
        // Off the event thread: a stop waits on Netty for over a second. The switch and the status
        // pill follow isRunning when the request has run.
        if (enable) {
            companionServer.requestStart(port, hostText.trim())
            onSettingsChange { s -> s.copy(serverSettings = s.serverSettings.copy(enabled = true, port = port)) }
        } else {
            companionServer.requestStop()
            onSettingsChange { s -> s.copy(serverSettings = s.serverSettings.copy(enabled = false)) }
        }
    }

    ServerSettingsCard(
        icon = Icons.Outlined.Dns,
        tint = MaterialTheme.semantic.info,
        title = stringResource(Res.string.companion_server),
        description = stringResource(Res.string.server_description),
        titleExtra = {
            if (isRunning) {
                Pill(stringResource(Res.string.server_running), MaterialTheme.semantic.success, dot = true)
            } else {
                Pill(stringResource(Res.string.server_stopped), MaterialTheme.colorScheme.onSurfaceVariant, dot = true)
            }
        },
        actions = {
            val name = stringResource(Res.string.enable_server)
            RaisedSwitch(
                checked = isRunning,
                onCheckedChange = onEnable,
                modifier = Modifier.guideTarget(GuideTargets.SERVER_ENABLE).semantics { contentDescription = name },
            )
        },
    ) {
        CardBlock {
            if (isRunning && serverUrl.isNotBlank()) {
                ServerUrlRow(
                    serverUrl = serverUrl,
                    copyText = copyText,
                    apiKey = if (settings.serverSettings.apiKeyEnabled && apiKeyText.isNotBlank()) apiKeyText else null,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ServerPortField(
                    portText = portText,
                    isRunning = isRunning,
                    onPortText = { v ->
                        portText = v
                        serverPortOrNull(v)?.let { port ->
                            onSettingsChange { s -> s.copy(serverSettings = s.serverSettings.copy(port = port)) }
                        }
                    },
                    onRestart = {
                        val port = serverPortOrNull(portText) ?: Constants.SERVER_DEFAULT_PORT
                        companionServer.requestRestart(port, hostText.trim())
                    },
                )
                ServerHostField(
                    hostText = hostText,
                    isRunning = isRunning,
                    onHostText = { v ->
                        hostText = v
                        onSettingsChange { s -> s.copy(serverSettings = s.serverSettings.copy(serverHost = v.trim())) }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            if (!isRunning) DimText(stringResource(Res.string.server_port_note), small = true)
        }
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
private fun ServerPortField(
    portText: String,
    isRunning: Boolean,
    onPortText: (String) -> Unit,
    onRestart: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        FieldLabel(stringResource(Res.string.server_port))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SettingsTextField(
                value = portText,
                onValueChange = { v -> if (v.length <= PORT_DIGITS && v.all(Char::isDigit)) onPortText(v) },
                modifier = Modifier.width(88.dp),
                singleLine = true,
                enabled = !isRunning,
                placeholder = { Text(stringResource(Res.string.server_port_hint)) },
            )
            if (isRunning) {
                TooltipIconButton(
                    painter = rememberVectorPainter(Icons.Outlined.Refresh),
                    text = stringResource(Res.string.server_restart),
                    onClick = onRestart,
                    iconSize = 16.dp,
                    buttonSize = 32.dp,
                )
            }
        }
    }
}

@Composable
private fun ServerHostField(
    hostText: String,
    isRunning: Boolean,
    onHostText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        FieldLabel(stringResource(Res.string.server_host_label))
        SettingsTextField(
            value = hostText,
            onValueChange = onHostText,
            modifier = Modifier.fillMaxWidth(),
            fillWidth = true,
            singleLine = true,
            enabled = !isRunning,
            placeholder = {
                Text(
                    stringResource(Res.string.server_host_hint),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        // Under the field, not beside its label: a note that wraps there pushes the field below the Port one.
        DimText(stringResource(Res.string.server_host_note), small = true)
    }
}

/** The address people actually copy, first: the URL in a monospace well, and its QR beside it. */
@Composable
private fun ServerUrlRow(serverUrl: String, copyText: (String) -> Unit, apiKey: String?) {
    var showConnectionQrDialog by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldHeading(stringResource(Res.string.server_url_label))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    text = serverUrl,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .sunken(AppShape(8.dp), elevationPalette())
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                )
            }
            KeyButton(
                shape = AppShape(8.dp),
                onClick = { copyText(serverUrl) },
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 13.dp),
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.server_url_copy), style = MaterialTheme.typography.labelMedium)
            }
            RaisedButton(
                shape = AppShape(8.dp),
                onClick = { showConnectionQrDialog = true },
                modifier = Modifier.guideTarget(GuideTargets.SERVER_QR).height(36.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                Icon(Icons.Outlined.QrCode2, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.show_qr_code), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    if (showConnectionQrDialog) {
        ConnectionQrDialog(
            serverUrl = serverUrl,
            apiKey = apiKey,
            onDismiss = { showConnectionQrDialog = false },
        )
    }
}

@Composable
private fun ColumnScope.ApiKeySection(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    apiKeyText: String,
    onApiKeyText: (String) -> Unit,
    copyText: (String) -> Unit,
) {
    SwitchBlock(
        title = stringResource(Res.string.api_key_protection),
        description = stringResource(Res.string.browser_source_note_in_server_settings),
        checked = settings.serverSettings.apiKeyEnabled,
        onCheckedChange = { enabled ->
            onSettingsChange { s -> s.copy(serverSettings = s.serverSettings.copy(apiKeyEnabled = enabled)) }
        },
    ) {
        val setApiKey: (String) -> Unit = { key ->
            onApiKeyText(key)
            onSettingsChange { s -> s.copy(serverSettings = s.serverSettings.copy(apiKey = key)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SettingsTextField(
                value = apiKeyText,
                onValueChange = setApiKey,
                label = stringResource(Res.string.api_key_label),
                modifier = Modifier.weight(1f),
                fillWidth = true,
                singleLine = true,
                placeholder = {
                    Text(stringResource(Res.string.api_key_hint), style = MaterialTheme.typography.bodySmall)
                },
            )
            FieldHeightKey(stringResource(Res.string.generate_api_key)) {
                setApiKey(UUID.randomUUID().toString().replace("-", ""))
            }
            FieldHeightKey(stringResource(Res.string.copy_api_key)) { copyText(apiKeyText) }
        }
    }
}

@Composable
private fun ColumnScope.FileUploadSection(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    SwitchBlock(
        title = stringResource(Res.string.allow_file_upload),
        description = stringResource(Res.string.allow_file_upload_description),
        checked = settings.serverSettings.fileUploadEnabled,
        onCheckedChange = { enabled ->
            onSettingsChange { s -> s.copy(serverSettings = s.serverSettings.copy(fileUploadEnabled = enabled)) }
        },
    ) {
        var maxMbText by remember(settings.serverSettings.maxMediaUploadMb) {
            mutableStateOf(settings.serverSettings.maxMediaUploadMb.toString())
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DimText(stringResource(Res.string.max_media_upload_label))
            SettingsTextField(
                value = maxMbText,
                onValueChange = { v ->
                    if (v.length <= PORT_DIGITS && v.all(Char::isDigit)) {
                        maxMbText = v
                        v.toIntOrNull()?.takeIf { it > 0 }?.let { mb ->
                            onSettingsChange { s ->
                                s.copy(serverSettings = s.serverSettings.copy(maxMediaUploadMb = mb))
                            }
                        }
                    }
                },
                modifier = Modifier.width(88.dp),
                singleLine = true,
                placeholder = { Text(Constants.DEFAULT_MAX_MEDIA_UPLOAD_MB.toString()) },
            )
            val minutes = hdVideoMinutes(maxMbText.toIntOrNull() ?: 0)
            DimText(
                if (minutes < 1) {
                    stringResource(Res.string.max_media_upload_under_minute)
                } else {
                    stringResource(Res.string.max_media_upload_minutes, minutes)
                },
                small = true,
            )
        }
        DimText(
            stringResource(Res.string.max_media_upload_description, Constants.DEFAULT_MAX_MEDIA_UPLOAD_MB),
            small = true,
        )
    }
}

/** A key as tall as the labelled field beside it, so the row reads as one line. */
@Composable
private fun FieldHeightKey(text: String, onClick: () -> Unit) {
    KeyButton(
        onClick = onClick,
        shape = AppShape(8.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        modifier = Modifier.height(LABELLED_FIELD_HEIGHT),
    ) { Text(text, style = MaterialTheme.typography.labelMedium) }
}

/** About how many minutes of HD video [mb] megabytes hold, at roughly 140 MB a minute. */
internal fun hdVideoMinutes(mb: Int): Int = (mb / MB_PER_HD_MINUTE).roundToInt()

private const val PORT_DIGITS = 5

/** What `SettingsTextField` draws a labelled single-line field at. */
private val LABELLED_FIELD_HEIGHT = 42.dp
private const val MB_PER_HD_MINUTE = 140.0
private val TWO_COLUMNS_FROM = 1100.dp
private val CARD_GAP = 8.dp
