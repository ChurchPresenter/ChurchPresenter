package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.runtime.Composable
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.media_scale_mode_all
import churchpresenter.composeapp.generated.resources.media_scale_mode_mixed_all
import churchpresenter.composeapp.generated.resources.picture_scale_mode_all
import churchpresenter.composeapp.generated.resources.picture_scale_mode_mixed_all
import org.churchpresenter.settings.OutputScaleMode
import org.jetbrains.compose.resources.stringResource

/**
 * The Pictures and Media tabs' scale button's tooltip.
 *
 * Says two things the old wording left out, both of which made the setting hard to place. It named
 * neither the content -- "Scale: Fit" is the same sentence on both tabs, though one governs photos
 * and the other video -- nor its reach: the button is a shortcut that writes to **every profile**,
 * while the per-profile control of the same name lives in Settings → Profiles → Scale. The same
 * three words appearing in three places with two different scopes is what made two settings read as
 * four.
 *
 * [shared] is null while the profiles disagree, in which case the tooltip says so and names what one
 * press would set them all to.
 */
@Composable
internal fun scaleButtonLabel(
    shared: OutputScaleMode?,
    current: OutputScaleMode,
    content: ScaleButtonContent,
): String {
    val pictures = content == ScaleButtonContent.PICTURES
    return if (shared == null) {
        stringResource(
            if (pictures) Res.string.picture_scale_mode_mixed_all else Res.string.media_scale_mode_mixed_all,
            stringResource(current.label),
        )
    } else {
        stringResource(
            if (pictures) Res.string.picture_scale_mode_all else Res.string.media_scale_mode_all,
            stringResource(shared.label),
        )
    }
}
