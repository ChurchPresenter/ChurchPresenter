package org.churchpresenter.app.churchpresenter.data

data class BibleBook(
    var book: String = "",
    var bookId: String = "",
    var chapterCount: Int = 0,
    var abbreviation: String = "" // Short form of book name (e.g., "Gen" for "Genesis")
)
