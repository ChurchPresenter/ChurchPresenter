package org.churchpresenter.app.churchpresenter.models

data class LyricSection(
    val labelName: String = "",
    val title: String = "",
    val songNumber: Int = 0,
    val type: String = "", // "verse", "chorus"
    val lines: List<String> = emptyList(),
    val isLastSection: Boolean = false,
)