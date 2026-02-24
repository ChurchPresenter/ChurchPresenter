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
    val titleFontSize: Int = 70,
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
    val primaryBibleFontSize: Int = 70,
    val primaryBibleHorizontalAlignment: String = Constants.CENTER,

    // Primary Bible book reference
    val primaryReferenceColor: String = "#FFFFFF",
    val primaryReferenceFontType: String = "Arial",
    val primaryReferenceFontSize: Int = 70,
    val primaryReferencePosition: String = "Above", // "Above" or "Below"
    val primaryReferenceHorizontalAlignment: String = Constants.CENTER,
    val primaryShowAbbreviation: Boolean = false,

    // Secondary Bible text
    val secondaryBibleColor: String = "#CCCCCC",
    val secondaryBibleFontType: String = "Arial",
    val secondaryBibleFontSize: Int = 70,
    val secondaryBibleHorizontalAlignment: String = Constants.CENTER,

    // Secondary Bible book reference
    val secondaryReferenceColor: String = "#CCCCCC",
    val secondaryReferenceFontType: String = "Arial",
    val secondaryReferenceFontSize: Int = 70,
    val secondaryReferencePosition: String = "Above", // "Above" or "Below"
    val secondaryReferenceHorizontalAlignment: String = Constants.CENTER,
    val secondaryShowAbbreviation: Boolean = false,

    // Language for captions
    val captionLanguage: String = "Interface" // "Interface" or "Database"
)

@Serializable
data class ScreenAssignment(
    val showBible: Boolean = true,
    val showSongs: Boolean = true,
    val showPictures: Boolean = true,
    val showMedia: Boolean = true,
    val showStreaming: Boolean = true,
    val showAnnouncements: Boolean = true,
    val displayMode: String = "fullscreen" // Constants.DISPLAY_MODE_FULLSCREEN or DISPLAY_MODE_LOWER_THIRD
)

@Serializable
data class ProjectionSettings(
    val numberOfWindows: Int = 1, // 1-4 projection windows
    val windowTop: Int = 32,
    val windowLeft: Int = 32,
    val windowRight: Int = 32,
    val windowBottom: Int = 32,
    val screen1Assignment: ScreenAssignment = ScreenAssignment(),
    val screen2Assignment: ScreenAssignment = ScreenAssignment(),
    val screen3Assignment: ScreenAssignment = ScreenAssignment(),
    val screen4Assignment: ScreenAssignment = ScreenAssignment()
)

@Serializable
data class PictureSettings(
    val autoScrollInterval: Float = 5f, // seconds
    val isLooping: Boolean = true,
    val transitionDuration: Float = 500f, // milliseconds
    val animationType: String = Constants.ANIMATION_CROSSFADE
)

@Serializable
data class LottieSearchReplacePair(
    val search: String = "",
    val replace: String = ""
)

@Serializable
data class LottiePreset(
    val id: String = "",
    val label: String = "",
    val savedFileName: String = "",
    val searchReplacePairs: List<LottieSearchReplacePair> = emptyList(),
    val pauseFrame: Float = -1f  // -1 = no pause frame set
)

@Serializable
data class StreamingSettings(
    val lowerThirdFolder: String = "",
    val savedSearchStrings: List<String> = emptyList(),
    val savedReplaceStrings: List<String> = emptyList(),
    val lottiePresets: List<LottiePreset> = emptyList(),
    val windowTop: Int = 0,
    val windowLeft: Int = 0,
    val windowRight: Int = 0,
    val windowBottom: Int = 0
)

@Serializable
data class AppSettings(
    val songSettings: SongSettings = SongSettings(),
    val bibleSettings: BibleSettings = BibleSettings(),
    val backgroundSettings: BackgroundSettings = BackgroundSettings(),
    val projectionSettings: ProjectionSettings = ProjectionSettings(),
    val pictureSettings: PictureSettings = PictureSettings(),
    val streamingSettings: StreamingSettings = StreamingSettings(),
    val theme: String = Constants.LIGHT,
    val language: String = "en"
)
