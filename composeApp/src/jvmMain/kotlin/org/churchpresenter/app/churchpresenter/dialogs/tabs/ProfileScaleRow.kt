package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.content_media
import churchpresenter.composeapp.generated.resources.pictures
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.app.churchpresenter.utils.label
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.OutputScaleMode
import org.jetbrains.compose.resources.stringResource

private val SCALE_SEGMENT_WIDTH = 76.dp
private val SCALE_SEGMENT_HEIGHT = 30.dp

/**
 * How pictures and video meet this profile's screens -- fit, fill or stretch -- one choice for each.
 *
 * Per profile because it answers to the screen's shape. The Pictures and Media tabs' own scale
 * buttons are a shortcut that sets it on every profile at once.
 */
@Composable
internal fun ProfileScaleRow(profile: OutputProfile, onProfileChange: (OutputProfile) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        if (profile.showPictures) {
            ScaleChoice(stringResource(Res.string.pictures), profile.pictureScaleMode) {
                onProfileChange(profile.copy(pictureScaleMode = it))
            }
        }
        if (profile.showMedia) {
            ScaleChoice(stringResource(Res.string.content_media), profile.mediaScaleMode) {
                onProfileChange(profile.copy(mediaScaleMode = it))
            }
        }
    }
}

@Composable
private fun ScaleChoice(label: String, selected: OutputScaleMode, onSelect: (OutputScaleMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SegmentedButton(
            items = OutputScaleMode.entries.map { SegmentedButtonItem(it, stringResource(it.label)) },
            selectedValue = selected,
            onValueChange = onSelect,
            buttonWidth = SCALE_SEGMENT_WIDTH,
            buttonHeight = SCALE_SEGMENT_HEIGHT,
            fontSize = MaterialTheme.typography.labelMedium.fontSize,
        )
    }
}
