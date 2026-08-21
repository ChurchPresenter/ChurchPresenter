package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.models.bible.SelectedVerse
import org.churchpresenter.app.churchpresenter.server.SelectBibleVerseRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class MainDesktopRemoteVerseFallbackTest {

    private val request = SelectBibleVerseRequest(
        bookName = "John",
        chapter = 3,
        verseNumber = 16,
        verseText = "For God so loved the world.",
        verseRange = "16-18",
    )

    private fun resolved(vararg verseNumbers: Int) = verseNumbers.map {
        SelectedVerse(
            translationFileName = "kjv.spb",
            bibleAbbreviation = "KJV",
            bibleName = "King James Version",
            bookName = "John",
            chapter = 3,
            verseNumber = it,
            verseText = "verse $it as this machine has it",
        )
    }

    private fun call(resolved: List<SelectedVerse>) = remoteSelectedVerses(
        resolved = resolved,
        request = request,
        translationFileName = "niv.spb",
        bibleAbbreviation = "NIV",
        bibleName = "New International Version",
    )

    @Test
    fun `a locally resolved reference shows this machine's own text`() {
        val verses = call(resolved(16, 17, 18))

        assertEquals(3, verses.size)
        assertEquals("verse 16 as this machine has it", verses.first().verseText)
    }

    @Test
    fun `the requested range is stamped onto every resolved verse`() {
        val verses = call(resolved(16, 17, 18))

        assertEquals(
            listOf("16-18", "16-18", "16-18"), verses.map { it.verseRange },
            "the range is what the reference line renders from, so every verse has to carry it",
        )
    }

    @Test
    fun `a resolved verse keeps everything except its range`() {
        val before = resolved(16).single()
        val after = call(listOf(before)).single()

        assertEquals(before.copy(verseRange = "16-18"), after)
    }

    @Test
    fun `an unresolvable reference falls back to the text the request carried`() {
        val verses = call(emptyList())

        assertEquals(1, verses.size)
        assertEquals("For God so loved the world.", verses.single().verseText)
    }

    @Test
    fun `the fallback verse carries the requested reference`() {
        val verse = call(emptyList()).single()

        assertEquals("John", verse.bookName)
        assertEquals(3, verse.chapter)
        assertEquals(16, verse.verseNumber)
        assertEquals("16-18", verse.verseRange)
    }

    @Test
    fun `the fallback is styled as this instance's own bible, not the sender's`() {
        val verse = call(emptyList()).single()

        assertEquals("niv.spb", verse.translationFileName)
        assertEquals("NIV", verse.bibleAbbreviation)
        assertEquals("New International Version", verse.bibleName)
    }

    @Test
    fun `a single-verse request needs no range`() {
        val plain = SelectBibleVerseRequest(bookName = "John", chapter = 3, verseNumber = 16, verseText = "text")
        val verse = remoteSelectedVerses(emptyList(), plain, "kjv.spb", "KJV", "King James Version").single()

        assertEquals("", verse.verseRange)
        assertEquals(16, verse.verseNumber)
    }
}
