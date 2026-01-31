package org.churchpresenter.app.churchpresenter.data

data class SongSettings(
    // Song file management
    val songFiles: List<String> = emptyList(),

    // Title settings
    val titleDisplay: String = "First Page",
    val titleFontSize: Int = 32,
    val titleFontType: String = "Arial",
    val titleMinFontSize: Int = 16,
    val titleMaxFontSize: Int = 72,
    val titleAlpha: Float = 100f,
    val titleAlignment: String = "Middle",
    val titleHorizontalAlignment: String = "Center",

    // Lyrics settings
    val lyricsFontSize: Int = 24,
    val lyricsFontType: String = "Arial",
    val lyricsMinFontSize: Int = 12,
    val lyricsMaxFontSize: Int = 60,
    val lyricsAlpha: Float = 100f,
    val wordWrap: Boolean = true,
    val lyricsAlignment: String = "Middle",
    val lyricsHorizontalAlignment: String = "Center",

    // Song number settings
    val songNumberFontSize: Int = 16,
    val songNumberFirstPageOnly: Boolean = true,
    val songNumberPosition: String = "Bottom Right"
)

data class AppSettings(
    val songSettings: SongSettings = SongSettings(),
    // Add other tab settings here as needed
)
