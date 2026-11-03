package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants

/** Which full-screen background a picture or clip is set as: the default behind everything, or one content's. */
enum class BackgroundContent(val surface: BackgroundSurface) {
    DEFAULT(BackgroundSurface.DEFAULT),
    SONG(BackgroundSurface.SONG),
    BIBLE(BackgroundSurface.BIBLE),
}

/**
 * [this] with the picture or clip at [path] set as the [content] background — a video when
 * [isVideo] — and saved.
 *
 * With [profileId] null it is every output's: the Background tab's, and the copy of that surface
 * any profile keeps of its own, so no output is left showing the old one. With a [profileId] it is
 * that profile's own, and so every output's that uses it; a linked profile keeps it as a value of
 * its own ([editProfile]). An unknown profile leaves the settings as they are.
 */
fun AppSettings.withBackgroundSet(
    path: String,
    isVideo: Boolean,
    content: BackgroundContent,
    profileId: String?,
): AppSettings {
    val surface = content.surface.name
    val projection = projectionSettings
    return if (profileId == null) {
        val owners = projection.outputProfiles.filter { surface in it.backgroundOverrides }.map { it.id }
        val edited = owners.fold(projection) { proj, id ->
            proj.editProfile(id) { it.withOwnBackground(backgroundSettings, path, isVideo, content) }
        }
        copy(
            backgroundSettings = backgroundSettings.withBackground(path, isVideo, content),
            projectionSettings = edited,
        )
    } else {
        if (projection.outputProfiles.none { it.id == profileId }) return this
        copy(
            projectionSettings = projection.editProfile(profileId) {
                it.withOwnBackground(backgroundSettings, path, isVideo, content)
            },
        )
    }
}

/**
 * [this] keeping its own [content] background, set to [path]. A surface it followed until now starts
 * from what it showed — [global]'s — so only the picture changes, not its opacity, dim or blur.
 */
private fun OutputProfile.withOwnBackground(
    global: BackgroundSettings,
    path: String,
    isVideo: Boolean,
    content: BackgroundContent,
): OutputProfile {
    val shown = resolveBackgroundSurfaces(global, backgroundSettings, backgroundOverrides)
    return copy(
        backgroundOverrides = backgroundOverrides + content.surface.name,
        backgroundSettings = shown.withBackground(path, isVideo, content),
    )
}

/** [this] with the [content] surface showing the picture or clip at [path]. */
internal fun BackgroundSettings.withBackground(
    path: String,
    isVideo: Boolean,
    content: BackgroundContent,
): BackgroundSettings {
    val type = if (isVideo) Constants.BACKGROUND_VIDEO else Constants.BACKGROUND_IMAGE
    return when (content) {
        BackgroundContent.DEFAULT -> if (isVideo) {
            copy(defaultBackgroundType = type, defaultBackgroundVideo = path)
        } else {
            copy(defaultBackgroundType = type, defaultBackgroundImage = path)
        }
        BackgroundContent.SONG -> copy(songBackground = songBackground.showing(type, path))
        BackgroundContent.BIBLE -> copy(bibleBackground = bibleBackground.showing(type, path))
    }
}

/** [this] showing [path] as its own background of [type], no longer following the level before it. */
private fun BackgroundConfig.showing(type: String, path: String): BackgroundConfig =
    if (type == Constants.BACKGROUND_VIDEO) {
        copy(backgroundType = type, backgroundVideo = path, ownBackgroundType = "")
    } else {
        copy(backgroundType = type, backgroundImage = path, ownBackgroundType = "")
    }

/**
 * Whether the picture or clip at [path] is the [content] background already: the Background tab's,
 * for [profileId] null, or what that profile's outputs show. False for an unknown profile.
 */
fun AppSettings.backgroundShows(path: String, content: BackgroundContent, profileId: String?): Boolean {
    val shown = if (profileId == null) {
        backgroundSettings
    } else {
        val profile = projectionSettings.outputProfiles.find { it.id == profileId } ?: return false
        resolveBackgroundSurfaces(backgroundSettings, profile.backgroundSettings, profile.backgroundOverrides)
    }
    return shown.shows(path, content)
}

/** Whether the [content] surface of [this] shows [path], as its picture or its clip. */
private fun BackgroundSettings.shows(path: String, content: BackgroundContent): Boolean = when (content) {
    BackgroundContent.DEFAULT -> when (defaultBackgroundType) {
        Constants.BACKGROUND_IMAGE -> defaultBackgroundImage == path
        Constants.BACKGROUND_VIDEO -> defaultBackgroundVideo == path
        else -> false
    }
    BackgroundContent.SONG -> songBackground.shows(path)
    BackgroundContent.BIBLE -> bibleBackground.shows(path)
}

private fun BackgroundConfig.shows(path: String): Boolean = when (backgroundType) {
    Constants.BACKGROUND_IMAGE -> backgroundImage == path
    Constants.BACKGROUND_VIDEO -> backgroundVideo == path
    else -> false
}
