/*
 * The six surfaces the Background tab configures, and the one shape it edits them all through.
 *
 * Four of them carry a [BackgroundConfig]; the two Defaults spell the same thing out in flat
 * fields on [BackgroundSettings]. Mapping both onto a config is what lets the tab have a single
 * editor instead of one per card, and keeps the inheritance a surface follows in one place rather
 * than in each preview that has to draw it.
 */
package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.presenter.lowerThirdBandFraction
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants

/** The three headings the surface rail groups its rows under. */
internal enum class BackgroundScopeGroup { DEFAULTS, BIBLE, SONGS }

/**
 * One background surface.
 *
 * [inheritType] is the type constant that means "whatever the surface above me says" — `Default`
 * for a content type, `FollowDefault` for the default lower third, and nothing at all for the
 * default full screen, which is where the chain ends.
 */
internal enum class BackgroundScope(
    val group: BackgroundScopeGroup,
    val lowerThird: Boolean,
    val inheritType: String?,
    val offersGradient: Boolean = false,
    /** Whether this band can play a Lottie template in place of a backdrop: the two content bands can. */
    val offersLottie: Boolean = false,
) {
    DEFAULT(BackgroundScopeGroup.DEFAULTS, lowerThird = false, inheritType = null),
    DEFAULT_LOWER_THIRD(
        BackgroundScopeGroup.DEFAULTS,
        lowerThird = true,
        inheritType = Constants.BACKGROUND_FOLLOW_DEFAULT,
    ),
    BIBLE(BackgroundScopeGroup.BIBLE, lowerThird = false, inheritType = Constants.BACKGROUND_DEFAULT),
    BIBLE_LOWER_THIRD(
        BackgroundScopeGroup.BIBLE,
        lowerThird = true,
        inheritType = Constants.BACKGROUND_DEFAULT,
        offersGradient = true,
        offersLottie = true,
    ),
    SONG(BackgroundScopeGroup.SONGS, lowerThird = false, inheritType = Constants.BACKGROUND_DEFAULT),
    SONG_LOWER_THIRD(
        BackgroundScopeGroup.SONGS,
        lowerThird = true,
        inheritType = Constants.BACKGROUND_DEFAULT,
        offersGradient = true,
        offersLottie = true,
    );

    /** The surface this one falls through to when its type is [inheritType]. */
    val inheritsFrom: BackgroundScope?
        get() = when (this) {
            DEFAULT -> null
            DEFAULT_LOWER_THIRD -> DEFAULT
            BIBLE, SONG -> DEFAULT
            BIBLE_LOWER_THIRD, SONG_LOWER_THIRD -> DEFAULT_LOWER_THIRD
        }
}

/**
 * The types this surface offers, in the order the segmented control shows them.
 *
 * Gradient reaches only the two content lower thirds, which is the one place a band is drawn over
 * something else and so the one place a fade to transparent means anything.
 */
internal fun BackgroundScope.typeOptions(): List<String> = buildList {
    inheritType?.let(::add)
    add(Constants.BACKGROUND_COLOR)
    add(Constants.BACKGROUND_IMAGE)
    add(Constants.BACKGROUND_VIDEO)
    add(Constants.BACKGROUND_CAMERA)
    add(Constants.BACKGROUND_TRANSPARENT)
    if (offersGradient) add(Constants.BACKGROUND_GRADIENT)
    if (offersLottie) add(Constants.BACKGROUND_LOTTIE)
}

/**
 * The types the wash above [this] surface's band offers, in the order its segments show them.
 *
 * The band's [typeOptions] minus everything that is not a color: no picture, no clip, no camera and
 * no gradient, because the band is where those belong and the two thirds above it are a flat wash
 * or nothing at all.
 *
 * `Default` appears for the same reason it does in the band's row — and is absent from the Default
 * Lower Third for a reason the band does not share. That surface's *band* can follow the
 * full-screen Default; its wash has nothing to follow, since a full screen has no area above a
 * band, so it is the top of this chain and answers for itself.
 */
internal fun BackgroundScope.aboveBandTypeOptions(): List<String> = buildList {
    if (this@aboveBandTypeOptions != BackgroundScope.DEFAULT_LOWER_THIRD) {
        add(Constants.BACKGROUND_DEFAULT)
    }
    add(Constants.BACKGROUND_COLOR)
    add(Constants.BACKGROUND_TRANSPARENT)
}

/** What [scope] is set to, as a config — flat Default fields included. */
internal fun BackgroundSettings.configFor(scope: BackgroundScope): BackgroundConfig = when (scope) {
    BackgroundScope.DEFAULT -> BackgroundConfig(
        backgroundType = defaultBackgroundType,
        backgroundColor = defaultBackgroundColor,
        backgroundImage = defaultBackgroundImage,
        backgroundVideo = defaultBackgroundVideo,
        backgroundOpacity = defaultBackgroundOpacity,
        dim = defaultBackgroundDim,
        blur = defaultBackgroundBlur,
        camera = defaultBackgroundCamera,
    )
    BackgroundScope.DEFAULT_LOWER_THIRD -> BackgroundConfig(
        backgroundType = defaultLowerThirdBackgroundType,
        backgroundColor = defaultLowerThirdBackgroundColor,
        backgroundImage = defaultLowerThirdBackgroundImage,
        backgroundVideo = defaultLowerThirdBackgroundVideo,
        backgroundOpacity = defaultLowerThirdBackgroundOpacity,
        dim = defaultLowerThirdBackgroundDim,
        blur = defaultLowerThirdBackgroundBlur,
        camera = defaultLowerThirdBackgroundCamera,
        aboveBandType = defaultLowerThirdAboveBandType,
        aboveBandColor = defaultLowerThirdAboveBandColor,
        aboveBandOpacity = defaultLowerThirdAboveBandOpacity,
    )
    BackgroundScope.BIBLE -> bibleBackground
    BackgroundScope.BIBLE_LOWER_THIRD -> bibleLowerThirdBackground
    BackgroundScope.SONG -> songBackground
    BackgroundScope.SONG_LOWER_THIRD -> songLowerThirdBackground
}

/** [this] with [scope] set to [config]. The two Defaults keep their flat fields. */
internal fun BackgroundSettings.withConfigFor(
    scope: BackgroundScope,
    config: BackgroundConfig,
): BackgroundSettings = when (scope) {
    BackgroundScope.DEFAULT -> copy(
        defaultBackgroundType = config.backgroundType,
        defaultBackgroundColor = config.backgroundColor,
        defaultBackgroundImage = config.backgroundImage,
        defaultBackgroundVideo = config.backgroundVideo,
        defaultBackgroundOpacity = config.backgroundOpacity,
        defaultBackgroundDim = config.dim,
        defaultBackgroundBlur = config.blur,
        defaultBackgroundCamera = config.camera,
    )
    BackgroundScope.DEFAULT_LOWER_THIRD -> copy(
        defaultLowerThirdBackgroundType = config.backgroundType,
        defaultLowerThirdBackgroundColor = config.backgroundColor,
        defaultLowerThirdBackgroundImage = config.backgroundImage,
        defaultLowerThirdBackgroundVideo = config.backgroundVideo,
        defaultLowerThirdBackgroundOpacity = config.backgroundOpacity,
        defaultLowerThirdBackgroundDim = config.dim,
        defaultLowerThirdBackgroundBlur = config.blur,
        defaultLowerThirdBackgroundCamera = config.camera,
        defaultLowerThirdAboveBandType = config.aboveBandType,
        defaultLowerThirdAboveBandColor = config.aboveBandColor,
        defaultLowerThirdAboveBandOpacity = config.aboveBandOpacity,
    )
    BackgroundScope.BIBLE -> copy(bibleBackground = config)
    BackgroundScope.BIBLE_LOWER_THIRD -> copy(bibleLowerThirdBackground = config)
    BackgroundScope.SONG -> copy(songBackground = config)
    BackgroundScope.SONG_LOWER_THIRD -> copy(songLowerThirdBackground = config)
}

/**
 * What [scope] actually puts on screen, following every "Default" it meets — the same chain
 * `resolveBackground` and `PresenterScreen` walk, so a chip in the rail shows what the output will.
 */
internal fun BackgroundSettings.resolvedConfigFor(scope: BackgroundScope): BackgroundConfig {
    val config = configFor(scope)
    val parent = scope.inheritsFrom ?: return config
    return if (config.backgroundType == scope.inheritType) resolvedConfigFor(parent) else config
}

/**
 * How tall the band is for [scope], as a fraction of the output.
 *
 * Bible and Songs each carry their own lower-third height, so a preview has to ask the one whose
 * surface it is drawing rather than assume a single number. The two Defaults sit behind both bands
 * at once and have no height of their own; they take the taller of the two, the largest area a
 * default can be showing in — the same stand-in the output itself takes when nothing is live.
 */
internal fun AppSettings.bandFractionFor(scope: BackgroundScope): Float = when (scope.group) {
    BackgroundScopeGroup.BIBLE -> lowerThirdBandFraction(Presenting.BIBLE)
    BackgroundScopeGroup.SONGS -> lowerThirdBandFraction(Presenting.LYRICS)
    BackgroundScopeGroup.DEFAULTS -> lowerThirdBandFraction(null)
}

/**
 * How much of the output a surface's background actually paints.
 *
 * The two are genuinely different areas, and the four cards this tab replaced said so with a
 * little colored TV badge in each corner: a full-screen background is the whole output, while
 * *every* lower-third background — the default one included — is the band at the bottom and
 * nothing above it. A preview that filled the whole screen for a lower third would show the
 * operator a look the output never produces.
 */
internal enum class BackgroundCoverage { FULL_SCREEN, BAND }

/** What [BackgroundCoverage] this surface paints. */
internal val BackgroundScope.coverage: BackgroundCoverage
    get() = if (lowerThird) BackgroundCoverage.BAND else BackgroundCoverage.FULL_SCREEN

/** Whether [scope] is set to something of its own rather than deferring upwards. */
internal fun BackgroundSettings.isSetExplicitly(scope: BackgroundScope): Boolean =
    scope.inheritType != null && configFor(scope).backgroundType != scope.inheritType

