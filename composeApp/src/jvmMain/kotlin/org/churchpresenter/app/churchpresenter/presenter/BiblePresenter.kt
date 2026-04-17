package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.composables.LoopingVideoBackground
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.min

private fun binarySearchFitScale(
    minScale: Float = 0.15f,
    iterations: Int = 8,
    fits: (scale: Float) -> Boolean
): Float {
    var lo = minScale
    var hi = 1f
    repeat(iterations) {
        val mid = (lo + hi) / 2f
        if (fits(mid)) lo = mid else hi = mid
    }
    return lo
}

@Composable
fun BiblePresenter(
    modifier: Modifier = Modifier,
    selectedVerses: List<SelectedVerse>,
    appSettings: AppSettings,
    isLowerThird: Boolean = false,
    outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
    transitionAlpha: Float = 1f,
    showBackground: Boolean = true,
    crossfadeEnabled: Boolean = false,
) {
    val isFillOrKey = outputRole == Constants.OUTPUT_ROLE_FILL || outputRole == Constants.OUTPUT_ROLE_KEY
    val isKey = outputRole == Constants.OUTPUT_ROLE_KEY
    val bs = appSettings.bibleSettings

    // Resolve font families — use lower-third-specific values when applicable
    val primaryBibleFontStyle = remember(
        if (isLowerThird) bs.primaryBibleLowerThirdFontType else bs.primaryBibleFontType
    ) {
        systemFontFamilyOrDefault(if (isLowerThird) bs.primaryBibleLowerThirdFontType else bs.primaryBibleFontType)
    }
    val primaryBibleReferenceFontStyle = remember(
        if (isLowerThird) bs.primaryReferenceLowerThirdFontType else bs.primaryReferenceFontType
    ) {
        systemFontFamilyOrDefault(if (isLowerThird) bs.primaryReferenceLowerThirdFontType else bs.primaryReferenceFontType)
    }
    val secondaryBibleFontStyle = remember(
        if (isLowerThird) bs.secondaryBibleLowerThirdFontType else bs.secondaryBibleFontType
    ) {
        systemFontFamilyOrDefault(if (isLowerThird) bs.secondaryBibleLowerThirdFontType else bs.secondaryBibleFontType)
    }
    val secondaryBibleReferenceFontStyle = remember(
        if (isLowerThird) bs.secondaryReferenceLowerThirdFontType else bs.secondaryReferenceFontType
    ) {
        systemFontFamilyOrDefault(if (isLowerThird) bs.secondaryReferenceLowerThirdFontType else bs.secondaryReferenceFontType)
    }

    val primaryBible = selectedVerses.first()
    val secondaryBible = selectedVerses.getOrNull(1)

    // Resolve colors — key mode forces white for a proper key signal
    val primaryBibleTextColor = remember(
        if (isLowerThird) bs.primaryBibleLowerThirdColor else bs.primaryBibleColor, isKey
    ) {
        if (isKey) Color.White
        else parseHexColor(if (isLowerThird) bs.primaryBibleLowerThirdColor else bs.primaryBibleColor)
    }
    val primaryBibleReferenceTextColor = remember(
        if (isLowerThird) bs.primaryReferenceLowerThirdColor else bs.primaryReferenceColor, isKey
    ) {
        if (isKey) Color.White
        else parseHexColor(if (isLowerThird) bs.primaryReferenceLowerThirdColor else bs.primaryReferenceColor)
    }
    val secondaryBibleTextColor = remember(
        if (isLowerThird) bs.secondaryBibleLowerThirdColor else bs.secondaryBibleColor, isKey
    ) {
        if (isKey) Color.White
        else parseHexColor(if (isLowerThird) bs.secondaryBibleLowerThirdColor else bs.secondaryBibleColor)
    }
    val secondaryBibleReferenceTextColor = remember(
        if (isLowerThird) bs.secondaryReferenceLowerThirdColor else bs.secondaryReferenceColor, isKey
    ) {
        if (isKey) Color.White
        else parseHexColor(if (isLowerThird) bs.secondaryReferenceLowerThirdColor else bs.secondaryReferenceColor)
    }

    // Resolve bold/italic/underline/shadow — use lower-third-specific values when applicable
    val pBold = if (isLowerThird) bs.primaryBibleLowerThirdBold else bs.primaryBibleBold
    val pItalic = if (isLowerThird) bs.primaryBibleLowerThirdItalic else bs.primaryBibleItalic
    val pUnderline = if (isLowerThird) bs.primaryBibleLowerThirdUnderline else bs.primaryBibleUnderline
    val pShadow = if (isLowerThird) bs.primaryBibleLowerThirdShadow else bs.primaryBibleShadow
    val prBold = if (isLowerThird) bs.primaryReferenceLowerThirdBold else bs.primaryReferenceBold
    val prItalic = if (isLowerThird) bs.primaryReferenceLowerThirdItalic else bs.primaryReferenceItalic
    val prUnderline = if (isLowerThird) bs.primaryReferenceLowerThirdUnderline else bs.primaryReferenceUnderline
    val prShadow = if (isLowerThird) bs.primaryReferenceLowerThirdShadow else bs.primaryReferenceShadow
    val sBold = if (isLowerThird) bs.secondaryBibleLowerThirdBold else bs.secondaryBibleBold
    val sItalic = if (isLowerThird) bs.secondaryBibleLowerThirdItalic else bs.secondaryBibleItalic
    val sUnderline = if (isLowerThird) bs.secondaryBibleLowerThirdUnderline else bs.secondaryBibleUnderline
    val sShadow = if (isLowerThird) bs.secondaryBibleLowerThirdShadow else bs.secondaryBibleShadow
    val srBold = if (isLowerThird) bs.secondaryReferenceLowerThirdBold else bs.secondaryReferenceBold
    val srItalic = if (isLowerThird) bs.secondaryReferenceLowerThirdItalic else bs.secondaryReferenceItalic
    val srUnderline = if (isLowerThird) bs.secondaryReferenceLowerThirdUnderline else bs.secondaryReferenceUnderline
    val srShadow = if (isLowerThird) bs.secondaryReferenceLowerThirdShadow else bs.secondaryReferenceShadow

    // Per-element shadow helpers
    fun makeShadow(color: String, size: Int, opacity: Int, alphaScale: Float = 0.78f): Shadow {
        val base = parseHexColor(color)
        val mul = size / 100f
        val alpha = (opacity / 100f).coerceIn(0f, 1f)
        return Shadow(
            color = base.copy(alpha = alpha * alphaScale),
            offset = Offset(2f * mul, 2f * mul),
            blurRadius = 4f * mul
        )
    }

    val pBibleShadowVal = makeShadow(
        if (isLowerThird) bs.primaryBibleLowerThirdShadowColor else bs.primaryBibleShadowColor,
        if (isLowerThird) bs.primaryBibleLowerThirdShadowSize else bs.primaryBibleShadowSize,
        if (isLowerThird) bs.primaryBibleLowerThirdShadowOpacity else bs.primaryBibleShadowOpacity
    )
    val pRefShadowVal = makeShadow(
        if (isLowerThird) bs.primaryReferenceLowerThirdShadowColor else bs.primaryReferenceShadowColor,
        if (isLowerThird) bs.primaryReferenceLowerThirdShadowSize else bs.primaryReferenceShadowSize,
        if (isLowerThird) bs.primaryReferenceLowerThirdShadowOpacity else bs.primaryReferenceShadowOpacity
    )
    val sBibleShadowVal = makeShadow(
        if (isLowerThird) bs.secondaryBibleLowerThirdShadowColor else bs.secondaryBibleShadowColor,
        if (isLowerThird) bs.secondaryBibleLowerThirdShadowSize else bs.secondaryBibleShadowSize,
        if (isLowerThird) bs.secondaryBibleLowerThirdShadowOpacity else bs.secondaryBibleShadowOpacity
    )
    val sRefShadowVal = makeShadow(
        if (isLowerThird) bs.secondaryReferenceLowerThirdShadowColor else bs.secondaryReferenceShadowColor,
        if (isLowerThird) bs.secondaryReferenceLowerThirdShadowSize else bs.secondaryReferenceShadowSize,
        if (isLowerThird) bs.secondaryReferenceLowerThirdShadowOpacity else bs.secondaryReferenceShadowOpacity
    )

    // Text styles from settings
    val primaryBibleTextStyle = TextStyle(
        fontWeight = if (pBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (pItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (pUnderline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (pShadow) pBibleShadowVal else null
    )
    val primaryReferenceTextStyle = TextStyle(
        fontWeight = if (prBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (prItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (prUnderline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (prShadow) pRefShadowVal else null
    )
    val secondaryBibleTextStyle = TextStyle(
        fontWeight = if (sBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (sItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (sUnderline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (sShadow) sBibleShadowVal else null
    )
    val secondaryReferenceTextStyle = TextStyle(
        fontWeight = if (srBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (srItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (srUnderline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (srShadow) sRefShadowVal else null
    )

    val primaryBibleHorizontalAlignment = when (
        if (isLowerThird) appSettings.bibleSettings.primaryBibleLowerThirdHorizontalAlignment
        else appSettings.bibleSettings.primaryBibleHorizontalAlignment
    ) {
        Constants.LEFT -> TextAlign.Start
        Constants.RIGHT -> TextAlign.End
        else -> TextAlign.Center
    }

    val primaryBibleReferenceHorizontalAlignment = when (
        if (isLowerThird) appSettings.bibleSettings.primaryReferenceLowerThirdHorizontalAlignment
        else appSettings.bibleSettings.primaryReferenceHorizontalAlignment
    ) {
        Constants.LEFT -> TextAlign.Start
        Constants.RIGHT -> TextAlign.End
        else -> TextAlign.Center
    }

    val secondaryBibleHorizontalAlignment = when (
        if (isLowerThird) appSettings.bibleSettings.secondaryBibleLowerThirdHorizontalAlignment
        else appSettings.bibleSettings.secondaryBibleHorizontalAlignment
    ) {
        Constants.LEFT -> TextAlign.Start
        Constants.RIGHT -> TextAlign.End
        else -> TextAlign.Center
    }

    val secondaryBibleReferenceHorizontalAlignment = when (
        if (isLowerThird) appSettings.bibleSettings.secondaryReferenceLowerThirdHorizontalAlignment
        else appSettings.bibleSettings.secondaryReferenceHorizontalAlignment
    ) {
        Constants.LEFT -> TextAlign.Start
        Constants.RIGHT -> TextAlign.End
        else -> TextAlign.Center
    }

    val primaryBibleReferencePosition = if (isLowerThird) bs.primaryReferenceLowerThirdPosition else bs.primaryReferencePosition
    val secondaryBibleReferencePosition = if (isLowerThird) bs.secondaryReferenceLowerThirdPosition else bs.secondaryReferencePosition

    // Combine vertical alignment with horizontal center
    val contentAlignment = when (appSettings.bibleSettings.verticalAlignment) {
        Constants.TOP -> Alignment.TopCenter
        Constants.BOTTOM -> Alignment.BottomCenter
        else -> Alignment.Center  // MIDDLE or default
    }

    val bgConfig = if (isLowerThird) appSettings.backgroundSettings.bibleLowerThirdBackground
    else appSettings.backgroundSettings.bibleBackground

    // Resolve effective background type/paths (handle Default → inherit from global)
    // For fill/key output: force black background, skip images/videos
    val effectiveType: String
    val effectiveImagePath: String
    val effectiveVideoPath: String
    var backgroundColor: Color
    val effectiveOpacity: Float

    if (!showBackground) {
        effectiveType = Constants.BACKGROUND_COLOR
        effectiveImagePath = ""
        effectiveVideoPath = ""
        backgroundColor = Color.Black
        effectiveOpacity = 1.0f
    } else if (bgConfig.backgroundType == Constants.BACKGROUND_DEFAULT) {
        val defaults = appSettings.backgroundSettings
        if (isLowerThird) {
            effectiveType = defaults.defaultLowerThirdBackgroundType
            effectiveImagePath = defaults.defaultLowerThirdBackgroundImage
            effectiveVideoPath = defaults.defaultLowerThirdBackgroundVideo
            backgroundColor = parseHexColor(defaults.defaultLowerThirdBackgroundColor)
            effectiveOpacity = defaults.defaultLowerThirdBackgroundOpacity
        } else {
            effectiveType = defaults.defaultBackgroundType
            effectiveImagePath = defaults.defaultBackgroundImage
            effectiveVideoPath = defaults.defaultBackgroundVideo
            backgroundColor = parseHexColor(defaults.defaultBackgroundColor)
            effectiveOpacity = defaults.defaultBackgroundOpacity
        }
    } else {
        effectiveType = bgConfig.backgroundType
        effectiveImagePath = bgConfig.backgroundImage
        effectiveVideoPath = bgConfig.backgroundVideo
        backgroundColor = parseHexColor(bgConfig.backgroundColor)
        effectiveOpacity = bgConfig.backgroundOpacity
    }

    val backgroundImageBitmap = remember(effectiveType, effectiveImagePath, isLowerThird) {
        if (effectiveType == Constants.BACKGROUND_IMAGE && effectiveImagePath.isNotEmpty()) {
            try {
                val file = File(effectiveImagePath)
                if (file.exists()) org.jetbrains.skia.Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
                else null
            } catch (_: Exception) {
                null
            }
        } else null
    }

    val useVideoBackground = effectiveType == Constants.BACKGROUND_VIDEO && effectiveVideoPath.isNotEmpty()

    val bgModifier: Modifier = when {
        effectiveType == Constants.BACKGROUND_TRANSPARENT -> Modifier
        effectiveType == Constants.BACKGROUND_GRADIENT -> Modifier
        useVideoBackground -> Modifier.background(Color.Black) // video rendered as overlay
        effectiveType == Constants.BACKGROUND_IMAGE && backgroundImageBitmap != null ->
            Modifier.alpha(effectiveOpacity).paint(painter = BitmapPainter(backgroundImageBitmap), contentScale = ContentScale.Crop)

        effectiveType == Constants.BACKGROUND_IMAGE ->
            Modifier.background(Color.Black)

        else ->
            Modifier.background(backgroundColor.copy(alpha = effectiveOpacity))
    }

    BoxWithConstraints(
        modifier.fillMaxSize()
            .graphicsLayer { alpha = transitionAlpha }
            .then(if (!isLowerThird) bgModifier else Modifier)
    ) {
        if (useVideoBackground && !isLowerThird) {
            LoopingVideoBackground(
                videoPath = effectiveVideoPath,
                modifier = Modifier.fillMaxSize().alpha(effectiveOpacity)
            )
        }
        val density = LocalDensity.current
        val widthScale = with(density) { maxWidth.toPx() / 1920f }
        val heightScale = with(density) { maxHeight.toPx() / 1080f }
        val scaleFactor = min(widthScale, heightScale).coerceIn(0.5f, 3.0f)

        // Scale shadow to be visible at projection resolution
        fun scaleElementShadow(color: String, size: Int, opacity: Int): Shadow {
            val base = parseHexColor(color)
            val mul = size / 100f
            val alpha = (opacity / 100f).coerceIn(0f, 1f)
            return Shadow(
                color = base.copy(alpha = alpha),
                offset = Offset(6f * scaleFactor * mul, 6f * scaleFactor * mul),
                blurRadius = 12f * scaleFactor * mul
            )
        }
        val primaryBibleTextStyleScaled = if (pShadow)
            primaryBibleTextStyle.copy(shadow = scaleElementShadow(
                if (isLowerThird) bs.primaryBibleLowerThirdShadowColor else bs.primaryBibleShadowColor,
                if (isLowerThird) bs.primaryBibleLowerThirdShadowSize else bs.primaryBibleShadowSize,
                if (isLowerThird) bs.primaryBibleLowerThirdShadowOpacity else bs.primaryBibleShadowOpacity
            )) else primaryBibleTextStyle
        val primaryReferenceTextStyleScaled = if (prShadow)
            primaryReferenceTextStyle.copy(shadow = scaleElementShadow(
                if (isLowerThird) bs.primaryReferenceLowerThirdShadowColor else bs.primaryReferenceShadowColor,
                if (isLowerThird) bs.primaryReferenceLowerThirdShadowSize else bs.primaryReferenceShadowSize,
                if (isLowerThird) bs.primaryReferenceLowerThirdShadowOpacity else bs.primaryReferenceShadowOpacity
            )) else primaryReferenceTextStyle
        val secondaryBibleTextStyleScaled = if (sShadow)
            secondaryBibleTextStyle.copy(shadow = scaleElementShadow(
                if (isLowerThird) bs.secondaryBibleLowerThirdShadowColor else bs.secondaryBibleShadowColor,
                if (isLowerThird) bs.secondaryBibleLowerThirdShadowSize else bs.secondaryBibleShadowSize,
                if (isLowerThird) bs.secondaryBibleLowerThirdShadowOpacity else bs.secondaryBibleShadowOpacity
            )) else secondaryBibleTextStyle
        val secondaryReferenceTextStyleScaled = if (srShadow)
            secondaryReferenceTextStyle.copy(shadow = scaleElementShadow(
                if (isLowerThird) bs.secondaryReferenceLowerThirdShadowColor else bs.secondaryReferenceShadowColor,
                if (isLowerThird) bs.secondaryReferenceLowerThirdShadowSize else bs.secondaryReferenceShadowSize,
                if (isLowerThird) bs.secondaryReferenceLowerThirdShadowOpacity else bs.secondaryReferenceShadowOpacity
            )) else secondaryReferenceTextStyle

        val effectivePrimaryBibleSize =
            if (isLowerThird) appSettings.bibleSettings.primaryBibleLowerThirdFontSize else appSettings.bibleSettings.primaryBibleFontSize
        val effectivePrimaryReferenceSize =
            if (isLowerThird) appSettings.bibleSettings.primaryReferenceLowerThirdFontSize else appSettings.bibleSettings.primaryReferenceFontSize
        val effectiveSecondaryBibleSize =
            if (isLowerThird) appSettings.bibleSettings.secondaryBibleLowerThirdFontSize else appSettings.bibleSettings.secondaryBibleFontSize
        val effectiveSecondaryReferenceSize =
            if (isLowerThird) appSettings.bibleSettings.secondaryReferenceLowerThirdFontSize else appSettings.bibleSettings.secondaryReferenceFontSize
        val scaledPrimaryBibleSize = (effectivePrimaryBibleSize * scaleFactor).sp
        val scaledPrimaryReferenceSize = (effectivePrimaryReferenceSize * scaleFactor).sp
        val scaledSecondaryBibleSize = (effectiveSecondaryBibleSize * scaleFactor).sp
        val scaledSecondaryReferenceSize = (effectiveSecondaryReferenceSize * scaleFactor).sp
        val leftOffSet = ((appSettings.projectionSettings.windowLeft + appSettings.bibleSettings.marginLeft) * scaleFactor).dp
        val rightOffSet = ((appSettings.projectionSettings.windowRight + appSettings.bibleSettings.marginRight) * scaleFactor).dp
        val topOffSet = ((appSettings.projectionSettings.windowTop + appSettings.bibleSettings.marginTop) * scaleFactor).dp
        val bottomOffSet = ((appSettings.projectionSettings.windowBottom + appSettings.bibleSettings.marginBottom) * scaleFactor).dp

        if (isLowerThird) {
            val lowerThirdFraction = appSettings.projectionSettings.lowerThirdHeightPercent / 100f
            // Background stretches full width at bottom third, text respects padding on top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(lowerThirdFraction)
                    .align(Alignment.BottomCenter)
                    .then(if (effectiveType == Constants.BACKGROUND_IMAGE && backgroundImageBitmap != null) Modifier else bgModifier)
            ) {
                if (effectiveType == Constants.BACKGROUND_IMAGE && backgroundImageBitmap != null) {
                    Image(
                        painter = BitmapPainter(backgroundImageBitmap),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.BottomCenter,
                        modifier = Modifier.fillMaxSize().alpha(effectiveOpacity)
                    )
                }
                if (useVideoBackground) {
                    LoopingVideoBackground(videoPath = effectiveVideoPath, modifier = Modifier.fillMaxSize().alpha(effectiveOpacity))
                }
            }
            // Gradient overlay
            if (bgConfig.gradientEnabled) {
                val gradientTop = parseHexColor(bgConfig.gradientTopColor).copy(alpha = bgConfig.gradientTopOpacity)
                val gradientBottom = parseHexColor(bgConfig.gradientBottomColor).copy(alpha = bgConfig.gradientBottomOpacity)
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

        // Outer box for padding/alignment — not animated
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = leftOffSet, end = rightOffSet, top = topOffSet, bottom = bottomOffSet),
            contentAlignment = if (isLowerThird) Alignment.BottomCenter else contentAlignment
        ) {
            val innerModifier = if (isLowerThird)
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(appSettings.projectionSettings.lowerThirdHeightPercent / 100f)
                    .align(Alignment.BottomCenter)
                    .clipToBounds()
            else
                Modifier.align(Alignment.Center)

            val textMeasurer = rememberTextMeasurer()

            // Helper: build reference text for a verse
            fun buildRefText(verse: SelectedVerse, showAbbr: Boolean): String {
                val abbr = if (showAbbr && verse.bibleAbbreviation.isNotEmpty()) verse.bibleAbbreviation else ""
                val verseRef = if (verse.verseRange.isNotEmpty()) verse.verseRange else verse.verseNumber.toString()
                return "$abbr ${verse.bookName} ${verse.chapter}:$verseRef"
            }

            // Only animate the text content — background is never inside this block
            @Composable
            fun TextContent(verses: List<SelectedVerse>) {
                val primary = verses.first()
                val secondary = verses.getOrNull(1)
                val primaryVerseRef = if (primary.verseRange.isNotEmpty()) primary.verseRange else primary.verseNumber.toString()
                val secondaryVerseRef = secondary?.let { if (it.verseRange.isNotEmpty()) it.verseRange else it.verseNumber.toString() } ?: ""
                val showSecondary =
                    secondary != null && (!isLowerThird || appSettings.bibleSettings.secondaryBibleLowerThirdEnabled)

                if (showSecondary && isLowerThird) {
                    val sec = secondary ?: return@TextContent
                    // Lower third: side-by-side Row layout (50/50) with matched auto-fit
                    BoxWithConstraints(
                        modifier = innerModifier,
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        // Pre-compute fit scales for both halves, then use the min so fonts match
                        val halfWidth = (constraints.maxWidth - with(density) { 16.dp.roundToPx() }) / 2
                        val halfConstraint = Constraints(maxWidth = halfWidth.coerceAtLeast(1))
                        // Use 90% of available height as safety margin for line spacing/shadow/padding offsets
                        val availH = (constraints.maxHeight * 0.90f).toInt()

                        val primaryRefText = buildRefText(primary, appSettings.bibleSettings.primaryShowAbbreviation)
                        val secondaryRefText = buildRefText(sec, appSettings.bibleSettings.secondaryShowAbbreviation)

                        // Binary search for the largest scale where both primary and secondary fit
                        // Scale both verse AND reference text together so everything shrinks proportionally
                        val initialPRefH = textMeasurer.measure(primaryRefText, primaryReferenceTextStyle.copy(fontFamily = primaryBibleReferenceFontStyle, fontSize = scaledPrimaryReferenceSize), constraints = halfConstraint).size.height
                        val initialSRefH = textMeasurer.measure(secondaryRefText, secondaryReferenceTextStyle.copy(fontFamily = secondaryBibleReferenceFontStyle, fontSize = scaledSecondaryReferenceSize), constraints = halfConstraint).size.height
                        val initialPH = textMeasurer.measure(primary.verseText, primaryBibleTextStyle.copy(fontFamily = primaryBibleFontStyle, fontSize = scaledPrimaryBibleSize), constraints = halfConstraint).size.height
                        val initialSH = textMeasurer.measure(sec.verseText, secondaryBibleTextStyle.copy(fontFamily = secondaryBibleFontStyle, fontSize = scaledSecondaryBibleSize), constraints = halfConstraint).size.height
                        val needsScaling = (initialPRefH + initialPH > availH) || (initialSRefH + initialSH > availH)

                        val matchedScale = if (needsScaling) {
                            binarySearchFitScale { scale ->
                                val pRefH = textMeasurer.measure(primaryRefText, primaryReferenceTextStyle.copy(fontFamily = primaryBibleReferenceFontStyle, fontSize = scaledPrimaryReferenceSize * scale), constraints = halfConstraint).size.height
                                val sRefH = textMeasurer.measure(secondaryRefText, secondaryReferenceTextStyle.copy(fontFamily = secondaryBibleReferenceFontStyle, fontSize = scaledSecondaryReferenceSize * scale), constraints = halfConstraint).size.height
                                val pH = textMeasurer.measure(primary.verseText, primaryBibleTextStyle.copy(fontFamily = primaryBibleFontStyle, fontSize = scaledPrimaryBibleSize * scale), constraints = halfConstraint).size.height
                                val sH = textMeasurer.measure(sec.verseText, secondaryBibleTextStyle.copy(fontFamily = secondaryBibleFontStyle, fontSize = scaledSecondaryBibleSize * scale), constraints = halfConstraint).size.height
                                (pRefH + pH <= availH) && (sRefH + sH <= availH)
                            }
                        } else 1f
                        val pBibleSize = scaledPrimaryBibleSize * matchedScale
                        val sBibleSize = scaledSecondaryBibleSize * matchedScale
                        // Use the smaller of the two so both sides display at the same visual size
                        val matchedBibleSize = if (pBibleSize.value <= sBibleSize.value) pBibleSize else sBibleSize
                        val scaledPrimaryRefSize = scaledPrimaryReferenceSize * matchedScale
                        val scaledSecondaryRefSize = scaledSecondaryReferenceSize * matchedScale

                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Left half: primary bible
                            Column(Modifier.weight(1f).fillMaxHeight().wrapContentHeight(Alignment.Bottom)) {
                                if (primaryBibleReferencePosition == Constants.POSITION_ABOVE) {
                                    Text(modifier = Modifier.fillMaxWidth(), textAlign = primaryBibleReferenceHorizontalAlignment, fontFamily = primaryBibleReferenceFontStyle, fontSize = scaledPrimaryRefSize, text = primaryRefText, color = primaryBibleReferenceTextColor, style = primaryReferenceTextStyleScaled)
                                }
                                Text(modifier = Modifier.fillMaxWidth(), textAlign = primaryBibleHorizontalAlignment, fontFamily = primaryBibleFontStyle, fontSize = matchedBibleSize, text = primary.verseText, color = primaryBibleTextColor, style = primaryBibleTextStyleScaled)
                                if (primaryBibleReferencePosition == Constants.POSITION_BELOW) {
                                    Text(modifier = Modifier.fillMaxWidth(), textAlign = primaryBibleReferenceHorizontalAlignment, fontFamily = primaryBibleReferenceFontStyle, fontSize = scaledPrimaryRefSize, text = primaryRefText, color = primaryBibleReferenceTextColor, style = primaryReferenceTextStyleScaled)
                                }
                            }
                            // Right half: secondary bible
                            Column(Modifier.weight(1f).fillMaxHeight().wrapContentHeight(Alignment.Bottom)) {
                                if (secondaryBibleReferencePosition == Constants.POSITION_ABOVE) {
                                    Text(modifier = Modifier.fillMaxWidth(), textAlign = secondaryBibleReferenceHorizontalAlignment, fontFamily = secondaryBibleReferenceFontStyle, fontSize = scaledSecondaryRefSize, text = secondaryRefText, color = secondaryBibleReferenceTextColor, style = secondaryReferenceTextStyleScaled)
                                }
                                Text(modifier = Modifier.fillMaxWidth(), textAlign = secondaryBibleHorizontalAlignment, fontFamily = secondaryBibleFontStyle, fontSize = matchedBibleSize, text = sec.verseText, color = secondaryBibleTextColor, style = secondaryBibleTextStyleScaled)
                                if (secondaryBibleReferencePosition == Constants.POSITION_BELOW) {
                                    Text(modifier = Modifier.fillMaxWidth(), textAlign = secondaryBibleReferenceHorizontalAlignment, fontFamily = secondaryBibleReferenceFontStyle, fontSize = scaledSecondaryRefSize, text = secondaryRefText, color = secondaryBibleReferenceTextColor, style = secondaryReferenceTextStyleScaled)
                                }
                            }
                        }
                    }
                } else if (showSecondary) {
                    // Parallel layout: rigid 50/50 split with matched auto-fit
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val widthConstraint = Constraints(maxWidth = constraints.maxWidth)
                        val halfH = constraints.maxHeight / 2

                        val primaryRefText = buildRefText(primary, appSettings.bibleSettings.primaryShowAbbreviation)
                        val primaryRefH = textMeasurer.measure(primaryRefText, primaryReferenceTextStyle.copy(fontFamily = primaryBibleReferenceFontStyle, fontSize = scaledPrimaryReferenceSize), constraints = widthConstraint).size.height
                        val secondaryRefText = buildRefText(secondary, appSettings.bibleSettings.secondaryShowAbbreviation)
                        val secondaryRefH = textMeasurer.measure(secondaryRefText, secondaryReferenceTextStyle.copy(fontFamily = secondaryBibleReferenceFontStyle, fontSize = scaledSecondaryReferenceSize), constraints = widthConstraint).size.height

                        // Binary search for the largest scale where both primary and secondary fit their halves
                        val initialPH = textMeasurer.measure(primary.verseText, primaryBibleTextStyle.copy(fontFamily = primaryBibleFontStyle, fontSize = scaledPrimaryBibleSize), constraints = widthConstraint).size.height
                        val initialSH = textMeasurer.measure(secondary.verseText, secondaryBibleTextStyle.copy(fontFamily = secondaryBibleFontStyle, fontSize = scaledSecondaryBibleSize), constraints = widthConstraint).size.height
                        val needsScaling = (primaryRefH + initialPH > halfH) || (secondaryRefH + initialSH > halfH)

                        val matchedScale = if (needsScaling) {
                            binarySearchFitScale { scale ->
                                val pH = textMeasurer.measure(primary.verseText, primaryBibleTextStyle.copy(fontFamily = primaryBibleFontStyle, fontSize = scaledPrimaryBibleSize * scale), constraints = widthConstraint).size.height
                                val sH = textMeasurer.measure(secondary.verseText, secondaryBibleTextStyle.copy(fontFamily = secondaryBibleFontStyle, fontSize = scaledSecondaryBibleSize * scale), constraints = widthConstraint).size.height
                                (primaryRefH + pH <= halfH) && (secondaryRefH + sH <= halfH)
                            }
                        } else 1f
                        val pBibleSize = scaledPrimaryBibleSize * matchedScale
                        val sBibleSize = scaledSecondaryBibleSize * matchedScale
                        // Use the smaller of the two so both halves display at the same visual size
                        val matchedBibleSize = if (pBibleSize.value <= sBibleSize.value) pBibleSize else sBibleSize

                        Column(modifier = Modifier.fillMaxSize()) {
                            // ── Top half: primary bible ──
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = contentAlignment
                            ) {
                                Column(Modifier.fillMaxWidth().wrapContentHeight()) {
                                    if (primaryBibleReferencePosition == Constants.POSITION_ABOVE) {
                                        Text(modifier = Modifier.fillMaxWidth(), textAlign = primaryBibleReferenceHorizontalAlignment, fontFamily = primaryBibleReferenceFontStyle, fontSize = scaledPrimaryReferenceSize, text = primaryRefText, color = primaryBibleReferenceTextColor, style = primaryReferenceTextStyleScaled)
                                    }
                                    Text(modifier = Modifier.fillMaxWidth(), textAlign = primaryBibleHorizontalAlignment, fontFamily = primaryBibleFontStyle, fontSize = matchedBibleSize, text = primary.verseText, color = primaryBibleTextColor, style = primaryBibleTextStyleScaled)
                                    if (primaryBibleReferencePosition == Constants.POSITION_BELOW) {
                                        Text(modifier = Modifier.fillMaxWidth(), textAlign = primaryBibleReferenceHorizontalAlignment, fontFamily = primaryBibleReferenceFontStyle, fontSize = scaledPrimaryReferenceSize, text = primaryRefText, color = primaryBibleReferenceTextColor, style = primaryReferenceTextStyleScaled)
                                    }
                                }
                            }
                            // ── Bottom half: secondary bible ──
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = contentAlignment
                            ) {
                                Column(Modifier.fillMaxWidth().wrapContentHeight()) {
                                    if (secondaryBibleReferencePosition == Constants.POSITION_ABOVE) {
                                        Text(modifier = Modifier.fillMaxWidth(), textAlign = secondaryBibleReferenceHorizontalAlignment, fontFamily = secondaryBibleReferenceFontStyle, fontSize = scaledSecondaryReferenceSize, text = secondaryRefText, color = secondaryBibleReferenceTextColor, style = secondaryReferenceTextStyleScaled)
                                    }
                                    Text(modifier = Modifier.fillMaxWidth(), textAlign = secondaryBibleHorizontalAlignment, fontFamily = secondaryBibleFontStyle, fontSize = matchedBibleSize, text = secondary.verseText, color = secondaryBibleTextColor, style = secondaryBibleTextStyleScaled)
                                    if (secondaryBibleReferencePosition == Constants.POSITION_BELOW) {
                                        Text(modifier = Modifier.fillMaxWidth(), textAlign = secondaryBibleReferenceHorizontalAlignment, fontFamily = secondaryBibleReferenceFontStyle, fontSize = scaledSecondaryReferenceSize, text = secondaryRefText, color = secondaryBibleReferenceTextColor, style = secondaryReferenceTextStyleScaled)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Original single-column layout (no secondary, or lower-third)
                    BoxWithConstraints(
                        modifier = innerModifier,
                        contentAlignment = if (isLowerThird) Alignment.BottomCenter else contentAlignment
                    ) {
                        // Auto-scale bible text if it overflows the available height
                        val widthConstraint = Constraints(maxWidth = constraints.maxWidth)
                        val primaryRefText = buildRefText(primary, appSettings.bibleSettings.primaryShowAbbreviation)
                        val primaryRefH = textMeasurer.measure(
                            text = primaryRefText,
                            style = primaryReferenceTextStyle.copy(fontFamily = primaryBibleReferenceFontStyle, fontSize = scaledPrimaryReferenceSize),
                            constraints = widthConstraint
                        ).size.height
                        val primaryVerseH = textMeasurer.measure(
                            text = primary.verseText,
                            style = primaryBibleTextStyle.copy(fontFamily = primaryBibleFontStyle, fontSize = scaledPrimaryBibleSize),
                            constraints = widthConstraint
                        ).size.height

                        val secondaryRefH = if (showSecondary && secondary != null) {
                            val secRefText = buildRefText(secondary, appSettings.bibleSettings.secondaryShowAbbreviation)
                            textMeasurer.measure(
                                text = secRefText,
                                style = secondaryReferenceTextStyle.copy(fontFamily = secondaryBibleReferenceFontStyle, fontSize = scaledSecondaryReferenceSize),
                                constraints = widthConstraint
                            ).size.height
                        } else 0
                        val secondaryVerseH = if (showSecondary && secondary != null) {
                            textMeasurer.measure(
                                text = secondary.verseText,
                                style = secondaryBibleTextStyle.copy(fontFamily = secondaryBibleFontStyle, fontSize = scaledSecondaryBibleSize),
                                constraints = widthConstraint
                            ).size.height
                        } else 0

                        val totalH = primaryRefH + primaryVerseH + secondaryRefH + secondaryVerseH
                        val fixedH = primaryRefH + secondaryRefH
                        val maxH = constraints.maxHeight
                        val fitScale = if (totalH > maxH) {
                            binarySearchFitScale { scale ->
                                val pH = textMeasurer.measure(primary.verseText, primaryBibleTextStyle.copy(fontFamily = primaryBibleFontStyle, fontSize = scaledPrimaryBibleSize * scale), constraints = widthConstraint).size.height
                                val sH = if (showSecondary && secondary != null) {
                                    textMeasurer.measure(secondary.verseText, secondaryBibleTextStyle.copy(fontFamily = secondaryBibleFontStyle, fontSize = scaledSecondaryBibleSize * scale), constraints = widthConstraint).size.height
                                } else 0
                                fixedH + pH + sH <= maxH
                            }
                        } else 1f
                        val fittedPrimaryBibleSize = scaledPrimaryBibleSize * fitScale
                        val fittedSecondaryBibleSize = scaledSecondaryBibleSize * fitScale
                        // Use the smaller so both primary and secondary display at the same visual size
                        val matchedFittedSize = if (showSecondary && secondary != null && fittedSecondaryBibleSize.value < fittedPrimaryBibleSize.value) fittedSecondaryBibleSize else fittedPrimaryBibleSize

                        Column(
                            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                            verticalArrangement = if (isLowerThird) Arrangement.Bottom else Arrangement.Top
                        ) {
                            if (primaryBibleReferencePosition == Constants.POSITION_ABOVE) {
                                val bookNameOrAbbr = if (appSettings.bibleSettings.primaryShowAbbreviation && primary.bibleAbbreviation.isNotEmpty()) primary.bibleAbbreviation else ""
                                Text(modifier = Modifier.fillMaxWidth(), textAlign = primaryBibleReferenceHorizontalAlignment, fontFamily = primaryBibleReferenceFontStyle, fontSize = scaledPrimaryReferenceSize, text = "$bookNameOrAbbr ${primary.bookName} ${primary.chapter}:$primaryVerseRef", color = primaryBibleReferenceTextColor, style = primaryReferenceTextStyleScaled)
                            }
                            Text(modifier = Modifier.fillMaxWidth(), textAlign = primaryBibleHorizontalAlignment, fontFamily = primaryBibleFontStyle, fontSize = matchedFittedSize, text = primary.verseText, color = primaryBibleTextColor, style = primaryBibleTextStyleScaled)
                            if (primaryBibleReferencePosition == Constants.POSITION_BELOW) {
                                val bookNameOrAbbr = if (appSettings.bibleSettings.primaryShowAbbreviation && primary.bibleAbbreviation.isNotEmpty()) primary.bibleAbbreviation else ""
                                Text(modifier = Modifier.fillMaxWidth(), textAlign = primaryBibleReferenceHorizontalAlignment, fontFamily = primaryBibleReferenceFontStyle, fontSize = scaledPrimaryReferenceSize, text = "$bookNameOrAbbr ${primary.bookName} ${primary.chapter}:$primaryVerseRef", color = primaryBibleReferenceTextColor, style = primaryReferenceTextStyleScaled)
                            }
                            if (showSecondary && secondary != null) {
                                if (secondaryBibleReferencePosition == Constants.POSITION_ABOVE) {
                                    val bookNameOrAbbr = if (appSettings.bibleSettings.secondaryShowAbbreviation && secondary.bibleAbbreviation.isNotEmpty()) secondary.bibleAbbreviation else ""
                                    Text(modifier = Modifier.fillMaxWidth(), textAlign = secondaryBibleReferenceHorizontalAlignment, fontFamily = secondaryBibleReferenceFontStyle, fontSize = scaledSecondaryReferenceSize, text = "$bookNameOrAbbr ${secondary.bookName} ${secondary.chapter}:$secondaryVerseRef", color = secondaryBibleReferenceTextColor, style = secondaryReferenceTextStyleScaled)
                                }
                                Text(modifier = Modifier.fillMaxWidth(), textAlign = secondaryBibleHorizontalAlignment, fontFamily = secondaryBibleFontStyle, fontSize = matchedFittedSize, text = secondary.verseText, color = secondaryBibleTextColor, style = secondaryBibleTextStyleScaled)
                                if (secondaryBibleReferencePosition == Constants.POSITION_BELOW) {
                                    val bookNameOrAbbr = if (appSettings.bibleSettings.secondaryShowAbbreviation && secondary.bibleAbbreviation.isNotEmpty()) secondary.bibleAbbreviation else ""
                                    Text(modifier = Modifier.fillMaxWidth(), textAlign = secondaryBibleReferenceHorizontalAlignment, fontFamily = secondaryBibleReferenceFontStyle, fontSize = scaledSecondaryReferenceSize, text = "$bookNameOrAbbr ${secondary.bookName} ${secondary.chapter}:$secondaryVerseRef", color = secondaryBibleReferenceTextColor, style = secondaryReferenceTextStyleScaled)
                                }
                            }
                        }
                    }
                }
            }

            if (crossfadeEnabled || appSettings.bibleSettings.fadeIn || appSettings.bibleSettings.fadeOut) {
                // Transition system:
                // - Fade in: first appearance fades from transparent
                // - Crossfade: switching verses blends old/new simultaneously
                // - Fade out: handled externally when clearing display
                val bs = appSettings.bibleSettings
                val duration = bs.transitionDuration.toInt().coerceAtLeast(100)
                val isCrossfade = crossfadeEnabled
                var displayedCurrent by remember { mutableStateOf(selectedVerses) }
                var displayedPrevious by remember { mutableStateOf<List<SelectedVerse>>(emptyList()) }
                var currentAlpha by remember { mutableStateOf(if (bs.fadeIn) 0f else 1f) }
                var previousAlpha by remember { mutableStateOf(0f) }
                val pendingQueue = remember { kotlinx.coroutines.channels.Channel<List<SelectedVerse>>(kotlinx.coroutines.channels.Channel.CONFLATED) }

                // Fade in on first composition
                LaunchedEffect(Unit) {
                    if (bs.fadeIn && currentAlpha < 1f) {
                        val anim = Animatable(0f)
                        anim.animateTo(1f, tween(durationMillis = duration)) {
                            currentAlpha = this.value
                        }
                        currentAlpha = 1f
                    }
                }

                // Queue verse changes
                LaunchedEffect(selectedVerses) {
                    if (displayedCurrent != selectedVerses) {
                        pendingQueue.send(selectedVerses)
                    }
                }

                // Process verse switches (crossfade between verses)
                LaunchedEffect(Unit) {
                    for (nextVerses in pendingQueue) {
                        if (displayedCurrent == nextVerses) continue

                        if (isCrossfade) {
                            // Crossfade: both layers animate simultaneously
                            displayedPrevious = displayedCurrent
                            displayedCurrent = nextVerses
                            previousAlpha = 1f
                            currentAlpha = 0f
                            val anim = Animatable(0f)
                            anim.animateTo(1f, tween(durationMillis = duration)) {
                                currentAlpha = this.value
                                previousAlpha = 1f - this.value
                            }
                        } else {
                            // No crossfade — just swap instantly
                            displayedCurrent = nextVerses
                        }
                        currentAlpha = 1f
                        previousAlpha = 0f
                        displayedPrevious = emptyList()
                    }
                }

                // transitionAlpha handles fade out (driven from main.kt when clearing display)
                Box(modifier = Modifier.matchParentSize().graphicsLayer { alpha = transitionAlpha }) {
                    if (displayedPrevious.isNotEmpty() && previousAlpha > 0f) {
                        Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = previousAlpha }) {
                            TextContent(displayedPrevious)
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = currentAlpha }) {
                        TextContent(displayedCurrent)
                    }
                }
            } else {
                Box(modifier = Modifier.graphicsLayer { alpha = transitionAlpha }) {
                    TextContent(selectedVerses)
                }
            }
        }
    }
}