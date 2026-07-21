package org.churchpresenter.app.churchpresenter.models

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The on-disk shape of a saved service.
 *
 * `ScheduleViewModel` writes the schedule to a file the church keeps and reopens weeks later, and
 * the same classes go over the wire to phones and to a linked instance. That makes them a format:
 * a renamed field or a moved polymorphic discriminator compiles fine and turns last year's saved
 * services into a parse error, while a changed default quietly rewrites items an older build saved.
 *
 * `displayText` is deliberately part of the payload rather than recomputed on load — see
 * [ScheduleItemDisplayTextTest] for why it is frozen at construction. That means a saved row keeps
 * the label it was saved with, which these pin too.
 */
class ScheduleItemSerializationTest {

    /** Configured as `ScheduleViewModel` configures its own encoder, minus the pretty printing. */
    private val json = Json { encodeDefaults = true }

    /** Lenient, as a schedule from another build is read. */
    private val lenient = Json { ignoreUnknownKeys = true }

    private inline fun <reified T : ScheduleItem> roundTrip(item: T): T {
        val encoded = json.encodeToString(ScheduleItem.serializer(), item)
        val decoded = json.decodeFromString(ScheduleItem.serializer(), encoded)
        return assertIs<T>(decoded, "a ${T::class.simpleName} came back as ${decoded::class.simpleName}")
    }

    private fun discriminatorOf(item: ScheduleItem): String =
        json.decodeFromString(JsonObject.serializer(), json.encodeToString(ScheduleItem.serializer(), item))
            .getValue("type").jsonPrimitive.content

    // ── Each kind of item ───────────────────────────────────────────────────────

    @Test
    fun `a song keeps what is needed to find it again in the library`() {
        val song = ScheduleItem.SongItem(
            id = "1", songNumber = 42, title = "Amazing Grace", songbook = "Hymnal", songId = "Hymnal::42",
        )

        val back = roundTrip(song)

        assertEquals(song, back)
        assertEquals("Hymnal::42", back.songId, "the stable id is how a reopened service finds the song")
        assertEquals("Hymnal", back.songbook)
        assertEquals("42 - Amazing Grace", back.displayText)
    }

    @Test
    fun `a song saved before stable ids reads back with an empty one`() {
        val old = lenient.decodeFromString(
            ScheduleItem.serializer(),
            """{"type":"org.churchpresenter.app.churchpresenter.models.ScheduleItem.SongItem",
                "id":"1","songNumber":7,"title":"Old Song","songbook":"Hymnal","displayText":"7 - Old Song"}""",
        )

        val song = assertIs<ScheduleItem.SongItem>(old)
        assertEquals("", song.songId, "empty is the documented signal to fall back to title+number matching")
        assertEquals("7 - Old Song", song.displayText)
    }

    @Test
    fun `a verse keeps its text so a reopened service shows it without the bible loaded`() {
        val verse = ScheduleItem.BibleVerseItem(
            id = "2", bookName = "John", chapter = 3, verseNumber = 16,
            verseText = "For God so loved the world", verseRange = "16-18", bookId = 43,
        )

        val back = roundTrip(verse)

        assertEquals(verse, back)
        assertEquals("For God so loved the world", back.verseText)
        assertEquals(43, back.bookId, "the canonical id is what survives a change of bible language")
        assertEquals("16-18", back.verseRange)
        assertEquals("John 3:16-18", back.displayText)
    }

    @Test
    fun `a verse from a companion payload with no book id reads back as unknown`() {
        val remote = lenient.decodeFromString(
            ScheduleItem.serializer(),
            """{"type":"org.churchpresenter.app.churchpresenter.models.ScheduleItem.BibleVerseItem",
                "id":"2","bookName":"John","chapter":3,"verseNumber":16,"verseText":"…"}""",
        )

        val verse = assertIs<ScheduleItem.BibleVerseItem>(remote)
        assertEquals(0, verse.bookId, "0 is the documented 'unknown' that triggers lookup by book name")
        assertEquals("", verse.verseRange)
    }

    @Test
    fun `a label keeps its colours`() {
        val label = ScheduleItem.LabelItem(
            id = "3", text = "Offering", textColor = "#FFFFFF", backgroundColor = "#2E7D32",
        )

        val back = roundTrip(label)

        assertEquals(label, back)
        assertEquals("#2E7D32", back.backgroundColor, "the colour is how an operator scans the list")
    }

    @Test
    fun `a picture folder keeps its path as well as its name`() {
        val pictures = ScheduleItem.PictureItem(
            id = "4", folderPath = "/Users/av/Pictures/Baptism", folderName = "Baptism", imageCount = 24,
        )

        val back = roundTrip(pictures)

        assertEquals(pictures, back)
        assertEquals("/Users/av/Pictures/Baptism", back.folderPath, "the name alone cannot reopen the folder")
        assertEquals(24, back.imageCount)
    }

    @Test
    fun `a presentation keeps its file type`() {
        val deck = ScheduleItem.PresentationItem(
            id = "5", filePath = "/decks/Sermon.pptx", fileName = "Sermon.pptx", slideCount = 12, fileType = "pptx",
        )

        val back = roundTrip(deck)

        assertEquals(deck, back)
        assertEquals("pptx", back.fileType, "the type picks which parser opens the deck")
        assertEquals(12, back.slideCount)
    }

    @Test
    fun `a media item keeps whether it is local or streamed`() {
        val stream = ScheduleItem.MediaItem(
            id = "6", mediaUrl = "rtsp://cam.local/live", mediaTitle = "Overflow feed", mediaType = "local",
        )

        val back = roundTrip(stream)

        assertEquals(stream, back)
        assertEquals("rtsp://cam.local/live", back.mediaUrl)
        assertEquals("local", back.mediaType)
    }

    @Test
    fun `a lower third keeps its pause timing`() {
        val lowerThird = ScheduleItem.LowerThirdItem(
            id = "7", presetId = "preset-9", presetLabel = "Guest speaker",
            pauseAtFrame = true, pauseDurationMs = 4500L,
        )

        val back = roundTrip(lowerThird)

        assertEquals(lowerThird, back)
        assertTrue(back.pauseAtFrame)
        assertEquals(4500L, back.pauseDurationMs, "the hold time is the difference between readable and a flash")
        assertEquals("preset-9", back.presetId)
    }

    @Test
    fun `a website keeps its address as well as its title`() {
        val site = ScheduleItem.WebsiteItem(id = "9", url = "https://example.org/give", title = "Giving page")

        val back = roundTrip(site)

        assertEquals(site, back)
        assertEquals("https://example.org/give", back.url)
    }

    @Test
    fun `a scene item keeps which scene it points at`() {
        val scene = ScheduleItem.SceneItem(id = "10", sceneId = "scene-uuid", sceneName = "Welcome loop")

        val back = roundTrip(scene)

        assertEquals(scene, back)
        assertEquals("scene-uuid", back.sceneId, "the name is shown, but the id is what loads the scene")
    }

    @Test
    fun `a dictionary entry keeps its full definition`() {
        val word = ScheduleItem.DictionaryItem(
            id = "11", number = "G26", word = "ἀγάπη", transliteration = "agapē",
            definition = "love, benevolence, good will",
        )

        val back = roundTrip(word)

        assertEquals(word, back)
        assertEquals("ἀγάπη", back.word, "the Greek must survive the encode intact")
        assertEquals("love, benevolence, good will", back.definition)
    }

    // ── Announcements, which carry the most state ───────────────────────────────

    @Test
    fun `an announcement keeps every styling choice`() {
        val announcement = ScheduleItem.AnnouncementItem(
            id = "8", text = "Welcome to the 10am service",
            textColor = "#FFEE00", backgroundColor = "#101010", fontSize = 72, fontType = "Georgia",
            bold = true, italic = true, underline = true,
            shadow = true, shadowColor = "#333333", shadowSize = 140, shadowOpacity = 55,
            horizontalAlignment = "left", position = "bottom",
            animationType = "SLIDE_FROM_LEFT", animationDuration = 900, loopCount = 3,
        )

        val back = roundTrip(announcement)

        assertEquals(announcement, back)
        assertEquals(72, back.fontSize)
        assertEquals("Georgia", back.fontType)
        assertTrue(back.bold && back.italic && back.underline)
        assertTrue(back.shadow)
        assertEquals(140, back.shadowSize)
        assertEquals(55, back.shadowOpacity)
        assertEquals("SLIDE_FROM_LEFT", back.animationType)
        assertEquals(900, back.animationDuration)
        assertEquals(3, back.loopCount)
        assertEquals("bottom", back.position)
    }

    @Test
    fun `a countdown keeps its duration and its expiry message`() {
        val timer = ScheduleItem.AnnouncementItem(
            id = "8", text = "", isTimer = true, timerMode = Constants.TIMER_MODE_DURATION,
            timerHours = 1, timerMinutes = 2, timerSeconds = 3,
            timerTextColor = "#00FF00", timerExpiredText = "We're starting!",
        )

        val back = roundTrip(timer)

        assertEquals(timer, back)
        assertTrue(back.isTimer)
        assertEquals(1, back.timerHours)
        assertEquals(2, back.timerMinutes)
        assertEquals(3, back.timerSeconds)
        assertEquals("We're starting!", back.timerExpiredText, "the end-of-countdown message is set up once, weeks earlier")
        assertEquals("#00FF00", back.timerTextColor)
        assertEquals("Timer 1:02:03", back.displayText)
    }

    @Test
    fun `a countdown to a clock time keeps its target`() {
        val until = ScheduleItem.AnnouncementItem(
            id = "8", text = "", isTimer = true, timerMode = Constants.TIMER_MODE_CLOCK,
            targetHour = 10, targetMinute = 30, targetSecond = 5,
        )

        val back = roundTrip(until)

        assertEquals(until, back)
        assertEquals(Constants.TIMER_MODE_CLOCK, back.timerMode)
        assertEquals(10, back.targetHour)
        assertEquals(30, back.targetMinute)
        assertEquals(5, back.targetSecond)
        assertEquals("Until 10:30:05", back.displayText)
    }

    @Test
    fun `a live clock keeps its format string`() {
        val clock = ScheduleItem.AnnouncementItem(
            id = "8", text = "", isTimer = true, timerMode = Constants.TIMER_MODE_CLOCK_DISPLAY,
            liveClockFormat = "h:mm a",
        )

        val back = roundTrip(clock)

        assertEquals(clock, back)
        assertEquals("h:mm a", back.liveClockFormat)
        assertEquals("Clock", back.displayText)
    }

    @Test
    fun `an announcement saved with only its text reads back at the documented defaults`() {
        val minimal = lenient.decodeFromString(
            ScheduleItem.serializer(),
            """{"type":"org.churchpresenter.app.churchpresenter.models.ScheduleItem.AnnouncementItem",
                "id":"8","text":"Notice","displayText":"Notice"}""",
        )

        val announcement = assertIs<ScheduleItem.AnnouncementItem>(minimal)
        assertEquals("#FFFFFF", announcement.textColor)
        assertEquals("#000000", announcement.backgroundColor)
        assertEquals(48, announcement.fontSize)
        assertEquals("center", announcement.horizontalAlignment)
        assertEquals("center", announcement.position)
        assertEquals("SLIDE_FROM_BOTTOM", announcement.animationType)
        assertEquals(500, announcement.animationDuration)
        assertEquals(0, announcement.loopCount, "0 means loop forever, and is what an old notice must keep doing")
        assertEquals(false, announcement.isTimer)
        assertEquals(Constants.TIMER_MODE_DURATION, announcement.timerMode)
        assertEquals("HH:mm:ss", announcement.liveClockFormat)
    }

    // ── The polymorphic contract ────────────────────────────────────────────────

    @Test
    fun `each item type is stored under its own stable discriminator`() {
        // These strings are in every saved service file. Renaming a class or adding @SerialName
        // moves them and every service saved by an older build stops opening.
        val prefix = "org.churchpresenter.app.churchpresenter.models.ScheduleItem."

        assertEquals(prefix + "SongItem", discriminatorOf(sample<ScheduleItem.SongItem>()))
        assertEquals(prefix + "BibleVerseItem", discriminatorOf(sample<ScheduleItem.BibleVerseItem>()))
        assertEquals(prefix + "LabelItem", discriminatorOf(sample<ScheduleItem.LabelItem>()))
        assertEquals(prefix + "PictureItem", discriminatorOf(sample<ScheduleItem.PictureItem>()))
        assertEquals(prefix + "PresentationItem", discriminatorOf(sample<ScheduleItem.PresentationItem>()))
        assertEquals(prefix + "MediaItem", discriminatorOf(sample<ScheduleItem.MediaItem>()))
        assertEquals(prefix + "LowerThirdItem", discriminatorOf(sample<ScheduleItem.LowerThirdItem>()))
        assertEquals(prefix + "AnnouncementItem", discriminatorOf(sample<ScheduleItem.AnnouncementItem>()))
        assertEquals(prefix + "WebsiteItem", discriminatorOf(sample<ScheduleItem.WebsiteItem>()))
        assertEquals(prefix + "SceneItem", discriminatorOf(sample<ScheduleItem.SceneItem>()))
        assertEquals(prefix + "DictionaryItem", discriminatorOf(sample<ScheduleItem.DictionaryItem>()))
    }

    @Test
    fun `an item saved by a newer build loads with the fields this build knows`() {
        val fromFuture = lenient.decodeFromString(
            ScheduleItem.serializer(),
            """{"type":"org.churchpresenter.app.churchpresenter.models.ScheduleItem.SceneItem",
                "id":"10","sceneId":"s","sceneName":"Welcome","displayText":"Scene: Welcome",
                "someFutureField":{"nested":true}}""",
        )

        val scene = assertIs<ScheduleItem.SceneItem>(fromFuture)
        assertEquals("Welcome", scene.sceneName, "one unknown key must not cost the church its saved service")
    }

    @Test
    fun `an item written by an encoder that omits defaults still reads back the same`() {
        // Schedule items also travel over the companion API and instance link, which do not all
        // encode defaults. Dropping a field that is at its default has to be lossless — including
        // `displayText`, which is a default computed from the other fields.
        val terse = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        allKinds().forEach {
            val encoded = terse.encodeToString(ScheduleItem.serializer(), it)
            val back = terse.decodeFromString(ScheduleItem.serializer(), encoded)

            assertEquals(it, back, "${it::class.simpleName} lost a field when its defaults were omitted")
            assertEquals(it.displayText, back.displayText)
        }
    }

    // ── The whole schedule ──────────────────────────────────────────────────────

    @Test
    fun `a whole service round trips in order with its types intact`() {
        val service = allKinds()

        val encoded = json.encodeToString(ListSerializer(ScheduleItem.serializer()), service)
        val back = json.decodeFromString(ListSerializer(ScheduleItem.serializer()), encoded)

        assertEquals(service, back)
        assertEquals(service.map { it.id }, back.map { it.id }, "the order is the order of the service")
        assertEquals(
            service.map { it::class },
            back.map { it::class },
            "an item that comes back as the wrong type goes live as the wrong thing",
        )
    }

    @Test
    fun `every saved row keeps the label it was saved with`() {
        allKinds().forEach { original ->
            val encoded = json.encodeToString(ScheduleItem.serializer(), original)
            val back = json.decodeFromString(ScheduleItem.serializer(), encoded)

            assertEquals(
                original.displayText,
                back.displayText,
                "${original::class.simpleName} would reopen showing a different row than it was saved as",
            )
            assertTrue(back.displayText.isNotBlank(), "${original::class.simpleName} reopened as a blank row")
        }
    }

    @Test
    fun `two items differing only in id are not the same item`() {
        val song = ScheduleItem.SongItem(id = "1", songNumber = 42, title = "Amazing Grace", songbook = "Hymnal")

        assertEquals(song, song.copy(), "an untouched copy is the same item")
        assertEquals(song.hashCode(), song.copy().hashCode())
        assertNotEquals(song, song.copy(id = "2"), "the same song added twice must stay two separate rows")
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────

    private inline fun <reified T : ScheduleItem> sample(): T =
        allKinds().filterIsInstance<T>().single()

    private fun allKinds(): List<ScheduleItem> = listOf(
        ScheduleItem.SongItem(id = "1", songNumber = 42, title = "Amazing Grace", songbook = "Hymnal", songId = "Hymnal::42"),
        ScheduleItem.BibleVerseItem(
            id = "2", bookName = "John", chapter = 3, verseNumber = 16, verseText = "For God so loved…",
            verseRange = "16-18", bookId = 43,
        ),
        ScheduleItem.LabelItem(id = "3", text = "Offering", textColor = "#FFFFFF", backgroundColor = "#2E7D32"),
        ScheduleItem.PictureItem(id = "4", folderPath = "/pics/baptism", folderName = "Baptism", imageCount = 24),
        ScheduleItem.PresentationItem(
            id = "5", filePath = "/decks/Sermon.pptx", fileName = "Sermon.pptx", slideCount = 12, fileType = "pptx",
        ),
        ScheduleItem.MediaItem(id = "6", mediaUrl = "/media/clip.mp4", mediaTitle = "Baptism video", mediaType = "local"),
        ScheduleItem.LowerThirdItem(
            id = "7", presetId = "preset-9", presetLabel = "Guest speaker", pauseAtFrame = true, pauseDurationMs = 4500L,
        ),
        ScheduleItem.AnnouncementItem(id = "8", text = "Welcome to the 10am service"),
        ScheduleItem.WebsiteItem(id = "9", url = "https://example.org/give", title = "Giving page"),
        ScheduleItem.SceneItem(id = "10", sceneId = "scene-uuid", sceneName = "Welcome loop"),
        ScheduleItem.DictionaryItem(
            id = "11", number = "G26", word = "agape", transliteration = "agapē", definition = "love",
        ),
    )
}
