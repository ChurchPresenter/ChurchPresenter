package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.server.SongCatalogResponse
import org.churchpresenter.server.SongDetailDto
import org.churchpresenter.server.SongDto
import org.churchpresenter.server.SongSectionDto
import org.churchpresenter.server.SongbookEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteSongCatalogTest {

    private fun detail(vararg sections: SongSectionDto) = SongDetailDto(
        number = "1", title = "Grace", songbook = "Hymnal", tune = "", author = "", composer = "",
        sectionTotal = sections.size, sections = sections.toList(),
    )

    @Test
    fun `every songbook's songs become song items in that songbook`() {
        val catalog = SongCatalogResponse(
            songBook = listOf(
                SongbookEntry(
                    "Hymnal", 2,
                    listOf(
                        SongDto(number = "1", title = "Grace", tune = "Amazing", author = "Newton"),
                        SongDto(number = "2", title = "Vision"),
                    ),
                ),
                SongbookEntry("Choruses", 1, listOf(SongDto(number = "7", title = "Shine"))),
            ),
            songBooks = 2, total = 3,
        )

        val items = catalog.toSongItems()

        assertEquals(listOf("Hymnal", "Hymnal", "Choruses"), items.map { it.songbook })
        assertEquals(listOf("1", "2", "7"), items.map { it.number })
        assertEquals("Amazing", items[0].tune)
        assertEquals("Newton", items[0].author)
        assertTrue(items.all { it.lyrics.isEmpty() }, "the catalog carries no lyrics")
    }

    @Test
    fun `an empty catalog has no songs`() =
        assertEquals(emptyList(), SongCatalogResponse(emptyList(), 0, 0).toSongItems())

    @Test
    fun `a chorus gets the brace header and other sections a capitalised bracket one`() =
        assertEquals(
            listOf("[Verse]", "verse line", "{Chorus}", "chorus line", "[Bridge]"),
            detail(
                SongSectionDto("verse", listOf("verse line")),
                SongSectionDto("chorus", listOf("chorus line")),
                SongSectionDto("bridge", emptyList()),
            ).toRawLyrics(),
        )

    @Test
    fun `a song with no sections has no lyrics`() = assertEquals(emptyList(), detail().toRawLyrics())

    @Test
    fun `a section with no type, or one already capitalised, keeps the header as sent`() =
        assertEquals(
            listOf("[]", "line", "[Tag]"),
            detail(SongSectionDto("", listOf("line")), SongSectionDto("Tag", emptyList())).toRawLyrics(),
        )
}
