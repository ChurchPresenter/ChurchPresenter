package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.server.CalendarEnrollDecision
import org.churchpresenter.app.churchpresenter.server.RemoteAccess
import org.churchpresenter.app.churchpresenter.utils.slideshowSeconds
import org.churchpresenter.core.models.schedule.ScheduleItem

/*
 * The calendar automation's decisions, held apart from AppRootState and the composables that act on
 * them. Everything here is pure: what to put on screen for a cue, how long a row runs, what a phone's
 * enrolment is answered without asking.
 */

/**
 * What a fired cue puts on screen. [plays] is the cue's Once / Loop / N times: 1, 0 or N. A text
 * announcement set to repeat carries that count as its own loop count (0 is forever there too); a
 * timer, a single play and every other kind of row go out as they are.
 */
internal fun calendarShownItem(item: ScheduleItem, plays: Int): ScheduleItem =
    if (item is ScheduleItem.AnnouncementItem && !item.isTimer && plays != 1) {
        item.copy(loopCount = plays)
    } else {
        item
    }

/** A cue's [plays] as a media loop count: media counts the repeats after the first play, and 0 is forever. */
internal fun calendarMediaLoopCount(plays: Int): Int = if (plays == 0) 0 else plays - 1

/**
 * Whether a loaded service sets the Schedule's start time. Replacing the Schedule always does;
 * appending keeps the start it already runs from, unless it was empty.
 */
internal fun calendarSetsServiceStart(replace: Boolean, wasEmpty: Boolean): Boolean = replace || wasEmpty

/**
 * How long a row is known to run by itself: a clip's own duration, read through [mediaSeconds], or a
 * slideshow's count times its interval. Null for anything else, and for a clip or slideshow whose
 * length cannot be told.
 */
internal fun knownRunSeconds(
    item: ScheduleItem,
    pictureIntervalSeconds: Float,
    presentationIntervalSeconds: Float,
    mediaSeconds: (String) -> Int?,
): Int? = when (item) {
    is ScheduleItem.MediaItem -> mediaSeconds(item.mediaUrl)
    is ScheduleItem.PictureItem -> slideshowSeconds(item.imageCount, pictureIntervalSeconds)
    is ScheduleItem.PresentationItem -> slideshowSeconds(item.slideCount, presentationIntervalSeconds)
    else -> null
}

/** The run-of-show PDF's font file, without its extension. */
internal fun calendarPdfFontName(bold: Boolean): String = if (bold) "OpenSans-Bold" else "OpenSans-Regular"

/**
 * The answer a phone's calendar enrolment gets without the operator being asked, or null when the
 * operator is asked. Sync off means there is no relay to enrol into; a blocked device is refused.
 */
internal fun calendarEnrollGate(syncEnabled: Boolean, access: RemoteAccess): CalendarEnrollDecision? = when {
    !syncEnabled -> CalendarEnrollDecision.SyncOff
    access == RemoteAccess.AUTO_REJECT -> CalendarEnrollDecision.Denied
    else -> null
}
