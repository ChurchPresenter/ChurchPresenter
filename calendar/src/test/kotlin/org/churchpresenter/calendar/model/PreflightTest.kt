package org.churchpresenter.calendar.model

import org.churchpresenter.calendar.CalendarBibleBook
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreflightTest {

    private val books = listOf(
        CalendarBibleBook(bookId = 1, name = "Genesis", verseCounts = listOf(31, 25)),
        CalendarBibleBook(bookId = 19, name = "Псалтирь", verseCounts = listOf(6, 12, 8)),
    )
    private val songs = listOf(
        SongItem(number = "0001", title = "Amazing Grace", songbook = "Hymns"),
        SongItem(number = "", title = "Untitled Chorus", songbook = "Hymns"),
    )

    private fun media(id: String, url: String) = ScheduleItem.MediaItem(id, url, "clip", "local")
    private fun deck(id: String, path: String) = ScheduleItem.PresentationItem(id, path, "deck", 3, "pptx")
    private fun pictures(id: String, path: String) = ScheduleItem.PictureItem(id, path, "folder", 4)
    private fun verse(id: String, book: String, chapter: Int, first: Int, range: String = "", bookId: Int = 0) =
        ScheduleItem.BibleVerseItem(id, book, chapter, first, "", range, bookId)

    private fun check(
        vararg items: ScheduleItem,
        songsNow: List<SongItem> = songs,
        booksNow: List<CalendarBibleBook> = books,
        present: Set<String> = emptySet(),
        resolve: (String) -> Int? = { null },
    ) = preflight(
        items.toList(),
        songsNow,
        booksNow,
        fileExists = { it in present },
        folderExists = { it in present },
        resolveBook = resolve,
    )

    @Test
    fun `a moved clip, deck or folder is reported, and a stream never is`() {
        val problems = check(
            media("m", "/gone/clip.mp4"),
            media("s", "https://example.org/stream"),
            deck("d", "/gone/deck.pptx"),
            pictures("p", "/gone/folder"),
            media("ok", "/here/clip.mp4"),
            present = setOf("/here/clip.mp4"),
        )
        assertEquals(
            mapOf(
                "m" to PreflightProblem.MISSING_FILE,
                "d" to PreflightProblem.MISSING_FILE,
                "p" to PreflightProblem.MISSING_FOLDER,
            ),
            problems,
        )
    }

    @Test
    fun `a deck and a folder that are there are not reported`() {
        assertTrue(check(deck("d", "/here.pptx"), pictures("p", "/here"), present = setOf("/here.pptx", "/here")).isEmpty())
    }

    @Test
    fun `a song in another book, or with another number, is not the same song`() {
        val otherBook = ScheduleItem.SongItem("a", 1, "Amazing Grace", "Carols", songId = "")
        val otherNumber = ScheduleItem.SongItem("b", 9, "Amazing Grace", "Hymns", songId = "")
        assertEquals(setOf("a", "b"), check(otherBook, otherNumber).keys)
    }

    @Test
    fun `chapter and verse zero are out of range`() {
        assertEquals(PreflightProblem.CHAPTER_OUT_OF_RANGE, check(verse("c", "Genesis", 0, 1))["c"])
        assertEquals(PreflightProblem.VERSE_OUT_OF_RANGE, check(verse("v", "Genesis", 1, 0))["v"])
    }

    @Test
    fun `a song is found by id, by book and number, or by book and title`() {
        val byId = ScheduleItem.SongItem("a", 1, "Amazing Grace", "Hymns", songId = "Hymns::0001")
        val byNumber = ScheduleItem.SongItem("b", 1, "Renamed", "Hymns", songId = "")
        val byTitle = ScheduleItem.SongItem("c", 0, "untitled chorus", "Hymns", songId = "")
        val gone = ScheduleItem.SongItem("d", 7, "Nowhere", "Hymns", songId = "Hymns::0007")
        assertEquals(mapOf("d" to PreflightProblem.SONG_NOT_IN_LIBRARY), check(byId, byNumber, byTitle, gone))
    }

    @Test
    fun `an unread library or Bible reports nothing`() {
        val song = ScheduleItem.SongItem("d", 7, "Nowhere", "Hymns", songId = "Hymns::0007")
        val verse = verse("v", "Nowhere", 1, 1)
        assertTrue(check(song, verse, songsNow = emptyList(), booksNow = emptyList()).isEmpty())
    }

    @Test
    fun `a verse is checked for its book, its chapter and the last verse of its range`() {
        val problems = check(
            verse("book", "Nowhere", 1, 1),
            verse("chapter", "Genesis", 3, 1),
            verse("verse", "Genesis", 2, 20, range = "20-26"),
            verse("ok", "genesis", 1, 30, range = "30-31"),
            verse("byId", "Whatever", 2, 12, bookId = 19),
        )
        assertEquals(
            mapOf(
                "book" to PreflightProblem.BOOK_NOT_IN_BIBLE,
                "chapter" to PreflightProblem.CHAPTER_OUT_OF_RANGE,
                "verse" to PreflightProblem.VERSE_OUT_OF_RANGE,
            ),
            problems,
        )
    }

    @Test
    fun `a typed book name is resolved the way go-live resolves it`() {
        val psalm = verse("v", "Psalm", 2, 12)
        assertEquals(mapOf("v" to PreflightProblem.BOOK_NOT_IN_BIBLE), check(psalm))
        assertTrue(check(psalm, resolve = { if (it == "Psalm") 19 else null }).isEmpty())
        assertEquals(setOf("Psalm"), typedBookNames(listOf(psalm, verse("id", "X", 1, 1, bookId = 1))))
    }

    @Test
    fun `a cue is judged by its payload`() {
        val cue = ScheduleItem.CueItem("c", CueAction.PROJECT, payload = deck("inner", "/gone.pptx"))
        val blank = ScheduleItem.CueItem("b", CueAction.BLANK)
        assertEquals(mapOf("c" to PreflightProblem.MISSING_FILE), check(cue, blank))
        assertEquals(setOf("Psalm"), typedBookNames(listOf(cue.copy(payload = verse("v", "Psalm", 1, 1)))))
    }

    @Test
    fun `each problem has its fix`() {
        assertEquals(ProblemFix.LOCATE_FILE, PreflightProblem.MISSING_FILE.fix)
        assertEquals(ProblemFix.LOCATE_FOLDER, PreflightProblem.MISSING_FOLDER.fix)
        listOf(
            PreflightProblem.SONG_NOT_IN_LIBRARY,
            PreflightProblem.BOOK_NOT_IN_BIBLE,
            PreflightProblem.CHAPTER_OUT_OF_RANGE,
            PreflightProblem.VERSE_OUT_OF_RANGE,
        ).forEach { assertEquals(ProblemFix.PICK_AGAIN, it.fix, it.name) }
    }

    @Test
    fun `the path a row points at, and the row pointed elsewhere`() {
        val target = File("/new/place/clip.mov")
        assertEquals(File("/old/clip.mp4"), media("m", "/old/clip.mp4").pointedPath())
        assertNull(media("s", "rtsp://cam/1").pointedPath())
        assertNull(song("x").pointedPath())
        assertNull(song("x").relocatedTo(target))

        val clip = media("m", "/old/clip.mp4").relocatedTo(target) as ScheduleItem.MediaItem
        assertEquals("m", clip.id)
        assertEquals(target.absolutePath, clip.mediaUrl)
        assertEquals("clip", clip.mediaTitle)

        val moved = deck("d", "/old/deck.pptx").relocatedTo(File("/new/talk.KEY")) as ScheduleItem.PresentationItem
        assertEquals("talk", moved.fileName)
        assertEquals("key", moved.fileType)
        assertEquals(3, moved.slideCount)
        assertEquals("talk (3 slides)", moved.displayText)

        val folder = pictures("p", "/old").relocatedTo(File("/new/photos"), imageCount = 9) as ScheduleItem.PictureItem
        assertEquals("photos", folder.folderName)
        assertEquals(9, folder.imageCount)
        assertEquals("photos (9 images)", folder.displayText)

        val cue = ScheduleItem.CueItem("c", CueAction.PROJECT, payload = deck("inner", "/old.pptx"))
        assertEquals(File("/old.pptx"), cue.pointedPath())
        val relocatedCue = cue.relocatedTo(File("/new.pptx")) as ScheduleItem.CueItem
        assertEquals("/new.pptx", (relocatedCue.payload as ScheduleItem.PresentationItem).filePath)
        assertNull(ScheduleItem.CueItem("b", CueAction.BLANK).relocatedTo(target))
    }

    @Test
    fun `pictures are counted by extension, whatever the case`() {
        val listing = listOf("a.JPG", "b.png", "notes.txt", "c.heic", "d", "e.webp")
        assertEquals(4, countImages(File("/any")) { listing })
        assertEquals(0, countImages(File("/missing")) { emptyList() })

        val folder = java.nio.file.Files.createTempDirectory("count-images").toFile()
        try {
            File(folder, "one.png").writeText("x")
            File(folder, "two.txt").writeText("x")
            assertEquals(1, countImages(folder), "the real listing")
            assertEquals(0, countImages(File(folder, "nowhere")), "a folder that is not there holds nothing")
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `a folder row points at its folder, and a bare cue at nothing`() {
        assertEquals(File("/pics"), pictures("p", "/pics").pointedPath())
        assertNull(ScheduleItem.CueItem("b", CueAction.BLANK).pointedPath())
    }

    private fun song(id: String) = ScheduleItem.SongItem(id, 1, "Song", "Hymns", songId = "Hymns::1")
}
