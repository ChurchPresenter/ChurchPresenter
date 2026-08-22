package org.churchpresenter.bible

data class BibleSearch(
    val book: String = "",
    val chapter: String = "",
    val verse: String = "",
    val verseText: String = ""
)
