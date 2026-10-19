package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.remote.RemoteSongSelection
import org.churchpresenter.app.churchpresenter.remote.scheduledSongFor
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.server.ScheduleSongDto
import java.util.UUID

/**
 * A remote's `select_song` — "navigate schedule to a song". The song's row is selected and the song
 * opened on the Songs tab, ready for Go Live: nothing goes on screen, and nothing is added. A song
 * that is not in the schedule is opened on its own.
 */
internal fun AppRootState.readyRemoteSong(song: ScheduleSongDto) {
    val row = scheduledSongFor(song, currentScheduleItems)
    if (row != null) currentScheduleActions.selectItem(row.id)
    val item = row ?: ScheduleItem.SongItem(
        id = UUID.randomUUID().toString(),
        songNumber = song.songNumber,
        title = song.title,
        songbook = song.songbook,
    )
    remoteSelectSongFlow.tryEmit(RemoteSongSelection(item, goLive = false, source = "remote"))
}
