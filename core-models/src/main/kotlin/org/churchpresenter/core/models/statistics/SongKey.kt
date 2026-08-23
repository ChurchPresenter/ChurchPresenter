package org.churchpresenter.core.models.statistics

/**
 * What identifies one song across both stores.
 *
 * The all-time tally is keyed by the catalog song id, which is not a field of [SongDisplayEntry] and
 * has no counterpart in the event log, so a song is matched on these three fields instead. A title
 * edited between plays therefore splits into two rows, exactly as it already does in the CCLI
 * report. `:statistics` carries the `key()` extensions that derive one from either store.
 */
data class SongKey(val songbook: String, val songNumber: Int, val title: String)
