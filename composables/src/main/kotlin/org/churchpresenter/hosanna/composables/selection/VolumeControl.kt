package org.churchpresenter.hosanna.composables.selection

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.churchpresenter.hosanna.composables.button.IconButton
import org.churchpresenter.hosanna.composables.defaults.VolumeDefaults
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.IconButtonSize
import org.churchpresenter.hosanna.composables.model.VolumeLabels
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_volume_off
import org.churchpresenter.icons.generated.resources.ic_volume_up
import org.jetbrains.compose.resources.painterResource

/**
 * A mute button, a volume slider and the level. Unmuting brings back the level from before.
 *
 * @param volume the level from 0 to 100.
 * @param onVolumeChange called with the new level.
 * @param labels the slider's and button's screen-reader names.
 * @param modifier applied to the outermost layout.
 * @param interactionSource drives the slider's looks.
 * @param muteInteractionSource drives the mute button's looks.
 * @param enabled whether it can be used.
 */
@Composable
fun VolumeControl(
    volume: Double,
    onVolumeChange: (Double) -> Unit,
    labels: VolumeLabels,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    muteInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
) {
    val muted = volume <= 0.0
    var restore by remember { mutableDoubleStateOf(VolumeDefaults.FULL) }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(VolumeDefaults.Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            icon = painterResource(if (muted) Res.drawable.ic_volume_off else Res.drawable.ic_volume_up),
            contentDescription = if (muted) labels.unmute else labels.mute,
            onClick = {
                if (muted) {
                    onVolumeChange(restore)
                } else {
                    restore = volume
                    onVolumeChange(0.0)
                }
            },
            interactionSource = muteInteractionSource,
            size = IconButtonSize.Small,
            hierarchy = ButtonHierarchy.Tertiary,
            enabled = enabled,
        )
        Slider(
            value = volume,
            onValueChange = onVolumeChange,
            contentDescription = labels.volume,
            modifier = Modifier.width(VolumeDefaults.Width),
            interactionSource = interactionSource,
            valueText = "${volume.toInt()}%",
            enabled = enabled,
        )
    }
}
