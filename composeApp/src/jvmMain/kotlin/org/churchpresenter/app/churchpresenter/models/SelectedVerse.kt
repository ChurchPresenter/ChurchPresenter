package org.churchpresenter.app.churchpresenter.models

data class SelectedVerse(
    val bibleAbbreviation: String = "",
    val bookName: String = "",
    val chapter: Int = 0,
    val verseNumber: Int = 0,
    val verseText: String = "",
    val verseRange: String = ""
)