package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.remote.RemoteSongSelection
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.helper.action.ActionOutcome
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.helperText
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_done_added_schedule
import org.churchpresenter.strings.generated.resources.helper_song_found
import org.churchpresenter.strings.generated.resources.helper_song_not_found
import org.churchpresenter.strings.generated.resources.helper_songs_not_loaded
import java.util.UUID

// The helper finding songs and adding songs and verses to the schedule.

/** The library song [query] names: its number first, then its title exactly, then a title starting or containing it. */
internal fun findHelperSong(songs: List<SongItem>, query: String): SongItem? {
    val q = query.trim().lowercase()
    return songs.firstOrNull { it.number == q } ?: songs.firstOrNull { it.title.lowercase() == q }
        ?: songs.firstOrNull { it.title.lowercase().startsWith(q) }
        ?: songs.firstOrNull { it.title.lowercase().contains(q) }
}

private fun SongItem.asScheduleItem() = ScheduleItem.SongItem(
    id = UUID.randomUUID().toString(),
    songNumber = number.toIntOrNull() ?: 0,
    title = title,
    songbook = songbook,
    songId = songId,
)

/** The song opened on the Songs tab and left there, for the operator to put live. */
internal fun readyNotLive(song: ScheduleItem.SongItem) = RemoteSongSelection(song, goLive = false, source = "helper")

private fun SongItem.label(): String = if (number.isNotBlank()) "$number. $title" else title

/** Finds the song and opens it on the Songs tab, ready for Go Live — nothing goes on screen. */
internal fun AppRootState.helperFindSong(query: String): ActionOutcome {
    val song = lookUpSong(query) ?: return songMissing(query)
    remoteSelectSongFlow.tryEmit(readyNotLive(song.asScheduleItem()))
    return ActionOutcome.Done(helperText(Res.string.helper_song_found, song.label()))
}

internal fun AppRootState.helperAddSongToSchedule(query: String): ActionOutcome {
    val song = lookUpSong(query) ?: return songMissing(query)
    currentScheduleActions.addSong(song.number.toIntOrNull() ?: 0, song.title, song.songbook, song.songId)
    return ActionOutcome.Done(helperText(Res.string.helper_done_added_schedule, song.label()))
}

private fun AppRootState.lookUpSong(query: String): SongItem? = findHelperSong(helperSongs, query)

private fun AppRootState.songMissing(query: String): ActionOutcome =
    if (helperSongCount == null) {
        ActionOutcome.Refused(helperText(Res.string.helper_songs_not_loaded))
    } else {
        ActionOutcome.Refused(helperText(Res.string.helper_song_not_found, query))
    }

/** Adds the verse with no text and no book id: the Schedule matches the name against the Bible, as for a phone. */
internal fun AppRootState.helperAddVerseToSchedule(action: HelperAction.AddVerseToSchedule): ActionOutcome {
    val range = if (action.lastVerse > action.verse) "${action.verse}-${action.lastVerse}" else ""
    currentScheduleActions.addBibleVerse(action.book, action.chapter, action.verse, "", range, 0)
    return ActionOutcome.Done(helperText(Res.string.helper_done_added_schedule, action.display))
}
