package org.churchpresenter.calendar.ui

import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import java.util.UUID

/**
 * Turning what the picker shows into what a run of show holds.
 *
 * Plain functions in their own file so they can be tested without composing the picker — the cap in
 * [matchSongs] and the re-keying in [withNewId] are both the kind of thing that is only wrong once,
 * in production, on somebody's Sunday.
 */

private const val SEARCH_LIMIT = 200

/**
 * Songs matching [query] by title or by number, capped at [SEARCH_LIMIT].
 *
 * Internal rather than private so it can be tested without composing the picker — the cap is the
 * part worth pinning, since an empty query otherwise renders the whole library into a dialog.
 */
internal fun matchSongs(songs: List<SongItem>, query: String): List<SongItem> {
    val trimmed = query.trim()
    val matching = if (trimmed.isEmpty()) {
        songs
    } else {
        songs.filter { song ->
            song.title.contains(trimmed, ignoreCase = true) || song.number == trimmed
        }
    }
    return matching.take(SEARCH_LIMIT)
}

/** A song as a run-of-show row. */
internal fun SongItem.toScheduleItem(): ScheduleItem.SongItem = ScheduleItem.SongItem(
    id = UUID.randomUUID().toString(),
    // A song's number is free text in the library (it can be "12a") but an Int on a schedule row.
    // Zero is what the row already means by "unnumbered", and songId carries the real identity.
    songNumber = number.toIntOrNull() ?: 0,
    title = title,
    songbook = songbook,
    songId = songId,
)

/** A row that happens up front and never on screen -- see [ScheduleItem.MinistryItem]. */
internal fun ministryItem(title: String, detail: String): ScheduleItem.MinistryItem =
    ScheduleItem.MinistryItem(id = UUID.randomUUID().toString(), title = title, detail = detail)
