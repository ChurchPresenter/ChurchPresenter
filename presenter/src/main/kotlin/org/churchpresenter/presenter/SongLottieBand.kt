package org.churchpresenter.presenter

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.churchpresenter.sharedui.utils.Utils.parseHexColor
import org.churchpresenter.sharedui.utils.VoiceColors
import org.churchpresenter.sharedui.utils.VoicedRange
import org.churchpresenter.sharedui.utils.voicedLine
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.songchords.SecondVoice
import org.churchpresenter.core.models.songs.shownNumber

/** The lyrics' lower-third typography as a band slot draws it. */
internal fun SongSettings.lyricsSlotStyle(isKey: Boolean) = BandSlotStyle(
    font = BandFontKey(lyricsLowerThirdFontType, lyricsLowerThirdBold, lyricsLowerThirdItalic),
    fontSizePt = lyricsLowerThirdFontSize,
    color = if (isKey) Color.White else parseHexColor(lyricsLowerThirdColor),
    voiceColors = if (isKey) VoiceColors(Color.White, Color.White, Color.White) else layoutExtras.voiceColors(),
    letterSpacingPt = lyricsLowerThirdLetterSpacing,
    transform = lyricsLowerThirdTransform,
    justify = justifyOf(lyricsLowerThirdHorizontalAlignment),
    shadow = lyricsLowerThirdShadow,
    shadowColor = parseHexColor(lyricsLowerThirdShadowColor),
    shadowSizePercent = lyricsLowerThirdShadowSize,
    shadowOpacityPercent = lyricsLowerThirdShadowOpacity,
)

/**
 * The title's typography as a band slot draws it: the lower third's, or with [fullScreen] the
 * full screen's -- a title slide's own Lottie played over a whole output.
 */
internal fun SongSettings.titleSlotStyle(isKey: Boolean, fullScreen: Boolean = false) =
    if (fullScreen) titleFullScreenSlotStyle(isKey) else titleLowerThirdSlotStyle(isKey)

private fun SongSettings.titleFullScreenSlotStyle(isKey: Boolean) = BandSlotStyle(
    font = BandFontKey(titleFontType, titleBold, titleItalic),
    fontSizePt = titleFontSize,
    color = if (isKey) Color.White else parseHexColor(titleColor),
    letterSpacingPt = titleLetterSpacing,
    transform = titleTransform,
    justify = justifyOf(titleHorizontalAlignment),
    shadow = titleShadow,
    shadowColor = parseHexColor(titleShadowColor),
    shadowSizePercent = titleShadowSize,
    shadowOpacityPercent = titleShadowOpacity,
)

private fun SongSettings.titleLowerThirdSlotStyle(isKey: Boolean) = BandSlotStyle(
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
/**
 * [text] in this style, cased the way the style says, its voice markup taken out and remembered as
 * the ranges the other voices' colours go on.
 */
private fun BandSlotStyle.slot(text: String): BandSlotText {
    val plain = StringBuilder()
    val voiceRanges = mutableListOf<VoicedRange>()
    text.split('\n').forEachIndexed { i, line ->
        if (i > 0) plain.append('\n')
        val voiced = voicedLine(line, transform)
        voiced.voiceRanges.forEach {
            voiceRanges += it.copy(range = (it.range.first + plain.length)..(it.range.last + plain.length))
        }
        plain.append(voiced.text)
    }
    return BandSlotText(plain.toString(), this, voiceRanges)
}

/** The page the band is on: the section, its place in the song, and the line within it. */
internal data class SongBandPage(
    val section: LyricSection,
    val allSections: List<LyricSection>,
    val displaySectionIndex: Int,
    val lineIndex: Int,
)

/**
 * What one page of a song puts in the band's slots, given the [languages] the output draws -- the
 * song's own slots, in the order the output shows them (`SongLook.activeLanguages`), the same list
 * its full-screen slide draws by, as the Bible band is handed its output's own verses.
 *
 * One language per text slot, and its title in the reference slot under it. More languages than
 * slots: the first [availableSlots] of them -- no cramming two languages' lines into one box --
 * except on a single-slot template, which has nowhere else to put them and so stacks them all.
 */
internal fun songBandSlots(
    page: SongBandPage,
    settings: SongSettings,
    languages: List<Int>,
    availableSlots: Int,
    isKey: Boolean,
    /** Drawn over a whole output -- a title slide's own Lottie -- so the title takes the full screen's look. */
    fullScreen: Boolean = false,
    /** How many `ReferenceN` lines the template has; a title slide's credits are split across them. */
    referenceSlots: Int = availableSlots,
): Map<String, BandSlotText> {
    val section = page.section
    val lyrics = settings.lyricsSlotStyle(isKey)
    val title = settings.titleSlotStyle(isKey, fullScreen)
    val slots = BibleLottieTemplate.TEXT_SLOT_LAYERS
    val shown = languages.ifEmpty { listOf(0) }
    // The title slide is its own thing, laid across the slots the template has: the titles in
    // the text slots — one language each — and the credits split across the reference lines, so
    // it fits a design made for lyrics and their title rather than needing one of its own.
    if (section.type == Constants.SECTION_TYPE_TITLE_SLIDE) {
        val (headings, credits) = titleSlideHeadingsAndCredits(section, settings, shown)
        val headingSlots = spreadAcrossSlots(headings, availableSlots)
        // Across the reference lines the template has, which can be fewer than its text lines.
        val creditSlots = spreadAcrossSlots(splitEvenly(credits, referenceSlots), referenceSlots)
        return buildMap {
            slots.forEachIndexed { i, (textLayer, refLayer) ->
                put(textLayer, title.slot(headingSlots.getOrElse(i) { "" }))
                put(refLayer, BandSlotText(creditSlots.getOrElse(i) { "" }, title))
            }
        }
    }
    val (languageTexts, languageTitles) = bandLanguageTexts(page, settings, shown, availableSlots)
    return buildMap {
        slots.forEachIndexed { i, (textLayer, refLayer) ->
            put(textLayer, lyrics.slot(languageTexts.getOrElse(i) { "" }))
            put(refLayer, title.slot(languageTitles.getOrElse(i) { "" }))
        }
    }
}

/** Each text slot's lines and the title under it, for a lyric page showing [shown] -- see [songBandSlots]. */
private fun bandLanguageTexts(
    page: SongBandPage,
    settings: SongSettings,
    shown: List<Int>,
    availableSlots: Int,
): Pair<List<String>, List<String>> {
    val section = page.section
    val lineMode = settings.lowerThirdDisplayMode == Constants.SONG_DISPLAY_MODE_LINE
    fun pick(lines: List<String>): List<String> = when {
        lines.isEmpty() -> lines
        // A line and the voice-2/3 lines answering it are one page — see [SecondVoice.lineGroups].
        lineMode -> lines.slice(SecondVoice.lineGroupAt(lines, page.lineIndex.coerceIn(0, lines.lastIndex)))
        else -> lines
    }
    val allLines = section.allLanguageLines()
    val allTitles = section.allLanguageTitles()
    val showTitle = shouldShowText(settings.titleLowerThirdDisplay, section, page.allSections, page.displaySectionIndex)
    val showNumber = section.shownNumber() != null &&
        shouldShowText(settings.showNumberLowerThird, section, page.allSections, page.displaySectionIndex)
    fun titleOf(language: Int) = allTitles.getOrElse(language) { "" }.ifBlank { section.title }
    // The leading language's title carries the number: a plain one reads "25. Title", an alias as
    // written.
    val leadingTitle = if (!showTitle) "" else listOfNotNull(
        section.shownNumber()?.takeIf { showNumber }?.let { if (section.alias.isBlank()) "$it." else it },
        titleOf(shown.first()).takeIf { it.isNotBlank() },
    ).joinToString(" ")
    val linesOf = { language: Int -> pick(allLines.getOrElse(language) { emptyList() }) }
    return when {
        // A language with nothing on this page shows the first language's words rather than none.
        shown.size == 1 -> listOf(linesOf(shown.first()).ifEmpty { pick(section.lines) }.joinToString("\n")) to
            listOf(leadingTitle)
        // A single-slot template has nowhere to put a second language but beside the first, so
        // every language's lines flatten into one list before the one join -- a language with
        // nothing on this page contributes no blank line of its own.
        availableSlots <= 1 -> listOf(shown.flatMap(linesOf).joinToString("\n")) to listOf(leadingTitle)
        else -> {
            val active = shown.take(availableSlots)
            active.map { linesOf(it).joinToString("\n") } to active.mapIndexed { position, language ->
                when {
                    position == 0 -> leadingTitle
                    !showTitle -> ""
                    else -> titleOf(language)
                }
            }
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
internal fun splitEvenly(items: List<String>, count: Int): List<String> {
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
    languages: List<Int>,
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
    fullScreen: Boolean = false,
) {
    val availableSlots = template.textSlotCount
    val referenceSlots = template.referenceSlotCount
    val page = SongBandPage(section, allSections, displaySectionIndex, lineIndex)
    val outgoingPage = outgoingSection?.let {
        SongBandPage(it, allSections, displaySectionIndex, outgoingLineIndex)
    }
    val slots = remember(page, settings, languages, availableSlots, isKey, fullScreen, referenceSlots) {
        songBandSlots(page, settings, languages, availableSlots, isKey, fullScreen, referenceSlots)
    }
    val outgoingSlots = remember(outgoingPage, settings, languages, availableSlots, isKey, fullScreen) {
        outgoingPage?.let {
            songBandSlots(it, settings, languages, availableSlots, isKey, fullScreen, referenceSlots)
        }
    }
    val presetTexts = remember(section, settings, languages, template) {
        titleSlidePresetTexts(section, settings, languages, template.layerNames)
    }
    LottieBand(template, slots, outgoingSlots, bandFraction, bandClock, isKey, showBackground, modifier, presetTexts)
}

/** Between credits sharing one reference line. */
private const val CREDIT_SEPARATOR = "  ·  "
