package org.churchpresenter.core.models.statistics

import kotlinx.serialization.Serializable

/**
 * One verse's row in the all-time tally. See [VerseKey] for what matches it against the event log.
 *
 * **Field names here are the on-disk format.** `statistics.json` and `play_log.json` are
 * append-only across years and cannot be reconstructed if lost — a church three years in has every
 * service it has ever run in them — and the reader is deliberately forgiving, so a renamed property
 * reads back as its default and surfaces as a report of *zero* rather than an error. Renaming one
 * is silent data loss, not a compile error. Nothing here is polymorphic, so no type discriminator
 * is written and this package is not part of the format.
 */
@Serializable
data class VerseDisplayEntry(
    val bibleName: String = "",
    val bookName: String = "",
    val chapter: Int = 0,
    val verseNumber: Int = 0,
    val count: Int = 0
)
