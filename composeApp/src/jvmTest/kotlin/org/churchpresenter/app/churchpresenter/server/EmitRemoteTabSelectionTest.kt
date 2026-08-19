package org.churchpresenter.app.churchpresenter.server

import core.models.songs.SongItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Which tab a remotely-projected item asks to load its content.
 *
 * `executeProjectItem` adds the item to the schedule and flips `presentingMode`, but deliberately
 * does not push picture or slide content itself — the owning tab does, driven by these flows. A type
 * missing from the dispatch therefore goes live as an **empty screen**: the mode changes and nothing
 * loads. That is the failure these tests exist to catch, and it is silent.
 *
 * The dispatch was written twice in `main.kt` — once in the auto-approved branch and once in the
 * approval dialog's allow lambda — so the two could drift and a request would behave differently
 * depending on whether the operator had already trusted the device.
 *
 * Real [MutableSharedFlow]s with `replay = 1`, so an emission is observable straight after the call
 * with no collector, no dispatcher and no waiting.
 */
class EmitRemoteTabSelectionTest {

    private val songs = MutableSharedFlow<ScheduleItem.SongItem>(replay = 1)
    private val pictures = MutableSharedFlow<ScheduleItem.PictureItem>(replay = 1)
    private val presentations = MutableSharedFlow<ScheduleItem.PresentationItem>(replay = 1)

    private fun emit(item: ScheduleItem): Boolean =
        runBlocking { emitRemoteTabSelection(item, songs, pictures, presentations) }

    private fun song() = ScheduleItem.SongItem(
        id = "s", songNumber = 42, title = "Amazing Grace", songbook = "Hymnal", songId = "sid",
    )

    private fun picture() =
        ScheduleItem.PictureItem(id = "pic", folderPath = "/photos/advent", folderName = "advent", imageCount = 12)

    private fun presentation() = ScheduleItem.PresentationItem(
        id = "p", filePath = "/decks/sunday.pdf", fileName = "sunday", slideCount = 4, fileType = "pdf",
    )

    /** Every emission that landed, as (flow name -> item), so a test can assert nothing else fired. */
    private fun emitted(): Map<String, Any> = buildMap {
        songs.replayCache.firstOrNull()?.let { put("songs", it) }
        pictures.replayCache.firstOrNull()?.let { put("pictures", it) }
        presentations.replayCache.firstOrNull()?.let { put("presentations", it) }
    }

    // ── Each type reaches its own tab ───────────────────────────────────────────

    @Test
    fun `a song is handed to the songs tab`() {
        val item = song()
        assertTrue(emit(item))

        assertSame(item, songs.replayCache.single())
        assertEquals(setOf("songs"), emitted().keys, "a song must not wake another tab")
    }

    @Test
    fun `a picture is handed to the pictures tab`() {
        val item = picture()
        assertTrue(emit(item))

        assertSame(item, pictures.replayCache.single())
        assertEquals(setOf("pictures"), emitted().keys)
    }

    @Test
    fun `a presentation is handed to the presentation tab`() {
        val item = presentation()
        assertTrue(emit(item))

        assertSame(item, presentations.replayCache.single())
        assertEquals(setOf("presentations"), emitted().keys)
    }

    // ── The types that own no tab ───────────────────────────────────────────────

    @Test
    fun `a bible verse drives no tab load`() {
        val verse = ScheduleItem.BibleVerseItem(
            id = "b", bookName = "John", chapter = 3, verseNumber = 16,
            verseText = "For God so loved", verseRange = "16", bookId = 43,
        )

        assertFalse(emit(verse), "the presenter renders a verse itself; no tab has to load anything")
        assertTrue(emitted().isEmpty())
    }

    @Test
    fun `the remaining projectable types drive no tab load`() {
        val others = listOf(
            ScheduleItem.AnnouncementItem(id = "a", text = "Service starts at 10"),
            ScheduleItem.WebsiteItem(id = "w", url = "https://example.org", title = "Notices"),
            ScheduleItem.DictionaryItem(
                id = "d", number = "G5485", word = "charis", transliteration = "charis", definition = "grace",
            ),
            ScheduleItem.LabelItem(id = "l", text = "Welcome", textColor = "#FFFFFF", backgroundColor = "#000000"),
            ScheduleItem.MediaItem(id = "m", mediaUrl = "/clips/w.mp4", mediaTitle = "Welcome", mediaType = "local"),
        )

        others.forEach { assertFalse(emit(it), "$it should not drive a tab load") }
        assertTrue(emitted().isEmpty(), "nothing should have been emitted, got ${emitted()}")
    }

    // ── The property that makes a drift visible ─────────────────────────────────

    @Test
    fun `exactly the three content-owning types report a tab load`() {
        val reporting = listOf<ScheduleItem>(
            song(), picture(), presentation(),
            ScheduleItem.AnnouncementItem(id = "a", text = "x"),
            ScheduleItem.WebsiteItem(id = "w", url = "https://example.org"),
            ScheduleItem.LabelItem(id = "l", text = "x", textColor = "#FFFFFF", backgroundColor = "#000000"),
        ).filter { emit(it) }

        assertEquals(
            listOf<Class<*>>(
                ScheduleItem.SongItem::class.java,
                ScheduleItem.PictureItem::class.java,
                ScheduleItem.PresentationItem::class.java,
            ),
            reporting.map { it.javaClass },
        )
    }
}
