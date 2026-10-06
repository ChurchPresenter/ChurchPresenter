package org.churchpresenter.liveoutput

import org.churchpresenter.settings.utils.Constants

/** The shortest an announcement stays up, however fast the speed slider is wound. */
internal const val MIN_ANNOUNCEMENT_DISPLAY_MS = 500L

/** Whether an announcement fades between states, as opposed to cutting or sliding. */
internal fun isFadeAnnouncement(animationType: String): Boolean =
    animationType == Constants.ANIMATION_FADE

/**
 * Whether the announcement slides in rather than being shown by this code at all.
 *
 * A directional slide is animated by the presenter itself, so here the text is simply swapped —
 * running a fade over it as well would fight the animation already in flight.
 */
internal fun isSlidingAnnouncement(animationType: String): Boolean =
    animationType != Constants.ANIMATION_FADE && animationType != Constants.ANIMATION_NONE

/**
 * Whether clearing an announcement should fade it away.
 *
 * Only when something was actually on screen: fading out from nothing spends the animation's length
 * showing an empty screen before the operator's next content can appear.
 */
internal fun shouldFadeOutAnnouncement(isFade: Boolean, wasEmpty: Boolean): Boolean =
    isFade && !wasEmpty

/** Whether the announcement clears itself after a set number of loops, rather than staying up. */
internal fun isFiniteAnnouncementLoop(loopCount: Int): Boolean = loopCount > 0

/**
 * How long an announcement stays up in total.
 *
 * The speed slider reads the other way round — a higher value means faster — so the configured
 * duration is subtracted from the slider's span rather than used directly.
 */
internal fun announcementDisplayMs(sliderSpan: Long, animationDuration: Long, loopCount: Int): Long =
    (sliderSpan - animationDuration).coerceAtLeast(MIN_ANNOUNCEMENT_DISPLAY_MS) * loopCount
