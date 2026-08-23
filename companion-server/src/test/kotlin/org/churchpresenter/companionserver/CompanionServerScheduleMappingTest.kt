package org.churchpresenter.companionserver

import org.churchpresenter.core.models.songs.SongItem
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.utils.Constants
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How a schedule crosses the wire, in both directions.
 *
 * Out: `updateSchedule` flattens twelve different `ScheduleItem` types into one DTO, and the fields
 * it fills differ per type. A phone renders its schedule list from nothing else, so a field dropped
 * here is an item that shows up blank or unlabelled on every connected device — and because the DTO
 * is one wide shape shared by every type, a mis-mapped field is silent rather than a compile error.
 *
 * In: `parseRemoteItem` turns what a phone posts back into a `ScheduleItem`, and it accepts three
 * shapes for historical reasons — the flat companion format, the same format with only an id for
 * content the server already knows about (pictures by folder, presentations by file hash), and the
 * legacy sealed-class format with a discriminator. Anything it fails to parse must be refused
 * rather than silently added as something else.
 *
 * Driven against a real server over real HTTP, as the sibling suites are, because `start()` builds
 * its own Netty server rather than exposing a separable Ktor module.
 */
class CompanionServerScheduleMappingTest {

    private lateinit var client: HttpClient
    private lateinit var approvals: CoroutineScope

    companion object {
        private lateinit var server: CompanionServer
        private var port: Int = 0
        private val json = Json { ignoreUnknownKeys = true }

        @JvmStatic
        @BeforeClass
        fun startServer() {
            server = CompanionServer()
            server.start(port = testPort(39_717))
            port = runBlocking {
                withTimeoutOrNull(10_000) {
                    while (!server.isRunning.value || server.serverUrl.value.isBlank()) {
                        kotlinx.coroutines.delay(25)
                    }
                    server.serverUrl.value.substringAfterLast(':').toInt()
                }
            } ?: error("server did not start")
        }

        @JvmStatic
        @AfterClass
        fun stopServer() {
            runCatching { server.stop() }
        }
    }

    @BeforeTest
    fun openClient() {
        client = HttpClient(CIO)
        approvals = CoroutineScope(Dispatchers.Default + Job())
    }

    @AfterTest
    fun closeClient() {
        runCatching { approvals.cancel() }
        runCatching { client.close() }
        server.updateSchedule(emptyList())
    }

    private fun url(path: String) = "http://127.0.0.1:$port$path"

    private fun getting(path: String): HttpResponse = runBlocking { client.get(url(path)) }

    private fun HttpResponse.text(): String = runBlocking { bodyAsText() }

    /** The schedule as the server currently serves it. */
    private fun servedSchedule(): List<JsonObject> =
        (json.parseToJsonElement(getting(Constants.ENDPOINT_SCHEDULE).text()) as JsonObject)["items"]!!
            .jsonArray.map { it.jsonObject }

    /** Pushes one item and returns the single DTO it became. */
    private fun push(item: ScheduleItem): JsonObject {
        server.updateSchedule(listOf(item))
        return servedSchedule().single()
    }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

    // ── Outbound: one DTO shape, twelve item types ──────────────────────────────

    @Test
    fun `a song carries what a phone lists it by`() {
        val dto = push(
            ScheduleItem.SongItem(id = "s1", songNumber = 42, title = "Amazing Grace", songbook = "Hymnal")
        )

        assertEquals("song", dto.str("type"))
        assertEquals("s1", dto.str("id"))
        assertEquals("42", dto.str("songNumber"))
        assertEquals("Amazing Grace", dto.str("title"))
        assertEquals("Hymnal", dto.str("songbook"))
        assertEquals("42 - Amazing Grace", dto.str("displayText"))
    }

    @Test
    fun `a single verse is sent without a range, a passage with one`() {
        val single = push(
            ScheduleItem.BibleVerseItem(
                id = "b1", bookName = "John", chapter = 3, verseNumber = 16,
                verseText = "For God so loved the world.",
            )
        )
        assertEquals("bible", single.str("type"))
        assertEquals("John", single.str("bookName"))
        assertEquals("16", single.str("verseNumber"))
        assertEquals("For God so loved the world.", single.str("text"))
        assertNull(
            single["verseRange"]?.jsonPrimitive?.contentOrNullIfNull(),
            "an empty range is normalised away so a client can test for a passage",
        )

        val passage = push(
            ScheduleItem.BibleVerseItem(
                id = "b2", bookName = "Psalms", chapter = 23, verseNumber = 1,
                verseText = "The LORD is my shepherd.", verseRange = "1-3",
            )
        )
        assertEquals("1-3", passage.str("verseRange"))
        assertEquals("Psalms 23:1-3", passage.str("displayText"))
    }

    @Test
    fun `a label carries its colours, because it is drawn rather than read`() {
        val dto = push(
            ScheduleItem.LabelItem(
                id = "l1", text = "Welcome", textColor = "#FFFFFF", backgroundColor = "#203040",
            )
        )

        assertEquals("label", dto.str("type"))
        assertEquals("Welcome", dto.str("text"))
        assertEquals("#FFFFFF", dto.str("textColor"))
        assertEquals("#203040", dto.str("backgroundColor"))
    }

    @Test
    fun `a media item carries the url a client would play`() {
        val dto = push(
            ScheduleItem.MediaItem(
                id = "m1", mediaUrl = "https://example.org/clip.mp4",
                mediaTitle = "Bumper", mediaType = "youtube",
            )
        )

        assertEquals("media", dto.str("type"))
        assertEquals("https://example.org/clip.mp4", dto.str("mediaUrl"))
        assertEquals("Bumper", dto.str("mediaTitle"))
        assertEquals("youtube", dto.str("mediaType"))
    }

    @Test
    fun `a lower third carries the preset, not the animation`() {
        val dto = push(
            ScheduleItem.LowerThirdItem(
                id = "lt1", presetId = "preset-7", presetLabel = "Speaker Name",
                pauseAtFrame = true, pauseDurationMs = 2000,
            )
        )

        assertEquals("lower_third", dto.str("type"))
        assertEquals("preset-7", dto.str("presetId"))
        assertEquals("Speaker Name", dto.str("presetLabel"))
    }

    @Test
    fun `an announcement carries its styling and animation`() {
        val dto = push(
            ScheduleItem.AnnouncementItem(
                id = "a1", text = "Service starts soon", textColor = "#EEEEEE",
                backgroundColor = "#111111", fontSize = 64,
                animationType = "slide", animationDuration = 500,
            )
        )

        assertEquals("announcement", dto.str("type"))
        assertEquals("Service starts soon", dto.str("text"))
        assertEquals("64", dto.str("fontSize"))
        assertEquals("slide", dto.str("animationType"))
        assertEquals("500", dto.str("animationDuration"))
        assertEquals("false", dto.str("isTimer"), "a plain announcement is not a timer")
    }

    @Test
    fun `a countdown carries every field needed to run the clock`() {
        // A phone renders the countdown itself rather than being told the remaining time, so all of
        // these have to survive: without them it would show a static, wrong number.
        val dto = push(
            ScheduleItem.AnnouncementItem(
                id = "a2", text = "", isTimer = true,
                timerMode = Constants.TIMER_MODE_DURATION,
                timerHours = 0, timerMinutes = 5, timerSeconds = 30,
                timerExpiredText = "Starting now",
            )
        )

        assertEquals("true", dto.str("isTimer"))
        assertEquals(Constants.TIMER_MODE_DURATION, dto.str("timerMode"))
        assertEquals("5", dto.str("timerMinutes"))
        assertEquals("30", dto.str("timerSeconds"))
        assertEquals("Starting now", dto.str("timerExpiredText"))
    }

    @Test
    fun `a website, a scene and a dictionary entry each map to their own shape`() {
        val website = push(ScheduleItem.WebsiteItem(id = "w1", url = "https://example.org", title = "Notices"))
        assertEquals("website", website.str("type"))
        assertEquals("https://example.org", website.str("url"))
        assertEquals("Notices", website.str("title"))

        val scene = push(ScheduleItem.SceneItem(id = "sc1", sceneId = "scene-9", sceneName = "Pre-service"))
        assertEquals("scene", scene.str("type"))
        assertEquals("Scene: Pre-service", scene.str("displayText"))

        // A dictionary entry has no dedicated fields — it is flattened into one readable line.
        val dictionary = push(
            ScheduleItem.DictionaryItem(
                id = "d1", number = "G26", word = "ἀγάπη",
                transliteration = "agape", definition = "love",
            )
        )
        assertEquals("dictionary", dictionary.str("type"))
        assertEquals("ἀγάπη (agape): love", dictionary.str("text"))
    }

    @Test
    fun `a picture folder in the schedule becomes browsable by index`() {
        // Pushing the item is also what registers the folder, so a phone can pull thumbnails for a
        // schedule item nobody has opened on the desktop yet.
        val folder = java.nio.file.Files.createTempDirectory("cp-schedule-pictures").toFile()
        try {
            listOf("one.jpg", "two.jpg").forEach {
                java.io.File(folder, it).writeBytes(byteArrayOf(1, 2, 3, 4))
            }

            val dto = push(
                ScheduleItem.PictureItem(
                    id = "p1", folderPath = folder.absolutePath, folderName = "Slides", imageCount = 2,
                )
            )

            assertEquals("picture", dto.str("type"))
            assertEquals(folder.absolutePath, dto.str("folderPath"))
            assertEquals("Slides", dto.str("folderName"))
            assertEquals("2", dto.str("imageCount"))

            // The registration happens off the calling thread, so wait for the effect itself.
            val served = runBlocking {
                withTimeoutOrNull(5_000) {
                    while (true) {
                        val response = getting("${Constants.ENDPOINT_PICTURES}/p1/images/0")
                        if (response.status == HttpStatusCode.OK) return@withTimeoutOrNull true
                        kotlinx.coroutines.delay(20)
                    }
                    @Suppress("UNREACHABLE_CODE") false
                }
            }
            assertTrue(served == true, "the folder's first image should become fetchable")
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `every item in a schedule is sent, in the order the operator arranged them`() {
        server.updateSchedule(
            listOf(
                ScheduleItem.LabelItem(id = "1", text = "Welcome", textColor = "#FFF", backgroundColor = "#000"),
                ScheduleItem.SongItem(id = "2", songNumber = 1, title = "Opening", songbook = "Hymnal"),
                ScheduleItem.WebsiteItem(id = "3", url = "https://example.org"),
            )
        )

        assertEquals(listOf("1", "2", "3"), servedSchedule().map { it.str("id") })
        assertEquals(listOf("label", "song", "website"), servedSchedule().map { it.str("type") })
    }

    @Test
    fun `clearing the schedule empties it for every client`() {
        push(ScheduleItem.LabelItem(id = "x", text = "Gone", textColor = "#FFF", backgroundColor = "#000"))
        server.updateSchedule(emptyList())

        assertTrue(servedSchedule().isEmpty())
    }

    // ── Inbound: what a phone posts back ────────────────────────────────────────

    /**
     * Posts [body] to the add endpoint with the operator approving, and returns the item the server
     * parsed out of it — or null if it refused the body.
     *
     * The endpoint suspends until the desktop answers, so the approval is wired up before the post
     * and the request is made from another coroutine; the collected request is the positive signal,
     * so nothing here waits on a timer.
     */
    private fun addAndApprove(body: String): Pair<HttpStatusCode, ScheduleItem?> = runBlocking {
        var parsed: ScheduleItem? = null
        approvals.launch {
            server.onAddToSchedule.collect { request ->
                parsed = request.item
                request.decision.complete(true)
            }
        }
        // The collector is not subscribed the moment `launch` returns, and onAddToSchedule has
        // replay = 0: a request emitted before it subscribes is dropped on the floor, its decision
        // is never completed, and the endpoint — which suspends until the desktop answers — hangs
        // until the HTTP client gives up. Wait for the subscription itself, which is a positive
        // signal and so costs nothing once the collector is live.
        withTimeout(5_000) { server.onAddToSchedule.subscriptionCount.first { it > 0 } }
        val response = async {
            client.post(url(Constants.ENDPOINT_SCHEDULE_ADD)) { setBody(body) }
        }.await()
        response.status to parsed
    }

    /**
     * Waits for [updatePresentation]'s own coroutine to publish the catalogue for [id] — the
     * endpoint answering 200 is the positive signal, so this never waits on a timer.
     */
    private fun awaitCatalog(id: String) {
        val published = runBlocking {
            withTimeoutOrNull(5_000) {
                while (getting("${Constants.ENDPOINT_PRESENTATIONS}/$id").status != HttpStatusCode.OK) {
                    kotlinx.coroutines.delay(20)
                }
                true
            }
        }
        assertTrue(published == true, "the catalogue for $id was never published")
    }

    @Test
    fun `a song posted in the companion format arrives as a song`() {
        val (status, item) = addAndApprove(
            """{"item":{"type":"song","songNumber":42,"title":"Amazing Grace","songbook":"Hymnal"}}"""
        )

        assertEquals(HttpStatusCode.OK, status)
        val song = assertNotNull(item as? ScheduleItem.SongItem, "got $item")
        assertEquals(42, song.songNumber)
        assertEquals("Amazing Grace", song.title)
        assertEquals("Hymnal", song.songbook)
    }

    @Test
    @Suppress("MaxLineLength")
    fun `a verse posted in the companion format arrives as a verse`() {
        val (status, item) = addAndApprove(
            """{"item":{"type":"bible","bookName":"John","chapter":3,"verseNumber":16,"verseText":"For God so loved the world."}}"""
        )

        assertEquals(HttpStatusCode.OK, status)
        val verse = assertNotNull(item as? ScheduleItem.BibleVerseItem, "got $item")
        assertEquals("John", verse.bookName)
        assertEquals(3, verse.chapter)
        assertEquals(16, verse.verseNumber)
    }

    @Test
    fun `a picture posted as a folder id is resolved against the catalogue`() {
        // The phone has only the folder id it was given; the paths stay on the desktop.
        val folder = java.nio.file.Files.createTempDirectory("cp-remote-pictures").toFile()
        try {
            val images = listOf("a.jpg", "b.jpg").map {
                java.io.File(folder, it).apply { writeBytes(byteArrayOf(9)) }
            }
            server.updatePictures("folder-42", "Announcements", folder.absolutePath, images)

            // "folder-id", not "folderId" — the DTO renames it for the companion app's wire format.
            val (status, item) = addAndApprove("""{"item":{"type":"picture","folder-id":"folder-42"}}""")

            assertEquals(HttpStatusCode.OK, status)
            val picture = assertNotNull(item as? ScheduleItem.PictureItem, "got $item")
            assertEquals(folder.absolutePath, picture.folderPath, "resolved to the real folder")
            assertEquals("Announcements", picture.folderName)
            assertEquals(2, picture.imageCount)
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `a presentation posted as an id is resolved against the rendered catalogue`() {
        // The phone lists presentations by the id the server gave it; the deck's path never leaves
        // the desktop, so the id is all it can post back.
        val dir = java.nio.file.Files.createTempDirectory("cp-remote-presentation").toFile()
        try {
            val deck = java.io.File(dir, "Sermon.pptx").apply { writeBytes(byteArrayOf(1)) }
            val slides = (0..2).map { java.io.File(dir, "slide$it.jpg").apply { writeBytes(byteArrayOf(it.toByte())) } }
            server.updatePresentation(
                id = "deck-1", filePath = deck.absolutePath,
                fileName = "Sermon", fileType = "pptx", slideFiles = slides
            )
            awaitCatalog("deck-1")

            val (status, item) = addAndApprove("""{"item":{"type":"presentation","id":"deck-1"}}""")

            assertEquals(HttpStatusCode.OK, status)
            val presentation = assertNotNull(item as? ScheduleItem.PresentationItem, "got $item")
            assertEquals(deck.absolutePath, presentation.filePath, "resolved to the real deck")
            assertEquals("Sermon", presentation.fileName, "the name comes from the catalogue, not the phone")
            assertEquals(3, presentation.slideCount)
            assertEquals("pptx", presentation.fileType)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a presentation posted with no type at all is still resolved by its id`() {
        // Mobile omits "type" when it equals its default, so an id that resolves has to be enough.
        val dir = java.nio.file.Files.createTempDirectory("cp-remote-presentation-untyped").toFile()
        try {
            val deck = java.io.File(dir, "Notices.pdf").apply { writeBytes(byteArrayOf(1)) }
            val slides = listOf(java.io.File(dir, "slide0.jpg").apply { writeBytes(byteArrayOf(7)) })
            server.updatePresentation(
                id = "deck-2", filePath = deck.absolutePath,
                fileName = "Notices", fileType = "pdf", slideFiles = slides
            )
            awaitCatalog("deck-2")

            val (status, item) = addAndApprove("""{"item":{"id":"deck-2"}}""")

            assertEquals(HttpStatusCode.OK, status)
            val presentation = assertNotNull(item as? ScheduleItem.PresentationItem, "got $item")
            assertEquals(deck.absolutePath, presentation.filePath)
            assertEquals(1, presentation.slideCount)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a presentation the desktop has only scheduled, never rendered, is found by scanning the schedule`() {
        // Nothing has opened this deck, so there is no catalogue and no render — only the schedule
        // entry. Posting the schedule item's own id (not the file-hash the render would key on) is
        // what a phone does when it adds a deck straight from the service list.
        val path = java.io.File(
            java.nio.file.Files.createTempDirectory("cp-remote-presentation-scan").toFile(),
            "Unopened.pptx"
        ).absolutePath
        server.updateSchedule(
            listOf(
                ScheduleItem.PresentationItem(
                    id = "sched-9", filePath = path, fileName = "Unopened", slideCount = 12, fileType = "pptx"
                )
            )
        )

        val (status, item) = addAndApprove("""{"item":{"type":"presentation","id":"sched-9","title":"Unopened"}}""")

        assertEquals(HttpStatusCode.OK, status)
        val presentation = assertNotNull(item as? ScheduleItem.PresentationItem, "got $item")
        assertEquals(path, presentation.filePath)
        assertEquals("Unopened", presentation.fileName, "with no catalogue the phone's own title is used")
        assertEquals(0, presentation.slideCount, "and the slide count is unknown until something renders it")
    }

    @Test
    fun `an announcement posted with only its text takes every default`() {
        val (status, item) = addAndApprove("""{"item":{"type":"announcement","announcementText":"Welcome"}}""")

        assertEquals(HttpStatusCode.OK, status)
        val announcement = assertNotNull(item as? ScheduleItem.AnnouncementItem, "got $item")
        assertEquals("Welcome", announcement.text)
        assertEquals("#FFFFFF", announcement.textColor)
        assertEquals("#000000", announcement.backgroundColor)
        assertEquals(48, announcement.fontSize)
        assertEquals("SLIDE_FROM_BOTTOM", announcement.animationType)
        assertEquals(500, announcement.animationDuration)
        assertFalse(announcement.isTimer)
        assertEquals(0, announcement.timerHours)
        assertEquals(0, announcement.timerMinutes)
        assertEquals(0, announcement.timerSeconds)
        assertEquals("", announcement.timerExpiredText)
        assertEquals("HH:mm:ss", announcement.liveClockFormat)
    }

    @Test
    fun `an announcement's timer colour falls back to its text colour`() {
        val (_, item) = addAndApprove(
            """{"item":{"type":"announcement","announcementText":"Starting soon","textColor":"#FF0000"}}"""
        )

        val announcement = assertNotNull(item as? ScheduleItem.AnnouncementItem, "got $item")
        assertEquals("#FF0000", announcement.timerTextColor)
    }

    @Test
    fun `an announcement with an empty text is still an announcement`() {
        val (status, item) = addAndApprove("""{"item":{"type":"announcement","announcementText":""}}""")

        assertEquals(HttpStatusCode.OK, status)
        val announcement = assertNotNull(item as? ScheduleItem.AnnouncementItem, "got $item")
        assertEquals("", announcement.text)
    }

    @Test
    @Suppress("MaxLineLength")
    fun `a timer posted with its own values keeps them`() {
        val (_, item) = addAndApprove(
            """{"item":{"type":"announcement","announcementText":"Countdown","isTimer":true,"timerHours":1,"timerMinutes":2,"timerSeconds":3,"timerExpiredText":"Time","liveClockFormat":"HH:mm"}}"""
        )

        val announcement = assertNotNull(item as? ScheduleItem.AnnouncementItem, "got $item")
        assertTrue(announcement.isTimer)
        assertEquals(1, announcement.timerHours)
        assertEquals(2, announcement.timerMinutes)
        assertEquals(3, announcement.timerSeconds)
        assertEquals("Time", announcement.timerExpiredText)
        assertEquals("HH:mm", announcement.liveClockFormat)
    }

    @Test
    fun `a folder id the desktop has never heard of is refused`() {
        val (status, item) = addAndApprove("""{"item":{"type":"picture","folder-id":"folder-nope"}}""")

        assertEquals(HttpStatusCode.BadRequest, status)
        assertNull(item)
    }

    @Test
    @Suppress("MaxLineLength")
    fun `a picture carrying an explicit path is taken as sent, not looked up`() {
        val folder = java.nio.file.Files.createTempDirectory("cp-remote-pictures-explicit").toFile()
        try {
            val images = listOf("a.jpg").map { java.io.File(folder, it).apply { writeBytes(byteArrayOf(9)) } }
            server.updatePictures("folder-77", "Catalogued", folder.absolutePath, images)
            val elsewhere = java.nio.file.Files.createTempDirectory("cp-remote-pictures-sent").toFile()

            val (status, item) = addAndApprove(
                """{"item":{"type":"picture","folder-id":"folder-77","folderPath":"${elsewhere.absolutePath}","folderName":"Sent","imageCount":4}}"""
            )

            assertEquals(HttpStatusCode.OK, status)
            val picture = assertNotNull(item as? ScheduleItem.PictureItem, "got $item")
            assertEquals(elsewhere.absolutePath, picture.folderPath)
            assertEquals("Sent", picture.folderName)
            assertEquals(4, picture.imageCount)
            elsewhere.deleteRecursively()
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `a picture posted with no id of its own is given one`() {
        val folder = java.nio.file.Files.createTempDirectory("cp-remote-pictures-blank-id").toFile()
        try {
            val images = listOf("a.jpg").map { java.io.File(folder, it).apply { writeBytes(byteArrayOf(9)) } }
            server.updatePictures("folder-88", "Announcements", folder.absolutePath, images)

            val (status, item) = addAndApprove("""{"item":{"type":"picture","id":"","folder-id":"folder-88"}}""")

            assertEquals(HttpStatusCode.OK, status)
            val picture = assertNotNull(item as? ScheduleItem.PictureItem, "got $item")
            assertTrue(picture.id.isNotBlank())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `a presentation id the desktop has never heard of is refused`() {
        val (status, item) = addAndApprove("""{"item":{"type":"presentation","id":"deck-that-never-existed"}}""")

        assertEquals(HttpStatusCode.BadRequest, status)
        assertNull(item, "an unresolvable id must not become an item with no file behind it")
    }

    @Test
    @Suppress("MaxLineLength")
    fun `a body in the legacy sealed-class format is still accepted`() {
        // Older clients send the discriminated form; they must keep working.
        val (status, item) = addAndApprove(
            """{"item":{"type":"org.churchpresenter.app.churchpresenter.models.ScheduleItem.LabelItem","id":"legacy-1","text":"Offering","textColor":"#FFFFFF","backgroundColor":"#000000"}}"""
        )

        assertEquals(HttpStatusCode.OK, status)
        val label = assertNotNull(item as? ScheduleItem.LabelItem, "got $item")
        assertEquals("Offering", label.text)
    }

    @Test
    fun `a body that parses as nothing is refused rather than added as an empty item`() {
        val response = runBlocking {
            client.post(url(Constants.ENDPOINT_SCHEDULE_ADD)) { setBody("""{"nonsense":true}""") }
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.text().contains("invalid request body"), response.text())
    }

    @Test
    fun `a body that is not json at all is refused`() {
        val response = runBlocking {
            client.post(url(Constants.ENDPOINT_SCHEDULE_ADD)) { setBody("not json") }
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}

/** Null for a JSON null, so an explicitly-null field reads the same as an absent one. */
private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullIfNull(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content
