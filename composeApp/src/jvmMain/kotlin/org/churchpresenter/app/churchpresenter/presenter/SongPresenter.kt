package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.platform.Typeface
import androidx.compose.ui.text.style.TextAlign
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
    println("lyricsFontType ${appSettings.songSettings.lyricsFontType}")
    println("Using lyrics font family: $lyricsFontFamily")
    val contentAlignment = when (appSettings.songSettings.lyricsAlignment) {
        DropdownValues.TOP -> Alignment.TopCenter
        DropdownValues.BOTTOM -> Alignment.BottomCenter
        else -> Alignment.Center
    }

    Box(modifier.fillMaxSize(), contentAlignment = contentAlignment) {
        Column {
            lyricSection.lines.forEach { line ->
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    val textAlign: TextAlign = when (appSettings.songSettings.lyricsHorizontalAlignment) {
                        DropdownValues.LEFT -> TextAlign.Start
                        DropdownValues.RIGHT -> TextAlign.End
                        else -> TextAlign.Center
                    }
                    Text(
                        fontFamily = lyricsFontFamily,
                        fontSize = appSettings.songSettings.lyricsFontSize.sp,
                        textAlign = textAlign,
                        softWrap = appSettings.songSettings.wordWrap,
                        text = line,
                        color = Color.White
                    )
                }
            }
        }
    }
}