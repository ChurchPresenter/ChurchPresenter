package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.applyTextTransform
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants

/** The lyrics' lower-third typography as a band slot draws it. */
internal fun SongSettings.lyricsSlotStyle(isKey: Boolean) = BandSlotStyle(
    font = BandFontKey(lyricsLowerThirdFontType, lyricsLowerThirdBold, lyricsLowerThirdItalic),
    fontSizePt = lyricsLowerThirdFontSize,
    color = if (isKey) Color.White else parseHexColor(lyricsLowerThirdColor),
    letterSpacingPt = lyricsLowerThirdLetterSpacing,
    transform = lyricsLowerThirdTransform,
    justify = justifyOf(lyricsLowerThirdHorizontalAlignment),
    shadow = lyricsLowerThirdShadow,
    shadowColor = parseHexColor(lyricsLowerThirdShadowColor),
    shadowSizePercent = lyricsLowerThirdShadowSize,
    shadowOpacityPercent = lyricsLowerThirdShadowOpacity,
)

/** The title's lower-third typography as a band slot draws it. */
internal fun SongSettings.titleSlotStyle(isKey: Boolean) = BandSlotStyle(
    font = BandFontKey(titleLowerThirdFontType, titleLowerThirdBold, titleLowerThirdItalic),
    fontSizePt = titleLowerThirdFontSize,
    color = if (isKey) Color.White else parseHexColor(titleLowerThirdColor),
    letterSpacingPt = titleLowerThirdLetterSpacing,
    transform = titleLowerThirdTransform,
    justify = justifyOf(titleLowerThirdHorizontalAlignment),
    shadow = titleLowerThirdShadow,
    shadowColor = parseHexColor(titleLowerThirdShadowColor),
    shadowSizePercent = titleLowerThirdShadowSize,
    shadowOpacityPercent = titleLowerThirdShadowOpacity,
)

/**
 * What the song band shows, by slot: the lyric in `Text1` (and the second language in `Text2`
 * when the template has it), the song title in `Reference1` on the pages the Title display
 * rule allows. One line or the whole section, and which language, follow the lower-third song
 * settings the classic band reads; look-ahead and chords have no slot and are left out.
 */
internal fun songBandSlots(
    section: LyricSection,
    settings: SongSettings,
    languageDisplay: String,
    lineIndex: Int,
    allSections: List<LyricSection>,
    displaySectionIndex: Int,
    hasSecondSlot: Boolean,
    isKey: Boolean,
): Map<String, BandSlotText> {
    val lyrics = settings.lyricsSlotStyle(isKey)
    val title = settings.titleSlotStyle(isKey)
    val lineMode = settings.lowerThirdDisplayMode == Constants.SONG_DISPLAY_MODE_LINE
    fun pick(lines: List<String>): List<String> = when {
        lines.isEmpty() -> lines
        lineMode -> listOf(lines[lineIndex.coerceIn(0, lines.lastIndex)])
        else -> lines
    }
    val primary = pick(section.lines)
    val secondary = pick(section.secondaryLines)
    val language = languageDisplay.ifBlank { settings.lowerThirdLanguageDisplay }
    val text1: List<String>
    val text2: List<String>
    when (language) {
        Constants.SONG_LANG_SECONDARY -> {
            text1 = secondary.ifEmpty { primary }
            text2 = emptyList()
        }
        Constants.SONG_LANG_BOTH -> if (hasSecondSlot) {
            text1 = primary
            text2 = secondary
        } else {
            text1 = primary + secondary
            text2 = emptyList()
        }
        else -> {
            text1 = primary
            text2 = emptyList()
        }
    }
    val isTitleSlide = section.type == Constants.SECTION_TYPE_TITLE_SLIDE
    val showTitle = !isTitleSlide &&
        shouldShowText(settings.titleLowerThirdDisplay, section, allSections, displaySectionIndex)
    val showNumber = !isTitleSlide && section.songNumber > 0 &&
        shouldShowText(settings.showNumberLowerThird, section, allSections, displaySectionIndex)
    val titleText = if (!showTitle) "" else listOfNotNull(
        section.songNumber.takeIf { showNumber }?.let { "$it." },
        section.title.takeIf { it.isNotBlank() },
    ).joinToString(" ")
    val secondaryTitle = if (showTitle && text2.isNotEmpty()) section.secondaryTitle else ""
    return mapOf(
        BibleLottieTemplate.LAYER_TEXT_1 to
            BandSlotText(applyTextTransform(text1.joinToString("\n"), lyrics.transform), if (isTitleSlide) title else lyrics),
        BibleLottieTemplate.LAYER_TEXT_2 to BandSlotText(applyTextTransform(text2.joinToString("\n"), lyrics.transform), lyrics),
        BibleLottieTemplate.LAYER_REFERENCE_1 to BandSlotText(applyTextTransform(titleText, title.transform), title),
        BibleLottieTemplate.LAYER_REFERENCE_2 to BandSlotText(applyTextTransform(secondaryTitle, title.transform), title),
    )
}

/** The song band: this page's lyric and title in the song settings' lower-third faces. */
@Composable
internal fun BoxScope.SongLottieBand(
    template: BibleLottieTemplate,
    section: LyricSection,
    settings: SongSettings,
    languageDisplay: String,
    lineIndex: Int,
    allSections: List<LyricSection>,
    displaySectionIndex: Int,
    bandFraction: Float,
    bandClock: BibleBandClock,
    isKey: Boolean,
    showBackground: Boolean,
    modifier: Modifier = Modifier,
) {
    val hasSecondSlot = template.hasLayer(BibleLottieTemplate.LAYER_TEXT_2)
    val slots = remember(section, settings, languageDisplay, lineIndex, allSections, displaySectionIndex, hasSecondSlot, isKey) {
        songBandSlots(section, settings, languageDisplay, lineIndex, allSections, displaySectionIndex, hasSecondSlot, isKey)
    }
    LottieBand(template, slots, bandFraction, bandClock, isKey, showBackground, modifier)
}
