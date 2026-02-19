package org.churchpresenter.app.churchpresenter.data

import kotlinx.serialization.Serializable
import org.churchpresenter.app.churchpresenter.utils.Constants

@Serializable
data class BackgroundConfig(
    val backgroundType: String = Constants.BACKGROUND_COLOR, // "Default", "Color", "Image"
    val backgroundColor: String = "#000000", // Black
    val backgroundImage: String = ""
)

@Serializable
data class BackgroundSettings(
    val defaultBackgroundColor: String = "#000000", // Black - used when backgroundType is "Default"
    val bibleBackground: BackgroundConfig = BackgroundConfig(),
    val songBackground: BackgroundConfig = BackgroundConfig()
)

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
data class BibleSettings(
    // Bible file management
    val storageDirectory: String = "",
    val bibleFiles: List<String> = emptyList(),

    // Bible selection
    val primaryBible: String = "",
    val secondaryBible: String = "",

    // Global vertical alignment (affects all 4 sections)
    val verticalAlignment: String = Constants.MIDDLE,

    // Primary Bible text
    val primaryBibleColor: String = "#FFFFFF",
    val primaryBibleFontType: String = "Arial",
    val primaryBibleFontSize: Int = 24,
    val primaryBibleHorizontalAlignment: String = Constants.CENTER,

    // Primary Bible book reference
    val primaryReferenceColor: String = "#FFFFFF",
    val primaryReferenceFontType: String = "Arial",
    val primaryReferenceFontSize: Int = 20,
    val primaryReferencePosition: String = "Above", // "Above" or "Below"
    val primaryReferenceHorizontalAlignment: String = Constants.CENTER,
    val primaryShowAbbreviation: Boolean = false,

    // Secondary Bible text
    val secondaryBibleColor: String = "#CCCCCC",
    val secondaryBibleFontType: String = "Arial",
    val secondaryBibleFontSize: Int = 18,
    val secondaryBibleHorizontalAlignment: String = Constants.CENTER,

    // Secondary Bible book reference
    val secondaryReferenceColor: String = "#CCCCCC",
    val secondaryReferenceFontType: String = "Arial",
    val secondaryReferenceFontSize: Int = 16,
    val secondaryReferencePosition: String = "Above", // "Above" or "Below"
    val secondaryReferenceHorizontalAlignment: String = Constants.CENTER,
    val secondaryShowAbbreviation: Boolean = false,

    // Language for captions
    val captionLanguage: String = "Interface" // "Interface" or "Database"
)

@Serializable
data class AppSettings(
    val songSettings: SongSettings = SongSettings(),
    val bibleSettings: BibleSettings = BibleSettings(),
    val backgroundSettings: BackgroundSettings = BackgroundSettings(),
    val theme: String = Constants.LIGHT,
    val language: String = "en" // Default to English
)
