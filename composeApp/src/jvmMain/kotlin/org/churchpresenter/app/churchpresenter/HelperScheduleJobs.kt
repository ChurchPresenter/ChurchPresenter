package org.churchpresenter.app.churchpresenter

import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.remote.emitRemoteTabSelection
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.helper.action.ActionOutcome
import org.churchpresenter.helper.helperText
import org.jetbrains.compose.resources.StringResource
import org.churchpresenter.server.SelectBibleVerseRequest
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_schedule_at_end
import org.churchpresenter.strings.generated.resources.helper_schedule_at_start
import org.churchpresenter.strings.generated.resources.helper_schedule_empty
import org.churchpresenter.strings.generated.resources.helper_schedule_live
import org.churchpresenter.strings.generated.resources.helper_schedule_not_found
import org.churchpresenter.strings.generated.resources.helper_schedule_ready
import org.churchpresenter.strings.generated.resources.helper_schedule_selected

// The helper stepping through the schedule, as a click on a row would.

/** Rows that show something; labels head sections and cues fire on their own. */
private fun ScheduleItem.isShowable() = this !is ScheduleItem.LabelItem && this !is ScheduleItem.CueItem

/** The schedule's next or previous showable row from the selected one, made ready to show. */
internal fun AppRootState.helperScheduleStep(forward: Boolean): ActionOutcome {
    val rows = currentScheduleItems.filter { it.isShowable() }
    val at = rows.indexOfFirst { it.id == selectedScheduleItemId }
    val target = when {
        rows.isEmpty() -> null
        at < 0 -> if (forward) rows.first() else rows.last()
        else -> rows.getOrNull(if (forward) at + 1 else at - 1)
    }
    return target?.let { readyScheduleRow(it) }
        ?: ActionOutcome.Refused(helperText(stepRefusal(rows.isEmpty(), forward)))
}

/** Why there is no row to step to: none at all, or already at the end being stepped toward. */
private fun stepRefusal(empty: Boolean, forward: Boolean): StringResource = when {
    empty -> Res.string.helper_schedule_empty
    forward -> Res.string.helper_schedule_at_end
    else -> Res.string.helper_schedule_at_start
}

internal fun AppRootState.helperScheduleGoTo(name: String): ActionOutcome {
    val q = name.lowercase()
    val target = currentScheduleItems.filter { it.isShowable() }.firstOrNull { it.displayText.lowercase().contains(q) }
        ?: return ActionOutcome.Refused(helperText(Res.string.helper_schedule_not_found, name))
    return readyScheduleRow(target)
}

/**
 * Selects [item] and readies it as its own tab would: a song, pictures, a deck or a video are loaded
 * for Go Live; a verse, an announcement or a scene go straight up, as a phone's would. Nothing is
 * added to the schedule — it is already there.
 */
private fun AppRootState.readyScheduleRow(item: ScheduleItem): ActionOutcome {
    currentScheduleActions.selectItem(item.id)
    val name = item.displayText
    return when (item) {
        is ScheduleItem.BibleVerseItem -> {
            companionServer.onSelectBibleVerse.tryEmit(
                SelectBibleVerseRequest(
                    bookName = item.bookName,
                    chapter = item.chapter,
                    verseNumber = item.verseNumber,
                    verseRange = item.verseRange,
                ),
            )
            ActionOutcome.Done(helperText(Res.string.helper_schedule_live, name))
        }
        is ScheduleItem.AnnouncementItem -> {
            showAnnouncement(item)
            ActionOutcome.Done(helperText(Res.string.helper_schedule_live, name))
        }
        is ScheduleItem.SceneItem -> {
            currentScheduleActions.presentScene(item.sceneId)
            ActionOutcome.Done(helperText(Res.string.helper_schedule_live, name))
        }
        // Not through the tab selection: that puts a song live, and here it waits for Go Live.
        is ScheduleItem.SongItem -> {
            remoteSelectSongFlow.tryEmit(readyNotLive(item))
            ActionOutcome.Done(helperText(Res.string.helper_schedule_ready, name))
        }
        is ScheduleItem.PictureItem, is ScheduleItem.PresentationItem, is ScheduleItem.MediaItem -> {
            coroutineScope.launch {
                emitRemoteTabSelection(
                    item, remoteSelectSongFlow, remoteSelectPictureFlow, remoteSelectPresentationFlow,
                    remoteSelectMediaFlow,
                )
            }
            ActionOutcome.Done(helperText(Res.string.helper_schedule_ready, name))
        }
        else -> ActionOutcome.Done(helperText(Res.string.helper_schedule_selected, name))
    }
}
