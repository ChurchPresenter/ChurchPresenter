package org.churchpresenter.presenter

import org.churchpresenter.settings.SongElementShift
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.withElementShift
import kotlin.test.Test
import kotlin.test.assertEquals

class SongElementMoveTest {

    @Test
    fun `a language's lines move by the element's move and their own together`() {
        val song = SongSettings()
            .withElementShift(songShiftKey(SongStyleElement.LYRICS, false), SongElementShift(10, 5))
            .withElementShift(songShiftKey(SongStyleElement.LYRICS, false, 1), SongElementShift(3, -2))
        assertEquals(13 to 3, song.elementMove(SongStyleElement.LYRICS, lowerThird = false, language = 1))
        assertEquals(10 to 5, song.elementMove(SongStyleElement.LYRICS, lowerThird = false, language = 0))
        assertEquals(0 to 0, song.elementMove(SongStyleElement.LYRICS, lowerThird = true, language = 1))
    }
}
