package org.churchpresenter.presenter

import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.churchpresenter.sharedui.utils.Utils.systemFontFamilyOrDefault
import org.churchpresenter.sharedui.utils.calculateAutoFitForAllSections
import org.churchpresenter.sharedui.utils.spacingEm
import org.churchpresenter.sharedui.utils.styledDisplayText
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SectionTranslation
import org.churchpresenter.settings.sideBySideLanguageGap
import org.churchpresenter.settings.stackedLanguageGap
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.utils.bilingualGrid
import androidx.compose.ui.text.TextMeasurer

/*
 * The song's auto-fit: the largest lyric size at which every slide (or, slide by slide, the one on
 * screen) fits the frame without wrapping, measured in the 1920x1080 reference space.
 */

/** How the frame is divided between the languages drawn in it, and the room each one gets. */
internal data class SongFitFrame(
    val drawnLanguages: Int,
    val gridRows: Int,
    val gridCols: Int,
    val sideBySide: Boolean,
    val topBottom: Boolean,
    val grid2x2: Boolean,
    val refWidth: Int,
    val refHeight: Int,
)

/** The fit for this song on this output, or null for a song with no sections. */
internal fun SongLook.computeSongFit(
    autoFitTextMeasurer: TextMeasurer,
    maxWidth: Dp,
    maxHeight: Dp,
    scaleFactor: Float,
    fitEachSlide: Boolean,
): SongFit? {
    if (allLyricSections.isEmpty()) return null
    val fitFrame = songFitFrame(maxWidth, maxHeight, scaleFactor)
    val (fitIsLineMode, sectionsForFit) = sectionsForFit()
    val reserved = reservedFitHeight(autoFitTextMeasurer, fitFrame, fitIsLineMode)
    return fitSections(autoFitTextMeasurer, fitFrame, fitIsLineMode, sectionsForFit, reserved, fitEachSlide)
}

private fun SongLook.songFitFrame(maxWidth: Dp, maxHeight: Dp, scaleFactor: Float): SongFitFrame {
    // How many blocks the frame is actually divided into. One language fills it; more
    // split it, in whichever direction the layout says.
    // A language whose lyrics have a box of their own is fitted in it, not here.
    val fitLyricsElement = if (lookAheadEnabled) SongStyleElement.LOOK_AHEAD else SongStyleElement.LYRICS
    val drawnLanguages = activeLanguages
        .count { !ss.isBoxed(fitLyricsElement, isLowerThird, it) }
        .coerceAtLeast(1)
    // [bilingualLayout]'s value as the grid it lays blocks out in. A row (1 × N) or a
    // column (N × 1) still divides by however many languages are actually drawn, exactly
    // as it always did; only a real two-dimensional grid (2 × 2 today) divides by its own
    // fixed row/column counts instead.
    val (gridRows, gridCols) = bilingualGrid(ss.bilingualLayout)
    val sideBySide = drawnLanguages > 1 && gridRows == 1 && gridCols > 1
    val topBottom = drawnLanguages > 1 && gridCols == 1 && gridRows > 1
    val grid2x2 = drawnLanguages > 1 && gridRows == 2 && gridCols == 2

    // The real output's own box, in the same reference space the rest of this fit and
    // scaleFactor's own 1920x1080 assumption are measured in -- not the literal 1920x1080
    // itself. A narrow/portrait output has a different aspect ratio than 16:9, and
    // scaleFactor is only ever the *limiting* dimension's ratio, so the reference-space box
    // implied by dividing back out by it is 1920 wide exactly when width is the constraint
    // but taller than 1080 when it is not -- which is what lets a portrait output's real
    // extra headroom reach this search instead of the fit being computed for a box the
    // output was never actually going to have (issue: lyrics overflowing on vertical
    // outputs, since the post-hoc scale-down by scaleFactor cannot add back room the fit
    // never knew it had).
    val referenceBoxWidth = maxWidth.value / scaleFactor
    val referenceBoxHeight = maxHeight.value / scaleFactor
    val fullWidth = referenceBoxWidth.toInt() - appSettings.projectionSettings.windowLeft -
            appSettings.projectionSettings.windowRight -
            appSettings.songSettings.marginLeft - appSettings.songSettings.marginRight
    // Side by side, each language gets a column; the fit has to hold in the narrowest
    // of them, which with equal weights is every one of them. A 2x2 grid's columns are
    // narrower still, but only ever two of them regardless of how many languages fill it.
    val fitSideGap = ss.layoutExtras.sideBySideLanguageGap()
    val refWidth = when {
        sideBySide -> (fullWidth - (drawnLanguages - 1) * fitSideGap) / drawnLanguages
        grid2x2 -> (fullWidth - (gridCols - 1) * fitSideGap) / gridCols
        else -> fullWidth
    }
    val fullHeight = if (isLowerThird) {
        (referenceBoxHeight * appSettings.songSettings.lowerThirdHeightPercent / 100).toInt() -
                appSettings.projectionSettings.windowTop - appSettings.projectionSettings.windowBottom -
                appSettings.songSettings.marginTop - appSettings.songSettings.marginBottom
    } else {
        referenceBoxHeight.toInt() - appSettings.projectionSettings.windowTop -
                appSettings.projectionSettings.windowBottom -
                appSettings.songSettings.marginTop - appSettings.songSettings.marginBottom
    }
    // Stacked, each language gets a band of the height on the same reasoning.
    val refHeight = when {
        topBottom -> fullHeight / drawnLanguages
        grid2x2 -> fullHeight / gridRows
        else -> fullHeight
    }
    return SongFitFrame(drawnLanguages, gridRows, gridCols, sideBySide, topBottom, grid2x2, refWidth, refHeight)
}

/** The sections the fit measures, and whether the slides are shown a line at a time. */
private fun SongLook.sectionsForFit(): Pair<Boolean, List<LyricSection>> {
    // Resolve display mode to know if we're in line mode
    val fitDisplayMode = if (lookAheadEnabled) {
        if (isLowerThird) ss.lowerThirdLookAheadDisplayMode else ss.lookAheadDisplayMode
    } else {
        if (isLowerThird) ss.lowerThirdDisplayMode else ss.fullscreenDisplayMode
    }
    val fitIsLineMode = fitDisplayMode == Constants.SONG_DISPLAY_MODE_LINE

    // For lookahead: combine each section with its next section so auto-fit
    // accounts for displaying both simultaneously at the same font size.
    // In line mode, only 2 lines are shown (1 main + 1 lookahead), so create
    // 2-line sections pairing each line with the next.
    val sectionsForFit = if (lookAheadEnabled && fitIsLineMode) {
        // Line mode: pair each line with the next line across all sections
        val allLines = allLyricSections.flatMap { it.lines }
        // Every language's lines end to end, in the same order, so line `i` of one is
        // line `i` of the others. Built per language rather than for the secondary
        // alone -- the fit has to measure the longest line of whichever language has
        // it, not of the first two.
        val allLanguageLines = List(availableLanguages) { language ->
            allLyricSections.flatMap { it.allLanguageLines().getOrElse(language) { emptyList() } }
        }
        allLines.indices.map { i ->
            val nextLine = allLines.getOrElse(i + 1) { allLines[i] }
            LyricSection(
                lines = listOf(allLines[i], nextLine),
                translations = allLanguageLines.drop(1).map { languageLines ->
                    if (languageLines.isEmpty()) SectionTranslation()
                    else {
                        val line = languageLines.getOrElse(i) { "" }
                        SectionTranslation(lines = listOf(line, languageLines.getOrElse(i + 1) { line }))
                    }
                },
            )
        }
    } else if (lookAheadEnabled) {
        // Verse mode: combine full section with next section
        allLyricSections.mapIndexed { i, section ->
            val next = allLyricSections.getOrNull(i + 1)
            if (next != null) {
                section.copy(
                    lines = section.lines + next.lines,
                    translations = List(availableLanguages - 1) { language ->
                        val own = section.translations.getOrNull(language)
                        val following = next.translations.getOrNull(language)
                        val joined = own?.lines.orEmpty() + following?.lines.orEmpty()
                        SectionTranslation(title = own?.title.orEmpty(), lines = joined)
                    },
                )
            } else section
        }
    } else allLyricSections
    return fitIsLineMode to sectionsForFit
}

/** The height taken from the lyrics by what is drawn above or beside them. */
private fun SongLook.reservedFitHeight(
    autoFitTextMeasurer: TextMeasurer,
    fitFrame: SongFitFrame,
    fitIsLineMode: Boolean,
): Int {
    val drawnLanguages = fitFrame.drawnLanguages
    val topBottom = fitFrame.topBottom
    val grid2x2 = fitFrame.grid2x2
    // Compute reserved height for what takes height from the lyrics: everything but the
    // bottom edge, which is drawn over them.
    val referenceDensity = Density(1f)
    var reserved = reservedHeadingHeight(autoFitTextMeasurer, referenceDensity)
    // The section label takes height from the lyrics exactly as the title row does,
    // wherever it sits but the bottom edge -- measured with its own face and size.
    val fitLabel = ss.layoutExtras.sectionLabel
    sectionLabelToReserve(fitLabel, allLyricSections, isLowerThird)
        ?.takeUnless { ss.isBoxed(SongStyleElement.SECTION_LABEL, isLowerThird) }
        ?.let { longestLabel ->
        val labelProfile = ss.elementStyle(SongStyleElement.SECTION_LABEL, songTarget)
        val labelStyle = TextStyle(
            fontSize = labelProfile.fontSize.sp,
            fontFamily = systemFontFamilyOrDefault(labelProfile.fontType),
        )
        reserved += autoFitTextMeasurer
            .measure(longestLabel, labelStyle, density = referenceDensity).size.height
    }
    // The fixed dp gaps the real layout draws that a section's own measured lines don't
    // account for: the spacer before the look-ahead line (`LookAheadSpacer`, drawn once
    // per language block) and the gaps between stacked-language blocks (`grid2x2`'s row
    // gap and `topBottom`'s band gap). At low font sizes these are negligible against the
    // text; near the real ceiling they are not, and a search that never reserved them
    // chose a size the real layout then clipped by exactly this much. The look-ahead
    // spacer is `12` in this same reference space at every call site below
    // (`LookAheadSpacer`, `LookAheadPlaceholder`); the language gap is the profile's own
    // `stackedLanguageGap`, as the grid2x2/topBottom render branches draw it.
    if (lookAheadEnabled && !fitIsLineMode) {
        reserved += SONG_LOOK_AHEAD_SPACER_GAP
    }
    if (drawnLanguages > 1 && (topBottom || grid2x2)) {
        reserved += (drawnLanguages - 1) * ss.layoutExtras.stackedLanguageGap()
    }
    return reserved
}

/** The height the title row and the number take from the lyrics, where they sit above or beside them. */
private fun SongLook.reservedHeadingHeight(autoFitTextMeasurer: TextMeasurer, referenceDensity: Density): Int {
    val fitTitleDisplay = if (isLowerThird) ss.titleLowerThirdDisplay else ss.titleDisplay
    val fitTitlePosition = if (isLowerThird) ss.titleLowerThirdPosition else ss.titlePosition
    val fitTitleFontSize = if (isLowerThird) ss.titleLowerThirdFontSize else ss.titleFontSize

    var reserved = 0
    // A boxed element is drawn in its own box and takes nothing from the lyrics.
    val titleInRow = fitTitleDisplay != Constants.NONE && fitTitlePosition != Constants.BELOW_VERSE
    if (titleInRow && !ss.isBoxed(SongStyleElement.TITLE, isLowerThird)) {
        val titleStyle = TextStyle(fontSize = fitTitleFontSize.sp, fontFamily = titleFontFamily)
        val longestTitle = allLyricSections.maxOfOrNull { it.title.length }?.let { len ->
            allLyricSections.first { it.title.length == len }.title
        } ?: ""
        if (longestTitle.isNotEmpty()) {
            reserved += autoFitTextMeasurer.measure(longestTitle, titleStyle, density = referenceDensity).size.height
        }
    }
    reserved += reservedNumberHeight(autoFitTextMeasurer, referenceDensity)
    return reserved
}

/** The height the song number takes from the lyrics in the row above or beside them. */
private fun SongLook.reservedNumberHeight(autoFitTextMeasurer: TextMeasurer, referenceDensity: Density): Int {
    val fitNumberDisplay = if (isLowerThird) ss.showNumberLowerThird else ss.showNumber
    val fitNumberPosition = if (isLowerThird) ss.songNumberLowerThirdPosition else ss.songNumberPosition
    val fitNumberCorner = if (isLowerThird) ss.songNumberLowerThirdCorner else ss.songNumberCorner
    val fitNumberFontSize = if (isLowerThird) ss.songNumberLowerThirdFontSize else ss.songNumberFontSize
    var reserved = 0
    // A cornered number is drawn over the slide rather than in the row above it, so it
    // takes no height from the lyrics and reserves none here.
    val numberInRow = fitNumberDisplay != Constants.NONE && fitNumberCorner == Constants.NONE
    if (numberInRow && fitNumberPosition != Constants.BELOW_VERSE &&
        !ss.isBoxed(SongStyleElement.NUMBER, isLowerThird)
    ) {
        val numStyle = TextStyle(fontSize = fitNumberFontSize.sp, fontFamily = titleFontFamily)
        val maxNum = allLyricSections.maxOfOrNull { it.songNumber } ?: 0
        if (maxNum > 0) {
            reserved += autoFitTextMeasurer.measure(maxNum.toString(), numStyle, density = referenceDensity).size.height
        }
    }
    return reserved
}

@Suppress("LongParameterList")
private fun SongLook.fitSections(
    autoFitTextMeasurer: TextMeasurer,
    fitFrame: SongFitFrame,
    fitIsLineMode: Boolean,
    sectionsForFit: List<LyricSection>,
    reserved: Int,
    fitEachSlide: Boolean,
): SongFit {
    val drawnLanguages = fitFrame.drawnLanguages
    val refWidth = fitFrame.refWidth
    val refHeight = fitFrame.refHeight
    val fitLyricsElement = if (lookAheadEnabled) SongStyleElement.LOOK_AHEAD else SongStyleElement.LYRICS
    // The same tracking the lines are drawn with. Spacing is stored in pixels against
    // the profile's own font size and converted to `em`, so the value does not change
    // as the search tries sizes -- it scales with whichever one it settles on, exactly
    // as the rendered line does.
    val fitLetterEm = spacingEm(lyricsStyleProfile.letterSpacing, lyricsStyleProfile.fontSize)
    val fitWordEm = spacingEm(lyricsStyleProfile.wordSpacing, lyricsStyleProfile.fontSize)
    val baseStyle = TextStyle(
        fontWeight = if (effectiveLyricsBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (effectiveLyricsItalic) FontStyle.Italic else FontStyle.Normal,
        letterSpacing = fitLetterEm.em,
        fontFamily = lyricsFontFamily
    )

    // Slide by slide, only the slide on screen is measured, so a short verse can grow up
    // to the configured size instead of being held to what the song's tallest slide needs.
    val slideFit = if (fitEachSlide) {
        slideFitSections(
            sectionsForFit = sectionsForFit,
            allLyricSections = allLyricSections,
            slide = SlidePosition(lyricSection, displaySectionIndex, displayLineIndex),
            isLineMode = fitIsLineMode,
            lookAheadEnabled = lookAheadEnabled,
        )
    } else {
        null
    }
    // Languages boxed on their own are fitted in their boxes; the rest share the frame.
    val boxedLanguages = activeLanguages.filter { ss.isBoxed(fitLyricsElement, isLowerThird, it) }.toSet()
    val fitSections = (slideFit?.sections ?: sectionsForFit).let { sections ->
        if (boxedLanguages.isEmpty()) sections else sections.map { it.withoutLanguages(boxedLanguages) }
    }
    val shared = calculateAutoFitForAllSections(
        textMeasurer = autoFitTextMeasurer,
        sections = fitSections,
        baseStyle = baseStyle,
        availableWidth = refWidth,
        availableHeight = refHeight,
        reservedHeight = reserved,
        // Every slide, and only while the marker is on: `EndOfSongIndicator` keeps its
        // row on every slide, invisible until the last, so the lyrics do not jump when it
        // appears. Counting it on the last slide alone -- and whether or not it was on --
        // sized that slide smaller than the rest under Each slide (#671).
        includeEndIndicator = ss.showEndOfSongIndicator,
        // Measure what `LyricLine` draws, not the stored line: an uppercase transform
        // and the word spacing below are both applied at render, and a fit that did not
        // include them chose a size whose lines then ran off the side of the output.
        styleText = { styledDisplayText(it, lyricsStyleProfile.transform, fitLetterEm, fitWordEm) },
    )
    // Each language on its own: its own lines, in its own font, in the same room.
    val perLanguage = if (ss.layoutExtras.fitLanguagesSeparately && drawnLanguages > 1) {
        val fitElement = if (lookAheadEnabled) SongStyleElement.LOOK_AHEAD else SongStyleElement.LYRICS
        activeLanguages.associateWith { language ->
            val look = ss.elementStyle(fitElement, songTarget, language)
            val letterEm = spacingEm(look.letterSpacing, look.fontSize)
            val wordEm = spacingEm(look.wordSpacing, look.fontSize)
            calculateAutoFitForAllSections(
                textMeasurer = autoFitTextMeasurer,
                sections = fitSections.map { it.onlyLanguage(language) },
                baseStyle = TextStyle(
                    fontWeight = if (look.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (look.italic) FontStyle.Italic else FontStyle.Normal,
                    letterSpacing = letterEm.em,
                    fontFamily = systemFontFamilyOrDefault(look.fontType),
                ),
                availableWidth = refWidth,
                availableHeight = refHeight,
                reservedHeight = reserved,
                includeEndIndicator = ss.showEndOfSongIndicator,
                styleText = { styledDisplayText(it, look.transform, letterEm, wordEm) },
            )
        }
    } else {
        emptyMap()
    }
    return SongFit(shared, perLanguage)
}
