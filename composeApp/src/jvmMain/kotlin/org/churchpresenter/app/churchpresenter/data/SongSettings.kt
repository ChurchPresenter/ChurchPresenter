package org.churchpresenter.app.churchpresenter.data

import kotlinx.serialization.Serializable
import org.churchpresenter.app.churchpresenter.utils.Constants

@Serializable
data class SongSettings(
    // Song file management
    val storageDirectory: String = "",
    val songFiles: List<String> = emptyList(),

    // Title settings
    val titleDisplay: String = Constants.FIRST_PAGE,
    val titleFontSize: Int = 32,
    val titleFontType: String = "Arial",
    val titleMinFontSize: Int = 16,
    val titleMaxFontSize: Int = 72,
    val titlePosition: String = Constants.MIDDLE,
    val titleHorizontalAlignment: String = Constants.CENTER,
    val titleColor: String = "#FFFFFF", // White

    // Lyrics settings
    val lyricsFontSize: Int = 24,
    val lyricsFontType: String = "Arial",
    val lyricsMinFontSize: Int = 12,
    val lyricsMaxFontSize: Int = 60,
    val wordWrap: Boolean = true,
    val lyricsAlignment: String = Constants.MIDDLE,
    val lyricsHorizontalAlignment: String = Constants.CENTER,
    val lyricsColor: String = "#FFFFFF", // White

    // Song number settings
    val songNumberFontSize: Int = 16,
    val showNumber: String = Constants.FIRST_PAGE,
    val songNumberPosition: String = Constants.BOTTOM_RIGHT,
    val songNumberColor: String = "#FFFFFF" // White
)

@Serializable
data class AppSettings(
    val songSettings: SongSettings = SongSettings(),
    val theme: String = Constants.LIGHT,
    // Add other tab settings here as needed
)
