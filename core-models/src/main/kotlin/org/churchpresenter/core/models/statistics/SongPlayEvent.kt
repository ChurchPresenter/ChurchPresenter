package org.churchpresenter.core.models.statistics

import kotlinx.serialization.Serializable

/**
 * One occasion a song was put on the screen. The date-filtered CCLI report is computed from these
 * rather than from the all-time tally, which carries no timestamps.
 *
 * **Field names here are the on-disk format.** `statistics.json` and `play_log.json` are
 * append-only across years and cannot be reconstructed if lost — a church three years in has every
 * service it has ever run in them — and the reader is deliberately forgiving, so a renamed property
 * reads back as its default and surfaces as a report of *zero* rather than an error. Renaming one
 * is silent data loss, not a compile error. Nothing here is polymorphic, so no type discriminator
 * is written and this package is not part of the format.
 */
@Serializable
data class SongPlayEvent(
    val songNumber: Int = 0,
    val title: String = "",
    val songbook: String = "",
    val author: String = "",
    val timestamp: Long = 0L
)
