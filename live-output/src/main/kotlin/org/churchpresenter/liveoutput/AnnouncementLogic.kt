package org.churchpresenter.liveoutput

import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.announcementSecondsMs

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
 * How long a still or fading announcement stays up in total: its Speed seconds for each loop -- the
 * same seconds a sliding one takes to cross the screen ([announcementSecondsMs]).
 */
internal fun announcementDisplayMs(animationDuration: Int, loopCount: Int): Long =
    announcementSecondsMs(animationDuration).toLong() * loopCount
