package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.media
import org.churchpresenter.strings.generated.resources.pictures
import org.churchpresenter.sharedui.utils.label
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.OutputScaleMode
import org.jetbrains.compose.resources.stringResource

/**
 * How pictures and video meet this profile's screens -- fit, fill or stretch -- one row for each it
 * shows.
 *
 * Per profile because it answers to the screen's shape. The Pictures and Media tabs' own scale
 * buttons are a shortcut that sets it on every profile at once.
 */
@Composable
internal fun ScaleRows(profile: OutputProfile, onProfileChange: (OutputProfile) -> Unit) {
    if (profile.showPictures) {
        SettingsRow(stringResource(Res.string.pictures), paths = listOf("pictureScaleMode")) {
            ScaleSegments(profile.pictureScaleMode) { onProfileChange(profile.copy(pictureScaleMode = it)) }
        }
    }
    if (profile.showMedia) {
        SettingsRow(stringResource(Res.string.media), paths = listOf("mediaScaleMode")) {
            ScaleSegments(profile.mediaScaleMode) { onProfileChange(profile.copy(mediaScaleMode = it)) }
        }
    }
}

@Composable
private fun ScaleSegments(selected: OutputScaleMode, onSelect: (OutputScaleMode) -> Unit) {
    RowSegmented(
        options = OutputScaleMode.entries.map { RowOption(it, stringResource(it.label)) },
        selected = selected,
        onSelect = onSelect,
    )
}
