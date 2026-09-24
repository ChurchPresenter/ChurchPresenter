package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.canvas_scale_fill
import churchpresenter.composeapp.generated.resources.canvas_scale_fit
import churchpresenter.composeapp.generated.resources.canvas_scale_stretch
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.OutputScaleMode
import org.jetbrains.compose.resources.StringResource

/** How the Pictures and Media outputs draw in this mode. */
internal val OutputScaleMode.contentScale: ContentScale
    get() = when (this) {
        OutputScaleMode.FIT -> ContentScale.Fit
        OutputScaleMode.FILL -> ContentScale.Crop
        OutputScaleMode.STRETCH -> ContentScale.FillBounds
    }

/** The mode's name, shared with a Canvas source's scale dropdown. */
internal val OutputScaleMode.label: StringResource
    get() = when (this) {
        OutputScaleMode.FIT -> Res.string.canvas_scale_fit
        OutputScaleMode.FILL -> Res.string.canvas_scale_fill
        OutputScaleMode.STRETCH -> Res.string.canvas_scale_stretch
    }

/** The scale button's icon, so the mode reads at a glance without hovering. */
internal val OutputScaleMode.icon: ImageVector
    get() = when (this) {
        OutputScaleMode.FIT -> Icons.Filled.FitScreen
        OutputScaleMode.FILL -> Icons.Filled.Crop
        OutputScaleMode.STRETCH -> Icons.Filled.AspectRatio
    }

/**
 * The scale every profile in [profiles] uses for one kind of content, or `null` when they differ.
 *
 * What the Pictures and Media tabs' scale button shows: the scaling is set per profile, and the
 * button is a shortcut over all of them, so it can only name one mode when there is one to name.
 */
internal fun sharedScaleMode(
    profiles: List<OutputProfile>,
    read: (OutputProfile) -> OutputScaleMode,
): OutputScaleMode? = profiles.map(read).distinct().singleOrNull()

/**
 * [mode] for pictures on every profile -- the tab button's one press -- and as the document's own
 * value, which the Pictures tab's own preview still draws with.
 */
internal fun AppSettings.withPictureScaleEverywhere(mode: OutputScaleMode): AppSettings = copy(
    pictureSettings = pictureSettings.copy(scaleMode = mode),
    projectionSettings = projectionSettings.copy(
        outputProfiles = projectionSettings.outputProfiles.map { it.copy(pictureScaleMode = mode) },
    ),
)

/** [withPictureScaleEverywhere] for video. */
internal fun AppSettings.withMediaScaleEverywhere(mode: OutputScaleMode): AppSettings = copy(
    mediaScaleMode = mode,
    projectionSettings = projectionSettings.copy(
        outputProfiles = projectionSettings.outputProfiles.map { it.copy(mediaScaleMode = mode) },
    ),
)
