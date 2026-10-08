package org.churchpresenter.songs

import org.churchpresenter.core.models.schedule.ScheduleItem

/** What the Songs tab does with a song handed to it from the schedule or a remote. */
enum class ScheduleSongAction {
    /**
     * A schedule row clicked: select the song. It is pushed to the output only while no song is
     * live, so a click never replaces what the congregation is reading.
     */
    OPEN,

    /** Select the song and push it, live or not -- what the tab did before it told these apart. */
    PUSH,

    /**
     * A schedule row double-clicked, or its Go Live button: select the song and put it on screen,
     * as the tab's own Go Live does -- its first section in one push, never a placeholder.
     */
    GO_LIVE,
}

/** The song the schedule or a remote handed over, the hand-over's count, and what to do with it. */
internal data class ScheduleSelection(
    val item: ScheduleItem.SongItem?,
    val version: Int,
    val action: ScheduleSongAction,
    val source: String = "schedule",
)
