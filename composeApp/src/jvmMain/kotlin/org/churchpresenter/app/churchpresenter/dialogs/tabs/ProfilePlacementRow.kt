package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.content_media
import churchpresenter.composeapp.generated.resources.lower_third_placement_full_screen
import churchpresenter.composeapp.generated.resources.lower_third_placement_in_band
import churchpresenter.composeapp.generated.resources.pictures
import churchpresenter.composeapp.generated.resources.presentation
import churchpresenter.composeapp.generated.resources.projection_content_web
import churchpresenter.composeapp.generated.resources.tab_canvas
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.settings.LowerThirdPlacement
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.PlaceableContent
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val PLACEMENT_SEGMENT_WIDTH = 84.dp
private val PLACEMENT_SEGMENT_HEIGHT = 30.dp

/** The content kinds this profile shows, each with its label — the ones a placement means anything for. */
internal fun OutputProfile.placeableShown(): List<PlaceableContent> = buildList {
    if (showMedia) add(PlaceableContent.MEDIA)
    if (showPictures) {
        add(PlaceableContent.PRESENTATION)
        add(PlaceableContent.PICTURES)
    }
    if (showWebsite) add(PlaceableContent.WEBSITE)
    if (showCanvas) add(PlaceableContent.CANVAS)
}

private fun PlaceableContent.label(): StringResource = when (this) {
    PlaceableContent.MEDIA -> Res.string.content_media
    PlaceableContent.PRESENTATION -> Res.string.presentation
    PlaceableContent.PICTURES -> Res.string.pictures
    PlaceableContent.WEBSITE -> Res.string.projection_content_web
    PlaceableContent.CANVAS -> Res.string.tab_canvas
}

private fun LowerThirdPlacement.label(): StringResource = when (this) {
    LowerThirdPlacement.FULL_SCREEN -> Res.string.lower_third_placement_full_screen
    LowerThirdPlacement.IN_BAND -> Res.string.lower_third_placement_in_band
}

/**
 * Where each band-less kind of content sits on this lower-third profile: the whole output, or the
 * band's rectangle. One choice per kind the profile shows, laid out like [ProfileScaleRow].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfilePlacementRow(profile: OutputProfile, onProfileChange: (OutputProfile) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        profile.placeableShown().forEach { content ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(content.label()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SegmentedButton(
                    items = LowerThirdPlacement.entries.map { SegmentedButtonItem(it, stringResource(it.label())) },
                    selectedValue = profile.placementFor(content),
                    onValueChange = { picked ->
                        onProfileChange(
                            profile.copy(lowerThirdPlacements = profile.lowerThirdPlacements + (content to picked)),
                        )
                    },
                    buttonWidth = PLACEMENT_SEGMENT_WIDTH,
                    buttonHeight = PLACEMENT_SEGMENT_HEIGHT,
                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                )
            }
        }
    }
}
