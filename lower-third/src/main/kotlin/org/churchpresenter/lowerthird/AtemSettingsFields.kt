package org.churchpresenter.lowerthird

import org.churchpresenter.atem.AtemState
import org.churchpresenter.atem.formatAtemFps
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import org.churchpresenter.theme.components.SettingsTextField
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.atem_capacity_unassigned
import org.churchpresenter.strings.generated.resources.atem_state_clips
import org.churchpresenter.strings.generated.resources.atem_state_frames
import org.churchpresenter.strings.generated.resources.atem_state_mode
import org.jetbrains.compose.resources.getString
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType

/** The switcher's video mode and frame rate, and how much clip memory it holds, in one line. */
internal suspend fun describeAtemState(state: AtemState): String {
    val fpsLabel = formatAtemFps(state.fps)
    val mode = getString(Res.string.atem_state_mode, state.videoMode, fpsLabel)
    if (state.clipMaxFrames.isEmpty()) return mode
    val capacity = state.clipMaxFrames.distinct()
        .map { frames ->
            val secs = String.format(java.util.Locale.US, "%.1f", frames / state.fps)
            getString(Res.string.atem_state_frames, frames, secs)
        }
        .joinToString("/")
    val clips = getString(Res.string.atem_state_clips, state.clipSlots.size, capacity)
    val unassigned = if (state.unassignedFrames > 0) {
        getString(Res.string.atem_capacity_unassigned, state.unassignedFrames)
    } else ""
    return "$mode — $clips$unassigned"
}

/**
 * A slot, M/E or keyer number. Stored 0-based (protocol) but displayed 1-based like ATEM Software
 * Control; once the switcher has been [detected] to have that many, the label gives the range and a
 * number outside it shows as an error. [onSlot] receives the 0-based value.
 */
@Composable
internal fun AtemSlotField(baseLabel: String, slot: Int, detected: Int, onSlot: (Int) -> Unit) {
    var text by remember(slot) { mutableStateOf((slot + 1).toString()) }
    SettingsTextField(
        value = text,
        onValueChange = { v ->
            text = v
            v.toIntOrNull()?.let { onSlot((it - 1).coerceAtLeast(0)) }
        },
        label = if (detected > 0) "$baseLabel (1–$detected)" else baseLabel,
        isError = detected > 0 && text.toIntOrNull()?.let { it !in 1..detected } != false,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

/** A whole-number setting, saved whenever its text parses. */
@Composable
internal fun AtemNumberField(value: Int, label: String, onValue: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    SettingsTextField(
        value = text,
        onValueChange = { v ->
            text = v
            v.toIntOrNull()?.let(onValue)
        },
        label = label,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
