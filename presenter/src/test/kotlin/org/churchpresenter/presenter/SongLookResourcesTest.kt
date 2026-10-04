package org.churchpresenter.presenter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.sharedui.utils.Utils.parseHexColor
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SongLookResourcesTest {

    private val red = "#FF0000"
    private val green = "#00FF00"
    private val blue = "#0000FF"

    @Test
    fun `each colour follows its own setting through every output shape and the key`() = runComposeUiTest {
        var ss by mutableStateOf(SongSettings())
        var lowerThird by mutableStateOf(false)
        var lookAhead by mutableStateOf(false)
        var key by mutableStateOf(false)
        var res: SongLookResources? = null
        setContent { res = rememberSongLookResources(ss, lowerThird, lookAhead, key) }
        fun look(): SongLookResources { waitForIdle(); return checkNotNull(res) }

        assertEquals(parseHexColor(SongSettings().lyricsColor), look().lyricsColor)

        ss = ss.copy(titleColor = red, lyricsColor = green, lyricsChordColor = blue, lookAheadNextColor = red)
        look().let {
            assertEquals(parseHexColor(red), it.titleColor)
            assertEquals(parseHexColor(green), it.lyricsColor)
            assertEquals(parseHexColor(blue), it.chordColor)
            assertEquals(parseHexColor(red), it.laColor)
        }

        ss = ss.copy(titleFontType = "Serif", lyricsFontType = "Serif", lookAheadNextFontType = "Serif")
        look()

        lookAhead = true
        ss = ss.copy(lookAheadColor = blue, lookAheadFontType = "Monospaced")
        assertEquals(parseHexColor(blue), look().lyricsColor)

        lowerThird = true
        ss = ss.copy(
            titleLowerThirdColor = green,
            lowerThirdLookAheadColor = red,
            lyricsLowerThirdChordColor = green,
            lowerThirdLookAheadNextColor = blue,
            titleLowerThirdFontType = "Serif",
            lowerThirdLookAheadFontType = "Serif",
            lowerThirdLookAheadNextFontType = "Monospaced",
        )
        look().let {
            assertEquals(parseHexColor(green), it.titleColor)
            assertEquals(parseHexColor(red), it.lyricsColor)
            assertEquals(parseHexColor(green), it.chordColor)
            assertEquals(parseHexColor(blue), it.laColor)
        }

        lookAhead = false
        ss = ss.copy(lyricsLowerThirdColor = blue, lyricsLowerThirdFontType = "Monospaced")
        assertEquals(parseHexColor(blue), look().lyricsColor)

        key = true
        look().let {
            assertEquals(Color.White, it.titleColor)
            assertEquals(Color.White, it.lyricsColor)
            assertEquals(Color.White, it.chordColor)
            assertEquals(Color.White, it.laColor)
        }

        lookAhead = true
        lowerThird = false
        assertEquals(Color.White, look().lyricsColor)
    }
}
