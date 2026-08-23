package org.churchpresenter.core.models.statistics

/**
 * One song as the CCLI report lists it: every play of it inside the selected period, rolled up.
 *
 * Computed in memory and never stored — [ccliNumber] is resolved from the song library at report
 * time, so a licence number filled in later shows up in the next report without touching history.
 */
data class SongSummary(
    val songNumber: Int,
    val title: String,
    val songbook: String,
    val author: String,
    val ccliNumber: String,
    val count: Int,
    val firstUsed: Long,
    val lastUsed: Long
)
