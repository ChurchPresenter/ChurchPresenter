package org.churchpresenter.app.churchpresenter.models

data class LyricSection(
    val header: String? = null,
    val labelName: String = "",
    val title: String = "",
    val secondaryTitle: String = "",
    val songNumber: Int = 0,
    val type: String = "", // "verse", "chorus"
    val lines: List<String> = emptyList(),
    val secondaryLines: List<String> = emptyList(),
    val isLastSection: Boolean = false,
    val bpm: Int = 0, // metronome tempo for this song (0 = off)
)