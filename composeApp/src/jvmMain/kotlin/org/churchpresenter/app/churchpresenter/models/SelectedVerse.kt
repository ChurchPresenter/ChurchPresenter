package org.churchpresenter.app.churchpresenter.models

data class SelectedVerse(
    val bookName: String = "",
    val chapter: Int = 0,
    val verseNumber: Int = 0,
    val verseText: String = ""
)