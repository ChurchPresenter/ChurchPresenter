package org.churchpresenter.app.churchpresenter.data

data class BibleSearch(
    var book: String = "",
    var chapter: String = "",
    var verse: String = "",
    var verseText: String = ""
)
