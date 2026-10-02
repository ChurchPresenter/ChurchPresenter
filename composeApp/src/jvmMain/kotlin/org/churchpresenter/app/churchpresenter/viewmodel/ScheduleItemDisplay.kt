package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.announcements
import org.churchpresenter.strings.generated.resources.bible
import org.churchpresenter.strings.generated.resources.media
import org.churchpresenter.strings.generated.resources.pictures
import org.churchpresenter.strings.generated.resources.presentation
import org.churchpresenter.strings.generated.resources.schedule_kind_cue
import org.churchpresenter.strings.generated.resources.schedule_kind_ministry
import org.churchpresenter.strings.generated.resources.display_lower_third
import org.churchpresenter.strings.generated.resources.songs
import org.churchpresenter.strings.generated.resources.tab_canvas
import org.churchpresenter.strings.generated.resources.tab_dictionary
import org.churchpresenter.strings.generated.resources.tab_web
import org.churchpresenter.calendar.model.clockText
import org.churchpresenter.calendar.model.localeUses24HourClock
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.StringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.ui.graphics.vector.ImageVector

private const val VERSE_PREVIEW_CHARS = 100
private const val PALETTE_INDEX_FOURTH = 3

/**
 * How a schedule item is labelled in the list — its type icon, its grey detail line, and an
 * announcement timer's preview. Extracted from ScheduleTab (the icon `when` was duplicated at two
 * sites) so the per-type mapping is exhaustive over the sealed [ScheduleItem] and tested in one place.
 */

/**
 * The type icon shown on a schedule row and its drag preview — the same icon as the item's own tab
 * where it has one (`tabIcon` in `TabSection`).
 */
internal fun scheduleItemIcon(item: ScheduleItem): ImageVector = when (item) {
    is ScheduleItem.SongItem -> Icons.Filled.MusicNote
    is ScheduleItem.BibleVerseItem -> Icons.AutoMirrored.Filled.MenuBook
    is ScheduleItem.LabelItem -> Icons.AutoMirrored.Filled.Label
    is ScheduleItem.PictureItem -> Icons.Filled.Image
    is ScheduleItem.PresentationItem -> Icons.Filled.Slideshow
    is ScheduleItem.MediaItem -> Icons.Filled.Movie
    is ScheduleItem.LowerThirdItem -> Icons.Filled.Subtitles
    is ScheduleItem.AnnouncementItem -> Icons.Filled.Campaign
    is ScheduleItem.WebsiteItem -> Icons.Filled.Language
    is ScheduleItem.SceneItem -> Icons.Filled.Dashboard
    is ScheduleItem.DictionaryItem -> Icons.Filled.Book
    is ScheduleItem.CueItem -> Icons.Filled.Bolt
    is ScheduleItem.MinistryItem -> Icons.Filled.RecordVoiceOver
}

/**
 * The grey secondary line for the four schedule item types whose detail is a plain formatted string —
 * Bible verse (truncated to 100 chars with an ellipsis), picture folder, and the `TYPE - path` line
 * for presentations and media. Returns null for every other type: some show no detail, and others
 * (e.g. the lower-third pause duration) render theirs with a string resource in the View, so those
 * branches stay in `ScheduleItemRow` rather than routing through here.
 */
internal fun scheduleItemDetailText(item: ScheduleItem): String? = when (item) {
    is ScheduleItem.BibleVerseItem ->
        item.verseText.take(VERSE_PREVIEW_CHARS) + if (item.verseText.length > VERSE_PREVIEW_CHARS) "..." else ""
    is ScheduleItem.PictureItem -> item.imagePath.ifEmpty { item.folderPath }
    is ScheduleItem.PresentationItem -> "${item.fileType.uppercase()} - ${item.filePath}"
    is ScheduleItem.MediaItem -> "${item.mediaType.uppercase()} - ${item.mediaUrl}"
    // `9:45 AM · Announcement loop`: when it fires, and what it puts on screen if anything.
    is ScheduleItem.CueItem -> listOfNotNull(
        item.absoluteTime.ifBlank { null }?.let { clockText(it, localeUses24HourClock()) },
        item.payload?.displayText,
    ).joinToString(" · ")
    else -> null
}

/**
 * Which of a small rotating set of theme colors a row's type-icon chip uses — not a distinct hue per
 * type (that would mean hardcoded colors, against project standards), but enough variety that
 * adjacent kinds in a service read as visually different. [ScheduleItem.LabelItem] rows render as
 * section headers with their own user-chosen color instead, so they never consult this.
 */
internal fun scheduleItemPaletteIndex(item: ScheduleItem): Int = when (item) {
    is ScheduleItem.SongItem -> 0
    is ScheduleItem.BibleVerseItem -> 1
    is ScheduleItem.PresentationItem -> 2
    is ScheduleItem.PictureItem -> PALETTE_INDEX_FOURTH
    is ScheduleItem.MediaItem -> 0
    is ScheduleItem.LowerThirdItem -> 1
    is ScheduleItem.AnnouncementItem -> 2
    is ScheduleItem.WebsiteItem -> PALETTE_INDEX_FOURTH
    is ScheduleItem.SceneItem -> 0
    is ScheduleItem.DictionaryItem -> 1
    is ScheduleItem.LabelItem -> 0
    is ScheduleItem.CueItem -> 2
    is ScheduleItem.MinistryItem -> 0
}

/** The row's type name, shown as a small uppercase chip at the Detailed density. */
internal fun scheduleItemKindLabel(item: ScheduleItem): StringResource = when (item) {
    is ScheduleItem.SongItem -> Res.string.songs
    is ScheduleItem.BibleVerseItem -> Res.string.bible
    is ScheduleItem.PresentationItem -> Res.string.presentation
    is ScheduleItem.PictureItem -> Res.string.pictures
    is ScheduleItem.MediaItem -> Res.string.media
    is ScheduleItem.LowerThirdItem -> Res.string.display_lower_third
    is ScheduleItem.AnnouncementItem -> Res.string.announcements
    is ScheduleItem.WebsiteItem -> Res.string.tab_web
    is ScheduleItem.SceneItem -> Res.string.tab_canvas
    is ScheduleItem.DictionaryItem -> Res.string.tab_dictionary
    is ScheduleItem.LabelItem -> Res.string.songs // unused — LabelItem renders as a section header
    is ScheduleItem.CueItem -> Res.string.schedule_kind_cue
    is ScheduleItem.MinistryItem -> Res.string.schedule_kind_ministry
}

/**
 * The h:m:s preview for an announcement timer, or null when there is nothing fixed to preview: a
 * count-up timer and the live clock display only have a value once triggered. A clock-target timer
 * previews the target time-of-day; a plain duration timer previews its minutes:seconds.
 */
internal fun announcementTimerSubtext(item: ScheduleItem.AnnouncementItem): String? = when (item.timerMode) {
    Constants.TIMER_MODE_CLOCK ->
        "%02d:%02d:%02d".format(item.targetHour, item.targetMinute, item.targetSecond)
    Constants.TIMER_MODE_COUNT_UP, Constants.TIMER_MODE_CLOCK_DISPLAY -> null
    else -> "%02d:%02d".format(item.timerMinutes, item.timerSeconds)
}
