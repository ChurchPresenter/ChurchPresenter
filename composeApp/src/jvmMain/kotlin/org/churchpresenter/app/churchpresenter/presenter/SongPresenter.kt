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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import kotlin.math.min
import org.churchpresenter.app.churchpresenter.composables.LoopingVideoBackground
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.Constants.VERSE_1_RUS
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
) {
    val isFillOrKey = outputRole == Constants.OUTPUT_ROLE_FILL || outputRole == Constants.OUTPUT_ROLE_KEY
    val titleFontFamily = remember(appSettings.songSettings.titleFontType) {
        systemFontFamilyOrDefault(appSettings.songSettings.titleFontType)
    }
    val lyricsFontFamily = remember(appSettings.songSettings.lyricsFontType) {
        systemFontFamilyOrDefault(appSettings.songSettings.lyricsFontType)
    }

    val titleColor = remember(appSettings.songSettings.titleColor) {
        parseHexColor(appSettings.songSettings.titleColor)
    }
    val lyricsColor = remember(appSettings.songSettings.lyricsColor) {
        parseHexColor(appSettings.songSettings.lyricsColor)
    }
    val songNumberColor = remember(appSettings.songSettings.songNumberColor) {
        parseHexColor(appSettings.songSettings.songNumberColor)
    }

    // Text styles derived from settings
    val titleTextStyle = TextStyle(
        fontWeight = if (appSettings.songSettings.titleBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (appSettings.songSettings.titleItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (appSettings.songSettings.titleUnderline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (appSettings.songSettings.titleShadow) Shadow(
            color = Color.Black.copy(alpha = 0.7f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        ) else null
    )
    val lyricsTextStyle = TextStyle(
        fontWeight = if (appSettings.songSettings.lyricsBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (appSettings.songSettings.lyricsItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (appSettings.songSettings.lyricsUnderline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (appSettings.songSettings.lyricsShadow) Shadow(
            color = Color.Black.copy(alpha = 0.7f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        ) else null
    )
    val songNumberTextStyle = TextStyle(
        fontWeight = if (appSettings.songSettings.songNumberBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (appSettings.songSettings.songNumberItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (appSettings.songSettings.songNumberUnderline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (appSettings.songSettings.songNumberShadow) Shadow(
            color = Color.Black.copy(alpha = 0.7f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        ) else null
    )

    val contentAlignment = when (appSettings.songSettings.lyricsAlignment) {
        Constants.TOP -> Alignment.TopCenter
        Constants.BOTTOM -> Alignment.BottomCenter
        else -> Alignment.Center
    }
    val lyricsHorizontalAlignment = getTextAlign(
        if (isLowerThird) appSettings.songSettings.lyricsLowerThirdHorizontalAlignment
        else appSettings.songSettings.lyricsHorizontalAlignment
    )
    val titleHorizontalAlignment = getTextAlign(
        appSettings.songSettings.titleHorizontalAlignment
    )

    val bgConfig = if (isLowerThird) appSettings.backgroundSettings.songLowerThirdBackground
    else appSettings.backgroundSettings.songBackground

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
            Modifier.paint(painter = BitmapPainter(backgroundImageBitmap), contentScale = ContentScale.Crop)

        effectiveType == Constants.BACKGROUND_IMAGE ->
            Modifier.background(Color.Black)

        else ->
            Modifier.background(backgroundColor)
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
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
        val titleTextStyleScaled = if (appSettings.songSettings.titleShadow)
            titleTextStyle.copy(shadow = scaledShadow) else titleTextStyle
        val lyricsTextStyleScaled = if (appSettings.songSettings.lyricsShadow)
            lyricsTextStyle.copy(shadow = scaledShadow) else lyricsTextStyle
        val songNumberTextStyleScaled = if (appSettings.songSettings.songNumberShadow)
            songNumberTextStyle.copy(shadow = scaledShadow) else songNumberTextStyle

        val scaledTitleFontSize = (appSettings.songSettings.titleFontSize * scaleFactor).sp
        val effectiveLyricsFontSize =
            if (isLowerThird) appSettings.songSettings.lyricsLowerThirdFontSize else appSettings.songSettings.lyricsFontSize
        val effectiveSongNumberFontSize =
            if (isLowerThird) appSettings.songSettings.songNumberLowerThirdFontSize else appSettings.songSettings.songNumberFontSize
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
                val alignment = when (appSettings.songSettings.songNumberPosition) {
                    Constants.TOP_LEFT -> Alignment.TopStart
                    Constants.TOP_RIGHT -> Alignment.TopEnd
                    Constants.BOTTOM_LEFT -> Alignment.BottomStart
                    else -> Alignment.BottomEnd
                }
                val shouldShowTitle = shouldShowText(appSettings.songSettings.titleDisplay, section)
                val shouldShowSongNumber = shouldShowText(appSettings.songSettings.showNumber, section)

                BoxWithConstraints(
                    modifier = innerModifier,
                    contentAlignment = if (isLowerThird) Alignment.BottomCenter else Alignment.TopStart
                ) {
                    if (shouldShowSongNumber) {
                        Text(
                            modifier = Modifier.wrapContentWidth().align(alignment),
                            fontFamily = titleFontFamily,
                            fontSize = scaledSongNumberFontSize,
                            text = section.songNumber.toString(),
                            color = songNumberColor,
                            style = songNumberTextStyleScaled
                        )
                    }

                    // Auto-scale lyrics if they overflow the available height
                    val songTextMeasurer = rememberTextMeasurer()
                    val widthConstraint = Constraints(maxWidth = constraints.maxWidth)
                    val titleH = if (shouldShowTitle) {
                        songTextMeasurer.measure(
                            text = section.title,
                            style = titleTextStyleScaled.copy(fontFamily = titleFontFamily, fontSize = scaledTitleFontSize),
                            constraints = widthConstraint
                        ).size.height
                    } else 0
                    val lyricsH = section.lines.filter { line ->
                        !line.startsWith(Constants.VERSE_RUS, ignoreCase = true) &&
                        !line.startsWith(Constants.CHORUS_RUS, ignoreCase = true) &&
                        !line.startsWith(Constants.VERSE, ignoreCase = true) &&
                        !line.startsWith(Constants.CHORUS, ignoreCase = true)
                    }.sumOf { line ->
                        songTextMeasurer.measure(
                            text = line,
                            style = lyricsTextStyleScaled.copy(fontFamily = lyricsFontFamily, fontSize = scaledLyricsFontSize),
                            constraints = widthConstraint
                        ).size.height
                    }
                    val totalH = titleH + lyricsH
                    val fitScale = if (totalH > constraints.maxHeight) {
                        val spaceForLyrics = (constraints.maxHeight - titleH).coerceAtLeast(1)
                        (spaceForLyrics.toFloat() / lyricsH).coerceAtLeast(0.3f)
                    } else 1f
                    val fittedLyricsFontSize = scaledLyricsFontSize * fitScale

                    Column(
                        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                        verticalArrangement = if (isLowerThird) Arrangement.Bottom else Arrangement.Top
                    ) {
                        if (shouldShowTitle && appSettings.songSettings.titlePosition == Constants.ABOVE_VERSE) {
                            Text(
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = titleHorizontalAlignment,
                                fontFamily = titleFontFamily,
                                fontSize = scaledTitleFontSize,
                                text = section.title,
                                color = titleColor,
                                style = titleTextStyleScaled
                            )
                        }
                        section.lines.forEach { line ->
                            val isSectionHeader =
                                line.startsWith(Constants.VERSE_RUS, ignoreCase = true) ||
                                        line.startsWith(Constants.CHORUS_RUS, ignoreCase = true) ||
                                        line.startsWith(Constants.VERSE, ignoreCase = true) ||
                                        line.startsWith(Constants.CHORUS, ignoreCase = true)
                            if (!isSectionHeader) {
                                Text(
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = lyricsHorizontalAlignment,
                                    fontFamily = lyricsFontFamily,
                                    fontSize = fittedLyricsFontSize,
                                    softWrap = appSettings.songSettings.wordWrap,
                                    text = line,
                                    color = lyricsColor,
                                    style = lyricsTextStyleScaled
                                )
                            }
                        }
                        if (shouldShowTitle && appSettings.songSettings.titlePosition == Constants.BELOW_VERSE) {
                            Text(
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = titleHorizontalAlignment,
                                fontFamily = titleFontFamily,
                                fontSize = scaledTitleFontSize,
                                text = section.title,
                                color = titleColor,
                                style = titleTextStyleScaled
                            )
                        }

                        // End-of-song indicator: 3 tight asterisks centered
                        if (section.isLastSection) {
                            Spacer(modifier = Modifier.padding(top = (4 * scaleFactor).dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                repeat(3) {
                                    Text(
                                        text = "  *  ",
                                        fontSize = scaledLyricsFontSize,
                                        color = lyricsColor,
                                        style = lyricsTextStyleScaled
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Resolve animation from appSettings — no params needed from caller
            val animationType = when (appSettings.songSettings.animationType) {
                Constants.ANIMATION_FADE -> AnimationType.FADE
                Constants.ANIMATION_SLIDE_LEFT -> AnimationType.SLIDE_LEFT
                Constants.ANIMATION_SLIDE_RIGHT -> AnimationType.SLIDE_RIGHT
                Constants.ANIMATION_NONE -> AnimationType.NONE
                else -> AnimationType.CROSSFADE
            }
            val transitionDuration = appSettings.songSettings.transitionDuration.toInt()

            when (animationType) {
                AnimationType.CROSSFADE -> Crossfade(
                    targetState = lyricSection,
                    animationSpec = tween(transitionDuration),
                    label = "SongCrossfade"
                ) { TextContent(it) }

                AnimationType.FADE -> AnimatedContent(
                    targetState = lyricSection,
                    transitionSpec = { fadeIn(tween(transitionDuration)) togetherWith fadeOut(tween(transitionDuration)) },
                    label = "SongFade"
                ) { TextContent(it) }

                AnimationType.SLIDE_LEFT -> AnimatedContent(
                    targetState = lyricSection,
                    transitionSpec = {
                        slideInHorizontally(tween(transitionDuration)) { it } togetherWith slideOutHorizontally(
                            tween(
                                transitionDuration
                            )
                        ) { -it }
                    },
                    label = "SongSlideLeft"
                ) { TextContent(it) }

                AnimationType.SLIDE_RIGHT -> AnimatedContent(
                    targetState = lyricSection,
                    transitionSpec = {
                        slideInHorizontally(tween(transitionDuration)) { -it } togetherWith slideOutHorizontally(
                            tween(
                                transitionDuration
                            )
                        ) { it }
                    },
                    label = "SongSlideRight"
                ) { TextContent(it) }

                AnimationType.NONE -> TextContent(lyricSection)
            }
        }
    }
}

private fun shouldShowText(display: String, lyricSection: LyricSection): Boolean {
    return when (display) {
        Constants.EVERY_PAGE -> true
        Constants.FIRST_PAGE -> {
            lyricSection.lines.firstOrNull()?.let { line ->
                line == VERSE_1_RUS
            } ?: false
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