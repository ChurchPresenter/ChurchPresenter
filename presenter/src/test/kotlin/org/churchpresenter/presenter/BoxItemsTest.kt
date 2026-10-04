package org.churchpresenter.presenter

import androidx.compose.ui.geometry.Rect
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SectionTranslation
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.SongLayoutExtras
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.TextBoxOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which song and Bible items are boxed, under which keys, and what each boxed item draws. */
class BoxItemsTest {

    private val on = TextBox(enabled = true, xPercent = 0f, yPercent = 0f, widthPercent = 50f, heightPercent = 50f)

    private fun song(boxes: Map<String, TextBox>, shared: Boolean = false) = SongSettings(
        layoutExtras = SongLayoutExtras(textBoxes = boxes, textBoxOptions = TextBoxOptions(sharedLanguageBox = shared)),
    )

    // ── Song keys ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `lyrics are boxed per language, unless the languages share one box`() {
        val own = song(emptyMap())
        assertEquals("LYRICS#1@LT", own.songBoxKey(SongStyleElement.LYRICS, lowerThird = true, language = 1))
        assertEquals("TITLE", own.songBoxKey(SongStyleElement.TITLE, lowerThird = false, language = 1))
        val shared = song(emptyMap(), shared = true)
        assertEquals("LYRICS", shared.songBoxKey(SongStyleElement.LYRICS, lowerThird = false, language = 1))
    }

    @Test
    fun `the title slide's boxes are kept apart from the lyric slides'`() {
        val s = song(emptyMap())
        assertEquals("SLIDE_TITLE#0", s.titleSlideBoxKey(SongStyleElement.TITLE, lowerThird = false, language = 0))
        assertEquals("SLIDE_AUTHOR", s.titleSlideBoxKey(SongStyleElement.AUTHOR, lowerThird = false, language = 2))
    }

    @Test
    fun `an element is boxed only while its box is on`() {
        val s = song(mapOf("NUMBER" to on, "TITLE" to on.copy(enabled = false)))
        assertTrue(s.isBoxed(SongStyleElement.NUMBER, lowerThird = false))
        assertFalse(s.isBoxed(SongStyleElement.TITLE, lowerThird = false))
        assertFalse(s.isBoxed(SongStyleElement.NUMBER, lowerThird = true), "the band has boxes of its own")
    }

    // ── Song items ──────────────────────────────────────────────────────────────────────────────

    private val blocks = listOf(
        SongLanguageBlock(0, listOf("Amazing grace"), listOf("I once was lost")),
        SongLanguageBlock(1, listOf("Удивительная"), emptyList()),
    )

    @Test
    fun `a slide draws every boxed element with something to say, and nothing else`() {
        val s = song(mapOf("TITLE" to on, "LYRICS#1" to on, "NEXT_SECTION#0" to on, "NUMBER" to on))
        val items = songBoxItems(
            s,
            lowerThird = false,
            lyricsElement = SongStyleElement.LYRICS,
            slide = SongBoxSlide(
                blocks,
                listOf(blocks),
                SongBoxTexts(title = "Amazing Grace", number = null, label = "Verse"),
            ),
        )
        assertEquals(listOf("TITLE", "LYRICS#1", "NEXT_SECTION#0"), items.map { it.key })
        assertEquals(listOf("Удивительная"), items[1].lines)
        assertEquals(listOf("I once was lost"), items[2].lines)
    }

    @Test
    fun `languages sharing a box draw all their lines in it`() {
        val s = song(mapOf("LYRICS" to on), shared = true)
        val slide = SongBoxSlide(blocks, listOf(blocks), SongBoxTexts(null, null, null))
        val items = songBoxItems(s, false, SongStyleElement.LYRICS, slide)
        assertEquals(1, items.size)
        assertEquals(listOf("Amazing grace", "Удивительная"), items.single().lines)
    }

    @Test
    fun `keep clear stops each box short of the others`() {
        val area = Rect(0f, 0f, 1000f, 1000f)
        val topBox = TextBox(enabled = true, 0f, 0f, 100f, 30f)
        val underBox = TextBox(enabled = true, 0f, 20f, 100f, 80f)
        val top = SongBoxItem("A", topBox, SongStyleElement.TITLE, null, emptyList(), emptyList())
        val under = SongBoxItem("B", underBox, SongStyleElement.LYRICS, 0, emptyList(), emptyList())
        val kept = songBoxRects(listOf(top, under), area, keepClear = true)
        assertTrue(kept[0].bottom <= kept[1].top, "${kept[0]} ${kept[1]}")
        val loose = songBoxRects(listOf(top, under), area, keepClear = false)
        assertTrue(loose[0].bottom > loose[1].top)
    }

    // ── Fitting one language ────────────────────────────────────────────────────────────────────

    private val bilingual = LyricSection(
        lines = listOf("Amazing grace"),
        translations = listOf(SectionTranslation(lines = listOf("Удивительная благодать"))),
    )

    @Test
    fun `one language on its own is that language's lines as the primary`() {
        assertEquals(listOf("Удивительная благодать"), bilingual.onlyLanguage(1).lines)
        assertTrue(bilingual.onlyLanguage(1).translations.isEmpty())
        assertEquals(emptyList(), bilingual.onlyLanguage(3).lines)
    }

    @Test
    fun `boxed languages are left out of the shared fit`() {
        val withoutPrimary = bilingual.withoutLanguages(setOf(0))
        assertEquals(emptyList(), withoutPrimary.lines)
        assertEquals(listOf("Удивительная благодать"), withoutPrimary.translations.single().lines)
        assertEquals(emptyList(), bilingual.withoutLanguages(setOf(1)).translations.single().lines)
    }

    // ── Bible ───────────────────────────────────────────────────────────────────────────────────

    private val kjv = BibleTranslationSettings(fileName = "kjv.spb")
    private val verse = SelectedVerse(
        bookName = "John",
        chapter = 3,
        verseNumber = 16,
        verseText = "For God so loved the world",
        translationFileName = "kjv.spb",
    )

    @Test
    fun `a translation's verse and reference are boxed apart`() {
        val bible = BibleSettings(textBoxes = mapOf("REFERENCE#kjv.spb" to on))
        assertTrue(bible.isBoxed(kjv, BibleStyleElement.REFERENCE, lowerThird = false))
        assertFalse(bible.isBoxed(kjv, BibleStyleElement.TEXT, lowerThird = false))
        val items = bibleBoxItems(bible, lowerThird = false, shown = listOf(verse to kjv))
        assertEquals(1, items.size)
        assertTrue(items.single().text.contains("John 3:16"))
    }

    @Test
    fun `translations sharing a box draw all their text in the first one's`() {
        val rst = BibleTranslationSettings(fileName = "rst.spb")
        val bible = BibleSettings(
            textBoxes = mapOf("TEXT" to on),
            textBoxOptions = TextBoxOptions(sharedLanguageBox = true),
        )
        val second = verse.copy(verseText = "Ибо так возлюбил Бог мир", translationFileName = "rst.spb")
        val items = bibleBoxItems(bible, false, listOf(verse to kjv, second to rst))
        assertEquals(1, items.size)
        assertEquals("For God so loved the world\n\nИбо так возлюбил Бог мир", items.single().text)
    }
}
