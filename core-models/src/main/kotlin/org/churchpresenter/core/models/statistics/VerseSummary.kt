package org.churchpresenter.core.models.statistics

/** One verse rolled up over the selected period — the verse counterpart of [SongSummary]. */
data class VerseSummary(
    val bibleName: String,
    val bookName: String,
    val chapter: Int,
    val verseNumber: Int,
    val count: Int,
    val firstUsed: Long,
    val lastUsed: Long
)
