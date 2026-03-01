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
    val defaultBackgroundColor: String = "#000000",
    val bibleBackground: BackgroundConfig = BackgroundConfig(),
    val bibleLowerThirdBackground: BackgroundConfig = BackgroundConfig(),
    val songBackground: BackgroundConfig = BackgroundConfig(),
    val songLowerThirdBackground: BackgroundConfig = BackgroundConfig()
)

@Serializable
data class SongSettings(
    // Song file management
    val storageDirectory: String = "",
    val songFiles: List<String> = emptyList(),

    // Song list column widths (dp)
    val colWidthNumber: Int = 70,
    val colWidthTitle: Int = 220,
    val colWidthSongbook: Int = 100,
    val colWidthTune: Int = 60,

    // Left/right panel split — lyrics panel width in dp (0 = use default weight)
    val lyricsPanelWidthDp: Int = 0,

    // Title settings
    val titleDisplay: String = Constants.FIRST_PAGE,
    val titleFontSize: Int = 70,
    val titleFontType: String = "Arial",
    val titleMinFontSize: Int = 16,
    val titleMaxFontSize: Int = 72,
    val titlePosition: String = Constants.MIDDLE,
    val titleHorizontalAlignment: String = Constants.CENTER,
    val titleColor: String = "#FFFFFF", // White
    val titleBold: Boolean = false,
    val titleItalic: Boolean = false,
    val titleUnderline: Boolean = false,
    val titleShadow: Boolean = false,

    // Lyrics settings
    val lyricsFontSize: Int = 24,
    val lyricsLowerThirdFontSize: Int = 18,
    val lyricsFontType: String = "Arial",
    val lyricsMinFontSize: Int = 12,
    val lyricsMaxFontSize: Int = 60,
    val wordWrap: Boolean = true,
    val lyricsAlignment: String = Constants.MIDDLE,
    val lyricsHorizontalAlignment: String = Constants.CENTER,
    val lyricsLowerThirdHorizontalAlignment: String = Constants.CENTER,
    val lyricsColor: String = "#FFFFFF", // White
    val lyricsBold: Boolean = false,
    val lyricsItalic: Boolean = false,
    val lyricsUnderline: Boolean = false,
    val lyricsShadow: Boolean = false,

    // Song number settings
    val songNumberFontSize: Int = 16,
    val songNumberLowerThirdFontSize: Int = 12,
    val showNumber: String = Constants.FIRST_PAGE,
    val songNumberPosition: String = Constants.BOTTOM_RIGHT,
    val songNumberColor: String = "#FFFFFF", // White
    val songNumberBold: Boolean = false,
    val songNumberItalic: Boolean = false,
    val songNumberUnderline: Boolean = false,
    val songNumberShadow: Boolean = false,

    // Transition animation
    val animationType: String = Constants.NONE,
    val transitionDuration: Float = 500f
)

@Serializable
data class BibleSettings(
    // Bible file management
    val storageDirectory: String = "",
    val bibleFiles: List<String> = emptyList(),

    // Bible selection
    val primaryBible: String = "",
    val secondaryBible: String = "",

    // Bible tab column widths (dp); 0 = use default
    val bibleColWidthBook: Int = 200,
    val bibleColWidthChapter: Int = 120,

    // Global vertical alignment (affects all 4 sections)
    val verticalAlignment: String = Constants.MIDDLE,

    // Primary Bible text
    val primaryBibleColor: String = "#FFFFFF",
    val primaryBibleFontType: String = "Arial",
    val primaryBibleFontSize: Int = 70,
    val primaryBibleLowerThirdFontSize: Int = 32,
    val primaryBibleHorizontalAlignment: String = Constants.CENTER,
    val primaryBibleLowerThirdHorizontalAlignment: String = Constants.CENTER,
    val primaryBibleBold: Boolean = false,
    val primaryBibleItalic: Boolean = false,
    val primaryBibleUnderline: Boolean = false,
    val primaryBibleShadow: Boolean = false,

    // Primary Bible book reference
    val primaryReferenceColor: String = "#FFFFFF",
    val primaryReferenceFontType: String = "Arial",
    val primaryReferenceFontSize: Int = 70,
    val primaryReferenceLowerThirdFontSize: Int = 24,
    val primaryReferencePosition: String = "Above", // "Above" or "Below"
    val primaryReferenceHorizontalAlignment: String = Constants.CENTER,
    val primaryReferenceLowerThirdHorizontalAlignment: String = Constants.CENTER,
    val primaryShowAbbreviation: Boolean = false,
    val primaryReferenceBold: Boolean = false,
    val primaryReferenceItalic: Boolean = false,
    val primaryReferenceUnderline: Boolean = false,
    val primaryReferenceShadow: Boolean = false,

    // Secondary Bible text
    val secondaryBibleColor: String = "#CCCCCC",
    val secondaryBibleFontType: String = "Arial",
    val secondaryBibleFontSize: Int = 70,
    val secondaryBibleLowerThirdFontSize: Int = 28,
    val secondaryBibleHorizontalAlignment: String = Constants.CENTER,
    val secondaryBibleLowerThirdHorizontalAlignment: String = Constants.CENTER,
    val secondaryBibleLowerThirdEnabled: Boolean = true,
    val secondaryBibleBold: Boolean = false,
    val secondaryBibleItalic: Boolean = false,
    val secondaryBibleUnderline: Boolean = false,
    val secondaryBibleShadow: Boolean = false,

    // Secondary Bible book reference
    val secondaryReferenceColor: String = "#CCCCCC",
    val secondaryReferenceFontType: String = "Arial",
    val secondaryReferenceFontSize: Int = 70,
    val secondaryReferenceLowerThirdFontSize: Int = 24,
    val secondaryReferencePosition: String = "Above", // "Above" or "Below"
    val secondaryReferenceHorizontalAlignment: String = Constants.CENTER,
    val secondaryReferenceLowerThirdHorizontalAlignment: String = Constants.CENTER,
    val secondaryShowAbbreviation: Boolean = false,
    val secondaryReferenceBold: Boolean = false,
    val secondaryReferenceItalic: Boolean = false,
    val secondaryReferenceUnderline: Boolean = false,
    val secondaryReferenceShadow: Boolean = false,

    // Language for captions
    val captionLanguage: String = "Interface", // "Interface" or "Database"

    // Transition animation
    val animationType: String = Constants.ANIMATION_CROSSFADE,
    val transitionDuration: Float = 500f
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
    val windowBottom: Int = 0,
    val lowerThirdListWidthDp: Int = 240
)

@Serializable
data class AnnouncementsSettings(
    val text: String = "",
    val textColor: String = "#FFFFFF",
    val backgroundColor: String = "#000000",
    val fontSize: Int = 48,
    val fontType: String = "Arial",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val shadow: Boolean = false,
    val horizontalAlignment: String = Constants.CENTER,
    val position: String = Constants.CENTER,
    val animationType: String = Constants.ANIMATION_SLIDE_FROM_BOTTOM,
    val animationDuration: Int = 500
)

@Serializable
data class AppSettings(
    val songSettings: SongSettings = SongSettings(),
    val bibleSettings: BibleSettings = BibleSettings(),
    val backgroundSettings: BackgroundSettings = BackgroundSettings(),
    val projectionSettings: ProjectionSettings = ProjectionSettings(),
    val pictureSettings: PictureSettings = PictureSettings(),
    val streamingSettings: StreamingSettings = StreamingSettings(),
    val announcementsSettings: AnnouncementsSettings = AnnouncementsSettings(),
    val schedulePanelWidthDp: Int = 280,
    val theme: String = Constants.LIGHT,
    val language: String = "en",
    val licenseAccepted: Boolean = false
)
