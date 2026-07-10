package org.churchpresenter.app.churchpresenter.models

data class SelectedVerse(
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