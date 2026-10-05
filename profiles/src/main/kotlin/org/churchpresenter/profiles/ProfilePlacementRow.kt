package org.churchpresenter.profiles

import androidx.compose.runtime.Composable
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.media
import org.churchpresenter.strings.generated.resources.profile_mode_full
import org.churchpresenter.strings.generated.resources.lower_third_placement_in_band
import org.churchpresenter.strings.generated.resources.pictures
import org.churchpresenter.strings.generated.resources.presentation
import org.churchpresenter.strings.generated.resources.projection_content_web
import org.churchpresenter.strings.generated.resources.tab_canvas
import org.churchpresenter.settings.LowerThirdPlacement
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.PlaceableContent
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The content kinds this profile shows, each with its label — the ones a placement means anything for. */
internal fun OutputProfile.placeableShown(): List<PlaceableContent> = buildList {
    if (look.media.video) add(PlaceableContent.MEDIA)
    if (look.media.pictures) {
        add(PlaceableContent.PRESENTATION)
        add(PlaceableContent.PICTURES)
    }
    if (look.slide.web) add(PlaceableContent.WEBSITE)
    if (look.slide.canvas) add(PlaceableContent.CANVAS)
}

internal fun PlaceableContent.label(): StringResource = when (this) {
    PlaceableContent.MEDIA -> Res.string.media
    PlaceableContent.PRESENTATION -> Res.string.presentation
    PlaceableContent.PICTURES -> Res.string.pictures
    PlaceableContent.WEBSITE -> Res.string.projection_content_web
    PlaceableContent.CANVAS -> Res.string.tab_canvas
}

private fun LowerThirdPlacement.label(): StringResource = when (this) {
    LowerThirdPlacement.FULL_SCREEN -> Res.string.profile_mode_full
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
