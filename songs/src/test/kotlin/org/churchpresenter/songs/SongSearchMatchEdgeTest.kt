package org.churchpresenter.songs

import org.churchpresenter.core.models.songs.SongItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongSearchMatchEdgeTest {

    private fun song(title: String = "Amazing Grace", lyrics: List<String> = emptyList()) =
        SongItem(number = "1", title = title, songbook = "Hymnal", lyrics = lyrics)

    @Test
    fun `no lyrics have no sections`() {
        assertTrue(searchableSections(emptyList()).isEmpty())
    }

    @Test
    fun `a header with nothing under it makes no section`() {
        assertEquals(
            listOf(SearchableSection("[Verse 2]", "words")),
            searchableSections(listOf("[Verse 1]", "[Verse 2]", "words")),
        )
    }

    @Test
    fun `runs of whitespace collapse to one space`() {
        assertEquals(
            listOf(SearchableSection(null, "a b c")),
            searchableSections(listOf("  a   b ", "c  ")),
        )
    }

    @Test
    fun `a header is kept trimmed`() {
        assertEquals("[Bridge]", searchableSections(listOf("  [Bridge]  ", "x")).single().header)
    }

    @Test
    fun `a query padded with spaces still matches the title`() {
        val match = findSongMatch(song(), "  amazing    grace ")
        assertEquals(SongMatchKind.TITLE, match?.kind)
    }

    @Test
    fun `words found nowhere are no match`() {
        assertNull(findSongMatch(song(lyrics = listOf("[Verse 1]", "how sweet")), "thunder"))
    }

    @Test
    fun `a pre-chorus counts as a chorus`() {
        val match = findSongMatch(song(lyrics = listOf("[Pre-Chorus]", "lift it up")), "lift")
        assertEquals(SongMatchKind.CHORUS, match?.kind)
        assertEquals("Pre-Chorus", match?.sectionName)
    }

    @Test
    fun `a bridge is another section, named`() {
        val match = findSongMatch(song(lyrics = listOf("[Bridge]", "high above")), "above")
        assertEquals(SongMatchKind.OTHER_SECTION, match?.kind)
        assertEquals("Bridge", match?.sectionName)
    }

    @Test
    fun `a supplied section reader is what is searched`() {
        val seen = mutableListOf<Int>()
        val match = findSongMatch(song(lyrics = listOf("ignored")), "secret") { index, _ ->
            seen += index
            listOf(SearchableSection("[Verse 1]", "a secret line"))
        }
        assertEquals(listOf(0), seen)
        assertEquals(SongMatchKind.VERSE, match?.kind)
        assertEquals("a secret line", match?.snippet)
    }

    @Test
    fun `a blank title is never matched`() {
        assertNull(findSongMatch(song(title = ""), "a"))
    }

    @Test
    fun `a snippet with no space to cut at keeps its leading ellipsis`() {
        val text = "abcdefghijklmnopqrstuvwxyz"
        assertEquals("…ijklmnopqrstuvwxyz", snippetAround(text, 20, 2))
    }

    @Test
    fun `a snippet with no space after the match is cut at the limit`() {
        val text = "x " + "a".repeat(100)
        assertEquals("x " + "a".repeat(61) + "…", snippetAround(text, 2, 1))
    }

    @Test
    fun `a match at the very start has no leading ellipsis`() {
        assertEquals("grace", snippetAround("grace", 0, 5))
    }
}
