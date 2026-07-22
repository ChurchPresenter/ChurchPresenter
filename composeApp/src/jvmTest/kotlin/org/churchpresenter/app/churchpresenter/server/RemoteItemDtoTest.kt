package org.churchpresenter.app.churchpresenter.server

import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Turning what a phone sends into a schedule item.
 *
 * The companion app and the phone-facing web page both post a single flat object with every field
 * optional, and the desktop works out what was meant by looking at which fields are present:
 * a `songNumber` makes it a song, a `bookName` + `chapter` + `verseNumber` makes it a verse, and so
 * on down a chain of nine tests in a fixed order. Nothing declares the type — `type` is carried but
 * never consulted — so the ORDER of that chain is the contract, and an object carrying two sets of
 * fields silently becomes whichever type is tested first.
 *
 * This is the app's only untyped boundary: the phone side is hand-written JavaScript that no
 * compiler checks against these classes. A renamed field here still compiles and simply stops the
 * phone working, and the failure appears on the desktop as an "Allow?" prompt that never arrives,
 * or as an item added with its fields blank.
 *
 * The fallbacks matter as much as the routing — a media item with no title falls back to its URL,
 * because a schedule row reading "🎬 " tells the operator nothing.
 */
class RemoteItemDtoTest {

    /** Configured as the companion server configures its own decoder. */
    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(payload: String): ScheduleItem? =
        json.decodeFromString(RemoteItemDto.serializer(), payload).toScheduleItem()

    // ── What each kind of payload becomes ───────────────────────────────────────

    @Test
    fun `a song is recognised by its number`() {
        val item = assertIs<ScheduleItem.SongItem>(
            parse("""{"id":"a","songNumber":42,"title":"Amazing Grace","songbook":"Hymnal"}"""),
        )

        assertEquals(42, item.songNumber)
        assertEquals("Amazing Grace", item.title)
        assertEquals("Hymnal", item.songbook)
        assertEquals("a", item.id)
    }

    @Test
    fun `a verse is recognised by book, chapter and verse together`() {
        val item = assertIs<ScheduleItem.BibleVerseItem>(
            parse("""{"id":"b","bookName":"John","chapter":3,"verseNumber":16,"verseText":"For God so loved…"}"""),
        )

        assertEquals("John", item.bookName)
        assertEquals(3, item.chapter)
        assertEquals(16, item.verseNumber)
        assertEquals("For God so loved…", item.verseText)
    }

    @Test
    fun `a verse range is carried through`() {
        val item = assertIs<ScheduleItem.BibleVerseItem>(
            parse("""{"bookName":"John","chapter":3,"verseNumber":16,"verseRange":"16-18"}"""),
        )

        assertEquals("16-18", item.verseRange, "without it the schedule row claims a single verse")
        assertEquals("John 3:16-18", item.displayText)
    }

    @Test
    fun `a picture folder is recognised by its path`() {
        val item = assertIs<ScheduleItem.PictureItem>(
            parse("""{"folderPath":"/pics/baptism","folderName":"Baptism","imageCount":24}"""),
        )

        assertEquals("/pics/baptism", item.folderPath)
        assertEquals(24, item.imageCount)
    }

    @Test
    fun `a presentation is recognised by its file path`() {
        val item = assertIs<ScheduleItem.PresentationItem>(
            parse("""{"filePath":"/decks/Sermon.pptx","fileName":"Sermon.pptx","slideCount":12,"fileType":"pptx"}"""),
        )

        assertEquals("/decks/Sermon.pptx", item.filePath)
        assertEquals("pptx", item.fileType)
    }

    @Test
    fun `media is recognised by its url`() {
        val item = assertIs<ScheduleItem.MediaItem>(
            parse("""{"mediaUrl":"/media/clip.mp4","mediaTitle":"Baptism video","mediaType":"local"}"""),
        )

        assertEquals("/media/clip.mp4", item.mediaUrl)
        assertEquals("Baptism video", item.mediaTitle)
    }

    @Test
    fun `a dictionary entry is recognised by its strongs number`() {
        val item = assertIs<ScheduleItem.DictionaryItem>(
            parse("""{"strongsNumber":"G26","title":"agape","transliteration":"agapē","definition":"love"}"""),
        )

        assertEquals("G26", item.number)
        assertEquals("agape", item.word, "the word travels in `title`, not in a field of its own")
        assertEquals("love", item.definition)
    }

    @Test
    fun `an announcement is recognised by its text`() {
        val item = assertIs<ScheduleItem.AnnouncementItem>(
            parse("""{"announcementText":"Welcome","textColor":"#FF0000","fontSize":72}"""),
        )

        assertEquals("Welcome", item.text)
        assertEquals("#FF0000", item.textColor)
        assertEquals(72, item.fontSize)
    }

    @Test
    fun `a timer with no words is still an announcement`() {
        // The discriminator is the field being PRESENT, not being non-empty — a pure countdown
        // carries an empty string, and treating that as "absent" would drop it entirely.
        val item = assertIs<ScheduleItem.AnnouncementItem>(
            parse("""{"announcementText":"","isTimer":true,"timerMinutes":5,"timerSeconds":30}"""),
        )

        assertTrue(item.isTimer)
        assertEquals(5, item.timerMinutes)
        assertEquals("Timer 05:30", item.displayText)
    }

    @Test
    fun `a countdown to a clock time carries its target`() {
        val item = assertIs<ScheduleItem.AnnouncementItem>(
            parse(
                """{"announcementText":"","isTimer":true,"timerMode":"${Constants.TIMER_MODE_CLOCK}",
                    "targetHour":10,"targetMinute":30,"targetSecond":5}""",
            ),
        )

        assertEquals(Constants.TIMER_MODE_CLOCK, item.timerMode)
        assertEquals("Until 10:30:05", item.displayText)
    }

    @Test
    fun `a website is recognised by its url`() {
        val item = assertIs<ScheduleItem.WebsiteItem>(parse("""{"url":"https://example.org","websiteTitle":"Giving"}"""))

        assertEquals("https://example.org", item.url)
        assertEquals("Giving", item.title)
    }

    // ── The order the tests are made in ─────────────────────────────────────────

    @Test
    fun `a payload carrying both a song number and a reference is read as a song`() {
        // The companion sends a song's own fields alongside leftovers from the previous selection
        // often enough that this order is load-bearing rather than theoretical.
        val item = parse("""{"songNumber":42,"title":"Amazing Grace","bookName":"John","chapter":3,"verseNumber":16}""")

        assertIs<ScheduleItem.SongItem>(item, "the song number is tested first and must win")
    }

    @Test
    fun `the routing order is fixed`() {
        // Each of these carries the fields of the type named AND of every type tested after it.
        val laterFields = """"folderPath":"/p","filePath":"/d","mediaUrl":"/m","strongsNumber":"G1",
                             "announcementText":"x","url":"https://example.org""""

        assertIs<ScheduleItem.SongItem>(parse("""{"songNumber":1,$laterFields}"""))
        assertIs<ScheduleItem.BibleVerseItem>(parse("""{"bookName":"John","chapter":3,"verseNumber":16,$laterFields}"""))
        assertIs<ScheduleItem.PictureItem>(
            parse("""{"folderPath":"/p","filePath":"/d","mediaUrl":"/m","strongsNumber":"G1","announcementText":"x","url":"u"}"""),
        )
        assertIs<ScheduleItem.PresentationItem>(
            parse("""{"filePath":"/d","mediaUrl":"/m","strongsNumber":"G1","announcementText":"x","url":"u"}"""),
        )
        assertIs<ScheduleItem.MediaItem>(parse("""{"mediaUrl":"/m","strongsNumber":"G1","announcementText":"x","url":"u"}"""))
        assertIs<ScheduleItem.DictionaryItem>(parse("""{"strongsNumber":"G1","announcementText":"x","url":"u"}"""))
        assertIs<ScheduleItem.AnnouncementItem>(parse("""{"announcementText":"x","url":"u"}"""))
        assertIs<ScheduleItem.WebsiteItem>(parse("""{"url":"https://example.org"}"""))
    }

    @Test
    fun `a partial reference is not a verse`() {
        // All three fields are required; two of them is a payload that means nothing, and inventing
        // a chapter 0 verse would put a blank slide in the schedule.
        assertNull(parse("""{"bookName":"John","chapter":3}"""))
        assertNull(parse("""{"bookName":"John","verseNumber":16}"""))
        assertNull(parse("""{"chapter":3,"verseNumber":16}"""))
    }

    @Test
    fun `a payload matching nothing is refused`() {
        assertNull(parse("""{"id":"x","type":"song","displayText":"Amazing Grace"}"""), "a label alone is not an item")
        assertNull(parse("""{}"""))
    }

    @Test
    fun `the type field is carried but not trusted`() {
        // `type` is what the companion app *says* it is; the fields are what it actually sent.
        val item = parse("""{"type":"website","songNumber":42,"title":"Amazing Grace"}""")

        assertIs<ScheduleItem.SongItem>(item, "routing on an unverified label would let one bad client corrupt a service")
    }

    // ── What is filled in when a field is left out ──────────────────────────────

    @Test
    fun `a song with only a number still becomes an item`() {
        val item = assertIs<ScheduleItem.SongItem>(parse("""{"songNumber":42}"""))

        assertEquals("", item.title)
        assertEquals("", item.songbook)
        assertEquals("42 - ", item.displayText)
    }

    @Test
    fun `a folder with no name of its own is labelled with its path`() {
        val item = assertIs<ScheduleItem.PictureItem>(parse("""{"folderPath":"/pics/baptism"}"""))

        assertEquals("/pics/baptism", item.folderName, "a blank row cannot be told apart from any other")
        assertEquals(0, item.imageCount)
    }

    @Test
    fun `a deck with no name of its own is labelled with its path`() {
        val item = assertIs<ScheduleItem.PresentationItem>(parse("""{"filePath":"/decks/Sermon.pptx"}"""))

        assertEquals("/decks/Sermon.pptx", item.fileName)
        assertEquals("", item.fileType)
    }

    @Test
    fun `media with no title is labelled with its url and assumed local`() {
        val item = assertIs<ScheduleItem.MediaItem>(parse("""{"mediaUrl":"/media/clip.mp4"}"""))

        assertEquals("/media/clip.mp4", item.mediaTitle)
        assertEquals("local", item.mediaType, "the desktop plays it as a file unless told otherwise")
    }

    @Test
    fun `a website with no title is labelled with its address`() {
        val item = assertIs<ScheduleItem.WebsiteItem>(parse("""{"url":"https://example.org/give"}"""))

        assertEquals("https://example.org/give", item.title)
    }

    @Test
    fun `an announcement left unstyled gets the app's own defaults`() {
        val item = assertIs<ScheduleItem.AnnouncementItem>(parse("""{"announcementText":"Welcome"}"""))

        assertEquals("#FFFFFF", item.textColor)
        assertEquals("#000000", item.backgroundColor)
        assertEquals(48, item.fontSize)
        assertEquals("SLIDE_FROM_BOTTOM", item.animationType)
        assertEquals(500, item.animationDuration)
        assertEquals(Constants.TIMER_MODE_DURATION, item.timerMode)
        assertEquals("HH:mm:ss", item.liveClockFormat)
    }

    @Test
    fun `a timer with no colour of its own follows the announcement's`() {
        val item = assertIs<ScheduleItem.AnnouncementItem>(
            parse("""{"announcementText":"","isTimer":true,"textColor":"#00FF00"}"""),
        )

        assertEquals(
            "#00FF00",
            item.timerTextColor,
            "a countdown in the default white on a styled announcement looks like a rendering fault",
        )
    }

    @Test
    fun `a timer colour set explicitly wins`() {
        val item = assertIs<ScheduleItem.AnnouncementItem>(
            parse("""{"announcementText":"","isTimer":true,"textColor":"#00FF00","timerTextColor":"#FF0000"}"""),
        )

        assertEquals("#FF0000", item.timerTextColor)
    }

    // ── Identity ────────────────────────────────────────────────────────────────

    @Test
    fun `an item sent without an id is given one`() {
        val first = parse("""{"songNumber":42}""")!!
        val second = parse("""{"songNumber":42}""")!!

        assertTrue(first.id.isNotBlank(), "an id-less row cannot be reordered, removed or gone live to")
        assertNotEquals(first.id, second.id, "the same song added twice must stay two separate rows")
    }

    @Test
    fun `a blank id is replaced rather than kept`() {
        assertTrue(parse("""{"id":"","songNumber":42}""")!!.id.isNotBlank())
    }

    @Test
    fun `an id the phone chose is kept`() {
        // The phone uses it to match its own optimistic row against the one that comes back.
        assertEquals("phone-generated-id", parse("""{"id":"phone-generated-id","songNumber":42}""")!!.id)
    }

    // ── The wire itself ─────────────────────────────────────────────────────────

    @Test
    fun `the hyphenated picture fields the companion app sends are understood`() {
        // `folder-id` and `image-index` are not valid Kotlin names and are mapped by @SerialName;
        // losing that mapping makes every picture the app sends parse as nothing at all.
        val dto = json.decodeFromString(RemoteItemDto.serializer(), """{"folder-id":"abc","image-index":7}""")

        assertEquals("abc", dto.folderId)
        assertEquals(7, dto.imageIndex)
    }

    @Test
    fun `a payload from a newer client version still parses`() {
        val item = parse("""{"songNumber":42,"title":"Amazing Grace","someFutureField":{"nested":true}}""")

        assertIs<ScheduleItem.SongItem>(item, "one unknown key must not reject the whole request")
    }

    @Test
    fun `a display text the phone computed is ignored`() {
        // The desktop composes its own row label; taking the phone's would let a client write
        // arbitrary text into the operator's schedule.
        val item = parse("""{"songNumber":42,"title":"Amazing Grace","displayText":"anything at all"}""")!!

        assertEquals("42 - Amazing Grace", item.displayText)
    }

    @Test
    fun `a batch request carries its items in order`() {
        val batch = json.decodeFromString(
            RemoteItemsRequest.serializer(),
            """{"items":[
                {"bookName":"John","chapter":3,"verseNumber":16},
                {"bookName":"John","chapter":3,"verseNumber":17}
            ]}""",
        )

        val items = batch.items.mapNotNull { it.toScheduleItem() }
        assertEquals(2, items.size)
        assertEquals(
            listOf("John 3:16", "John 3:17"),
            items.map { it.displayText },
            "a passage added from a phone has to arrive in reading order",
        )
    }

    @Test
    fun `a single-item request unwraps to the same item`() {
        val request = json.decodeFromString(RemoteItemRequest.serializer(), """{"item":{"songNumber":42}}""")

        assertIs<ScheduleItem.SongItem>(request.item.toScheduleItem())
    }
}
