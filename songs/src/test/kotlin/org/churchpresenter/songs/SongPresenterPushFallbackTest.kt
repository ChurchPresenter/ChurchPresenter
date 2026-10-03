package org.churchpresenter.songs

import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTranslation
import org.churchpresenter.core.models.songs.SongTuning
import kotlin.test.Test
import kotlin.test.assertEquals

class SongPresenterPushFallbackTest {

    @Test
    fun `the fallback slide for a song with a non-numeric number carries number zero`() =
        assertEquals(
            0,
            resolveEditedSongPush(emptyList(), 0, 0, SongItem(number = "12b", title = "Odd"), SongTuning())
                .section.songNumber,
        )

    @Test
    fun `a translation's background directives never reach its slide lines`() {
        val song = SongItem(
            number = "1", title = "A", lyrics = listOf("one"),
            translations = listOf(SongTranslation(title = "B", lyrics = listOf("[background: color]", "uno"))),
        )
        assertEquals(listOf("uno"), song.presentableTranslations().single().lines)
    }
}
