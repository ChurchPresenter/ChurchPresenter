package org.churchpresenter.core.models.stt

data class STTSegment(
    val id: Int,
    val timestamp: String,
    val text: String,
    val start: Double,
    val end: Double,
    val completed: Boolean
)

data class HighlightedWord(
    val word: String,
    val color: String,
    val caseSensitive: Boolean = false,
    val isRegex: Boolean = false
)
