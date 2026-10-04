package org.churchpresenter.profiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.background_camera_option
import org.churchpresenter.strings.generated.resources.canvas_source_color
import org.churchpresenter.strings.generated.resources.background_default
import org.churchpresenter.strings.generated.resources.background_follow_default_option
import org.churchpresenter.strings.generated.resources.background_lottie_option
import org.churchpresenter.strings.generated.resources.background_group_defaults
import org.churchpresenter.strings.generated.resources.customize_type_image
import org.churchpresenter.strings.generated.resources.background_scope_default_lower_third
import org.churchpresenter.strings.generated.resources.background_scope_title
import org.churchpresenter.strings.generated.resources.background_transparent_option
import org.churchpresenter.strings.generated.resources.background_video_option
import org.churchpresenter.strings.generated.resources.bible
import org.churchpresenter.strings.generated.resources.display_lower_third
import org.churchpresenter.strings.generated.resources.full_screen
import org.churchpresenter.strings.generated.resources.gradient_enabled
import org.churchpresenter.strings.generated.resources.songs
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal fun backgroundGroupLabel(group: BackgroundScopeGroup): StringResource = when (group) {
    BackgroundScopeGroup.DEFAULTS -> Res.string.background_group_defaults
    BackgroundScopeGroup.BIBLE -> Res.string.bible
    BackgroundScopeGroup.SONGS -> Res.string.songs
}

internal fun backgroundScopeName(scope: BackgroundScope): StringResource = when (scope) {
    BackgroundScope.DEFAULT -> Res.string.background_default
    BackgroundScope.DEFAULT_LOWER_THIRD -> Res.string.background_scope_default_lower_third
    else -> if (scope.lowerThird) Res.string.display_lower_third else Res.string.full_screen
}

/** "Bible · Lower Third" for a content surface; a Default surface stands on its own name. */
@Composable
internal fun backgroundScopeTitle(scope: BackgroundScope): String =
    if (scope.group == BackgroundScopeGroup.DEFAULTS) stringResource(backgroundScopeName(scope))
    else stringResource(
        Res.string.background_scope_title,
        stringResource(backgroundGroupLabel(scope.group)),
        stringResource(backgroundScopeName(scope))
    )

internal fun backgroundTypeLabel(type: String): StringResource = when (type) {
    Constants.BACKGROUND_COLOR -> Res.string.canvas_source_color
    Constants.BACKGROUND_IMAGE -> Res.string.customize_type_image
    Constants.BACKGROUND_VIDEO -> Res.string.background_video_option
    Constants.BACKGROUND_CAMERA -> Res.string.background_camera_option
    Constants.BACKGROUND_TRANSPARENT -> Res.string.background_transparent_option
    Constants.BACKGROUND_GRADIENT -> Res.string.gradient_enabled
    Constants.BACKGROUND_FOLLOW_DEFAULT -> Res.string.background_follow_default_option
    Constants.BACKGROUND_LOTTIE -> Res.string.background_lottie_option
    else -> Res.string.background_default
}
