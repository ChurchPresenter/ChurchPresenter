package org.churchpresenter.app.churchpresenter.data

import kotlinx.serialization.Serializable
import org.churchpresenter.app.churchpresenter.utils.Constants

@Serializable
data class BackgroundConfig(
    val backgroundType: String = Constants.BACKGROUND_COLOR, // "Default", "Color", "Image", "Video"
    val backgroundColor: String = "#000000", // Black
    val backgroundImage: String = "",
    val backgroundVideo: String = "",
    val backgroundOpacity: Float = 1.0f,
    val gradientEnabled: Boolean = false,
    val gradientTopColor: String = "#000000",
    val gradientTopOpacity: Float = 0.0f,
    val gradientBottomColor: String = "#000000",
    val gradientBottomOpacity: Float = 0.8f,
    val gradientPosition: Float = 0.5f
)

@Serializable
data class BackgroundSettings(
    val defaultBackgroundColor: String = "#000000",
    val defaultBackgroundImage: String = "",
    val defaultBackgroundVideo: String = "",
    val defaultBackgroundType: String = Constants.BACKGROUND_COLOR,
    val defaultLowerThirdBackgroundColor: String = "#000000",
    val defaultLowerThirdBackgroundImage: String = "",
    val defaultLowerThirdBackgroundVideo: String = "",
    val defaultBackgroundOpacity: Float = 1.0f,
    val defaultLowerThirdBackgroundType: String = Constants.BACKGROUND_COLOR,
    val defaultLowerThirdBackgroundOpacity: Float = 1.0f,
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

    // Title settings — lower third
    val titleLowerThirdDisplay: String = Constants.FIRST_PAGE,
    val titleLowerThirdFontSize: Int = 28,
    val titleLowerThirdFontType: String = "Arial",
    val titleLowerThirdPosition: String = Constants.MIDDLE,
    val titleLowerThirdHorizontalAlignment: String = Constants.CENTER,
    val titleLowerThirdColor: String = "#FFFFFF",
    val titleLowerThirdBold: Boolean = false,
    val titleLowerThirdItalic: Boolean = false,
    val titleLowerThirdUnderline: Boolean = false,
    val titleLowerThirdShadow: Boolean = false,

    // Lyrics settings
    val lyricsFontSize: Int = 70,
    val lyricsFontSizeAutoFit: Boolean = true,
    val lyricsLowerThirdFontSize: Int = 28,
    val lyricsLowerThirdFontSizeAutoFit: Boolean = true,
    val lyricsFontType: String = "Arial",
    val lyricsMinFontSize: Int = 12,
    val lyricsMaxFontSize: Int = 60,
    val wordWrap: Boolean = false,
    val lyricsAlignment: String = Constants.MIDDLE,
    val lyricsHorizontalAlignment: String = Constants.CENTER,
    val lyricsLowerThirdHorizontalAlignment: String = Constants.CENTER,
    val lyricsColor: String = "#FFFFFF", // White
    val lyricsBold: Boolean = false,
    val lyricsItalic: Boolean = false,
    val lyricsUnderline: Boolean = false,
    val lyricsShadow: Boolean = false,

    // Lyrics settings — lower third
    val lyricsLowerThirdFontType: String = "Arial",
    val lyricsLowerThirdColor: String = "#FFFFFF",
    val lyricsLowerThirdBold: Boolean = false,
    val lyricsLowerThirdItalic: Boolean = false,
    val lyricsLowerThirdUnderline: Boolean = false,
    val lyricsLowerThirdShadow: Boolean = false,

    // Song Title Slide settings
    val titleSlideEnabled: Boolean = false,

    // Song number settings
    val songNumberFontSize: Int = 70,
    val songNumberLowerThirdFontSize: Int = 28,
    val showNumber: String = Constants.FIRST_PAGE,
    val songNumberPosition: String = Constants.BELOW_VERSE,
    val songNumberHorizontalAlignment: String = Constants.RIGHT,
    val songNumberColor: String = "#FFFFFF", // White
    val songNumberBold: Boolean = false,
    val songNumberItalic: Boolean = false,
    val songNumberUnderline: Boolean = false,
    val songNumberShadow: Boolean = false,

    // Song number settings — lower third
    val showNumberLowerThird: String = Constants.FIRST_PAGE,
    val songNumberLowerThirdColor: String = "#FFFFFF",
    val songNumberLowerThirdPosition: String = Constants.BELOW_VERSE,
    val songNumberLowerThirdHorizontalAlignment: String = Constants.RIGHT,
    val songNumberLowerThirdBold: Boolean = false,
    val songNumberLowerThirdItalic: Boolean = false,
    val songNumberLowerThirdUnderline: Boolean = false,
    val songNumberLowerThirdShadow: Boolean = false,
    val songNumberBeforeTitle: Boolean = true,

    // Text margins (additional padding inside global projection offsets)
    val marginTop: Int = 54,
    val marginBottom: Int = 54,
    val marginLeft: Int = 96,
    val marginRight: Int = 96,

    // Shadow customization — per-element (title)
    val titleShadowColor: String = "#000000",
    val titleShadowSize: Int = 100,
    val titleShadowOpacity: Int = 90,
    val titleLowerThirdShadowColor: String = "#000000",
    val titleLowerThirdShadowSize: Int = 100,
    val titleLowerThirdShadowOpacity: Int = 90,

    // Shadow customization — per-element (lyrics)
    val lyricsShadowColor: String = "#000000",
    val lyricsShadowSize: Int = 100,
    val lyricsShadowOpacity: Int = 90,
    val lyricsLowerThirdShadowColor: String = "#000000",
    val lyricsLowerThirdShadowSize: Int = 100,
    val lyricsLowerThirdShadowOpacity: Int = 90,

    // Legacy shared shadow properties (kept for backward compatibility)
    val shadowColor: String = "#000000",
    val shadowSize: Int = 100,
    val shadowOpacity: Int = 90,
    val lowerThirdShadowColor: String = "#000000",
    val lowerThirdShadowSize: Int = 100,
    val lowerThirdShadowOpacity: Int = 90,

    // Transition animation
    val fadeIn: Boolean = true,
    val fadeOut: Boolean = true,
    val crossfade: Boolean = false,
    val transitionDuration: Float = 500f,

    // Fullscreen display
    val fullscreenDisplayMode: String = Constants.SONG_DISPLAY_MODE_VERSE, // "verse" or "line"
    val fullscreenLanguageDisplay: String = Constants.SONG_LANG_BOTH, // "both", "primary", "secondary"

    // Lower third display
    val lowerThirdDisplayMode: String = Constants.SONG_DISPLAY_MODE_LINE, // "verse" or "line"
    val lowerThirdLanguageDisplay: String = Constants.SONG_LANG_BOTH, // "both", "primary", "secondary"

    // Bilingual layout: "side_by_side" or "top_bottom"
    val bilingualLayout: String = Constants.BILINGUAL_SIDE_BY_SIDE,

    // Look-ahead styling — fullscreen
    val lookAheadDisplayMode: String = Constants.SONG_DISPLAY_MODE_VERSE,
    val lookAheadLanguageDisplay: String = Constants.SONG_LANG_PRIMARY,
    val lookAheadHorizontalAlignment: String = Constants.CENTER,
    val lookAheadFontSize: Int = 70,
    val lookAheadFontSizeAutoFit: Boolean = true,
    val lookAheadFontType: String = "Arial",
    val lookAheadColor: String = "#FFFFFF",
    val lookAheadBold: Boolean = false,
    val lookAheadItalic: Boolean = false,
    val lookAheadUnderline: Boolean = false,
    val lookAheadShadow: Boolean = false,
    val lookAheadShadowColor: String = "#000000",
    val lookAheadShadowSize: Int = 100,
    val lookAheadShadowOpacity: Int = 90,

    // Look-ahead next section preview styling — fullscreen
    val lookAheadNextFontSize: Int = 70,
    val lookAheadNextFontSizeAutoFit: Boolean = true,
    val lookAheadNextFontType: String = "Arial",
    val lookAheadNextColor: String = "#888888",
    val lookAheadNextBold: Boolean = false,
    val lookAheadNextItalic: Boolean = true,
    val lookAheadNextUnderline: Boolean = false,
    val lookAheadNextShadow: Boolean = false,
    val lookAheadNextShadowColor: String = "#000000",
    val lookAheadNextShadowSize: Int = 100,
    val lookAheadNextShadowOpacity: Int = 90,

    // Look-ahead styling — lower third
    val lowerThirdLookAheadDisplayMode: String = Constants.SONG_DISPLAY_MODE_LINE,
    val lowerThirdLookAheadLanguageDisplay: String = Constants.SONG_LANG_PRIMARY,
    val lowerThirdLookAheadHorizontalAlignment: String = Constants.CENTER,
    val lowerThirdLookAheadFontSize: Int = 28,
    val lowerThirdLookAheadFontSizeAutoFit: Boolean = true,
    val lowerThirdLookAheadFontType: String = "Arial",
    val lowerThirdLookAheadColor: String = "#FFFFFF",
    val lowerThirdLookAheadBold: Boolean = false,
    val lowerThirdLookAheadItalic: Boolean = false,
    val lowerThirdLookAheadUnderline: Boolean = false,
    val lowerThirdLookAheadShadow: Boolean = false,
    val lowerThirdLookAheadShadowColor: String = "#000000",
    val lowerThirdLookAheadShadowSize: Int = 100,
    val lowerThirdLookAheadShadowOpacity: Int = 90,

    // Look-ahead next section preview styling — lower third
    val lowerThirdLookAheadNextFontSize: Int = 28,
    val lowerThirdLookAheadNextFontSizeAutoFit: Boolean = true,
    val lowerThirdLookAheadNextFontType: String = "Arial",
    val lowerThirdLookAheadNextColor: String = "#888888",
    val lowerThirdLookAheadNextBold: Boolean = false,
    val lowerThirdLookAheadNextItalic: Boolean = true,
    val lowerThirdLookAheadNextUnderline: Boolean = false,
    val lowerThirdLookAheadNextShadow: Boolean = false,
    val lowerThirdLookAheadNextShadowColor: String = "#000000",
    val lowerThirdLookAheadNextShadowSize: Int = 100,
    val lowerThirdLookAheadNextShadowOpacity: Int = 90
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
    val verticalAlignment: String = Constants.BOTTOM,

    // Primary Bible text
    val primaryBibleColor: String = "#FFFFFF",
    val primaryBibleFontType: String = "Arial",
    val primaryBibleFontSize: Int = 70,
    val primaryBibleLowerThirdFontSize: Int = 32,
    val primaryBibleHorizontalAlignment: String = Constants.LEFT,
    val primaryBibleLowerThirdHorizontalAlignment: String = Constants.LEFT,
    val primaryBibleBold: Boolean = false,
    val primaryBibleItalic: Boolean = false,
    val primaryBibleUnderline: Boolean = false,
    val primaryBibleShadow: Boolean = false,
    val primaryBibleLowerThirdColor: String = "#FFFFFF",
    val primaryBibleLowerThirdFontType: String = "Arial",
    val primaryBibleLowerThirdBold: Boolean = false,
    val primaryBibleLowerThirdItalic: Boolean = false,
    val primaryBibleLowerThirdUnderline: Boolean = false,
    val primaryBibleLowerThirdShadow: Boolean = false,

    // Primary Bible book reference
    val primaryReferenceColor: String = "#FFFFFF",
    val primaryReferenceFontType: String = "Arial",
    val primaryReferenceFontSize: Int = 70,
    val primaryReferenceLowerThirdFontSize: Int = 24,
    val primaryReferencePosition: String = "Below", // "Above" or "Below"
    val primaryReferenceLowerThirdPosition: String = "Below",
    val primaryReferenceHorizontalAlignment: String = Constants.RIGHT,
    val primaryReferenceLowerThirdHorizontalAlignment: String = Constants.RIGHT,
    val primaryShowAbbreviation: Boolean = false,
    val primaryReferenceBold: Boolean = false,
    val primaryReferenceItalic: Boolean = false,
    val primaryReferenceUnderline: Boolean = false,
    val primaryReferenceShadow: Boolean = false,
    val primaryReferenceLowerThirdColor: String = "#FFFFFF",
    val primaryReferenceLowerThirdFontType: String = "Arial",
    val primaryReferenceLowerThirdBold: Boolean = false,
    val primaryReferenceLowerThirdItalic: Boolean = false,
    val primaryReferenceLowerThirdUnderline: Boolean = false,
    val primaryReferenceLowerThirdShadow: Boolean = false,

    // Secondary Bible text
    val secondaryBibleColor: String = "#FFFFFF",
    val secondaryBibleFontType: String = "Arial",
    val secondaryBibleFontSize: Int = 70,
    val secondaryBibleLowerThirdFontSize: Int = 28,
    val secondaryBibleHorizontalAlignment: String = Constants.LEFT,
    val secondaryBibleLowerThirdHorizontalAlignment: String = Constants.LEFT,
    val secondaryBibleLowerThirdEnabled: Boolean = true,
    val secondaryBibleBold: Boolean = false,
    val secondaryBibleItalic: Boolean = false,
    val secondaryBibleUnderline: Boolean = false,
    val secondaryBibleShadow: Boolean = false,
    val secondaryBibleLowerThirdColor: String = "#FFFFFF",
    val secondaryBibleLowerThirdFontType: String = "Arial",
    val secondaryBibleLowerThirdBold: Boolean = false,
    val secondaryBibleLowerThirdItalic: Boolean = false,
    val secondaryBibleLowerThirdUnderline: Boolean = false,
    val secondaryBibleLowerThirdShadow: Boolean = false,

    // Secondary Bible book reference
    val secondaryReferenceColor: String = "#FFFFFF",
    val secondaryReferenceFontType: String = "Arial",
    val secondaryReferenceFontSize: Int = 70,
    val secondaryReferenceLowerThirdFontSize: Int = 24,
    val secondaryReferencePosition: String = "Below", // "Above" or "Below"
    val secondaryReferenceLowerThirdPosition: String = "Below",
    val secondaryReferenceHorizontalAlignment: String = Constants.RIGHT,
    val secondaryReferenceLowerThirdHorizontalAlignment: String = Constants.RIGHT,
    val secondaryShowAbbreviation: Boolean = false,
    val secondaryReferenceBold: Boolean = false,
    val secondaryReferenceItalic: Boolean = false,
    val secondaryReferenceUnderline: Boolean = false,
    val secondaryReferenceShadow: Boolean = false,
    val secondaryReferenceLowerThirdColor: String = "#FFFFFF",
    val secondaryReferenceLowerThirdFontType: String = "Arial",
    val secondaryReferenceLowerThirdBold: Boolean = false,
    val secondaryReferenceLowerThirdItalic: Boolean = false,
    val secondaryReferenceLowerThirdUnderline: Boolean = false,
    val secondaryReferenceLowerThirdShadow: Boolean = false,

    // Language for captions
    val captionLanguage: String = "Interface", // "Interface" or "Database"

    // Text margins (additional padding inside global projection offsets)
    val marginTop: Int = 54,
    val marginBottom: Int = 54,
    val marginLeft: Int = 96,
    val marginRight: Int = 96,

    // Shadow customization — per-element
    val primaryBibleShadowColor: String = "#000000",
    val primaryBibleShadowSize: Int = 100,
    val primaryBibleShadowOpacity: Int = 90,
    val primaryBibleLowerThirdShadowColor: String = "#000000",
    val primaryBibleLowerThirdShadowSize: Int = 100,
    val primaryBibleLowerThirdShadowOpacity: Int = 90,

    val primaryReferenceShadowColor: String = "#000000",
    val primaryReferenceShadowSize: Int = 100,
    val primaryReferenceShadowOpacity: Int = 90,
    val primaryReferenceLowerThirdShadowColor: String = "#000000",
    val primaryReferenceLowerThirdShadowSize: Int = 100,
    val primaryReferenceLowerThirdShadowOpacity: Int = 90,

    val secondaryBibleShadowColor: String = "#000000",
    val secondaryBibleShadowSize: Int = 100,
    val secondaryBibleShadowOpacity: Int = 90,
    val secondaryBibleLowerThirdShadowColor: String = "#000000",
    val secondaryBibleLowerThirdShadowSize: Int = 100,
    val secondaryBibleLowerThirdShadowOpacity: Int = 90,

    val secondaryReferenceShadowColor: String = "#000000",
    val secondaryReferenceShadowSize: Int = 100,
    val secondaryReferenceShadowOpacity: Int = 90,
    val secondaryReferenceLowerThirdShadowColor: String = "#000000",
    val secondaryReferenceLowerThirdShadowSize: Int = 100,
    val secondaryReferenceLowerThirdShadowOpacity: Int = 90,

    // Transition animation
    val fadeIn: Boolean = true,
    val fadeOut: Boolean = true,
    val crossfade: Boolean = false,
    val transitionDuration: Float = 500f
) {
    /** Returns a copy with primary and secondary bible settings swapped. */
    fun swapped() = copy(
        primaryBible = secondaryBible,
        secondaryBible = primaryBible,
        primaryBibleColor = secondaryBibleColor,
        primaryBibleFontType = secondaryBibleFontType,
        primaryBibleFontSize = secondaryBibleFontSize,
        primaryBibleLowerThirdFontSize = secondaryBibleLowerThirdFontSize,
        primaryBibleHorizontalAlignment = secondaryBibleHorizontalAlignment,
        primaryBibleLowerThirdHorizontalAlignment = secondaryBibleLowerThirdHorizontalAlignment,
        primaryBibleBold = secondaryBibleBold,
        primaryBibleItalic = secondaryBibleItalic,
        primaryBibleUnderline = secondaryBibleUnderline,
        primaryBibleShadow = secondaryBibleShadow,
        primaryBibleShadowColor = secondaryBibleShadowColor,
        primaryBibleShadowSize = secondaryBibleShadowSize,
        primaryBibleShadowOpacity = secondaryBibleShadowOpacity,
        primaryBibleLowerThirdShadowColor = secondaryBibleLowerThirdShadowColor,
        primaryBibleLowerThirdShadowSize = secondaryBibleLowerThirdShadowSize,
        primaryBibleLowerThirdShadowOpacity = secondaryBibleLowerThirdShadowOpacity,
        primaryBibleLowerThirdColor = secondaryBibleLowerThirdColor,
        primaryBibleLowerThirdFontType = secondaryBibleLowerThirdFontType,
        primaryBibleLowerThirdBold = secondaryBibleLowerThirdBold,
        primaryBibleLowerThirdItalic = secondaryBibleLowerThirdItalic,
        primaryBibleLowerThirdUnderline = secondaryBibleLowerThirdUnderline,
        primaryBibleLowerThirdShadow = secondaryBibleLowerThirdShadow,
        primaryReferenceColor = secondaryReferenceColor,
        primaryReferenceFontType = secondaryReferenceFontType,
        primaryReferenceFontSize = secondaryReferenceFontSize,
        primaryReferenceLowerThirdFontSize = secondaryReferenceLowerThirdFontSize,
        primaryReferencePosition = secondaryReferencePosition,
        primaryReferenceLowerThirdPosition = secondaryReferenceLowerThirdPosition,
        primaryReferenceHorizontalAlignment = secondaryReferenceHorizontalAlignment,
        primaryReferenceLowerThirdHorizontalAlignment = secondaryReferenceLowerThirdHorizontalAlignment,
        primaryShowAbbreviation = secondaryShowAbbreviation,
        primaryReferenceBold = secondaryReferenceBold,
        primaryReferenceItalic = secondaryReferenceItalic,
        primaryReferenceUnderline = secondaryReferenceUnderline,
        primaryReferenceShadow = secondaryReferenceShadow,
        primaryReferenceShadowColor = secondaryReferenceShadowColor,
        primaryReferenceShadowSize = secondaryReferenceShadowSize,
        primaryReferenceShadowOpacity = secondaryReferenceShadowOpacity,
        primaryReferenceLowerThirdShadowColor = secondaryReferenceLowerThirdShadowColor,
        primaryReferenceLowerThirdShadowSize = secondaryReferenceLowerThirdShadowSize,
        primaryReferenceLowerThirdShadowOpacity = secondaryReferenceLowerThirdShadowOpacity,
        primaryReferenceLowerThirdColor = secondaryReferenceLowerThirdColor,
        primaryReferenceLowerThirdFontType = secondaryReferenceLowerThirdFontType,
        primaryReferenceLowerThirdBold = secondaryReferenceLowerThirdBold,
        primaryReferenceLowerThirdItalic = secondaryReferenceLowerThirdItalic,
        primaryReferenceLowerThirdUnderline = secondaryReferenceLowerThirdUnderline,
        primaryReferenceLowerThirdShadow = secondaryReferenceLowerThirdShadow,
        secondaryBibleColor = primaryBibleColor,
        secondaryBibleFontType = primaryBibleFontType,
        secondaryBibleFontSize = primaryBibleFontSize,
        secondaryBibleLowerThirdFontSize = primaryBibleLowerThirdFontSize,
        secondaryBibleHorizontalAlignment = primaryBibleHorizontalAlignment,
        secondaryBibleLowerThirdHorizontalAlignment = primaryBibleLowerThirdHorizontalAlignment,
        secondaryBibleBold = primaryBibleBold,
        secondaryBibleItalic = primaryBibleItalic,
        secondaryBibleUnderline = primaryBibleUnderline,
        secondaryBibleShadow = primaryBibleShadow,
        secondaryBibleShadowColor = primaryBibleShadowColor,
        secondaryBibleShadowSize = primaryBibleShadowSize,
        secondaryBibleShadowOpacity = primaryBibleShadowOpacity,
        secondaryBibleLowerThirdShadowColor = primaryBibleLowerThirdShadowColor,
        secondaryBibleLowerThirdShadowSize = primaryBibleLowerThirdShadowSize,
        secondaryBibleLowerThirdShadowOpacity = primaryBibleLowerThirdShadowOpacity,
        secondaryBibleLowerThirdColor = primaryBibleLowerThirdColor,
        secondaryBibleLowerThirdFontType = primaryBibleLowerThirdFontType,
        secondaryBibleLowerThirdBold = primaryBibleLowerThirdBold,
        secondaryBibleLowerThirdItalic = primaryBibleLowerThirdItalic,
        secondaryBibleLowerThirdUnderline = primaryBibleLowerThirdUnderline,
        secondaryBibleLowerThirdShadow = primaryBibleLowerThirdShadow,
        secondaryReferenceColor = primaryReferenceColor,
        secondaryReferenceFontType = primaryReferenceFontType,
        secondaryReferenceFontSize = primaryReferenceFontSize,
        secondaryReferenceLowerThirdFontSize = primaryReferenceLowerThirdFontSize,
        secondaryReferencePosition = primaryReferencePosition,
        secondaryReferenceLowerThirdPosition = primaryReferenceLowerThirdPosition,
        secondaryReferenceHorizontalAlignment = primaryReferenceHorizontalAlignment,
        secondaryReferenceLowerThirdHorizontalAlignment = primaryReferenceLowerThirdHorizontalAlignment,
        secondaryShowAbbreviation = primaryShowAbbreviation,
        secondaryReferenceBold = primaryReferenceBold,
        secondaryReferenceItalic = primaryReferenceItalic,
        secondaryReferenceUnderline = primaryReferenceUnderline,
        secondaryReferenceShadow = primaryReferenceShadow,
        secondaryReferenceShadowColor = primaryReferenceShadowColor,
        secondaryReferenceShadowSize = primaryReferenceShadowSize,
        secondaryReferenceShadowOpacity = primaryReferenceShadowOpacity,
        secondaryReferenceLowerThirdShadowColor = primaryReferenceLowerThirdShadowColor,
        secondaryReferenceLowerThirdShadowSize = primaryReferenceLowerThirdShadowSize,
        secondaryReferenceLowerThirdShadowOpacity = primaryReferenceLowerThirdShadowOpacity,
        secondaryReferenceLowerThirdColor = primaryReferenceLowerThirdColor,
        secondaryReferenceLowerThirdFontType = primaryReferenceLowerThirdFontType,
        secondaryReferenceLowerThirdBold = primaryReferenceLowerThirdBold,
        secondaryReferenceLowerThirdItalic = primaryReferenceLowerThirdItalic,
        secondaryReferenceLowerThirdUnderline = primaryReferenceLowerThirdUnderline,
        secondaryReferenceLowerThirdShadow = primaryReferenceLowerThirdShadow,
    )
}

@Serializable
data class ScreenAssignment(
    val targetDisplay: Int = -1,  // -1 = auto (resolved at runtime), -2 = none, 0+ = specific display (legacy)
    val targetType: String = "screen",  // "screen" or "decklink"
    val targetBoundsX: Int = Int.MIN_VALUE,  // screen bounds for reliable mapping (MIN_VALUE = unset)
    val targetBoundsY: Int = Int.MIN_VALUE,
    val targetBoundsW: Int = 0,
    val targetBoundsH: Int = 0,
    val keyTargetDisplay: Int = Constants.KEY_TARGET_NONE,  // -2 = none (disabled), 0+ = specific display/device
    val keyTargetType: String = "screen",  // "screen" or "decklink"
    val keyTargetBoundsX: Int = Int.MIN_VALUE,
    val keyTargetBoundsY: Int = Int.MIN_VALUE,
    val keyTargetBoundsW: Int = 0,
    val keyTargetBoundsH: Int = 0,
    val showBible: Boolean = true,
    val showSongs: Boolean = true,
    val showPictures: Boolean = true,
    val showMedia: Boolean = true,
    val showStreaming: Boolean = true,
    val showAnnouncements: Boolean = true,
    val showWebsite: Boolean = true,
    val displayMode: String = "fullscreen", // Constants.DISPLAY_MODE_FULLSCREEN or DISPLAY_MODE_LOWER_THIRD
    val songLookAhead: Boolean = false, // enable look-ahead for songs on this output
    val showFullscreenBackground: Boolean = true, // show configured background in fullscreen mode
    val showLowerThirdBackground: Boolean = true  // show configured background in lower third mode
) {
    /** Whether a key output target is configured */
    val hasKeyOutput: Boolean get() = keyTargetDisplay >= 0

    /** Primary window role: "fill" if key output is configured, "normal" otherwise */
    val primaryOutputRole: String get() = if (hasKeyOutput) Constants.OUTPUT_ROLE_FILL else Constants.OUTPUT_ROLE_NORMAL
}

@Serializable
data class ProjectionSettings(
    val windowTop: Int = 32,
    val windowLeft: Int = 32,
    val windowRight: Int = 32,
    val windowBottom: Int = 32,
    val screenAssignments: List<ScreenAssignment> = listOf(ScreenAssignment()),
    val audioOutputDeviceId: String = "", // empty = system default
    val vlcPath: String = "", // custom VLC installation directory (empty = auto-detect)
    val lowerThirdHeightPercent: Int = 33 // 10-60, used by Bible & Song presenters
) {
    fun getAssignment(index: Int): ScreenAssignment =
        screenAssignments.getOrElse(index) { ScreenAssignment() }

    fun withAssignment(index: Int, assignment: ScreenAssignment): ProjectionSettings {
        val mutable = screenAssignments.toMutableList()
        while (mutable.size <= index) mutable.add(ScreenAssignment())
        mutable[index] = assignment
        return copy(screenAssignments = mutable)
    }
}


@Serializable
data class PictureSettings(
    val storageDirectory: String = "",
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
    val backgroundColor: String = "#00000000",
    val fontSize: Int = 48,
    val fontType: String = "Arial",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val shadow: Boolean = false,
    val shadowColor: String = "#000000",
    val shadowSize: Int = 100,
    val shadowOpacity: Int = 78,
    val horizontalAlignment: String = Constants.CENTER,
    val position: String = Constants.CENTER,
    val animationType: String = Constants.ANIMATION_SLIDE_FROM_BOTTOM,
    val animationDuration: Int = 12000,
    val loopCount: Int = 0,
    val timerHours: Int = 0,
    val timerMinutes: Int = 0,
    val timerSeconds: Int = 0,
    val timerTextColor: String = "#FFFFFF",
    val timerExpiredText: String = ""
)

@Serializable
data class ServerSettings(
    val enabled: Boolean = false,
    val port: Int = Constants.SERVER_DEFAULT_PORT,
    val apiKeyEnabled: Boolean = false,
    val apiKey: String = "",
    /** Optional fixed hostname/IP shown in the Server URL.
     *  Leave blank to auto-detect from the active network interface. */
    val serverHost: String = "",
    /** When false, POST /api/presentations/upload and POST /api/pictures/upload
     *  return 403 and no files are written to disk. */
    val fileUploadEnabled: Boolean = false
)

@Serializable
data class WebBookmark(
    val url: String = "",
    val title: String = ""
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
    val serverSettings: ServerSettings = ServerSettings(),
    val presentationStorageDirectory: String = "",
    val mediaStorageDirectory: String = "",
    val schedulePanelWidthDp: Int = 280,
    val schedulePanelCollapsed: Boolean = false,
    val previewPanelWidthDp: Int = 280,
    val previewPanelCollapsed: Boolean = false,
    val theme: String = Constants.SYSTEM,
    val language: String = "en",
    val licenseAccepted: Boolean = false,
    val webBookmarks: List<WebBookmark> = emptyList(),
    val windowPlacement: String = "maximized",
    val windowWidth: Int = 1280,
    val windowHeight: Int = 800,
    val windowX: Int = -1,
    val windowY: Int = -1
)
