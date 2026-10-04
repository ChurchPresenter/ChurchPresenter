package org.churchpresenter.presenter

import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.sideBySideLanguageGap
import org.churchpresenter.settings.stackedLanguageGap
import org.churchpresenter.settings.utils.Constants

/**
 * The slide's layout: the top edge, the lyrics with what is held on them, the bottom edge overlaid
 * on the lyrics, and a number pinned to a corner.
 */
@Composable
internal fun SongSlide.SongSlideLayout() {
    // Outer column fills the content area; title/number at edges, lyrics centered.
    // Every language's two containers go on it -- each paints only the lines that
    // reported to it, so the ones for languages this slide does not draw cost a
    // modifier and nothing else.
    val blockContainers = (lyricsBlocks + laBlocks)
        .fold(Modifier as Modifier) { acc, block -> acc.then(block.containerModifier) }
    val lyricsOffset = if (isLowerThird) null else ss.layoutExtras.lyricsOffset
    // Full screen, several languages each given a band of the height.
    val stackedBands = isMultiLanguage && !useGrid2x2 && !useSideBySide && !isLowerThird
    Column(modifier = Modifier.fillMaxSize().then(blockContainers)) {
        // Top edge: the label, then the title and number, then the next section.
        SectionLabelPart(Constants.ABOVE_VERSE)
        TitleAndNumberRow(Constants.ABOVE_VERSE)
        NextSectionEdge(Constants.ABOVE_VERSE)

        // Lyrics area + bottom title/number overlaid (z-stacked).
        // The bottom title/number floats over the lyrics so it doesn't
        // steal vertical space and cut off lyrics text.
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // Lyrics fill the entire remaining space -- unless they have been
            // positioned, in which case the block wraps its own height and is
            // placed in that space, which is the same idea the three-way vertical
            // alignment expresses, in one-percent steps instead of three stops.
            //
            // Vertical only, and the settings offer only that axis: the blocks
            // below draw `fillMaxWidth()`, so there is no horizontal room to move
            // through. Narrowing and shifting the lyrics sideways is what Content
            // Region already does, against the frame these sit in.
            Box(
                modifier = if (lyricsOffset != null) {
                    Modifier.fillMaxWidth().wrapContentHeight().elementOffset(lyricsOffset)
                } else {
                    Modifier.fillMaxSize()
                },
                contentAlignment = if (isLowerThird) Alignment.BottomCenter else contentAlignment
            ) {
                HeldOnLyrics(fillHeight = stackedBands) {
                    LanguageBlocksLayout()
                }
            }

            // Bottom edge, overlaid at the bottom of the lyrics area: the top
            // edge's order mirrored.
            if (hasBottomContent) {
                Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                    NextSectionEdge(Constants.BELOW_VERSE)
                    TitleAndNumberRow(Constants.BELOW_VERSE)
                    SectionLabelPart(Constants.BELOW_VERSE)
                }
            }
        }
    }

    // The number pinned to a corner, drawn over the slide rather than in the row it
    // would otherwise share with the title: it costs the lyrics no height and does
    // not shift when the title's row grows or is left off a slide.
    if (numberInCorner && shouldShowSongNumber) {
        val numberOffset =
            if (isLowerThird) ss.layoutExtras.numberLowerThirdOffset else ss.layoutExtras.numberOffset
        NumberPart(
            modifier = Modifier.songNumberCornerOffset(songNumberCorner, numberOffset),
            fillWidth = false,
        )
    }
}

/** Every language's lines, laid out as a grid, a row, a stack or a single block. */
@Composable
private fun SongSlide.LanguageBlocksLayout() {
    val stackedGapDp = (ss.layoutExtras.stackedLanguageGap() * scaleFactor).dp
    val sideGap = ss.layoutExtras.sideBySideLanguageGap()
    // No gap is the columns' old `SpaceEvenly`, which equal weights leave with
    // nothing to spread; a gap is spaced between them.
    val sideBySideArrangement = if (sideGap == 0) {
        Arrangement.SpaceEvenly
    } else {
        Arrangement.spacedBy((sideGap * scaleFactor).dp)
    }
    if (isMultiLanguage) {
        MultiLanguageLayout(stackedGapDp, sideBySideArrangement)
    } else {
        // Single language layout
        val onlyBlock = languageBlocks.firstOrNull()
        Column(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            verticalArrangement = if (isLowerThird) Arrangement.Bottom else Arrangement.Top
        ) {
            if (onlyBlock != null) {
                LanguageLines(onlyBlock)
                EndOfSongIndicator()
                LookAheadPlaceholder(onlyBlock)
            }
        }
    }
}

@Composable
private fun SongSlide.MultiLanguageLayout(stackedGapDp: Dp, sideBySideArrangement: Arrangement.Horizontal) {
    if (useGrid2x2) {
        // Two rows of up to two languages each. `chunked(2)` on
        // however many blocks there are: a third or fourth language
        // starts the second row, and a lone third fills it alone
        // rather than waiting on a fourth that was never selected.
        Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
            languageBlocks.chunked(2).forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) {
                    Spacer(modifier = Modifier.padding(top = stackedGapDp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = sideBySideArrangement,
                ) {
                    row.forEach { block ->
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            LanguageLines(block)
                            EndOfSongIndicator()
                            LookAheadPlaceholder(block)
                        }
                    }
                }
            }
        }
    } else if (useSideBySide) {
        // A column each, equally weighted. `SpaceEvenly` and equal
        // weights agree at any count, so three and four languages
        // divide the width the way two always did.
        Row(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            horizontalArrangement = sideBySideArrangement,
        ) {
            languageBlocks.forEach { block ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    LanguageLines(block)
                    EndOfSongIndicator()
                    LookAheadPlaceholder(block)
                }
            }
        }
    } else if (isLowerThird) {
        // Lower third: compact stack, no height splitting -- a band
        // is too short to give each language a share of it.
        Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
            languageBlocks.forEachIndexed { position, block ->
                if (position > 0) {
                    Spacer(modifier = Modifier.padding(top = stackedGapDp))
                }
                LanguageLines(block)
                EndOfSongIndicator()
                LookAheadPlaceholder(block)
            }
        }
    } else {
        // Full screen: a band of the height each, equally weighted.
        val bandAlignment = contentAlignment
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            languageBlocks.forEachIndexed { position, block ->
                if (position > 0) {
                    Spacer(modifier = Modifier.padding(top = stackedGapDp))
                }
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = bandAlignment,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                        LanguageLines(block)
                        EndOfSongIndicator()
                        LookAheadPlaceholder(block)
                    }
                }
            }
        }
    }
}
