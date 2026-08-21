package org.churchpresenter.app.churchpresenter.server

import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.bible.SelectedVerse
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CompanionServerLiveStateFieldsTest {

    private lateinit var server: CompanionServer
    private lateinit var dir: File

    @BeforeTest
    fun create() {
        server = CompanionServer()
        dir = Files.createTempDirectory("cp-live-fields").toFile()
    }

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun goLive(
        mode: String,
        verse: SelectedVerse? = null,
        section: LyricSection? = null,
        picturePath: String? = null,
        mediaUrl: String? = null,
        mediaType: String? = null,
        announcement: String? = null,
        websiteUrl: String? = null,
        websiteTitle: String? = null,
        sceneId: String? = null,
        sceneName: String? = null,
        questionId: String? = null,
        questionText: String? = null,
        dictionaryWord: String? = null,
        dictionaryEntry: StrongsEntry? = null,
        lowerThirdName: String? = null,
        verseCode: Triple<Int, Int, Int>? = null,
        songSectionIndex: Int? = null,
        songLineIndex: Int? = null,
    ): LiveStateDto {
        server.updateLiveState(
            mode = mode,
            bibleVerse = verse,
            lyricSection = section,
            pictureImagePath = picturePath,
            mediaUrl = mediaUrl,
            mediaType = mediaType,
            announcementText = announcement,
            websiteUrl = websiteUrl,
            websiteTitle = websiteTitle,
            sceneId = sceneId,
            sceneName = sceneName,
            questionId = questionId,
            questionText = questionText,
            dictionaryWord = dictionaryWord,
            dictionaryEntry = dictionaryEntry,
            lowerThirdName = lowerThirdName,
            verseCode = verseCode,
            songSectionIndex = songSectionIndex,
            songLineIndex = songLineIndex,
        )
        return assertNotNull(server.liveState.value)
    }

    @Test
    fun `a verse carries its range and canonical code`() {
        val state = goLive(
            mode = "BIBLE",
            verse = SelectedVerse(
                bookName = "John", chapter = 3, verseNumber = 16,
                verseRange = "16-17", verseText = "For God so loved the world",
            ),
            verseCode = Triple(43, 3, 16),
        )

        assertEquals("John", state.bookName)
        assertEquals(3, state.chapter)
        assertEquals(16, state.verseNumber)
        assertEquals("16-17", state.verseRange)
        assertEquals(43, state.verseCodeBook)
        assertEquals(3, state.verseCodeChapter)
        assertEquals(16, state.verseCodeVerse)
    }

    @Test
    fun `a verse with no canonical code omits all three code fields`() {
        val state = goLive(
            mode = "BIBLE",
            verse = SelectedVerse(bookName = "John", chapter = 3, verseNumber = 16),
        )

        assertNull(state.verseCodeBook)
        assertNull(state.verseCodeChapter)
        assertNull(state.verseCodeVerse)
    }

    @Test
    fun `a verse from a bible with no book name omits it rather than sending an empty one`() {
        val state = goLive(
            mode = "BIBLE",
            verse = SelectedVerse(bookName = "", chapter = 3, verseNumber = 16, verseRange = ""),
        )

        assertNull(state.bookName)
        assertNull(state.verseRange)
    }

    @Test
    fun `a song section carries its title, number and position`() {
        val state = goLive(
            mode = "LYRICS",
            section = LyricSection(
                title = "Amazing Grace", songNumber = 42, type = "verse",
                lines = listOf("Amazing grace, how sweet the sound"),
            ),
            songSectionIndex = 2,
            songLineIndex = 1,
        )

        assertEquals("Amazing Grace", state.songTitle)
        assertEquals(42, state.songNumber)
        assertEquals("verse", state.sectionType)
        assertEquals(listOf("Amazing grace, how sweet the sound"), state.lines)
        assertEquals(2, state.songSectionIndex)
        assertEquals(1, state.songLineIndex)
    }

    @Test
    fun `an untitled untyped section omits both rather than sending empty strings`() {
        val state = goLive(mode = "LYRICS", section = LyricSection(lines = listOf("La la la")))

        assertNull(state.songTitle)
        assertNull(state.sectionType)
        assertEquals(listOf("La la la"), state.lines)
    }

    @Test
    fun `a scheduled local video is reported by its schedule item id`() {
        val path = File(dir, "clip.mp4").apply { writeBytes(ByteArray(4)) }.absolutePath
        server.updateSchedule(
            listOf(
                ScheduleItem.MediaItem(
                    id = "media-item-1", mediaUrl = path, mediaTitle = "Clip", mediaType = "local",
                ),
            ),
        )

        val state = goLive(mode = "MEDIA", mediaUrl = path, mediaType = "local")

        assertEquals("media-item-1", state.mediaId)
        assertEquals(path, state.mediaUrl)
        assertEquals("local", state.mediaType)
    }

    @Test
    fun `a stream that is not in the schedule is reported by url alone`() {
        val state = goLive(mode = "MEDIA", mediaUrl = "rtsp://camera/1", mediaType = "stream")

        assertNull(state.mediaId)
        assertEquals("rtsp://camera/1", state.mediaUrl)
    }

    @Test
    fun `a blank media url and type are omitted`() {
        val state = goLive(mode = "MEDIA", mediaUrl = "", mediaType = "")

        assertNull(state.mediaUrl)
        assertNull(state.mediaType)
    }

    @Test
    fun `an announcement carries its text`() {
        val state = goLive(mode = "ANNOUNCEMENTS", announcement = "Service starts at 10")

        assertEquals("Service starts at 10", state.announcementText)
    }

    @Test
    fun `a blank announcement is omitted`() {
        val state = goLive(mode = "ANNOUNCEMENTS", announcement = "")

        assertNull(state.announcementText)
    }

    @Test
    fun `a website carries its url and title`() {
        val state = goLive(mode = "WEBSITE", websiteUrl = "https://example.org", websiteTitle = "Example")

        assertEquals("https://example.org", state.websiteUrl)
        assertEquals("Example", state.websiteTitle)
    }

    @Test
    fun `an untitled website omits the title but keeps the url`() {
        val state = goLive(mode = "WEBSITE", websiteUrl = "https://example.org", websiteTitle = "")

        assertEquals("https://example.org", state.websiteUrl)
        assertNull(state.websiteTitle)
    }

    @Test
    fun `a canvas scene carries its id and name`() {
        val state = goLive(mode = "CANVAS", sceneId = "scene-1", sceneName = "Opening")

        assertEquals("scene-1", state.sceneId)
        assertEquals("Opening", state.sceneName)
    }

    @Test
    fun `a question carries its id and text`() {
        val state = goLive(mode = "QA", questionId = "q-1", questionText = "Where is the nursery?")

        assertEquals("q-1", state.questionId)
        assertEquals("Where is the nursery?", state.questionText)
    }

    @Test
    fun `a dictionary word travels with the whole entry`() {
        val entry = StrongsEntry(
            number = "G26", word = "ἀγάπη", transliteration = "agape",
            pronunciation = "ag-ah'-pay", definition = "love", kjvUsage = "love",
        )

        val state = goLive(mode = "DICTIONARY", dictionaryWord = "agape", dictionaryEntry = entry)

        assertEquals("agape", state.dictionaryWord)
        assertEquals(entry, state.dictionaryEntry)
    }

    @Test
    fun `a dictionary word from an older primary arrives without an entry`() {
        val state = goLive(mode = "DICTIONARY", dictionaryWord = "agape")

        assertEquals("agape", state.dictionaryWord)
        assertNull(state.dictionaryEntry)
    }

    @Test
    fun `a lower third carries the name its file is looked up by`() {
        val state = goLive(mode = "LOWER_THIRD", lowerThirdName = "speaker")

        assertEquals("speaker", state.lowerThirdName)
    }

    @Test
    fun `a blank lower third name is omitted`() {
        val state = goLive(mode = "LOWER_THIRD", lowerThirdName = "")

        assertNull(state.lowerThirdName)
    }

    @Test
    fun `a picture is located in the folder it was registered under`() {
        val images = listOf("a.jpg", "b.jpg", "c.jpg").map {
            File(dir, it).apply { writeBytes(ByteArray(4)) }
        }
        server.updatePictures("folder-1", "Slides", dir.absolutePath, images)

        val state = goLive(mode = "PICTURE", picturePath = images[2].absolutePath)

        assertEquals("folder-1", state.pictureFolderId)
        assertEquals(2, state.pictureIndex)
    }

    @Test
    fun `showing the same thing twice leaves the snapshot untouched`() {
        val first = goLive(mode = "ANNOUNCEMENTS", announcement = "Welcome")
        val second = goLive(mode = "ANNOUNCEMENTS", announcement = "Welcome")

        assertEquals(first, second)
    }

    @Test
    fun `clearing the output replaces the snapshot with an empty one`() {
        goLive(mode = "BIBLE", verse = SelectedVerse(bookName = "John", chapter = 3, verseNumber = 16))

        val cleared = goLive(mode = "NONE")

        assertEquals("NONE", cleared.contentType)
        assertNull(cleared.bookName)
        assertNull(cleared.verseNumber)
    }
}
