package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import org.churchpresenter.app.churchpresenter.dialogs.tabs.BibleStyleElement
import org.churchpresenter.app.churchpresenter.utils.spacingEm
import org.churchpresenter.app.churchpresenter.utils.combinedTextDecoration
import org.churchpresenter.app.churchpresenter.utils.styledDisplayText
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.composables.OutlinedText
import org.churchpresenter.app.churchpresenter.composables.backdropRoom
import org.churchpresenter.app.churchpresenter.composables.rememberTextBackdropPainter
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.utils.bilingualColumns
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault

/**
 * The verses one output draws right now, and how each translation among them is styled. Forwards
 * [BibleFrame]'s values under their own names, so the layouts drawing them -- extensions of this
 * class -- read them as they did when they were local to one function.
 */
internal class BibleSlide(val frame: BibleFrame, val verses: List<SelectedVerse>) {
    val isKey get() = frame.isKey
    val bs get() = frame.bs
    val translationStack get() = frame.translationStack
    val assignedFileNames get() = frame.assignedFileNames
    val effectiveVerses get() = frame.effectiveVerses
    val t0 get() = frame.t0
    val t1 get() = frame.t1
    val t2 get() = frame.t2
    val t3 get() = frame.t3
    val bgConfig get() = frame.bgConfig
    val selectedVerses get() = frame.selectedVerses
    val appSettings get() = frame.appSettings
    val isLowerThird get() = frame.isLowerThird
    val isLowerThirdVertical get() = frame.isLowerThirdVertical
    val transitionAlpha get() = frame.transitionAlpha
    val showBackground get() = frame.showBackground
    val crossfadeEnabled get() = frame.crossfadeEnabled
    val bibleTranslations get() = frame.bibleTranslations
    val secondaryBible get() = frame.secondaryBible
    val pBold get() = frame.pBold
    val pItalic get() = frame.pItalic
    val pUnderline get() = frame.pUnderline
    val pShadow get() = frame.pShadow
    val prBold get() = frame.prBold
    val prItalic get() = frame.prItalic
    val prUnderline get() = frame.prUnderline
    val prShadow get() = frame.prShadow
    val sBold get() = frame.sBold
    val sItalic get() = frame.sItalic
    val sUnderline get() = frame.sUnderline
    val sShadow get() = frame.sShadow
    val srBold get() = frame.srBold
    val srItalic get() = frame.srItalic
    val srUnderline get() = frame.srUnderline
    val srShadow get() = frame.srShadow
    val pStrike get() = frame.pStrike
    val prStrike get() = frame.prStrike
    val sStrike get() = frame.sStrike
    val srStrike get() = frame.srStrike
    val pTransform get() = frame.pTransform
    val prTransform get() = frame.prTransform
    val sTransform get() = frame.sTransform
    val srTransform get() = frame.srTransform
    val pLsEm get() = frame.pLsEm
    val prLsEm get() = frame.prLsEm
    val sLsEm get() = frame.sLsEm
    val srLsEm get() = frame.srLsEm
    val pWsEm get() = frame.pWsEm
    val prWsEm get() = frame.prWsEm
    val sWsEm get() = frame.sWsEm
    val srWsEm get() = frame.srWsEm
    val pBibleShadowVal get() = frame.pBibleShadowVal
    val pRefShadowVal get() = frame.pRefShadowVal
    val sBibleShadowVal get() = frame.sBibleShadowVal
    val sRefShadowVal get() = frame.sRefShadowVal
    val primaryBibleTextStyle get() = frame.primaryBibleTextStyle
    val primaryReferenceTextStyle get() = frame.primaryReferenceTextStyle
    val secondaryBibleTextStyle get() = frame.secondaryBibleTextStyle
    val secondaryReferenceTextStyle get() = frame.secondaryReferenceTextStyle
    val primaryBibleHorizontalAlignment get() = frame.primaryBibleHorizontalAlignment
    val primaryBibleReferenceHorizontalAlignment get() = frame.primaryBibleReferenceHorizontalAlignment
    val secondaryBibleHorizontalAlignment get() = frame.secondaryBibleHorizontalAlignment
    val secondaryBibleReferenceHorizontalAlignment get() = frame.secondaryBibleReferenceHorizontalAlignment
    val primaryBibleReferencePosition get() = frame.primaryBibleReferencePosition
    val secondaryBibleReferencePosition get() = frame.secondaryBibleReferencePosition
    val contentAlignment get() = frame.contentAlignment
    val primaryBibleFontStyle get() = frame.primaryBibleFontStyle
    val primaryBibleReferenceFontStyle get() = frame.primaryBibleReferenceFontStyle
    val secondaryBibleFontStyle get() = frame.secondaryBibleFontStyle
    val secondaryBibleReferenceFontStyle get() = frame.secondaryBibleReferenceFontStyle
    val primaryBibleTextColor get() = frame.primaryBibleTextColor
    val primaryBibleReferenceTextColor get() = frame.primaryBibleReferenceTextColor
    val secondaryBibleTextColor get() = frame.secondaryBibleTextColor
    val secondaryBibleReferenceTextColor get() = frame.secondaryBibleReferenceTextColor
    val pTextPainter get() = frame.pTextPainter
    val pRefPainter get() = frame.pRefPainter
    val sTextPainter get() = frame.sTextPainter
    val sRefPainter get() = frame.sRefPainter
    val resolvedBg get() = frame.resolvedBg
    val backgroundImageBitmap get() = frame.backgroundImageBitmap
    val effectiveOpacity get() = frame.effectiveOpacity
    val bgModifier get() = frame.bgModifier
    val scaleFactor get() = frame.scaleFactor
    val primaryBibleTextStyleScaled get() = frame.primaryBibleTextStyleScaled
    val primaryReferenceTextStyleScaled get() = frame.primaryReferenceTextStyleScaled
    val secondaryBibleTextStyleScaled get() = frame.secondaryBibleTextStyleScaled
    val secondaryReferenceTextStyleScaled get() = frame.secondaryReferenceTextStyleScaled
    val effectivePrimaryBibleSize get() = frame.effectivePrimaryBibleSize
    val effectivePrimaryReferenceSize get() = frame.effectivePrimaryReferenceSize
    val effectiveSecondaryBibleSize get() = frame.effectiveSecondaryBibleSize
    val effectiveSecondaryReferenceSize get() = frame.effectiveSecondaryReferenceSize
    val scaledPrimaryBibleSize get() = frame.scaledPrimaryBibleSize
    val scaledPrimaryReferenceSize get() = frame.scaledPrimaryReferenceSize
    val scaledSecondaryBibleSize get() = frame.scaledSecondaryBibleSize
    val scaledSecondaryReferenceSize get() = frame.scaledSecondaryReferenceSize
    val leftOffSet get() = frame.leftOffSet
    val rightOffSet get() = frame.rightOffSet
    val topOffSet get() = frame.topOffSet
    val bottomOffSet get() = frame.bottomOffSet
    val outputWidth get() = frame.outputWidth
    val outputHeight get() = frame.outputHeight
    val blurRadius get() = frame.blurRadius
    val density get() = frame.density
    val maxWidth get() = frame.maxWidth
    val maxHeight get() = frame.maxHeight
    val textMeasurer get() = frame.textMeasurer

    fun pText(raw: String) = frame.pText(raw)
    fun prText(raw: String) = frame.prText(raw)
    fun sText(raw: String) = frame.sText(raw)
    fun srText(raw: String) = frame.srText(raw)
    fun scaleElementShadow(color: String, size: Int, opacity: Int): Shadow =
        frame.scaleElementShadow(color, size, opacity)

    val primary = verses.first()
    val secondary = verses.getOrNull(1)
    // A settings file that names only a secondary bible still means "bilingual" -- the
    // condition this replaced keyed off exactly that, and dropping it stopped the second
    // language rendering for those files.
    // The second clause looks redundant — `withTranslations` keeps the legacy name in step
    // with the stack — but it is what covers a legacy file whose secondary is configured
    // and whose primary is not, where the stack collapses to one entry. It does mean a
    // hand-edited file with a stale `secondaryBible` can admit a second language this
    // output never asked for; that is a narrower problem than dropping one it did.
    val isParallelIntended = translationStack.size > 1 || bs.secondaryBible.isNotEmpty()
    val showParallelLayout = isParallelIntended && secondary != null && (!isLowerThird || t1.lowerThirdEnabled)
    val showSecondary = secondary != null && showParallelLayout
    // Every translation's own text -- styled, coloured, sized and outlined from its own
    // profile rather than from `t0`/`t1` -- shared by the full-screen stack below and by
    // the lower third's own 3/4-language grid further down. The lower third's original
    // two-language layouts style directly from `t0`/`t1` and do not use these.
    fun alignment(value: String) = when (value) {
        Constants.LEFT -> TextAlign.Start
        Constants.RIGHT -> TextAlign.End
        else -> TextAlign.Center
    }
    fun textStyle(item: BibleTranslationSettings): TextStyle {
        val shadowEnabled = item.textShadow
        val shadow = if (shadowEnabled) scaleElementShadow(
            item.textShadowColor,
            item.textShadowSize,
            item.textShadowOpacity,
        ) else null
        return TextStyle(
            fontWeight = if (item.textBold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (item.textItalic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = combinedTextDecoration(item.textUnderline, item.textStrikethrough),
            letterSpacing = spacingEm(item.textLetterSpacing, item.textFontSize).em,
            shadow = shadow,
        )
    }
    fun referenceStyle(item: BibleTranslationSettings): TextStyle {
        val shadowEnabled = item.referenceShadow
        val shadow = if (shadowEnabled) scaleElementShadow(
            item.referenceShadowColor,
            item.referenceShadowSize,
            item.referenceShadowOpacity,
        ) else null
        return TextStyle(
            fontWeight = if (item.referenceBold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (item.referenceItalic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = combinedTextDecoration(
                item.referenceUnderline,
                item.referenceStrikethrough,
            ),
            letterSpacing = spacingEm(item.referenceLetterSpacing, item.referenceFontSize).em,
            shadow = shadow,
        )
    }
    // The stack's equivalent of the pText/prText helpers above: this path styles
    // every translation from its own profile rather than from t0/t1, so the
    // transform and word spacing have to be read per item.
    fun itemText(item: BibleTranslationSettings, raw: String) = styledDisplayText(
        raw,
        item.textTransform,
        spacingEm(item.textLetterSpacing, item.textFontSize),
        spacingEm(item.textWordSpacing, item.textFontSize),
    )
    fun itemRefText(item: BibleTranslationSettings, raw: String) = styledDisplayText(
        raw,
        item.referenceTransform,
        spacingEm(item.referenceLetterSpacing, item.referenceFontSize),
        spacingEm(item.referenceWordSpacing, item.referenceFontSize),
    )
    // The lower third's own grid, from `bilingualLayoutLowerThird` -- read once here so
    // both the new 3/4-translation branch and the [bandSplits] gate below agree on it.
    // A vertical strip has no width to split, so it always stacks (one column) regardless
    // of what is configured, exactly as the single Top/Bottom choice always has.
    // Every translation this band shows, laid out in whatever the arrangement means for
    // that many -- not `rows x cols` of them. Capped only by what a band can carry.
    val lowerThirdSlots = verses.size.coerceAtMost(MAX_BIBLE_BAND_TRANSLATIONS)
    val lowerThirdGridCols = bilingualColumns(bs.bilingualLayoutLowerThird, lowerThirdSlots)
    val lowerThirdGridRows =
        ((lowerThirdSlots + lowerThirdGridCols - 1) / lowerThirdGridCols).coerceAtLeast(1)
    // Three or four languages, only when the band is actually configured for that many
    // and asked to show them. Two keep the layouts proven below, byte-for-byte unchanged
    // -- this is new capability, not a rewrite of what was already there.
    val lowerThirdMultiVisible = if (showParallelLayout && !isLowerThirdVertical &&
        lowerThirdSlots > 2
    ) {
        verses.take(lowerThirdSlots).mapIndexedNotNull { index, verse ->
            val style = when (index) {
                0 -> t0
                1 -> t1
                // Not `t0`/`t1`'s own resolution shape (that pair is looked up against
                // `effectiveVerses`, the output's own list) -- looked up against this
                // call's own `verses`/`translationStack` instead, which is what every
                // other slot beyond the first two has to go on.
                else -> translationStack.firstOrNull { it.fileName == verse.translationFileName }
                    ?: translationStack.getOrNull(index)
                    ?: BibleTranslationSettings()
            }
            // The first two are already gated by `showParallelLayout` above; a third or
            // fourth translation switched off for the lower third specifically drops out
            // here rather than displacing the ones after it.
            if (index > 1 && !style.lowerThirdEnabled) null else verse to style
        }
    } else {
        emptyList()
    }
    // isLowerThirdVertical forces bilingual/parallel content to stack (one below the
    // other) instead of the side-by-side Row split below — same band/geometry as
    // horizontal otherwise, see the routing to the single-column "else" branch.
    //
    // `bilingualLayoutLowerThird` now takes the same route by choice rather than by
    // shape: Top/Bottom on a horizontal band falls through to that same stacked branch,
    // which is why making the band stack needed no second layout written for it. A
    // vertical strip still stacks whatever the setting says — it has no width to split.
    // A band splits across its width only when it has a width to split: a vertical strip
    // and a Top/Bottom choice both send it to the stacked branch instead.
    val bandSplits = lowerThirdGridRows == 1 && lowerThirdGridCols > 1 && !isLowerThirdVertical
}

/** Only animate the text content — background is never inside this block. */
@Composable
internal fun BibleFrame.TextContent(verses: List<SelectedVerse>, innerModifier: Modifier) {
    val slide = BibleSlide(this, verses)
    with(slide) {
        // Full screen always draws the ordered stack, however many translations there are:
        // each line reads its own style profile and a shared fit scale keeps the whole stack
        // on screen. The lower third keeps its own one/two-language layouts below, because a
        // narrow band cannot usefully hold more than a couple of languages anyway.
        if (!isLowerThird) {
            val configured = translationStack
            // Only draw as many languages as this machine is set up for. A verse can arrive
            // carrying a second translation -- from a linked instance -- while nothing here
            // is configured to show one, and it must not appear unasked.
            val allowed = if (isParallelIntended) maxOf(configured.size, 2) else 1
            val visible = verses.take(allowed).mapIndexedNotNull { index, verse ->
                val style = configured.firstOrNull { it.fileName == verse.translationFileName }
                    ?: configured.getOrNull(index)
                    ?: BibleTranslationSettings(fileName = verse.translationFileName)
                verse to style
            }
            // Fills the frame rather than hugging its text: each translation gets an equal
            // band of the height and is aligned inside it, which is what the 50/50 split this
            // replaced did for two. Hugging left every translation bunched against the
            // configured alignment with the whole remainder as one empty strip -- with two
            // bibles and the default bottom alignment, an empty top half that read as a
            // section of its own.
            //
            // Clipped, unlike the single-language paths that never needed it: the fit search
            // below is measured against the bands, so anything it cannot get under would
            // otherwise draw through the configured margins and off the output.
            BoxWithConstraints(modifier = innerModifier.fillMaxSize().clipToBounds()) {
                BibleFullScreenStack(slide, visible)
            }
            return
        }

        if (lowerThirdMultiVisible.size > 2) {
            // Same shape of fit-search and grid the full-screen stack above uses, bounded to
            // this band's own box instead of the whole output, and laid out in the configured
            // rows x cols rather than always one row or one column.
            BoxWithConstraints(modifier = innerModifier, contentAlignment = Alignment.BottomCenter) {
                BibleLowerThirdGrid(slide)
            }
            return
        }

        if (showParallelLayout && isLowerThird && bandSplits) {
            // Never null here: a parallel layout is only shown with a second verse to put in it.
            val sec = secondary ?: return
            // Lower third: side-by-side Row layout (50/50) with matched auto-fit
            BoxWithConstraints(
                modifier = innerModifier,
                contentAlignment = Alignment.BottomCenter
            ) {
                BibleBandSplit(slide, sec)
            }
        } else {
            // Single-column layout: the lower third, in either orientation, with or without a
            // second language. Full screen never reaches here -- the stack above returns for
            // every !isLowerThird case, whatever the stack holds.
            BoxWithConstraints(
                modifier = innerModifier,
                contentAlignment = if (isLowerThird) Alignment.BottomCenter else contentAlignment
            ) {
                BibleBandStack(slide)
            }
        }
    }
}

/**
 * One translation, laid out inside whatever slot the container gives it, fit to a [fitScale] the
 * caller has already solved for -- so a full-screen stack of six and a lower-third grid of four can
 * each run their own search and share this one drawer.
 */
@Composable
internal fun BibleSlide.TranslationBlock(verse: SelectedVerse, item: BibleTranslationSettings, fitScale: Float) {
    val textSize = (item.textFontSize * scaleFactor * fitScale).sp
    val refSize = (item.referenceFontSize * scaleFactor * fitScale).sp
    val textFont = systemFontFamilyOrDefault(item.textFontType)
    val refFont = systemFontFamilyOrDefault(item.referenceFontType)
    val textColor = if (isKey) Color.White else parseHexColor(item.textColor)
    val refColor = if (isKey) Color.White else parseHexColor(item.referenceColor)
    val textAlign = alignment(item.textHorizontalAlignment)
    val refAlign = alignment(item.referenceHorizontalAlignment)
    val refPosition = item.referencePosition
    // Per translation, not per profile: every one in the stack draws
    // from its own settings, so each needs a painter of its own.
    val itemTextPainter =
        rememberTextBackdropPainter(item.textBackdropFor(isLowerThird))
    val itemRefPainter =
        rememberTextBackdropPainter(item.referenceBackdropFor(isLowerThird))
    // A boxed half is drawn in its box by the box layer, and leaves this block.
    val refBoxed = bs.isBoxed(item, BibleStyleElement.REFERENCE, isLowerThird)
    val textBoxed = bs.isBoxed(item, BibleStyleElement.TEXT, isLowerThird)

    // The two halves as their own composables, drawn the same wherever the
    // column puts them.
    val reference: @Composable (Boolean) -> Unit = { fill ->
        OutlinedText(
            text = itemRefText(item, buildRefText(verse, item)),
            fillWidth = fill,
            modifier = (if (fill) Modifier.fillMaxWidth() else Modifier)
                .referenceShift(item, isLowerThird, scaleFactor)
                .reportsBlock(PresentedBlock(PresentedBlock.Kind.REFERENCE, item.fileName))
                .backdropRoom(item.referenceBackdropFor(isLowerThird)).then(itemRefPainter.modifier),
            outline = item.referenceOutlineFor(isLowerThird),
            scaleFactor = scaleFactor,
            color = refColor,
            fontFamily = refFont,
            fontSize = refSize,
            textAlign = refAlign,
            style = referenceStyle(item),
            onTextLayout = itemRefPainter::onTextLayout,
        )
    }
    val verseText: @Composable (Boolean) -> Unit = { fill ->
        OutlinedText(
            text = itemText(item, verse.verseText),
            fillWidth = fill,
            modifier = (if (fill) Modifier.fillMaxWidth() else Modifier)
                .backdropRoom(item.textBackdropFor(isLowerThird)).then(itemTextPainter.modifier),
            outline = item.textOutlineFor(isLowerThird),
            scaleFactor = scaleFactor,
            color = textColor,
            fontFamily = textFont,
            fontSize = textSize,
            textAlign = textAlign,
            style = textStyle(item),
            onTextLayout = itemTextPainter::onTextLayout,
        )
    }

    Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
        if (refPosition == Constants.POSITION_ABOVE && !refBoxed) reference(true)
        if (!textBoxed) verseText(true)
        if (refPosition == Constants.POSITION_BELOW && !refBoxed) reference(true)
    }
}
