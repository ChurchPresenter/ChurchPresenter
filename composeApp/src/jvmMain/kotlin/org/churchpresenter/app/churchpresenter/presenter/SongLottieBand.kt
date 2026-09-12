package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.applyTextTransform
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongStyleElement
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
/** [text] in this style, cased the way the style says. */
private fun BandSlotStyle.slot(text: String): BandSlotText = BandSlotText(applyTextTransform(text, transform), this)

/** The page the band is on: the section, its place in the song, and the line within it. */
internal data class SongBandPage(
    val section: LyricSection,
    val allSections: List<LyricSection>,
    val displaySectionIndex: Int,
    val lineIndex: Int,
)

internal fun songBandSlots(
    page: SongBandPage,
    settings: SongSettings,
    languageDisplay: String,
    hasSecondSlot: Boolean,
    isKey: Boolean,
): Map<String, BandSlotText> {
    val section = page.section
    val allSections = page.allSections
    val displaySectionIndex = page.displaySectionIndex
    val lineIndex = page.lineIndex
    val lyrics = settings.lyricsSlotStyle(isKey)
    val title = settings.titleSlotStyle(isKey)
    val language = languageDisplay.ifBlank { settings.lowerThirdLanguageDisplay }
    // The title slide is its own thing, laid across the slots the template has: the titles in
    // the text slots — one language each when there are two — and the credits on the reference
    // lines, so it fits a design made for lyrics and their title.
    if (section.type == Constants.SECTION_TYPE_TITLE_SLIDE) {
        val lines = titleSlideLines(section, settings, language)
        val headings = lines.filter { it.element == SongStyleElement.TITLE || it.element == SongStyleElement.NUMBER }
        val credits = lines.filter { it !in headings }.map { it.plainText }
        val text1: String
        val text2: String
        if (hasSecondSlot && headings.size >= 2) {
            text1 = headings.first().plainText
            text2 = headings.drop(1).joinToString("\n") { it.plainText }
        } else {
            text1 = headings.joinToString("\n") { it.plainText }
            text2 = ""
        }
        val reference1: String
        val reference2: String
        if (hasSecondSlot && credits.size >= 2) {
            val half = (credits.size + 1) / 2
            reference1 = credits.take(half).joinToString(CREDIT_SEPARATOR)
            reference2 = credits.drop(half).joinToString(CREDIT_SEPARATOR)
        } else {
            reference1 = credits.joinToString(CREDIT_SEPARATOR)
            reference2 = ""
        }
        return mapOf(
            BibleLottieTemplate.LAYER_TEXT_1 to title.slot(text1),
            BibleLottieTemplate.LAYER_TEXT_2 to title.slot(text2),
            BibleLottieTemplate.LAYER_REFERENCE_1 to BandSlotText(reference1, title),
            BibleLottieTemplate.LAYER_REFERENCE_2 to BandSlotText(reference2, title),
        )
    }
    val lineMode = settings.lowerThirdDisplayMode == Constants.SONG_DISPLAY_MODE_LINE
    fun pick(lines: List<String>): List<String> = when {
        lines.isEmpty() -> lines
        lineMode -> listOf(lines[lineIndex.coerceIn(0, lines.lastIndex)])
        else -> lines
    }
    val primary = pick(section.lines)
    val secondary = pick(section.secondaryLines)
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
    val showTitle = shouldShowText(settings.titleLowerThirdDisplay, section, allSections, displaySectionIndex)
    val showNumber = section.songNumber > 0 &&
        shouldShowText(settings.showNumberLowerThird, section, allSections, displaySectionIndex)
    val titleText = if (!showTitle) "" else listOfNotNull(
        section.songNumber.takeIf { showNumber }?.let { "$it." },
        section.title.takeIf { it.isNotBlank() },
    ).joinToString(" ")
    val secondaryTitle = if (showTitle && text2.isNotEmpty()) section.secondaryTitle else ""
    return mapOf(
        BibleLottieTemplate.LAYER_TEXT_1 to lyrics.slot(text1.joinToString("\n")),
        BibleLottieTemplate.LAYER_TEXT_2 to lyrics.slot(text2.joinToString("\n")),
        BibleLottieTemplate.LAYER_REFERENCE_1 to title.slot(titleText),
        BibleLottieTemplate.LAYER_REFERENCE_2 to title.slot(secondaryTitle),
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
    val page = SongBandPage(section, allSections, displaySectionIndex, lineIndex)
    val slots = remember(page, settings, languageDisplay, hasSecondSlot, isKey) {
        songBandSlots(page, settings, languageDisplay, hasSecondSlot, isKey)
    }
    LottieBand(template, slots, bandFraction, bandClock, isKey, showBackground, modifier)
}

/** Between credits sharing one reference line. */
private const val CREDIT_SEPARATOR = "  ·  "
