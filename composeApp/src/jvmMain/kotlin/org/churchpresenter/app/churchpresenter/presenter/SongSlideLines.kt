package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.composables.OutlinedText
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongStyleElement
import org.churchpresenter.app.churchpresenter.utils.spacingEm
import org.churchpresenter.app.churchpresenter.utils.styledDisplayText
import org.churchpresenter.settings.utils.Constants

/* A song slide's lyric and next-section lines, drawn per language. */

/**
 * [language] says which language this line belongs to, and so which styling
 * draws it and which backdrop block it reports to. Every language is drawn by
 * this one composable with the same `lineIdx`, so sharing a block would have
 * each overwrite the last line for line -- and would frame all of them as one
 * block of text, which they are not.
 */
@Composable
internal fun SongSlide.LyricLine(lineIdx: Int, line: String, laStart: Int, language: Int = 0) {
    val isLookAheadLine = laStart >= 0 && lineIdx >= laStart
    val styling =
        if (isLookAheadLine) languageLaStyling[language] else languageLyricStyling[language]
    val lineProfile = styling.profile
    // The next-section lines take their own alignment once one is set; blank
    // keeps them following the look-ahead's, which is what they always did.
    val lineAlign = if (isLookAheadLine && lineProfile.horizontalAlignment.isNotBlank()) {
        getTextAlign(lineProfile.horizontalAlignment)
    } else {
        lyricsHorizontalAlignment
    }
    val lineBlock = if (isLookAheadLine) laBlocks[language] else lyricsBlocks[language]
    OutlinedText(
        modifier = Modifier.fillMaxWidth().then(lineBlock.lineModifier(lineIdx)),
        outline = keyedOutline(lineProfile.outline),
        scaleFactor = scaleFactor,
        textAlign = lineAlign,
        fontFamily = styling.fontFamily,
        fontSize = styling.fontSize,
        softWrap = appSettings.songSettings.wordWrap,
        text = styledDisplayText(
            line,
            lineProfile.transform,
            spacingEm(lineProfile.letterSpacing, lineProfile.fontSize),
            spacingEm(lineProfile.wordSpacing, lineProfile.fontSize),
        ),
        color = styling.color,
        style = styling.textStyle,
        onTextLayout = { lineBlock.onTextLayout(lineIdx, it) },
    )
}

@Composable
internal fun SongSlide.LookAheadSpacer(idx: Int, laStart: Int) {
    if (laStart >= 0 && idx == laStart && !laIsLineMode) {
        Spacer(modifier = Modifier.padding(top = (12 * scaleFactor).dp))
    }
}

/** The marker, in each language's block -- or, with [afterHeld], once under what is held below. */
@Composable
internal fun SongSlide.EndOfSongIndicator(afterHeld: Boolean = false) {
    if (!ss.showEndOfSongIndicator || heldBelowLyrics != afterHeld) return
    // Always reserve space so lyrics don't shift when the indicator appears on the last section
    val visible = section.isLastSection && (!isLineMode || effectiveLineIndex >= allDisplayLines.size - 1)
    val indicatorAlpha = if (visible) 1f else 0f
    Spacer(modifier = Modifier.padding(top = (4 * scaleFactor).dp))
    val indicatorPad = " ".repeat(ss.endOfSongIndicatorSpacing)
    val indicatorText = "$indicatorPad*$indicatorPad"
    Row(modifier = Modifier.fillMaxWidth().alpha(indicatorAlpha), horizontalArrangement = Arrangement.Center) {
        repeat(SONG_INDICATOR_REPEAT_COUNT) {
            OutlinedText(
                text = AnnotatedString(indicatorText),
                outline = keyedOutline(lyricsStyleProfile.outline),
                scaleFactor = scaleFactor,
                fillWidth = false,
                fontSize = scaledLyricsFontSize,
                color = lyricsColor,
                style = lyricsTextStyleScaled,
            )
        }
    }
}

// Invisible placeholder to reserve space for missing lookahead on last section,
// [gapAfter] when it stands above what it is spaced from rather than below.
@Composable
internal fun SongSlide.NextSectionPlaceholder(block: SongLanguageBlock, gapAfter: Boolean) {
    val holdsNext = lookAheadEnabled && block.lookAheadLines.isEmpty() && block.lines.isNotEmpty()
    if (holdsNext && !ss.isBoxed(SongStyleElement.NEXT_SECTION, isLowerThird, block.index)) {
        if (!laIsLineMode && !gapAfter) {
            Spacer(modifier = Modifier.padding(top = (12 * scaleFactor).dp))
        }
        val placeholderStyling = languageLaStyling[block.index]
        Column(modifier = Modifier.alpha(0f)) {
            block.lines.forEach { line ->
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = lyricsHorizontalAlignment,
                    fontFamily = placeholderStyling.fontFamily,
                    fontSize = placeholderStyling.fontSize,
                    softWrap = appSettings.songSettings.wordWrap,
                    text = line,
                    color = placeholderStyling.color,
                    style = placeholderStyling.textStyle,
                )
            }
        }
        if (!laIsLineMode && gapAfter) {
            Spacer(modifier = Modifier.padding(top = (12 * scaleFactor).dp))
        }
    }
}

// The placeholder where the next section is drawn under the lyrics, as it always
// was. Held above them, [LanguageLines] draws its own; at an edge, the edge row does.
@Composable
internal fun SongSlide.LookAheadPlaceholder(block: SongLanguageBlock) {
    if (nextSectionPosition == Constants.BELOW_LYRICS) {
        NextSectionPlaceholder(block, gapAfter = false)
    }
}

/** The gap between lyrics and their next section, in this output's own pixels. */
@Composable
internal fun SongSlide.NextSectionGap() {
    if (!laIsLineMode) Spacer(modifier = Modifier.padding(top = (12 * scaleFactor).dp))
}

/**
 * One language's next-section lines, spaced from the lyrics on the side they
 * face: before them when drawn below the lyrics, after them when above.
 */
@Composable
internal fun SongSlide.NextSectionOf(block: SongLanguageBlock, gapAfter: Boolean) {
    if (block.index == 0 && mainChartRows.isNotEmpty()) {
        if (laChartRows.isEmpty()) return
        if (!gapAfter) NextSectionGap()
        SectionChordChart(
            lines = laChartRows,
            color = laColor,
            chordColor = chordColor,
            horizontalAlignment = chartHorizontalAlignment,
            maxFontSize = effectiveLaFontSize,
            scaleFactor = scaleFactor,
            fontFamily = laFontFamily,
            textStyle = lookAheadTextStyle,
        )
        if (gapAfter) NextSectionGap()
        return
    }
    val laStart = block.lookAheadStart
    if (laStart < 0) {
        NextSectionPlaceholder(block, gapAfter)
        return
    }
    val next = SongStyleElement.NEXT_SECTION
    Column(Modifier.fillMaxWidth()) {
        SongElementLines(ss, next, isLowerThird, block.index, scaleFactor) {
            if (!gapAfter) LookAheadSpacer(laStart, laStart)
            block.allLines.drop(laStart).forEachIndexed { offset, line ->
                LyricLine(laStart + offset, line, laStart, block.index)
            }
            if (gapAfter) LookAheadSpacer(laStart, laStart)
        }
    }
}

/**
 * One language's lines — as a chord chart where this output draws one, and as
 * plain lines everywhere else -- with its next-section lines above or below them
 * where they are held on the lyrics. At an edge the edge row draws those.
 *
 * The chart is the primary's alone: chords are written against the primary's
 * words, and a chart drawn over a translation would put them over syllables
 * they do not belong to.
 */
@Composable
internal fun SongSlide.LanguageLines(block: SongLanguageBlock) {
    // The lyric lines and the look-ahead lines are two elements, each moved on
    // its own (Move X / Y, or a drag on the Profiles preview) and each in this
    // language's own block where it has one.
    Column(Modifier.fillMaxWidth()) {
        if (lookAheadEnabled && nextSectionPosition == Constants.ABOVE_LYRICS) {
            NextSectionOf(block, gapAfter = true)
        }
        if (block.index != 0 || mainChartRows.isEmpty()) {
            val laStart = block.lookAheadStart
            val lyricCount = if (laStart >= 0) laStart else block.allLines.size
            SongElementLines(ss, lyricsElement, isLowerThird, block.index, scaleFactor) {
                block.allLines.take(lyricCount).forEachIndexed { idx, line ->
                    LyricLine(idx, line, laStart, block.index)
                }
            }
        } else {
            SectionChordChart(
                lines = mainChartRows,
                color = lyricsColor,
                chordColor = chordColor,
                horizontalAlignment = chartHorizontalAlignment,
                maxFontSize = effectiveLyricsFontSize,
                scaleFactor = scaleFactor,
                fontFamily = lyricsFontFamily,
                textStyle = lyricsTextStyleScaled,
            )
        }
        // A block with no next section of its own leaves its placeholder to
        // [LookAheadPlaceholder], after the end-of-song marker.
        val hasNext = block.lookAheadStart >= 0 || block.index == 0 && mainChartRows.isNotEmpty()
        if (nextSectionPosition == Constants.BELOW_LYRICS && hasNext) {
            NextSectionOf(block, gapAfter = false)
        }
    }
}

/** Every language's next-section lines, stacked, where they sit at [position]'s edge. */
@Composable
internal fun SongSlide.NextSectionEdge(position: String) {
    if (!lookAheadEnabled || nextSectionPosition != position) return
    Column(Modifier.fillMaxWidth()) {
        languageBlocks.forEach { block ->
            NextSectionOf(block, gapAfter = position == Constants.ABOVE_VERSE)
        }
    }
}
