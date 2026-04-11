package org.churchpresenter.app.churchpresenter.data

data class BibleBook(
    val book: String = "",
    val bookId: String = "",
    val chapterCount: Int = 0,
    val abbreviation: String = "" // Short form of book name (e.g., "Gen" for "Genesis")
)
