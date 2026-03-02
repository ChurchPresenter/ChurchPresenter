package org.churchpresenter.app.churchpresenter.presenter

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import kotlin.math.min
import org.churchpresenter.app.churchpresenter.composables.LoopingVideoBackground
import org.churchpresenter.app.churchpresenter.data.AppSettings
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
) {
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
        shadow = if (appSettings.songSettings.titleShadow) Shadow(color = Color.Black.copy(alpha = 0.7f), offset = Offset(2f, 2f), blurRadius = 4f) else null
    )
    val lyricsTextStyle = TextStyle(
        fontWeight = if (appSettings.songSettings.lyricsBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (appSettings.songSettings.lyricsItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (appSettings.songSettings.lyricsUnderline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (appSettings.songSettings.lyricsShadow) Shadow(color = Color.Black.copy(alpha = 0.7f), offset = Offset(2f, 2f), blurRadius = 4f) else null
    )
    val songNumberTextStyle = TextStyle(
        fontWeight = if (appSettings.songSettings.songNumberBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (appSettings.songSettings.songNumberItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (appSettings.songSettings.songNumberUnderline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (appSettings.songSettings.songNumberShadow) Shadow(color = Color.Black.copy(alpha = 0.7f), offset = Offset(2f, 2f), blurRadius = 4f) else null
    )

    val contentAlignment = when (appSettings.songSettings.lyricsAlignment) {
        Constants.TOP -> Alignment.TopCenter
        Constants.BOTTOM -> Alignment.BottomCenter
        else -> Alignment.Center
    }
    val lyricsHorizontalAlignment = getHorizontalArrangement(
        if (isLowerThird) appSettings.songSettings.lyricsLowerThirdHorizontalAlignment
        else appSettings.songSettings.lyricsHorizontalAlignment
    )
    val titleHorizontalAlignment = getHorizontalArrangement(
        appSettings.songSettings.titleHorizontalAlignment
    )

    val bgConfig = if (isLowerThird) appSettings.backgroundSettings.songLowerThirdBackground
                   else appSettings.backgroundSettings.songBackground

    // Resolve effective background type/paths (handle Default → inherit from global)
    val effectiveType: String
    val effectiveImagePath: String
    val effectiveVideoPath: String
    var backgroundColor: Color

    if (bgConfig.backgroundType == Constants.BACKGROUND_DEFAULT) {
        val defaults = appSettings.backgroundSettings
        effectiveType = defaults.defaultBackgroundType
        effectiveImagePath = defaults.defaultBackgroundImage
        effectiveVideoPath = defaults.defaultBackgroundVideo
        backgroundColor = parseHexColor(defaults.defaultBackgroundColor)
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
            } catch (_: Exception) { null }
        } else null
    }

    val useVideoBackground = effectiveType == Constants.BACKGROUND_VIDEO && effectiveVideoPath.isNotEmpty()

    val bgModifier: Modifier = when {
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

        val scaledTitleFontSize = (appSettings.songSettings.titleFontSize * scaleFactor).sp
        val effectiveLyricsFontSize = if (isLowerThird) appSettings.songSettings.lyricsLowerThirdFontSize else appSettings.songSettings.lyricsFontSize
        val effectiveSongNumberFontSize = if (isLowerThird) appSettings.songSettings.songNumberLowerThirdFontSize else appSettings.songSettings.songNumberFontSize
        val scaledLyricsFontSize = (effectiveLyricsFontSize * scaleFactor).sp
        val scaledSongNumberFontSize = (effectiveSongNumberFontSize * scaleFactor).sp

        val leftOffSet = (appSettings.projectionSettings.windowLeft * scaleFactor).dp
        val rightOffSet = (appSettings.projectionSettings.windowRight * scaleFactor).dp
        val topOffSet = (appSettings.projectionSettings.windowTop * scaleFactor).dp
        val bottomOffSet = (appSettings.projectionSettings.windowBottom * scaleFactor).dp

        if (isLowerThird) {
            // Background stretches full width at bottom third, text respects padding on top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.333f)
                    .align(Alignment.BottomCenter)
                    .then(bgModifier)
            )
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
                    .fillMaxHeight(0.333f)
                    .align(Alignment.BottomCenter)
            else
                Modifier
            val alignment = when (appSettings.songSettings.songNumberPosition) {
                Constants.TOP_LEFT -> Alignment.TopStart
                Constants.TOP_RIGHT -> Alignment.TopEnd
                Constants.BOTTOM_LEFT -> Alignment.BottomStart
                else -> Alignment.BottomEnd
            }
            val shouldShowTitle = shouldShowText(appSettings.songSettings.titleDisplay, lyricSection)
            val shouldShowSongNumber = shouldShowText(appSettings.songSettings.showNumber, lyricSection)

            Box(
                modifier = innerModifier,
                contentAlignment = if (isLowerThird) Alignment.BottomCenter else Alignment.TopStart
            ) {
                if (shouldShowSongNumber) {
                    Text(
                        modifier = Modifier.wrapContentWidth().align(alignment),
                        fontFamily = titleFontFamily,
                        fontSize = scaledSongNumberFontSize,
                        text = lyricSection.songNumber.toString(),
                        color = songNumberColor,
                        style = songNumberTextStyle
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    verticalArrangement = if (isLowerThird) Arrangement.Bottom else Arrangement.Top
                ) {
                    if (shouldShowTitle && appSettings.songSettings.titlePosition == Constants.ABOVE_VERSE) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = titleHorizontalAlignment) {
                            Text(
                                modifier = Modifier.wrapContentWidth(),
                                fontFamily = titleFontFamily,
                                fontSize = scaledTitleFontSize,
                                text = lyricSection.title,
                                color = titleColor,
                                style = titleTextStyle
                            )
                        }
                    }
                    lyricSection.lines.forEach { line ->
                        val isSectionHeader =
                            line.startsWith(Constants.VERSE_RUS, ignoreCase = true) ||
                            line.startsWith(Constants.CHORUS_RUS, ignoreCase = true) ||
                            line.startsWith(Constants.VERSE, ignoreCase = true) ||
                            line.startsWith(Constants.CHORUS, ignoreCase = true)
                        if (!isSectionHeader) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = lyricsHorizontalAlignment) {
                                Text(
                                    modifier = Modifier.wrapContentWidth(),
                                    fontFamily = lyricsFontFamily,
                                    fontSize = scaledLyricsFontSize,
                                    softWrap = appSettings.songSettings.wordWrap,
                                    text = line,
                                    color = lyricsColor,
                                    style = lyricsTextStyle
                                )
                            }
                        }
                    }
                    if (shouldShowTitle && appSettings.songSettings.titlePosition == Constants.BELOW_VERSE) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = titleHorizontalAlignment) {
                            Text(
                                modifier = Modifier.wrapContentWidth(),
                                fontFamily = titleFontFamily,
                                fontSize = scaledTitleFontSize,
                                text = lyricSection.title,
                                color = titleColor,
                                style = titleTextStyle
                            )
                        }
                    }
                }
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

private fun getHorizontalArrangement(alignment: String): Arrangement.Horizontal {
    return when (alignment) {
        Constants.LEFT -> Arrangement.Start
        Constants.RIGHT -> Arrangement.End
        else -> Arrangement.Center
    }
}