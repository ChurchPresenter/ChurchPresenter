package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.unit.TextUnit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.Color
import org.churchpresenter.app.churchpresenter.dialogs.tabs.BibleStyleElement
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.composables.OutlinedText
import org.churchpresenter.app.churchpresenter.composables.backdropRoom
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.utils.bilingualColumns
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import androidx.compose.foundation.layout.BoxWithConstraintsScope

/*
 * The ways one output lays out its verses: the full-screen stack, the lower third's grid of three
 * or four, its two languages split across the band, and its single stacked column.
 */

/** Full screen: every translation in its own cell of the configured grid, at one shared fit scale. */
@Composable
internal fun BoxWithConstraintsScope.BibleFullScreenStack(
    slide: BibleSlide,
    visible: List<Pair<SelectedVerse, BibleTranslationSettings>>,
) {
    with(slide) {
        // How the stack is arranged, as real rows and columns rather than the
        // side-by-side flag this used to be. The band has read [bilingualGrid] since
        // it gained 3- and 4-translation support; the full screen reduced the same
        // seven-option setting to a boolean, so every grid an operator picked here --
        // 2x2, 4x1, 1x4 -- drew as a plain stack and the control promised what this
        // path could not do.
        //
        // The two original arrangements keep their exact meaning: side by side is one
        // row however many translations there are, and top/bottom one column. A named
        // grid means what it says.
        val slots = visible.size.coerceAtLeast(1)
        val gridCols = bilingualColumns(bs.bilingualLayout, slots)
        val gridRows = ((slots + gridCols - 1) / gridCols).coerceAtLeast(1)
        // The spacing between two translations plus the divider's own line, along one
        // axis. It scales with the fit, so every scale the search probes has to
        // recompute it -- and it is per axis now, because a grid has gaps on both.
        fun gapsPx(count: Int, scale: Float): Int {
            val gapCount = (count - 1).coerceAtLeast(0)
            val gap = with(density) { (bs.multiTranslationSpacing * scale).dp.roundToPx() }
            val divider = if (bs.multiTranslationDivider) {
                with(density) { 1.dp.roundToPx() }
            } else {
                0
            }
            return gapCount * (gap + divider)
        }
        // One cell of the grid. The measurement below has to agree with what is drawn,
        // or the fit search solves for a box the text is not laid out in.
        fun itemWidth(scale: Float): Int =
            ((constraints.maxWidth - gapsPx(gridCols, scale)) / gridCols).coerceAtLeast(1)
        fun textHeight(verse: SelectedVerse, item: BibleTranslationSettings, scale: Float): Int {
            val textSize = (item.textFontSize * scaleFactor * scale).sp
            val textFont = systemFontFamilyOrDefault(item.textFontType)
            return textMeasurer.measure(
                itemText(item, verse.verseText),
                textStyle(item).copy(fontFamily = textFont, fontSize = textSize),
                constraints = Constraints(maxWidth = itemWidth(scale)),
            ).size.height
        }
        fun refHeight(verse: SelectedVerse, item: BibleTranslationSettings, scale: Float): Int {
            val refSize = (item.referenceFontSize * scaleFactor * scale).sp
            val refFont = systemFontFamilyOrDefault(item.referenceFontType)
            return textMeasurer.measure(
                itemRefText(item, buildRefText(verse, item)),
                referenceStyle(item).copy(fontFamily = refFont, fontSize = refSize),
                constraints = Constraints(maxWidth = itemWidth(scale)),
            ).size.height
        }
        // Only the halves still in the column add up: a boxed one is fitted in its box.
        fun stackedHeight(verse: SelectedVerse, item: BibleTranslationSettings, scale: Float): Int {
            val textH = if (bs.isBoxed(item, BibleStyleElement.TEXT, isLowerThird)) {
                0
            } else {
                textHeight(verse, item, scale)
            }
            val refH = if (bs.isBoxed(item, BibleStyleElement.REFERENCE, isLowerThird)) {
                0
            } else {
                refHeight(verse, item, scale)
            }
            return textH + refH
        }
        // What one translation has to fit in: the frame less this axis's gaps, split
        // over the rows. One row and it is the whole height, which is what side by
        // side has always given each column.
        fun bandHeight(scale: Float): Int =
            ((constraints.maxHeight - gapsPx(gridRows, scale)) / gridRows).coerceAtLeast(1)
        // One scale for the whole stack, so every translation reads at the same size,
        // and no floor: a full stack of six shrinks until the whole of every one of them
        // is inside its band. Everything measured here scales with the argument bar the
        // 1dp dividers, so a fitting scale always exists to be found.
        //
        // Per band rather than against the total, now that each has its own: a long
        // verse can no longer borrow the slack of a short one beside it and push its
        // own band's text out through the clip.
        fun everyBlockFits(scale: Float): Boolean {
            val band = bandHeight(scale)
            return visible.all { (verse, item) -> stackedHeight(verse, item, scale) <= band }
        }
        // No full-size gate in front of the search: its own opening probe is that same
        // measurement and returns 1f when it fits, so gating here measured every
        // translation twice over.
        val fitScale = binarySearchFitScale(iterations = 10) { scale -> everyBlockFits(scale) }
        val dividerColor = if (isKey) Color.White else Color.White.copy(alpha = BIBLE_DIVIDER_ALPHA)
        // Half the spacing either side of the divider, so the rule sits on the centre line of
        // the gap whether or not it is drawn -- as it did when this was one Column.
        val halfGap = (bs.multiTranslationSpacing * fitScale / 2f).dp
        // One grid covers all seven arrangements: side by side is a single row, top
        // and bottom a single column, and the rest are what they are named. A row
        // short of a full one pads with weighted spacers so its cells keep the
        // column width the rows above them set.
        val rowsOfVisible = visible.chunked(gridCols)
        Column(modifier = Modifier.fillMaxSize()) {
            rowsOfVisible.forEachIndexed { rowIndex, rowItems ->
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Every column position is laid out, filled or not, and the
                    // separators between them are laid out either way -- so a row
                    // that does not fill the grid still lines its cells up with the
                    // rows above. Padding only the empty cells left a short row
                    // one divider lighter and therefore fractionally wider, which
                    // put six translations in a four-column grid out of true.
                    for (colIndex in 0 until gridCols) {
                        val cell = rowItems.getOrNull(colIndex)
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()
                                // The cell clips what is moved out of it; a preview
                                // keeps a dragged reference inside it.
                                .then(
                                    cell?.let {
                                        val kind = PresentedBlock.Kind.CELL
                                        Modifier.reportsBlock(PresentedBlock(kind, it.second.fileName))
                                    } ?: Modifier,
                                ),
                            contentAlignment = contentAlignment,
                        ) {
                            if (cell != null) {
                                Box(
                                    Modifier.translationShift(cell.second, isLowerThird, scaleFactor)
                                        .reportsBlock(
                                            PresentedBlock(PresentedBlock.Kind.TRANSLATION, cell.second.fileName),
                                        ),
                                ) {
                                    TranslationBlock(cell.first, cell.second, fitScale)
                                }
                            }
                        }
                        if (colIndex < gridCols - 1) {
                            Spacer(modifier = Modifier.width(halfGap))
                            // Drawn only between two translations: a rule running
                            // down the empty half of a short row divides nothing.
                            val between = cell != null && rowItems.getOrNull(colIndex + 1) != null
                            if (bs.multiTranslationDivider && between) {
                                VerticalDivider(color = dividerColor, thickness = 1.dp)
                            } else if (bs.multiTranslationDivider) {
                                Spacer(modifier = Modifier.width(1.dp))
                            }
                            Spacer(modifier = Modifier.width(halfGap))
                        }
                    }
                }
                if (rowIndex < rowsOfVisible.lastIndex) {
                    Spacer(modifier = Modifier.height(halfGap))
                    if (bs.multiTranslationDivider) {
                        HorizontalDivider(color = dividerColor, thickness = 1.dp)
                    }
                    Spacer(modifier = Modifier.height(halfGap))
                }
            }
        }
    }
}

/** The lower third's grid of three or four translations, fitted to this band's own box. */
@Composable
internal fun BoxWithConstraintsScope.BibleLowerThirdGrid(slide: BibleSlide) {
    with(slide) {
        val cols = lowerThirdGridCols.coerceAtLeast(1)
        val rows = ((lowerThirdMultiVisible.size + cols - 1) / cols).coerceAtLeast(1)
        fun gapPx(scale: Float) = with(density) { (BIBLE_LOWER_THIRD_GRID_GAP_DP * scale).dp.roundToPx() }
        fun cellWidth(scale: Float): Int =
            ((constraints.maxWidth - (cols - 1) * gapPx(scale)) / cols).coerceAtLeast(1)
        fun cellHeight(scale: Float): Int =
            ((constraints.maxHeight - (rows - 1) * gapPx(scale)) / rows).coerceAtLeast(1)
        fun cellTextHeight(verse: SelectedVerse, item: BibleTranslationSettings, scale: Float): Int {
            val textSize = (item.textFontSize * scaleFactor * scale).sp
            val textFont = systemFontFamilyOrDefault(item.textFontType)
            return textMeasurer.measure(
                itemText(item, verse.verseText),
                textStyle(item).copy(fontFamily = textFont, fontSize = textSize),
                constraints = Constraints(maxWidth = cellWidth(scale)),
            ).size.height
        }
        fun cellRefHeight(verse: SelectedVerse, item: BibleTranslationSettings, scale: Float): Int {
            val refSize = (item.referenceFontSize * scaleFactor * scale).sp
            val refFont = systemFontFamilyOrDefault(item.referenceFontType)
            return textMeasurer.measure(
                itemRefText(item, buildRefText(verse, item)),
                referenceStyle(item).copy(fontFamily = refFont, fontSize = refSize),
                constraints = Constraints(maxWidth = cellWidth(scale)),
            ).size.height
        }
        // Same split as the full-screen grid above, against the cell rather than the
        // band: what is stacked adds up, and a boxed half is fitted in its box.
        fun everyBlockFits(scale: Float): Boolean {
            val cell = cellHeight(scale)
            return lowerThirdMultiVisible.all { (verse, item) ->
                val textH = if (bs.isBoxed(item, BibleStyleElement.TEXT, isLowerThird)) {
                    0
                } else {
                    cellTextHeight(verse, item, scale)
                }
                val refH = if (bs.isBoxed(item, BibleStyleElement.REFERENCE, isLowerThird)) {
                    0
                } else {
                    cellRefHeight(verse, item, scale)
                }
                textH + refH <= cell
            }
        }
        val fitScale = binarySearchFitScale(iterations = 10) { scale -> everyBlockFits(scale) }
        val gap = with(density) { gapPx(fitScale).toDp() }
        Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
            lowerThirdMultiVisible.chunked(cols).forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) Spacer(modifier = Modifier.height(gap))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    // Every column position, filled or not, so a row short of a
                    // full one keeps the column width the rows above it set
                    // instead of stretching its cells across the band.
                    for (colIndex in 0 until cols) {
                        val cell = row.getOrNull(colIndex)
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            if (cell != null) {
                                Box(
                                    Modifier.translationShift(cell.second, isLowerThird, scaleFactor)
                                        .reportsBlock(
                                            PresentedBlock(PresentedBlock.Kind.TRANSLATION, cell.second.fileName),
                                        ),
                                ) {
                                    TranslationBlock(cell.first, cell.second, fitScale)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The lower third's two languages side by side, matched to one size. */
@Composable
internal fun BoxWithConstraintsScope.BibleBandSplit(slide: BibleSlide, sec: SelectedVerse) {
    val fit = slide.bandSplitFit(constraints, sec)
    with(slide) {
        val matchedScale = fit.scale
        val pTextBoxed = fit.primaryTextBoxed
        val pRefBoxed = fit.primaryRefBoxed
        val sTextBoxed = fit.secondaryTextBoxed
        val sRefBoxed = fit.secondaryRefBoxed
        val primaryRefText = buildRefText(primary, t0)
        val secondaryRefText = buildRefText(sec, t1)
        val pBibleSize = scaledPrimaryBibleSize * matchedScale
        val sBibleSize = scaledSecondaryBibleSize * matchedScale
        // Use the smaller of the two so both sides display at the same visual size
        val matchedBibleSize = if (sBibleSize.value < pBibleSize.value) sBibleSize else pBibleSize
        val scaledPrimaryRefSize = scaledPrimaryReferenceSize * matchedScale
        val scaledSecondaryRefSize = scaledSecondaryReferenceSize * matchedScale

        // Each element through one lambda, drawn either filling its half or sized to
        // itself -- see [BandElement], which is also what decides stacked from
        // floated. The reference's above/below setting becomes the order of the list.
        val pRef = BandElement(pRefBoxed) { fill ->
            OutlinedText(
                fillWidth = fill,
                modifier = (if (fill) Modifier.fillMaxWidth() else Modifier)
                    .backdropRoom(t0.referenceBackdropFor(isLowerThird))
                    .then(pRefPainter.modifier),
                outline = t0.referenceOutlineFor(isLowerThird),
                scaleFactor = scaleFactor,
                textAlign = primaryBibleReferenceHorizontalAlignment,
                fontFamily = primaryBibleReferenceFontStyle,
                fontSize = scaledPrimaryRefSize,
                text = prText(primaryRefText),
                color = primaryBibleReferenceTextColor,
                style = primaryReferenceTextStyleScaled,
                onTextLayout = pRefPainter::onTextLayout,
            )
        }
        val pVerse = BandElement(pTextBoxed) { fill ->
            OutlinedText(
                fillWidth = fill,
                modifier = (if (fill) Modifier.fillMaxWidth() else Modifier)
                    .backdropRoom(t0.textBackdropFor(isLowerThird))
                    .then(pTextPainter.modifier),
                outline = t0.textOutlineFor(isLowerThird),
                scaleFactor = scaleFactor,
                textAlign = primaryBibleHorizontalAlignment,
                fontFamily = primaryBibleFontStyle,
                fontSize = matchedBibleSize,
                text = pText(primary.verseText),
                color = primaryBibleTextColor,
                style = primaryBibleTextStyleScaled,
                onTextLayout = pTextPainter::onTextLayout,
            )
        }
        val sRef = BandElement(sRefBoxed) { fill ->
            OutlinedText(
                fillWidth = fill,
                modifier = (if (fill) Modifier.fillMaxWidth() else Modifier)
                    .backdropRoom(t1.referenceBackdropFor(isLowerThird))
                    .then(sRefPainter.modifier),
                outline = t1.referenceOutlineFor(isLowerThird),
                scaleFactor = scaleFactor,
                textAlign = secondaryBibleReferenceHorizontalAlignment,
                fontFamily = secondaryBibleReferenceFontStyle,
                fontSize = scaledSecondaryRefSize,
                text = srText(secondaryRefText),
                color = secondaryBibleReferenceTextColor,
                style = secondaryReferenceTextStyleScaled,
                onTextLayout = sRefPainter::onTextLayout,
            )
        }
        val sVerse = BandElement(sTextBoxed) { fill ->
            OutlinedText(
                fillWidth = fill,
                modifier = (if (fill) Modifier.fillMaxWidth() else Modifier)
                    .backdropRoom(t1.textBackdropFor(isLowerThird))
                    .then(sTextPainter.modifier),
                outline = t1.textOutlineFor(isLowerThird),
                scaleFactor = scaleFactor,
                textAlign = secondaryBibleHorizontalAlignment,
                fontFamily = secondaryBibleFontStyle,
                fontSize = matchedBibleSize,
                text = sText(sec.verseText),
                color = secondaryBibleTextColor,
                style = secondaryBibleTextStyleScaled,
                onTextLayout = sTextPainter::onTextLayout,
            )
        }
        BandSplitRow(pRef, pVerse, sRef, sVerse)
    }
}

/** The fit this band is drawn at: one scale for everything in it, and which halves are boxed. */
private fun BibleSlide.bandSplitFit(constraints: Constraints, sec: SelectedVerse): BandFit {
    // Pre-compute fit scales for both halves, then use the min so fonts match
    val halfWidth = (constraints.maxWidth - with(density) { 16.dp.roundToPx() }) / 2
    val halfConstraint = Constraints(maxWidth = halfWidth.coerceAtLeast(1))
    // Use 90% of available height as safety margin for line spacing/shadow/padding offsets
    val availH = (constraints.maxHeight * 0.90f).toInt()
    // Which of the four halves are boxed and so have left their column. Read once:
    // the fit search below and the layout after it have to agree about this.
    val pTextBoxed = bs.isBoxed(t0, BibleStyleElement.TEXT, isLowerThird)
    val pRefBoxed = bs.isBoxed(t0, BibleStyleElement.REFERENCE, isLowerThird)
    val sTextBoxed = bs.isBoxed(t1, BibleStyleElement.TEXT, isLowerThird)
    val sRefBoxed = bs.isBoxed(t1, BibleStyleElement.REFERENCE, isLowerThird)

    val primaryRefText = buildRefText(primary, t0)
    val secondaryRefText = buildRefText(sec, t1)

    // Binary search for the largest scale where both primary and secondary fit
    // Scale both verse AND reference text together so everything shrinks proportionally
    val initialPRefH = textMeasurer.measure(
        prText(primaryRefText),
        primaryReferenceTextStyle.copy(
            fontFamily = primaryBibleReferenceFontStyle,
            fontSize = scaledPrimaryReferenceSize,
        ),
        constraints = halfConstraint,
    ).size.height
    val initialSRefH = textMeasurer.measure(
        srText(secondaryRefText),
        secondaryReferenceTextStyle.copy(
            fontFamily = secondaryBibleReferenceFontStyle,
            fontSize = scaledSecondaryReferenceSize,
        ),
        constraints = halfConstraint,
    ).size.height
    val initialPH = textMeasurer.measure(
        pText(primary.verseText),
        primaryBibleTextStyle.copy(
            fontFamily = primaryBibleFontStyle,
            fontSize = scaledPrimaryBibleSize,
        ),
        constraints = halfConstraint,
    ).size.height
    val initialSH = textMeasurer.measure(
        sText(sec.verseText),
        secondaryBibleTextStyle.copy(
            fontFamily = secondaryBibleFontStyle,
            fontSize = scaledSecondaryBibleSize,
        ),
        constraints = halfConstraint,
    ).size.height
    // Only the halves still stacked add up; a boxed one is fitted in its box.
    fun halfFits(refH: Int, textH: Int, refBoxed: Boolean, textBoxed: Boolean): Boolean =
        (if (refBoxed) 0 else refH) + (if (textBoxed) 0 else textH) <= availH
    val needsScaling = !halfFits(initialPRefH, initialPH, pRefBoxed, pTextBoxed) ||
        !halfFits(initialSRefH, initialSH, sRefBoxed, sTextBoxed)

    val matchedScale = if (needsScaling) {
        binarySearchFitScale { scale ->
            val pRefH = textMeasurer.measure(
                prText(primaryRefText),
                primaryReferenceTextStyle.copy(
                    fontFamily = primaryBibleReferenceFontStyle,
                    fontSize = scaledPrimaryReferenceSize * scale,
                ),
                constraints = halfConstraint,
            ).size.height
            val sRefH = textMeasurer.measure(
                srText(secondaryRefText),
                secondaryReferenceTextStyle.copy(
                    fontFamily = secondaryBibleReferenceFontStyle,
                    fontSize = scaledSecondaryReferenceSize * scale,
                ),
                constraints = halfConstraint,
            ).size.height
            val pH = textMeasurer.measure(
                pText(primary.verseText),
                primaryBibleTextStyle.copy(
                    fontFamily = primaryBibleFontStyle,
                    fontSize = scaledPrimaryBibleSize * scale,
                ),
                constraints = halfConstraint,
            ).size.height
            val sH = textMeasurer.measure(
                sText(sec.verseText),
                secondaryBibleTextStyle.copy(
                    fontFamily = secondaryBibleFontStyle,
                    fontSize = scaledSecondaryBibleSize * scale,
                ),
                constraints = halfConstraint,
            ).size.height
            halfFits(pRefH, pH, pRefBoxed, pTextBoxed) &&
                halfFits(sRefH, sH, sRefBoxed, sTextBoxed)
        }
    } else 1f
    return BandFit(pTextBoxed, pRefBoxed, sTextBoxed, sRefBoxed, matchedScale)
}

/** The lower third's single column: one language, or two stacked, fitted to the band. */
@Composable
internal fun BoxWithConstraintsScope.BibleBandStack(slide: BibleSlide) {
    val fit = slide.bandStackFit(constraints)
    with(slide) {
        val fitScale = fit.scale
        val colPTextBoxed = fit.primaryTextBoxed
        val colPRefBoxed = fit.primaryRefBoxed
        val primaryRefText = buildRefText(primary, t0)
        // Empty when there is no second language.
        val secondaryRefText = secondary?.let { buildRefText(it, t1) } ?: ""
        val fittedPrimaryRefSize = scaledPrimaryReferenceSize * fitScale
        val fittedSecondaryRefSize = scaledSecondaryReferenceSize * fitScale
        val fittedPrimaryBibleSize = scaledPrimaryBibleSize * fitScale
        val fittedSecondaryBibleSize = scaledSecondaryBibleSize * fitScale
        // Use the smaller so both primary and secondary display at the same visual size
        val matchedFittedSize =
            if (showSecondary && fittedSecondaryBibleSize.value < fittedPrimaryBibleSize.value) {
                fittedSecondaryBibleSize
            } else {
                fittedPrimaryBibleSize
            }

        // One lambda per element, as in the pair above: drawn either filling the
        // band's width or sized to itself, so a stacked and a positioned element
        // cannot come apart.
        val colPRef = BandElement(colPRefBoxed) { fill ->
            OutlinedText(
                fillWidth = fill,
                modifier = (if (fill) Modifier.fillMaxWidth() else Modifier)
                    .backdropRoom(t0.referenceBackdropFor(isLowerThird))
                    .then(pRefPainter.modifier),
                outline = t0.referenceOutlineFor(isLowerThird),
                scaleFactor = scaleFactor,
                textAlign = primaryBibleReferenceHorizontalAlignment,
                fontFamily = primaryBibleReferenceFontStyle,
                fontSize = fittedPrimaryRefSize,
                text = prText(primaryRefText),
                color = primaryBibleReferenceTextColor,
                style = primaryReferenceTextStyleScaled,
                onTextLayout = pRefPainter::onTextLayout,
            )
        }
        val colPVerse = BandElement(colPTextBoxed) { fill ->
            OutlinedText(
                fillWidth = fill,
                modifier = (if (fill) Modifier.fillMaxWidth() else Modifier)
                    .backdropRoom(t0.textBackdropFor(isLowerThird))
                    .then(pTextPainter.modifier),
                outline = t0.textOutlineFor(isLowerThird),
                scaleFactor = scaleFactor,
                textAlign = primaryBibleHorizontalAlignment,
                fontFamily = primaryBibleFontStyle,
                fontSize = matchedFittedSize,
                text = pText(primary.verseText),
                color = primaryBibleTextColor,
                style = primaryBibleTextStyleScaled,
                onTextLayout = pTextPainter::onTextLayout,
            )
        }
        val primaryElements = if (primaryBibleReferencePosition == Constants.POSITION_ABOVE) {
            listOf(colPRef, colPVerse)
        } else {
            listOf(colPVerse, colPRef)
        }
        val secondaryElements = if (!showSecondary) {
            emptyList()
        } else {
            bandStackSecondaryElements(fit, fittedSecondaryRefSize, matchedFittedSize, secondaryRefText)

        }
        BibleBandColumn(
            elements = primaryElements + secondaryElements,
            verticalArrangement = if (isLowerThird) Arrangement.Bottom else Arrangement.Top,
        )
    }
}

/** The fit this band is drawn at: one scale for everything in it, and which halves are boxed. */
private fun BibleSlide.bandStackFit(constraints: Constraints): BandFit {
    // Auto-scale bible text if it overflows the available height
    val widthConstraint = Constraints(maxWidth = constraints.maxWidth)
    val primaryRefText = buildRefText(primary, t0)
    // Empty when there is no second language, which is the same as the zero height
    // that branch contributes.
    val secondaryRefText = secondary?.let { buildRefText(it, t1) } ?: ""

    val maxH = constraints.maxHeight
    // Which of this column's elements are boxed and have left it. As in the pair
    // above, read once and used by both the search and the layout.
    val colPTextBoxed = bs.isBoxed(t0, BibleStyleElement.TEXT, isLowerThird)
    val colPRefBoxed = bs.isBoxed(t0, BibleStyleElement.REFERENCE, isLowerThird)
    val colSTextBoxed = showSecondary && bs.isBoxed(t1, BibleStyleElement.TEXT, isLowerThird)
    val colSRefBoxed = showSecondary && bs.isBoxed(t1, BibleStyleElement.REFERENCE, isLowerThird)
    // The stacked elements add up; a boxed one is fitted in its box. A second
    // language that is not shown contributes nothing either way, which is what
    // its zero heights below already said.
    fun columnFits(pRefH: Int, pH: Int, sRefH: Int, sH: Int): Boolean =
        (if (colPRefBoxed) 0 else pRefH) + (if (colPTextBoxed) 0 else pH) +
            (if (colSRefBoxed) 0 else sRefH) + (if (colSTextBoxed) 0 else sH) <= maxH
    // The reference lines scale with the verse rather than staying at full size.
    // Held fixed, they were a floor the search could not get under: a band whose
    // references alone overfill it had no fitting scale to find, so the text ran off
    // the bottom however far the verse shrank. This is also the lower third's path.
    //
    // Handed straight to the search rather than gated on a full-size measurement
    // first: the search's own opening probe is that same measurement and returns 1f
    // when it fits, so a gate here only measured the whole passage twice.
    val fitScale = binarySearchFitScale { scale ->
        val pRefH = textMeasurer.measure(
            prText(primaryRefText),
            primaryReferenceTextStyle.copy(
                fontFamily = primaryBibleReferenceFontStyle,
                fontSize = scaledPrimaryReferenceSize * scale,
            ),
            constraints = widthConstraint,
        ).size.height
        val pH = textMeasurer.measure(
            pText(primary.verseText),
            primaryBibleTextStyle.copy(
                fontFamily = primaryBibleFontStyle,
                fontSize = scaledPrimaryBibleSize * scale,
            ),
            constraints = widthConstraint,
        ).size.height
        val sRefH = if (showSecondary) {
            textMeasurer.measure(
                srText(secondaryRefText),
                secondaryReferenceTextStyle.copy(
                    fontFamily = secondaryBibleReferenceFontStyle,
                    fontSize = scaledSecondaryReferenceSize * scale,
                ),
                constraints = widthConstraint,
            ).size.height
        } else 0
        val sH = if (showSecondary) {
            textMeasurer.measure(
                sText(secondary?.verseText.orEmpty()),
                secondaryBibleTextStyle.copy(
                    fontFamily = secondaryBibleFontStyle,
                    fontSize = scaledSecondaryBibleSize * scale,
                ),
                constraints = widthConstraint,
            ).size.height
        } else 0
        columnFits(pRefH, pH, sRefH, sH)
    }
    return BandFit(colPTextBoxed, colPRefBoxed, colSTextBoxed, colSRefBoxed, fitScale)
}

/** What a lower-third band is fitted to: the scale everything in it shares, and which halves are boxed. */
private class BandFit(
    val primaryTextBoxed: Boolean,
    val primaryRefBoxed: Boolean,
    val secondaryTextBoxed: Boolean,
    val secondaryRefBoxed: Boolean,
    val scale: Float,
)

/** The split band's two halves, primary on the left, each with its reference above or below as set. */
@Composable
private fun BibleSlide.BandSplitRow(pRef: BandElement, pVerse: BandElement, sRef: BandElement, sVerse: BandElement) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left half: primary bible
        BibleBandHalf(
            if (primaryBibleReferencePosition == Constants.POSITION_ABOVE) {
                listOf(pRef, pVerse)
            } else {
                listOf(pVerse, pRef)
            }
        )
        // Right half: secondary bible
        BibleBandHalf(
            if (secondaryBibleReferencePosition == Constants.POSITION_ABOVE) {
                listOf(sRef, sVerse)
            } else {
                listOf(sVerse, sRef)
            }
        )
    }
}

/** The stacked band's second language: its reference and its verse, in the order set for it. */
private fun BibleSlide.bandStackSecondaryElements(
    fit: BandFit,
    fittedSecondaryRefSize: TextUnit,
    matchedFittedSize: TextUnit,
    secondaryRefText: String,
): List<BandElement> {
    val colSTextBoxed = fit.secondaryTextBoxed
    val colSRefBoxed = fit.secondaryRefBoxed
    val colSRef = BandElement(colSRefBoxed) { fill ->
        OutlinedText(
            fillWidth = fill,
            modifier = (if (fill) Modifier.fillMaxWidth() else Modifier)
                .backdropRoom(t1.referenceBackdropFor(isLowerThird))
                .then(sRefPainter.modifier),
            outline = t1.referenceOutlineFor(isLowerThird),
            scaleFactor = scaleFactor,
            textAlign = secondaryBibleReferenceHorizontalAlignment,
            fontFamily = secondaryBibleReferenceFontStyle,
            fontSize = fittedSecondaryRefSize,
            text = srText(secondaryRefText),
            color = secondaryBibleReferenceTextColor,
            style = secondaryReferenceTextStyleScaled,
            onTextLayout = sRefPainter::onTextLayout,
        )
    }
    val colSVerse = BandElement(colSTextBoxed) { fill ->
        OutlinedText(
            fillWidth = fill,
            modifier = (if (fill) Modifier.fillMaxWidth() else Modifier)
                .backdropRoom(t1.textBackdropFor(isLowerThird))
                .then(sTextPainter.modifier),
            outline = t1.textOutlineFor(isLowerThird),
            scaleFactor = scaleFactor,
            textAlign = secondaryBibleHorizontalAlignment,
            fontFamily = secondaryBibleFontStyle,
            fontSize = matchedFittedSize,
            text = sText(secondary?.verseText.orEmpty()),
            color = secondaryBibleTextColor,
            style = secondaryBibleTextStyleScaled,
            onTextLayout = sTextPainter::onTextLayout,
        )
    }
    return if (secondaryBibleReferencePosition == Constants.POSITION_ABOVE) {
        listOf(colSRef, colSVerse)
    } else {
        listOf(colSVerse, colSRef)
    }
}
