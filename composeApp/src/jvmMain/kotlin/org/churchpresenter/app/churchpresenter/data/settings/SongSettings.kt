package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.app.churchpresenter.utils.Constants

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
    val colWidthPlayCount: Int = 60,
    val colWidthAuthor: Int = 120,
    val colWidthComposer: Int = 120,

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

    // End-of-song indicator spacing (number of spaces between each asterisk)
    val endOfSongIndicatorSpacing: Int = 2,

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
