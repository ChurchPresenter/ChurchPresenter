package org.churchpresenter.profiles

import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.utils.Constants

/**
 * [this] following the level before it, with its own type kept aside so [ownAgain] can bring it
 * back -- every other field of its own is left exactly as it was.
 */
internal fun BackgroundConfig.followingDefault(scope: BackgroundScope): BackgroundConfig {
    val inherit = scope.inheritType.orEmpty()
    return if (backgroundType == inherit) this else copy(backgroundType = inherit, ownBackgroundType = backgroundType)
}

/**
 * [this] with a background of its own again: the one it had before it followed the level above, or
 * -- when it never had one -- seeded from [resolved], what was showing, rather than from black.
 */
internal fun BackgroundConfig.ownAgain(resolved: BackgroundConfig): BackgroundConfig =
    if (ownBackgroundType.isNotEmpty()) {
        copy(backgroundType = ownBackgroundType, ownBackgroundType = "")
    } else {
        seedFrom(resolved, this)
    }

/** [resolved]'s picture on [own]'s other fields -- a surface taken over keeps the look it had. */
private fun seedFrom(resolved: BackgroundConfig, own: BackgroundConfig): BackgroundConfig = own.copy(
    backgroundType = resolved.backgroundType.takeUnless {
        it == Constants.BACKGROUND_DEFAULT || it == Constants.BACKGROUND_FOLLOW_DEFAULT
    } ?: Constants.BACKGROUND_COLOR,
    backgroundColor = resolved.backgroundColor,
    backgroundImage = resolved.backgroundImage,
    backgroundVideo = resolved.backgroundVideo,
    backgroundOpacity = resolved.backgroundOpacity,
    dim = resolved.dim,
    blur = resolved.blur,
    camera = resolved.camera,
)
