package org.churchpresenter.app.churchpresenter.data

data class BibleVerse(
    var verseId: String = "",
    var book: Int = 0,
    var chapter: Int = 0,
    var verseNumber: Int = 0,
    var verseText: String = ""
)
