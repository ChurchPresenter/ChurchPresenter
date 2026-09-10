package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.BibleTranslationSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The reference line drawn under a verse — "KJV John 3:16".
 *
 * Every part is optional, and the parts are joined rather than interpolated so an absent one costs
 * no separator: the form this replaced always emitted its leading space, and a translation with no
 * abbreviation drew " John 3:16" with a gap in front of it.
 */
class BibleRefTextTest {

    private fun verse(
        book: String = "John",
        chapter: Int = 3,
        verseNumber: Int = 16,
        verseRange: String = "",
        abbreviation: String = "KJV",
    ) = SelectedVerse(
        bibleAbbreviation = abbreviation,
        bookName = book,
        chapter = chapter,
        verseNumber = verseNumber,
        verseRange = verseRange,
    )

    private fun translation(show: Boolean = true, custom: String = "") =
        BibleTranslationSettings(showAbbreviation = show, customAbbreviation = custom)

    @Test
    fun `the module's own abbreviation leads the reference`() {
        assertEquals("KJV John 3:16", buildRefText(verse(), translation()))
    }

    @Test
    fun `an abbreviation the operator typed wins over the module's`() {
        assertEquals("King James John 3:16", buildRefText(verse(), translation(custom = "King James")))
    }

    @Test
    fun `a custom abbreviation is trimmed before it is used`() {
        assertEquals("NIV John 3:16", buildRefText(verse(), translation(custom = "  NIV  ")))
    }

    @Test
    fun `a custom abbreviation of nothing but spaces falls back to the module's`() {
        assertEquals("KJV John 3:16", buildRefText(verse(), translation(custom = "   ")))
    }

    @Test
    fun `with the abbreviation switched off the reference starts at the book`() {
        val text = buildRefText(verse(), translation(show = false, custom = "King James"))
        assertEquals("John 3:16", text)
        assertFalse(text.startsWith(" "), "an absent label must not leave its separator behind")
    }

    @Test
    fun `a module with no abbreviation of its own draws none, and no gap`() {
        assertEquals("John 3:16", buildRefText(verse(abbreviation = ""), translation()))
    }

    @Test
    fun `a module abbreviation of nothing but spaces is dropped too`() {
        assertEquals("John 3:16", buildRefText(verse(abbreviation = "   "), translation()))
    }

    @Test
    fun `a verse range replaces the single verse number`() {
        assertEquals("KJV John 3:16-18", buildRefText(verse(verseRange = "16-18"), translation()))
    }

    @Test
    fun `a range is used verbatim, however it is written`() {
        val verse = verse(book = "Psalms", chapter = 23, verseRange = "1,3,5")
        assertEquals("KJV Psalms 23:1,3,5", buildRefText(verse, translation()))
    }

    @Test
    fun `the chapter and verse are always present`() {
        val verse = verse(book = "Genesis", chapter = 1, verseNumber = 1)
        assertEquals("KJV Genesis 1:1", buildRefText(verse, translation()))
    }

    @Test
    fun `a book name with a space in it stays whole`() {
        val verse = verse(book = "1 Corinthians", chapter = 13, verseNumber = 4)
        assertEquals("KJV 1 Corinthians 13:4", buildRefText(verse, translation()))
    }

    @Test
    fun `a verse with no book name still gives a usable reference`() {
        assertEquals("KJV 3:16", buildRefText(verse(book = ""), translation()))
    }
}
