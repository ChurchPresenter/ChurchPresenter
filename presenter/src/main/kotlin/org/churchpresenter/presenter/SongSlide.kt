package org.churchpresenter.presenter

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.churchpresenter.sharedui.composables.rememberTextBlockBackdrop
import org.churchpresenter.sharedui.utils.combinedTextDecoration
import org.churchpresenter.sharedui.utils.spacingEm
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.MAX_SONG_TRANSLATIONS
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.utils.bilingualGrid
import org.churchpresenter.sharedui.composables.TextBlockBackdrop
import org.churchpresenter.sharedui.presenter.BoxMargins
import org.churchpresenter.sharedui.presenter.textBoxArea

/**
 * One slide of a song as the frame draws it: which lines of which languages, the chart rows, and
 * the styling of every element on it.
 *
 * It forwards [SongFrame]'s values under their own names, so the composables drawing the slide --
 * extensions of this class, in `SongSlideLines.kt`, `SongSlideHeadings.kt` and
 * `SongSlideLayout.kt` -- read them as they did when they were local to one function.
 */
internal class SongSlide(
    val frame: SongFrame,
    val section: LyricSection,
    val lineIndex: Int,
    val innerModifier: Modifier,
) {
    val isKey get() = frame.isKey
    val ss get() = frame.ss
    val activeLanguages get() = frame.activeLanguages
    val laFontSize get() = frame.laFontSize
    val laBold get() = frame.laBold
    val laItalic get() = frame.laItalic
    val laUnderline get() = frame.laUnderline
    val laShadowEnabled get() = frame.laShadowEnabled
    val laShadowColor get() = frame.laShadowColor
    val laShadowSizeMul get() = frame.laShadowSizeMul
    val laShadowAlpha get() = frame.laShadowAlpha
    val songTarget get() = frame.songTarget
    val titleStyleProfile get() = frame.titleStyleProfile
    val numberStyleProfile get() = frame.numberStyleProfile
    val songNumberColor get() = frame.songNumberColor
    val songNumberFontFamily get() = frame.songNumberFontFamily
    val lyricsStyleProfile get() = frame.lyricsStyleProfile
    val chartHorizontalAlignment get() = frame.chartHorizontalAlignment
    val contentAlignment get() = frame.contentAlignment
    val lyricsHorizontalAlignment get() = frame.lyricsHorizontalAlignment
    val titleHorizontalAlignment get() = frame.titleHorizontalAlignment
    val songNumberHorizontalAlignment get() = frame.songNumberHorizontalAlignment
    val titleFontFamily get() = frame.titleFontFamily
    val lyricsFontFamily get() = frame.lyricsFontFamily
    val titleColor get() = frame.titleColor
    val lyricsColor get() = frame.lyricsColor
    val chordColor get() = frame.chordColor
    val laColor get() = frame.laColor
    val laFontFamily get() = frame.laFontFamily
    val appSettings get() = frame.appSettings
    val isLowerThird get() = frame.isLowerThird
    val isLowerThirdVertical get() = frame.isLowerThirdVertical
    val lookAheadEnabled get() = frame.lookAheadEnabled
    val allLyricSections get() = frame.allLyricSections
    val displaySectionIndex get() = frame.displaySectionIndex
    val showChords get() = frame.showChords
    val scaleFactor get() = frame.scaleFactor
    val titleTextStyleScaled get() = frame.titleTextStyleScaled
    val songNumberTextStyleScaled get() = frame.songNumberTextStyleScaled
    val lyricsTextStyleScaled get() = frame.lyricsTextStyleScaled
    val scaledTitleFontSize get() = frame.scaledTitleFontSize
    val autoFitFontSize get() = frame.autoFitFontSize
    val languageFitSizes get() = frame.languageFitSizes
    val autoFitEnabled get() = frame.autoFitEnabled
    val effectiveLyricsFontSize get() = frame.effectiveLyricsFontSize
    val scaledLyricsFontSize get() = frame.scaledLyricsFontSize
    val scaledSongNumberFontSize get() = frame.scaledSongNumberFontSize

    fun keyedOutline(outline: TextOutline): TextOutline = frame.keyedOutline(outline)
    fun scaleElementShadow(color: String, size: Int, opacity: Int): Shadow =
        frame.scaleElementShadow(color, size, opacity)

    /** Each language's backdrop block for its lyric lines, remembered by [TextContent]. */
    lateinit var lyricsBlocks: List<TextBlockBackdrop>
    /** Each language's backdrop block for its look-ahead lines, remembered by [TextContent]. */
    lateinit var laBlocks: List<TextBlockBackdrop>

    private val titleDisplay = if (isLowerThird) ss.titleLowerThirdDisplay else ss.titleDisplay
    private val numberDisplay = if (isLowerThird) ss.showNumberLowerThird else ss.showNumber
    // The title slide's lines are the song's title and credit, so they take the Title
    // element's style -- what the settings tab's Title tab edits -- rather than the
    // lyrics', and the title row above the lyrics stays out of it: it would repeat the
    // slide.
    val isTitleSlide = section.type == Constants.SECTION_TYPE_TITLE_SLIDE
    // A boxed title or number is drawn in its box (`SlideBoxes`), never in the row.
    private val titleBoxed = ss.isBoxed(SongStyleElement.TITLE, isLowerThird)
    private val numberBoxed = ss.isBoxed(SongStyleElement.NUMBER, isLowerThird)
    val shouldShowTitle =
        shouldShowText(titleDisplay, section, allLyricSections, displaySectionIndex) && !isTitleSlide &&
            !titleBoxed
    val shouldShowSongNumber =
        shouldShowText(numberDisplay, section, allLyricSections, displaySectionIndex) &&
            section.songNumber > 0 && !isTitleSlide && !numberBoxed
    // "Configured" means not set to "None" — title/number could appear on some slides
    val titleConfigured = titleDisplay != Constants.NONE && !titleBoxed
    val numberConfigured = numberDisplay != Constants.NONE && section.songNumber > 0 && !numberBoxed
    val effectiveTitlePosition = if (isLowerThird) ss.titleLowerThirdPosition else ss.titlePosition
    val effectiveSongNumberPosition = if (isLowerThird) ss.songNumberLowerThirdPosition else ss.songNumberPosition
    // Which corner the number is pinned to, or NONE for the row it shares with the title.
    val songNumberCorner = if (isLowerThird) ss.songNumberLowerThirdCorner else ss.songNumberCorner
    val numberInCorner = numberConfigured && songNumberCorner != Constants.NONE
    // Where the section label and the next-section lines sit: an edge, or held on the lyrics.
    val sectionLabelProfile = ss.elementStyle(SongStyleElement.SECTION_LABEL, songTarget)
    val sectionLabelPosition = ss.layoutExtras.sectionLabel.positionFor(isLowerThird)
    val nextSectionPosition = ss.layoutExtras.nextSectionPosition.positionFor(isLowerThird)
    // isLowerThirdVertical forces bilingual content to stack (one below the other)
    // instead of side-by-side — see the useSideBySide gate further below — same
    // band/geometry as horizontal otherwise.
    // The title slide has no verse to fit, no look-ahead and no chart -- it is a
    // heading and its credits, each drawn in its own element's profile -- so it is
    // drawn by its own composable, in the same box the lyrics would have had.
    val allDisplayLines = section.lines
    private val hasChart = showChords && section.chordLines.isNotEmpty()
    // Resolve per-mode settings based on fullscreen vs lower third
    // When lookAheadEnabled, the entire screen uses lookahead's own display mode
    val displayMode = if (lookAheadEnabled) {
        if (isLowerThird) ss.lowerThirdLookAheadDisplayMode else ss.lookAheadDisplayMode
    } else {
        if (isLowerThird) ss.lowerThirdDisplayMode else ss.fullscreenDisplayMode
    }
    // Look-ahead portion uses same display mode as the screen
    private val laDisplayMode = displayMode
    val laIsLineMode = laDisplayMode == Constants.SONG_DISPLAY_MODE_LINE

    val isLineMode = displayMode == Constants.SONG_DISPLAY_MODE_LINE
    val effectiveLineIndex = if (isLineMode && lineIndex < 0) 0 else lineIndex

    // Get next section for look-ahead
    val nextSection: LyricSection? = if (lookAheadEnabled && displaySectionIndex >= 0) {
        allLyricSections.getOrNull(displaySectionIndex + 1)?.takeIf { it.lines.isNotEmpty() }
    } else null

    // Every language this output draws, sliced the same way: the words now and the
    // words next. One call rather than the four parallel `val`s this replaced --
    // primary main, primary look-ahead, secondary main, secondary look-ahead --
    // which could not grow past two languages without becoming eight.
    val slideBlocks = songLanguageBlocks(
        section = section,
        nextSection = nextSection,
        languages = activeLanguages,
        modes = SongSlideModes(
            lookAheadEnabled = lookAheadEnabled,
            isLineMode = isLineMode,
            laIsLineMode = laIsLineMode,
            lineIndex = effectiveLineIndex,
        ),
    )
    // The languages laid out here: a language whose lyrics are boxed is drawn in its
    // box instead, and one whose next section is boxed keeps only its lyrics here.
    val boxLyricsElement =
        if (lookAheadEnabled) SongStyleElement.LOOK_AHEAD else SongStyleElement.LYRICS
    val languageBlocks = slideBlocks
        .filterNot { ss.isBoxed(boxLyricsElement, isLowerThird, it.index) }
        .map { block ->
            if (ss.isBoxed(SongStyleElement.NEXT_SECTION, isLowerThird, block.index)) {
                block.copy(lookAheadLines = emptyList())
            } else {
                block
            }
        }

    // Sliced the way the words are: one row in line mode, the section in verse
    // mode, the look-ahead's own row after it.
    val mainChartRows: List<String> = when {
        !hasChart -> emptyList()
        // The section as written, including a chord-only intro folded onto it.
        !isLineMode -> section.chordLines
        else -> listOfNotNull(chartRowFor(section, effectiveLineIndex.coerceAtLeast(0)))
    }
    // The next line of this section, when line mode has one left to show.
    val nextLineHere = if (lookAheadEnabled && isLineMode && laIsLineMode) {
        effectiveLineIndex.takeIf { it in 0 until allDisplayLines.size - 1 }?.plus(1)
    } else {
        null
    }
    val laChartRows: List<String> = when {
        !hasChart -> emptyList()
        nextLineHere != null -> listOfNotNull(chartRowFor(section, nextLineHere))
        nextSection == null -> emptyList()
        laIsLineMode -> listOfNotNull(chartRowFor(nextSection, 0))
        else -> nextSection.chordLines.ifEmpty { nextSection.lines }
    }

    // The title row shows the leading drawn language's title, so an output set to
    // one language shows that language's title rather than the primary's. Falls
    // back to the song's own whenever that language has none, which is the common
    // case: a second language is often lyrics with no separate title.
    private val titles = section.allLanguageTitles()
    val effectiveTitle = slideBlocks.firstOrNull()
        ?.let { titles.getOrNull(it.index) }
        ?.takeIf { it.isNotEmpty() }
        ?: section.title

    // The title is drawn in the leading language's own title profile once that
    // language has a look of its own, and in the primary's otherwise -- the same
    // rule the lyric lines follow.
    val titleLanguage = slideBlocks.firstOrNull()?.index ?: 0
    val titleOwnStyling = if (ss.languageOverridesStyle(titleLanguage)) {
        songLineStyling(
            profile = ss.elementStyle(SongStyleElement.TITLE, songTarget, titleLanguage),
            autoFitFontSize = null,
            scaleFactor = scaleFactor,
            isKey = isKey,
            shadowOf = this::scaleElementShadow,
        )
    } else {
        null
    }
    val titleProfileHere = titleOwnStyling?.profile ?: titleStyleProfile
    val titleFontFamilyHere = titleOwnStyling?.fontFamily ?: titleFontFamily
    val titleColorHere = titleOwnStyling?.color ?: titleColor
    val titleFontSizeHere = titleOwnStyling?.fontSize ?: scaledTitleFontSize
    val titleTextStyleHere = titleOwnStyling?.textStyle ?: titleTextStyleScaled
    val titleAlignHere = titleOwnStyling?.let { getTextAlign(it.profile.horizontalAlignment) }
        ?: titleHorizontalAlignment

    val isMultiLanguage = languageBlocks.size > 1
    // Row, column or 2x2 grid, from the same [bilingualGrid] mapping the auto-fit
    // above already read. A vertical lower third is too narrow for a row or a 2x2
    // grid -- both fall through to the stacked branch below, which already
    // special-cases isLowerThird (true for vertical too) with a compact stack.
    private val grid = bilingualGrid(appSettings.songSettings.bilingualLayout)
    private val gridRows = grid.first
    private val gridCols = grid.second
    val useSideBySide = gridRows == 1 && gridCols > 1 && !isLowerThirdVertical
    val useGrid2x2 = gridRows == 2 && gridCols == 2 && !isLowerThirdVertical

    // Look-ahead text style with full font controls
    val laBaseShadow = Shadow(
        color = laShadowColor.copy(alpha = laShadowAlpha),
        offset = Offset(6f * scaleFactor * laShadowSizeMul, 6f * scaleFactor * laShadowSizeMul),
        blurRadius = 12f * scaleFactor * laShadowSizeMul
    )
    private val laStyleProfile = ss.elementStyle(SongStyleElement.NEXT_SECTION, songTarget)

    val lookAheadTextStyle = TextStyle(
        fontWeight = if (laBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (laItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = combinedTextDecoration(laUnderline, laStyleProfile.strikethrough),
        letterSpacing = spacingEm(laStyleProfile.letterSpacing, laStyleProfile.fontSize).em,
        shadow = if (laShadowEnabled) laBaseShadow else null
    )
    // Look-ahead next uses auto-fit capped at its own configured max
    val laAutoFitEnabled =
        if (isLowerThird) ss.lowerThirdLookAheadNextFontSizeAutoFit else ss.lookAheadNextFontSizeAutoFit
    val effectiveLaFontSize = if (laAutoFitEnabled) {
        (autoFitFontSize ?: laFontSize).coerceAtMost(laFontSize)
    } else laFontSize
    private val scaledLaFontSize = (effectiveLaFontSize * scaleFactor).sp

    // How each language draws its lyric lines and its look-ahead lines.
    //
    // Language 0, and every language that has not asked for a look of its own, get
    // the values already resolved above rather than a freshly derived copy of them.
    // That is not just an optimisation: those values carry the look-ahead slide's
    // own overrides and the key-output white, and rebuilding them from the stored
    // profile alone would quietly drop both.
    val primaryLyricStyling = SongLineStyling(
        profile = lyricsStyleProfile,
        color = lyricsColor,
        fontFamily = lyricsFontFamily,
        fontSize = scaledLyricsFontSize,
        textStyle = lyricsTextStyleScaled,
    )
    val primaryLaStyling = SongLineStyling(
        profile = laStyleProfile,
        color = laColor,
        fontFamily = laFontFamily,
        fontSize = scaledLaFontSize,
        textStyle = lookAheadTextStyle,
    )
    val lyricsElement = if (lookAheadEnabled) SongStyleElement.LOOK_AHEAD else SongStyleElement.LYRICS
    val languageLyricStyling = List(MAX_SONG_TRANSLATIONS) { language ->
        if (languageFitSizes.isNotEmpty()) {
            // Fitted one by one: every language at its own size, its own Auto-fit
            // switch deciding whether it takes it.
            val look = ss.elementStyle(lyricsElement, songTarget, language)
            songLineStyling(
                profile = look,
                autoFitFontSize = languageFitSizes[language]?.takeIf { look.autoFit },
                scaleFactor = scaleFactor,
                isKey = isKey,
                shadowOf = this::scaleElementShadow,
            )
        } else if (!ss.languageOverridesStyle(language)) primaryLyricStyling
        else songLineStyling(
            profile = ss.elementStyle(lyricsElement, songTarget, language),
            autoFitFontSize = if (autoFitEnabled) autoFitFontSize else null,
            scaleFactor = scaleFactor,
            isKey = isKey,
            shadowOf = this::scaleElementShadow,
        )
    }
    val languageLaStyling = List(MAX_SONG_TRANSLATIONS) { language ->
        if (languageFitSizes.isNotEmpty()) {
            songLineStyling(
                profile = ss.elementStyle(SongStyleElement.NEXT_SECTION, songTarget, language),
                autoFitFontSize = languageFitSizes[language]?.takeIf { laAutoFitEnabled },
                scaleFactor = scaleFactor,
                isKey = isKey,
                shadowOf = this::scaleElementShadow,
            )
        } else if (!ss.languageOverridesStyle(language)) primaryLaStyling
        else songLineStyling(
            profile = ss.elementStyle(SongStyleElement.NEXT_SECTION, songTarget, language),
            autoFitFontSize = if (laAutoFitEnabled) autoFitFontSize else null,
            scaleFactor = scaleFactor,
            isKey = isKey,
            shadowOf = this::scaleElementShadow,
        )
    }
    // Something held directly under the lyrics takes the marker's place there, and the
    // marker follows it -- otherwise the marker's row, kept on every slide, would sit
    // between the lyrics and what was meant to touch them.
    val heldBelowLyrics = (sectionLabelPosition == Constants.BELOW_LYRICS &&
        sectionLabelText(section, ss.layoutExtras.sectionLabel, isTitleSlide) != null) ||
        (titleConfigured && effectiveTitlePosition == Constants.BELOW_LYRICS) ||
        (numberConfigured && !numberInCorner && effectiveSongNumberPosition == Constants.BELOW_LYRICS)
    // Renders title and/or song number for a given position (ABOVE_VERSE or BELOW_VERSE)
    val samePosition = effectiveTitlePosition == effectiveSongNumberPosition
    val sameHorizontal =
        (if (isLowerThird) ss.songNumberLowerThirdHorizontalAlignment else ss.songNumberHorizontalAlignment) ==
            (if (isLowerThird) ss.titleLowerThirdHorizontalAlignment else ss.titleHorizontalAlignment)
    val numberBeforeTitle = ss.songNumberBeforeTitle
    // Determine which positions have content for balancing
    val hasBottomContent = (titleConfigured && effectiveTitlePosition == Constants.BELOW_VERSE) ||
            (numberConfigured && !numberInCorner &&
                    effectiveSongNumberPosition == Constants.BELOW_VERSE) ||
            sectionLabelPosition == Constants.BELOW_VERSE ||
            (lookAheadEnabled && nextSectionPosition == Constants.BELOW_VERSE)
}

/** Only animate the text content — background is never inside this block. */
@Composable
internal fun SongFrame.TextContent(section: LyricSection, lineIndex: Int, innerModifier: Modifier) {
    val slide = SongSlide(this, section, lineIndex, innerModifier)
    with(slide) {
        // The title slide has no verse to fit, no look-ahead and no chart -- it is a
        // heading and its credits, each drawn in its own element's profile -- so it is
        // drawn by its own composable, in the same box the lyrics would have had.
        if (isTitleSlide) {
            SongTitleSlideContent(
                section = section,
                settings = ss,
                target = songTarget,
                languages = activeLanguages,
                isKey = isKey,
                scaleFactor = scaleFactor,
                contentAlignment = if (isLowerThird) {
                    Alignment.BottomCenter
                } else {
                    when (ss.titleSlideVerticalAlignment) {
                        Constants.TOP -> Alignment.TopCenter
                        Constants.BOTTOM -> Alignment.BottomCenter
                        else -> Alignment.Center
                    }
                },
                modifier = innerModifier,
            )
            return
        }
        BoxWithConstraints(
            modifier = innerModifier,
            contentAlignment = if (isLowerThird) Alignment.BottomCenter else contentAlignment
        ) {
            // Two blocks per language, because two things divide the lines and each division
            // wants its own box. A lyric line and a look-ahead line are drawn by one
            // composable but styled by two profiles; and each language is its own block of
            // text, so they get a box each rather than one box drawn around all of them.
            // Every container goes on the same column below -- each paints only the lines
            // that reported to it, and a block nobody reported to draws nothing.
            //
            // Always [MAX_SONG_TRANSLATIONS] of each, never `languageBlocks.size`: these are
            // `remember`ed, and a list whose length changes with the song would shift every
            // later block's slot in the composition and hand a language the box that had
            // been painting another one's lines.
            val lyricsBlocks = List(MAX_SONG_TRANSLATIONS) {
                rememberTextBlockBackdrop(languageLyricStyling[it].profile.backdrop)
            }
            val laBlocks = List(MAX_SONG_TRANSLATIONS) {
                rememberTextBlockBackdrop(languageLaStyling[it].profile.backdrop)
            }
            slide.lyricsBlocks = lyricsBlocks
            slide.laBlocks = laBlocks
            SongSlideLayout()
        }
    }
}

/**
 * The slide's boxed elements, drawn over it at [alpha] -- each in its own box, at the
 * size that fits it. Nothing at all while the song has no box turned on.
 */
@Composable
internal fun SongFrame.SlideBoxes(section: LyricSection, lineIndex: Int, alpha: Float) {
    if (ss.layoutExtras.textBoxes.values.none { it.enabled }) return
    val isTitleSlide = section.type == Constants.SECTION_TYPE_TITLE_SLIDE
    val displayMode = if (lookAheadEnabled) {
        if (isLowerThird) ss.lowerThirdLookAheadDisplayMode else ss.lookAheadDisplayMode
    } else {
        if (isLowerThird) ss.lowerThirdDisplayMode else ss.fullscreenDisplayMode
    }
    val isLineMode = displayMode == Constants.SONG_DISPLAY_MODE_LINE
    val modes = SongSlideModes(
        lookAheadEnabled = lookAheadEnabled,
        isLineMode = isLineMode,
        laIsLineMode = isLineMode,
        lineIndex = if (isLineMode && lineIndex < 0) 0 else lineIndex,
    )
    fun nextOf(index: Int): LyricSection? = if (lookAheadEnabled && index >= 0) {
        allLyricSections.getOrNull(index + 1)?.takeIf { it.lines.isNotEmpty() }
    } else {
        null
    }
    val blocks = songLanguageBlocks(section, nextOf(displaySectionIndex), activeLanguages, modes)
    // Fitted as a whole song, every slide it has counts toward one size per box.
    val songBlocks = if (fitEachSlide) {
        listOf(blocks)
    } else {
        allLyricSections.flatMapIndexed { index, other ->
            val slides = if (isLineMode) other.lines.indices.toList().ifEmpty { listOf(0) } else listOf(-1)
            slides.map { line ->
                songLanguageBlocks(other, nextOf(index), activeLanguages, modes.copy(lineIndex = line))
            }
        }
    }
    val titleDisplay = if (isLowerThird) ss.titleLowerThirdDisplay else ss.titleDisplay
    val numberDisplay = if (isLowerThird) ss.showNumberLowerThird else ss.showNumber
    val titleText = section.allLanguageTitles()
        .getOrNull(blocks.firstOrNull()?.index ?: 0)
        ?.takeIf { it.isNotEmpty() }
        ?: section.title
    val items = if (isTitleSlide) {
        titleSlideBoxItems(ss, isLowerThird, titleSlideLines(section, ss, activeLanguages))
    } else {
        songBoxItems(
            settings = ss,
            lowerThird = isLowerThird,
            lyricsElement = if (lookAheadEnabled) SongStyleElement.LOOK_AHEAD else SongStyleElement.LYRICS,
            slide = SongBoxSlide(
                blocks = blocks,
                songBlocks = songBlocks,
                texts = SongBoxTexts(
                title = titleText.takeIf {
                    shouldShowText(titleDisplay, section, allLyricSections, displaySectionIndex)
                },
                number = section.songNumber.takeIf {
                    it > 0 && shouldShowText(numberDisplay, section, allLyricSections, displaySectionIndex)
                }?.toString(),
                    label = sectionLabelText(section, ss.layoutExtras.sectionLabel, isTitleSlide = false),
                ),
            ),
        )
    }
    val bandHeight = outputHeight.value * ss.lowerThirdHeightPercent / 100f
    SongBoxLayer(
        items = items,
        settings = ss,
        target = songTarget,
        area = textBoxArea(
            outputWidth = outputWidth.value,
            outputHeight = outputHeight.value,
            options = ss.layoutExtras.textBoxOptions,
            margins = BoxMargins(leftOffSet.value, topOffSet.value, rightOffSet.value, bottomOffSet.value),
            band = if (isLowerThird) {
                Rect(0f, outputHeight.value - bandHeight, outputWidth.value, outputHeight.value)
            } else {
                null
            },
        ),
        // The layer fills the padded box on a full screen, and the whole output on a band.
        origin = if (isLowerThird) Offset.Zero else Offset(leftOffSet.value, topOffSet.value),
        scaleFactor = scaleFactor,
        alpha = alpha,
        isKey = isKey,
        outlineOf = this::keyedOutline,
        shadowOf = this::scaleElementShadow,
    )
}
