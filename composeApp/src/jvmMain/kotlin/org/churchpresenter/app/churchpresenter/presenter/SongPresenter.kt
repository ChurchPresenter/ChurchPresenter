package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.Constants.VERSE_1_RUS
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault

@Composable
fun SongPresenter(
    modifier: Modifier = Modifier,
    lyricSection: LyricSection,
    appSettings: AppSettings,
) {
    val titleFontFamily = systemFontFamilyOrDefault(appSettings.songSettings.titleFontType)
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
    Box(
        modifier.fillMaxSize().padding(16.dp),
        contentAlignment = contentAlignment
    ) {
        val alignment = when (appSettings.songSettings.songNumberPosition) {
            Constants.TOP_LEFT -> Alignment.TopStart
            Constants.TOP_RIGHT -> Alignment.TopEnd
            Constants.BOTTOM_LEFT -> Alignment.BottomStart
            else -> Alignment.BottomEnd
        }
        val shouldShowTitle = shouldShowText(appSettings.songSettings.titleDisplay, lyricSection)
        val shouldShowSongNumber = shouldShowText(appSettings.songSettings.showNumber, lyricSection)
        if (shouldShowSongNumber) {
            Text(
                modifier = Modifier.wrapContentWidth().align(alignment),
                fontFamily = titleFontFamily,
                fontSize = appSettings.songSettings.songNumberFontSize.sp,
                text = lyricSection.songNumber.toString(),
                color = songNumberColor
            )
        }
        Column(modifier = Modifier.wrapContentHeight()) {
            if (shouldShowTitle && appSettings.songSettings.titlePosition == Constants.ABOVE_VERSE) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = titleHorizontalAlignment
                ) {
                    Text(
                        modifier = Modifier.wrapContentWidth(),
                        fontFamily = titleFontFamily,
                        fontSize = appSettings.songSettings.titleFontSize.sp,
                        text = lyricSection.title,
                        color = titleColor
                    )
                }
            }

            lyricSection.lines.forEachIndexed { _, line ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = lyricsHorizontalAlignment
                ) {
                    Text(
                        modifier = Modifier.wrapContentWidth(),
                        fontFamily = lyricsFontFamily,
                        fontSize = appSettings.songSettings.lyricsFontSize.sp,
                        softWrap = appSettings.songSettings.wordWrap,
                        text = line,
                        color = lyricsColor
                    )
                }
            }
            if (shouldShowTitle && appSettings.songSettings.titlePosition == Constants.BELOW_VERSE) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = titleHorizontalAlignment
                ) {
                    Text(
                        modifier = Modifier.wrapContentWidth(),
                        fontFamily = titleFontFamily,
                        fontSize = appSettings.songSettings.titleFontSize.sp,
                        text = lyricSection.title,
                        color = titleColor
                    )
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