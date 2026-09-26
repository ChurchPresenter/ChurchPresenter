package org.churchpresenter.core.models.songs

import kotlin.test.Test
import kotlin.test.assertEquals

class SongLanguageFieldsTest {

    private fun song(title: String, vararg extraTitles: String) = SongItem(
        number = "",
        title = title,
        sourceFile = "/library/$title.song",
    ).withTranslations(extraTitles.map { SongTranslation(title = it, lyrics = listOf("line")) })

    @Test
    fun `the third and fourth language titles are read from their own slots`() {
        val song = song("Grace", "Благодать", "Благодать UA", "Ырайым")

        assertEquals("Благодать", SongField.SECONDARY_TITLE.of(song))
        assertEquals("Благодать UA", SongField.THIRD_TITLE.of(song))
        assertEquals("Ырайым", SongField.FOURTH_TITLE.of(song))
        assertEquals("", SongField.FOURTH_TITLE.of(song("Grace", "Благодать")))
    }

    @Test
    fun `setting a third title leaves the other languages and its own lyrics alone`() {
        val edited = SongField.THIRD_TITLE.set(song("Grace", "Благодать", "Old"), "  New  ")

        assertEquals(listOf("Благодать", "New"), edited.extraTranslations().map { it.title })
        assertEquals(listOf("line"), edited.extraTranslations()[1].lyrics)
    }

    @Test
    fun `setting a fourth title on a two-language song keeps the empty third slot between them`() {
        val edited = SongField.FOURTH_TITLE.set(song("Grace", "Благодать"), "Ырайым")

        assertEquals(listOf("Благодать", "", "Ырайым"), edited.extraTranslations().map { it.title })
    }

    @Test
    fun `a search finds a song by any language's title`() {
        val songs = listOf(song("Grace", "Благодать", "Ласка"), song("Vision", "Видение"))

        assertEquals(listOf("Grace"), SongGrid.rows(songs, GridView(query = "ласка")).map { it.title })
    }

    @Test
    fun `the third and fourth title columns sort by their own field, blanks last`() {
        val songs = listOf(song("A", "x", "Zulu"), song("B"), song("C", "x", "Alpha", "Mike"))

        assertEquals(
            listOf("C", "A", "B"),
            SongGrid.rows(songs, GridView(sortBy = SortColumn.THIRD_TITLE)).map { it.title },
        )
        assertEquals(
            listOf("C", "A", "B"),
            SongGrid.rows(songs, GridView(sortBy = SortColumn.FOURTH_TITLE)).map { it.title },
        )
    }
}
