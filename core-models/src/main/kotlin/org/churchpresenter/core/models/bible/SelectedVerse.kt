package org.churchpresenter.core.models.bible

import kotlinx.serialization.Serializable

/** Serializable so an app the output guard relaunches can put the live verse back. */
@Serializable
data class SelectedVerse(
    /** Persisted Bible module file name; keeps verse content tied to its own style profile. */
    val translationFileName: String = "",
    val bibleAbbreviation: String = "",
    val bibleName: String = "",
    val bookName: String = "",
    val chapter: Int = 0,
    val verseNumber: Int = 0,
    val verseText: String = "",
    val verseRange: String = "",
    /** Canonical (bible-agnostic) book id for [bookName] within the bible it was resolved from. */
    val bookId: Int = 0
)
