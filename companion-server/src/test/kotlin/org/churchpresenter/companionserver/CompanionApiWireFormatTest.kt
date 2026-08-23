package org.churchpresenter.companionserver

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompanionApiWireFormatTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun <T> roundTrip(serializer: KSerializer<T>, value: T): T =
        json.decodeFromString(serializer, json.encodeToString(serializer, value))

    private fun <T> keysOf(serializer: KSerializer<T>, value: T): Set<String> =
        json.parseToJsonElement(json.encodeToString(serializer, value)).jsonObject.keys

    @Test
    fun `a song catalog survives the round trip`() {
        val catalog = SongCatalogResponse(
            songBook = listOf(
                SongbookEntry(
                    bookName = "Hymns",
                    songTotal = 2,
                    songs = listOf(
                        SongDto(
                            id = 0,
                            number = "42",
                            title = "Amazing Grace",
                            tune = "NEW BRITAIN",
                            author = "John Newton",
                        ),
                        SongDto(id = 1, number = "43", title = "It Is Well"),
                    ),
                ),
            ),
            songBooks = 1,
            total = 2,
        )

        assertEquals(catalog, roundTrip(SongCatalogResponse.serializer(), catalog))
    }

    @Test
    fun `the song catalog uses the hyphenated names the phone reads`() {
        val catalog = SongCatalogResponse(emptyList(), songBooks = 0, total = 0)
        assertEquals(setOf("song-book", "songBooks", "total"), keysOf(SongCatalogResponse.serializer(), catalog))

        val entry = SongbookEntry(bookName = "Hymns", songTotal = 0, songs = emptyList())
        assertEquals(setOf("book-name", "song-total", "songs"), keysOf(SongbookEntry.serializer(), entry))
    }

    @Test
    fun `a song detail survives the round trip`() {
        val detail = SongDetailDto(
            number = "42",
            title = "Amazing Grace",
            songbook = "Hymns",
            tune = "NEW BRITAIN",
            author = "John Newton",
            composer = "",
            sectionTotal = 2,
            sections = listOf(
                SongSectionDto(type = "verse", lines = listOf("Amazing grace, how sweet the sound")),
                SongSectionDto(type = "chorus", lines = listOf("Praise God")),
            ),
        )

        val decoded = roundTrip(SongDetailDto.serializer(), detail)

        assertEquals(detail, decoded)
        assertTrue("section-total" in keysOf(SongDetailDto.serializer(), detail))
    }

    @Test
    fun `a fully populated schedule item survives the round trip`() {
        val item = ScheduleItemDto(
            id = "item-1",
            type = "announcement",
            displayText = "Countdown",
            songNumber = 42,
            title = "Amazing Grace",
            songbook = "Hymns",
            bookName = "John",
            chapter = 3,
            verseNumber = 16,
            verseRange = "16-17",
            text = "Welcome",
            textColor = "#FFFFFF",
            backgroundColor = "#000000",
            fontSize = 72,
            animationType = "FADE",
            animationDuration = 12_000,
            isTimer = true,
            timerMode = "duration",
            timerHours = 1,
            timerMinutes = 2,
            timerSeconds = 3,
            timerExpiredText = "Starting now",
            targetHour = 10,
            targetMinute = 30,
            liveClockFormat = "HH:mm",
            folderPath = "/pictures/easter",
            folderName = "Easter",
            imageCount = 12,
            filePath = "/decks/sermon.pptx",
            fileName = "sermon.pptx",
            slideCount = 20,
            fileType = "pptx",
            mediaUrl = "rtsp://camera/1",
            mediaTitle = "Foyer camera",
            mediaType = "video",
            presetId = "preset-1",
            presetLabel = "Speaker name",
            url = "https://example.org",
        )

        val decoded = roundTrip(ScheduleItemDto.serializer(), item)

        assertEquals(item, decoded)
        assertEquals(72, decoded.fontSize)
        assertEquals("FADE", decoded.animationType)
        assertEquals(12_000, decoded.animationDuration)
        assertEquals(true, decoded.isTimer)
        assertEquals("duration", decoded.timerMode)
        assertEquals(1, decoded.timerHours)
        assertEquals(2, decoded.timerMinutes)
        assertEquals(3, decoded.timerSeconds)
        assertEquals("Starting now", decoded.timerExpiredText)
        assertEquals(10, decoded.targetHour)
        assertEquals(30, decoded.targetMinute)
        assertEquals("HH:mm", decoded.liveClockFormat)
    }

    @Test
    fun `a schedule song survives the round trip`() {
        val song = ScheduleSongDto(id = "1", songNumber = 42, title = "Amazing Grace", songbook = "Hymns")

        val decoded = roundTrip(ScheduleSongDto.serializer(), song)

        assertEquals(song, decoded)
        assertEquals(42, decoded.songNumber)
        assertEquals("Amazing Grace", decoded.title)
        assertEquals("Hymns", decoded.songbook)
    }

    @Test
    fun `a schedule response survives the round trip`() {
        val response = ScheduleResponse(
            items = listOf(ScheduleItemDto(id = "1", type = "song", displayText = "42 Amazing Grace")),
            total = 1,
        )

        assertEquals(response, roundTrip(ScheduleResponse.serializer(), response))
    }

    @Test
    fun `a live state snapshot survives the round trip`() {
        val state = LiveStateDto(
            contentType = "BIBLE",
            bookName = "John",
            chapter = 3,
            verseNumber = 16,
            verseRange = "16-17",
            verseText = "For God so loved the world",
            verseCodeBook = 43,
            verseCodeChapter = 3,
            verseCodeVerse = 16,
            songTitle = "Amazing Grace",
            songNumber = 42,
            sectionType = "verse",
            lines = listOf("Amazing grace"),
            songSectionIndex = 1,
            songLineIndex = 0,
            pictureFolderId = "folder-1",
            pictureIndex = 2,
            mediaId = "media-1",
            mediaUrl = "https://example.org/clip.mp4",
            mediaType = "video",
            announcementText = "Welcome",
            websiteUrl = "https://example.org",
            websiteTitle = "Example",
            sceneId = "scene-1",
            sceneName = "Opening",
            questionId = "q-1",
            questionText = "Where is the nursery?",
            dictionaryWord = "agape",
            lowerThirdName = "speaker",
        )

        assertEquals(state, roundTrip(LiveStateDto.serializer(), state))
    }

    @Test
    fun `the bible catalog uses the hyphenated names the phone reads`() {
        val catalog = BibleCatalogResponse(
            translation = "KJV",
            books = listOf(
                BibleBookDto(
                    bookId = 1,
                    bookName = "Genesis",
                    chapterTotal = 1,
                    chapters = listOf(BibleChapterDto(chapter = 1, verseTotal = 31)),
                ),
            ),
            bookTotal = 1,
            verseTotal = 31,
        )

        assertEquals(catalog, roundTrip(BibleCatalogResponse.serializer(), catalog))
        assertEquals(
            setOf("translation", "books", "book-total", "verse-total"),
            keysOf(BibleCatalogResponse.serializer(), catalog),
        )
        assertEquals(
            setOf("book-id", "book-name", "chapter-total", "chapters"),
            keysOf(BibleBookDto.serializer(), catalog.books[0]),
        )
    }

    @Test
    fun `a bible chapter survives the round trip`() {
        val chapter = BibleChapterResponse(
            translation = "KJV",
            bookId = 43,
            bookName = "John",
            chapter = 3,
            verseTotal = 2,
            verses = listOf(
                BibleVerseDto(verse = 16, text = "For God so loved the world"),
                BibleVerseDto(verse = 17, text = "For God sent not his Son"),
            ),
        )

        val decoded = roundTrip(BibleChapterResponse.serializer(), chapter)

        assertEquals(chapter, decoded)
        assertEquals(2, decoded.verseTotal)
    }

    @Test
    fun `a presentation catalog survives the round trip`() {
        val catalog = PresentationCatalogResponse(
            presentations = listOf(
                PresentationDto(
                    id = "deck-1",
                    fileName = "sermon",
                    fileType = "pptx",
                    slideTotal = 2,
                    slides = listOf(
                        SlideDto(slideIndex = 0, thumbnailUrl = "/api/presentations/deck-1/slides/0"),
                        SlideDto(slideIndex = 1, thumbnailUrl = "/api/presentations/deck-1/slides/1"),
                    ),
                ),
            ),
            total = 1,
        )

        assertEquals(catalog, roundTrip(PresentationCatalogResponse.serializer(), catalog))
        assertEquals(
            setOf("id", "file-name", "file-type", "slide-total", "slides"),
            keysOf(PresentationDto.serializer(), catalog.presentations[0]),
        )
        assertEquals(
            setOf("slide-index", "thumbnail-url"),
            keysOf(SlideDto.serializer(), catalog.presentations[0].slides[0]),
        )
    }

    @Test
    fun `a picture folder survives the round trip`() {
        val folder = PictureFolderResponse(
            folderId = "a1b2c3d4",
            folderName = "Easter 2026",
            folderPath = "/pictures/easter",
            imageTotal = 1,
            images = listOf(
                PictureFileDto(index = 0, fileName = "img001.jpg", thumbnailUrl = "/api/pictures/a1b2c3d4/images/0"),
            ),
        )

        assertEquals(folder, roundTrip(PictureFolderResponse.serializer(), folder))
        assertEquals(
            setOf("folder-id", "folder-name", "folder-path", "image-total", "images"),
            keysOf(PictureFolderResponse.serializer(), folder),
        )
        assertEquals(
            setOf("index", "file-name", "thumbnail-url"),
            keysOf(PictureFileDto.serializer(), folder.images[0]),
        )
    }

    @Test
    fun `a picture selection can name the file rather than trust the index`() {
        val request = SelectPictureRequest(folderId = "a1b2c3d4", index = 3, fileName = "img004.jpg")

        val decoded = roundTrip(SelectPictureRequest.serializer(), request)

        assertEquals(request, decoded)
        assertEquals("img004.jpg", decoded.fileName)
        assertTrue("folder-id" in keysOf(SelectPictureRequest.serializer(), request))
        assertTrue("file-name" in keysOf(SelectPictureRequest.serializer(), request))
    }

    @Test
    fun `a slide selection defaults its presentation id to empty`() {
        val decoded = json.decodeFromString(SelectSlideRequest.serializer(), """{"index":4}""")

        assertEquals("", decoded.id)
        assertEquals(4, decoded.index)
    }

    @Test
    fun `a verse selection defaults its optional text and range`() {
        val decoded = json.decodeFromString(
            SelectBibleVerseRequest.serializer(),
            """{"bookName":"John","chapter":3,"verseNumber":16}""",
        )

        assertEquals("John", decoded.bookName)
        assertEquals(3, decoded.chapter)
        assertEquals(16, decoded.verseNumber)
        assertEquals("", decoded.verseText)
        assertEquals("", decoded.verseRange)
    }

    @Test
    fun `a song section selection defaults to section-level navigation`() {
        val decoded = json.decodeFromString(
            SelectSongSectionRequest.serializer(),
            """{"number":"42","section":2}""",
        )

        assertEquals("42", decoded.number)
        assertEquals(2, decoded.section)
        assertEquals(-1, decoded.lineIndex)
    }

    @Test
    fun `the status response carries the endpoints and permissions a device may use`() {
        val status = StatusResponse(
            appVersion = "1.2.3",
            endpoints = listOf("/api/songs", "/api/bible"),
            bibles = listOf("kjv.spb"),
            songbooks = listOf("Hymns"),
            permissions = DevicePermissionsDto(
                canPresent = false,
                canAddToSchedule = true,
                canUploadFiles = false,
                maxMediaUploadMb = 25,
            ),
        )

        val decoded = roundTrip(StatusResponse.serializer(), status)

        assertEquals(status, decoded)
        assertEquals("1.2.3", decoded.appVersion)
        assertEquals(listOf("/api/songs", "/api/bible"), decoded.endpoints)
        assertEquals(listOf("kjv.spb"), decoded.bibles)
        assertEquals(listOf("Hymns"), decoded.songbooks)
        assertFalse(decoded.permissions.canPresent)
        assertTrue(decoded.permissions.canAddToSchedule)
        assertFalse(decoded.permissions.canUploadFiles)
        assertEquals(25, decoded.permissions.maxMediaUploadMb)
    }

    @Test
    fun `device permissions default to fully allowed`() {
        val permissions = DevicePermissionsDto()

        assertTrue(permissions.canPresent)
        assertTrue(permissions.canAddToSchedule)
        assertTrue(permissions.canUploadFiles)
        assertTrue(permissions.maxMediaUploadMb > 0)
    }

    @Test
    fun `the server info response carries its port`() {
        val info = ServerInfoResponse(port = 8080)

        val decoded = roundTrip(ServerInfoResponse.serializer(), info)

        assertEquals(info, decoded)
        assertEquals(8080, decoded.port)
        assertTrue(decoded.name.isNotBlank())
        assertTrue(decoded.version.isNotBlank())
    }

    @Test
    fun `a websocket message without a command id does not carry the field at all`() {
        val message = WebSocketMessage(type = "schedule_updated", payload = "{}")

        assertEquals(setOf("type", "payload"), keysOf(WebSocketMessage.serializer(), message))
        assertEquals(message, roundTrip(WebSocketMessage.serializer(), message))
    }

    @Test
    fun `a websocket message with a command id carries it`() {
        val message = WebSocketMessage(type = "select_slide", payload = "{}", commandId = "cmd-1")

        val decoded = roundTrip(WebSocketMessage.serializer(), message)

        assertEquals("cmd-1", decoded.commandId)
        assertTrue("commandId" in keysOf(WebSocketMessage.serializer(), message))
    }

    @Test
    fun `a command ack survives the round trip`() {
        val ack = CommandAckPayload(commandId = "cmd-1", ok = false, reason = "pending_approval")

        val decoded = roundTrip(CommandAckPayload.serializer(), ack)

        assertEquals(ack, decoded)
        assertEquals("cmd-1", decoded.commandId)
        assertFalse(decoded.ok)
        assertEquals("pending_approval", decoded.reason)
    }

    @Test
    fun `a remote item keeps every field it was sent with`() {
        val item = RemoteItemDto(
            id = "1",
            type = "announcement",
            songNumber = 42,
            title = "Amazing Grace",
            songbook = "Hymns",
            bookName = "John",
            chapter = 3,
            verseNumber = 16,
            verseText = "For God so loved the world",
            verseRange = "16-17",
            folderId = "folder-1",
            imageIndex = 2,
            folderPath = "/pictures/easter",
            folderName = "Easter",
            imageCount = 12,
            filePath = "/decks/sermon.pptx",
            fileName = "sermon.pptx",
            slideCount = 20,
            fileType = "pptx",
            mediaUrl = "https://example.org/clip.mp4",
            mediaTitle = "Clip",
            mediaType = "video",
            strongsNumber = "G26",
            transliteration = "agape",
            definition = "love",
            announcementText = "Welcome",
            textColor = "#FFFFFF",
            backgroundColor = "#000000",
            fontSize = 72,
            animationType = "FADE",
            animationDuration = 12_000,
            isTimer = true,
            timerHours = 1,
            timerMinutes = 2,
            timerSeconds = 3,
            timerTextColor = "#FF0000",
            timerExpiredText = "Starting now",
            timerMode = "duration",
            targetHour = 10,
            targetMinute = 30,
            targetSecond = 15,
            liveClockFormat = "HH:mm",
            url = "https://example.org",
            websiteTitle = "Example",
            displayText = "Welcome",
        )

        val decoded = roundTrip(RemoteItemDto.serializer(), item)

        assertEquals(item, decoded)
        assertEquals("Welcome", decoded.displayText)
    }

    @Test
    fun `a remote item uses the hyphenated picture names the companion app sends`() {
        val decoded = json.decodeFromString(
            RemoteItemDto.serializer(),
            """{"id":"1","folder-id":"a1b2c3d4","image-index":3}""",
        )

        assertEquals("a1b2c3d4", decoded.folderId)
        assertEquals(3, decoded.imageIndex)
    }

    @Test
    fun `a batch of remote items survives the round trip`() {
        val request = RemoteItemsRequest(
            items = listOf(
                RemoteItemDto(bookName = "John", chapter = 3, verseNumber = 16),
                RemoteItemDto(bookName = "John", chapter = 3, verseNumber = 17),
            ),
        )

        val decoded = roundTrip(RemoteItemsRequest.serializer(), request)

        assertEquals(request, decoded)
        assertEquals(2, decoded.items.size)
    }

    @Test
    fun `a single remote item request survives the round trip`() {
        val request = RemoteItemRequest(item = RemoteItemDto(id = "1", songNumber = 42))

        assertEquals(request, roundTrip(RemoteItemRequest.serializer(), request))
    }

    @Test
    fun `a remove request carries only the id`() {
        val request = RemoveFromScheduleRequest(id = "item-1")

        assertEquals(request, roundTrip(RemoveFromScheduleRequest.serializer(), request))
        assertEquals(setOf("id"), keysOf(RemoveFromScheduleRequest.serializer(), request))
    }
}
