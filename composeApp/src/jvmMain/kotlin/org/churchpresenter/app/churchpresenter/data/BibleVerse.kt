package org.churchpresenter.app.churchpresenter.data

data class BibleVerse(
    val verseId: String = "",
    val book: Int = 0,
    val chapter: Int = 0,
    val verseNumber: Int = 0,
    val verseText: String = ""
)
