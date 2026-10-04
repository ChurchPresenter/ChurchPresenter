package org.churchpresenter.app.churchpresenter

import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.server.SelectBibleVerseRequest
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

    // ── A phone reading a Bible downloaded onto it ─────────────────────────────

    private val fromPhone = request.copy(
        verseText = "Бо так полюбив Бог світ",
        bibleName = "Біблія (Огієнко)",
        bibleAbbreviation = "UKR_OGI",
        useClientText = true,
        bookId = 43,
    )

    private fun callFromPhone(resolved: List<SelectedVerse>, sent: SelectBibleVerseRequest = fromPhone) =
        remoteSelectedVerses(resolved, sent, "niv.spb", "NIV", "New International Version")

    @Test
    fun `the phone's own text is shown even where this machine resolves the reference`() {
        val verse = callFromPhone(resolved(16, 17, 18)).single()

        assertEquals("Бо так полюбив Бог світ", verse.verseText, "the screen shows what the phone shows")
    }

    @Test
    fun `the phone's text is shown under the phone's translation`() {
        val verse = callFromPhone(resolved(16)).single()

        assertEquals("Біблія (Огієнко)", verse.bibleName)
        assertEquals("UKR_OGI", verse.bibleAbbreviation)
        assertEquals("niv.spb", verse.translationFileName, "the look is still keyed to this machine's profile")
    }

    @Test
    fun `a phone that names no translation is shown under this machine's`() {
        val verse = callFromPhone(emptyList(), fromPhone.copy(bibleName = "", bibleAbbreviation = "")).single()

        assertEquals("New International Version", verse.bibleName)
        assertEquals("NIV", verse.bibleAbbreviation)
    }

    @Test
    fun `the book id comes from the local lookup when there was one`() {
        val local = resolved(16).map { it.copy(bookId = 99) }
        assertEquals(99, callFromPhone(local).single().bookId)
    }

    @Test
    fun `the book id comes from the phone when nothing resolved here`() {
        assertEquals(43, callFromPhone(emptyList()).single().bookId)
    }

    @Test
    fun `asking for the phone's text with none sent shows this machine's instead`() {
        val verses = callFromPhone(resolved(16, 17, 18), fromPhone.copy(verseText = " "))

        assertEquals(3, verses.size)
        assertEquals("verse 16 as this machine has it", verses.first().verseText)
    }

    // ── Which verses a remote request counts as shown ──────────────────────────

    private fun numbers(range: String, verse: Int = 16) = remoteVerseNumbers(
        SelectBibleVerseRequest(bookName = "John", chapter = 3, verseNumber = verse, verseRange = range),
    )

    @Test
    fun `a single verse is one play`() {
        assertEquals(listOf(16), numbers(""))
    }

    @Test
    fun `a range counts every verse in it`() {
        assertEquals(listOf(16, 17, 18), numbers("16-18"))
    }

    @Test
    fun `a list counts each verse it names once`() {
        assertEquals(listOf(2, 4, 5), numbers("2,4,5,4", verse = 2))
    }

    @Test
    fun `ranges and single verses can be mixed`() {
        assertEquals(listOf(1, 2, 3, 7), numbers("1-3, 7", verse = 1))
    }

    @Test
    fun `a backwards range falls back to the single verse`() {
        assertEquals(listOf(16), numbers("18-16"))
    }

    @Test
    fun `an absurd range falls back rather than recording hundreds of plays`() {
        assertEquals(listOf(1), numbers("1-5000", verse = 1))
    }

    @Test
    fun `a range that is not numbers falls back to the single verse`() {
        assertEquals(listOf(16), numbers("sixteen"))
    }
}
