package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.composables.LoopingVideoBackground
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.min

@Composable
fun BiblePresenter(
    modifier: Modifier = Modifier,
    selectedVerses: List<SelectedVerse>,
    appSettings: AppSettings,
    isLowerThird: Boolean = false,
    outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
) {
    val isFillOrKey = outputRole == Constants.OUTPUT_ROLE_FILL || outputRole == Constants.OUTPUT_ROLE_KEY
    val primaryBibleFontStyle = remember(appSettings.bibleSettings.primaryBibleFontType) {
        systemFontFamilyOrDefault(appSettings.bibleSettings.primaryBibleFontType)
    }
    val primaryBibleReferenceFontStyle = remember(appSettings.bibleSettings.primaryReferenceFontType) {
        systemFontFamilyOrDefault(appSettings.bibleSettings.primaryReferenceFontType)
    }

    val secondaryBibleFontStyle = remember(appSettings.bibleSettings.secondaryBibleFontType) {
        systemFontFamilyOrDefault(appSettings.bibleSettings.secondaryBibleFontType)
    }
    val secondaryBibleReferenceFontStyle = remember(appSettings.bibleSettings.secondaryReferenceFontType) {
        systemFontFamilyOrDefault(appSettings.bibleSettings.secondaryReferenceFontType)
    }

    val primaryBible = selectedVerses.first()
    val secondaryBible = selectedVerses.getOrNull(1)

    val primaryBibleTextColor = remember(appSettings.bibleSettings.primaryBibleColor) {
        parseHexColor(appSettings.bibleSettings.primaryBibleColor)
    }
    val primaryBibleReferenceTextColor = remember(appSettings.bibleSettings.primaryReferenceColor) {
        parseHexColor(appSettings.bibleSettings.primaryReferenceColor)
    }
    val secondaryBibleTextColor = remember(appSettings.bibleSettings.secondaryBibleColor) {
        parseHexColor(appSettings.bibleSettings.secondaryBibleColor)
    }
    val secondaryBibleReferenceTextColor = remember(appSettings.bibleSettings.secondaryReferenceColor) {
        parseHexColor(appSettings.bibleSettings.secondaryReferenceColor)
    }

    // Text styles from settings
    val primaryBibleTextStyle = TextStyle(
        fontWeight = if (appSettings.bibleSettings.primaryBibleBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (appSettings.bibleSettings.primaryBibleItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (appSettings.bibleSettings.primaryBibleUnderline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (appSettings.bibleSettings.primaryBibleShadow) Shadow(
            color = Color.Black.copy(alpha = 0.7f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        ) else null
    )
    val primaryReferenceTextStyle = TextStyle(
        fontWeight = if (appSettings.bibleSettings.primaryReferenceBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (appSettings.bibleSettings.primaryReferenceItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (appSettings.bibleSettings.primaryReferenceUnderline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (appSettings.bibleSettings.primaryReferenceShadow) Shadow(
            color = Color.Black.copy(alpha = 0.7f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        ) else null
    )
    val secondaryBibleTextStyle = TextStyle(
        fontWeight = if (appSettings.bibleSettings.secondaryBibleBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (appSettings.bibleSettings.secondaryBibleItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (appSettings.bibleSettings.secondaryBibleUnderline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (appSettings.bibleSettings.secondaryBibleShadow) Shadow(
            color = Color.Black.copy(alpha = 0.7f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        ) else null
    )
    val secondaryReferenceTextStyle = TextStyle(
        fontWeight = if (appSettings.bibleSettings.secondaryReferenceBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (appSettings.bibleSettings.secondaryReferenceItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (appSettings.bibleSettings.secondaryReferenceUnderline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (appSettings.bibleSettings.secondaryReferenceShadow) Shadow(
            color = Color.Black.copy(alpha = 0.7f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        ) else null
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

    val primaryBibleReferencePosition = appSettings.bibleSettings.primaryReferencePosition
    val secondaryBibleReferencePosition = appSettings.bibleSettings.secondaryReferencePosition

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

    if (isFillOrKey) {
        effectiveType = Constants.BACKGROUND_COLOR
        effectiveImagePath = ""
        effectiveVideoPath = ""
        backgroundColor = Color.Black
    } else if (bgConfig.backgroundType == Constants.BACKGROUND_DEFAULT) {
        val defaults = appSettings.backgroundSettings
        if (isLowerThird) {
            effectiveType = defaults.defaultLowerThirdBackgroundType
            effectiveImagePath = defaults.defaultLowerThirdBackgroundImage
            effectiveVideoPath = defaults.defaultLowerThirdBackgroundVideo
            backgroundColor = parseHexColor(defaults.defaultLowerThirdBackgroundColor)
        } else {
            effectiveType = defaults.defaultBackgroundType
            effectiveImagePath = defaults.defaultBackgroundImage
            effectiveVideoPath = defaults.defaultBackgroundVideo
            backgroundColor = parseHexColor(defaults.defaultBackgroundColor)
        }
    } else {
        effectiveType = bgConfig.backgroundType
        effectiveImagePath = bgConfig.backgroundImage
        effectiveVideoPath = bgConfig.backgroundVideo
        backgroundColor = parseHexColor(bgConfig.backgroundColor)
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
            Modifier.paint(painter = BitmapPainter(backgroundImageBitmap), contentScale = ContentScale.Crop)

        effectiveType == Constants.BACKGROUND_IMAGE ->
            Modifier.background(Color.Black)

        else ->
            Modifier.background(backgroundColor)
    }

    BoxWithConstraints(
        modifier.fillMaxSize()
            .then(if (!isLowerThird) bgModifier else Modifier)
    ) {
        if (useVideoBackground && !isLowerThird) {
            LoopingVideoBackground(
                videoPath = effectiveVideoPath,
                modifier = Modifier.fillMaxSize()
            )
        }
        val density = LocalDensity.current
        val widthScale = with(density) { maxWidth.toPx() / 1920f }
        val heightScale = with(density) { maxHeight.toPx() / 1080f }
        val scaleFactor = min(widthScale, heightScale).coerceIn(0.5f, 3.0f)

        // Scale shadow to be visible at projection resolution
        val scaledShadow = Shadow(
            color = Color.Black.copy(alpha = 0.9f),
            offset = Offset(6f * scaleFactor, 6f * scaleFactor),
            blurRadius = 12f * scaleFactor
        )
        val primaryBibleTextStyleScaled = if (appSettings.bibleSettings.primaryBibleShadow)
            primaryBibleTextStyle.copy(shadow = scaledShadow) else primaryBibleTextStyle
        val primaryReferenceTextStyleScaled = if (appSettings.bibleSettings.primaryReferenceShadow)
            primaryReferenceTextStyle.copy(shadow = scaledShadow) else primaryReferenceTextStyle
        val secondaryBibleTextStyleScaled = if (appSettings.bibleSettings.secondaryBibleShadow)
            secondaryBibleTextStyle.copy(shadow = scaledShadow) else secondaryBibleTextStyle
        val secondaryReferenceTextStyleScaled = if (appSettings.bibleSettings.secondaryReferenceShadow)
            secondaryReferenceTextStyle.copy(shadow = scaledShadow) else secondaryReferenceTextStyle

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
                    androidx.compose.foundation.Image(
                        painter = BitmapPainter(backgroundImageBitmap),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.BottomCenter,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (useVideoBackground) {
                    LoopingVideoBackground(videoPath = effectiveVideoPath, modifier = Modifier.fillMaxSize())
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
                        val availH = constraints.maxHeight

                        val primaryRefText = buildRefText(primary, appSettings.bibleSettings.primaryShowAbbreviation)
                        val primaryRefH = textMeasurer.measure(primaryRefText, primaryReferenceTextStyle.copy(fontFamily = primaryBibleReferenceFontStyle, fontSize = scaledPrimaryReferenceSize), constraints = halfConstraint).size.height
                        val primaryVerseH = textMeasurer.measure(primary.verseText, primaryBibleTextStyle.copy(fontFamily = primaryBibleFontStyle, fontSize = scaledPrimaryBibleSize), constraints = halfConstraint).size.height
                        val pFitScale = if (primaryRefH + primaryVerseH > availH) {
                            ((availH - primaryRefH).coerceAtLeast(1).toFloat() / primaryVerseH).coerceAtLeast(0.3f)
                        } else 1f

                        val secondaryRefText = buildRefText(sec, appSettings.bibleSettings.secondaryShowAbbreviation)
                        val secondaryRefH = textMeasurer.measure(secondaryRefText, secondaryReferenceTextStyle.copy(fontFamily = secondaryBibleReferenceFontStyle, fontSize = scaledSecondaryReferenceSize), constraints = halfConstraint).size.height
                        val secondaryVerseH = textMeasurer.measure(sec.verseText, secondaryBibleTextStyle.copy(fontFamily = secondaryBibleFontStyle, fontSize = scaledSecondaryBibleSize), constraints = halfConstraint).size.height
                        val sFitScale = if (secondaryRefH + secondaryVerseH > availH) {
                            ((availH - secondaryRefH).coerceAtLeast(1).toFloat() / secondaryVerseH).coerceAtLeast(0.3f)
                        } else 1f

                        val matchedScale = minOf(pFitScale, sFitScale)
                        val pBibleSize = scaledPrimaryBibleSize * matchedScale
                        val sBibleSize = scaledSecondaryBibleSize * matchedScale

                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Left half: primary bible
                            Column(Modifier.weight(1f).fillMaxHeight().wrapContentHeight(Alignment.Bottom)) {
                                if (primaryBibleReferencePosition == Constants.POSITION_ABOVE) {
                                    Text(modifier = Modifier.fillMaxWidth(), textAlign = primaryBibleReferenceHorizontalAlignment, fontFamily = primaryBibleReferenceFontStyle, fontSize = scaledPrimaryReferenceSize, text = primaryRefText, color = primaryBibleReferenceTextColor, style = primaryReferenceTextStyleScaled)
                                }
                                Text(modifier = Modifier.fillMaxWidth(), textAlign = primaryBibleHorizontalAlignment, fontFamily = primaryBibleFontStyle, fontSize = pBibleSize, text = primary.verseText, color = primaryBibleTextColor, style = primaryBibleTextStyleScaled)
                                if (primaryBibleReferencePosition == Constants.POSITION_BELOW) {
                                    Text(modifier = Modifier.fillMaxWidth(), textAlign = primaryBibleReferenceHorizontalAlignment, fontFamily = primaryBibleReferenceFontStyle, fontSize = scaledPrimaryReferenceSize, text = primaryRefText, color = primaryBibleReferenceTextColor, style = primaryReferenceTextStyleScaled)
                                }
                            }
                            // Right half: secondary bible
                            Column(Modifier.weight(1f).fillMaxHeight().wrapContentHeight(Alignment.Bottom)) {
                                if (secondaryBibleReferencePosition == Constants.POSITION_ABOVE) {
                                    Text(modifier = Modifier.fillMaxWidth(), textAlign = secondaryBibleReferenceHorizontalAlignment, fontFamily = secondaryBibleReferenceFontStyle, fontSize = scaledSecondaryReferenceSize, text = secondaryRefText, color = secondaryBibleReferenceTextColor, style = secondaryReferenceTextStyleScaled)
                                }
                                Text(modifier = Modifier.fillMaxWidth(), textAlign = secondaryBibleHorizontalAlignment, fontFamily = secondaryBibleFontStyle, fontSize = sBibleSize, text = sec.verseText, color = secondaryBibleTextColor, style = secondaryBibleTextStyleScaled)
                                if (secondaryBibleReferencePosition == Constants.POSITION_BELOW) {
                                    Text(modifier = Modifier.fillMaxWidth(), textAlign = secondaryBibleReferenceHorizontalAlignment, fontFamily = secondaryBibleReferenceFontStyle, fontSize = scaledSecondaryReferenceSize, text = secondaryRefText, color = secondaryBibleReferenceTextColor, style = secondaryReferenceTextStyleScaled)
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
                        val primaryVerseH = textMeasurer.measure(primary.verseText, primaryBibleTextStyle.copy(fontFamily = primaryBibleFontStyle, fontSize = scaledPrimaryBibleSize), constraints = widthConstraint).size.height
                        val pFitScale = if (primaryRefH + primaryVerseH > halfH) {
                            ((halfH - primaryRefH).coerceAtLeast(1).toFloat() / primaryVerseH).coerceAtLeast(0.3f)
                        } else 1f

                        val secondaryRefText = buildRefText(secondary, appSettings.bibleSettings.secondaryShowAbbreviation)
                        val secondaryRefH = textMeasurer.measure(secondaryRefText, secondaryReferenceTextStyle.copy(fontFamily = secondaryBibleReferenceFontStyle, fontSize = scaledSecondaryReferenceSize), constraints = widthConstraint).size.height
                        val secondaryVerseH = textMeasurer.measure(secondary.verseText, secondaryBibleTextStyle.copy(fontFamily = secondaryBibleFontStyle, fontSize = scaledSecondaryBibleSize), constraints = widthConstraint).size.height
                        val sFitScale = if (secondaryRefH + secondaryVerseH > halfH) {
                            ((halfH - secondaryRefH).coerceAtLeast(1).toFloat() / secondaryVerseH).coerceAtLeast(0.3f)
                        } else 1f

                        val matchedScale = minOf(pFitScale, sFitScale)
                        val pBibleSize = scaledPrimaryBibleSize * matchedScale
                        val sBibleSize = scaledSecondaryBibleSize * matchedScale

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
                                    Text(modifier = Modifier.fillMaxWidth(), textAlign = primaryBibleHorizontalAlignment, fontFamily = primaryBibleFontStyle, fontSize = pBibleSize, text = primary.verseText, color = primaryBibleTextColor, style = primaryBibleTextStyleScaled)
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
                                    Text(modifier = Modifier.fillMaxWidth(), textAlign = secondaryBibleHorizontalAlignment, fontFamily = secondaryBibleFontStyle, fontSize = sBibleSize, text = secondary.verseText, color = secondaryBibleTextColor, style = secondaryBibleTextStyleScaled)
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
                        val verseH = primaryVerseH + secondaryVerseH
                        val fitScale = if (totalH > constraints.maxHeight && verseH > 0) {
                            val spaceForVerses = (constraints.maxHeight - fixedH).coerceAtLeast(1)
                            (spaceForVerses.toFloat() / verseH).coerceAtLeast(0.3f)
                        } else 1f
                        val fittedPrimaryBibleSize = scaledPrimaryBibleSize * fitScale
                        val fittedSecondaryBibleSize = scaledSecondaryBibleSize * fitScale

                        Column(
                            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                            verticalArrangement = if (isLowerThird) Arrangement.Bottom else Arrangement.Top
                        ) {
                            if (primaryBibleReferencePosition == Constants.POSITION_ABOVE) {
                                val bookNameOrAbbr = if (appSettings.bibleSettings.primaryShowAbbreviation && primary.bibleAbbreviation.isNotEmpty()) primary.bibleAbbreviation else ""
                                Text(modifier = Modifier.fillMaxWidth(), textAlign = primaryBibleReferenceHorizontalAlignment, fontFamily = primaryBibleReferenceFontStyle, fontSize = scaledPrimaryReferenceSize, text = "$bookNameOrAbbr ${primary.bookName} ${primary.chapter}:$primaryVerseRef", color = primaryBibleReferenceTextColor, style = primaryReferenceTextStyleScaled)
                            }
                            Text(modifier = Modifier.fillMaxWidth(), textAlign = primaryBibleHorizontalAlignment, fontFamily = primaryBibleFontStyle, fontSize = fittedPrimaryBibleSize, text = primary.verseText, color = primaryBibleTextColor, style = primaryBibleTextStyleScaled)
                            if (primaryBibleReferencePosition == Constants.POSITION_BELOW) {
                                val bookNameOrAbbr = if (appSettings.bibleSettings.primaryShowAbbreviation && primary.bibleAbbreviation.isNotEmpty()) primary.bibleAbbreviation else ""
                                Text(modifier = Modifier.fillMaxWidth(), textAlign = primaryBibleReferenceHorizontalAlignment, fontFamily = primaryBibleReferenceFontStyle, fontSize = scaledPrimaryReferenceSize, text = "$bookNameOrAbbr ${primary.bookName} ${primary.chapter}:$primaryVerseRef", color = primaryBibleReferenceTextColor, style = primaryReferenceTextStyleScaled)
                            }
                            if (showSecondary && secondary != null) {
                                if (secondaryBibleReferencePosition == Constants.POSITION_ABOVE) {
                                    val bookNameOrAbbr = if (appSettings.bibleSettings.secondaryShowAbbreviation && secondary.bibleAbbreviation.isNotEmpty()) secondary.bibleAbbreviation else ""
                                    Text(modifier = Modifier.fillMaxWidth(), textAlign = secondaryBibleReferenceHorizontalAlignment, fontFamily = secondaryBibleReferenceFontStyle, fontSize = scaledSecondaryReferenceSize, text = "$bookNameOrAbbr ${secondary.bookName} ${secondary.chapter}:$secondaryVerseRef", color = secondaryBibleReferenceTextColor, style = secondaryReferenceTextStyleScaled)
                                }
                                Text(modifier = Modifier.fillMaxWidth(), textAlign = secondaryBibleHorizontalAlignment, fontFamily = secondaryBibleFontStyle, fontSize = fittedSecondaryBibleSize, text = secondary.verseText, color = secondaryBibleTextColor, style = secondaryBibleTextStyleScaled)
                                if (secondaryBibleReferencePosition == Constants.POSITION_BELOW) {
                                    val bookNameOrAbbr = if (appSettings.bibleSettings.secondaryShowAbbreviation && secondary.bibleAbbreviation.isNotEmpty()) secondary.bibleAbbreviation else ""
                                    Text(modifier = Modifier.fillMaxWidth(), textAlign = secondaryBibleReferenceHorizontalAlignment, fontFamily = secondaryBibleReferenceFontStyle, fontSize = scaledSecondaryReferenceSize, text = "$bookNameOrAbbr ${secondary.bookName} ${secondary.chapter}:$secondaryVerseRef", color = secondaryBibleReferenceTextColor, style = secondaryReferenceTextStyleScaled)
                                }
                            }
                        }
                    }
                }
            }

            // Resolve animation from appSettings — no params needed from caller
            val animationType = when (appSettings.bibleSettings.animationType) {
                Constants.ANIMATION_FADE -> AnimationType.FADE
                Constants.ANIMATION_SLIDE_LEFT -> AnimationType.SLIDE_LEFT
                Constants.ANIMATION_SLIDE_RIGHT -> AnimationType.SLIDE_RIGHT
                Constants.ANIMATION_NONE -> AnimationType.NONE
                else -> AnimationType.CROSSFADE
            }
            val transitionDuration = appSettings.bibleSettings.transitionDuration.toInt()

            when (animationType) {
                AnimationType.CROSSFADE -> Crossfade(
                    targetState = selectedVerses,
                    animationSpec = tween(transitionDuration),
                    label = "BibleCrossfade"
                ) { TextContent(it) }

                AnimationType.FADE -> AnimatedContent(
                    targetState = selectedVerses,
                    transitionSpec = { fadeIn(tween(transitionDuration)) togetherWith fadeOut(tween(transitionDuration)) },
                    label = "BibleFade"
                ) { TextContent(it) }

                AnimationType.SLIDE_LEFT -> AnimatedContent(
                    targetState = selectedVerses,
                    transitionSpec = {
                        slideInHorizontally(tween(transitionDuration)) { it } togetherWith slideOutHorizontally(
                            tween(
                                transitionDuration
                            )
                        ) { -it }
                    },
                    label = "BibleSlideLeft"
                ) { TextContent(it) }

                AnimationType.SLIDE_RIGHT -> AnimatedContent(
                    targetState = selectedVerses,
                    transitionSpec = {
                        slideInHorizontally(tween(transitionDuration)) { -it } togetherWith slideOutHorizontally(
                            tween(
                                transitionDuration
                            )
                        ) { it }
                    },
                    label = "BibleSlideRight"
                ) { TextContent(it) }

                AnimationType.NONE -> TextContent(selectedVerses)
            }
        }
    }
}