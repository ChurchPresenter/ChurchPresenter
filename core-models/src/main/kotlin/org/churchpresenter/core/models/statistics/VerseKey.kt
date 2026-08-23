package org.churchpresenter.core.models.statistics

/** What identifies one verse. Both stores agree on this composite, unlike [SongKey]'s. */
data class VerseKey(val bibleName: String, val bookName: String, val chapter: Int, val verseNumber: Int)
