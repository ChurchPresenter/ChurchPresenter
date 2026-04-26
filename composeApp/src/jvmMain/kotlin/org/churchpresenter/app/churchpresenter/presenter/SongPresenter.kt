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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import kotlin.math.min
import org.churchpresenter.app.churchpresenter.composables.LoopingVideoBackground
import org.churchpresenter.app.churchpresenter.data.AppSettings

import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.calculateAutoFitForAllSections
import org.churchpresenter.app.churchpresenter.utils.calculateAutoFitFontSize
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import org.jetbrains.skia.Image
import java.io.File

@Composable
fun SongPresenter(
    modifier: Modifier = Modifier,
    lyricSection: LyricSection,
    appSettings: AppSettings,
    isLowerThird: Boolean = false,
    outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
    transitionAlpha: Float = 1f,
    displayLineIndex: Int = -1,
    lookAheadEnabled: Boolean = false,
    allLyricSections: List<LyricSection> = emptyList(),
    displaySectionIndex: Int = -1,
    showBackground: Boolean = true,
    crossfadeEnabled: Boolean = false,
) {
    val isKey = outputRole == Constants.OUTPUT_ROLE_KEY
    val ss = appSettings.songSettings

    // Resolve font families per fullscreen / lower third
    val titleFontFamily = remember(ss.titleFontType, ss.titleLowerThirdFontType, isLowerThird) {
        systemFontFamilyOrDefault(if (isLowerThird) ss.titleLowerThirdFontType else ss.titleFontType)
    }
    val lyricsFontFamily = remember(ss.lyricsFontType, ss.lyricsLowerThirdFontType,
        ss.lookAheadFontType, ss.lowerThirdLookAheadFontType, isLowerThird, lookAheadEnabled) {
        if (lookAheadEnabled) {
            systemFontFamilyOrDefault(if (isLowerThird) ss.lowerThirdLookAheadFontType else ss.lookAheadFontType)
        } else {
            systemFontFamilyOrDefault(if (isLowerThird) ss.lyricsLowerThirdFontType else ss.lyricsFontType)
        }
    }

    // Resolve colors — key mode forces white for a proper key signal
    val titleColor = remember(ss.titleColor, ss.titleLowerThirdColor, isLowerThird, isKey) {
        if (isKey) Color.White
        else parseHexColor(if (isLowerThird) ss.titleLowerThirdColor else ss.titleColor)
    }
    val lyricsColor = remember(ss.lyricsColor, ss.lyricsLowerThirdColor,
        ss.lookAheadColor, ss.lowerThirdLookAheadColor, isLowerThird, lookAheadEnabled, isKey) {
        if (isKey) Color.White
        else if (lookAheadEnabled) {
            parseHexColor(if (isLowerThird) ss.lowerThirdLookAheadColor else ss.lookAheadColor)
        } else {
            parseHexColor(if (isLowerThird) ss.lyricsLowerThirdColor else ss.lyricsColor)
        }
    }
    // Look-ahead next section preview font settings (resolved per fullscreen / lower third)
    val laColor = remember(ss.lookAheadNextColor, ss.lowerThirdLookAheadNextColor, isLowerThird, isKey) {
        if (isKey) Color.White
        else parseHexColor(if (isLowerThird) ss.lowerThirdLookAheadNextColor else ss.lookAheadNextColor)
    }
    val laFontFamily = remember(ss.lookAheadNextFontType, ss.lowerThirdLookAheadNextFontType, isLowerThird) {
        systemFontFamilyOrDefault(if (isLowerThird) ss.lowerThirdLookAheadNextFontType else ss.lookAheadNextFontType)
    }
    val laFontSize = if (isLowerThird) ss.lowerThirdLookAheadNextFontSize else ss.lookAheadNextFontSize
    val laBold = if (isLowerThird) ss.lowerThirdLookAheadNextBold else ss.lookAheadNextBold
    val laItalic = if (isLowerThird) ss.lowerThirdLookAheadNextItalic else ss.lookAheadNextItalic
    val laUnderline = if (isLowerThird) ss.lowerThirdLookAheadNextUnderline else ss.lookAheadNextUnderline
    val laShadowEnabled = if (isLowerThird) ss.lowerThirdLookAheadNextShadow else ss.lookAheadNextShadow
    val laShadowColor = parseHexColor(if (isLowerThird) ss.lowerThirdLookAheadNextShadowColor else ss.lookAheadNextShadowColor)
    val laShadowSizeMul = (if (isLowerThird) ss.lowerThirdLookAheadNextShadowSize else ss.lookAheadNextShadowSize) / 100f
    val laShadowAlpha = ((if (isLowerThird) ss.lowerThirdLookAheadNextShadowOpacity else ss.lookAheadNextShadowOpacity) / 100f).coerceIn(0f, 1f)

    // Per-element shadow customization (resolved per fullscreen / lower third)
    fun makeSongShadow(color: String, size: Int, opacity: Int, alphaScale: Float = 0.78f): Shadow {
        val base = parseHexColor(color)
        val mul = size / 100f
        val alpha = (opacity / 100f).coerceIn(0f, 1f)
        return Shadow(
            color = base.copy(alpha = alpha * alphaScale),
            offset = Offset(2f * mul, 2f * mul),
            blurRadius = 4f * mul
        )
    }
    val titleBaseShadow = makeSongShadow(
        if (isLowerThird) ss.titleLowerThirdShadowColor else ss.titleShadowColor,
        if (isLowerThird) ss.titleLowerThirdShadowSize else ss.titleShadowSize,
        if (isLowerThird) ss.titleLowerThirdShadowOpacity else ss.titleShadowOpacity
    )
    val lyricsBaseShadow = makeSongShadow(
        if (isLowerThird) ss.lyricsLowerThirdShadowColor else ss.lyricsShadowColor,
        if (isLowerThird) ss.lyricsLowerThirdShadowSize else ss.lyricsShadowSize,
        if (isLowerThird) ss.lyricsLowerThirdShadowOpacity else ss.lyricsShadowOpacity
    )

    // Text styles derived from settings (resolved per fullscreen / lower third)
    val effectiveTitleBold = if (isLowerThird) ss.titleLowerThirdBold else ss.titleBold
    val effectiveTitleItalic = if (isLowerThird) ss.titleLowerThirdItalic else ss.titleItalic
    val effectiveTitleUnderline = if (isLowerThird) ss.titleLowerThirdUnderline else ss.titleUnderline
    val effectiveTitleShadow = if (isLowerThird) ss.titleLowerThirdShadow else ss.titleShadow
    val titleTextStyle = TextStyle(
        fontWeight = if (effectiveTitleBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (effectiveTitleItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (effectiveTitleUnderline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (effectiveTitleShadow) titleBaseShadow else null
    )
    val effectiveLyricsBold = if (lookAheadEnabled) {
        if (isLowerThird) ss.lowerThirdLookAheadBold else ss.lookAheadBold
    } else if (isLowerThird) ss.lyricsLowerThirdBold else ss.lyricsBold
    val effectiveLyricsItalic = if (lookAheadEnabled) {
        if (isLowerThird) ss.lowerThirdLookAheadItalic else ss.lookAheadItalic
    } else if (isLowerThird) ss.lyricsLowerThirdItalic else ss.lyricsItalic
    val effectiveLyricsUnderline = if (lookAheadEnabled) {
        if (isLowerThird) ss.lowerThirdLookAheadUnderline else ss.lookAheadUnderline
    } else if (isLowerThird) ss.lyricsLowerThirdUnderline else ss.lyricsUnderline
    val effectiveLyricsShadow = if (lookAheadEnabled) {
        if (isLowerThird) ss.lowerThirdLookAheadShadow else ss.lookAheadShadow
    } else if (isLowerThird) ss.lyricsLowerThirdShadow else ss.lyricsShadow
    val lyricsTextStyle = TextStyle(
        fontWeight = if (effectiveLyricsBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (effectiveLyricsItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (effectiveLyricsUnderline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (effectiveLyricsShadow) lyricsBaseShadow else null
    )
    val contentAlignment = when (appSettings.songSettings.lyricsAlignment) {
        Constants.TOP -> Alignment.TopCenter
        Constants.BOTTOM -> Alignment.BottomCenter
        else -> Alignment.Center
    }
    val lyricsHorizontalAlignment = getTextAlign(
        if (lookAheadEnabled) {
            if (isLowerThird) ss.lowerThirdLookAheadHorizontalAlignment else ss.lookAheadHorizontalAlignment
        } else {
            if (isLowerThird) ss.lyricsLowerThirdHorizontalAlignment else ss.lyricsHorizontalAlignment
        }
    )
    val titleHorizontalAlignment = getTextAlign(
        if (isLowerThird) ss.titleLowerThirdHorizontalAlignment else ss.titleHorizontalAlignment
    )
    val songNumberHorizontalAlignment = getTextAlign(
        if (isLowerThird) ss.songNumberLowerThirdHorizontalAlignment else ss.songNumberHorizontalAlignment
    )
    val bgConfig = if (isLowerThird) appSettings.backgroundSettings.songLowerThirdBackground
    else appSettings.backgroundSettings.songBackground

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
                if (file.exists()) Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
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
        useVideoBackground -> Modifier.background(Color.Black)
        effectiveType == Constants.BACKGROUND_IMAGE && backgroundImageBitmap != null ->
            Modifier.alpha(effectiveOpacity).paint(painter = BitmapPainter(backgroundImageBitmap), contentScale = ContentScale.Crop)

        effectiveType == Constants.BACKGROUND_IMAGE ->
            Modifier.background(Color.Black)

        else ->
            Modifier.background(backgroundColor.copy(alpha = effectiveOpacity))
    }

    // Fade-in on first appearance (covers background + text)
    val fadeInDuration = appSettings.songSettings.transitionDuration.toInt().coerceAtLeast(100)
    var enterAlpha by remember { mutableStateOf(if (appSettings.songSettings.fadeIn) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (appSettings.songSettings.fadeIn && enterAlpha < 1f) {
            val anim = Animatable(0f)
            anim.animateTo(1f, tween(durationMillis = fadeInDuration)) {
                enterAlpha = this.value
            }
            enterAlpha = 1f
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = transitionAlpha * enterAlpha }
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
        val titleTextStyleScaled = if (effectiveTitleShadow)
            titleTextStyle.copy(shadow = scaleElementShadow(
                if (isLowerThird) ss.titleLowerThirdShadowColor else ss.titleShadowColor,
                if (isLowerThird) ss.titleLowerThirdShadowSize else ss.titleShadowSize,
                if (isLowerThird) ss.titleLowerThirdShadowOpacity else ss.titleShadowOpacity
            )) else titleTextStyle
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
        } else if (isLowerThird) appSettings.songSettings.lyricsLowerThirdFontSize else appSettings.songSettings.lyricsFontSize
        val effectiveSongNumberFontSize =
            if (isLowerThird) appSettings.songSettings.songNumberLowerThirdFontSize else appSettings.songSettings.songNumberFontSize

        // Auto-fit: compute the largest font size that fits ALL sections without line wrapping.
        // Uses the reference 1920×1080 coordinate space (margins subtracted).
        val autoFitTextMeasurer = rememberTextMeasurer()
        val autoFitFontSize = remember(allLyricSections, isLowerThird, lookAheadEnabled, appSettings.songSettings, appSettings.projectionSettings) {
            if (allLyricSections.isEmpty()) null
            else {
                val ld = if (lookAheadEnabled) {
                    if (isLowerThird) ss.lowerThirdLookAheadLanguageDisplay else ss.lookAheadLanguageDisplay
                } else {
                    if (isLowerThird) ss.lowerThirdLanguageDisplay else ss.fullscreenLanguageDisplay
                }
                val hasBilingual = allLyricSections.any { it.secondaryLines.isNotEmpty() }
                val sideBySide = ld == Constants.SONG_LANG_BOTH &&
                        ss.bilingualLayout == Constants.BILINGUAL_SIDE_BY_SIDE && hasBilingual
                val topBottom = ld == Constants.SONG_LANG_BOTH &&
                        ss.bilingualLayout == Constants.BILINGUAL_TOP_BOTTOM && hasBilingual

                val fullWidth = 1920 - appSettings.projectionSettings.windowLeft - appSettings.projectionSettings.windowRight -
                        appSettings.songSettings.marginLeft - appSettings.songSettings.marginRight
                // In side-by-side bilingual mode, each column gets half the width
                val refWidth = if (sideBySide) fullWidth / 2 else fullWidth
                val fullHeight = if (isLowerThird) {
                    (1080 * appSettings.projectionSettings.lowerThirdHeightPercent / 100) -
                            appSettings.projectionSettings.windowTop - appSettings.projectionSettings.windowBottom -
                            appSettings.songSettings.marginTop - appSettings.songSettings.marginBottom
                } else {
                    1080 - appSettings.projectionSettings.windowTop - appSettings.projectionSettings.windowBottom -
                            appSettings.songSettings.marginTop - appSettings.songSettings.marginBottom
                }
                // In top/bottom bilingual mode, each language gets half the height
                val refHeight = if (topBottom) fullHeight / 2 else fullHeight
                val baseStyle = TextStyle(
                    fontWeight = if (effectiveLyricsBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (effectiveLyricsItalic) FontStyle.Italic else FontStyle.Normal,
                    fontFamily = lyricsFontFamily
                )
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
                    val allSecLines = allLyricSections.flatMap { it.secondaryLines }
                    allLines.indices.map { i ->
                        val nextLine = allLines.getOrElse(i + 1) { allLines[i] }
                        LyricSection(
                            lines = listOf(allLines[i], nextLine),
                            secondaryLines = if (allSecLines.isNotEmpty()) {
                                val secLine = allSecLines.getOrElse(i) { "" }
                                val secNext = allSecLines.getOrElse(i + 1) { secLine }
                                listOf(secLine, secNext)
                            } else emptyList()
                        )
                    }
                } else if (lookAheadEnabled) {
                    // Verse mode: combine full section with next section
                    allLyricSections.mapIndexed { i, section ->
                        val next = allLyricSections.getOrNull(i + 1)
                        if (next != null) {
                            section.copy(
                                lines = section.lines + next.lines,
                                secondaryLines = if (section.secondaryLines.isNotEmpty() || next.secondaryLines.isNotEmpty())
                                    section.secondaryLines + next.secondaryLines else emptyList()
                            )
                        } else section
                    }
                } else allLyricSections
                calculateAutoFitForAllSections(
                    textMeasurer = autoFitTextMeasurer,
                    sections = sectionsForFit,
                    baseStyle = baseStyle,
                    availableWidth = refWidth,
                    availableHeight = refHeight
                )
            }
        }
        // Bilingual flags for layout decisions (outside remember, always fresh)
        val langDisplay = if (lookAheadEnabled) {
            if (isLowerThird) ss.lowerThirdLookAheadLanguageDisplay else ss.lookAheadLanguageDisplay
        } else {
            if (isLowerThird) ss.lowerThirdLanguageDisplay else ss.fullscreenLanguageDisplay
        }
        val hasBilingualContent = allLyricSections.any { it.secondaryLines.isNotEmpty() }
        val isBilingualSideBySide = langDisplay == Constants.SONG_LANG_BOTH &&
                ss.bilingualLayout == Constants.BILINGUAL_SIDE_BY_SIDE && hasBilingualContent
        val isBilingualTopBottom = langDisplay == Constants.SONG_LANG_BOTH &&
                ss.bilingualLayout == Constants.BILINGUAL_TOP_BOTTOM && hasBilingualContent
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

        val leftOffSet = ((appSettings.projectionSettings.windowLeft + appSettings.songSettings.marginLeft) * scaleFactor).dp
        val rightOffSet = ((appSettings.projectionSettings.windowRight + appSettings.songSettings.marginRight) * scaleFactor).dp
        val topOffSet = ((appSettings.projectionSettings.windowTop + appSettings.songSettings.marginTop) * scaleFactor).dp
        val bottomOffSet = ((appSettings.projectionSettings.windowBottom + appSettings.songSettings.marginBottom) * scaleFactor).dp

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

        Box(
            Modifier
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
                Modifier

            // Only animate the text content — background is never inside this block
            @Composable
            fun TextContent(section: LyricSection) {
                val titleDisplay = if (isLowerThird) ss.titleLowerThirdDisplay else ss.titleDisplay
                val numberDisplay = if (isLowerThird) ss.showNumberLowerThird else ss.showNumber
                val shouldShowTitle = shouldShowText(titleDisplay, section)
                val shouldShowSongNumber = shouldShowText(numberDisplay, section) && section.songNumber > 0
                // "Configured" means not set to "None" — title/number could appear on some slides
                val titleConfigured = titleDisplay != Constants.NONE
                val numberConfigured = numberDisplay != Constants.NONE && section.songNumber > 0
                val effectiveTitlePosition = if (isLowerThird) ss.titleLowerThirdPosition else ss.titlePosition
                val effectiveSongNumberPosition = if (isLowerThird) ss.songNumberLowerThirdPosition else ss.songNumberPosition
                BoxWithConstraints(
                    modifier = innerModifier,
                    contentAlignment = if (isLowerThird) Alignment.BottomCenter else contentAlignment
                ) {

                    val allDisplayLines = section.lines
                    // Resolve per-mode settings based on fullscreen vs lower third
                    // When lookAheadEnabled, the entire screen uses lookahead's own display mode
                    val displayMode = if (lookAheadEnabled) {
                        if (isLowerThird) ss.lowerThirdLookAheadDisplayMode else ss.lookAheadDisplayMode
                    } else {
                        if (isLowerThird) ss.lowerThirdDisplayMode else ss.fullscreenDisplayMode
                    }
                    val langDisplay = if (lookAheadEnabled) {
                        if (isLowerThird) ss.lowerThirdLookAheadLanguageDisplay else ss.lookAheadLanguageDisplay
                    } else {
                        if (isLowerThird) ss.lowerThirdLanguageDisplay else ss.fullscreenLanguageDisplay
                    }

                    // Look-ahead portion uses same display mode as the screen
                    val laDisplayMode = displayMode
                    val laLangDisplay = langDisplay
                    val laIsLineMode = laDisplayMode == Constants.SONG_DISPLAY_MODE_LINE

                    val isLineMode = displayMode == Constants.SONG_DISPLAY_MODE_LINE
                    val effectiveLineIndex = if (isLineMode && displayLineIndex < 0) 0 else displayLineIndex

                    // Get next section for look-ahead
                    val nextSection: LyricSection? = if (lookAheadEnabled && displaySectionIndex >= 0) {
                        allLyricSections.getOrNull(displaySectionIndex + 1)?.takeIf { it.lines.isNotEmpty() }
                    } else null

                    // Build main display lines (current section)
                    val mainLines: List<String>
                    if (isLineMode && effectiveLineIndex >= 0 && effectiveLineIndex < allDisplayLines.size) {
                        mainLines = listOf(allDisplayLines[effectiveLineIndex])
                    } else {
                        mainLines = allDisplayLines
                    }

                    // Build look-ahead primary lines
                    val laLines: List<String> = if (nextSection != null) {
                        if (laIsLineMode) {
                            // Look-ahead = 1 line: next line after current position
                            if (isLineMode && effectiveLineIndex >= 0) {
                                // Main is line mode: if there's a next line in same section, show it; otherwise first line of next section
                                if (effectiveLineIndex + 1 < allDisplayLines.size) {
                                    listOf(allDisplayLines[effectiveLineIndex + 1])
                                } else {
                                    listOf(nextSection.lines.first())
                                }
                            } else {
                                // Main is verse mode: first line of next section
                                listOf(nextSection.lines.first())
                            }
                        } else {
                            // Look-ahead = 1 verse: all lines of next section
                            nextSection.lines
                        }
                    } else if (lookAheadEnabled && isLineMode && effectiveLineIndex >= 0 && effectiveLineIndex + 1 < allDisplayLines.size) {
                        // No next section but there's a next line in the current section
                        if (laIsLineMode) listOf(allDisplayLines[effectiveLineIndex + 1]) else emptyList()
                    } else {
                        emptyList()
                    }

                    // Combine main + look-ahead
                    val displayLines = mainLines + laLines
                    val lookAheadStartIndex = if (laLines.isNotEmpty()) mainLines.size else -1

                    // Build main secondary lines (for bilingual)
                    val mainSecondaryLines: List<String> = if (section.secondaryLines.isNotEmpty()) {
                        if (isLineMode && effectiveLineIndex >= 0 && effectiveLineIndex < section.secondaryLines.size) {
                            listOf(section.secondaryLines[effectiveLineIndex])
                        } else {
                            section.secondaryLines
                        }
                    } else emptyList()

                    // Build look-ahead secondary lines
                    val laSecondaryLines: List<String> = if (nextSection != null && nextSection.secondaryLines.isNotEmpty()) {
                        if (laIsLineMode) {
                            if (isLineMode && effectiveLineIndex >= 0) {
                                if (effectiveLineIndex + 1 < (section.secondaryLines.size)) {
                                    listOf(section.secondaryLines[effectiveLineIndex + 1])
                                } else {
                                    listOf(nextSection.secondaryLines.first())
                                }
                            } else {
                                listOf(nextSection.secondaryLines.first())
                            }
                        } else {
                            nextSection.secondaryLines
                        }
                    } else if (lookAheadEnabled && isLineMode && effectiveLineIndex >= 0 && effectiveLineIndex + 1 < (section.secondaryLines.size)) {
                        if (laIsLineMode) listOf(section.secondaryLines[effectiveLineIndex + 1]) else emptyList()
                    } else {
                        emptyList()
                    }

                    val secondaryLookAheadStartIndex = if (laSecondaryLines.isNotEmpty()) mainSecondaryLines.size else -1

                    // Apply language display to main lines
                    val effectiveDisplayLines: List<String>
                    val effectiveSecondaryDisplayLines: List<String>

                    when (langDisplay) {
                        Constants.SONG_LANG_SECONDARY -> {
                            effectiveDisplayLines = mainSecondaryLines.ifEmpty { mainLines }
                            effectiveSecondaryDisplayLines = emptyList()
                        }
                        Constants.SONG_LANG_BOTH -> {
                            effectiveDisplayLines = mainLines
                            effectiveSecondaryDisplayLines = mainSecondaryLines
                        }
                        else -> { // PRIMARY
                            effectiveDisplayLines = mainLines
                            effectiveSecondaryDisplayLines = emptyList()
                        }
                    }

                    // Apply language display to look-ahead lines
                    val effectiveLaLines: List<String>
                    val effectiveLaSecondaryLines: List<String>

                    when (laLangDisplay) {
                        Constants.SONG_LANG_SECONDARY -> {
                            effectiveLaLines = laSecondaryLines.ifEmpty { laLines }
                            effectiveLaSecondaryLines = emptyList()
                        }
                        Constants.SONG_LANG_BOTH -> {
                            effectiveLaLines = laLines
                            effectiveLaSecondaryLines = laSecondaryLines
                        }
                        else -> { // PRIMARY
                            effectiveLaLines = laLines
                            effectiveLaSecondaryLines = emptyList()
                        }
                    }

                    // Combined primary lines with look-ahead start index
                    val combinedPrimaryLines = effectiveDisplayLines + effectiveLaLines
                    val primaryLaStart = if (effectiveLaLines.isNotEmpty()) effectiveDisplayLines.size else -1

                    // Combined secondary lines with look-ahead start index
                    val combinedSecondaryLines = effectiveSecondaryDisplayLines + effectiveLaSecondaryLines
                    val secondaryLaStart = if (effectiveLaSecondaryLines.isNotEmpty()) effectiveSecondaryDisplayLines.size else -1

                    val effectiveTitle = if (langDisplay == Constants.SONG_LANG_SECONDARY && section.secondaryTitle.isNotEmpty()) {
                        section.secondaryTitle
                    } else {
                        section.title
                    }

                    val hasBilingual = combinedSecondaryLines.isNotEmpty()
                    val useSideBySide = appSettings.songSettings.bilingualLayout == Constants.BILINGUAL_SIDE_BY_SIDE

                    // Look-ahead text style with full font controls
                    val laBaseShadow = Shadow(
                        color = laShadowColor.copy(alpha = laShadowAlpha),
                        offset = Offset(6f * scaleFactor * laShadowSizeMul, 6f * scaleFactor * laShadowSizeMul),
                        blurRadius = 12f * scaleFactor * laShadowSizeMul
                    )
                    val lookAheadTextStyle = TextStyle(
                        fontWeight = if (laBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (laItalic) FontStyle.Italic else FontStyle.Normal,
                        textDecoration = if (laUnderline) TextDecoration.Underline else TextDecoration.None,
                        shadow = if (laShadowEnabled) laBaseShadow else null
                    )
                    // Look-ahead next uses auto-fit capped at its own configured max
                    val laAutoFitEnabled = if (isLowerThird) ss.lowerThirdLookAheadNextFontSizeAutoFit else ss.lookAheadNextFontSizeAutoFit
                    val effectiveLaFontSize = if (laAutoFitEnabled) {
                        (autoFitFontSize ?: laFontSize).coerceAtMost(laFontSize)
                    } else laFontSize
                    val scaledLaFontSize = (effectiveLaFontSize * scaleFactor).sp

                    @Composable
                    fun LyricLine(lineIdx: Int, line: String, laStart: Int) {
                        val isLookAheadLine = laStart >= 0 && lineIdx >= laStart
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = lyricsHorizontalAlignment,
                            fontFamily = if (isLookAheadLine) laFontFamily else lyricsFontFamily,
                            fontSize = if (isLookAheadLine) scaledLaFontSize else scaledLyricsFontSize,
                            softWrap = appSettings.songSettings.wordWrap,
                            text = line,
                            color = if (isLookAheadLine) laColor else lyricsColor,
                            style = if (isLookAheadLine) lookAheadTextStyle else lyricsTextStyleScaled
                        )
                    }

                    @Composable
                    fun LookAheadSpacer(idx: Int, laStart: Int) {
                        if (laStart >= 0 && idx == laStart && !laIsLineMode) {
                            Spacer(modifier = Modifier.padding(top = (12 * scaleFactor).dp))
                        }
                    }

                    @Composable
                    fun EndOfSongIndicator() {
                        // Always reserve space so lyrics don't shift when the indicator appears on the last section
                        val visible = section.isLastSection && (!isLineMode || effectiveLineIndex >= allDisplayLines.size - 1)
                        val indicatorAlpha = if (visible) 1f else 0f
                        Spacer(modifier = Modifier.padding(top = (4 * scaleFactor).dp))
                        Row(modifier = Modifier.fillMaxWidth().alpha(indicatorAlpha), horizontalArrangement = Arrangement.Center) {
                            repeat(3) { Text(text = "  *  ", fontSize = scaledLyricsFontSize, color = lyricsColor, style = lyricsTextStyleScaled) }
                        }
                    }

                    // Invisible placeholder to reserve space for missing lookahead on last section
                    @Composable
                    fun LookAheadPlaceholder() {
                        if (lookAheadEnabled && effectiveLaLines.isEmpty() && effectiveDisplayLines.isNotEmpty()) {
                            if (!laIsLineMode) {
                                Spacer(modifier = Modifier.padding(top = (12 * scaleFactor).dp))
                            }
                            Column(modifier = Modifier.alpha(0f)) {
                                effectiveDisplayLines.forEach { line ->
                                    Text(
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = lyricsHorizontalAlignment,
                                        fontFamily = laFontFamily,
                                        fontSize = scaledLaFontSize,
                                        softWrap = appSettings.songSettings.wordWrap,
                                        text = line,
                                        color = laColor,
                                        style = lookAheadTextStyle
                                    )
                                }
                            }
                        }
                    }

                    // Renders title and/or song number for a given position (ABOVE_VERSE or BELOW_VERSE)
                    val samePosition = effectiveTitlePosition == effectiveSongNumberPosition
                    val sameHorizontal = (if (isLowerThird) ss.songNumberLowerThirdHorizontalAlignment else ss.songNumberHorizontalAlignment) ==
                            (if (isLowerThird) ss.titleLowerThirdHorizontalAlignment else ss.titleHorizontalAlignment)
                    val numberBeforeTitle = ss.songNumberBeforeTitle

                    @Composable
                    fun NumberPart(modifier: Modifier = Modifier, visibilityAlpha: Float = 1f) {
                        Text(
                            modifier = modifier.alpha(visibilityAlpha),
                            textAlign = songNumberHorizontalAlignment,
                            fontFamily = titleFontFamily,
                            fontSize = scaledSongNumberFontSize,
                            text = section.songNumber.toString(),
                            color = titleColor,
                            style = titleTextStyleScaled
                        )
                    }

                    @Composable
                    fun TitlePart(modifier: Modifier = Modifier, visibilityAlpha: Float = 1f) {
                        Text(
                            modifier = modifier.alpha(visibilityAlpha),
                            textAlign = titleHorizontalAlignment,
                            fontFamily = titleFontFamily,
                            fontSize = scaledTitleFontSize,
                            text = effectiveTitle,
                            color = titleColor,
                            style = titleTextStyleScaled
                        )
                    }

                    @Composable
                    fun TitleAndNumberRow(position: String, invisible: Boolean = false) {
                        // "configured" = setting is not None (could appear on some slides)
                        val hasTitleHere = titleConfigured && effectiveTitlePosition == position
                        val hasNumberHere = numberConfigured && effectiveSongNumberPosition == position
                        if (!hasTitleHere && !hasNumberHere) return

                        // Alpha: fully invisible when used as a balancing spacer,
                        // otherwise visible on this slide or invisible (reserving space)
                        val titleAlpha = if (invisible) 0f else if (shouldShowTitle) 1f else 0f
                        val numberAlpha = if (invisible) 0f else if (shouldShowSongNumber) 1f else 0f

                        if (hasTitleHere && hasNumberHere && samePosition) {
                            if (sameHorizontal) {
                                val sharedHAlign = if (isLowerThird) ss.songNumberLowerThirdHorizontalAlignment else ss.songNumberHorizontalAlignment
                                val arrangement = when (sharedHAlign) {
                                    Constants.LEFT -> Arrangement.Start
                                    Constants.CENTER -> Arrangement.Center
                                    else -> Arrangement.End
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = arrangement) {
                                    if (numberBeforeTitle) {
                                        NumberPart(visibilityAlpha = numberAlpha); Spacer(modifier = Modifier.padding(horizontal = (4 * scaleFactor).dp)); TitlePart(visibilityAlpha = titleAlpha)
                                    } else {
                                        TitlePart(visibilityAlpha = titleAlpha); Spacer(modifier = Modifier.padding(horizontal = (4 * scaleFactor).dp)); NumberPart(visibilityAlpha = numberAlpha)
                                    }
                                }
                            } else {
                                NumberPart(modifier = Modifier.fillMaxWidth(), visibilityAlpha = numberAlpha)
                                TitlePart(modifier = Modifier.fillMaxWidth(), visibilityAlpha = titleAlpha)
                            }
                        } else if (hasNumberHere) {
                            NumberPart(modifier = Modifier.fillMaxWidth(), visibilityAlpha = numberAlpha)
                        } else if (hasTitleHere) {
                            TitlePart(modifier = Modifier.fillMaxWidth(), visibilityAlpha = titleAlpha)
                        }
                    }

                    // Determine which positions have content for balancing
                    val hasTopContent = (titleConfigured && effectiveTitlePosition == Constants.ABOVE_VERSE) ||
                            (numberConfigured && effectiveSongNumberPosition == Constants.ABOVE_VERSE)
                    val hasBottomContent = (titleConfigured && effectiveTitlePosition == Constants.BELOW_VERSE) ||
                            (numberConfigured && effectiveSongNumberPosition == Constants.BELOW_VERSE)

                    // Outer column fills the content area; title/number at edges, lyrics centered
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top section: items positioned "above verse"
                        TitleAndNumberRow(Constants.ABOVE_VERSE)

                        // Lyrics area + bottom title/number overlaid (z-stacked).
                        // The bottom title/number floats over the lyrics so it doesn't
                        // steal vertical space and cut off lyrics text.
                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            // Lyrics fill the entire remaining space
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = if (isLowerThird) Alignment.BottomCenter else contentAlignment
                            ) {
                                if (hasBilingual) {
                                    if (useSideBySide) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Bottom) {
                                                combinedPrimaryLines.forEachIndexed { idx, line ->
                                                    LookAheadSpacer(idx, primaryLaStart)
                                                    LyricLine(idx, line, primaryLaStart)
                                                }
                                                EndOfSongIndicator()
                                                LookAheadPlaceholder()
                                            }
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Bottom) {
                                                combinedSecondaryLines.forEachIndexed { idx, line ->
                                                    LookAheadSpacer(idx, secondaryLaStart)
                                                    LyricLine(idx, line, secondaryLaStart)
                                                }
                                                EndOfSongIndicator()
                                                LookAheadPlaceholder()
                                            }
                                        }
                                    } else {
                                        // Top/bottom bilingual layout
                                        if (isLowerThird) {
                                            // Lower third: compact layout, no height splitting
                                            Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                                                combinedPrimaryLines.forEachIndexed { idx, line ->
                                                    LookAheadSpacer(idx, primaryLaStart)
                                                    LyricLine(idx, line, primaryLaStart)
                                                }
                                                EndOfSongIndicator()
                                                LookAheadPlaceholder()
                                                Spacer(modifier = Modifier.padding(top = (12 * scaleFactor).dp))
                                                combinedSecondaryLines.forEachIndexed { idx, line ->
                                                    LookAheadSpacer(idx, secondaryLaStart)
                                                    LyricLine(idx, line, secondaryLaStart)
                                                }
                                                EndOfSongIndicator()
                                                LookAheadPlaceholder()
                                            }
                                        } else {
                                            // Full screen: each language gets its own half
                                            val halfAlignment = contentAlignment
                                            Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                                                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = halfAlignment) {
                                                    Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                                                        combinedPrimaryLines.forEachIndexed { idx, line ->
                                                            LookAheadSpacer(idx, primaryLaStart)
                                                            LyricLine(idx, line, primaryLaStart)
                                                        }
                                                        EndOfSongIndicator()
                                                        LookAheadPlaceholder()
                                                    }
                                                }
                                                Spacer(modifier = Modifier.padding(top = (12 * scaleFactor).dp))
                                                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = halfAlignment) {
                                                    Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                                                        combinedSecondaryLines.forEachIndexed { idx, line ->
                                                            LookAheadSpacer(idx, secondaryLaStart)
                                                            LyricLine(idx, line, secondaryLaStart)
                                                        }
                                                        EndOfSongIndicator()
                                                        LookAheadPlaceholder()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Single language layout
                                    Column(
                                        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                                        verticalArrangement = if (isLowerThird) Arrangement.Bottom else Arrangement.Top
                                    ) {
                                        combinedPrimaryLines.forEachIndexed { idx, line ->
                                            LookAheadSpacer(idx, primaryLaStart)
                                            LyricLine(idx, line, primaryLaStart)
                                        }
                                        EndOfSongIndicator()
                                        LookAheadPlaceholder()
                                    }
                                }
                            }

                            // Bottom title/number overlaid at the bottom of the lyrics area
                            if (hasBottomContent) {
                                Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                                    TitleAndNumberRow(Constants.BELOW_VERSE)
                                }
                            }
                        }
                    }
                }
            }

            if (crossfadeEnabled || ss.fadeIn || ss.fadeOut) {
                val duration = ss.transitionDuration.toInt().coerceAtLeast(100)
                val isCrossfade = crossfadeEnabled
                var displayedCurrent by remember { mutableStateOf(lyricSection) }
                var displayedPrevious by remember { mutableStateOf(LyricSection()) }
                var currentAlpha by remember { mutableStateOf(1f) }
                var previousAlpha by remember { mutableStateOf(0f) }
                val pendingQueue = remember { kotlinx.coroutines.channels.Channel<LyricSection>(kotlinx.coroutines.channels.Channel.CONFLATED) }

                // Queue section changes
                LaunchedEffect(lyricSection) {
                    if (displayedCurrent != lyricSection) {
                        pendingQueue.send(lyricSection)
                    }
                }

                // Process section switches (crossfade between sections)
                LaunchedEffect(Unit) {
                    for (nextSection in pendingQueue) {
                        if (displayedCurrent == nextSection) continue

                        if (isCrossfade) {
                            displayedPrevious = displayedCurrent
                            displayedCurrent = nextSection
                            previousAlpha = 1f
                            currentAlpha = 0f
                            val anim = Animatable(0f)
                            anim.animateTo(1f, tween(durationMillis = duration)) {
                                currentAlpha = this.value
                                previousAlpha = 1f - this.value
                            }
                        } else {
                            displayedCurrent = nextSection
                        }
                        currentAlpha = 1f
                        previousAlpha = 0f
                        displayedPrevious = LyricSection()
                    }
                }

                Box(modifier = Modifier.matchParentSize().graphicsLayer { alpha = transitionAlpha }) {
                    if (displayedPrevious.lines.isNotEmpty() && previousAlpha > 0f) {
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
                    TextContent(lyricSection)
                }
            }
        }
    }
}

private fun shouldShowText(display: String, lyricSection: LyricSection): Boolean {
    return when (display) {
        Constants.EVERY_PAGE -> true
        Constants.FIRST_PAGE -> {
            // Show only on the first verse section (header null, ends with "1", or verse with no number)
            val header = lyricSection.header ?: return true  // null header = first/only section
            // Chorus/bridge sections are not "first page"
            if (lyricSection.type == Constants.SECTION_TYPE_CHORUS) return false
            val inner = header.trim().removePrefix("[").removePrefix("{").removeSuffix("]").removeSuffix("}").trim()
            inner.endsWith("1") || !inner.any { it.isDigit() }
        }

        else -> false
    }
}

private fun getTextAlign(alignment: String): TextAlign {
    return when (alignment) {
        Constants.LEFT -> TextAlign.Start
        Constants.RIGHT -> TextAlign.End
        else -> TextAlign.Center
    }
}