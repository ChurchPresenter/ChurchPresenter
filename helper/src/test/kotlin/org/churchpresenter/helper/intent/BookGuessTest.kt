package org.churchpresenter.helper.intent

import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.helperText
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_book_not_found
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class BookGuessTest {

    /** A Russian Bible: its own names first, the standard English one second. */
    private val books = listOf(
        listOf("Бытие", "Genesis"),
        listOf("Псалтирь", "Psalms"),
        listOf("От Иоанна", "John"),
        listOf("1 Коринфянам", "1 Corinthians"),
        listOf("Иуда", "Jude"),
    )

    @Test
    fun `a name, or the start of one, is sure and comes back as the Bible's own`() {
        assertEquals(BookGuess("От Иоанна", sure = true), guessBook(books, "John"))
        assertEquals(BookGuess("От Иоанна", sure = true), guessBook(books, "от иоанна"))
        assertEquals(BookGuess("Псалтирь", sure = true), guessBook(books, "ps"))
        assertEquals(BookGuess("1 Коринфянам", sure = true), guessBook(books, "1 Cor."))
    }

    @Test
    fun `an exact name wins over one it starts`() {
        assertEquals(BookGuess("Иуда", sure = true), guessBook(books, "Jude"))
    }

    @Test
    fun `a misspelling or a short form is a guess`() {
        assertEquals(BookGuess("От Иоанна", sure = false), guessBook(books, "Jhon"))
        assertEquals(BookGuess("Бытие", sure = false), guessBook(books, "Gensis"))
        assertEquals(BookGuess("Псалтирь", sure = false), guessBook(books, "Pslams"))
        assertEquals(BookGuess("От Иоанна", sure = false), guessBook(books, "jn"))
    }

    @Test
    fun `nothing close, or nothing typed, is no book`() {
        assertNull(guessBook(books, "Hezekiah"))
        assertNull(guessBook(books, " . "))
        assertNull(guessBook(books, "q"))
    }

    private val context = ResolveContext(language = "en", bibleBooks = books)

    private fun resolve(text: String) = RuleIntentResolver().resolveNow(text, context)

    @Test
    fun `against the loaded Bible a verse is shown by its own book name, and a misspelling asked about`() {
        val shown = assertIs<HelperAction.ShowBibleVerse>(assertIs<Resolution.Act>(resolve("show John 3:16")).action)
        assertEquals("От Иоанна", shown.book)
        assertEquals(16, shown.verse)
        val guess = assertIs<Resolution.DidYouMean>(resolve("show Jhon 3:16"))
        assertEquals(HelperText.Plain("От Иоанна 3:16"), guess.label)
        assertEquals("От Иоанна", assertIs<HelperAction.ShowBibleVerse>(guess.action).book)
    }

    @Test
    fun `a book the loaded Bible does not have is said so`() {
        val said = assertIs<HelperAction.Say>(assertIs<Resolution.Act>(resolve("show Hezekiah 3:16")).action)
        assertEquals(helperText(Res.string.helper_book_not_found, "Hezekiah"), said.text)
    }
}
