package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.Utils.parseHexColor
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/**
 * The verses on one output once its size is known: the scale, the scaled styles and sizes, and the
 * margins. [maxWidth] and [maxHeight] are the text region's. Forwards [BibleStyle]'s values.
 */
@Suppress("LongParameterList")
internal class BibleFrame(
    val style: BibleStyle,
    val backdrop: PresenterBackdrop,
    val blurRadius: Dp,
    val density: Density,
    val maxWidth: Dp,
    val maxHeight: Dp,
    val textMeasurer: TextMeasurer,
) {
    val isKey get() = style.isKey
    val bs get() = style.bs
    val translationStack get() = style.translationStack
    val assignedFileNames get() = style.assignedFileNames
    val effectiveVerses get() = style.effectiveVerses
    val t0 get() = style.t0
    val t1 get() = style.t1
    val t2 get() = style.t2
    val t3 get() = style.t3
    val bgConfig get() = style.bgConfig
    val selectedVerses get() = style.selectedVerses
    val appSettings get() = style.appSettings
    val isLowerThird get() = style.isLowerThird
    val isLowerThirdVertical get() = style.isLowerThirdVertical
    val transitionAlpha get() = style.transitionAlpha
    val showBackground get() = style.showBackground
    val crossfadeEnabled get() = style.crossfadeEnabled
    val bibleTranslations get() = style.bibleTranslations
    val secondaryBible get() = style.secondaryBible
    val pBold get() = style.pBold
    val pItalic get() = style.pItalic
    val pUnderline get() = style.pUnderline
    val pShadow get() = style.pShadow
    val prBold get() = style.prBold
    val prItalic get() = style.prItalic
    val prUnderline get() = style.prUnderline
    val prShadow get() = style.prShadow
    val sBold get() = style.sBold
    val sItalic get() = style.sItalic
    val sUnderline get() = style.sUnderline
    val sShadow get() = style.sShadow
    val srBold get() = style.srBold
    val srItalic get() = style.srItalic
    val srUnderline get() = style.srUnderline
    val srShadow get() = style.srShadow
    val pStrike get() = style.pStrike
    val prStrike get() = style.prStrike
    val sStrike get() = style.sStrike
    val srStrike get() = style.srStrike
    val pTransform get() = style.pTransform
    val prTransform get() = style.prTransform
    val sTransform get() = style.sTransform
    val srTransform get() = style.srTransform
    val pLsEm get() = style.pLsEm
    val prLsEm get() = style.prLsEm
    val sLsEm get() = style.sLsEm
    val srLsEm get() = style.srLsEm
    val pWsEm get() = style.pWsEm
    val prWsEm get() = style.prWsEm
    val sWsEm get() = style.sWsEm
    val srWsEm get() = style.srWsEm
    val pBibleShadowVal get() = style.pBibleShadowVal
    val pRefShadowVal get() = style.pRefShadowVal
    val sBibleShadowVal get() = style.sBibleShadowVal
    val sRefShadowVal get() = style.sRefShadowVal
    val primaryBibleTextStyle get() = style.primaryBibleTextStyle
    val primaryReferenceTextStyle get() = style.primaryReferenceTextStyle
    val secondaryBibleTextStyle get() = style.secondaryBibleTextStyle
    val secondaryReferenceTextStyle get() = style.secondaryReferenceTextStyle
    val primaryBibleHorizontalAlignment get() = style.primaryBibleHorizontalAlignment
    val primaryBibleReferenceHorizontalAlignment get() = style.primaryBibleReferenceHorizontalAlignment
    val secondaryBibleHorizontalAlignment get() = style.secondaryBibleHorizontalAlignment
    val secondaryBibleReferenceHorizontalAlignment get() = style.secondaryBibleReferenceHorizontalAlignment
    val primaryBibleReferencePosition get() = style.primaryBibleReferencePosition
    val secondaryBibleReferencePosition get() = style.secondaryBibleReferencePosition
    val contentAlignment get() = style.contentAlignment
    val primaryBibleFontStyle get() = style.primaryBibleFontStyle
    val primaryBibleReferenceFontStyle get() = style.primaryBibleReferenceFontStyle
    val secondaryBibleFontStyle get() = style.secondaryBibleFontStyle
    val secondaryBibleReferenceFontStyle get() = style.secondaryBibleReferenceFontStyle
    val primaryBibleTextColor get() = style.primaryBibleTextColor
    val primaryBibleReferenceTextColor get() = style.primaryBibleReferenceTextColor
    val secondaryBibleTextColor get() = style.secondaryBibleTextColor
    val secondaryBibleReferenceTextColor get() = style.secondaryBibleReferenceTextColor
    val pTextPainter get() = style.pTextPainter
    val pRefPainter get() = style.pRefPainter
    val sTextPainter get() = style.sTextPainter
    val sRefPainter get() = style.sRefPainter
    val resolvedBg get() = backdrop.resolvedBg
    val backgroundImageBitmap get() = backdrop.backgroundImageBitmap
    val effectiveOpacity get() = backdrop.effectiveOpacity
    val bgModifier get() = backdrop.bgModifier

    fun pText(raw: String) = style.pText(raw)
    fun prText(raw: String) = style.prText(raw)
    fun sText(raw: String) = style.sText(raw)
    fun srText(raw: String) = style.srText(raw)
    fun slotStyle(slot: Int): BibleTranslationSettings = style.look.slotStyle(slot)

    val scaleFactor = presenterScale(maxWidth, maxHeight)

    // Scale shadow to be visible at projection resolution
    fun scaleElementShadow(color: String, size: Int, opacity: Int): Shadow {
        val base = parseHexColor(color)
        val mul = size / 100f
        val alpha = (opacity / 100f).coerceIn(0f, 1f)
        return Shadow(
            color = base.copy(alpha = alpha),
            offset = Offset(BIBLE_SHADOW_OFFSET_PX * scaleFactor * mul, BIBLE_SHADOW_OFFSET_PX * scaleFactor * mul),
            blurRadius = 12f * scaleFactor * mul
        )
    }
    val primaryBibleTextStyleScaled = if (pShadow)
        primaryBibleTextStyle.copy(shadow = scaleElementShadow(
            if (isLowerThird) t0.lowerThirdTextShadowColor else t0.textShadowColor,
            if (isLowerThird) t0.lowerThirdTextShadowSize else t0.textShadowSize,
            if (isLowerThird) t0.lowerThirdTextShadowOpacity else t0.textShadowOpacity
        )) else primaryBibleTextStyle
    val primaryReferenceTextStyleScaled = if (prShadow)
        primaryReferenceTextStyle.copy(shadow = scaleElementShadow(
            if (isLowerThird) t0.lowerThirdReferenceShadowColor else t0.referenceShadowColor,
            if (isLowerThird) t0.lowerThirdReferenceShadowSize else t0.referenceShadowSize,
            if (isLowerThird) t0.lowerThirdReferenceShadowOpacity else t0.referenceShadowOpacity
        )) else primaryReferenceTextStyle
    val secondaryBibleTextStyleScaled = if (sShadow)
        secondaryBibleTextStyle.copy(shadow = scaleElementShadow(
            if (isLowerThird) t1.lowerThirdTextShadowColor else t1.textShadowColor,
            if (isLowerThird) t1.lowerThirdTextShadowSize else t1.textShadowSize,
            if (isLowerThird) t1.lowerThirdTextShadowOpacity else t1.textShadowOpacity
        )) else secondaryBibleTextStyle
    val secondaryReferenceTextStyleScaled = if (srShadow)
        secondaryReferenceTextStyle.copy(shadow = scaleElementShadow(
            if (isLowerThird) t1.lowerThirdReferenceShadowColor else t1.referenceShadowColor,
            if (isLowerThird) t1.lowerThirdReferenceShadowSize else t1.referenceShadowSize,
            if (isLowerThird) t1.lowerThirdReferenceShadowOpacity else t1.referenceShadowOpacity
        )) else secondaryReferenceTextStyle

    val effectivePrimaryBibleSize =
        if (isLowerThird) t0.lowerThirdTextFontSize else t0.textFontSize
    val effectivePrimaryReferenceSize =
        if (isLowerThird) t0.lowerThirdReferenceFontSize else t0.referenceFontSize
    val effectiveSecondaryBibleSize =
        if (isLowerThird) t1.lowerThirdTextFontSize else t1.textFontSize
    val effectiveSecondaryReferenceSize =
        if (isLowerThird) t1.lowerThirdReferenceFontSize else t1.referenceFontSize
    val scaledPrimaryBibleSize = (effectivePrimaryBibleSize * scaleFactor).sp
    val scaledPrimaryReferenceSize = (effectivePrimaryReferenceSize * scaleFactor).sp
    val scaledSecondaryBibleSize = (effectiveSecondaryBibleSize * scaleFactor).sp
    val scaledSecondaryReferenceSize = (effectiveSecondaryReferenceSize * scaleFactor).sp
    val leftOffSet =
        ((appSettings.projectionSettings.windowLeft + appSettings.bibleSettings.marginLeft) * scaleFactor).dp
    val rightOffSet =
        ((appSettings.projectionSettings.windowRight + appSettings.bibleSettings.marginRight) * scaleFactor).dp
    val topOffSet = ((appSettings.projectionSettings.windowTop + appSettings.bibleSettings.marginTop) * scaleFactor).dp
    val bottomOffSet =
        ((appSettings.projectionSettings.windowBottom + appSettings.bibleSettings.marginBottom) * scaleFactor).dp
    // Captured here, not read from inside the nested Box below: BoxScope and
    // BoxWithConstraintsScope both carry @LayoutScopeMarker, which hides this outer
    // BoxWithConstraints' maxWidth/maxHeight from a Box nested inside it.
    val outputWidth = maxWidth
    val outputHeight = maxHeight
}

/** The verses' frame inside the text region: the lower-third band behind them, then the text. */
@Composable
internal fun BoxWithConstraintsScope.BibleFrameContent(
    style: BibleStyle,
    backdrop: PresenterBackdrop,
    blurRadius: Dp,
    density: Density,
) {
    val frame = BibleFrame(style, backdrop, blurRadius, density, maxWidth, maxHeight, rememberTextMeasurer())
    if (style.isLowerThird) BibleLowerThirdBand(frame)
    frame.BibleTextArea()
}

/**
 * The lower third's band: its wash, its fill (grown past the band so a blur's fade falls outside),
 * its dim and its gradient.
 */
@Composable
private fun BoxScope.BibleLowerThirdBand(frame: BibleFrame) {
    // A local, so the null check below can smart-cast it.
    val backgroundImageBitmap = frame.backgroundImageBitmap
    with(frame) {
        val lowerThirdFraction = appSettings.bibleSettings.lowerThirdHeightPercent / 100f
        // Background stretches full width at bottom third, text respects padding on top —
        // same band geometry for horizontal and vertical; isLowerThirdVertical only forces
        // bilingual content to stack instead of side-by-side, see TextContent below.
        // `Modifier.blur` fades a layer's own edge to transparent, so blurring the band
        // itself let whatever is behind — the default lower third's own color — show through
        // along the band's top as a hairline the width of the blur.
        // The fill is drawn larger than the band and the band clips it, so the fade
        // `Modifier.blur` leaves around a layer's own edge falls out of sight. Grown rather
        // than scaled: the picture is cropped from a slightly larger rectangle instead of
        // being stretched, which a band is wide enough to show.
        // The wash behind the whole lower third, band included — see AboveBandFill.
        val above = resolveAboveBand(appSettings.backgroundSettings, bgConfig)
        AboveBandFill(
            above = above,
            show = showBackground,
            bandFraction = lowerThirdFraction,
        )
        val bandBleed = if (resolvedBg.isBlurred) blurRadius * BLUR_EDGE_BLEED else 0.dp
        // Read out here: the band Box's own scope shadows this one.
        val bandFillWidth = maxWidth + bandBleed * 2
        val bandFillHeight = maxHeight * lowerThirdFraction + bandBleed * 2
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(lowerThirdFraction)
                .align(Alignment.BottomCenter)
                .clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .requiredSize(width = bandFillWidth, height = bandFillHeight)
                    .then(if (resolvedBg.isBlurred) Modifier.blur(blurRadius) else Modifier)
                    .then(
                        if (resolvedBg.type == Constants.BACKGROUND_IMAGE && backgroundImageBitmap != null) {
                            Modifier
                        } else {
                            bgModifier
                        }
                    )
            ) {
                if (resolvedBg.type == Constants.BACKGROUND_IMAGE && backgroundImageBitmap != null) {
                    Image(
                        painter = BitmapPainter(backgroundImageBitmap),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        // Cropped from the middle of the picture, not its bottom edge. Scaled
                        // to the band's width a photo is several times the band's height, so
                        // anchoring it to the bottom showed the strip below the subject — the
                        // desk under a photo of someone reading — and never the photo itself.
                        // The Background tab's preview crops from the center; this is what
                        // makes the two agree.
                        alignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize().alpha(effectiveOpacity)
                    )
                }
                BandMediaLayers(resolvedBg)
            }
        }
        if (resolvedBg.dimPercent > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(lowerThirdFraction)
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = resolvedBg.dimPercent / PERCENT))
            )
        }
        // Gradient overlay
        if (bgConfig.gradientEnabled) {
            val gradientTop = parseHexColor(bgConfig.gradientTopColor).copy(alpha = bgConfig.gradientTopOpacity)
            val gradientBottom =
                parseHexColor(bgConfig.gradientBottomColor).copy(alpha = bgConfig.gradientBottomOpacity)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(lowerThirdFraction)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to gradientTop,
                                bgConfig.gradientPosition to gradientBottom,
                                1.0f to gradientBottom
                            )
                        )
                    )
            )
        }
    }
}

/** The verses over the background, crossfaded between verses where the settings ask for it. */
@Composable
private fun BibleFrame.BibleTextArea() {
    // Outer box for padding/alignment — not animated
    Box(
        modifier = Modifier
            .fillMaxSize()
            // For a lower third, the padding moves inside the band's own box below instead of
            // applying here -- see the comment there for why (SongPresenter carries the same fix).
            .then(
                if (isLowerThird) Modifier
                else Modifier.padding(start = leftOffSet, end = rightOffSet, top = topOffSet, bottom = bottomOffSet)
            ),
        contentAlignment = if (isLowerThird) Alignment.BottomCenter else contentAlignment
    ) {
        val innerModifier = if (isLowerThird) {
            // Capped at a quarter of the band's own height/width each -- see SongPresenter's identical
            // fix for why: an uncapped padding that exceeds a shallow band's own box collapses
            // it to zero size, reporting every line at y=0 of the whole screen instead of
            // rendering small type crowding the band.
            val bandHeight = outputHeight * (appSettings.bibleSettings.lowerThirdHeightPercent / 100f)
            val bandTopOffSet = topOffSet.coerceAtMost(bandHeight / 4)
            val bandBottomOffSet = bottomOffSet.coerceAtMost(bandHeight / 4)
            val bandLeftOffSet = leftOffSet.coerceAtMost(outputWidth / 4)
            val bandRightOffSet = rightOffSet.coerceAtMost(outputWidth / 4)
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(appSettings.bibleSettings.lowerThirdHeightPercent / 100f)
                .align(Alignment.BottomCenter)
                // Sized and positioned first, against this Box's own full (unpadded) bounds --
                // the same bounds the band's background above measures its fraction against --
                // and only then padded and clipped. Padding used to apply before the fraction was
                // taken, shifting this box's top edge above the background band's by the window
                // inset and margin combined (issue: a verse or its reference rendering above the
                // visible band rather than inside it).
                .padding(start = bandLeftOffSet, end = bandRightOffSet, top = bandTopOffSet, bottom = bandBottomOffSet)
                .clipToBounds()
        } else {
            Modifier.align(contentAlignment)
        }
        if (crossfadeEnabled || appSettings.bibleSettings.fadeIn || appSettings.bibleSettings.fadeOut) {
            BibleVerseTransition(this@BibleTextArea, innerModifier)
        } else {
            Box(modifier = Modifier.graphicsLayer { alpha = transitionAlpha }) {
                TextContent(effectiveVerses, innerModifier)
            }
            VerseBoxes(effectiveVerses, transitionAlpha)
        }
    }
}

/**
 * Crossfades or fades from one set of verses to the next. Fade in on first appearance and fade out
 * on clear are driven from outside, through `transitionAlpha`.
 */
@Composable
private fun BoxScope.BibleVerseTransition(frame: BibleFrame, innerModifier: Modifier) {
    with(frame) {
        val layers = rememberFadeLayers(
            target = effectiveVerses,
            crossfade = crossfadeEnabled,
            durationMs = bs.transitionDuration.toInt().coerceAtLeast(100),
            samePage = { _, _ -> false },
        )
        Box(modifier = Modifier.matchParentSize().graphicsLayer { alpha = transitionAlpha }) {
            for (layer in layers) {
                key(layer) {
                    val verses = layer.page
                    Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = layer.alpha }) {
                        TextContent(verses, innerModifier)
                    }
                    VerseBoxes(verses, layer.alpha)
                }
            }
        }
    }
}

/**
 * The verses' boxed text and references, drawn over them at [alpha]. Nothing at all
 * while the page has no box turned on.
 */
@Composable
internal fun BibleFrame.VerseBoxes(verses: List<SelectedVerse>, alpha: Float) {
    if (bs.textBoxes.values.none { it.enabled } || verses.isEmpty()) return
    val shown = verses.mapIndexed { index, verse ->
        val item = translationStack.firstOrNull { it.fileName == verse.translationFileName }
            ?: translationStack.getOrNull(index)
            ?: BibleTranslationSettings(fileName = verse.translationFileName)
        verse to item
    }
    val bandHeight = outputHeight.value * bs.lowerThirdHeightPercent / 100f
    BibleBoxLayer(
        items = bibleBoxItems(bs, isLowerThird, shown),
        settings = bs,
        lowerThird = isLowerThird,
        area = textBoxArea(
            outputWidth = outputWidth.value,
            outputHeight = outputHeight.value,
            options = bs.textBoxOptions,
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
        shadowOf = this::scaleElementShadow,
    )
}
