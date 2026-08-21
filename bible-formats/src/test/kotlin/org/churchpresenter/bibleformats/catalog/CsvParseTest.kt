package org.churchpresenter.bibleformats.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The CSV reader the eBible catalogue is parsed with, on its own.
 *
 * eBible's `translations.csv` is a real-world CSV, not a comma-joined file: copyright strings carry
 * commas, quotes and the occasional embedded newline, and the archive is generated on machines that
 * disagree about line endings. Splitting on commas would drop a translation or shift every column
 * after the copyright — which shows up as a catalogue that is silently a few rows short, or as rows
 * whose language and title are swapped.
 *
 * The catalogue-level behaviour is covered by [EBibleSourceTest]; this is the parser underneath it,
 * exercised on the shapes that file actually contains.
 */
class CsvParseTest {

    @Test
    fun `plain rows split on commas`() {
        assertEquals(listOf(listOf("a", "b", "c"), listOf("1", "2", "3")), Csv.parse("a,b,c\n1,2,3"))
    }

    @Test
    fun `a quoted field keeps the commas inside it`() {
        assertEquals(
            listOf(listOf("eng", "Public Domain, mostly", "True")),
            Csv.parse("""eng,"Public Domain, mostly",True"""),
        )
    }

    /** A doubled quote is how CSV escapes one — the copyright fields use it for quoted names. */
    @Test
    fun `a doubled quote inside a quoted field becomes one quote`() {
        assertEquals(
            listOf(listOf("""the "King James" Version""")),
            Csv.parse(""""the ""King James"" Version""""),
        )
    }

    @Test
    fun `a newline inside a quoted field does not start a new row`() {
        val rows = Csv.parse("id,\"line one\nline two\",True")

        assertEquals(1, rows.size, "the embedded newline is part of the field, not a row break")
        assertEquals("line one\nline two", rows.single()[1])
    }

    @Test
    fun `CRLF and a bare CR are both single row breaks`() {
        assertEquals(listOf(listOf("a"), listOf("b")), Csv.parse("a\r\nb"))
        assertEquals(listOf(listOf("a"), listOf("b")), Csv.parse("a\rb"))
    }

    @Test
    fun `blank lines do not become empty rows`() {
        assertEquals(listOf(listOf("a"), listOf("b")), Csv.parse("a\n\n\nb\n"))
    }

    @Test
    fun `a trailing comma yields a trailing empty field rather than dropping it`() {
        assertEquals(listOf(listOf("a", "b", "")), Csv.parse("a,b,"))
    }

    @Test
    fun `a final row with no trailing newline is still returned`() {
        assertEquals(listOf(listOf("a", "b")), Csv.parse("a,b"))
    }

    @Test
    fun `an empty body parses to no rows rather than one empty one`() {
        assertTrue(Csv.parse("").isEmpty())
        assertTrue(Csv.parse("\n").isEmpty())
    }
}
