package org.churchpresenter.app.churchpresenter.models

data class LyricSection(
    val type: String, // "verse", "chorus"
    val lines: List<String>
)