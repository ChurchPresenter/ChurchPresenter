package org.churchpresenter.schedule

import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.sharedui.models.Presenting

fun ScheduleViewModel.selectItem(id: String) {
    _selectedItemId.value = if (_selectedItemId.value == id) null else id
}

/**
 * Selects [id] outright -- what the automation does when it puts a row on screen.
 *
 * Not [selectItem]: that toggles, because a second click on a row is how the operator clears
 * the selection. A row going live is not a click, and firing the same row twice must not
 * deselect it.
 */
fun ScheduleViewModel.selectOnly(id: String) {
    _selectedItemId.value = id
    markLive(id)
}

fun ScheduleViewModel.clearSelection() {
    _selectedItemId.value = null
}

/** Presents [item] through its own callback, or else switches to the matching [Presenting] mode. */
fun ScheduleViewModel.presentItem(
    item: ScheduleItem,
    onPresenting: (Presenting) -> Unit,
    onPresentSong: ((ScheduleItem.SongItem) -> Unit)? = null,
    onPresentBible: ((ScheduleItem.BibleVerseItem) -> Unit)? = null,
    onPresentPresentation: ((ScheduleItem.PresentationItem) -> Unit)? = null,
    onPresentPictures: ((ScheduleItem.PictureItem) -> Unit)? = null,
    onPresentMedia: ((ScheduleItem.MediaItem) -> Unit)? = null,
    onPresentAnnouncement: ((ScheduleItem.AnnouncementItem) -> Unit)? = null,
    onPresentLowerThird: ((ScheduleItem.LowerThirdItem) -> Unit)? = null,
    onPresentWebsite: ((ScheduleItem.WebsiteItem) -> Unit)? = null,
    onPresentScene: ((ScheduleItem.SceneItem) -> Unit)? = null,
    onPresentDictionary: ((ScheduleItem.DictionaryItem) -> Unit)? = null,
    onPresentCue: ((ScheduleItem.CueItem) -> Unit)? = null,
) {
    onItemPresented?.invoke(item)
    if (item !is ScheduleItem.LabelItem) markLive(item.id)
    when (item) {
        is ScheduleItem.SongItem -> onPresentSong?.invoke(item) ?: onPresenting(Presenting.LYRICS)
        is ScheduleItem.BibleVerseItem -> onPresentBible?.invoke(item) ?: onPresenting(Presenting.BIBLE)
        is ScheduleItem.LabelItem -> { /* not presentable */ }
        is ScheduleItem.PictureItem -> onPresentPictures?.invoke(item) ?: onPresenting(Presenting.PICTURES)
        is ScheduleItem.PresentationItem ->
            onPresentPresentation?.invoke(item) ?: onPresenting(Presenting.PRESENTATION)
        is ScheduleItem.MediaItem -> onPresentMedia?.invoke(item) ?: onPresenting(Presenting.MEDIA)
        is ScheduleItem.LowerThirdItem -> onPresentLowerThird?.invoke(item)
        is ScheduleItem.AnnouncementItem ->
            onPresentAnnouncement?.invoke(item) ?: onPresenting(Presenting.ANNOUNCEMENTS)
        is ScheduleItem.WebsiteItem -> onPresentWebsite?.invoke(item) ?: onPresenting(Presenting.WEBSITE)
        is ScheduleItem.SceneItem -> onPresentScene?.invoke(item) ?: onPresenting(Presenting.CANVAS)
        is ScheduleItem.DictionaryItem ->
            onPresentDictionary?.invoke(item) ?: onPresenting(Presenting.ANNOUNCEMENTS)
        is ScheduleItem.CueItem -> onPresentCue?.invoke(item)
        is ScheduleItem.MinistryItem -> { /* happens up front, never on screen */ }
    }
    actionsFor(item.id).takeIf { it.isNotEmpty() }?.let { onRowActions?.invoke(item, it) }
}
