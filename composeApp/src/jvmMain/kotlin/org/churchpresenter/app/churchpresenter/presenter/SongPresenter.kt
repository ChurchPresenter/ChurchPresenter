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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.dialogs.tabs.DropdownValues
import org.churchpresenter.app.churchpresenter.models.LyricSection
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
    val contentAlignment = when (appSettings.songSettings.lyricsAlignment) {
        DropdownValues.TOP -> Alignment.TopCenter
        DropdownValues.BOTTOM -> Alignment.BottomCenter
        else -> Alignment.Center
    }
    val horizontalAlignment = when (appSettings.songSettings.lyricsHorizontalAlignment) {
        DropdownValues.LEFT -> Arrangement.Start
        DropdownValues.RIGHT -> Arrangement.End
        else -> Arrangement.Center
    }
    Box(
        modifier.fillMaxSize().padding(16.dp),
        contentAlignment = contentAlignment
    ) {
        val alignment = when (appSettings.songSettings.songNumberPosition) {
            DropdownValues.TOP_LEFT -> Alignment.TopStart
            DropdownValues.TOP_RIGHT -> Alignment.TopEnd
            DropdownValues.BOTTOM_LEFT -> Alignment.BottomStart
            else -> Alignment.BottomEnd
        }
        Text(
            modifier = Modifier.wrapContentWidth().align(alignment),
            fontFamily = titleFontFamily,
            fontSize = appSettings.songSettings.songNumberFontSize.sp,
            text = lyricSection.songNumber.toString(),
            color = Color.White
        )
        Column(modifier = Modifier.wrapContentHeight()) {
            when (appSettings.songSettings.titleDisplay) {
                DropdownValues.FIRST_PAGE, DropdownValues.EVERY_PAGE -> {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            modifier = Modifier.wrapContentWidth().alpha(
                                appSettings.songSettings.titleAlpha / 100f
                            ),
                            fontFamily = titleFontFamily,
                            fontSize = appSettings.songSettings.titleFontSize.sp,
                            text = lyricSection.title,
                            color = Color.White
                        )
                    }
                }
            }
            lyricSection.lines.forEach { line ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = horizontalAlignment
                ) {
                    Text(
                        modifier = Modifier.wrapContentWidth().alpha(
                            appSettings.songSettings.lyricsAlpha / 100f
                        ),
                        fontFamily = lyricsFontFamily,
                        fontSize = appSettings.songSettings.lyricsFontSize.sp,
                        softWrap = appSettings.songSettings.wordWrap,
                        text = line,
                        color = Color.White
                    )
                }
            }
        }
    }
}