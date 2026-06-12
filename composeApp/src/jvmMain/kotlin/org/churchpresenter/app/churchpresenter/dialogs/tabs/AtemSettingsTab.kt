package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.atem_clip_fps
import churchpresenter.composeapp.generated.resources.atem_clip_fps_hint
import churchpresenter.composeapp.generated.resources.atem_default_clip_slot
import churchpresenter.composeapp.generated.resources.atem_default_still_slot
import churchpresenter.composeapp.generated.resources.atem_description
import churchpresenter.composeapp.generated.resources.atem_host
import churchpresenter.composeapp.generated.resources.atem_host_hint
import churchpresenter.composeapp.generated.resources.atem_port
import churchpresenter.composeapp.generated.resources.atem_render_height
import churchpresenter.composeapp.generated.resources.atem_render_resolution
import churchpresenter.composeapp.generated.resources.atem_render_width
import churchpresenter.composeapp.generated.resources.atem_slot_range
import churchpresenter.composeapp.generated.resources.atem_detected_video_mode
import churchpresenter.composeapp.generated.resources.atem_status_connected
import churchpresenter.composeapp.generated.resources.atem_status_connecting
import churchpresenter.composeapp.generated.resources.atem_status_disconnected
import churchpresenter.composeapp.generated.resources.atem_status_error
import churchpresenter.composeapp.generated.resources.atem_test_connection
import churchpresenter.composeapp.generated.resources.atem_test_connection_hint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.AtemSettings
import org.churchpresenter.app.churchpresenter.server.AtemClient
import org.jetbrains.compose.resources.stringResource

@Composable
fun AtemSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    val atem = settings.atemSettings
    fun update(block: AtemSettings.() -> AtemSettings) {
        onSettingsChange { s -> s.copy(atemSettings = s.atemSettings.block()) }
    }

    var hostText by remember(atem.host) { mutableStateOf(atem.host) }
    var portText by remember(atem.port) { mutableStateOf(atem.port.toString()) }
    // Slots are stored 0-based (protocol) but displayed 1-based like ATEM Software Control
    var stillSlotText by remember(atem.defaultStillSlot) { mutableStateOf((atem.defaultStillSlot + 1).toString()) }
    var clipSlotText by remember(atem.defaultClipSlot) { mutableStateOf((atem.defaultClipSlot + 1).toString()) }
    var renderWidthText by remember(atem.renderWidth) { mutableStateOf(atem.renderWidth.toString()) }
    var renderHeightText by remember(atem.renderHeight) { mutableStateOf(atem.renderHeight.toString()) }
    var clipFpsText by remember(atem.clipFps) { mutableStateOf(formatAtemFps(atem.clipFps)) }

    var connectionStatus by remember { mutableStateOf<String?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var detectedVideoMode by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
            val cardMod = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 16.dp)

            // ── Connection card ──────────────────────────────────────────────
            Column(modifier = cardMod, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                AtemSectionHeader("Blackmagic ATEM")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.atem_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = hostText,
                        onValueChange = {
                            hostText = it
                            update { copy(host = it) }
                        },
                        label = { Text(stringResource(Res.string.atem_host)) },
                        placeholder = { Text(stringResource(Res.string.atem_host_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { v ->
                            portText = v
                            v.toIntOrNull()?.let { update { copy(port = it) } }
                        },
                        label = { Text(stringResource(Res.string.atem_port)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(100.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.atem_test_connection_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (isTesting) return@Button
                            isTesting = true
                            connectionStatus = null
                            connectionError = null
                            detectedVideoMode = null
                            scope.launch {
                                try {
                                    val state = withContext(Dispatchers.IO) {
                                        AtemClient(hostText, portText.toIntOrNull() ?: 9910).queryState()
                                    }
                                    val fpsLabel = formatAtemFps(state.fps)
                                    detectedVideoMode = "${state.videoMode} ($fpsLabel fps)"
                                    clipFpsText = fpsLabel
                                    update {
                                        copy(
                                            clipFps = state.fps,
                                            detectedStillSlots = state.stillSlots.size,
                                            detectedClipSlots = state.clipSlots.size
                                        )
                                    }
                                    connectionStatus = "connected"
                                    connectionError = null
                                } catch (e: Exception) {
                                    connectionStatus = "error"
                                    connectionError = e.message ?: "Unknown error"
                                } finally {
                                    isTesting = false
                                }
                            }
                        },
                        enabled = hostText.isNotBlank() && !isTesting
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            if (isTesting) stringResource(Res.string.atem_status_connecting)
                            else stringResource(Res.string.atem_test_connection)
                        )
                    }

                    val (statusText, statusColor) = when {
                        isTesting ->
                            stringResource(Res.string.atem_status_connecting) to Color(0xFFFFC107)
                        connectionStatus == "connected" ->
                            stringResource(Res.string.atem_status_connected) to Color(0xFF4CAF50)
                        connectionStatus == "error" ->
                            stringResource(Res.string.atem_status_error, connectionError ?: "") to MaterialTheme.colorScheme.error
                        else ->
                            stringResource(Res.string.atem_status_disconnected) to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    }
                    if (connectionStatus != null || isTesting) {
                        Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
                    }
                    val detectedMode = detectedVideoMode
                    if (detectedMode != null && connectionStatus == "connected") {
                        Text(
                            stringResource(Res.string.atem_detected_video_mode, detectedMode),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }

            // ── Defaults card ────────────────────────────────────────────────
            Column(modifier = cardMod, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                AtemSectionHeader("Upload Defaults")
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = stillSlotText,
                        onValueChange = { v ->
                            stillSlotText = v
                            v.toIntOrNull()?.let { update { copy(defaultStillSlot = (it - 1).coerceAtLeast(0)) } }
                        },
                        label = { Text(stringResource(Res.string.atem_default_still_slot)) },
                        supportingText = if (atem.detectedStillSlots > 0) {
                            { Text(stringResource(Res.string.atem_slot_range, atem.detectedStillSlots)) }
                        } else null,
                        isError = atem.detectedStillSlots > 0 &&
                            stillSlotText.toIntOrNull()?.let { it !in 1..atem.detectedStillSlots } != false,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = clipSlotText,
                        onValueChange = { v ->
                            clipSlotText = v
                            v.toIntOrNull()?.let { update { copy(defaultClipSlot = (it - 1).coerceAtLeast(0)) } }
                        },
                        label = { Text(stringResource(Res.string.atem_default_clip_slot)) },
                        supportingText = if (atem.detectedClipSlots > 0) {
                            { Text(stringResource(Res.string.atem_slot_range, atem.detectedClipSlots)) }
                        } else null,
                        isError = atem.detectedClipSlots > 0 &&
                            clipSlotText.toIntOrNull()?.let { it !in 1..atem.detectedClipSlots } != false,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))

                Text(
                    stringResource(Res.string.atem_render_resolution),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = renderWidthText,
                        onValueChange = { v ->
                            renderWidthText = v
                            v.toIntOrNull()?.let { update { copy(renderWidth = it) } }
                        },
                        label = { Text(stringResource(Res.string.atem_render_width)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = renderHeightText,
                        onValueChange = { v ->
                            renderHeightText = v
                            v.toIntOrNull()?.let { update { copy(renderHeight = it) } }
                        },
                        label = { Text(stringResource(Res.string.atem_render_height)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = clipFpsText,
                    onValueChange = { v ->
                        clipFpsText = v
                        v.toDoubleOrNull()?.let { update { copy(clipFps = it) } }
                    },
                    label = { Text(stringResource(Res.string.atem_clip_fps)) },
                    placeholder = { Text(stringResource(Res.string.atem_clip_fps_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** "25", "59.94" — exact fps without truncation, locale-independent decimal point. */
internal fun formatAtemFps(fps: Double): String =
    if (fps == kotlin.math.floor(fps)) fps.toInt().toString()
    else String.format(java.util.Locale.US, "%.2f", fps).trimEnd('0').trimEnd('.')

@Composable
private fun AtemSectionHeader(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            thickness = 1.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
