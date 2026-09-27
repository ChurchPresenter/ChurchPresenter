package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.content_media
import churchpresenter.composeapp.generated.resources.lower_third_placement_full_screen
import churchpresenter.composeapp.generated.resources.lower_third_placement_in_band
import churchpresenter.composeapp.generated.resources.pictures
import churchpresenter.composeapp.generated.resources.presentation
import churchpresenter.composeapp.generated.resources.projection_content_web
import churchpresenter.composeapp.generated.resources.tab_canvas
import org.churchpresenter.settings.LowerThirdPlacement
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.PlaceableContent
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

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

internal fun PlaceableContent.label(): StringResource = when (this) {
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
 * band's rectangle. One row per kind the profile shows.
 */
@Composable
internal fun PlacementRows(profile: OutputProfile, onProfileChange: (OutputProfile) -> Unit) {
    profile.placeableShown().forEach { content ->
        SettingsRow(stringResource(content.label()), paths = listOf("$PLACEMENTS_PATH.${content.name}")) {
            RowSegmented(
                options = LowerThirdPlacement.entries.map { RowOption(it, stringResource(it.label())) },
                selected = profile.placementFor(content),
                onSelect = { picked ->
                    onProfileChange(
                        profile.copy(lowerThirdPlacements = profile.lowerThirdPlacements + (content to picked)),
                    )
                },
            )
        }
    }
}
