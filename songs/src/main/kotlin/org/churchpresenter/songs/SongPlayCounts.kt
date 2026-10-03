package org.churchpresenter.songs

/** How often each song has gone live, for the Plays column and its sort. The app's `StatisticsManager`. */
fun interface SongPlayCounts {
    fun getSongPlayCount(songId: String): Int
}
