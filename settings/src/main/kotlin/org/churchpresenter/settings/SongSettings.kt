package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.settings.utils.Constants

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
    /**
     * Where the title sits relative to the verse: [Constants.ABOVE_VERSE] or [Constants.BELOW_VERSE].
     *
     * This defaulted to [Constants.MIDDLE], which `SongPresenter` draws the title row at neither of
     * -- it renders the row only above or below -- so out of the box the title was configured to
     * appear and then appeared nowhere. Above matches where the number is not, and where a title
     * belongs.
     */
    val titlePosition: String = Constants.ABOVE_VERSE,
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
    val titleLowerThirdPosition: String = Constants.ABOVE_VERSE,
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

    /**
     * Whether a chorus is repeated after every verse, rather than presented only where it is
     * written.
     *
     * A hymnal writes the chorus once and expects it sung after each verse, which is what this does
     * and why it defaults on -- switching it off for existing installs would rearrange every song
     * they present without warning. A song that writes each repeat out in full, or that places a
     * chorus deliberately (before verse 1, after verse 2 only), wants it off: then the sections are
     * presented exactly as the file has them.
     *
     * Either way no chorus is ever dropped, and a second distinct chorus is never collapsed into the
     * first -- that was a data-loss bug (#403), not a mode.
     */
    val autoRepeatChorus: Boolean = true,
    val lyricsAlignment: String = Constants.MIDDLE,
    val lyricsHorizontalAlignment: String = Constants.CENTER,
    val lyricsLowerThirdHorizontalAlignment: String = Constants.CENTER,
    val lyricsColor: String = "#FFFFFF", // White
    /** The chords' own color in a chart; everything else about them follows the lyrics. */
    val lyricsChordColor: String = "#4FD3E8",
    val lyricsBold: Boolean = false,
    val lyricsItalic: Boolean = false,
    val lyricsUnderline: Boolean = false,
    val lyricsShadow: Boolean = false,

    // Lyrics settings — lower third
    val lyricsLowerThirdFontType: String = "Arial",
    val lyricsLowerThirdColor: String = "#FFFFFF",
    val lyricsLowerThirdChordColor: String = "#4FD3E8",
    val lyricsLowerThirdBold: Boolean = false,
    val lyricsLowerThirdItalic: Boolean = false,
    val lyricsLowerThirdUnderline: Boolean = false,
    val lyricsLowerThirdShadow: Boolean = false,

    /**
     * Whether the song editor's preview shows chords. An editor preference rather than a per-song
     * one: someone who works with chord charts wants them on for every song they open, and someone
     * who never uses them should not have to switch them off again each time.
     */
    val editorShowChords: Boolean = true,

    // Song Title Slide settings
    val titleSlideEnabled: Boolean = false,
    val titleSlideShowSongNumber: Boolean = true,

    // Song number settings
    val songNumberFontSize: Int = 70,
    val songNumberLowerThirdFontSize: Int = 28,
    val showNumber: String = Constants.FIRST_PAGE,
    val songNumberPosition: String = Constants.BELOW_VERSE,
    /**
     * Pins the number to a corner of the slide instead of letting it flow with the title.
     *
     * [Constants.NONE] leaves it in the row above or below the lyrics that [songNumberPosition] and
     * [songNumberHorizontalAlignment] describe. Any of [Constants.TOP_LEFT], [Constants.TOP_RIGHT],
     * [Constants.BOTTOM_LEFT] or [Constants.BOTTOM_RIGHT] takes it out of that row and draws it over
     * the slide in that corner, inside the configured margins -- so it costs the lyrics no height
     * and never moves when the title does.
     *
     * Bottom right by default, which is where [songNumberPosition] and
     * [songNumberHorizontalAlignment] already put it out of the box -- so a fresh install looks the
     * same either way, and a corner is what the number is now pinned by rather than a row it shares.
     * Documents written before this field existed are pinned to [Constants.NONE] by the schema 9
     * migration instead, so an operator who had moved the number keeps where they put it.
     */
    val songNumberCorner: String = Constants.BOTTOM_RIGHT,
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
    /** [songNumberCorner] for the lower third; the corners are the band's, not the screen's. */
    val songNumberLowerThirdCorner: String = Constants.BOTTOM_RIGHT,
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

    /**
     * How much of the output's height the lower-third band takes, as a whole percentage. 10..60.
     *
     * Per content type rather than global. It used to be one number on `ProjectionSettings`, and
     * two things were wrong with that. Only the Bible and song presenters ever read it -- the Lottie
     * lower third, announcements, captions and Q&A all size themselves -- so it was never a property
     * of the projection window; and being single, it forced scripture and lyrics into the same band,
     * when wanting a shallow one for a verse and a deeper one for two lines of a chorus is the usual
     * reason to reach for the number at all.
     *
     * This one is lyrics'. Every output kind honours it without knowing it exists: a screen
     * window, a Browser Source and an NDI sender all render the same presenter with the same
     * `AppSettings`. The control it replaced reached only the first of those -- it lived on the
     * Screen Assignment card, so an operator sending an NDI lower third could see the band on air
     * and find nothing in settings that moved it.
     */
    val lowerThirdHeightPercent: Int = 33,

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
    /**
     * **Overridden at every live call site -- see [ScreenAssignment.songMode].**
     *
     * This and the three fields like it below are read by `SongPresenter` only when it is given no
     * `languageOverride`, and every real caller passes that output's own `songMode`, which is never
     * blank. So a control wired to these restricts nothing that reaches a screen. The Song settings
     * tab writes `songMode` instead.
     */
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
    val lowerThirdLookAheadNextShadowOpacity: Int = 90,

    // ── Typography that reaches the text rather than the TextStyle alone ─────────────────────────
    //
    // Struck-through text, tracking, word spacing and a case transform, for each of the five things
    // a song draws and on each of the two outputs. Spacing is in points at the element's own
    // configured size and is turned into a fraction of the em as it is drawn, so it keeps its
    // proportion through the output's resolution scale and the auto-fit; the transform is one of
    // [Constants.TEXT_TRANSFORM_NONE], `_UPPERCASE`, `_LOWERCASE` or `_CAPITALIZE` and is applied at
    // draw time, so the stored lyrics are never altered.

    // The song number
    val songNumberFontType: String = "",
    val songNumberLowerThirdFontType: String = "",
    val songNumberShadowColor: String = "#000000",
    val songNumberShadowSize: Int = 100,
    val songNumberShadowOpacity: Int = 90,
    val songNumberLowerThirdShadowColor: String = "#000000",
    val songNumberLowerThirdShadowSize: Int = 100,
    val songNumberLowerThirdShadowOpacity: Int = 90,
    /**
     * Where the next-section marker sits across the slide.
     *
     * It had no alignment of its own and inherited the look-ahead line's; blank keeps doing that, so
     * a settings file written before this reads back looking exactly as it did.
     */
    val lookAheadNextHorizontalAlignment: String = "",
    val lowerThirdLookAheadNextHorizontalAlignment: String = "",

    val songNumberStrikethrough: Boolean = false,
    val songNumberLetterSpacing: Int = 0,
    val songNumberWordSpacing: Int = 0,
    val songNumberTransform: String = Constants.TEXT_TRANSFORM_NONE,
    val songNumberLowerThirdStrikethrough: Boolean = false,
    val songNumberLowerThirdLetterSpacing: Int = 0,
    val songNumberLowerThirdWordSpacing: Int = 0,
    val songNumberLowerThirdTransform: String = Constants.TEXT_TRANSFORM_NONE,

    // The song title
    val titleStrikethrough: Boolean = false,
    val titleLetterSpacing: Int = 0,
    val titleWordSpacing: Int = 0,
    val titleTransform: String = Constants.TEXT_TRANSFORM_NONE,
    val titleLowerThirdStrikethrough: Boolean = false,
    val titleLowerThirdLetterSpacing: Int = 0,
    val titleLowerThirdWordSpacing: Int = 0,
    val titleLowerThirdTransform: String = Constants.TEXT_TRANSFORM_NONE,

    // The lyrics
    val lyricsStrikethrough: Boolean = false,
    val lyricsLetterSpacing: Int = 0,
    val lyricsWordSpacing: Int = 0,
    val lyricsTransform: String = Constants.TEXT_TRANSFORM_NONE,
    val lyricsLowerThirdStrikethrough: Boolean = false,
    val lyricsLowerThirdLetterSpacing: Int = 0,
    val lyricsLowerThirdWordSpacing: Int = 0,
    val lyricsLowerThirdTransform: String = Constants.TEXT_TRANSFORM_NONE,

    // The look-ahead line
    val lookAheadStrikethrough: Boolean = false,
    val lookAheadLetterSpacing: Int = 0,
    val lookAheadWordSpacing: Int = 0,
    val lookAheadTransform: String = Constants.TEXT_TRANSFORM_NONE,
    val lowerThirdLookAheadStrikethrough: Boolean = false,
    val lowerThirdLookAheadLetterSpacing: Int = 0,
    val lowerThirdLookAheadWordSpacing: Int = 0,
    val lowerThirdLookAheadTransform: String = Constants.TEXT_TRANSFORM_NONE,

    // The next-section marker
    val lookAheadNextStrikethrough: Boolean = false,
    val lookAheadNextLetterSpacing: Int = 0,
    val lookAheadNextWordSpacing: Int = 0,
    val lookAheadNextTransform: String = Constants.TEXT_TRANSFORM_NONE,
    val lowerThirdLookAheadNextStrikethrough: Boolean = false,
    val lowerThirdLookAheadNextLetterSpacing: Int = 0,
    val lowerThirdLookAheadNextWordSpacing: Int = 0,
    val lowerThirdLookAheadNextTransform: String = Constants.TEXT_TRANSFORM_NONE,

    // The band behind each line and the box around the block, one record per profile. Nested
    // rather than ten more families of flat fields: these are eleven numbers each, and a
    // hundred and ten fields is not a settings class anyone can read.
    val songNumberBackdrop: TextBackdrop = TextBackdrop(),
    val songNumberLowerThirdBackdrop: TextBackdrop = TextBackdrop(),
    val titleBackdrop: TextBackdrop = TextBackdrop(),
    val titleLowerThirdBackdrop: TextBackdrop = TextBackdrop(),
    val lyricsBackdrop: TextBackdrop = TextBackdrop(),
    val lyricsLowerThirdBackdrop: TextBackdrop = TextBackdrop(),
    val lookAheadBackdrop: TextBackdrop = TextBackdrop(),
    val lowerThirdLookAheadBackdrop: TextBackdrop = TextBackdrop(),
    val lookAheadNextBackdrop: TextBackdrop = TextBackdrop(),
    val lowerThirdLookAheadNextBackdrop: TextBackdrop = TextBackdrop(),
)

/**
 * Carries a styled title's look across to the song number, once and only for a file that predates
 * the number having a look of its own.
 *
 * The number used to be drawn with the title's font, colour and face -- its own stored fields were
 * there but nothing read them, so every settings file has them sitting at their defaults. Now that
 * the number is drawn from its own fields, a file whose title was styled would have shown a plain
 * white number where it used to show a styled one. This copies the title across so nothing changes
 * appearance; a number already styled away from the defaults is left alone, which is what makes it
 * idempotent and what stops it treading on a deliberate choice.
 *
 * The font is deliberately **not** copied. A blank [songNumberFontType] already means "use the
 * title's", which is what the presenter falls back to -- writing the title's face in would pin the
 * number to whatever the title happened to be that day and stop it following a later change.
 */
fun SongSettings.migrateSongNumberStyle(): SongSettings {
    val untouched = SongSettings()
    fun styled(target: SongSettings) = target.songNumberColor != untouched.songNumberColor ||
        target.songNumberBold != untouched.songNumberBold ||
        target.songNumberItalic != untouched.songNumberItalic ||
        target.songNumberUnderline != untouched.songNumberUnderline ||
        target.songNumberShadow != untouched.songNumberShadow ||
        target.songNumberFontType.isNotEmpty()
    if (styled(this)) return this
    return copy(
        songNumberColor = titleColor,
        songNumberBold = titleBold,
        songNumberItalic = titleItalic,
        songNumberUnderline = titleUnderline,
        songNumberShadow = titleShadow,
        songNumberShadowColor = titleShadowColor,
        songNumberShadowSize = titleShadowSize,
        songNumberShadowOpacity = titleShadowOpacity,
        songNumberLowerThirdColor = titleLowerThirdColor,
        songNumberLowerThirdBold = titleLowerThirdBold,
        songNumberLowerThirdItalic = titleLowerThirdItalic,
        songNumberLowerThirdUnderline = titleLowerThirdUnderline,
        songNumberLowerThirdShadow = titleLowerThirdShadow,
        songNumberLowerThirdShadowColor = titleLowerThirdShadowColor,
        songNumberLowerThirdShadowSize = titleLowerThirdShadowSize,
        songNumberLowerThirdShadowOpacity = titleLowerThirdShadowOpacity,
    )
}
