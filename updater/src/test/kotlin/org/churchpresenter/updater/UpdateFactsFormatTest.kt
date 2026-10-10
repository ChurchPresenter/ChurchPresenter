package org.churchpresenter.updater

import java.time.Instant
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The facts under the new version — when it was released and how big the installer is — and the
 * timestamps they are read from.
 */
class UpdateFactsFormatTest {

    @Test
    fun `sizes are whole decimal megabytes, rounded the way the OS rounds a file`() {
        assertEquals(601, megabytes(601_405_516L))
        assertEquals(84, megabytes(84_499_999L))
        assertEquals(85, megabytes(84_500_000L))
        assertEquals(0, megabytes(0L))
    }

    @Test
    fun `the release date is a long date in the given language`() {
        val released = Instant.parse("2026-10-08T09:01:10Z")
        assertEquals("October 8, 2026", releaseDate(released, Locale.US))
        assertEquals("8. Oktober 2026", releaseDate(released, Locale.GERMANY))
    }

    @Test
    fun `the release date is read on the UTC calendar, so a late-evening release names its own day`() {
        assertEquals("October 8, 2026", releaseDate(Instant.parse("2026-10-08T23:59:00Z"), Locale.US))
    }

    @Test
    fun `GitHub's timestamps parse, and anything else is no date rather than an error`() {
        assertEquals(Instant.parse("2026-09-28T09:01:10Z"), parseInstantOrNull("2026-09-28T09:01:10Z"))
        assertNull(parseInstantOrNull("last Tuesday"))
        assertNull(parseInstantOrNull(""))
    }

    @Test
    fun `notes under the ceiling are kept exactly as written`() {
        val notes = "**Songs**\n- Song compare (#667)\n"
        assertEquals(notes, capReleaseNotes(notes, max = notes.length))
    }

    @Test
    fun `a single line longer than the ceiling is cut at the ceiling, having no line to fall back to`() {
        assertEquals("x".repeat(10), capReleaseNotes("x".repeat(30), max = 10))
    }
}
