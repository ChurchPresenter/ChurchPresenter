package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileScaleRow(profile: OutputProfile, onProfileChange: (OutputProfile) -> Unit) {
    // Flowing, not a plain Row. Each choice is a label plus three 76dp segments, and the pair needs
    // a little over 400dp to sit on one line -- measured, in `ProfileScaleRowWrapTest`, which fails
    // against a plain Row at 360dp and passes here. A Row does not wrap: below that it laid them out
    // at full width anyway and the second one, Media, went off the right edge, so a profile showing
    // both pictures and video lost the video's own control entirely on a narrow enough window.
    // The same fix, for the same reason, as the strip rows in `CustomizeStripRows`.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
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
