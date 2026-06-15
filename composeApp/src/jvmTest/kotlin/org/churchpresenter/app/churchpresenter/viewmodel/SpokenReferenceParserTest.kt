package org.churchpresenter.app.churchpresenter.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.app.churchpresenter.viewmodel.SpokenReferenceParser.SpokenRef

/**
 * Tests the spoken-reference parser against real strings taken from the user's transcription
 * samples (the `*_Church.db` files). The parser only produces the raw book phrase + numbers; book
 * resolution is the ViewModel's job, so we assert on chapter/verse and that the book phrase contains
 * the expected book word.
 */
class SpokenReferenceParserTest {

    private fun ru(text: String): List<SpokenRef> = SpokenReferenceParser.parse(text, "ru")
    private fun en(text: String): List<SpokenRef> = SpokenReferenceParser.parse(text, "en")
    private fun one(refs: List<SpokenRef>): SpokenRef {
        assertEquals(1, refs.size, "expected exactly one reference, got $refs")
        return refs.first()
    }

    @Test fun ru_book_chapter_verse_with_commas() {
        val r = one(ru("прочитаем Слово Божие, которое записано в книге притчи, 30 глава, 5 стих."))
        assertEquals(30, r.chapter)
        assertEquals(5, r.verseStart)
        assertEquals(null, r.verseEnd)
        assertTrue(r.bookPhrase!!.contains("притчи"), "book phrase was ${r.bookPhrase}")
    }

    @Test fun ru_bare_verse_ordinal_word() {
        val r = one(ru("Пятый стих."))
        assertEquals(null, r.chapter)
        assertEquals(5, r.verseStart)
        assertEquals(null, r.bookPhrase)
    }

    @Test fun ru_spelled_ordinal_verse() {
        assertEquals(17, one(ru("Семнадцатый стих и принесен был камень")).verseStart)
        assertEquals(4, one(ru("Четвертый стих.")).verseStart)
    }

    @Test fun ru_compound_spelled_ordinal_verse() {
        assertEquals(23, one(ru("двадцать третий стих")).verseStart)
        assertEquals(21, one(ru("двадцать первый стих")).verseStart)
        assertEquals(35, one(ru("тридцать пятый стих")).verseStart)
    }

    @Test fun en_compound_spelled_chapter() {
        assertEquals(23, one(en("chapter twenty three")).chapter)
    }

    @Test fun ru_book_chapter_only() {
        val r = one(ru("Галатам, 1 глава, 1 стих."))
        assertEquals(1, r.chapter)
        assertEquals(1, r.verseStart)
        assertTrue(r.bookPhrase!!.contains("галатам"))
    }

    @Test fun ru_numbered_book() {
        val r = one(ru("1 Коринфянам, 1 глава"))
        assertEquals(1, r.chapter)
        assertTrue(r.bookPhrase!!.contains("коринфянам"))
        assertTrue(r.bookPhrase!!.contains("1"), "leading book number missing: ${r.bookPhrase}")
    }

    @Test fun ru_gospel_phrase_and_genitive_verse() {
        val r = one(ru("Ивангелие от Матфея, 28 глава, 18 стиха."))
        assertEquals(28, r.chapter)
        assertEquals(18, r.verseStart)
        assertTrue(r.bookPhrase!!.contains("матфея"))
    }

    @Test fun ru_verse_range_po() {
        val r = one(ru("Евангелие от Марка, 16 глава, 15 по 16 стих."))
        assertEquals(16, r.chapter)
        assertEquals(15, r.verseStart)
        assertEquals(16, r.verseEnd)
    }

    @Test fun ru_verse_range_s_po() {
        val r = one(ru("Деяние 16 глава с 30 по 31 стих"))
        assertEquals(16, r.chapter)
        assertEquals(30, r.verseStart)
        assertEquals(31, r.verseEnd)
    }

    @Test fun ru_hyphen_range() {
        val r = one(ru("Деяния 2 глава 37-38 стихи."))
        assertEquals(2, r.chapter)
        assertEquals(37, r.verseStart)
        assertEquals(38, r.verseEnd)
    }

    @Test fun ru_list_keeps_first_verse_only() {
        val r = one(ru("3 глава, 14 и 19 стихи."))
        assertEquals(3, r.chapter)
        assertEquals(14, r.verseStart)
        assertEquals(null, r.verseEnd) // non-contiguous list → just the first verse
    }

    @Test fun ru_ordinal_suffix_digits() {
        val r = one(ru("1-е послание Петра, 3-я глава, 21-й стих."))
        assertEquals(3, r.chapter)
        assertEquals(21, r.verseStart)
        assertTrue(r.bookPhrase!!.contains("петра"))
    }

    @Test fun ru_no_keyword_no_detection() {
        // "главное"/"главный" must NOT be read as a chapter word.
        assertTrue(ru("Но самое главное увидеть работу Духа Святого.").isEmpty())
        assertTrue(ru("И самый главный момент.").isEmpty())
        // A chapter word with no number is not a reference.
        assertTrue(ru("И пророка Данила очень интересная глава,").isEmpty())
    }

    @Test fun ru_no_false_number_from_common_words() {
        // "стоит" must not become 100; "однако" must not become 1.
        assertTrue(ru("нам стоит несколько раз прочитать книгу.").isEmpty())
    }

    @Test fun en_gospel_reference() {
        val r = one(en("The Gospel of Matthew, chapter 28, verse 18."))
        assertEquals(28, r.chapter)
        assertEquals(18, r.verseStart)
        assertTrue(r.bookPhrase!!.contains("matthew"))
    }

    @Test fun en_verse_range_to() {
        val r = one(en("Acts chapter 16 verses 30 to 31."))
        assertEquals(16, r.chapter)
        assertEquals(30, r.verseStart)
        assertEquals(31, r.verseEnd)
    }

    // ── Live-captured strings from the running STT server (192.168.2.62) ────────

    @Test fun live_galatians_chapter_accusative() {
        val r = one(ru("Мы читаем Галатам 1 главу."))
        assertEquals(1, r.chapter)
        assertEquals(null, r.verseStart)
        assertTrue(r.bookPhrase!!.contains("галатам"), "book phrase was ${r.bookPhrase}")
    }

    @Test fun live_bare_ordinal_verse() {
        val r = one(ru("Первый стих."))
        assertEquals(1, r.verseStart)
        assertEquals(null, r.chapter)
        assertEquals(null, r.bookPhrase)
    }

    @Test fun en_single_word_book_phrase_for_crossfill() {
        // English translation parse feeds book cross-fill when the Russian book name is garbled.
        val r = one(en("Philemon, chapter 1, verse 6."))
        assertEquals(1, r.chapter)
        assertEquals(6, r.verseStart)
        assertTrue(r.bookPhrase!!.contains("philemon"))
    }

    @Test fun ru_detected_even_with_non_russian_locale() {
        // The OS/UI locale may not be "ru"; Russian speech must still be detected by script.
        val r = one(SpokenReferenceParser.parse("Галатам 1 глава, 1 стих.", "en"))
        assertEquals(1, r.chapter)
        assertEquals(1, r.verseStart)
        assertTrue(r.bookPhrase!!.contains("галатам"))
    }

    @Test fun live_two_references_in_one_segment() {
        val refs = ru("Далее, 1 Коринфянам, 1 глава, 1 стих, и 2 Коринфянам, 1 глава, тоже стих, идентичный текст.")
        assertEquals(2, refs.size, "expected two references, got $refs")
        val first = refs[0]
        assertEquals(1, first.chapter)
        assertEquals(1, first.verseStart)
        assertTrue(first.bookPhrase!!.contains("коринфянам"))
        assertTrue(first.bookPhrase!!.contains("1"), "first leading number missing: ${first.bookPhrase}")
        val second = refs[1]
        assertEquals(1, second.chapter)
        assertTrue(second.bookPhrase!!.contains("коринфянам"))
        assertTrue(second.bookPhrase!!.contains("2"), "second leading number missing: ${second.bookPhrase}")
    }
}
