package org.churchpresenter.songs

import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import java.util.UUID

/** This song as a schedule row — the same one Add to Schedule makes — for dragging onto the schedule. */
internal fun SongItem.toScheduleItem(): ScheduleItem = ScheduleItem.SongItem(
    id = UUID.randomUUID().toString(),
    songNumber = number.toIntOrNull() ?: 0,
    title = title,
    songbook = songbook,
    songId = songId,
)
