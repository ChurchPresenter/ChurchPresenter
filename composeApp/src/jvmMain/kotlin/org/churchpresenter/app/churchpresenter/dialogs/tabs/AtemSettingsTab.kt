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
import org.churchpresenter.app.churchpresenter.composables.SettingsTextField
import androidx.compose.material3.Switch
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
import churchpresenter.composeapp.generated.resources.atem_capacity_equal
import churchpresenter.composeapp.generated.resources.atem_capacity_mixed
import churchpresenter.composeapp.generated.resources.atem_capacity_unassigned
import churchpresenter.composeapp.generated.resources.atem_capacity_unknown
import churchpresenter.composeapp.generated.resources.atem_clip_fps_hint
import churchpresenter.composeapp.generated.resources.atem_clip_fps_unit
import churchpresenter.composeapp.generated.resources.atem_default_clip_slot
import churchpresenter.composeapp.generated.resources.atem_default_still_slot
import churchpresenter.composeapp.generated.resources.atem_description
import churchpresenter.composeapp.generated.resources.atem_detected_keyers
import churchpresenter.composeapp.generated.resources.atem_detected_keyers_unknown
import churchpresenter.composeapp.generated.resources.atem_key_postroll
import churchpresenter.composeapp.generated.resources.atem_key_preroll
import churchpresenter.composeapp.generated.resources.atem_golive_key
import churchpresenter.composeapp.generated.resources.atem_golive_key_hint
import churchpresenter.composeapp.generated.resources.atem_host
import churchpresenter.composeapp.generated.resources.atem_host_hint
import churchpresenter.composeapp.generated.resources.atem_port
import churchpresenter.composeapp.generated.resources.atem_quick_upload
import churchpresenter.composeapp.generated.resources.atem_quick_upload_hint
import churchpresenter.composeapp.generated.resources.atem_render_height
import churchpresenter.composeapp.generated.resources.atem_render_width
import churchpresenter.composeapp.generated.resources.atem_detected_video_mode
import churchpresenter.composeapp.generated.resources.atem_status_connected
import churchpresenter.composeapp.generated.resources.atem_status_connecting
import churchpresenter.composeapp.generated.resources.atem_status_disconnected
import churchpresenter.composeapp.generated.resources.atem_status_error
import churchpresenter.composeapp.generated.resources.atem_test_connection
import churchpresenter.composeapp.generated.resources.atem_section_connection
import churchpresenter.composeapp.generated.resources.atem_section_upload_defaults
import churchpresenter.composeapp.generated.resources.atem_test_connection_hint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.composables.SettingRow
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
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
    var meText by remember(atem.keyMixEffect) { mutableStateOf((atem.keyMixEffect + 1).toString()) }
    var keyText by remember(atem.keyIndex) { mutableStateOf((atem.keyIndex + 1).toString()) }
    var dskText by remember(atem.dskIndex) { mutableStateOf((atem.dskIndex + 1).toString()) }
    var keyPreRollText by remember(atem.keyPreRollMs) { mutableStateOf(atem.keyPreRollMs.toString()) }
    var keyPostRollText by remember(atem.keyPostRollMs) { mutableStateOf(atem.keyPostRollMs.toString()) }

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
            // ── Connection card ──────────────────────────────────────────────
            SettingsSection(
                title = stringResource(Res.string.atem_section_connection),
                modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)
            ) {
                Text(
                    text = stringResource(Res.string.atem_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(12.dp))

                SettingRow(label = stringResource(Res.string.atem_host)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.widthIn(max = 350.dp)
                    ) {
                        SettingsTextField(
                            value = hostText,
                            onValueChange = {
                                hostText = it
                                update { copy(host = it) }
                            },
                            placeholder = { Text(stringResource(Res.string.atem_host_hint)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        SettingsTextField(
                            value = portText,
                            onValueChange = { v ->
                                portText = v
                                v.toIntOrNull()?.let { update { copy(port = it) } }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(68.dp)
                        )
                    }
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
                        shape = RoundedCornerShape(6.dp),
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
                                    detectedVideoMode = buildString {
                                        append("${state.videoMode} ($fpsLabel fps)")
                                        if (state.clipMaxFrames.isNotEmpty()) {
                                            val capacity = state.clipMaxFrames.distinct()
                                                .joinToString("/") { frames ->
                                                    val secs = String.format(java.util.Locale.US, "%.1f", frames / state.fps)
                                                    "$frames frames (≈${secs}s)"
                                                }
                                            append(" — ${state.clipSlots.size} clips × up to $capacity")
                                            if (state.unassignedFrames > 0) {
                                                append(", ${state.unassignedFrames} frames unassigned")
                                            }
                                        }
                                    }
                                    clipFpsText = fpsLabel
                                    update {
                                        copy(
                                            clipFps = state.fps,
                                            detectedStillSlots = state.stillSlots.size,
                                            detectedClipSlots = state.clipSlots.size,
                                            detectedClipMaxFrames = state.clipMaxFrames,
                                            detectedUnassignedFrames = state.unassignedFrames,
                                            detectedMixEffects = state.mixEffectCount,
                                            detectedKeyersPerMe = state.keyersPerMe,
                                            detectedDownstreamKeyers = state.downstreamKeyers
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
            SettingsSection(
                title = stringResource(Res.string.atem_section_upload_defaults),
                modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)
            ) {
                // Everything on one line: width / height / fps / still slot / clip slot,
                // labels (and detected ranges) directly underneath each field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsTextField(
                        value = renderWidthText,
                        onValueChange = { v ->
                            renderWidthText = v
                            v.toIntOrNull()?.let { update { copy(renderWidth = it) } }
                        },
                        label = stringResource(Res.string.atem_render_width),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    SettingsTextField(
                        value = renderHeightText,
                        onValueChange = { v ->
                            renderHeightText = v
                            v.toIntOrNull()?.let { update { copy(renderHeight = it) } }
                        },
                        label = stringResource(Res.string.atem_render_height),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    SettingsTextField(
                        value = clipFpsText,
                        onValueChange = { v ->
                            clipFpsText = v
                            v.toDoubleOrNull()?.let { update { copy(clipFps = it) } }
                        },
                        label = stringResource(Res.string.atem_clip_fps_unit),
                        placeholder = { Text(stringResource(Res.string.atem_clip_fps_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    SettingsTextField(
                        value = stillSlotText,
                        onValueChange = { v ->
                            stillSlotText = v
                            v.toIntOrNull()?.let { update { copy(defaultStillSlot = (it - 1).coerceAtLeast(0)) } }
                        },
                        label = run {
                            val l = stringResource(Res.string.atem_default_still_slot)
                            if (atem.detectedStillSlots > 0) "$l (1–${atem.detectedStillSlots})" else l
                        },
                        isError = atem.detectedStillSlots > 0 &&
                            stillSlotText.toIntOrNull()?.let { it !in 1..atem.detectedStillSlots } != false,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    SettingsTextField(
                        value = clipSlotText,
                        onValueChange = { v ->
                            clipSlotText = v
                            v.toIntOrNull()?.let { update { copy(defaultClipSlot = (it - 1).coerceAtLeast(0)) } }
                        },
                        label = run {
                            val l = stringResource(Res.string.atem_default_clip_slot)
                            if (atem.detectedClipSlots > 0) "$l (1–${atem.detectedClipSlots})" else l
                        },
                        isError = atem.detectedClipSlots > 0 &&
                            clipSlotText.toIntOrNull()?.let { it !in 1..atem.detectedClipSlots } != false,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Standing reference: how much clip the ATEM can hold (persisted from the
                // last successful Test Connection)
                if (atem.detectedClipMaxFrames.isNotEmpty() && atem.clipFps > 0) {
                    val banks = atem.detectedClipMaxFrames
                    val distinct = banks.distinct()
                    val fpsLabel = formatAtemFps(atem.clipFps)
                    val base = if (distinct.size == 1) {
                        val secs = String.format(java.util.Locale.US, "%.1f", distinct[0] / atem.clipFps)
                        stringResource(Res.string.atem_capacity_equal, banks.size, distinct[0], secs, fpsLabel)
                    } else {
                        stringResource(Res.string.atem_capacity_mixed, banks.size, distinct.joinToString(" / "), fpsLabel)
                    }
                    val suffix = if (atem.detectedUnassignedFrames > 0) {
                        stringResource(Res.string.atem_capacity_unassigned, atem.detectedUnassignedFrames)
                    } else ""
                    Text(
                        base + suffix,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                } else {
                    Text(
                        stringResource(Res.string.atem_capacity_unknown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Key sequencing defaults for the Companion / Go-Live trigger: an upstream keyer
                // (M/E + keyer) or a downstream keyer (DSK), plus the margins around the animation.
                val useDsk = atem.useDownstreamKey
                val keyersOnMe = atem.detectedKeyersPerMe.getOrNull(atem.keyMixEffect) ?: 0

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (useDsk) {
                        SettingsTextField(
                            value = dskText,
                            onValueChange = { v ->
                                dskText = v
                                v.toIntOrNull()?.let { update { copy(dskIndex = (it - 1).coerceAtLeast(0)) } }
                            },
                            label = if (atem.detectedDownstreamKeyers > 0) "DSK (1–${atem.detectedDownstreamKeyers})" else "DSK",
                            isError = atem.detectedDownstreamKeyers > 0 &&
                                dskText.toIntOrNull()?.let { it !in 1..atem.detectedDownstreamKeyers } != false,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    } else {
                        SettingsTextField(
                            value = meText,
                            onValueChange = { v ->
                                meText = v
                                v.toIntOrNull()?.let { update { copy(keyMixEffect = (it - 1).coerceAtLeast(0)) } }
                            },
                            label = if (atem.detectedMixEffects > 0) "M/E (1–${atem.detectedMixEffects})" else "M/E",
                            isError = atem.detectedMixEffects > 0 &&
                                meText.toIntOrNull()?.let { it !in 1..atem.detectedMixEffects } != false,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        SettingsTextField(
                            value = keyText,
                            onValueChange = { v ->
                                keyText = v
                                v.toIntOrNull()?.let { update { copy(keyIndex = (it - 1).coerceAtLeast(0)) } }
                            },
                            label = if (keyersOnMe > 0) "Key (1–$keyersOnMe)" else "Key",
                            isError = keyersOnMe > 0 &&
                                keyText.toIntOrNull()?.let { it !in 1..keyersOnMe } != false,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    SettingsTextField(
                        value = keyPreRollText,
                        onValueChange = { v ->
                            keyPreRollText = v
                            v.toIntOrNull()?.let { update { copy(keyPreRollMs = it.coerceAtLeast(0)) } }
                        },
                        label = stringResource(Res.string.atem_key_preroll),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    SettingsTextField(
                        value = keyPostRollText,
                        onValueChange = { v ->
                            keyPostRollText = v
                            v.toIntOrNull()?.let { update { copy(keyPostRollMs = it.coerceAtLeast(0)) } }
                        },
                        label = stringResource(Res.string.atem_key_postroll),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Detected M/E + keyer matrix, so the user knows the valid ranges
                if (atem.detectedKeyersPerMe.isNotEmpty()) {
                    val perMe = atem.detectedKeyersPerMe.mapIndexed { i, k -> "M/E ${i + 1}: ${k} keys" }
                        .joinToString("   ") +
                        (if (atem.detectedDownstreamKeyers > 0) "   DSK: ${atem.detectedDownstreamKeyers}" else "")
                    Text(
                        stringResource(Res.string.atem_detected_keyers, perMe),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                } else {
                    Text(
                        stringResource(Res.string.atem_detected_keyers_unknown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))

                // Key type: drive the API / Go Live key as a downstream keyer instead of upstream
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Switch(
                        checked = atem.useDownstreamKey,
                        onCheckedChange = { update { copy(useDownstreamKey = it) } }
                    )
                    Column {
                        Text(
                            "Downstream keyer (DSK)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Drive the key as a downstream keyer instead of an upstream keyer (USK)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Quick upload: one-press upload to the default slots, no dialog
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Switch(
                        checked = atem.quickUpload,
                        onCheckedChange = { update { copy(quickUpload = it) } }
                    )
                    Column {
                        Text(
                            stringResource(Res.string.atem_quick_upload),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(Res.string.atem_quick_upload_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Go Live drives the key: the tab's Go Live runs the timed key sequence
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Switch(
                        checked = atem.goLiveKey,
                        onCheckedChange = { update { copy(goLiveKey = it) } }
                    )
                    Column {
                        Text(
                            stringResource(Res.string.atem_golive_key),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(Res.string.atem_golive_key_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

/** "25", "59.94" — exact fps without truncation, locale-independent decimal point. */
internal fun formatAtemFps(fps: Double): String =
    if (fps == kotlin.math.floor(fps)) fps.toInt().toString()
    else String.format(java.util.Locale.US, "%.2f", fps).trimEnd('0').trimEnd('.')

