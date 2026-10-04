package org.churchpresenter.app.churchpresenter

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.profileFor
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.sharedui.models.Presenting

private const val DEFAULT_FADE_OUT_MS = 500

/** The floor every transition is held to, below which a fade reads as a flicker. */
internal const val MIN_TRANSITION_MS = 100

/**
 * How long a mode-level crossfade runs: the longer of the two that are switched on.
 *
 * One duration has to serve both, because a crossfade between scripture and a song is a single
 * transition — taking the shorter of the two would cut it off part-way.
 */
internal fun modeCrossfadeDuration(bible: BibleSettings, song: SongSettings): Int = maxOf(
    if (bible.crossfade) bible.transitionDuration.toInt() else 0,
    if (song.crossfade) song.transitionDuration.toInt() else 0,
).coerceAtLeast(MIN_TRANSITION_MS)

/** Whether any output is pinned to [mode], and so is still showing it. */
internal fun isAnyScreenLockedTo(locks: Map<Int, Presenting>, mode: Presenting): Boolean =
    locks.values.any { it == mode }

/**
 * Whether clearing the output should fade it out first.
 *
 * Not when a screen is locked to what is being cleared: that screen goes on showing the content, so
 * fading the shared alpha would dim it on a display nobody asked to clear. Only scripture and songs
 * fade at all; everything else clears instantly.
 */
internal fun shouldFadeOnClear(
    mode: Presenting,
    anyScreenLocked: Boolean,
    bible: BibleSettings,
    song: SongSettings,
): Boolean = !anyScreenLocked && when (mode) {
    Presenting.BIBLE -> bible.fadeOut
    Presenting.LYRICS -> song.fadeOut
    else -> false
}

/**
 * Whether a Bible lower-third background is a Lottie template rather than a backdrop — the band
 * then draws its own text and plays its own entrance and exit, so the fade on clear gives way to
 * the template's exit segment.
 */
internal fun usesBibleLottieBand(config: BackgroundConfig): Boolean =
    config.backgroundType == Constants.BACKGROUND_LOTTIE && config.backgroundLottie.isNotBlank()

/**
 * The Lottie band template the clear and text-change choreography for [mode] is timed against:
 * that content's global lower third, or failing that the first assigned profile's. Null means
 * no output uses one and the classic fade applies — and always null for content with no band.
 */
internal fun lottieBandPath(settings: AppSettings, mode: Presenting): String? {
    fun BackgroundSettings.bandFor(): BackgroundConfig? = when (mode) {
        Presenting.BIBLE -> bibleLowerThirdBackground
        Presenting.LYRICS -> songLowerThirdBackground
        else -> null
    }
    // Through each screen's assigned profile, so a screen following a profile with its own
    // background is seen here exactly as it renders.
    val proj = settings.projectionSettings
    val candidates = listOfNotNull(settings.backgroundSettings.bandFor()) +
        proj.screenAssignments
            .mapNotNull { proj.profileFor(it) }
            .mapNotNull { it.backgroundSettings.bandFor() }
    return candidates.firstOrNull { usesBibleLottieBand(it) }?.backgroundLottie
}

/** How long that fade-out runs, per content type, never below [MIN_TRANSITION_MS]. */
internal fun fadeOutDuration(mode: Presenting, bible: BibleSettings, song: SongSettings): Int = when (mode) {
    Presenting.BIBLE -> bible.transitionDuration.toInt()
    Presenting.LYRICS -> song.transitionDuration.toInt()
    else -> DEFAULT_FADE_OUT_MS
}.coerceAtLeast(MIN_TRANSITION_MS)

/**
 * Whether a mode change on this output crossfades rather than cuts.
 *
 * Only between two pieces of content: crossfading from or to nothing is a fade, which the per-type
 * fade settings own, and running both would fade twice over the same moment.
 */
internal fun isScreenCrossfadeActive(
    bible: BibleSettings,
    song: SongSettings,
    mode: Presenting,
    previousMode: Presenting,
): Boolean = (bible.crossfade || song.crossfade) &&
    mode != Presenting.NONE && previousMode != Presenting.NONE
