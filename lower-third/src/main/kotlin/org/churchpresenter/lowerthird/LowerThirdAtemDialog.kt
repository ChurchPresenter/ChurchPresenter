package org.churchpresenter.lowerthird

import org.churchpresenter.sharedui.composables.LabeledRadioButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import org.churchpresenter.theme.AppShape
import androidx.compose.material3.AlertDialog
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.SunkenOutlinedTextField
import androidx.compose.material3.Text
import org.churchpresenter.theme.components.GhostButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.atem_loading_slots
import org.churchpresenter.strings.generated.resources.atem_slot_empty
import org.churchpresenter.strings.generated.resources.atem_slot_in_use
import org.churchpresenter.strings.generated.resources.atem_slot_named
import org.churchpresenter.strings.generated.resources.atem_slot_unnamed
import org.churchpresenter.strings.generated.resources.atem_mode_clip
import org.churchpresenter.strings.generated.resources.atem_mode_still
import org.churchpresenter.strings.generated.resources.atem_aspect_mismatch
import org.churchpresenter.strings.generated.resources.atem_clip_capacity_info
import org.churchpresenter.strings.generated.resources.atem_clip_too_long
import org.churchpresenter.strings.generated.resources.atem_upscale_notice
import org.churchpresenter.strings.generated.resources.atem_preparing
import org.churchpresenter.strings.generated.resources.atem_ready
import org.churchpresenter.strings.generated.resources.atem_send_to_atem
import org.churchpresenter.strings.generated.resources.atem_slot
import org.churchpresenter.strings.generated.resources.atem_slots_error
import org.churchpresenter.strings.generated.resources.atem_upload
import org.churchpresenter.strings.generated.resources.atem_upload_error
import org.churchpresenter.strings.generated.resources.atem_upload_mode
import org.churchpresenter.strings.generated.resources.atem_uploading
import org.churchpresenter.atem.AtemMediaSlot
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.atem.formatAtemFps
import org.churchpresenter.lowerthird.render.LottieRenderCache
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.semantic

/** The Send to ATEM dialog: mode, slot, clip capacity, and preparation and upload progress. */
@Composable
internal fun LowerThirdTabScope.LowerThirdAtemDialog() {
    if (!showAtemDialog) return
    // Pre-upload capacity check: an over-capacity clip upload is guaranteed to fail,
    // so block it up front instead of minutes into the transfer
    val atemClipFramesNeeded = if (atemIsClip) atemVariant(atemIsClip).frameCount else 0
    val atemSlotCapacity = atemClipMaxFrames.getOrNull(atemSlot)
    val atemClipTooLong = atemIsClip && atemSlotCapacity != null && atemClipFramesNeeded > atemSlotCapacity
    AlertDialog(
        onDismissRequest = { if (!atemBusy) showAtemDialog = false },
        title = { Text(stringResource(Res.string.atem_send_to_atem)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Mode selection
                Text(stringResource(Res.string.atem_upload_mode), style = MaterialTheme.typography.labelMedium)
                AtemUploadModeRow()

                AtemFrameWarnings()

                AtemSlotPicker()

                // Detected FPS + clip pool capacity (clips only)
                if (atemIsClip) {
                    val detectedFps = atemDetectedFps
                    val fpsUsed = detectedFps ?: appSettings.atemSettings.clipFps
                    if (detectedFps != null || atemSlotCapacity != null) {
                        val parts = buildList {
                            if (detectedFps != null) add("${formatAtemFps(detectedFps)} fps")
                            if (atemSlotCapacity != null) {
                                val secs = String.format(java.util.Locale.US, "%.1f", atemSlotCapacity / fpsUsed)
                                add(
                                    stringResource(
                                        Res.string.atem_clip_capacity_info,
                                        atemClipFramesNeeded, atemSlotCapacity, secs
                                    )
                                )
                            }
                        }
                        Text(
                            "ATEM: ${parts.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    if (atemClipTooLong) {
                        val maxSecs = String.format(java.util.Locale.US, "%.1f", atemSlotCapacity / fpsUsed)
                        Text(
                            stringResource(
                                Res.string.atem_clip_too_long,
                                atemClipFramesNeeded, atemSlot + 1, atemSlotCapacity, maxSecs
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                AtemUploadProgress()
            }
        },
        confirmButton = {
            RaisedButton(
                onClick = {
                    startAtemUpload(atemVariant(atemIsClip), atemSlot, closeDialogOnSuccess = true)
                },
                enabled = !atemBusy && !atemClipTooLong,
                shape = AppShape(8.dp)
            ) {
                Text(stringResource(Res.string.atem_upload))
            }
        },
        dismissButton = {
            GhostButton(
                shape = AppShape(6.dp),
                onClick = { showAtemDialog = false },
                enabled = !atemBusy
            ) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}

@Composable
private fun LowerThirdTabScope.AtemUploadModeRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        LabeledRadioButton(
            selected = !atemIsClip,
            onClick = {
                atemIsClip = false
                atemSlot = appSettings.atemSettings.defaultStillSlot
            },
            label = stringResource(Res.string.atem_mode_still),
        )
        Spacer(Modifier.width(16.dp))
        LabeledRadioButton(
            selected = atemIsClip,
            onClick = {
                atemIsClip = true
                atemSlot = appSettings.atemSettings.defaultClipSlot
            },
            label = stringResource(Res.string.atem_mode_clip),
        )
    }
}

/** Warns when the design does not match the ATEM frame. */
@Composable
private fun LowerThirdTabScope.AtemFrameWarnings() {
    val canvasSize = remember(jsonContent) { LottieRenderCache.lottieCanvasSize(jsonContent) }
    if (canvasSize != null) {
        val (cw, ch) = canvasSize
        val s = appSettings.atemSettings
        val designAspect = cw.toFloat() / ch
        val frameAspect = s.renderWidth.toFloat() / s.renderHeight
        if (kotlin.math.abs(designAspect - frameAspect) > LOWER_THIRD_ASPECT_EPSILON) {
            Text(
                stringResource(
                    Res.string.atem_aspect_mismatch,
                    "${cw}×${ch}", "${s.renderWidth}×${s.renderHeight}"
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.semantic.warning
            )
        }
        val fitScale = minOf(s.renderWidth.toFloat() / cw, s.renderHeight.toFloat() / ch)
        if (fitScale > LOWER_THIRD_MAX_FIT_SCALE) {
            Text(
                stringResource(
                    Res.string.atem_upscale_notice,
                    "${cw}×${ch}",
                    String.format(java.util.Locale.US, "%.1f", fitScale),
                    "${s.renderWidth}×${s.renderHeight}"
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.semantic.warning
            )
        }
    }
}

/** The slot: a dropdown when the slots are loaded, a text field otherwise. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LowerThirdTabScope.AtemSlotPicker() {
    // Slot — dropdown when slots are loaded, text field fallback
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.atem_slot), style = MaterialTheme.typography.labelMedium)
        when {
            atemSlotsLoading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(Res.string.atem_loading_slots), style = MaterialTheme.typography.bodySmall)
                }
            }
            atemSlots.isNotEmpty() -> {
                var slotExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = slotExpanded,
                    onExpandedChange = { slotExpanded = !slotExpanded }
                ) {
                    SunkenOutlinedTextField(
                        value = atemSlotLabel(atemSlot, atemSlots),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(slotExpanded) },
                        singleLine = true,
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = slotExpanded,
                        onDismissRequest = { slotExpanded = false }
                    ) {
                        atemSlots.forEach { slot ->
                            DropdownMenuItem(
                                text = { Text(atemSlotLabel(slot.index, atemSlots)) },
                                onClick = { atemSlot = slot.index; slotExpanded = false }
                            )
                        }
                    }
                }
            }
            else -> {
                // Manual entry fallback — displayed 1-based like ATEM Software Control
                SunkenOutlinedTextField(
                    value = (atemSlot + 1).toString(),
                    onValueChange = { it.toIntOrNull()?.let { v -> atemSlot = (v - 1).coerceAtLeast(0) } },
                    singleLine = true,
                    modifier = Modifier.width(100.dp)
                )
                val slotsErr = atemSlotsError
                if (slotsErr != null) {
                    Text(
                        stringResource(Res.string.atem_slots_error, slotsErr),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/** Preparation and upload status, and the upload error. */
@Composable
private fun LowerThirdTabScope.AtemUploadProgress() {
    val p = atemProgress
    when {
        p != null -> {
            Text(stringResource(Res.string.atem_uploading), style = MaterialTheme.typography.labelSmall)
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
        }
        atemPrepareProgress < 1f -> {
            Text(stringResource(Res.string.atem_preparing), style = MaterialTheme.typography.labelSmall)
            LinearProgressIndicator(progress = { atemPrepareProgress }, modifier = Modifier.fillMaxWidth())
        }
        else -> {
            Text(
                stringResource(Res.string.atem_ready),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.semantic.success
            )
        }
    }
    val e = atemError
    if (e != null) {
        Text(
            stringResource(Res.string.atem_upload_error, e),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun atemSlotLabel(index: Int, slots: List<AtemMediaSlot>): String {
    // Display 1-based to match ATEM Software Control's numbering (protocol is 0-based)
    val display = index + 1
    val slot = slots.find { it.index == index }
    return when {
        slot == null           -> stringResource(Res.string.atem_slot_unnamed, display)
        slot.name.isNotBlank() -> stringResource(Res.string.atem_slot_named, display, slot.name)
        slot.isUsed            -> stringResource(Res.string.atem_slot_in_use, display)
        else                   -> stringResource(Res.string.atem_slot_empty, display)
    }
}
