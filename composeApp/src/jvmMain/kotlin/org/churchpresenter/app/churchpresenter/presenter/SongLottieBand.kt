package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.applyTextTransform
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongStyleElement
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.songLanguageSelection
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
 * What the song band shows, by slot: each active language's lyric in its own `TextN` (as many as
 * the template has, up to four), the song title in `Reference1` on the pages the Title display
 * rule allows, and languages 2-4's own titles in `ReferenceN` alongside them once
 * [Constants.SONG_LANG_BOTH] is drawing more than one. One line or the whole section, and which
 * language(s), follow the lower-third song settings the classic band reads; look-ahead and chords
 * have no slot and are left out.
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

/**
 * [languageDisplay]'s active languages against a song of [available] languages, and against
 * [availableSlots] -- however many `TextN`/`ReferenceN` pairs the loaded template actually has.
 *
 * More languages than slots: the first [availableSlots] of them, the same "first two of the stack"
 * rule the Bible band already lived by before this had a third or fourth slot to reach for -- no
 * cramming two languages' lines into one box.
 */
private fun slottedLanguages(languageDisplay: String, available: Int, availableSlots: Int): List<Int> =
    songLanguageSelection(languageDisplay, emptyList(), available).take(availableSlots.coerceAtLeast(1))

internal fun songBandSlots(
    page: SongBandPage,
    settings: SongSettings,
    languageDisplay: String,
    availableSlots: Int,
    isKey: Boolean,
): Map<String, BandSlotText> {
    val section = page.section
    val allSections = page.allSections
    val displaySectionIndex = page.displaySectionIndex
    val lineIndex = page.lineIndex
    val lyrics = settings.lyricsSlotStyle(isKey)
    val title = settings.titleSlotStyle(isKey)
    val language = languageDisplay.ifBlank { settings.lowerThirdLanguageDisplay }
    val slots = BibleLottieTemplate.TEXT_SLOT_LAYERS
    // The title slide is its own thing, laid across the slots the template has: the titles in
    // the text slots — one language each — and the credits split across the reference lines, so
    // it fits a design made for lyrics and their title rather than needing one of its own.
    if (section.type == Constants.SECTION_TYPE_TITLE_SLIDE) {
        val languages = songLanguageSelection(language, emptyList(), section.translations.size + 1)
        val lines = titleSlideLines(section, settings, languages)
        val headings = lines.filter { it.element == SongStyleElement.TITLE || it.element == SongStyleElement.NUMBER }
        val credits = lines.filter { it !in headings }.map { it.plainText }
        val headingSlots = spreadAcrossSlots(headings.map { it.plainText }, availableSlots)
        val creditSlots = spreadAcrossSlots(splitEvenly(credits, availableSlots), availableSlots)
        return buildMap {
            slots.forEachIndexed { i, (textLayer, refLayer) ->
                put(textLayer, title.slot(headingSlots.getOrElse(i) { "" }))
                put(refLayer, BandSlotText(creditSlots.getOrElse(i) { "" }, title))
            }
        }
    }
    val lineMode = settings.lowerThirdDisplayMode == Constants.SONG_DISPLAY_MODE_LINE
    fun pick(lines: List<String>): List<String> = when {
        lines.isEmpty() -> lines
        lineMode -> listOf(lines[lineIndex.coerceIn(0, lines.lastIndex)])
        else -> lines
    }
    val allLines = section.allLanguageLines()
    val allTitles = section.allLanguageTitles()
    val primary = pick(section.lines)
    val showTitle = shouldShowText(settings.titleLowerThirdDisplay, section, allSections, displaySectionIndex)
    val showNumber = section.songNumber > 0 &&
        shouldShowText(settings.showNumberLowerThird, section, allSections, displaySectionIndex)
    val primaryTitleText = if (!showTitle) "" else listOfNotNull(
        section.songNumber.takeIf { showNumber }?.let { "$it." },
        section.title.takeIf { it.isNotBlank() },
    ).joinToString(" ")
    // Language[0] is always the primary; every mode falls back to it when its own pick has nothing.
    val languageTexts: List<String>
    val languageTitles: List<String>
    when (language) {
        Constants.SONG_LANG_SECONDARY, Constants.SONG_LANG_THIRD, Constants.SONG_LANG_FOURTH -> {
            val index = when (language) {
                Constants.SONG_LANG_SECONDARY -> 1
                Constants.SONG_LANG_THIRD -> 2
                else -> 3
            }
            val chosen = pick(allLines.getOrElse(index) { emptyList() }).ifEmpty { primary }
            languageTexts = listOf(chosen.joinToString("\n"))
            languageTitles = listOf(primaryTitleText)
        }
        Constants.SONG_LANG_BOTH -> if (availableSlots <= 1) {
            // A single-slot template has nowhere to put a second language but beside the first, so
            // every active language's lines flatten into one list before the one join at the end --
            // unlike two-plus slots, this has always crammed rather than dropped, and still does.
            // Flattened rather than joined per language and then joined again: a language with
            // nothing to show (an untranslated line, in line mode) must contribute no blank line of
            // its own, the same as `primary + secondary` never did for the original two.
            val everyActive =
                songLanguageSelection(Constants.SONG_LANG_BOTH, emptyList(), allLines.size.coerceAtLeast(1))
            val flatLines = everyActive.flatMap { pick(allLines.getOrElse(it) { emptyList() }) }
            languageTexts = listOf(flatLines.joinToString("\n"))
            languageTitles = listOf(primaryTitleText)
        } else {
            val active = slottedLanguages(Constants.SONG_LANG_BOTH, allLines.size.coerceAtLeast(1), availableSlots)
            languageTexts = active.map { pick(allLines.getOrElse(it) { emptyList() }).joinToString("\n") }
            languageTitles = active.mapIndexed { position, index ->
                if (position == 0) {
                    primaryTitleText
                } else if (!showTitle) {
                    ""
                } else {
                    allTitles.getOrElse(index) { "" }.ifBlank { section.title }
                }
            }
        }
        else -> {
            languageTexts = listOf(primary.joinToString("\n"))
            languageTitles = listOf(primaryTitleText)
        }
    }
    return buildMap {
        slots.forEachIndexed { i, (textLayer, refLayer) ->
            put(textLayer, lyrics.slot(languageTexts.getOrElse(i) { "" }))
            put(refLayer, title.slot(languageTitles.getOrElse(i) { "" }))
        }
    }
}

/**
 * [items], one per slot up to [count] -- or all of them joined into the one slot there is, when
 * there is only one.
 */
private fun spreadAcrossSlots(items: List<String>, count: Int): List<String> =
    if (count <= 1) listOf(items.joinToString("\n")) else List(count) { items.getOrElse(it) { "" } }

/**
 * [items] joined across [count] slots as evenly as they fit -- one bucket when there is one slot
 * or nothing to split, otherwise as many buckets as there are items, up to [count].
 */
private fun splitEvenly(items: List<String>, count: Int): List<String> {
    if (count <= 1 || items.size <= 1) return listOf(items.joinToString(CREDIT_SEPARATOR))
    val buckets = count.coerceAtMost(items.size)
    val perBucket = (items.size + buckets - 1) / buckets
    return items.chunked(perBucket).map { it.joinToString(CREDIT_SEPARATOR) }
}

/** The song band: this page's lyric and title in the song settings' lower-third faces. */
@Composable
internal fun BoxScope.SongLottieBand(
    template: BibleLottieTemplate,
    section: LyricSection,
    settings: SongSettings,
    languageDisplay: String,
    lineIndex: Int,
    outgoingSection: LyricSection?,
    outgoingLineIndex: Int,
    allSections: List<LyricSection>,
    displaySectionIndex: Int,
    bandFraction: Float,
    bandClock: State<BibleBandClock>,
    isKey: Boolean,
    showBackground: Boolean,
    modifier: Modifier = Modifier,
) {
    val availableSlots = template.textSlotCount
    val page = SongBandPage(section, allSections, displaySectionIndex, lineIndex)
    val outgoingPage = outgoingSection?.let {
        SongBandPage(it, allSections, displaySectionIndex, outgoingLineIndex)
    }
    val slots = remember(page, settings, languageDisplay, availableSlots, isKey) {
        songBandSlots(page, settings, languageDisplay, availableSlots, isKey)
    }
    val outgoingSlots = remember(outgoingPage, settings, languageDisplay, availableSlots, isKey) {
        outgoingPage?.let { songBandSlots(it, settings, languageDisplay, availableSlots, isKey) }
    }
    LottieBand(template, slots, outgoingSlots, bandFraction, bandClock, isKey, showBackground, modifier)
}

/** Between credits sharing one reference line. */
private const val CREDIT_SEPARATOR = "  ·  "
