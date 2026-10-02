package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.channels.Channel
import org.churchpresenter.sharedui.utils.Utils.parseHexColor
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.SongSettings
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import org.churchpresenter.sharedui.presenter.presenterScale

/**
 * A song on one output once its size is known: the scale, the fitted lyric size and the scaled
 * text styles and margins. [maxWidth] and [maxHeight] are the text region's.
 *
 * It forwards [SongLook]'s values and the background's under their own names, so the code drawing
 * the frame reads them as it did when all of this was one function.
 */
@Suppress("LongParameterList")
internal class SongFrame(
    val look: SongLook,
    val backdrop: PresenterBackdrop,
    val blurRadius: Dp,
    val maxWidth: Dp,
    val maxHeight: Dp,
    val songFit: SongFit?,
) {
    val isKey get() = look.isKey
    val ss get() = look.ss
    val effectiveLangDisplay get() = look.effectiveLangDisplay
    val availableLanguages get() = look.availableLanguages
    val activeLanguages get() = look.activeLanguages
    val laFontSize get() = look.laFontSize
    val laBold get() = look.laBold
    val laItalic get() = look.laItalic
    val laUnderline get() = look.laUnderline
    val laShadowEnabled get() = look.laShadowEnabled
    val laShadowColor get() = look.laShadowColor
    val laShadowSizeMul get() = look.laShadowSizeMul
    val laShadowAlpha get() = look.laShadowAlpha
    val titleBaseShadow get() = look.titleBaseShadow
    val lyricsBaseShadow get() = look.lyricsBaseShadow
    val songTarget get() = look.songTarget
    val effectiveTitleBold get() = look.effectiveTitleBold
    val effectiveTitleItalic get() = look.effectiveTitleItalic
    val effectiveTitleUnderline get() = look.effectiveTitleUnderline
    val effectiveTitleShadow get() = look.effectiveTitleShadow
    val titleStyleProfile get() = look.titleStyleProfile
    val titleTextStyle get() = look.titleTextStyle
    val numberStyleProfile get() = look.numberStyleProfile
    val songNumberBaseShadow get() = look.songNumberBaseShadow
    val songNumberTextStyle get() = look.songNumberTextStyle
    val songNumberColor get() = look.songNumberColor
    val songNumberFontFamily get() = look.songNumberFontFamily
    val effectiveLyricsBold get() = look.effectiveLyricsBold
    val effectiveLyricsItalic get() = look.effectiveLyricsItalic
    val effectiveLyricsUnderline get() = look.effectiveLyricsUnderline
    val effectiveLyricsShadow get() = look.effectiveLyricsShadow
    val lyricsStyleProfile get() = look.lyricsStyleProfile
    val lyricsTextStyle get() = look.lyricsTextStyle
    val chartHorizontalAlignment get() = look.chartHorizontalAlignment
    val contentAlignment get() = look.contentAlignment
    val lyricsHorizontalAlignment get() = look.lyricsHorizontalAlignment
    val titleHorizontalAlignment get() = look.titleHorizontalAlignment
    val songNumberHorizontalAlignment get() = look.songNumberHorizontalAlignment
    val bgConfig get() = look.bgConfig
    val titleFontFamily get() = look.titleFontFamily
    val lyricsFontFamily get() = look.lyricsFontFamily
    val titleColor get() = look.titleColor
    val lyricsColor get() = look.lyricsColor
    val chordColor get() = look.chordColor
    val laColor get() = look.laColor
    val laFontFamily get() = look.laFontFamily
    val lyricSection get() = look.lyricSection
    val appSettings get() = look.appSettings
    val isLowerThird get() = look.isLowerThird
    val isLowerThirdVertical get() = look.isLowerThirdVertical
    val transitionAlpha get() = look.transitionAlpha
    val displayLineIndex get() = look.displayLineIndex
    val lookAheadEnabled get() = look.lookAheadEnabled
    val allLyricSections get() = look.allLyricSections
    val displaySectionIndex get() = look.displaySectionIndex
    val showBackground get() = look.showBackground
    val crossfadeEnabled get() = look.crossfadeEnabled
    val languageOverride get() = look.languageOverride
    val showChords get() = look.showChords
    val resolvedBg get() = backdrop.resolvedBg
    val backgroundImageBitmap get() = backdrop.backgroundImageBitmap
    val bgDimPercent get() = backdrop.bgDimPercent
    val bgBlurReferencePx get() = backdrop.bgBlurReferencePx
    val effectiveOpacity get() = backdrop.effectiveOpacity
    val bgModifier get() = backdrop.bgModifier
    val blurred get() = backdrop.blurred

    fun keyedOutline(outline: TextOutline): TextOutline = look.keyedOutline(outline)

    val scaleFactor = presenterScale(maxWidth, maxHeight)

    // Scale shadow to be visible at projection resolution
    fun scaleElementShadow(color: String, size: Int, opacity: Int): Shadow {
        val base = parseHexColor(color)
        val mul = size / 100f
        val alpha = (opacity / 100f).coerceIn(0f, 1f)
        return Shadow(
            color = base.copy(alpha = alpha),
            offset = Offset(SONG_SHADOW_OFFSET_PX * scaleFactor * mul, SONG_SHADOW_OFFSET_PX * scaleFactor * mul),
            blurRadius = 12f * scaleFactor * mul
        )
    }
    val titleTextStyleScaled = if (effectiveTitleShadow)
        titleTextStyle.copy(shadow = scaleElementShadow(
            if (isLowerThird) ss.titleLowerThirdShadowColor else ss.titleShadowColor,
            if (isLowerThird) ss.titleLowerThirdShadowSize else ss.titleShadowSize,
            if (isLowerThird) ss.titleLowerThirdShadowOpacity else ss.titleShadowOpacity
        )) else titleTextStyle
    val songNumberTextStyleScaled = if (numberStyleProfile.shadow) {
        songNumberTextStyle.copy(
            shadow = scaleElementShadow(
                numberStyleProfile.shadowColor,
                numberStyleProfile.shadowSize,
                numberStyleProfile.shadowOpacity,
            ),
        )
    } else {
        songNumberTextStyle
    }
    val lyricsTextStyleScaled = if (effectiveLyricsShadow)
        lyricsTextStyle.copy(shadow = scaleElementShadow(
            if (isLowerThird) ss.lyricsLowerThirdShadowColor else ss.lyricsShadowColor,
            if (isLowerThird) ss.lyricsLowerThirdShadowSize else ss.lyricsShadowSize,
            if (isLowerThird) ss.lyricsLowerThirdShadowOpacity else ss.lyricsShadowOpacity
        )) else lyricsTextStyle
    val effectiveTitleFontSize = if (isLowerThird) ss.titleLowerThirdFontSize else ss.titleFontSize
    val scaledTitleFontSize = (effectiveTitleFontSize * scaleFactor).sp
    val settingsLyricsFontSize = if (lookAheadEnabled) {
        if (isLowerThird) ss.lowerThirdLookAheadFontSize else ss.lookAheadFontSize
    } else if (isLowerThird) {
        appSettings.songSettings.lyricsLowerThirdFontSize
    } else {
        appSettings.songSettings.lyricsFontSize
    }
    val effectiveSongNumberFontSize =
        if (isLowerThird) {
            appSettings.songSettings.songNumberLowerThirdFontSize
        } else {
            appSettings.songSettings.songNumberFontSize
        }
    val fitEachSlide = songFitsEachSlide(ss, isLowerThird)
    val autoFitFontSize = songFit?.shared
    // Empty unless each language is fitted on its own -- see `fitLanguagesSeparately`.
    val languageFitSizes = songFit?.perLanguage.orEmpty()
    val autoFitEnabled = if (lookAheadEnabled) {
        if (isLowerThird) ss.lowerThirdLookAheadFontSizeAutoFit else ss.lookAheadFontSizeAutoFit
    } else {
        if (isLowerThird) ss.lyricsLowerThirdFontSizeAutoFit else ss.lyricsFontSizeAutoFit
    }
    val effectiveLyricsFontSize = if (autoFitEnabled) {
        (autoFitFontSize ?: settingsLyricsFontSize).coerceAtMost(settingsLyricsFontSize)
    } else settingsLyricsFontSize

    val scaledLyricsFontSize = (effectiveLyricsFontSize * scaleFactor).sp
    val scaledSongNumberFontSize = (effectiveSongNumberFontSize * scaleFactor).sp

    val leftOffSet =
        ((appSettings.projectionSettings.windowLeft + appSettings.songSettings.marginLeft) * scaleFactor).dp
    val rightOffSet =
        ((appSettings.projectionSettings.windowRight + appSettings.songSettings.marginRight) * scaleFactor).dp
    val topOffSet = ((appSettings.projectionSettings.windowTop + appSettings.songSettings.marginTop) * scaleFactor).dp
    val bottomOffSet =
        ((appSettings.projectionSettings.windowBottom + appSettings.songSettings.marginBottom) * scaleFactor).dp
    // Captured here, not read from inside the nested Box below: BoxScope and
    // BoxWithConstraintsScope both carry @LayoutScopeMarker, which hides this outer
    // BoxWithConstraints' maxWidth/maxHeight from a Box nested inside it.
    val outputWidth = maxWidth
    val outputHeight = maxHeight
}

/** Whether a song is fitted slide by slide rather than as a whole -- see `autoFitEachSlide`. */
internal fun songFitsEachSlide(ss: SongSettings, isLowerThird: Boolean): Boolean =
    if (isLowerThird) {
        ss.layoutExtras.autoFitEachSlideLowerThird
    } else {
        ss.layoutExtras.autoFitEachSlide
    }

/**
 * The song's frame inside the text region: the fit, then the lower-third band behind the text,
 * then the text itself.
 */
@Composable
internal fun BoxWithConstraintsScope.SongFrameContent(look: SongLook, backdrop: PresenterBackdrop, blurRadius: Dp) {
    val scaleFactor = presenterScale(maxWidth, maxHeight)
    val fitEachSlide = songFitsEachSlide(look.ss, look.isLowerThird)
    // Auto-fit: compute the largest font size that fits ALL sections without line wrapping.
    // Uses the reference 1920×1080 coordinate space (margins subtracted).
    val autoFitTextMeasurer = rememberTextMeasurer()
    val boxWidth = maxWidth
    val boxHeight = maxHeight
    val songFit = with(look) {
        remember(
            allLyricSections,
            isLowerThird,
            lookAheadEnabled,
            languageOverride,
            appSettings.songSettings,
            appSettings.projectionSettings,
            // The fit now measures against the real output's own aspect ratio (see referenceBoxWidth/
            // Height below), so a resize that changes scaleFactor without changing anything else above
            // must also invalidate this memo -- otherwise a live resize (or a screenshot test moving
            // between box sizes) would keep the previous size's stale fit.
            scaleFactor,
            // Only while fitting slide by slide: the song-wide fit is the same for every slide, and
            // keying it on the slide would re-measure the whole song on every advance.
            if (fitEachSlide) lyricSection else null,
            if (fitEachSlide) displaySectionIndex else null,
            if (fitEachSlide) displayLineIndex else null,
        ) {
            computeSongFit(autoFitTextMeasurer, boxWidth, boxHeight, scaleFactor, fitEachSlide)
        }
    }
    val frame = SongFrame(look, backdrop, blurRadius, maxWidth, maxHeight, songFit)
    if (look.isLowerThird) SongLowerThirdBand(frame)
    frame.SongTextArea()
}

/**
 * The lower third's band: its wash, its fill (grown past the band so a blur's fade falls outside),
 * its dim and its gradient.
 */
@Composable
private fun BoxScope.SongLowerThirdBand(frame: SongFrame) {
    // A local, so the null check below can smart-cast it.
    val backgroundImageBitmap = frame.backgroundImageBitmap
    with(frame) {
        val lowerThirdFraction = appSettings.songSettings.lowerThirdHeightPercent / 100f
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
        val bandBleed = if (blurred) blurRadius * BLUR_EDGE_BLEED else 0.dp
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
                    .then(if (blurred) Modifier.blur(blurRadius) else Modifier)
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
        if (bgDimPercent > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(lowerThirdFraction)
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = bgDimPercent / 100f))
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

/** The text over the background, crossfaded between slides where the song settings ask for it. */
@Composable
private fun SongFrame.SongTextArea() {
    Box(
        Modifier
            .fillMaxSize()
            // For a lower third, the padding moves inside the band's own box below instead of
            // applying here -- see the comment there for why.
            .then(
                if (isLowerThird) Modifier
                else Modifier.padding(start = leftOffSet, end = rightOffSet, top = topOffSet, bottom = bottomOffSet)
            ),
        contentAlignment = if (isLowerThird) Alignment.BottomCenter else contentAlignment
    ) {
        val innerModifier = if (isLowerThird) {
            // Capped at a quarter of the band's own height/width each, so top+bottom (or
            // left+right) can never consume more than half of it: a shallow band (a low
            // lowerThirdHeightPercent) combined with the operator's ordinary window/margin
            // insets can ask for more padding than the band is tall, and an uncapped padding
            // that exceeds its own box collapses it to zero size -- which is a real
            // misconfiguration to render as small type crowding the band, not a reason to
            // report every line at y=0 of the whole screen, which is what a collapsed box does.
            val bandHeight = outputHeight * (appSettings.songSettings.lowerThirdHeightPercent / 100f)
            val bandTopOffSet = topOffSet.coerceAtMost(bandHeight / 4)
            val bandBottomOffSet = bottomOffSet.coerceAtMost(bandHeight / 4)
            val bandLeftOffSet = leftOffSet.coerceAtMost(outputWidth / 4)
            val bandRightOffSet = rightOffSet.coerceAtMost(outputWidth / 4)
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(appSettings.songSettings.lowerThirdHeightPercent / 100f)
                .align(Alignment.BottomCenter)
                // Sized and positioned first, against this Box's own full (unpadded) bounds --
                // the same bounds the band's background above measures its fraction against --
                // and only then padded. Padding used to apply before the fraction was taken, so
                // it shrank *and* shifted this box relative to the background: with a window
                // inset and a margin on top (32 + 54 by default), the content box's own top
                // edge sat ~86px above the background band's, so a title positioned at the top
                // of this box -- as "above the verse" always is -- rendered above the visible
                // band rather than inside it. Padding here now insets the text within the band
                // exactly as it was always meant to, without moving the band itself.
                .padding(start = bandLeftOffSet, end = bandRightOffSet, top = bandTopOffSet, bottom = bandBottomOffSet)
        } else {
            Modifier
        }
        if (crossfadeEnabled || ss.fadeIn || ss.fadeOut) {
            SongSlideTransition(this@SongTextArea, innerModifier)
        } else {
            Box(modifier = Modifier.graphicsLayer { alpha = transitionAlpha }) {
                TextContent(lyricSection, displayLineIndex, innerModifier)
            }
            SlideBoxes(lyricSection, displayLineIndex, transitionAlpha)
        }
    }
}

/** Crossfades or fades from one slide to the next, each layer carrying the line it draws. */
@Composable
private fun BoxScope.SongSlideTransition(frame: SongFrame, innerModifier: Modifier) {
    with(frame) {
        val duration = ss.transitionDuration.toInt().coerceAtLeast(100)
        val isCrossfade = crossfadeEnabled
        // Each layer carries the line it draws, not just its section. Sharing one live line
        // index made the outgoing layer redraw its old section at the incoming line for as
        // long as the crossfade ran, which is a flash of a line that was never on that page.
        var displayedCurrent by remember {
            mutableStateOf(SongCrossfadePage(lyricSection, displayLineIndex))
        }
        var displayedPrevious by remember { mutableStateOf(SongCrossfadePage(LyricSection(), -1)) }
        var currentAlpha by remember { mutableStateOf(1f) }
        var previousAlpha by remember { mutableStateOf(0f) }
        val pendingQueue = remember { Channel<SongCrossfadePage>(Channel.CONFLATED) }

        // Queue section changes
        LaunchedEffect(lyricSection, displayLineIndex) {
            val target = SongCrossfadePage(lyricSection, displayLineIndex)
            when {
                displayedCurrent == target -> Unit
                // Stepping a line inside the section already up has never crossfaded, and
                // queueing it would fade the section out against itself.
                displayedCurrent.section == target.section -> displayedCurrent = target
                else -> pendingQueue.send(target)
            }
        }

        // Process section switches (crossfade between sections)
        LaunchedEffect(Unit) {
            for (nextPage in pendingQueue) {
                if (displayedCurrent == nextPage) continue

                if (isCrossfade) {
                    displayedPrevious = displayedCurrent
                    displayedCurrent = nextPage
                    previousAlpha = 1f
                    currentAlpha = 0f
                    val anim = Animatable(0f)
                    anim.animateTo(1f, tween(durationMillis = duration)) {
                        currentAlpha = this.value
                        previousAlpha = 1f - this.value
                    }
                } else {
                    displayedCurrent = nextPage
                }
                currentAlpha = 1f
                previousAlpha = 0f
                displayedPrevious = SongCrossfadePage(LyricSection(), -1)
            }
        }

        Box(modifier = Modifier.matchParentSize().graphicsLayer { alpha = transitionAlpha }) {
            if (displayedPrevious.section.lines.isNotEmpty() && previousAlpha > 0f) {
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = previousAlpha }) {
                    TextContent(displayedPrevious.section, displayedPrevious.lineIndex, innerModifier)
                }
                SlideBoxes(displayedPrevious.section, displayedPrevious.lineIndex, previousAlpha)
            }
            Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = currentAlpha }) {
                TextContent(displayedCurrent.section, displayedCurrent.lineIndex, innerModifier)
            }
            SlideBoxes(displayedCurrent.section, displayedCurrent.lineIndex, currentAlpha)
        }
    }
}
