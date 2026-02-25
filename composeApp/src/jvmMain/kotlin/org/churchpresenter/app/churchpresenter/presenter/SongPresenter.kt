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
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.extensions.conditional
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

    // Parse colors from hex strings
    val titleColor = remember(appSettings.songSettings.titleColor) {
        parseHexColor(appSettings.songSettings.titleColor)
    }
    val lyricsColor = remember(appSettings.songSettings.lyricsColor) {
        parseHexColor(appSettings.songSettings.lyricsColor)
    }
    val songNumberColor = remember(appSettings.songSettings.songNumberColor) {
        parseHexColor(appSettings.songSettings.songNumberColor)
    }

    val contentAlignment = when (appSettings.songSettings.lyricsAlignment) {
        Constants.TOP -> Alignment.TopCenter
        Constants.BOTTOM -> Alignment.BottomCenter
        else -> Alignment.Center
    }
    val lyricsHorizontalAlignment = getHorizontalArrangement(
        appSettings.songSettings.lyricsHorizontalAlignment
    )
    val titleHorizontalAlignment = getHorizontalArrangement(
        appSettings.songSettings.titleHorizontalAlignment
    )

    // Background settings
    var backgroundColor: Color = parseHexColor(appSettings.backgroundSettings.songBackground.backgroundColor)
    val backgroundType = appSettings.backgroundSettings.songBackground.backgroundType
    val backgroundImagePath = appSettings.backgroundSettings.songBackground.backgroundImage

    if (backgroundType == Constants.BACKGROUND_DEFAULT) {
        backgroundColor = parseHexColor(appSettings.backgroundSettings.defaultBackgroundColor)
    }

    // Load background image if type is IMAGE and path is not empty
    val backgroundImageBitmap = remember(backgroundImagePath) {
        if (backgroundType == Constants.BACKGROUND_IMAGE && backgroundImagePath.isNotEmpty()) {
            try {
                val file = File(backgroundImagePath)
                if (file.exists()) {
                    val bytes = file.readBytes()
                    Image.makeFromEncoded(bytes).toComposeImageBitmap()
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .conditional(backgroundType == Constants.BACKGROUND_DEFAULT || backgroundType == Constants.BACKGROUND_COLOR) {
                background(color = backgroundColor)
            }
            .conditional(backgroundType == Constants.BACKGROUND_IMAGE && backgroundImageBitmap != null) {
                paint(
                    painter = BitmapPainter(backgroundImageBitmap!!),
                    contentScale = ContentScale.Crop
                )
            }
            .conditional(backgroundType == Constants.BACKGROUND_IMAGE && backgroundImageBitmap == null) {
                background(color = Color.Black) // Fallback if image fails to load
            }
    ) {
        val density = LocalDensity.current

        // Calculate scale factor based on available width and height
        // Using a reference size of 1920x1080 as base
        val widthScale = with(density) { maxWidth.toPx() / 1920f }
        val heightScale = with(density) { maxHeight.toPx() / 1080f }
        val scaleFactor = min(widthScale, heightScale).coerceIn(0.5f, 3.0f)

        // Scale font sizes based on window size
        val scaledTitleFontSize = (appSettings.songSettings.titleFontSize * scaleFactor).sp
        val scaledLyricsFontSize = (appSettings.songSettings.lyricsFontSize * scaleFactor).sp
        val scaledSongNumberFontSize = (appSettings.songSettings.songNumberFontSize * scaleFactor).sp

        val leftOffSet = (appSettings.projectionSettings.windowLeft * scaleFactor).dp
        val rightOffSet = (appSettings.projectionSettings.windowRight * scaleFactor).dp
        val topOffSet = (appSettings.projectionSettings.windowTop * scaleFactor).dp
        val bottomOffSet = (appSettings.projectionSettings.windowBottom * scaleFactor).dp

        Box(
            Modifier
                .fillMaxSize()
                .padding(start = leftOffSet, end = rightOffSet, top = topOffSet, bottom = bottomOffSet),
            contentAlignment = if (isLowerThird) Alignment.BottomCenter else contentAlignment
        ) {
            // When lower third mode: wrap content in a box capped at 1/3 screen height, pinned to bottom
            val innerModifier = if (isLowerThird)
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.333f)
                    .align(Alignment.BottomCenter)
            else
                Modifier
            // ...existing code...
            val alignment = when (appSettings.songSettings.songNumberPosition) {
                Constants.TOP_LEFT -> Alignment.TopStart
                Constants.TOP_RIGHT -> Alignment.TopEnd
                Constants.BOTTOM_LEFT -> Alignment.BottomStart
                else -> Alignment.BottomEnd
            }
            val shouldShowTitle = shouldShowText(appSettings.songSettings.titleDisplay, lyricSection)
            val shouldShowSongNumber = shouldShowText(appSettings.songSettings.showNumber, lyricSection)

            Box(modifier = innerModifier) {
                if (shouldShowSongNumber) {
                    Text(
                        modifier = Modifier.wrapContentWidth().align(alignment),
                        fontFamily = titleFontFamily,
                        fontSize = scaledSongNumberFontSize,
                        text = lyricSection.songNumber.toString(),
                        color = songNumberColor
                    )
                }
                Column(modifier = Modifier.wrapContentHeight()) {
                    if (shouldShowTitle && appSettings.songSettings.titlePosition == Constants.ABOVE_VERSE) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = titleHorizontalAlignment) {
                            Text(modifier = Modifier.wrapContentWidth(), fontFamily = titleFontFamily, fontSize = scaledTitleFontSize, text = lyricSection.title, color = titleColor)
                        }
                    }
                    lyricSection.lines.forEachIndexed { _, line ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = lyricsHorizontalAlignment) {
                            Text(modifier = Modifier.wrapContentWidth(), fontFamily = lyricsFontFamily, fontSize = scaledLyricsFontSize, softWrap = appSettings.songSettings.wordWrap, text = line, color = lyricsColor)
                        }
                    }
                    if (shouldShowTitle && appSettings.songSettings.titlePosition == Constants.BELOW_VERSE) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = titleHorizontalAlignment) {
                            Text(modifier = Modifier.wrapContentWidth(), fontFamily = titleFontFamily, fontSize = scaledTitleFontSize, text = lyricSection.title, color = titleColor)
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