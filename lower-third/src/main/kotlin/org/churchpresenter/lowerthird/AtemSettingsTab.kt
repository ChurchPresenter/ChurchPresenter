package org.churchpresenter.lowerthird

import org.churchpresenter.sharedui.guide.guideTarget
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.atem.AtemState
import org.churchpresenter.atem.formatAtemFps
import androidx.compose.foundation.background
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
import org.churchpresenter.strings.generated.resources.atem_downstream_keyer
import org.churchpresenter.strings.generated.resources.atem_downstream_keyer_hint
import org.churchpresenter.theme.AppShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.SettingsTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.atem_capacity_equal
import org.churchpresenter.strings.generated.resources.atem_capacity_mixed
import org.churchpresenter.strings.generated.resources.atem_capacity_unassigned
import org.churchpresenter.strings.generated.resources.atem_capacity_unknown
import org.churchpresenter.strings.generated.resources.atem_clip_fps_hint
import org.churchpresenter.strings.generated.resources.atem_clip_fps_unit
import org.churchpresenter.strings.generated.resources.atem_default_clip_slot
import org.churchpresenter.strings.generated.resources.atem_default_still_slot
import org.churchpresenter.strings.generated.resources.atem_section_background_uploads
import org.churchpresenter.strings.generated.resources.atem_background_slot_1
import org.churchpresenter.strings.generated.resources.atem_background_slot_2
import org.churchpresenter.strings.generated.resources.atem_description
import org.churchpresenter.strings.generated.resources.atem_detected_keyers
import org.churchpresenter.strings.generated.resources.atem_detected_keyers_unknown
import org.churchpresenter.strings.generated.resources.atem_key_postroll
import org.churchpresenter.strings.generated.resources.atem_key_preroll
import org.churchpresenter.strings.generated.resources.atem_golive_key
import org.churchpresenter.strings.generated.resources.atem_golive_key_hint
import org.churchpresenter.strings.generated.resources.atem_host
import org.churchpresenter.strings.generated.resources.atem_host_hint
import org.churchpresenter.strings.generated.resources.atem_quick_upload
import org.churchpresenter.strings.generated.resources.atem_quick_upload_hint
import org.churchpresenter.strings.generated.resources.atem_render_height
import org.churchpresenter.strings.generated.resources.atem_render_width
import org.churchpresenter.strings.generated.resources.atem_detected_video_mode
import org.churchpresenter.strings.generated.resources.atem_status_connected
import org.churchpresenter.strings.generated.resources.atem_status_connecting
import org.churchpresenter.strings.generated.resources.atem_status_disconnected
import org.churchpresenter.strings.generated.resources.atem_status_error
import org.churchpresenter.strings.generated.resources.atem_test_connection
import org.churchpresenter.strings.generated.resources.atem_section_connection
import org.churchpresenter.strings.generated.resources.atem_section_lower_third_uploads
import org.churchpresenter.strings.generated.resources.atem_render_resolution
import org.churchpresenter.strings.generated.resources.atem_test_connection_hint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import org.churchpresenter.sharedui.composables.SettingRow
import org.churchpresenter.sharedui.composables.SettingsScrollbar
import org.churchpresenter.sharedui.composables.SettingsScrollbarGutter
import org.churchpresenter.sharedui.composables.SettingsSection
import org.churchpresenter.atem.AtemClient
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.AtemSettings
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.sharedui.composables.LabeledSwitch
import org.churchpresenter.theme.semantic
import java.io.IOException

/** The port the ATEM listens on, used when the field does not hold a number. */
private const val DEFAULT_ATEM_PORT = 9910

@Composable
fun AtemSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    val atem = settings.atemSettings
    val update: (AtemSettings.() -> AtemSettings) -> Unit = { block ->
        onSettingsChange { s -> s.copy(atemSettings = s.atemSettings.block()) }
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
            AtemConnectionCard(atem, update)

            // ── Lower Third Uploads + Background Uploads, side by side ──────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AtemLowerThirdUploadsCard(atem, update, Modifier.weight(1f))
                AtemBackgroundUploadsCard(atem, update, Modifier.weight(1f))
            }
        }
        SettingsScrollbar(scrollState)
    }
}

/** Where the switcher is, the size and rate lower thirds render at, and the connection test. */
@Composable
private fun AtemConnectionCard(atem: AtemSettings, update: (AtemSettings.() -> AtemSettings) -> Unit) {
    var hostText by remember(atem.host) { mutableStateOf(atem.host) }
    var portText by remember(atem.port) { mutableStateOf(atem.port.toString()) }
    var clipFpsText by remember(atem.clipFps) { mutableStateOf(formatAtemFps(atem.clipFps)) }
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

        SettingRow(label = Res.string.atem_host) {
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

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.atem_render_resolution),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.widthIn(max = 350.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AtemNumberField(atem.renderWidth, stringResource(Res.string.atem_render_width)) {
                update { copy(renderWidth = it) }
            }
            AtemNumberField(atem.renderHeight, stringResource(Res.string.atem_render_height)) {
                update { copy(renderHeight = it) }
            }
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
        }

        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.atem_test_connection_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(8.dp))

        AtemTestConnectionRow(hostText, portText.toIntOrNull() ?: DEFAULT_ATEM_PORT) { state ->
            clipFpsText = formatAtemFps(state.fps)
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
        }
    }
}

/**
 * Test Connection: queries the switcher at [host]:[port], says how that went, and hands what it
 * found to [onDetected] to store.
 */
@Composable
private fun AtemTestConnectionRow(host: String, port: Int, onDetected: (AtemState) -> Unit) {
    var connectionStatus by remember { mutableStateOf<String?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var detectedVideoMode by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RaisedButton(
            shape = AppShape(6.dp),
            onClick = {
                if (isTesting) return@RaisedButton
                isTesting = true
                connectionStatus = null
                connectionError = null
                detectedVideoMode = null
                scope.launch {
                    try {
                        val state = withContext(Dispatchers.IO) { AtemClient(host, port).queryState() }
                        detectedVideoMode = describeAtemState(state)
                        onDetected(state)
                        connectionStatus = "connected"
                        connectionError = null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        // The ATEM link: AtemProtocolException is one.
                        connectionStatus = "error"
                        connectionError = e.message ?: "Unknown error"
                    } catch (e: IllegalStateException) {
                        connectionStatus = "error"
                        connectionError = e.message ?: "Unknown error"
                    } catch (e: IllegalArgumentException) {
                        // A host or port the socket cannot be pointed at.
                        connectionStatus = "error"
                        connectionError = e.message ?: "Unknown error"
                    } finally {
                        isTesting = false
                    }
                }
            },
            enabled = host.isNotBlank() && !isTesting,
            modifier = Modifier.guideTarget(GuideTargets.ATEM_TEST_CONNECTION),
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
                stringResource(Res.string.atem_status_connecting) to MaterialTheme.semantic.warning
            connectionStatus == "connected" ->
                stringResource(Res.string.atem_status_connected) to MaterialTheme.semantic.success
            connectionStatus == "error" ->
                stringResource(Res.string.atem_status_error, connectionError ?: "") to
                    MaterialTheme.colorScheme.error
            else ->
                stringResource(Res.string.atem_status_disconnected) to
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        }
        if (connectionStatus != null || isTesting) {
            Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
        }
        val detectedMode = detectedVideoMode
        if (detectedMode != null && connectionStatus == "connected") {
            Text(
                stringResource(Res.string.atem_detected_video_mode, detectedMode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.semantic.success
            )
        }
    }
}

/** Default slots for lower-third uploads, the key they go on air with, and how uploads behave. */
@Composable
private fun AtemLowerThirdUploadsCard(
    atem: AtemSettings,
    update: (AtemSettings.() -> AtemSettings) -> Unit,
    modifier: Modifier,
) {
    SettingsSection(
        title = stringResource(Res.string.atem_section_lower_third_uploads),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AtemSlotField(
                stringResource(Res.string.atem_default_still_slot),
                atem.defaultStillSlot, atem.detectedStillSlots,
            ) {
                update { copy(defaultStillSlot = it) }
            }
            AtemSlotField(
                stringResource(Res.string.atem_default_clip_slot),
                atem.defaultClipSlot, atem.detectedClipSlots,
            ) {
                update { copy(defaultClipSlot = it) }
            }
        }

        Spacer(Modifier.height(4.dp))
        AtemClipCapacity(atem)
        Spacer(Modifier.height(8.dp))
        AtemKeyFields(atem, update)
        Spacer(Modifier.height(4.dp))
        AtemDetectedKeyers(atem)

        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(8.dp))

        AtemUploadSwitches(atem, update)
    }
}

/**
 * How much clip the ATEM can hold, persisted from the last successful Test Connection -- or that it
 * is not known yet.
 */
@Composable
private fun AtemClipCapacity(atem: AtemSettings) {
    if (atem.detectedClipMaxFrames.isNotEmpty() && atem.clipFps > 0) {
        val banks = atem.detectedClipMaxFrames
        val distinct = banks.distinct()
        val fpsLabel = formatAtemFps(atem.clipFps)
        val base = if (distinct.size == 1) {
            val secs = String.format(java.util.Locale.US, "%.1f", distinct[0] / atem.clipFps)
            stringResource(Res.string.atem_capacity_equal, banks.size, distinct[0], secs, fpsLabel)
        } else {
            stringResource(
                Res.string.atem_capacity_mixed,
                banks.size,
                distinct.joinToString(" / "),
                fpsLabel
            )
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
}

/**
 * Key sequencing defaults for the Companion / Go-Live trigger: an upstream keyer (M/E + keyer) or a
 * downstream keyer (DSK), plus the margins around the animation.
 */
@Composable
private fun AtemKeyFields(atem: AtemSettings, update: (AtemSettings.() -> AtemSettings) -> Unit) {
    val keyersOnMe = atem.detectedKeyersPerMe.getOrNull(atem.keyMixEffect) ?: 0
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (atem.useDownstreamKey) {
            AtemSlotField("DSK", atem.dskIndex, atem.detectedDownstreamKeyers) { update { copy(dskIndex = it) } }
        } else {
            AtemSlotField("M/E", atem.keyMixEffect, atem.detectedMixEffects) { update { copy(keyMixEffect = it) } }
            AtemSlotField("Key", atem.keyIndex, keyersOnMe) { update { copy(keyIndex = it) } }
        }
        AtemNumberField(atem.keyPreRollMs, stringResource(Res.string.atem_key_preroll)) {
            update { copy(keyPreRollMs = it.coerceAtLeast(0)) }
        }
        AtemNumberField(atem.keyPostRollMs, stringResource(Res.string.atem_key_postroll)) {
            update { copy(keyPostRollMs = it.coerceAtLeast(0)) }
        }
    }
}

/** The detected M/E and keyer matrix, so the user knows the valid ranges. */
@Composable
private fun AtemDetectedKeyers(atem: AtemSettings) {
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
}

/** Downstream keying, one-press quick upload, and Go Live driving the key. */
@Composable
private fun AtemUploadSwitches(atem: AtemSettings, update: (AtemSettings.() -> AtemSettings) -> Unit) {
    // Key type: drive the API / Go Live key as a downstream keyer instead of upstream
    LabeledSwitch(
        checked = atem.useDownstreamKey,
        onCheckedChange = { update { copy(useDownstreamKey = it) } },
        label = stringResource(Res.string.atem_downstream_keyer),
        supporting = stringResource(Res.string.atem_downstream_keyer_hint),
        modifier = Modifier.fillMaxWidth().guideTarget(GuideTargets.ATEM_DSK_SWITCH),
        spacing = 12.dp,
    )

    Spacer(Modifier.height(8.dp))

    // Quick upload: one-press upload to the default slots, no dialog
    LabeledSwitch(
        checked = atem.quickUpload,
        onCheckedChange = { update { copy(quickUpload = it) } },
        label = stringResource(Res.string.atem_quick_upload),
        supporting = stringResource(Res.string.atem_quick_upload_hint),
        modifier = Modifier.fillMaxWidth(),
        spacing = 12.dp,
    )

    Spacer(Modifier.height(8.dp))

    // Go Live drives the key: the tab's Go Live runs the timed key sequence
    LabeledSwitch(
        checked = atem.goLiveKey,
        onCheckedChange = { update { copy(goLiveKey = it) } },
        label = stringResource(Res.string.atem_golive_key),
        supporting = stringResource(Res.string.atem_golive_key_hint),
        modifier = Modifier.fillMaxWidth(),
        spacing = 12.dp,
    )
}

/**
 * The two still slots background uploads (Settings → Backgrounds) go to. Its own card so they are
 * never confused with the lower-third slot fields beside it.
 */
@Composable
private fun AtemBackgroundUploadsCard(
    atem: AtemSettings,
    update: (AtemSettings.() -> AtemSettings) -> Unit,
    modifier: Modifier,
) {
    SettingsSection(
        title = stringResource(Res.string.atem_section_background_uploads),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AtemSlotField(
                stringResource(Res.string.atem_background_slot_1),
                atem.backgroundSlot1, atem.detectedStillSlots,
            ) {
                update { copy(backgroundSlot1 = it) }
            }
            AtemSlotField(
                stringResource(Res.string.atem_background_slot_2),
                atem.backgroundSlot2, atem.detectedStillSlots,
            ) {
                update { copy(backgroundSlot2 = it) }
            }
        }
    }
}
