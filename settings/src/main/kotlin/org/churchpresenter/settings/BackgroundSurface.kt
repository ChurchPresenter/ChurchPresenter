package org.churchpresenter.settings

/**
 * The six background surfaces a profile can either follow or carry its own copy of.
 *
 * "Follow" means the surface is taken from the Background tab -- the global document, which is the
 * background shown when nothing is live and the base every profile starts from. "Override" means
 * this profile keeps its own.
 *
 * Per surface rather than per category because the two are genuinely separate choices: a profile
 * driving a stream may want its own song band while still following the house default behind
 * everything else.
 *
 * Stored on [OutputProfile.backgroundOverrides] as names of these entries, so a surface added later
 * defaults to "follow" on every existing profile rather than to whatever a new field's default
 * happened to be.
 */
enum class BackgroundSurface {
    DEFAULT,
    DEFAULT_LOWER_THIRD,
    BIBLE,
    BIBLE_LOWER_THIRD,
    SONG,
    SONG_LOWER_THIRD,
}

/**
 * [global] with each surface named in [overridden] replaced by [profile]'s copy of it.
 *
 * Field group by field group rather than wholesale, which is what makes the choice per surface. The
 * `@Transient` quick-tray picks are deliberately never taken from the profile: they are a live
 * control on the document being rendered, not stored state a profile could carry.
 */
fun resolveBackgroundSurfaces(
    global: BackgroundSettings,
    profile: BackgroundSettings,
    overridden: Set<String>,
): BackgroundSettings {
    var out = global
    if (BackgroundSurface.DEFAULT.name in overridden) {
        out = out.copy(
            defaultBackgroundColor = profile.defaultBackgroundColor,
            defaultBackgroundImage = profile.defaultBackgroundImage,
            defaultBackgroundVideo = profile.defaultBackgroundVideo,
            defaultBackgroundType = profile.defaultBackgroundType,
            defaultBackgroundOpacity = profile.defaultBackgroundOpacity,
            defaultBackgroundDim = profile.defaultBackgroundDim,
            defaultBackgroundBlur = profile.defaultBackgroundBlur,
            defaultBackgroundCamera = profile.defaultBackgroundCamera,
        )
    }
    if (BackgroundSurface.DEFAULT_LOWER_THIRD.name in overridden) {
        out = out.copy(
            defaultLowerThirdBackgroundColor = profile.defaultLowerThirdBackgroundColor,
            defaultLowerThirdBackgroundImage = profile.defaultLowerThirdBackgroundImage,
            defaultLowerThirdBackgroundVideo = profile.defaultLowerThirdBackgroundVideo,
            defaultLowerThirdBackgroundType = profile.defaultLowerThirdBackgroundType,
            defaultLowerThirdBackgroundOpacity = profile.defaultLowerThirdBackgroundOpacity,
            defaultLowerThirdBackgroundDim = profile.defaultLowerThirdBackgroundDim,
            defaultLowerThirdBackgroundBlur = profile.defaultLowerThirdBackgroundBlur,
            defaultLowerThirdBackgroundCamera = profile.defaultLowerThirdBackgroundCamera,
            defaultLowerThirdAboveBandType = profile.defaultLowerThirdAboveBandType,
            defaultLowerThirdAboveBandColor = profile.defaultLowerThirdAboveBandColor,
            defaultLowerThirdAboveBandOpacity = profile.defaultLowerThirdAboveBandOpacity,
            defaultLowerThirdAboveBandFillsBehindBand = profile.defaultLowerThirdAboveBandFillsBehindBand,
            defaultLowerThirdAboveBandImage = profile.defaultLowerThirdAboveBandImage,
            defaultLowerThirdAboveBandVideo = profile.defaultLowerThirdAboveBandVideo,
            defaultLowerThirdAboveBandCamera = profile.defaultLowerThirdAboveBandCamera,
        )
    }
    if (BackgroundSurface.BIBLE.name in overridden) out = out.copy(bibleBackground = profile.bibleBackground)
    if (BackgroundSurface.BIBLE_LOWER_THIRD.name in overridden) {
        out = out.copy(bibleLowerThirdBackground = profile.bibleLowerThirdBackground)
    }
    if (BackgroundSurface.SONG.name in overridden) out = out.copy(songBackground = profile.songBackground)
    if (BackgroundSurface.SONG_LOWER_THIRD.name in overridden) {
        out = out.copy(songLowerThirdBackground = profile.songLowerThirdBackground)
    }
    return out
}
