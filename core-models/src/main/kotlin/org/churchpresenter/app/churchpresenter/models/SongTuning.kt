package org.churchpresenter.app.churchpresenter.models

/**
 * What a song is played at rather than what it says: the metronome tempo and the capo the chart is
 * read with.
 *
 * Kept out of `SongItem` because neither is written to the song file — both live per-machine in
 * `AppSettings`, keyed by `songId`. Held together because the editor commits them together and
 * every caller that wants one wants the other.
 */
data class SongTuning(val bpm: Int = 0, val capo: Int = 0)
