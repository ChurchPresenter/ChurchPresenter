package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTranslation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

/** Where [findSongMatch] says a search found a song, and the snippet it cuts around the match. */
class SongSearchMatchTest {

    private fun song(
        title: String = "Be Thou My Vision",
        lyrics: List<String> = emptyList(),
        translations: List<SongTranslation> = emptyList(),
    ) = SongItem(number = "1", title = title, songbook = "Hymnal", lyrics = lyrics).withTranslations(translations)

    // ── Titles ──────────────────────────────────────────────────────────────────

    @Test
    fun `the song's own title is a title match with no snippet`() {
        val match = findSongMatch(song(title = "Lord, I Lift Your Name"), "lord")
        assertEquals(SongSearchMatch(SongMatchKind.TITLE, null, 0, "", null), match)
    }

    @Test
    fun `a translated title names its language and shows the title it matched`() {
        val match = findSongMatch(
            song(title = "Слава Господу", translations = listOf(SongTranslation("English", "Glory to the Lord"))),
            "lord",
        )
        assertEquals(SongSearchMatch(SongMatchKind.TITLE, null, 1, "English", "Glory to the Lord"), match)
    }

    @Test
    fun `a title match wins over the same words in the lyrics`() {
        val match = findSongMatch(song(title = "Holy Lord", lyrics = listOf("[Verse 1]", "Lord of all")), "lord")
        assertEquals(SongMatchKind.TITLE, match?.kind)
    }

    // ── Sections ────────────────────────────────────────────────────────────────

    @Test
    fun `a verse match is named as the song file names it`() {
        val match = findSongMatch(song(lyrics = listOf("[Verse 2]", "O Lord of my heart")), "lord")
        assertEquals(SongMatchKind.VERSE, match?.kind)
        assertEquals("Verse 2", match?.sectionName)
    }

    @Test
    fun `a braced header is a chorus whatever it is called`() {
        val match = findSongMatch(song(lyrics = listOf("{Refrain}", "Lord God Almighty")), "almighty")
        assertEquals(SongMatchKind.CHORUS, match?.kind)
        assertEquals("Refrain", match?.sectionName)
    }

    @Test
    fun `a chorus named in another language is still a chorus`() {
        val match = findSongMatch(song(lyrics = listOf("[Приспів]", "Слава Тобі")), "слава")
        assertEquals(SongMatchKind.CHORUS, match?.kind)
        assertEquals("Приспів", match?.sectionName)
    }

    @Test
    fun `any other named section keeps its name`() {
        val match = findSongMatch(song(lyrics = listOf("[Bridge]", "Hallelujah")), "hallelujah")
        assertEquals(SongMatchKind.OTHER_SECTION, match?.kind)
        assertEquals("Bridge", match?.sectionName)
    }

    @Test
    fun `lyrics with no header above them are just lyrics`() {
        val match = findSongMatch(song(lyrics = listOf("Praise God from whom all blessings flow")), "blessings")
        assertEquals(SongMatchKind.LYRICS, match?.kind)
        assertNull(match?.sectionName)
    }

    @Test
    fun `the first section holding the words is the one reported`() {
        val match = findSongMatch(
            song(lyrics = listOf("[Verse 1]", "Amazing grace", "{Chorus}", "Grace upon grace")),
            "grace",
        )
        assertEquals("Verse 1", match?.sectionName)
    }

    // ── Languages ───────────────────────────────────────────────────────────────

    @Test
    fun `lyrics in a translation name its language`() {
        val match = findSongMatch(
            song(
                lyrics = listOf("[Куплет 1]", "Коли дивлюсь"),
                translations = listOf(SongTranslation("English", "", listOf("{Chorus}", "How great Thou art"))),
            ),
            "great",
        )
        assertEquals(SongSearchMatch(SongMatchKind.CHORUS, "Chorus", 1, "English", "How great Thou art"), match)
    }

    @Test
    fun `a translation with no language typed reports a blank label`() {
        val match = findSongMatch(
            song(translations = listOf(SongTranslation("", "", listOf("Сердца владыка")))),
            "владыка",
        )
        assertEquals(1, match?.languageIndex)
        assertEquals("", match?.languageLabel)
    }

    // ── What the search reads ───────────────────────────────────────────────────

    @Test
    fun `chords are not part of the text`() {
        val lyrics = listOf("[Verse 1]", "[G]Amazing grace how [C]sweet the sound")
        val match = findSongMatch(song(lyrics = lyrics), "how sweet")
        val snippet = match?.snippet.orEmpty()
        assertTrue("how sweet the sound" in snippet, snippet)
        assertFalse('[' in snippet, snippet)
    }

    @Test
    fun `a phrase runs across the lines of one section`() {
        val match = findSongMatch(
            song(lyrics = listOf("[Verse 1]", "how sweet the sound", "That saved a wretch")),
            "the sound that saved",
        )
        assertEquals(SongMatchKind.VERSE, match?.kind)
    }

    @Test
    fun `a phrase across two sections is still found, as lyrics`() {
        val match = findSongMatch(
            song(lyrics = listOf("[Verse 1]", "the end of one", "[Verse 2]", "the start of two")),
            "one the start",
        )
        assertEquals(SongMatchKind.LYRICS, match?.kind)
        assertNull(match?.sectionName)
    }

    @Test
    fun `headers, slide breaks and background lines are never matched`() {
        val lyrics = listOf("[Verse 1]", "Holy", "[---]", "[background: gradient]", "Holy")
        assertNull(findSongMatch(song(lyrics = lyrics), "verse"))
        assertNull(findSongMatch(song(lyrics = lyrics), "gradient"))
    }

    @Test
    fun `a blank query matches nothing`() {
        assertNull(findSongMatch(song(title = "Anything"), "   "))
    }

    // ── Snippets ────────────────────────────────────────────────────────────────

    @Test
    fun `a snippet is cut at words, with an ellipsis where it was trimmed`() {
        val text = "one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen " +
            "sixteen seventeen eighteen nineteen twenty twenty-one twenty-two twenty-three twenty-four"
        val at = text.indexOf("eight")
        val snippet = snippetAround(text, at, "eight".length)

        assertTrue(snippet.startsWith("…") && snippet.endsWith("…"), snippet)
        assertTrue("eight" in snippet, snippet)
        val words = text.split(' ').toSet()
        assertTrue(snippet.trim('…').split(' ').all { it in words }, "whole words only: $snippet")
        assertTrue(snippet.length < text.length, snippet)
    }

    @Test
    fun `a snippet of short text is the whole text`() {
        assertEquals("Lord God Almighty", snippetAround("Lord God Almighty", 0, 4))
    }
}
