package org.churchpresenter.app.churchpresenter.server

import org.churchpresenter.app.churchpresenter.models.songs.SongItem
import org.churchpresenter.app.churchpresenter.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How a batch add-to-schedule request is described to the operator.
 *
 * This is what the approval prompt and the activity toast both show, and it was computed twice in
 * `main.kt` — once for the auto-approved path and once for the prompt — in two spellings of the same
 * logic. The two are now one function, which is what these tests drive.
 *
 * The two properties that fail quietly if the shared version drifts: a single item is described by
 * [remoteEventLabel] rather than as "1 items", and the " …" suffix marks a *fourth* item, so exactly
 * three carries no ellipsis.
 */
class BatchEventSummaryTest {

    private fun song(number: Int = 42, title: String = "Amazing Grace") = ScheduleItem.SongItem(
        id = "s$number", songNumber = number, title = title, songbook = "Hymnal", songId = "sid",
    )

    private fun bibleVerse(book: String = "John", chapter: Int = 3, verse: Int = 16) =
        ScheduleItem.BibleVerseItem(
            id = "b$verse", bookName = book, chapter = chapter, verseNumber = verse,
            verseText = "For God so loved the world", verseRange = "16-18", bookId = 43,
        )

    private fun announcement(text: String = "Service starts at 10") =
        ScheduleItem.AnnouncementItem(id = "a", text = text)

    private fun website(title: String = "Notices") =
        ScheduleItem.WebsiteItem(id = "w", url = "https://example.org", title = title)

    private fun dictionary() = ScheduleItem.DictionaryItem(
        id = "d", number = "G5485", word = "charis", transliteration = "charis", definition = "grace",
    )

    // ── The title ───────────────────────────────────────────────────────────────

    @Test
    fun `a single item is named, not counted`() {
        val (title, _) = batchEventSummary(listOf(song()))

        assertEquals(remoteEventLabel(song()).first, title)
        assertFalse(title.contains("items"), "the operator is approving something specific, not a count: $title")
    }

    @Test
    fun `a single verse is named by its reference`() {
        val (title, _) = batchEventSummary(listOf(bibleVerse()))

        assertEquals(remoteEventLabel(bibleVerse()).first, title)
    }

    @Test
    fun `more than one item is counted`() {
        assertEquals("2 items", batchEventSummary(listOf(song(), bibleVerse())).first)
        assertEquals("5 items", batchEventSummary(List(5) { song(it) }).first)
    }

    // ── The " …" suffix, and its off-by-one ─────────────────────────────────────

    @Test
    fun `exactly three items carry no ellipsis`() {
        val (_, detail) = batchEventSummary(listOf(song(1), song(2), song(3)))

        assertFalse(detail.endsWith("…"), "the ellipsis means a fourth item is hidden; there is none: $detail")
        assertEquals(3, detail.split(" · ").size)
    }

    @Test
    fun `a fourth item is what adds the ellipsis`() {
        val (_, detail) = batchEventSummary(listOf(song(1), song(2), song(3), song(4)))

        assertTrue(detail.endsWith(" …"), detail)
        assertFalse(detail.contains("4 –"), "only the first three are listed: $detail")
    }

    @Test
    fun `only the first three are listed however long the batch`() {
        val (title, detail) = batchEventSummary(List(20) { song(it + 1) })

        assertEquals("20 items", title)
        assertEquals(3, detail.removeSuffix(" …").split(" · ").size)
    }

    // ── Per-item formatting ─────────────────────────────────────────────────────

    @Test
    fun `a verse is listed as book chapter and verse`() {
        val (_, detail) = batchEventSummary(listOf(bibleVerse(book = "Psalms", chapter = 23, verse = 1), song()))

        assertTrue(detail.startsWith("Psalms 23:1"), detail)
    }

    @Test
    fun `a song is listed as number and title`() {
        val (_, detail) = batchEventSummary(listOf(song(number = 7, title = "Be Thou My Vision"), song(8)))

        assertTrue(detail.startsWith("7 – Be Thou My Vision"), detail)
    }

    @Test
    fun `anything else is listed by its own display text`() {
        val (_, detail) = batchEventSummary(listOf(dictionary(), website(), announcement()))

        assertEquals(
            listOf(dictionary().displayText, website().displayText, announcement().displayText),
            detail.split(" · "),
        )
    }

    @Test
    fun `a long display text is truncated so one item cannot fill the line`() {
        val long = "A very long website title that would otherwise run off the end of the toast"
        val (_, detail) = batchEventSummary(listOf(website(title = long), song()))

        assertEquals(long.take(30), detail.split(" · ").first())
    }

    // ── Order and edges ─────────────────────────────────────────────────────────

    @Test
    fun `items are listed in the order they were sent`() {
        val (_, detail) = batchEventSummary(listOf(song(1, "First"), bibleVerse(verse = 2), website("Third")))

        assertEquals(listOf("1 – First", "John 3:2", "Third"), detail.split(" · "))
    }

    @Test
    fun `an empty batch does not throw`() {
        val (title, detail) = batchEventSummary(emptyList())

        assertEquals("0 items", title)
        assertEquals("", detail)
    }
}
