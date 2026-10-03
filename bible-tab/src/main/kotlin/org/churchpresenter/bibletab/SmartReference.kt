package org.churchpresenter.bibletab

internal data class SmartReference(
    val bookIndex: Int,
    val chapter: Int?,
    val verseStart: Int?,
    val verseEnd: Int?
)
