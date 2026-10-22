package org.churchpresenter.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * [resolvedFor] remembers each merged section by the identity of the document's section and the
 * profile's copy, so a settings change elsewhere decodes nothing and every output following the
 * same profile shares one decode.
 */
class ResolvedSectionsMemoTest {

    private val profile = OutputProfile(
        id = "p1",
        songSettings = SongSettings(lyricsColor = "#FF0000"),
        bibleSettings = BibleSettings(translations = listOf(BibleTranslationSettings(fileName = "KJV.spb"))),
        sttSettings = STTSettings(displayMode = "translate"),
        qaSettings = QASettings(fontSize = 60),
    )

    private val global = AppSettings(
        bibleSettings = BibleSettings(translations = listOf(BibleTranslationSettings(fileName = "KJV.spb"))),
    )

    @Test
    fun `a change outside the four sections reuses every merged section`() {
        val first = global.resolvedFor(profile)
        val second = global.copy(stockPhotoSettings = StockPhotoSettings(pexelsApiKey = "changed"))
            .resolvedFor(profile)

        assertSame(first.songSettings, second.songSettings)
        assertSame(first.bibleSettings, second.bibleSettings)
        assertSame(first.sttSettings, second.sttSettings)
        assertSame(first.qaSettings, second.qaSettings)
        assertEquals("#FF0000", second.songSettings.lyricsColor, "the profile's styling still wins")
    }

    @Test
    fun `changing one section of the document merges that section again and only that one`() {
        val first = global.resolvedFor(profile)
        val second = global.copy(songSettings = global.songSettings.copy(storageDirectory = "/church/songs"))
            .resolvedFor(profile)

        assertNotSame(first.songSettings, second.songSettings)
        assertEquals("/church/songs", second.songSettings.storageDirectory)
        assertSame(first.bibleSettings, second.bibleSettings)
        assertSame(first.sttSettings, second.sttSettings)
        assertSame(first.qaSettings, second.qaSettings)
    }

    @Test
    fun `changing the profile's copy of a section merges it again`() {
        val first = global.resolvedFor(profile)
        val restyled = profile.copy(qaSettings = profile.qaSettings.copy(fontSize = 72))
        val second = global.resolvedFor(restyled)

        assertNotSame(first.qaSettings, second.qaSettings)
        assertEquals(72, second.qaSettings.fontSize)
        assertSame(first.songSettings, second.songSettings, "the profile's other sections are the same objects")
    }

    @Test
    fun `outputs on different profiles each keep their own merge`() {
        val other = OutputProfile(id = "p2", songSettings = SongSettings(lyricsColor = "#0000FF"))
        val firstA = global.resolvedFor(profile).songSettings
        val firstB = global.resolvedFor(other).songSettings

        assertSame(firstA, global.resolvedFor(profile).songSettings)
        assertSame(firstB, global.resolvedFor(other).songSettings)
        assertEquals("#0000FF", firstB.lyricsColor)
    }

    @Test
    fun `recent merges keep the newest pairs up to their capacity`() {
        val merges = RecentMerges<String>(capacity = 2)
        var calls = 0
        val merge = { a: String, b: String -> calls++; a + b }
        val (a, b, c) = listOf(String(charArrayOf('a')), String(charArrayOf('b')), String(charArrayOf('c')))

        merges.of(a, b, merge)
        merges.of(a, b, merge)
        assertEquals(1, calls, "the same pair is merged once")

        merges.of(b, c, merge)
        merges.of(c, a, merge)
        assertEquals(3, calls)
        merges.of(a, b, merge)
        assertEquals(4, calls, "the oldest pair fell out once a third arrived")
        merges.of(String(charArrayOf('c')), a, merge)
        assertEquals(5, calls, "an equal but different instance is a different input")
    }
}
