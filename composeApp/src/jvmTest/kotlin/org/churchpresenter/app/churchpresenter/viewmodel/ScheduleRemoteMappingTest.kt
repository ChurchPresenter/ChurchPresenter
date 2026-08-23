package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.companionserver.ScheduleItemDto
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ScheduleRemoteMappingTest {

    private lateinit var tempHome: File
    private var realHome: String? = null
    private lateinit var model: ScheduleViewModel

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-schedule-remote").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        model = ScheduleViewModel()
    }

    @AfterTest
    fun restoreHome() {
        runCatching { model.dispose() }
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    private fun apply(vararg dtos: ScheduleItemDto): List<ScheduleItem> {
        model.applyRemoteSchedule(dtos.toList())
        return model.scheduleItems
    }

    private fun dto(
        type: String,
        id: String = "1",
        displayText: String = "",
        build: ScheduleItemDto.() -> ScheduleItemDto = { this },
    ) =
        ScheduleItemDto(id = id, type = type, displayText = displayText).build()

    @Test
    fun `a song arrives with its number, title and songbook`() {
        val items = apply(
            dto("song") { copy(songNumber = 42, title = "Amazing Grace", songbook = "Hymns") },
        )

        val song = assertIs<ScheduleItem.SongItem>(items.single())
        assertEquals(42, song.songNumber)
        assertEquals("Amazing Grace", song.title)
        assertEquals("Hymns", song.songbook)
    }

    @Test
    fun `a song missing every optional field still maps`() {
        val song = assertIs<ScheduleItem.SongItem>(apply(dto("song")).single())

        assertEquals(0, song.songNumber)
        assertEquals("", song.title)
        assertEquals("", song.songbook)
    }

    @Test
    fun `a verse arrives with its reference, text and range`() {
        val items = apply(
            dto("bible") {
                copy(bookName = "John", chapter = 3, verseNumber = 16, text = "For God so loved", verseRange = "16-17")
            },
        )

        val verse = assertIs<ScheduleItem.BibleVerseItem>(items.single())
        assertEquals("John", verse.bookName)
        assertEquals(3, verse.chapter)
        assertEquals(16, verse.verseNumber)
        assertEquals("For God so loved", verse.verseText)
        assertEquals("16-17", verse.verseRange)
    }

    @Test
    fun `a verse missing every optional field still maps`() {
        val verse = assertIs<ScheduleItem.BibleVerseItem>(apply(dto("bible")).single())

        assertEquals("", verse.bookName)
        assertEquals(0, verse.chapter)
        assertEquals(0, verse.verseNumber)
        assertEquals("", verse.verseRange)
    }

    @Test
    fun `a label keeps the colors it was sent`() {
        val items = apply(
            dto("label") { copy(text = "Welcome", textColor = "#000000", backgroundColor = "#FF0000") },
        )

        val label = assertIs<ScheduleItem.LabelItem>(items.single())
        assertEquals("Welcome", label.text)
        assertEquals("#000000", label.textColor)
        assertEquals("#FF0000", label.backgroundColor)
    }

    @Test
    fun `a label with no colors falls back to the defaults rather than to blank`() {
        val label = assertIs<ScheduleItem.LabelItem>(apply(dto("label")).single())

        assertEquals("#FFFFFF", label.textColor)
        assertEquals("#2196F3", label.backgroundColor)
    }

    @Test
    fun `a picture arrives with its folder and count`() {
        val items = apply(
            dto("picture") { copy(folderPath = "/pictures/easter", folderName = "Easter", imageCount = 12) },
        )

        val picture = assertIs<ScheduleItem.PictureItem>(items.single())
        assertEquals("/pictures/easter", picture.folderPath)
        assertEquals("Easter", picture.folderName)
        assertEquals(12, picture.imageCount)
    }

    @Test
    fun `a picture missing every optional field still maps`() {
        val picture = assertIs<ScheduleItem.PictureItem>(apply(dto("picture")).single())

        assertEquals("", picture.folderPath)
        assertEquals(0, picture.imageCount)
    }

    @Test
    fun `a presentation arrives with its file, slide count and type`() {
        val items = apply(
            dto("presentation") {
                copy(filePath = "/decks/sermon.pptx", fileName = "sermon.pptx", slideCount = 20, fileType = "pptx")
            },
        )

        val deck = assertIs<ScheduleItem.PresentationItem>(items.single())
        assertEquals("/decks/sermon.pptx", deck.filePath)
        assertEquals("sermon.pptx", deck.fileName)
        assertEquals(20, deck.slideCount)
        assertEquals("pptx", deck.fileType)
    }

    @Test
    fun `a presentation missing every optional field still maps`() {
        val deck = assertIs<ScheduleItem.PresentationItem>(apply(dto("presentation")).single())

        assertEquals("", deck.filePath)
        assertEquals(0, deck.slideCount)
        assertEquals("", deck.fileType)
    }

    @Test
    fun `media arrives with its url, title and type`() {
        val items = apply(
            dto("media") { copy(mediaUrl = "rtsp://camera/1", mediaTitle = "Foyer", mediaType = "stream") },
        )

        val media = assertIs<ScheduleItem.MediaItem>(items.single())
        assertEquals("rtsp://camera/1", media.mediaUrl)
        assertEquals("Foyer", media.mediaTitle)
        assertEquals("stream", media.mediaType)
    }

    @Test
    fun `media missing every optional field still maps`() {
        val media = assertIs<ScheduleItem.MediaItem>(apply(dto("media")).single())

        assertEquals("", media.mediaUrl)
        assertEquals("", media.mediaType)
    }

    @Test
    fun `a lower third arrives with its preset`() {
        val items = apply(
            dto("lower_third") { copy(presetId = "preset-1", presetLabel = "Speaker name") },
        )

        val lowerThird = assertIs<ScheduleItem.LowerThirdItem>(items.single())
        assertEquals("preset-1", lowerThird.presetId)
        assertEquals("Speaker name", lowerThird.presetLabel)
    }

    @Test
    fun `a lower third missing its preset still maps`() {
        val lowerThird = assertIs<ScheduleItem.LowerThirdItem>(apply(dto("lower_third")).single())

        assertEquals("", lowerThird.presetId)
        assertEquals("", lowerThird.presetLabel)
    }

    @Test
    fun `an announcement keeps the colors it was sent`() {
        val items = apply(
            dto("announcement") { copy(text = "Welcome", textColor = "#111111", backgroundColor = "#222222") },
        )

        val announcement = assertIs<ScheduleItem.AnnouncementItem>(items.single())
        assertEquals("Welcome", announcement.text)
        assertEquals("#111111", announcement.textColor)
        assertEquals("#222222", announcement.backgroundColor)
    }

    @Test
    fun `an announcement with no colors falls back to white on black`() {
        val announcement = assertIs<ScheduleItem.AnnouncementItem>(apply(dto("announcement")).single())

        assertEquals("#FFFFFF", announcement.textColor)
        assertEquals("#000000", announcement.backgroundColor)
    }

    @Test
    fun `a website arrives with its url and title`() {
        val items = apply(dto("website") { copy(url = "https://example.org", title = "Example") })

        val site = assertIs<ScheduleItem.WebsiteItem>(items.single())
        assertEquals("https://example.org", site.url)
        assertEquals("Example", site.title)
    }

    @Test
    fun `an untitled website is titled with its own url`() {
        val site = assertIs<ScheduleItem.WebsiteItem>(
            apply(dto("website") { copy(url = "https://example.org") }).single(),
        )

        assertEquals("https://example.org", site.title)
    }

    @Test
    fun `a website with neither url nor title still maps`() {
        val site = assertIs<ScheduleItem.WebsiteItem>(apply(dto("website")).single())

        assertEquals("", site.url)
        assertEquals("", site.title)
    }

    @Test
    fun `a scene is named by its display text`() {
        val items = apply(dto("scene", displayText = "Opening"))

        val scene = assertIs<ScheduleItem.SceneItem>(items.single())
        assertEquals("Opening", scene.sceneName)
    }

    @Test
    fun `a dictionary entry carries the word from its display text`() {
        val items = apply(dto("dictionary", displayText = "agape"))

        val entry = assertIs<ScheduleItem.DictionaryItem>(items.single())
        assertEquals("agape", entry.word)
    }

    @Test
    fun `an item type this build does not know is dropped rather than guessed at`() {
        val items = apply(
            dto("song", id = "1") { copy(songNumber = 1) },
            dto("something_from_a_newer_primary", id = "2"),
            dto("song", id = "3") { copy(songNumber = 3) },
        )

        assertEquals(listOf("1", "3"), items.map { it.id })
    }

    @Test
    fun `applying a remote schedule takes local editing away`() {
        apply(dto("song") { copy(songNumber = 1) })

        assertTrue(model.isFollowingRemote)
    }

    @Test
    fun `disconnecting hands local editing back`() {
        apply(dto("song") { copy(songNumber = 1) })

        model.stopFollowingRemote()

        assertTrue(!model.isFollowingRemote)
    }

    @Test
    fun `a later remote schedule replaces the previous one entirely`() {
        apply(dto("song", id = "1") { copy(songNumber = 1) })

        val second = apply(dto("song", id = "2") { copy(songNumber = 2) })

        assertEquals(listOf("2"), second.map { it.id })
    }

    @Test
    fun `an empty remote schedule empties the local one`() {
        apply(dto("song", id = "1") { copy(songNumber = 1) })

        model.applyRemoteSchedule(emptyList())

        assertTrue(model.scheduleItems.isEmpty())
    }
}
