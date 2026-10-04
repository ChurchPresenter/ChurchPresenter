package org.churchpresenter.presenter

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

import org.churchpresenter.sharedui.utils.Utils.parseHexColor
import org.churchpresenter.sharedui.utils.Utils.systemFontFamilyOrDefault
import org.churchpresenter.sharedui.utils.combinedTextDecoration
import org.churchpresenter.sharedui.utils.spacingEm
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SectionTranslation
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.songLanguageSelection
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.SongSettings

/**
 * How a song is drawn on one output, before any sizes are known: which languages, and the fonts,
 * colours, shadows, text styles and alignments of each element, resolved for full screen or lower
 * third and for a key output. Computed afresh on every composition of [SongPresenter].
 */
@Suppress("LongParameterList")
internal class SongLook(
    val lyricSection: LyricSection,
    val appSettings: AppSettings,
    val isLowerThird: Boolean,
    val isLowerThirdVertical: Boolean,
    outputRole: String,
    val transitionAlpha: Float,
    val displayLineIndex: Int,
    val lookAheadEnabled: Boolean,
    val allLyricSections: List<LyricSection>,
    val displaySectionIndex: Int,
    val showBackground: Boolean,
    val crossfadeEnabled: Boolean,
    val languageOverride: String,
    languageSelection: List<Int>,
    val showChords: Boolean,
    resources: SongLookResources,
) {
    val titleFontFamily = resources.titleFontFamily
    val lyricsFontFamily = resources.lyricsFontFamily
    val titleColor = resources.titleColor
    val lyricsColor = resources.lyricsColor
    val chordColor = resources.chordColor
    val laColor = resources.laColor
    val laFontFamily = resources.laFontFamily

    // When languageOverride is set by the per-screen songMode, use it instead of the global setting.
    val isKey = outputRole == Constants.OUTPUT_ROLE_KEY
    val ss = appSettings.songSettings
    val effectiveLangDisplay = if (languageOverride.isNotBlank()) languageOverride else {
        if (lookAheadEnabled) {
            if (isLowerThird) ss.lowerThirdLookAheadLanguageDisplay else ss.lookAheadLanguageDisplay
        } else {
            if (isLowerThird) ss.lowerThirdLanguageDisplay else ss.fullscreenLanguageDisplay
        }
    }

    // How many languages the song carries, and which of them this output draws.
    //
    // The whole song *and* the slide in hand: the auto-fit below measures every section, so it has
    // to divide the frame the way every slide will be laid out -- but `allLyricSections` is only
    // supplied by outputs that offer look-ahead, and reading it alone left every other caller
    // believing a bilingual song had one language and drawing only the primary.
    //
    // Counted by the last translation that has anything to draw, a title on the title slide or words
    // on a lyric slide: an empty one is drawn as nothing, so it must not divide the frame the fit
    // measures against either.
    private fun SectionTranslation.isDrawn() = lines.isNotEmpty() || title.isNotBlank()
    val availableLanguages = maxOf(
        lyricSection.translations.indexOfLast { it.isDrawn() },
        allLyricSections.maxOfOrNull { section -> section.translations.indexOfLast { it.isDrawn() } } ?: -1,
    ) + 2
    val activeLanguages = songLanguageSelection(effectiveLangDisplay, languageSelection, availableLanguages)

    /**
     * The element's outline as this output draws it.
     *
     * A key output carries the shape of the fill as a white matte, and the outline is part of that
     * shape -- so it is kept and painted white rather than dropped, which would key out a hole the
     * width of the stroke around every letter.
     */
    fun keyedOutline(outline: TextOutline): TextOutline =
        if (isKey && outline.isVisible) outline.copy(color = "#FFFFFF") else outline
    val laFontSize = if (isLowerThird) ss.lowerThirdLookAheadNextFontSize else ss.lookAheadNextFontSize
    val laBold = if (isLowerThird) ss.lowerThirdLookAheadNextBold else ss.lookAheadNextBold
    val laItalic = if (isLowerThird) ss.lowerThirdLookAheadNextItalic else ss.lookAheadNextItalic
    val laUnderline = if (isLowerThird) ss.lowerThirdLookAheadNextUnderline else ss.lookAheadNextUnderline
    val laShadowEnabled = if (isLowerThird) ss.lowerThirdLookAheadNextShadow else ss.lookAheadNextShadow
    val laShadowColor =
        parseHexColor(if (isLowerThird) ss.lowerThirdLookAheadNextShadowColor else ss.lookAheadNextShadowColor)
    val laShadowSizeMul =
        (if (isLowerThird) ss.lowerThirdLookAheadNextShadowSize else ss.lookAheadNextShadowSize) / 100f
    val laShadowAlpha =
        ((if (isLowerThird) ss.lowerThirdLookAheadNextShadowOpacity else ss.lookAheadNextShadowOpacity) / 100f)
            .coerceIn(0f, 1f)

    // Per-element shadow customization (resolved per fullscreen / lower third)
    fun makeSongShadow(color: String, size: Int, opacity: Int, alphaScale: Float = 0.78f): Shadow {
        val base = parseHexColor(color)
        val mul = size / 100f
        val alpha = (opacity / 100f).coerceIn(0f, 1f)
        return Shadow(
            color = base.copy(alpha = alpha * alphaScale),
            offset = Offset(2f * mul, 2f * mul),
            blurRadius = 4f * mul
        )
    }
    val titleBaseShadow = makeSongShadow(
        if (isLowerThird) ss.titleLowerThirdShadowColor else ss.titleShadowColor,
        if (isLowerThird) ss.titleLowerThirdShadowSize else ss.titleShadowSize,
        if (isLowerThird) ss.titleLowerThirdShadowOpacity else ss.titleShadowOpacity
    )
    val lyricsBaseShadow = makeSongShadow(
        if (isLowerThird) ss.lyricsLowerThirdShadowColor else ss.lyricsShadowColor,
        if (isLowerThird) ss.lyricsLowerThirdShadowSize else ss.lyricsShadowSize,
        if (isLowerThird) ss.lyricsLowerThirdShadowOpacity else ss.lyricsShadowOpacity
    )

    val songTarget = if (isLowerThird) SongStyleTarget.LOWER_THIRD else SongStyleTarget.FULL_SCREEN

    // Text styles derived from settings (resolved per fullscreen / lower third)
    private val effectiveTitleBold = if (isLowerThird) ss.titleLowerThirdBold else ss.titleBold
    private val effectiveTitleItalic = if (isLowerThird) ss.titleLowerThirdItalic else ss.titleItalic
    private val effectiveTitleUnderline = if (isLowerThird) ss.titleLowerThirdUnderline else ss.titleUnderline
    val effectiveTitleShadow = if (isLowerThird) ss.titleLowerThirdShadow else ss.titleShadow
    val titleStyleProfile = ss.elementStyle(SongStyleElement.TITLE, songTarget)
    val titleTextStyle = TextStyle(
        fontWeight = if (effectiveTitleBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (effectiveTitleItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = combinedTextDecoration(effectiveTitleUnderline, titleStyleProfile.strikethrough),
        letterSpacing = spacingEm(titleStyleProfile.letterSpacing, titleStyleProfile.fontSize).em,
        shadow = if (effectiveTitleShadow) titleBaseShadow else null
    )

    // The song number is drawn from its own profile now. It used to borrow the title's font, colour
    // and face, which left its own stored fields unread -- see `SongSettings.migrateSongNumberStyle`,
    // which carries a styled title across so a settings file written then still looks the same.
    val numberStyleProfile = ss.elementStyle(SongStyleElement.NUMBER, songTarget)
    val songNumberBaseShadow = makeSongShadow(
        numberStyleProfile.shadowColor,
        numberStyleProfile.shadowSize,
        numberStyleProfile.shadowOpacity,
    )
    val songNumberTextStyle = TextStyle(
        fontWeight = if (numberStyleProfile.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (numberStyleProfile.italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = combinedTextDecoration(numberStyleProfile.underline, numberStyleProfile.strikethrough),
        letterSpacing = spacingEm(numberStyleProfile.letterSpacing, numberStyleProfile.fontSize).em,
        shadow = if (numberStyleProfile.shadow) songNumberBaseShadow else null,
    )
    val songNumberColor = if (isKey) Color.White else parseHexColor(numberStyleProfile.color)
    val songNumberFontFamily = systemFontFamilyOrDefault(
        numberStyleProfile.fontType.ifBlank { if (isLowerThird) ss.titleLowerThirdFontType else ss.titleFontType },
    )
    val effectiveLyricsBold = if (lookAheadEnabled) {
        if (isLowerThird) ss.lowerThirdLookAheadBold else ss.lookAheadBold
    } else if (isLowerThird) ss.lyricsLowerThirdBold else ss.lyricsBold
    val effectiveLyricsItalic = if (lookAheadEnabled) {
        if (isLowerThird) ss.lowerThirdLookAheadItalic else ss.lookAheadItalic
    } else if (isLowerThird) ss.lyricsLowerThirdItalic else ss.lyricsItalic
    val effectiveLyricsUnderline = if (lookAheadEnabled) {
        if (isLowerThird) ss.lowerThirdLookAheadUnderline else ss.lookAheadUnderline
    } else if (isLowerThird) ss.lyricsLowerThirdUnderline else ss.lyricsUnderline
    val effectiveLyricsShadow = if (lookAheadEnabled) {
        if (isLowerThird) ss.lowerThirdLookAheadShadow else ss.lookAheadShadow
    } else if (isLowerThird) ss.lyricsLowerThirdShadow else ss.lyricsShadow
    // Which profile the body text is drawn from: the look-ahead slide styles its lines separately.
    val lyricsStyleProfile = ss.elementStyle(
        if (lookAheadEnabled) SongStyleElement.LOOK_AHEAD else SongStyleElement.LYRICS,
        songTarget,
    )
    val lyricsTextStyle = TextStyle(
        fontWeight = if (effectiveLyricsBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (effectiveLyricsItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = combinedTextDecoration(effectiveLyricsUnderline, lyricsStyleProfile.strikethrough),
        letterSpacing = spacingEm(lyricsStyleProfile.letterSpacing, lyricsStyleProfile.fontSize).em,
        shadow = if (effectiveLyricsShadow) lyricsBaseShadow else null
    )
    val chartHorizontalAlignment = when (
        if (isLowerThird) ss.lyricsLowerThirdHorizontalAlignment else ss.lyricsHorizontalAlignment
    ) {
        Constants.LEFT -> Alignment.Start
        Constants.RIGHT -> Alignment.End
        else -> Alignment.CenterHorizontally
    }
    val contentAlignment = when (appSettings.songSettings.lyricsAlignment) {
        Constants.TOP -> Alignment.TopCenter
        Constants.BOTTOM -> Alignment.BottomCenter
        else -> Alignment.Center
    }
    val lyricsHorizontalAlignment = getTextAlign(
        if (lookAheadEnabled) {
            if (isLowerThird) ss.lowerThirdLookAheadHorizontalAlignment else ss.lookAheadHorizontalAlignment
        } else {
            if (isLowerThird) ss.lyricsLowerThirdHorizontalAlignment else ss.lyricsHorizontalAlignment
        }
    )
    val titleHorizontalAlignment = getTextAlign(
        if (isLowerThird) ss.titleLowerThirdHorizontalAlignment else ss.titleHorizontalAlignment
    )
    val songNumberHorizontalAlignment = getTextAlign(
        if (isLowerThird) ss.songNumberLowerThirdHorizontalAlignment else ss.songNumberHorizontalAlignment
    )
    val bgConfig = if (isLowerThird) appSettings.backgroundSettings.songLowerThirdBackground
    else appSettings.backgroundSettings.songBackground
}

/** The fonts and colours a song resolves, remembered so a font is not looked up again on every frame. */
// A plain holder of the seven values remembered together; each has its own name at its use.
@Suppress("LongParameterList")
internal class SongLookResources(
    val titleFontFamily: FontFamily,
    val lyricsFontFamily: FontFamily,
    val titleColor: Color,
    val lyricsColor: Color,
    val chordColor: Color,
    val laColor: Color,
    val laFontFamily: FontFamily,
)

@Composable
internal fun rememberSongLookResources(
    ss: SongSettings,
    isLowerThird: Boolean,
    lookAheadEnabled: Boolean,
    isKey: Boolean,
): SongLookResources {
    // Resolve font families per fullscreen / lower third
    val titleFontFamily = remember(ss.titleFontType, ss.titleLowerThirdFontType, isLowerThird) {
        systemFontFamilyOrDefault(if (isLowerThird) ss.titleLowerThirdFontType else ss.titleFontType)
    }
    val lyricsFontFamily = remember(ss.lyricsFontType, ss.lyricsLowerThirdFontType,
        ss.lookAheadFontType, ss.lowerThirdLookAheadFontType, isLowerThird, lookAheadEnabled) {
        if (lookAheadEnabled) {
            systemFontFamilyOrDefault(if (isLowerThird) ss.lowerThirdLookAheadFontType else ss.lookAheadFontType)
        } else {
            systemFontFamilyOrDefault(if (isLowerThird) ss.lyricsLowerThirdFontType else ss.lyricsFontType)
        }
    }

    // Resolve colors — key mode forces white for a proper key signal
    val titleColor = remember(ss.titleColor, ss.titleLowerThirdColor, isLowerThird, isKey) {
        if (isKey) Color.White
        else parseHexColor(if (isLowerThird) ss.titleLowerThirdColor else ss.titleColor)
    }
    val lyricsColor = remember(ss.lyricsColor, ss.lyricsLowerThirdColor,
        ss.lookAheadColor, ss.lowerThirdLookAheadColor, isLowerThird, lookAheadEnabled, isKey) {
        if (isKey) Color.White
        else if (lookAheadEnabled) {
            parseHexColor(if (isLowerThird) ss.lowerThirdLookAheadColor else ss.lookAheadColor)
        } else {
            parseHexColor(if (isLowerThird) ss.lyricsLowerThirdColor else ss.lyricsColor)
        }
    }
    val chordColor = remember(ss.lyricsChordColor, ss.lyricsLowerThirdChordColor, isLowerThird, isKey) {
        if (isKey) Color.White
        else parseHexColor(if (isLowerThird) ss.lyricsLowerThirdChordColor else ss.lyricsChordColor)
    }
    // Look-ahead next section preview font settings (resolved per fullscreen / lower third)
    val laColor = remember(ss.lookAheadNextColor, ss.lowerThirdLookAheadNextColor, isLowerThird, isKey) {
        if (isKey) Color.White
        else parseHexColor(if (isLowerThird) ss.lowerThirdLookAheadNextColor else ss.lookAheadNextColor)
    }
    val laFontFamily = remember(ss.lookAheadNextFontType, ss.lowerThirdLookAheadNextFontType, isLowerThird) {
        systemFontFamilyOrDefault(if (isLowerThird) ss.lowerThirdLookAheadNextFontType else ss.lookAheadNextFontType)
    }
    return SongLookResources(
        titleFontFamily, lyricsFontFamily, titleColor, lyricsColor, chordColor, laColor, laFontFamily,
    )
}
