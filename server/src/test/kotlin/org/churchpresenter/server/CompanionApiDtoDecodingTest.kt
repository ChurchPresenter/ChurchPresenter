package org.churchpresenter.server

import kotlinx.serialization.json.Json
import org.churchpresenter.core.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompanionApiDtoDecodingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a schedule row carries every kind of item's fields off the wire`() {
        val item = json.decodeFromString<ScheduleItemDto>(
            """{"id":"r1","type":"song","displayText":"1 Grace","songNumber":12,"title":"Grace",
            "songbook":"Hymns","bookName":"John","chapter":3,"verseNumber":16,"verseRange":"16-17",
            "text":"Welcome","textColor":"#FFFFFF","backgroundColor":"#000000","folderPath":"/p",
            "folderName":"Pics","imageCount":4,"fileName":"deck.pptx","slideCount":9,"fileType":"pptx",
            "mediaUrl":"http://m","mediaTitle":"Clip","mediaType":"video","presetId":"lt1",
            "presetLabel":"Name","url":"https://site"}""",
        )
        assertEquals(12, item.songNumber)
        assertEquals("Grace", item.title)
        assertEquals("Hymns", item.songbook)
        assertEquals("John", item.bookName)
        assertEquals(3, item.chapter)
        assertEquals(16, item.verseNumber)
        assertEquals("16-17", item.verseRange)
        assertEquals("Welcome", item.text)
        assertEquals("#FFFFFF", item.textColor)
        assertEquals("#000000", item.backgroundColor)
        assertEquals("/p", item.folderPath)
        assertEquals("Pics", item.folderName)
        assertEquals(4, item.imageCount)
        assertEquals("deck.pptx", item.fileName)
        assertEquals(9, item.slideCount)
        assertEquals("pptx", item.fileType)
        assertEquals("http://m", item.mediaUrl)
        assertEquals("Clip", item.mediaTitle)
        assertEquals("video", item.mediaType)
        assertEquals("lt1", item.presetId)
        assertEquals("Name", item.presetLabel)
        assertEquals("https://site", item.url)
    }

    @Test
    fun `a schedule response counts its rows and a schedule song keeps its id`() {
        val response = json.decodeFromString<ScheduleResponse>(
            """{"items":[{"id":"a","type":"label","displayText":"A"}],"total":1}""",
        )
        assertEquals(1, response.total)
        assertEquals("s9", json.decodeFromString<ScheduleSongDto>(
            """{"id":"s9","songNumber":9,"title":"Nine","songbook":"Hymns"}""",
        ).id)
    }

    @Test
    fun `a chapter response names its book and lists its verses`() {
        val chapter = json.decodeFromString<BibleChapterResponse>(
            """{"translation":"KJV","book-id":43,"book-name":"John","chapter":3,"verse-total":1,
            "verses":[{"verse":16,"text":"For God so loved"}]}""",
        )
        assertEquals("KJV", chapter.translation)
        assertEquals(43, chapter.bookId)
        assertEquals("John", chapter.bookName)
        assertEquals(3, chapter.chapter)
        assertEquals(16, chapter.verses.single().verse)
        assertEquals("For God so loved", chapter.verses.single().text)
    }

    @Test
    fun `a bible catalog totals its books and verses`() {
        val catalog = json.decodeFromString<BibleCatalogResponse>(
            """{"translation":"KJV","book-total":1,"verse-total":31102,
            "books":[{"book-id":1,"book-name":"Genesis","chapter-total":50,"chapters":[]}]}""",
        )
        assertEquals(1, catalog.bookTotal)
        assertEquals(31102, catalog.verseTotal)
        assertEquals(1, catalog.books.single().bookId)
        assertEquals(50, catalog.books.single().chapterTotal)
    }

    @Test
    fun `a songbook lists its songs with their numbers, tunes and second titles`() {
        val book = json.decodeFromString<SongbookEntry>(
            """{"book-name":"Hymns","song-total":1,"songs":[{"id":4,"number":"12","title":"Grace",
            "tune":"New Britain","author":"Newton","secondaryTitle":"Gracia"}]}""",
        )
        val song = book.songs.single()
        assertEquals(4, song.id)
        assertEquals("12", song.number)
        assertEquals("Grace", song.title)
        assertEquals("New Britain", song.tune)
        assertEquals("Newton", song.author)
        assertEquals("Gracia", song.secondaryTitle)
    }

    @Test
    fun `a song's detail carries its credits and sections`() {
        val detail = json.decodeFromString<SongDetailDto>(
            """{"number":"12","title":"Grace","songbook":"Hymns","tune":"New Britain","author":"Newton",
            "composer":"Excell","section-total":1,"sections":[{"type":"chorus","lines":["How sweet"]}]}""",
        )
        assertEquals("12", detail.number)
        assertEquals("Hymns", detail.songbook)
        assertEquals("New Britain", detail.tune)
        assertEquals("Excell", detail.composer)
        assertEquals(1, detail.sectionTotal)
        assertEquals("chorus", detail.sections.single().type)
        assertEquals(listOf("How sweet"), detail.sections.single().lines)
    }

    @Test
    fun `a verse request can bring its own translation and text`() {
        val request = json.decodeFromString<SelectBibleVerseRequest>(
            """{"bookName":"John","chapter":3,"verseNumber":16,"bibleName":"King James",
            "bibleAbbreviation":"KJV","useClientText":true,"bookId":43}""",
        )
        assertEquals("King James", request.bibleName)
        assertEquals("KJV", request.bibleAbbreviation)
        assertTrue(request.useClientText)
        assertEquals(43, request.bookId)
    }

    @Test
    fun `a presentation and a picture folder list their slides and files`() {
        val deck = json.decodeFromString<PresentationDto>(
            """{"id":"d1","file-name":"deck.pptx","file-type":"pptx","slide-total":1,
            "slides":[{"slide-index":0,"thumbnail-url":"/s/0"}]}""",
        )
        assertEquals("d1", deck.id)
        assertEquals(0, deck.slides.single().slideIndex)
        assertEquals("/s/0", deck.slides.single().thumbnailUrl)
        val picture = json.decodeFromString<PictureFileDto>(
            """{"index":2,"file-name":"a.jpg","thumbnail-url":"/p/2"}""",
        )
        assertEquals(2, picture.index)
        assertEquals("a.jpg", picture.fileName)
        assertEquals("/p/2", picture.thumbnailUrl)
    }

    @Test
    fun `a live verse carries its text`() =
        assertEquals("For God so loved", json.decodeFromString<LiveStateDto>(
            """{"contentType":"BIBLE","verseText":"For God so loved"}""",
        ).verseText)

    @Test
    fun `requests leave out what the client did not say`() {
        val ack = CommandAckPayload(commandId = "c1", ok = true)
        assertNull(ack.reason)
        val picture = SelectPictureRequest(folderId = "f1")
        assertEquals(-1, picture.index)
        assertNull(picture.fileName)
        assertEquals("", SelectSlideRequest(index = 2).id)
        val status = StatusResponse()
        assertTrue(status.endpoints.isEmpty() && status.bibles.isEmpty() && status.songbooks.isEmpty())
        val label = ScheduleItem.LabelItem(id = "l1", text = "Hi", textColor = "#fff", backgroundColor = "#000")
        assertEquals(label, ProjectRequest(label).item)
    }
}
