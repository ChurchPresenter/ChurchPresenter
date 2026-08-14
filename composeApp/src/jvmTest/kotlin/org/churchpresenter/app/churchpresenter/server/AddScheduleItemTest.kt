package org.churchpresenter.app.churchpresenter.server

import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * What an approved remote "add this to the schedule" request actually adds.
 *
 * This dispatch was written out four times in `main.kt` — twice for a single item, twice inside the
 * batch loop — and the two batch copies had drifted: they were missing the dictionary, announcement
 * and website branches, so those types fell to `else -> Unit`. `RemoteItemDto.toScheduleItem`
 * produces all three and `POST /api/schedule/add-batch` answers `{"ok":true,"added":N}` counting
 * every item it parsed, so a batch containing one told the phone it had been added while nothing
 * reached the schedule. `a mixed batch adds every item` below is the pin for that.
 *
 * Driven with a [ScheduleActionsRecorder] — `ScheduleActions` is a data class of lambdas, so
 * recording what it was asked to add needs no mock and the assertions are the real argument lists.
 *
 * Lived in `main.kt` until the extraction that added this file, in the same way
 * [ExecuteProjectItemTest] did for the project path.
 */
class AddScheduleItemTest {

    private fun song(number: Int = 42) =
        ScheduleItem.SongItem(
            id = "s",
            songNumber = number,
            title = "Amazing Grace",
            songbook = "Hymnal",
            songId = "sid"
        )

    private fun bibleVerse() = ScheduleItem.BibleVerseItem(
        id = "b", bookName = "John", chapter = 3, verseNumber = 16,
        verseText = "For God so loved", verseRange = "16", bookId = 43,
    )

    private fun presentation() = ScheduleItem.PresentationItem(
        id = "p", filePath = "/decks/sunday.pdf", fileName = "sunday", slideCount = 4, fileType = "pdf",
    )

    private fun picture() =
        ScheduleItem.PictureItem(id = "pic", folderPath = "/photos/advent", folderName = "advent", imageCount = 12)

    private fun media() =
        ScheduleItem.MediaItem(id = "m", mediaUrl = "/clips/welcome.mp4", mediaTitle = "Welcome", mediaType = "local")

    private fun dictionary() = ScheduleItem.DictionaryItem(
        id = "d", number = "G5485", word = "χάρις", transliteration = "charis", definition = "grace",
    )

    private fun announcement() = ScheduleItem.AnnouncementItem(id = "a", text = "Service starts at 10")

    private fun website() = ScheduleItem.WebsiteItem(id = "w", url = "https://example.org", title = "Notices")

    private fun add(item: ScheduleItem): Pair<ScheduleActionsRecorder, Boolean> {
        val recorder = ScheduleActionsRecorder()
        val added = addScheduleItem(item, recorder.actions())
        return recorder to added
    }

    // ── One of each ─────────────────────────────────────────────────────────────

    @Test
    fun `each kind of content reaches its own schedule action`() {
        // One test rather than eight: what this pins is that no branch calls a neighbour's action —
        // a picture added as a presentation is the copy-paste slip this shape invites.
        assertEquals(listOf("song:42:Amazing Grace:Hymnal:sid"), add(song()).first.added)
        assertEquals(listOf("bible:John:3:16:For God so loved:16:43"), add(bibleVerse()).first.added)
        assertEquals(listOf("presentation:/decks/sunday.pdf:sunday:4:pdf"), add(presentation()).first.added)
        assertEquals(listOf("picture:/photos/advent:advent:12"), add(picture()).first.added)
        assertEquals(listOf("media:/clips/welcome.mp4:Welcome:local"), add(media()).first.added)
        assertEquals(listOf("dictionary:G5485:χάρις:charis:grace"), add(dictionary()).first.added)
        assertEquals(listOf("announcement:a"), add(announcement()).first.added)
        assertEquals(listOf("website:https://example.org:Notices"), add(website()).first.added)
    }

    @Test
    fun `every kind of content reports that it was added`() {
        listOf(song(), bibleVerse(), presentation(), picture(), media(), dictionary(), announcement(), website())
            .forEach { assertTrue(add(it).second, "${it::class.simpleName} is schedule content") }
    }

    @Test
    fun `an announcement is handed over whole`() {
        // Its colour, font and timer fields ride on the item itself, so passing anything but the
        // original instance would quietly drop the styling the operator set up.
        val item = announcement()
        val recorder = ScheduleActionsRecorder()

        addScheduleItem(item, recorder.actions())

        assertSame(item, recorder.announcements.single())
    }

    // ── The song's extra half ───────────────────────────────────────────────────

    @Test
    fun `a song also asks the Songs tab to navigate to it`() {
        val item = song()
        val navigated = mutableListOf<ScheduleItem.SongItem>()

        addScheduleItem(item, ScheduleActionsRecorder().actions()) { navigated += it }

        assertSame(item, navigated.single(), "the tab needs the item itself to select the right song")
    }

    @Test
    fun `nothing but a song asks the Songs tab to navigate`() {
        var navigations = 0

        listOf(song(), bibleVerse(), presentation(), picture(), media(), dictionary(), announcement(), website())
            .forEach { addScheduleItem(it, ScheduleActionsRecorder().actions()) { navigations++ } }

        assertEquals(1, navigations, "only the song branch may pull the Songs tab away from what it is showing")
    }

    // ── Things that are not schedule content ────────────────────────────────────

    @Test
    fun `a label, a lower third and a scene add nothing`() {
        // A scene has an addScene action, but no remote path has ever called it — recorded here so
        // the gap is visible rather than looking like an oversight in this function.
        val notContent = listOf(
            ScheduleItem.LabelItem(id = "l", text = "Part 2", textColor = "#fff", backgroundColor = "#000"),
            ScheduleItem.LowerThirdItem(
                id = "lt", presetId = "welcome", presetLabel = "Welcome",
                pauseAtFrame = false, pauseDurationMs = 0,
            ),
            ScheduleItem.SceneItem(id = "sc", sceneId = "scene-1", sceneName = "Opening"),
        )

        notContent.forEach { item ->
            val (recorder, added) = add(item)
            assertFalse(added, "${item::class.simpleName} is not schedule content")
            assertTrue(recorder.added.isEmpty(), "${item::class.simpleName} must not add anything")
        }
    }

    // ── The bug this extraction fixed ───────────────────────────────────────────

    @Test
    fun `a mixed batch adds every item, in order`() {
        // The batch copies of this dispatch handled only song/bible/presentation/picture/media, so a
        // batch carrying a dictionary entry, an announcement or a website was answered
        // {"ok":true,"added":5} with three of them silently dropped. One dispatch is what stops the
        // single and batch paths drifting apart again.
        val batch = listOf(song(), bibleVerse(), dictionary(), announcement(), website())
        val recorder = ScheduleActionsRecorder()

        batch.forEach { addScheduleItem(it, recorder.actions()) }

        assertEquals(
            listOf(
                "song:42:Amazing Grace:Hymnal:sid",
                "bible:John:3:16:For God so loved:16:43",
                "dictionary:G5485:χάρις:charis:grace",
                "announcement:a",
                "website:https://example.org:Notices",
            ),
            recorder.added,
            "every item the endpoint counted in its `added` response has to actually be added",
        )
    }
}
